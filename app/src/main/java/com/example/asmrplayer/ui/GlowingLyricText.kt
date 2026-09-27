package com.example.asmrplayer.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.example.asmrplayer.feature.subtitles.LyricGlowSnapshot
import com.example.asmrplayer.feature.subtitles.LyricGlowTimeline
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Shared grapheme-glow renderer for the immersive scene and the single focused,
 * currently playing cover. Callers own the bounded timeline; this composable owns
 * only the next-boundary wake-up needed to keep the light moving between Media3's
 * normal position updates.
 */
@Composable
internal fun GlowingLyricText(
    text: String,
    timeline: LyricGlowTimeline?,
    positionMs: Long,
    isPlaying: Boolean,
    accent: Color,
    motionPercent: Int,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    textAlign: TextAlign,
    maxLines: Int,
    overflow: TextOverflow,
    modifier: Modifier = Modifier,
    restingColor: Color = Color.White,
    textStyle: TextStyle = TextStyle.Default,
) {
    if (timeline == null) {
        Text(
            text = text,
            color = restingColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            style = textStyle,
            modifier = modifier,
        )
        return
    }

    val snapshot = rememberLyricGlowSnapshot(
        timeline = timeline,
        positionMs = positionMs,
        isPlaying = isPlaying,
    )
    val body = remember(text, timeline, snapshot, accent, restingColor) {
        glowBodyText(text, timeline, snapshot, accent, restingColor)
    }
    val glow = remember(text, timeline, snapshot, accent) {
        glowShadowText(text, timeline, snapshot, accent)
    }
    val glowAlpha = remember(timeline) { Animatable(0f) }
    LaunchedEffect(timeline, snapshot.activeIndex, isPlaying, motionPercent) {
        val active = timeline.segments.getOrNull(snapshot.activeIndex)
        if (active == null) {
            glowAlpha.snapTo(0f)
        } else if (!isPlaying || motionPercent <= 0) {
            glowAlpha.snapTo(0.72f)
        } else {
            val duration = (active.endMillis - active.startMillis)
                .coerceIn(90L, 900L)
                .toInt()
            glowAlpha.snapTo(0.24f)
            glowAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween((duration * 0.30f).roundToInt().coerceAtLeast(30)),
            )
            glowAlpha.animateTo(
                targetValue = 0.34f,
                animationSpec = tween((duration * 0.70f).roundToInt().coerceAtLeast(50)),
            )
        }
    }

    Box(modifier = modifier) {
        Text(
            text = body,
            color = restingColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            style = textStyle,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = glow,
            color = Color.Transparent,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            // The body already owns the static readability shadow. Keeping it off
            // this transparent layer prevents that shadow from being painted twice;
            // active graphemes still receive their SpanStyle glow below.
            style = textStyle.copy(shadow = null),
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { }
                .graphicsLayer { alpha = glowAlpha.value },
        )
    }
}

/**
 * Interpolates only until the next grapheme boundary, not on every display frame.
 * Media3 remains the clock of record and its 500 ms state updates restart this
 * bounded scheduler. Only a visible glowing line owns this local state.
 */
@Composable
private fun rememberLyricGlowSnapshot(
    timeline: LyricGlowTimeline,
    positionMs: Long,
    isPlaying: Boolean,
): LyricGlowSnapshot {
    var snapshot by remember(timeline) { mutableStateOf(timeline.snapshotAt(positionMs)) }
    LaunchedEffect(timeline, positionMs, isPlaying) {
        snapshot = timeline.snapshotAt(positionMs)
        if (!isPlaying) return@LaunchedEffect
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            val estimatedPosition = positionMs + (SystemClock.elapsedRealtime() - startedAt)
            snapshot = timeline.snapshotAt(estimatedPosition)
            val nextBoundary = timeline.nextBoundaryAfter(estimatedPosition) ?: break
            delay((nextBoundary - estimatedPosition).coerceIn(16L, 500L))
        }
    }
    return snapshot
}

private fun glowBodyText(
    text: String,
    timeline: LyricGlowTimeline,
    snapshot: LyricGlowSnapshot,
    accent: Color,
    restingColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    if (length == 0) return@buildAnnotatedString
    addStyle(SpanStyle(color = restingColor.copy(alpha = restingColor.alpha * 0.40f)), 0, length)
    timeline.segments.forEachIndexed { index, segment ->
        val colour = when {
            index < snapshot.completedCount -> restingColor.copy(alpha = restingColor.alpha * 0.92f)
            index == snapshot.activeIndex -> accent
            else -> return@forEachIndexed
        }
        addStyle(
            SpanStyle(color = colour),
            segment.startOffset.coerceIn(0, length),
            segment.endOffset.coerceIn(0, length),
        )
    }
}

/** Transparent glyphs carrying only a hot core and a two-grapheme afterglow. */
private fun glowShadowText(
    text: String,
    timeline: LyricGlowTimeline,
    snapshot: LyricGlowSnapshot,
    accent: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    if (length == 0 || snapshot.activeIndex < 0) return@buildAnnotatedString
    addStyle(SpanStyle(color = Color.Transparent), 0, length)
    for (distance in 2 downTo 0) {
        val index = snapshot.activeIndex - distance
        val segment = timeline.segments.getOrNull(index) ?: continue
        val alpha = when (distance) {
            0 -> 0.96f
            1 -> 0.54f
            else -> 0.26f
        }
        val radius = when (distance) {
            0 -> 20f
            1 -> 13f
            else -> 8f
        }
        addStyle(
            SpanStyle(
                color = Color.Transparent,
                shadow = Shadow(
                    color = accent.copy(alpha = alpha),
                    blurRadius = radius,
                ),
            ),
            segment.startOffset.coerceIn(0, length),
            segment.endOffset.coerceIn(0, length),
        )
    }
}
