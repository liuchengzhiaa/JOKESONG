package com.example.asmrplayer.ui.lyrics

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.asmrplayer.R
import com.example.asmrplayer.core.artwork.CoverAccentLoader
import com.example.asmrplayer.core.media.PlaybackState
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.feature.subtitles.LuminousTuning
import com.example.asmrplayer.feature.subtitles.PendoloTuning
import com.example.asmrplayer.feature.subtitles.TemperaTuning
import com.example.asmrplayer.feature.subtitles.LyricsVisualizerMode
import com.example.asmrplayer.feature.subtitles.LyricsWordGlowMode
import com.example.asmrplayer.feature.subtitles.immersiveLyricsFrame
import com.example.asmrplayer.feature.subtitles.resolveLyricsBackground
import com.example.asmrplayer.ui.PlayerBackground
import com.example.asmrplayer.ui.theme.artworkAccent
import kotlinx.coroutines.delay

/**
 * One full-screen timed-lyric scene.
 *
 * The host owns everything that is not the lyric's look: the clock, the parsed
 * document, the extracted palette and the transport. It then resolves the two
 * registries -- a visualizer for the lines and, independently, a background for the
 * artwork -- and mounts each. Adding a presentation means adding a registry entry,
 * not editing this file.
 *
 * The scene remains a view over the existing Media3 clock and parsed document: no
 * player or parser lives here. A renderer may request the bounded scene clock from
 * [rememberLyricsScenePosition] while it is composed and playing; the host never starts a
 * second playback clock.
 */
