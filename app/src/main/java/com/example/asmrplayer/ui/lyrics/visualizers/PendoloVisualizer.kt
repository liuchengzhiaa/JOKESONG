package com.example.asmrplayer.ui.lyrics.visualizers

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asmrplayer.core.subtitles.TimedTextLine
import com.example.asmrplayer.feature.subtitles.LyricRailWindow
import com.example.asmrplayer.feature.subtitles.PendoloLineItem
import com.example.asmrplayer.feature.subtitles.PendoloLineTiltFraction
import com.example.asmrplayer.feature.subtitles.PendoloTextSwingCorrectionFraction
import com.example.asmrplayer.feature.subtitles.PendoloTuning
import com.example.asmrplayer.feature.subtitles.PendoloWheel
import com.example.asmrplayer.feature.subtitles.PendoloWheelCenterXRange
import com.example.asmrplayer.feature.subtitles.PendoloWheelCenterYRange
import com.example.asmrplayer.feature.subtitles.clamped
import com.example.asmrplayer.feature.subtitles.pendoloGearRotationRadians
import com.example.asmrplayer.feature.subtitles.pendoloLineItems
import com.example.asmrplayer.feature.subtitles.pendoloWheel
import com.example.asmrplayer.feature.subtitles.pendoloWheelRotationDegrees
import com.example.asmrplayer.ui.lyrics.GlowingCurrentLineText
import com.example.asmrplayer.ui.lyrics.LyricsSceneInputs
import com.example.asmrplayer.ui.lyrics.WindowedLyricsVisualizer
import com.example.asmrplayer.ui.lyrics.visualizers.pendolo.PendoloClockwork
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The lyric escapement: a wheel whose rim carries the lines of the song.
 *
 * Nine or so lines sit on an arc struck from a hub on the left edge, the line being
 * sung level with the hub and its neighbours curving away above and below. Nothing
 * moves while a line is being sung -- the wheel is at rest within a line -- and on
 * every line change the whole thing ratchets one tooth further round, spring-driven,
 * with the clockwork underneath turning by the same amount. That shared angle is the
 * whole idea: the gear and the words are one object, so they cannot drift apart.
 *
 * Everything positional is a pure function of the stage and the tuning
 * (`PendoloGeometry`), so a seek reproduces a frame exactly rather than approximately,
 * and the spring is the only piece of state this mode owns.
 */
internal object PendoloVisualizer : WindowedLyricsVisualizer {
    override fun transition(durationMillis: Int, direction: Int): ContentTransform =
        fadeIn(tween(durationMillis)).togetherWith(fadeOut(tween(durationMillis)))

