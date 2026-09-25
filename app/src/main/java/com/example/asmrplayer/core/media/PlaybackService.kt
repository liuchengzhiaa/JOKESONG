package com.example.asmrplayer.core.media

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns the ExoPlayer instance and the media session.
 *
 * The service, not an Activity or a ViewModel, owns queue state, audio focus and
 * the media notification, so playback continues when the screen locks, the app
 * is switched away from, or the task is swiped away. The UI never touches the
 * player directly; it goes through [PlaybackConnection].
 *
 * Media comes from the SAF document URIs the library already scans, so no broad
 * storage permission is involved.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            // Headphone disconnect pauses immediately.
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Swiping the task away keeps playing, which is the product default; the
     * service only stops itself when there is nothing left to play.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