@Composable
internal fun ImmersiveTimedLyricsScene(
    item: LibraryItem,
    document: TimedTextDocument,
    playbackState: PlaybackState,
    motionPercent: Int,
    visualizerMode: LyricsVisualizerMode,
    wordGlowMode: LyricsWordGlowMode,
    backgroundStoredOrdinal: Int?,
    luminousTuning: LuminousTuning,
    pendoloTuning: PendoloTuning,
    temperaTuning: TemperaTuning,
    onVisualizerModeChange: (LyricsVisualizerMode) -> Unit,
    onBackgroundChange: (Int?) -> Unit,
    onMotionPercentChange: (Int) -> Unit,
    onWordGlowModeChange: (LyricsWordGlowMode) -> Unit,
    onLuminousTuningChange: (LuminousTuning) -> Unit,
    onPendoloTuningChange: (PendoloTuning) -> Unit,
    onTemperaTuningChange: (TemperaTuning) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ImmersiveSystemBars()

    // A parameter being dragged is drawn immediately but written only on release, so
    // the sheet commits to storage while the scene follows a value that may never be
    // persisted. Null means "nothing is being dragged": the stored tuning is the truth.
    var luminousPreview by remember { mutableStateOf<LuminousTuning?>(null) }
    var pendoloPreview by remember { mutableStateOf<PendoloTuning?>(null) }
    var temperaPreview by remember { mutableStateOf<TemperaTuning?>(null) }
    val luminous = luminousPreview ?: luminousTuning
    val pendolo = pendoloPreview ?: pendoloTuning
    val tempera = temperaPreview ?: temperaTuning
    val context = LocalContext.current
    var extractedPalette by remember(item.artworkUri) { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(item.artworkUri) {
        extractedPalette = CoverAccentLoader.resolve(context, item.artworkUri)?.paletteArgbs.orEmpty()
    }
    val fallbackAccent = artworkAccent(item.paletteSeed)
    val palette = remember(extractedPalette, fallbackAccent) {
        extractedPalette.map(::Color).ifEmpty { listOf(fallbackAccent) }
    }
    val accent = lerp(palette.first(), Color.White, 0.28f)
    val positionMs = playbackState.positionMs
    val frame = remember(document, positionMs) { document.immersiveLyricsFrame(positionMs) }
    val background = resolveLyricsBackground(backgroundStoredOrdinal, visualizerMode)
    var chromeVisible by remember { mutableStateOf(true) }
    var chromeActivity by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    val revealChrome = {
        chromeVisible = true
        chromeActivity += 1
    }
    // Full-screen artwork is the resting state. Controls stay present while paused,
    // then retreat after the last interaction while playback is moving. A hidden
    // scene consumes one tap only to reveal them, so an accidental tap never seeks.
    LaunchedEffect(chromeActivity, playbackState.isPlaying, visualizerMode, settingsOpen) {
        if (!playbackState.isPlaying || settingsOpen) {
            chromeVisible = true
            return@LaunchedEffect
        }
        delay(ChromeAutoHideMillis)
        chromeVisible = false
    }
    val chromeMotionMillis = if (motionPercent <= 0) 0 else ChromeMotionMillis

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PlayerBackground),
    ) {
        background.backdrop().Backdrop(
            LyricsBackgroundInputs(
                item = item,
                palette = palette,
                lineIndex = frame?.currentIndex ?: 0,
                motionPercent = motionPercent,
            ),
        )

        if (frame == null) {
            // The first line has not started. Keeping it dim and unhighlighted is a
            // truthful preview rather than declaring that playback has reached it.
            document.lines.firstOrNull()?.let { first ->
                Text(
                    text = first.text,
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 34.dp),
                )
            }
        } else {
            val visualizer = visualizerMode.visualizer()
            LyricsLineStage(
                visualizer = visualizer,
                document = document,
                inputs = LyricsSceneInputs(
                    item = item,
                    frame = frame,
                    format = document.format,
                    positionMs = positionMs,
                    isPlaying = playbackState.isPlaying,
                    accent = accent,
                    palette = palette,
                    compact = false,
                    wordGlowMode = wordGlowMode,
                    motionPercent = motionPercent,
                    onSeekTo = onSeekTo,
                    luminous = luminous,
                    pendolo = pendolo,
                    onPendoloTuningChange = onPendoloTuningChange,
                    tempera = tempera,
                ),
                // Chrome is an overlay, not layout. Every renderer gets the complete
                // stage and can use the same coordinates whether controls are visible
                // or have faded away.
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(chromeMotionMillis)) +
                slideInVertically(tween(chromeMotionMillis)) { -it / 4 } +
                scaleIn(tween(chromeMotionMillis), initialScale = 0.97f),
            exit = fadeOut(tween(chromeMotionMillis)) +
                slideOutVertically(tween(chromeMotionMillis)) { -it / 5 } +
                scaleOut(tween(chromeMotionMillis), targetScale = 0.98f),
            label = "immersive lyric header",
        ) {
            ImmersiveLyricsHeader(
                settingsOpen = settingsOpen,
                onSettingsOpenChange = { open ->
                    settingsOpen = open
                    revealChrome()
                },
                visualizerMode = visualizerMode,
                background = background,
                backgroundFollowsMode = backgroundStoredOrdinal == null,
                luminousTuning = luminous,
                pendoloTuning = pendolo,
                temperaTuning = tempera,
                onVisualizerModeChange = onVisualizerModeChange,
                // Storage owns no UI type: the picker speaks in backgrounds, the
                // settings layer speaks in ordinals.
                onBackgroundChange = { choice -> onBackgroundChange(choice?.ordinal) },
                motionPercent = motionPercent,
                wordGlowMode = wordGlowMode,
                onMotionPercentChange = onMotionPercentChange,
                onWordGlowModeChange = onWordGlowModeChange,
                onLuminousTuningPreview = { luminousPreview = it },
                onPendoloTuningPreview = { pendoloPreview = it },
                onTemperaTuningPreview = { temperaPreview = it },
                onLuminousTuningChange = onLuminousTuningChange,
                onPendoloTuningChange = onPendoloTuningChange,
                onTemperaTuningChange = onTemperaTuningChange,
                accent = accent,
                onInteraction = revealChrome,
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(chromeMotionMillis)) +
                slideInVertically(tween(chromeMotionMillis)) { it / 3 } +
                scaleIn(tween(chromeMotionMillis), initialScale = 0.96f),
            exit = fadeOut(tween(chromeMotionMillis)) +
                slideOutVertically(tween(chromeMotionMillis)) { it / 3 } +
                scaleOut(tween(chromeMotionMillis), targetScale = 0.97f),
            label = "immersive lyric transport",
        ) {
            ImmersiveLyricsTransport(
                playbackState = playbackState,
                accent = accent,
                onTogglePlayPause = onTogglePlayPause,
                onSeekTo = onSeekTo,
                onPrevious = onPrevious,
                onNext = onNext,
                onInteraction = revealChrome,
            )
        }

        if (!chromeVisible) {
            val showControls = stringResource(R.string.lyrics_show_controls)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = showControls }
                    .clickable(onClick = revealChrome),
            )
        }
    }
}

/**
 * Makes this one route genuinely edge-to-edge and restores normal app chrome when
 * it leaves composition. System bars remain reachable with Android's transient
 * edge swipe, matching a media viewer instead of trapping the device in kiosk UI.
 */
@Composable
private fun ImmersiveSystemBars() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        if (activity == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val previousBehaviour = controller.systemBarsBehavior
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previousBehaviour
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val ChromeAutoHideMillis = 3_200L
private const val ChromeMotionMillis = 180
