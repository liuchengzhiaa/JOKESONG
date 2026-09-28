package com.example.asmrplayer.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.example.asmrplayer.feature.subtitles.LyricGlowTimeline
import com.example.asmrplayer.feature.subtitles.LyricWipeFront

/**
 * Shared wipe renderer for the immersive scene and the single focused, currently
 * playing cover.
 *
 * The line is painted as one continuous left-to-right colour wipe: everything
 * behind the front is [accent], everything ahead of it stays in the resting
 * colour, and the front itself carries a short glowing head. The front is derived
 * from the same presentation-only grapheme timeline the caller owns, so a display
 * character is crossed rather than switched on.
 *
 * Callers own the bounded timeline and the resting colour; this composable owns
 * only the per-frame interpolation needed to keep the front moving between
 * Media3's normal position updates. Nothing here rebuilds an annotated string per
 * frame -- the fill is a clip over an already-measured layout.
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
    /**
     * How far ahead of the front the fill fades in, in pixels. Zero is the hard
     * wipe every mode but one uses; a soft edge reads as light moving across the
     * line rather than as a mask being dragged over it.
     */
    edgeSoftnessPx: Float = 0f,
    /** Strength of the halo around the sung text, 0 for none. */
    glowStrength: Float = 0f,
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

    // Held as state so the measurement survives recomposition, and read only from
    // the draw lambda below: the layout hand-off never triggers a recomposition.
    val layout = remember(text, timeline) { mutableStateOf<TextLayoutResult?>(null) }
    val front = rememberLyricWipeFront(
        timeline = timeline,
        positionMs = positionMs,
        isPlaying = isPlaying,
        motionPercent = motionPercent,
    )
    val unsungColor = restingColor.copy(alpha = restingColor.alpha * UnsungAlphaFraction)
    val shadow = textStyle.shadow

    Box(modifier = modifier) {
        Text(
            text = text,
            color = unsungColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            style = textStyle,
            onTextLayout = { layout.value = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .clearAndSetSemantics { }
                // SrcAtop below has to blend against this layer's own glyphs, not
                // against the cover art sitting behind them.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawBehind {
                    drawLyricWipe(
                        layout = layout.value,
                        front = front.value,
                        accent = accent,
                        shadow = shadow,
                        fontSize = fontSize,
                        motionPercent = motionPercent,
                        edgeSoftnessPx = edgeSoftnessPx,
                        glowStrength = glowStrength,
                    )
                },
        )
    }
}

/**
 * Interpolates the front every frame while this line is the visible, playing one.
 * Media3 remains the clock of record: its position updates restart this loop, and
 * the loop only fills the gaps between them. Paused, or with motion turned down,
 * the front simply stays where the last position update left it.
 */
@Composable
private fun rememberLyricWipeFront(
    timeline: LyricGlowTimeline,
    positionMs: Long,
    isPlaying: Boolean,
    motionPercent: Int,
): State<LyricWipeFront> {
    val front = remember(timeline) { mutableStateOf(timeline.wipeFrontAt(positionMs)) }
    LaunchedEffect(timeline, positionMs, isPlaying, motionPercent) {
        front.value = timeline.wipeFrontAt(positionMs)
        if (!isPlaying || motionPercent <= 0) return@LaunchedEffect
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            withFrameNanos { }
            front.value = timeline.wipeFrontAt(
                positionMs + (SystemClock.elapsedRealtime() - startedAt),
            )
        }
    }
    return front
}

