package com.example.asmrplayer.ui.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asmrplayer.R
import com.example.asmrplayer.core.media.PlaybackCommand
import com.example.asmrplayer.core.media.PlaybackState
import com.example.asmrplayer.core.media.toggleCommandFor
import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.feature.player.formatPlaybackTime
import com.example.asmrplayer.feature.subtitles.LuminousTuning
import com.example.asmrplayer.feature.subtitles.PendoloTuning
import com.example.asmrplayer.feature.subtitles.LyricsVisualizerBackground
import com.example.asmrplayer.feature.subtitles.LyricsVisualizerMode
import com.example.asmrplayer.feature.subtitles.immersiveLyricsFrameAt
import com.example.asmrplayer.feature.subtitles.lyricRailWindow
import com.example.asmrplayer.ui.FoliaSeekBar
import kotlin.math.roundToInt

/**
 * The line stage: whichever visualizer is selected, driven by whichever line
 * playback has reached.
 *
 * The stage owns the two things every mode would otherwise have to implement again
 * -- the compact-layout decision and the line-change boundary -- and hands the mode
 * a finished [LyricsSceneInputs]. A visualizer therefore cannot disagree with the
 * others about the clock, and none of them can start its own.
 */
@Composable
internal fun LyricsLineStage(
    visualizer: LyricsVisualizer,
    document: TimedTextDocument,
    inputs: LyricsSceneInputs,
    modifier: Modifier = Modifier,
) {
    val duration = (TransitionMillis * inputs.motionPercent.coerceIn(0, 100) / 100f).roundToInt()
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val compact = maxHeight < CompactHeight
        val stageInputs = inputs.copy(compact = compact)
        if (visualizer is ProgramLyricsVisualizer) {
            // A compiled PV owns shot and paragraph boundaries. Wrapping it in the
            // ordinary line transition would tear down its outgoing scene exactly
            // when the incoming shot is supposed to overlap it.
            visualizer.Program(document = document, inputs = stageInputs)
            return@BoxWithConstraints
        }
        if (visualizer is WindowedLyricsVisualizer) {
            // A rail lays every visible line out for itself and moves them as one
            // object, so it deliberately gets neither the per-line transition below
            // nor the compact height: its geometry is a function of the whole stage,
            // which the host hands it unpadded for the same reason.
            val rail = remember(document, inputs.positionMs) {
                document.lyricRailWindow(inputs.positionMs)
            }
            visualizer.Window(rail = rail, inputs = stageInputs)
            return@BoxWithConstraints
        }
        if (duration == 0) {
            // Motion is turned down: the line is simply in place, with no boundary
            // animation to sit through.
            visualizer.Content(stageInputs)
        } else {
            AnimatedContent(
                targetState = inputs.frame.currentIndex,
                transitionSpec = {
                    visualizer.transition(
                        durationMillis = duration,
                        direction = if (targetState >= initialState) 1 else -1,
                    )
                },
                label = "immersive lyric line",
            ) { targetIndex ->
                val targetFrame = remember(document, targetIndex, inputs.positionMs) {
                    document.immersiveLyricsFrameAt(targetIndex, inputs.positionMs)
                }
                if (targetFrame != null) {
                    visualizer.Content(stageInputs.copy(frame = targetFrame))
                }
            }
        }
    }
}