    @Composable
    override fun Window(rail: LyricRailWindow, inputs: LyricsSceneInputs) {
        if (rail.isEmpty) return
        val storedTuning by rememberUpdatedState(inputs.pendolo.clamped())
        val commitTuning by rememberUpdatedState(inputs.onPendoloTuningChange)
        var draggedTuning by remember { mutableStateOf<PendoloTuning?>(null) }
        val tuning = draggedTuning ?: storedTuning

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val current = draggedTuning ?: storedTuning
                            val xDelta = if (size.width > 0) {
                                dragAmount.x / size.width * 1_000f
                            } else {
                                0f
                            }
                            val yDelta = if (size.height > 0) {
                                dragAmount.y / size.height * 1_000f
                            } else {
                                0f
                            }
                            draggedTuning = current.copy(
                                wheelCenterXPerMille =
                                    (current.wheelCenterXPerMille + xDelta.roundToInt())
                                        .coerceIn(PendoloWheelCenterXRange),
                                wheelCenterYPerMille =
                                    (current.wheelCenterYPerMille + yDelta.roundToInt())
                                        .coerceIn(PendoloWheelCenterYRange),
                            )
                        },
                        onDragCancel = { draggedTuning = null },
                        onDragEnd = {
                            draggedTuning?.let(commitTuning)
                            draggedTuning = null
                        },
                    )
                },
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val wheel = remember(widthPx, heightPx, tuning) {
                pendoloWheel(width = widthPx, height = heightPx, tuning = tuning)
            }
            if (wheel.lyricRadius <= 0f) return@BoxWithConstraints

            val stageScale = (minOf(maxWidth, maxHeight).value / ReferenceStageSideDp)
                .coerceIn(MinStageScale, MaxStageScale)
            val textWidth = with(density) { wheel.availableTextWidth.toDp() }
            val focalFontSize = (FocalFontSp * stageScale).sp
            val neighbourFontSize = (NeighbourFontSp * stageScale).sp
            val softnessPx = with(density) {
                (focalFontSize.value * SoftEdgeEm).coerceIn(MinSoftEdgeDp, MaxSoftEdgeDp).dp.toPx()
            }
            val glow = if (tuning.lineGlow) 1f else 0f

            // The wheel's position in the song, not in this window: the clockwork has
            // to keep its place when the window moves, or every seek would rewind it.
            val anchor = rail.startIndex + rail.anchor
            val spring = remember { Animatable(anchor) }
            LaunchedEffect(anchor, tuning.tickSnappiness, inputs.motionPercent) {
                val travel = anchor - spring.value
                if (inputs.motionPercent <= 0 || abs(travel) > SnapThresholdLines) {
                    // Motion is off, or the listener jumped somewhere else in the song:
                    // the wheel is simply there. A spring told to cross a hundred lines
                    // would spend the rest of the track getting there.
                    spring.snapTo(anchor)
                } else if (travel != 0f) {
                    spring.animateTo(anchor, tickSpec(tuning.tickSnappiness))
                }
            }

            val blockHeights = measureBlockHeights(
                rail = rail,
                wheel = wheel,
                focalFontSize = focalFontSize,
                neighbourFontSize = neighbourFontSize,
            )
            val items = remember(rail, wheel, tuning, blockHeights) {
                pendoloLineItems(rail, wheel, tuning, blockHeights)
            }
            // Read inside a layer or a draw block, never in composition, so a tick in
            // flight redraws the wheel instead of rebuilding fifteen lines per frame.
            // Remembered because a position update recomposes this whole window, and a
            // fresh lambda would cost every line below a recomposition of its own.
            val railRotation = remember(spring, anchor, wheel) {
                { pendoloWheelRotationDegrees(spring.value, anchor, wheel) }
            }
            val gearRotation = remember(spring, wheel) {
                { pendoloGearRotationRadians(spring.value, wheel) }
            }

            PendoloClockwork(
                item = inputs.item,
                wheel = wheel,
                tuning = tuning,
                accent = inputs.accent,
                isPlaying = inputs.isPlaying,
                motionPercent = inputs.motionPercent,
                gearRotation = gearRotation,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer {
                        rotationZ = railRotation()
                        transformOrigin = TransformOrigin(
                            if (widthPx > 0f) wheel.centerX / widthPx else 0f,
                            if (heightPx > 0f) wheel.centerY / heightPx else 0f,
                        )
                    },
            ) {
                items.forEach { item ->
                    val line = rail.lines.getOrNull(item.offset) ?: return@forEach
                    if (item.isFocal) {
                        PendoloFocalLine(
                            item = item,
                            inputs = inputs,
                            railRotationDegrees = railRotation,
                            textWidth = textWidth,
                            fontSize = focalFontSize,
                            softnessPx = softnessPx,
                            glow = glow,
                        )
                    } else {
                        PendoloNeighbourLine(
                            item = item,
                            line = line,
                            onSeekTo = inputs.onSeekTo,
                            railRotationDegrees = railRotation,
                            textWidth = textWidth,
                            fontSize = neighbourFontSize,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The line being sung: the same wipe renderer every mode uses, hung on the wheel.
 *
 * Split from [PendoloNeighbourLine] rather than branch inside one composable, because
 * this one has to be rebuilt on every position update -- its wipe advances with the
 * clock -- and the neighbours must not be dragged along with it.
 */
@Composable
private fun PendoloFocalLine(
    item: PendoloLineItem,
    inputs: LyricsSceneInputs,
    railRotationDegrees: () -> Float,
    textWidth: Dp,
    fontSize: TextUnit,
    softnessPx: Float,
    glow: Float,
) {
    Box(
        modifier = Modifier
            .pendoloPlacement(item, railRotationDegrees)
            .widthIn(max = textWidth)
            .clickable { inputs.onSeekTo(inputs.frame.current.startMillis) },
    ) {
        GlowingCurrentLineText(
            inputs = inputs,
            fontSize = fontSize,
            lineHeight = fontSize * FocalLineHeightRatio,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Left,
            maxLines = MaxLineCount,
            overflow = TextOverflow.Ellipsis,
            edgeSoftnessPx = softnessPx,
            glowStrength = glow,
        )
    }
}

/** A line waiting its turn, or one that has already been sung. */
@Composable
private fun PendoloNeighbourLine(
    item: PendoloLineItem,
    line: TimedTextLine,
    onSeekTo: (Long) -> Unit,
    railRotationDegrees: () -> Float,
    textWidth: Dp,
    fontSize: TextUnit,
) {
    Box(
        modifier = Modifier
            .pendoloPlacement(item, railRotationDegrees)
            .widthIn(max = textWidth)
            .clickable { onSeekTo(line.startMillis) },
    ) {
        Text(
            text = line.text,
            color = Color.White.copy(alpha = NeighbourTextAlpha),
            fontSize = fontSize,
            lineHeight = fontSize * NeighbourLineHeightRatio,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Left,
            maxLines = MaxLineCount,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Where a line sits on the arc, and how far over it leans.
 *
 * The block is anchored by its *left edge*, which is the point the reference rotates
 * it about -- so a line reaches outwards from its place on the rim rather than being
 * centred on it, and the arc keeps its shape whatever the lines say.
 *
 * The line's own rotation cancels most of the rail's, leaving it tilted by a third of
 * its position on the arc. Text that turned with the wheel would be unreadable by the
 * time it reached the cull, which is exactly the text a person is looking at when they
 * are reading a line that is about to be sung.
 */
private fun Modifier.pendoloPlacement(
    item: PendoloLineItem,
    railRotationDegrees: () -> Float,
): Modifier = graphicsLayer {
    translationX = item.x
    translationY = item.y - size.height / 2f
    transformOrigin = TransformOrigin(0f, 0.5f)
    rotationZ = item.angleDegrees * PendoloLineTiltFraction -
        railRotationDegrees() * PendoloTextSwingCorrectionFraction
    scaleX = item.scale
    scaleY = item.scale
    alpha = item.alpha
}

/**
 * How tall each line's block will be, for the arc's spacing.
 *
 * The wheel spaces neighbours by their measured heights, so a long line pushes its
 * neighbours outwards instead of being overlapped by them. Measuring depends on the
 * lines and the width and not on playback, so it does not run again on every position
 * update -- and the arc keeps the same shape for as long as the same window is up.
 */
@Composable
private fun measureBlockHeights(
    rail: LyricRailWindow,
    wheel: PendoloWheel,
    focalFontSize: TextUnit,
    neighbourFontSize: TextUnit,
): List<Float> {
    val measurer = rememberTextMeasurer(cacheSize = MeasurerCacheSize)
    val maxWidth = wheel.availableTextWidth.toInt().coerceAtLeast(1)
    return remember(rail, maxWidth, focalFontSize, neighbourFontSize) {
        rail.lines.mapIndexed { offset, line ->
            val focal = rail.hasAnchor && abs(rail.distanceFromAnchor(offset)) < FocalTolerance
            val size = if (focal) focalFontSize else neighbourFontSize
            measurer.measure(
                text = AnnotatedString(line.text),
                style = TextStyle(
                    fontSize = size,
                    lineHeight = size * if (focal) FocalLineHeightRatio else NeighbourLineHeightRatio,
                    fontWeight = if (focal) FontWeight.Bold else FontWeight.Medium,
                ),
                constraints = Constraints(maxWidth = maxWidth),
                maxLines = MaxLineCount,
            ).size.height.toFloat()
        }
    }
}

/** A damped spring, tightened by the listener's own escapement setting. */
private fun tickSpec(snappiness: Float) = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = BaseTickStiffness * snappiness,
)

/** The whole line's height, as a multiple of its font size. */
private const val FocalLineHeightRatio = 1.28f
private const val NeighbourLineHeightRatio = 1.30f

private const val FocalFontSp = 28f
private const val NeighbourFontSp = 22f

/** The reference stage's shorter side, which is what its font sizes were chosen against. */
private const val ReferenceStageSideDp = 411f
private const val MinStageScale = 0.72f
private const val MaxStageScale = 1.45f

/** The soft front, as the reference computes it: a fraction of the font, clamped. */
private const val SoftEdgeEm = 0.42f
private const val MinSoftEdgeDp = 8f
private const val MaxSoftEdgeDp = 16f

private const val NeighbourTextAlpha = 0.75f
private const val MaxLineCount = 3
private const val FocalTolerance = 0.5f
private const val MeasurerCacheSize = 48

/** Beyond this many lines the wheel is placed rather than swung into place. */
private const val SnapThresholdLines = 3f

/** Stiffness at an escapement setting of 1.0, scaled by the listener's own. */
private const val BaseTickStiffness = 200f