private fun DrawScope.drawLyricWipe(
    layout: TextLayoutResult?,
    front: LyricWipeFront,
    accent: Color,
    shadow: Shadow?,
    fontSize: TextUnit,
    motionPercent: Int,
    edgeSoftnessPx: Float,
    glowStrength: Float,
) {
    if (layout == null) return
    val length = layout.layoutInput.text.length
    val fromOffset = front.fromOffset.coerceIn(0, length)
    val toOffset = front.toOffset.coerceIn(0, length)
    val fromLine = layout.getLineForOffset(fromOffset)
    val toLine = layout.getLineForOffset(toOffset)
    val fromX = layout.getHorizontalPosition(fromOffset, usePrimaryDirection = true)
    // A front whose two knots sit on different lines has reached a wrap: finish
    // this line, then start the next one from its own left edge.
    val toX = if (toLine == fromLine) {
        layout.getHorizontalPosition(toOffset, usePrimaryDirection = true)
    } else {
        layout.getLineRight(fromLine)
    }
    val frontX = fromX + (toX - fromX) * front.fraction
    val lineTop = layout.getLineTop(fromLine)
    val lineBottom = layout.getLineBottom(fromLine)

    // Whole lines above the front are already sung.
    if (fromLine > 0) {
        clipRect(0f, 0f, size.width, lineTop) {
            drawText(layout, color = accent, shadow = shadow)
        }
    }

    // The halo goes under the fill, and only over the part playback has reached: a
    // line glowing ahead of the wipe would be a picture of something that has not
    // happened. It is clipped at the front, so the halo does not bloom past it.
    if (glowStrength > 0f) {
        val fontPx = fontSize.toPx()
        clipRect(0f, lineTop, frontX, lineBottom) {
            drawText(
                layout,
                color = accent,
                shadow = Shadow(
                    color = accent.copy(alpha = OuterHaloAlpha * glowStrength),
                    offset = Offset.Zero,
                    blurRadius = fontPx * OuterHaloRadiusEm * glowStrength,
                ),
            )
            drawText(
                layout,
                color = accent,
                shadow = Shadow(
                    color = accent.copy(alpha = InnerHaloAlpha * glowStrength),
                    offset = Offset.Zero,
                    blurRadius = fontPx * InnerHaloRadiusEm * glowStrength,
                ),
            )
        }
    }

    if (edgeSoftnessPx > 0f && size.width > 0f) {
        // A soft front cannot be a clip: the fill is drawn whole and then masked with
        // a gradient, so the leading edge ramps in. DstIn multiplies the layer's own
        // alpha, which is why the mask has to run inside the offscreen layer this
        // composable already draws into.
        clipRect(0f, lineTop, size.width, lineBottom) {
            drawText(layout, color = accent, shadow = shadow)
            drawRect(
                brush = softFrontMask(frontX = frontX, softness = edgeSoftnessPx),
                topLeft = Offset(0f, lineTop),
                size = Size(size.width, lineBottom - lineTop),
                blendMode = BlendMode.DstIn,
            )
        }
    } else {
        clipRect(0f, lineTop, frontX, lineBottom) {
            drawText(layout, color = accent, shadow = shadow)
        }
    }

    val hotWidth = HotHeadWidthEm * fontSize.toPx() * (motionPercent.coerceIn(0, 100) / 100f)
    // size.width guards the gradient stops: a zero-width layout would divide by
    // zero and hand the shader NaN positions.
    if (hotWidth > 0f && frontX > 0f && size.width > 0f) {
        // SrcAtop tints the glyphs already drawn on this line and leaves the
        // transparent stretch of the gradient alone, so the head never spills
        // onto the cover behind the text.
        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    ((frontX - hotWidth) / size.width).coerceIn(0f, 1f) to Color.Transparent,
                    (frontX / size.width).coerceIn(0f, 1f) to lerp(accent, Color.White, HotHeadLighten),
                ),
                startX = 0f,
                endX = size.width,
            ),
            topLeft = Offset(0f, lineTop),
            size = Size(size.width, lineBottom - lineTop),
            blendMode = BlendMode.SrcAtop,
        )
    }
}

/**
 * The mask that gives a soft front its ramp: opaque where the line is sung, a
 * gradient across the last [softness] pixels, and clear ahead of it.
 *
 * The stops are clamped inwards so a front near either edge of the layout cannot
 * produce stops outside the gradient's own range or out of order.
 */
private fun DrawScope.softFrontMask(frontX: Float, softness: Float): Brush {
    val frontStop = (frontX / size.width).coerceIn(0f, 1f)
    val backStop = ((frontX - softness) / size.width).coerceIn(0f, frontStop)
    return Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color.White,
            backStop to Color.White,
            frontStop to Color.Transparent,
            1f to Color.Transparent,
        ),
        startX = 0f,
        endX = size.width,
    )
}

/** Share of [GlowingLyricText]'s resting alpha used by the not-yet-sung text. */
private const val UnsungAlphaFraction = 0.40f

/** The halo's two passes, as fractions of the line's own font size. */
private const val InnerHaloRadiusEm = 0.30f
private const val OuterHaloRadiusEm = 0.62f
private const val InnerHaloAlpha = 0.55f
private const val OuterHaloAlpha = 0.30f

/** Glowing head width, in ems of the line's own font size. */
private const val HotHeadWidthEm = 0.45f

/** How far the head's colour is pushed towards white. */
private const val HotHeadLighten = 0.55f
