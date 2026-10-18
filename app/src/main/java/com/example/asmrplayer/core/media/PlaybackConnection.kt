package com.example.asmrplayer.core.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.asmrplayer.core.model.LibraryItem
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Position is republished at this cadence while playback is running. */
private const val PositionTickMillis = 500L

/**
 * Lifecycle-safe UI-side handle on [PlaybackService].
 *
 * It connects a `MediaController` while the UI is started and releases it when
 * the UI stops, so nothing leaks while the service keeps playing in the
 * background. All player state is published as one [PlaybackState] value that
 * every player surface observes, so the focus card and the full player can never
 * disagree about what is playing or where the position is.
 */
class PlaybackConnection(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
        }
    }

    // Extras are not player events, so the tick above would not notice a sleep timer
    // being armed from another surface while this one is paused. This is the only
    // signal that the deadline changed.
    private val sessionListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            publish()
        }
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionJob: Job? = null
    private var pendingAction: ((MediaController) -> Unit)? = null

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token)
            .setListener(sessionListener)
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future) return@addListener
                val connected = try {
                    future.get()
                } catch (_: Exception) {
                    null
                }
                if (connected == null) {
                    controllerFuture = null
                    mutableState.value = PlaybackState()
                    return@addListener
                }
                controller = connected
                connected.addListener(listener)
                val queued = pendingAction
                pendingAction = null
                publish()
                startPositionTicker()
                queued?.invoke(connected)
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun disconnect() {
        stopPositionTicker()
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        pendingAction = null
        mutableState.value = PlaybackState()
    }

    /** Releases the connection and its scope. The instance is unusable afterwards. */
    fun release() {
        disconnect()
        scope.cancel()
    }

    /**
     * Replaces the queue with the current folder's audio children, in display
     * order, and starts at [selected].
     */
    fun playFromFolder(folderItems: List<LibraryItem>, selected: LibraryItem,
        timedText: Map<String, com.example.asmrplayer.core.subtitles.TimedTextMatch> = emptyMap()) {
        val plan = buildFolderQueue(folderItems, selected) ?: return
        withController { controller ->
            controller.setMediaItems(plan.items.map { it.toMediaItem(timedText[it.id]) }, plan.startIndex, 0L)
            controller.prepare()
            controller.play()
        }
    }

    fun togglePlayPause() {
        withController { controller ->
            when (toggleCommandFor(controller.toPlaybackState(controller.sleepTimerSnapshot()))) {
                PlaybackCommand.Play -> controller.play()
                PlaybackCommand.Pause -> controller.pause()
                PlaybackCommand.None -> Unit
            }
        }
    }

    /** Reopen the edited source without losing queue, position, repeat, timer or subtitle extras. */
    fun onFileMetadataSaved(id: String, saved: com.example.asmrplayer.core.metadata.TagSaveResult) {
        withController { c ->
            val index = (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == id } ?: return@withController
            val old = c.getMediaItemAt(index)
            val current = c.currentMediaItemIndex
            val position = c.currentPosition.coerceAtLeast(0L)
            val ready = c.playWhenReady
            if (index == current) c.stop() // Releases the now-replaced inode, rather than reusing an identical URI.
            val title = saved.title.ifBlank { old.mediaMetadata.title?.toString().orEmpty() }
            c.replaceMediaItem(index, old.buildUpon().setUri(saved.documentUri).setMediaMetadata(
                old.mediaMetadata.buildUpon().setTitle(title).setArtist(saved.artist).setArtworkUri(saved.artworkUri.toUri()).build()).build())
            if (index == current) { c.seekTo(current, position); c.prepare(); if (ready) c.play() }
            publish()
        }
    }

    fun seekTo(positionMs: Long) {
        withController { controller ->
            if (controller.mediaItemCount == 0) return@withController
            controller.seekTo(positionMs.coerceAtLeast(0L))
        }
    }

    fun seekBy(deltaMs: Long) {
        withController { controller ->
            if (controller.mediaItemCount == 0) return@withController
            controller.seekTo((controller.currentPosition + deltaMs).coerceAtLeast(0L))
        }
    }

    fun skipToNext() {
        withController { controller ->
            if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
        }
    }

    fun skipToPrevious() {
        withController { controller ->
            if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem()
        }
    }

    /**
     * Repeat is a player command rather than app state, so it is set on the player and
     * read back off it. That is what makes it survive a locked screen and a UI that is
     * torn down and rebuilt -- there is no second copy to go stale.
     */
    fun setRepeatMode(mode: RepeatMode) {
        withController { controller -> controller.setRepeatMode(mode.toPlayerMode()) }
    }

    /** Arms the timer for [minutes], or cancels it when that is zero. */
    fun setSleepTimer(minutes: Int) {
        withController { controller ->
            controller.sendCustomCommand(
                SleepTimerCommand,
                Bundle().apply { putInt(SleepTimerMinutesKey, minutes) },
            )
        }
    }

    /**
     * Runs [action] against the live controller. A click that arrives while the
     * controller is still connecting is replayed once, so the first tap after a
     * cold start is never dropped.
     */
    private fun withController(action: (MediaController) -> Unit) {
        val current = controller
        if (current != null) {
            action(current)
            return
        }
        pendingAction = action
        connect()
    }

    private fun startPositionTicker() {
        if (positionJob != null) return
        positionJob = scope.launch {
            while (isActive) {
                delay(PositionTickMillis)
                val player = controller ?: continue
                if (!player.playWhenReady) continue
                publish()
            }
        }
    }

    private fun stopPositionTicker() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun publish() {
        val current = controller
        mutableState.value = current?.toPlaybackState(current.sleepTimerSnapshot())
            ?: PlaybackState()
    }
}

