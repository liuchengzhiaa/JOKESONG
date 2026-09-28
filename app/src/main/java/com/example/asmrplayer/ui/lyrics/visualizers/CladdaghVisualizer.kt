package com.example.asmrplayer.ui.lyrics.visualizers

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.asmrplayer.core.subtitles.TimedTextLine
import com.example.asmrplayer.feature.subtitles.LyricRailWindow
import com.example.asmrplayer.feature.subtitles.claddaghLinePose
import com.example.asmrplayer.feature.subtitles.claddaghOrbit
import com.example.asmrplayer.ui.lyrics.GlowingCurrentLineText
import com.example.asmrplayer.ui.lyrics.LyricsSceneInputs
import com.example.asmrplayer.ui.lyrics.WindowedLyricsVisualizer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A tilted lyric loop, clean-room modelled from Folia's Claddagh behaviour.
 *
 * The cover remains a stable anchor in the independently selected backdrop while a
 * bounded span of lyric lines travels around one continuous ellipse. The active
 * line owns the front of the loop; history falls down-left, the next lines rise
 * up-right, and the shared grapheme wipe moves colour through the focal line. One
 * spring advances the entire orbit on a line boundary, so old and new lines coexist
 * during the hand-off instead of cross-fading two unrelated pages.
 */
internal object CladdaghVisualizer : WindowedLyricsVisualizer {
    override fun transition(durationMillis: Int, direction: Int): ContentTransform =
        fadeIn(tween(durationMillis)).togetherWith(fadeOut(tween(durationMillis)))

    @Composable
    override fun Window(rail: LyricRailWindow, inputs: LyricsSceneInputs) {
        if (rail.isEmpty) return
        val targetAnchor = rail.startIndex + rail.anchor
        val orbitPosition = remember { Animatable(targetAnchor) }
        LaunchedEffect(targetAnchor, inputs.motionPercent) {
            val distance = targetAnchor - orbitPosition.value
            if (inputs.motionPercent <= 0 || abs(distance) > SnapDistanceLines) {
                orbitPosition.snapTo(targetAnchor)
            } else if (distance != 0f) {
                orbitPosition.animateTo(
                    targetValue = targetAnchor,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow +
                            (Spring.StiffnessMedium - Spring.StiffnessLow) *
                            (inputs.motionPercent.coerceIn(0, 100) / 100f),
                    ),
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            CladdaghGuide(
                accent = inputs.accent,
                modifier = Modifier.fillMaxSize(),
            )

            val focalOffset = if (rail.hasAnchor) rail.anchor.roundToInt() else -1
            val drawOrder = rail.lines.indices.sortedByDescending { offset ->
                abs((rail.startIndex + offset) - targetAnchor)
            }
            drawOrder.forEach { offset ->
                val line = rail.lines[offset]
                val documentIndex = rail.startIndex + offset
                val isFocal = offset == focalOffset
                val restingPose = remember(documentIndex, targetAnchor, widthPx, heightPx) {
                    claddaghLinePose(documentIndex - targetAnchor, widthPx, heightPx)
                }
                CladdaghLyric(
                    line = line,
                    isFocal = isFocal,
                    isPast = documentIndex < targetAnchor,
                    inputs = inputs,
                    blurRadiusDp = restingPose.blurRadiusDp,
                    pose = {
                        claddaghLinePose(
                            distanceFromFocal = documentIndex - orbitPosition.value,
                            width = widthPx,
                            height = heightPx,
                        )
                    },
                )
            }
        }
    }
}

/** The mechanical-pencil orbit underneath the words, cheap enough for one Canvas. */
@Composable
private fun CladdaghGuide(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val orbit = claddaghOrbit(size.width, size.height)
        val pivot = androidx.compose.ui.geometry.Offset(orbit.centerX, orbit.centerY)
        rotate(orbit.rotationDegrees, pivot = pivot) {
            listOf(1f, 0.92f, 0.76f).forEachIndexed { index, ratio ->
                drawOval(
                    color = accent.copy(alpha = 0.17f - index * 0.035f),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        orbit.centerX - orbit.radiusX * ratio,
                        orbit.centerY - orbit.radiusY * ratio,
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        orbit.radiusX * 2f * ratio,
                        orbit.radiusY * 2f * ratio,
                    ),
                    style = Stroke(width = if (index == 0) 1.4.dp.toPx() else 0.7.dp.toPx()),
                )
            }
            repeat(14) { spoke ->
                val theta = (2.0 * PI * spoke / 14.0).toFloat()
                val edge = androidx.compose.ui.geometry.Offset(
                    orbit.centerX + orbit.radiusX * cos(theta),
                    orbit.centerY + orbit.radiusY * sin(theta),
                )
                drawLine(
                    color = accent.copy(alpha = if (spoke % 3 == 0) 0.11f else 0.045f),
                    start = pivot,
                    end = edge,
                    strokeWidth = 0.7.dp.toPx(),
                )
            }
            drawArc(
                color = accent.copy(alpha = 0.48f),
                startAngle = 196f,
                sweepAngle = 86f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    orbit.centerX - orbit.radiusX,
                    orbit.centerY - orbit.radiusY,
                ),
                size = androidx.compose.ui.geometry.Size(orbit.radiusX * 2f, orbit.radiusY * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun CladdaghLyric(
    line: TimedTextLine,
    isFocal: Boolean,
    isPast: Boolean,
    inputs: LyricsSceneInputs,
    blurRadiusDp: Float,
    pose: () -> com.example.asmrplayer.feature.subtitles.CladdaghLinePose,
) {
    val lineModifier = Modifier
        .fillMaxWidth(0.68f)
        .height(if (inputs.compact) 72.dp else 98.dp)
        .graphicsLayer {
            val frame = pose()
            translationX = frame.centerX - size.width / 2f
            translationY = frame.centerY - size.height / 2f
            rotationZ = frame.rotationDegrees
            scaleX = frame.scale
            scaleY = frame.scale
            alpha = frame.alpha
        }
        .blur(blurRadiusDp.dp)
        .clickable { inputs.onSeekTo(line.startMillis) }

    Box(
        modifier = lineModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (isFocal) {
            GlowingCurrentLineText(
                inputs = inputs,
                fontSize = (if (inputs.compact) 24 else 29).sp,
                lineHeight = (if (inputs.compact) 30 else 36).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                edgeSoftnessPx = 13f,
                glowStrength = 0.72f,
            )
        } else {
            Text(
                text = line.text,
                color = if (isPast) {
                    inputs.accent.copy(alpha = 0.82f)
                } else {
                    Color.White.copy(alpha = 0.78f)
                },
                fontSize = (if (inputs.compact) 14 else 17).sp,
                lineHeight = (if (inputs.compact) 19 else 23).sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val SnapDistanceLines = 4f