@Composable
internal fun ImmersiveLyricsHeader(
    settingsOpen: Boolean,
    onSettingsOpenChange: (Boolean) -> Unit,
    visualizerMode: LyricsVisualizerMode,
    background: LyricsVisualizerBackground,
    backgroundFollowsMode: Boolean,
    motionPercent: Int,
    wordGlowMode: com.example.asmrplayer.feature.subtitles.LyricsWordGlowMode,
    luminousTuning: LuminousTuning,
    pendoloTuning: PendoloTuning,
    temperaTuning: com.example.asmrplayer.feature.subtitles.TemperaTuning,
    onVisualizerModeChange: (LyricsVisualizerMode) -> Unit,
    onBackgroundChange: (LyricsVisualizerBackground?) -> Unit,
    onMotionPercentChange: (Int) -> Unit,
    onWordGlowModeChange: (com.example.asmrplayer.feature.subtitles.LyricsWordGlowMode) -> Unit,
    onLuminousTuningPreview: (LuminousTuning?) -> Unit,
    onPendoloTuningPreview: (PendoloTuning?) -> Unit,
    onTemperaTuningPreview: (com.example.asmrplayer.feature.subtitles.TemperaTuning?) -> Unit,
    onLuminousTuningChange: (LuminousTuning) -> Unit,
    onPendoloTuningChange: (PendoloTuning) -> Unit,
    onTemperaTuningChange: (com.example.asmrplayer.feature.subtitles.TemperaTuning) -> Unit,
    accent: Color,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (settingsOpen) {
        LyricsSettingsPanel(
            visualizerMode = visualizerMode,
            background = background,
            backgroundFollowsMode = backgroundFollowsMode,
            motionPercent = motionPercent,
            wordGlowMode = wordGlowMode,
            luminous = luminousTuning,
            pendolo = pendoloTuning,
            tempera = temperaTuning,
            onVisualizerModeChange = onVisualizerModeChange,
            onBackgroundChange = onBackgroundChange,
            onMotionPercentChange = onMotionPercentChange,
            onWordGlowModeChange = onWordGlowModeChange,
            onLuminousPreview = { onLuminousTuningPreview(it) },
            onPendoloPreview = { onPendoloTuningPreview(it) },
            onTemperaPreview = { onTemperaTuningPreview(it) },
            onLuminousChange = onLuminousTuningChange,
            onPendoloChange = onPendoloTuningChange,
            onTemperaChange = {
                onTemperaTuningPreview(null)
                onTemperaTuningChange(it)
            },
            onDismiss = {
                onSettingsOpenChange(false)
                // A sheet can also be dismissed mid-gesture, and a preview that
                // outlived its drag would show a value nobody wrote.
                onLuminousTuningPreview(null)
                onPendoloTuningPreview(null)
                onTemperaTuningPreview(null)
            },
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        IconButton(
            modifier = Modifier.align(Alignment.TopEnd),
            onClick = {
                onInteraction()
                onSettingsOpenChange(true)
            },
        ) {
            Icon(
                Icons.Default.Tune,
                stringResource(R.string.lyrics_settings_open),
                tint = lerp(Color.White, accent, 0.18f).copy(alpha = 0.94f),
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

/**
 * A 48dp touch target whose visible glass is deliberately smaller.
 *
 * The old chrome painted a solid 44dp black disc for every action. On a phone that
 * read as four unrelated Material buttons pasted over a cinematic scene. This keeps
 * the accessible target while making the visible control a palette-coloured 34dp
 * lens, so the artwork rather than the chrome remains the largest shape.
 */
@Composable
private fun SceneChromeIconButton(
    icon: ImageVector,
    description: String,
    accent: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val contentAlpha = if (enabled) 0.92f else 0.30f
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(sceneGlassColour(accent), CircleShape)
                .border(0.7.dp, accent.copy(alpha = 0.34f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                description,
                tint = Color.White.copy(alpha = contentAlpha),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

internal fun LyricsVisualizerMode.labelResource(): Int = when (this) {
    LyricsVisualizerMode.Luminous -> R.string.lyrics_visualizer_luminous
    LyricsVisualizerMode.Pendolo -> R.string.lyrics_visualizer_pendolo
    LyricsVisualizerMode.Claddagh -> R.string.lyrics_visualizer_claddagh
    LyricsVisualizerMode.Tempera -> R.string.lyrics_visualizer_tempera
}

internal fun LyricsVisualizerBackground.labelResource(): Int = when (this) {
    LyricsVisualizerBackground.BlurredCover -> R.string.lyrics_background_blurred_cover
    LyricsVisualizerBackground.SharpPoster -> R.string.lyrics_background_sharp_poster
    LyricsVisualizerBackground.DarkSpotlight -> R.string.lyrics_background_dark_spotlight
    LyricsVisualizerBackground.ColourField -> R.string.lyrics_background_colour_field
    LyricsVisualizerBackground.Lattice -> R.string.lyrics_background_lattice
    LyricsVisualizerBackground.Drift -> R.string.lyrics_background_drift
}

@Composable
internal fun ImmersiveLyricsTransport(
    playbackState: PlaybackState,
    accent: Color,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(sceneGlassColour(accent), RoundedCornerShape(24.dp))
                .border(0.7.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatPlaybackTime(playbackState.positionMs),
                    color = Color.White.copy(alpha = 0.66f),
                    fontSize = 10.sp,
                )
                FoliaSeekBar(
                    playbackState = playbackState,
                    onSeekTo = {
                        onInteraction()
                        onSeekTo(it)
                    },
                    activeColor = lerp(accent, Color.White, 0.42f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(22.dp),
                )
                Text(
                    formatPlaybackTime(playbackState.durationMs),
                    color = Color.White.copy(alpha = 0.66f),
                    fontSize = 10.sp,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SceneChromeIconButton(
                    icon = Icons.Default.SkipPrevious,
                    description = stringResource(R.string.previous_track),
                    accent = accent,
                    enabled = onPrevious != null,
                    onClick = {
                        onInteraction()
                        onPrevious?.invoke()
                    },
                )
                SceneChromeIconButton(
                    icon = if (playbackState.playWhenReady) {
                        Icons.Default.Pause
                    } else {
                        Icons.Default.PlayArrow
                    },
                    description = stringResource(
                        if (playbackState.playWhenReady) R.string.pause else R.string.play,
                    ),
                    accent = lerp(accent, Color.White, 0.12f),
                    enabled = toggleCommandFor(playbackState) != PlaybackCommand.None,
                    onClick = {
                        onInteraction()
                        onTogglePlayPause()
                    },
                )
                SceneChromeIconButton(
                    icon = Icons.Default.SkipNext,
                    description = stringResource(R.string.next_track),
                    accent = accent,
                    enabled = onNext != null,
                    onClick = {
                        onInteraction()
                        onNext?.invoke()
                    },
                )
            }
        }
    }
}

/** A palette-tinted fallback for backdrop blur, which Compose cannot sample safely here. */
private fun sceneGlassColour(accent: Color): Color =
    lerp(Color(0xCC090A0D), accent.copy(alpha = 1f), 0.10f).copy(alpha = 0.58f)

/** The full line-change duration at a motion setting of 100%. */
private const val TransitionMillis = 380

/** Below this height the five-line window no longer fits and is trimmed. */
private val CompactHeight = 300.dp
