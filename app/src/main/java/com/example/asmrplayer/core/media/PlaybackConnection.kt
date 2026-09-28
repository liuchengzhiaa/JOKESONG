package com.example.asmrplayer.core.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
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

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionJob: Job? = null
    private var pendingAction: ((MediaController) -> Unit)? = null

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
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
    fun playFromFolder(folderItems: List<LibraryItem>, selected: LibraryItem) {
        val plan = buildFolderQueue(folderItems, selected) ?: return
        withController { controller ->
            controller.setMediaItems(plan.items.map(LibraryItem::toMediaItem), plan.startIndex, 0L)
            controller.prepare()
            controller.play()
        }
    }

    fun togglePlayPause() {
        withController { controller ->
            when (toggleCommandFor(controller.toPlaybackState())) {
                PlaybackCommand.Play -> controller.play()
                PlaybackCommand.Pause -> controller.pause()
                PlaybackCommand.None -> Unit
            }
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
        mutableState.value = current?.toPlaybackState() ?: PlaybackState()
    }
}

private fun Player.toPlaybackState(): PlaybackState {
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
    )
}

private fun LibraryItem.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(documentUri)
    .setMediaMetadata(
        MediaMetadata.Builder()
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