private fun Player.toPlaybackState(sleepTimer: SleepTimerSnapshot): PlaybackState {
    val position = currentPosition
    val duration = duration
    return PlaybackState(
        isConnected = true,
        mediaId = currentMediaItem?.mediaId?.takeIf(String::isNotEmpty),
        title = mediaMetadata.title?.toString(),
        artworkUri = mediaMetadata.artworkUri?.toString(),
        documentUri = currentMediaItem?.localConfiguration?.uri?.toString(),
        isPlaying = isPlaying,
        playWhenReady = playWhenReady,
        isBuffering = playbackState == Player.STATE_BUFFERING,
        positionMs = position.coerceAtLeast(0L),
        durationMs = if (duration == C.TIME_UNSET) 0L else duration.coerceAtLeast(0L),
        hasNext = hasNextMediaItem(),
        hasPrevious = hasPreviousMediaItem(),
        hasQueue = mediaItemCount > 0,
        errorMessage = playerError?.message,
        repeatMode = RepeatMode.ofPlayerMode(repeatMode),
        sleepExpiresAtMs = sleepTimer.expiresAtMs,
        sleepTimerMinutes = sleepTimer.minutes,
    )
}

private fun LibraryItem.toMediaItem(match: com.example.asmrplayer.core.subtitles.TimedTextMatch?): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(documentUri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setExtras(Bundle().apply {
                match?.candidate?.let {
                    putString("desktop_lyric_uri", it.documentUri)
                    putString("desktop_lyric_format", it.format.name)
                    putString("desktop_lyric_version", "${it.sizeBytes}:${it.modifiedAtMs}")
                }
            })
            .setTitle(title)
            .setArtworkUri(artworkUri?.let(Uri::parse))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build(),
    )
    .build()

/**
 * Connects while the UI is started and releases the controller when it stops,
 * so the connection follows the visible lifecycle instead of leaking.
 */
@Composable
fun rememberPlaybackConnection(): PlaybackConnection {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val connection = remember(context) { PlaybackConnection(context) }
    DisposableEffect(connection, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> connection.connect()
                Lifecycle.Event.ON_STOP -> connection.disconnect()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            connection.disconnect()
        }
    }
    return connection
}
