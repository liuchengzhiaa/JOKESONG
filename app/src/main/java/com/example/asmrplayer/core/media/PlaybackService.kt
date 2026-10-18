package com.example.asmrplayer.core.media

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the ExoPlayer instance and the media session.
 *
 * The service, not an Activity or a ViewModel, owns queue state, audio focus and
 * the media notification, so playback continues when the screen locks, the app
 * is switched away from, or the task is swiped away. The UI never touches the
 * player directly; it goes through [PlaybackConnection].
 *
 * It also owns the sleep timer, for the same reason it owns the queue: the timer's
 * whole job happens while the screen is off. See [SleepTimerSession].
 *
 * Media comes from the SAF document URIs the library already scans, so no broad
 * storage permission is involved.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null
    private var desktopLyrics: com.example.asmrplayer.core.overlay.DesktopLyricsController? = null

    private val callback = object : MediaSession.Callback {
        /**
         * Grants the sleep timer command on top of whatever a session grants by
         * default.
         *
         * The defaults are carried over rather than restated: this builder starts from
         * an empty command set, and returning that would take away every command the
         * controller needs to drive playback at all -- the queue, the transport, the
         * seek -- which would look like the player page having lost its controls.
         */
        // The connection result is Media3's opt-in surface: it is the only way to add a
        // command to what a controller may send, and it is exactly what that surface
        // exists for. Opted in here rather than on the class, so nothing else in this
        // service is labelled unstable by association.
        @androidx.annotation.OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val defaults =
                MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(
                    defaults.availableSessionCommands.buildUpon().add(SleepTimerCommand).build(),
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != SleepTimerAction) return notSupportedResult()
            armSleepTimer(session, args.getInt(SleepTimerMinutesKey))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            // Headphone disconnect pauses immediately.
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(callback)
            // Published before anyone connects, so a UI that arrives late reads the
            // timer rather than starting out believing there is none.
            .setSessionExtras(timerExtras(noSleepTimer()))
            .build()
        desktopLyrics = com.example.asmrplayer.core.overlay.DesktopLyricsController(this, player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Arms or cancels the sleep timer.
     *
     * Arming publishes the deadline first and then waits, so the countdown the owner
     * sees is the one that is actually running -- there is no interval in which the
     * service is counting down to a moment nobody has been told about.
     */
    private fun armSleepTimer(session: MediaSession, minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        val now = System.currentTimeMillis()
        val deadline = sleepDeadlineFrom(minutes, now)
        session.setSessionExtras(timerExtras(SleepTimerSnapshot(deadline, minutes.takeIf { it > 0 })))
        if (deadline == null) return
        sleepJob = scope.launch {
            delay((deadline - now).coerceAtLeast(0L))
            session.player.pause()
            // Cleared on the way out: a deadline in the past is not a timer, and
            // leaving it there would draw a countdown that never ends.
            session.setSessionExtras(timerExtras(noSleepTimer()))
            sleepJob = null
        }
    }

    private fun timerExtras(timer: SleepTimerSnapshot): Bundle = Bundle().apply {
        putLong(SleepTimerDeadlineKey, timer.expiresAtMs ?: 0L)
        putInt(SleepTimerArmedKey, timer.minutes ?: 0)
    }

    /**
     * Swiping the task away keeps playing, which is the product default; the
     * service only stops itself when there is nothing left to play.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (shouldStopPlaybackServiceAfterTaskRemoved(player.mediaItemCount)) stopSelf()
    }

    override fun onDestroy() {
        desktopLyrics?.close()
        desktopLyrics = null
        // The timer dies with the thing it would have paused. A coroutine left holding
        // a released player would fire into a session that no longer exists.
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

/**
 * The product default keeps a paused queue alive after the task is swiped away.
 * Only an empty session can be stopped here. A future "swipe to stop" setting
 * can become an explicit second input instead of inferring intent from pause.
 */
internal fun shouldStopPlaybackServiceAfterTaskRemoved(mediaItemCount: Int): Boolean =
    mediaItemCount <= 0
