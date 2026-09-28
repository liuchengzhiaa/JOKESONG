package com.example.asmrplayer.ui.lyrics.visualizers

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asmrplayer.feature.subtitles.LuminousTuning
import com.example.asmrplayer.feature.subtitles.LuminousWordScatter
import com.example.asmrplayer.feature.subtitles.LuminousWordSlot
import com.example.asmrplayer.feature.subtitles.LyricWord
import com.example.asmrplayer.feature.subtitles.LyricWordPresentation
import com.example.asmrplayer.feature.subtitles.LyricWordState
import com.example.asmrplayer.feature.subtitles.LyricWordTimeline
import com.example.asmrplayer.feature.subtitles.clamped
import com.example.asmrplayer.feature.subtitles.luminousBreathOffset
import com.example.asmrplayer.feature.subtitles.luminousWordFlow
import com.example.asmrplayer.feature.subtitles.luminousWordScatter
import com.example.asmrplayer.feature.subtitles.lyricWordTimeline
import com.example.asmrplayer.feature.subtitles.motionFraction
import com.example.asmrplayer.ui.lyrics.ContextLyric
import com.example.asmrplayer.ui.lyrics.GlowingCurrentLineText
import com.example.asmrplayer.ui.lyrics.LyricsSceneInputs
import com.example.asmrplayer.ui.lyrics.LyricsVisualizer
import com.example.asmrplayer.ui.lyrics.lyricsSceneIsRunning
import com.example.asmrplayer.ui.lyrics.rememberLyricsSceneElapsedSeconds
import com.example.asmrplayer.ui.lyrics.rememberLyricsScenePosition
import kotlin.math.min

/**
 * One line at a time, with its words thrown into a cloud.
 *
 * The line being sung is the only line on the screen. Its words sit in a wrapped
 * block, and each one is thrown from its place, tilted and resized by a hash of the
 * line's own start time -- so the line assembles as it is sung and the same line
 * always assembles the same way, seek back to it or come round to it again.
 *
 * A word has three poses. Waiting, it is out at its thrown position, smaller and dim,
 * as if it were not there yet. Sung, it settles into its place in the block,
 * grows, sharpens, and the accent colour crosses it grapheme by grapheme with a halo
 * behind it. Passed, it eases a little way back out, gets a touch more tilt, and
 * returns to the plain colour so the eye is left with the line rather than with the
 * last word.
 *
 * The whole block breathes -- slowly up, back, a shorter dip the other way -- on the
 * scene's own clock, which is the only part of this that runs while a word is not
 * changing. Motion at zero stops the breath and the throw together, leaving a plain
 * wrapped line, which is what the setting means everywhere else in the app.
 */
internal object LuminousVisualizer : LyricsVisualizer {
    override fun transition(durationMillis: Int, direction: Int): ContentTransform =
        (fadeIn(tween(durationMillis)) + scaleIn(tween(durationMillis), initialScale = 0.94f))
            .togetherWith(
                fadeOut(tween(durationMillis)) +
                    scaleOut(tween(durationMillis), targetScale = 1.04f),
            )

    @Composable
    override fun Content(inputs: LyricsSceneInputs) {
        val tuning = inputs.luminous.clamped()
        val line = inputs.frame.current
        val timeline = remember(line, inputs.frame.next?.startMillis, inputs.format, inputs.wordGlowMode) {
            line.lyricWordTimeline(
                format = inputs.format,
                nextLineStartMillis = inputs.frame.next?.startMillis,
                mode = inputs.wordGlowMode,
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            if (timeline == null) {
                // Word glow turned off, or a line nothing can be timed from. The
                // shared wipe still says what is being sung, which is better than a
                // mode that draws nothing because its own projection came back empty.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlowingCurrentLineText(
                        inputs = inputs,
                        fontSize = CloudFontSp.sp,
                        lineHeight = (CloudFontSp * CloudLineHeightRatio).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = MaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                WordCloud(timeline = timeline, inputs = inputs, tuning = tuning)
            }
            if (!inputs.compact) {
                // The line after this one, where the reference puts it: out of the way
                // at the bottom, so a listener can see what is coming without it
                // competing with the line they are reading.
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    ContextLyric(
                        line = inputs.frame.next,
                        alpha = CueAlpha,
                        fontSize = CueFontSp,
                        height = CueHeightDp,
                        onSeekTo = inputs.onSeekTo,
                    )
                }
            }
        }
    }
}

/**
 * The cloud itself: measured words, their places, and their poses.
 *
 * Everything that depends only on the line -- measurement, the wrapped block, where
 * each word would sit, how it is thrown -- is remembered against the line, so a
 * position update re-derives nothing but the poses. That is the one part that has to
 * change as the song moves, and it is a handful of arithmetic per word.
 */
@Composable
private fun WordCloud(
    timeline: LyricWordTimeline,
    inputs: LyricsSceneInputs,
    tuning: LuminousTuning,
) {
    val running = lyricsSceneIsRunning(inputs.isPlaying, inputs.motionPercent)
    val elapsed = rememberLyricsSceneElapsedSeconds(running)
    val position = rememberLyricsScenePosition(inputs.positionMs, running)
    val motion = motionFraction(inputs.motionPercent)
    val measurer = rememberTextMeasurer(cacheSize = MeasurerCacheSize)

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val availableWidth = with(density) { maxWidth.toPx() }
        val availableHeight = with(density) { maxHeight.toPx() }
        val stageScale = (min(maxWidth.value, maxHeight.value) / ReferenceStageSideDp)
            .coerceIn(MinStageScale, MaxStageScale)
        val fontSize = (CloudFontSp * stageScale).sp
        val lineHeight = fontSize.value * CloudLineHeightRatio

        val layouts = remember(timeline, fontSize) {
            val style = TextStyle(
                fontSize = fontSize,
                lineHeight = lineHeight.sp,
                fontWeight = FontWeight.Bold,
            )
            timeline.words.map { word ->
                measurer.measure(
                    text = AnnotatedString(word.text),
                    style = style,
                    constraints = Constraints(),
                )
            }
        }
        val spacing = with(density) { (BaseWordGapDp * tuning.wordSpacingPercent / 100f).dp.toPx() }
        val lineHeightPx = with(density) { lineHeight.sp.toPx() }
        val cloudWidth = availableWidth * CloudWidthFraction
        val flow = remember(layouts, cloudWidth, spacing, lineHeightPx) {
            luminousWordFlow(
                wordWidths = layouts.map { it.size.width.toFloat() },
                maxWidth = cloudWidth,
                spacing = spacing,
                lineHeight = lineHeightPx,
            )
        }
        // A line with a great many words is scaled down rather than clipped, so the
        // block stays whole and the mode keeps its promise that the words are there.
        val cloudScale = if (flow.height > availableHeight * CloudHeightFraction) {
            availableHeight * CloudHeightFraction / flow.height
        } else {
            1f
        }
        val seed = timeline.words.firstOrNull()?.let { it.startMillis } ?: 0L
        val scatters = remember(timeline, seed, motion, tuning) {
            timeline.words.indices.map { index -> luminousWordScatter(index, seed, motion, tuning) }
        }
        val presentations = timeline.presentationAt(position.value, motion)
        // Motion multiplies the breath as well as the listener's own setting: the two
        // are a master switch and a shape, and either one at zero means no breathing.
        val breathAmplitude = with(density) {
            (BreathDp * tuning.breathingFloatPercent / 100f * motion).dp.toPx()
        }

        // The line comes into focus as it starts, which is the blur the reference
        // gives its entrance and which a fade alone cannot say. It is applied to the
        // block rather than to each word -- one offscreen pass while a line arrives
        // instead of one per word every frame -- and a word blurred on its own, out at
        // its waiting position, is the one part of the reference's entrance that is
        // not reproduced here.
        val entry = progress(
            sinceMillis = position.value - inputs.frame.current.startMillis,
            durationMillis = EntryMillis,
        )
        val entryBlur = if (motion > 0f && entry < 1f) {
            Modifier.blur(((1f - entry) * EntryBlurDp).dp)
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .size(
                    width = with(density) { flow.width.toDp() },
                    height = with(density) { flow.height.toDp() },
                )
                // The breath is read inside the layer rather than in composition, for
                // the same reason the escapement does it: the frame clock ticks far
                // more often than the song does anything.
                .graphicsLayer {
                    val phase = elapsed.value / BreathPeriodSeconds
                    translationY = luminousBreathOffset(phase, breathAmplitude) * cloudScale
                    scaleX = cloudScale
                    scaleY = cloudScale
                }
                .clickable { inputs.onSeekTo(inputs.frame.current.startMillis) }
                // Innermost, so the blur sees the block in its own untranslated space:
                // a blur outside the layer would clip the block at its bounds as the
                // breath carried it past them.
                .then(entryBlur),
        ) {
            presentations.forEachIndexed { index, presentation ->
                val layout = layouts.getOrNull(index) ?: return@forEachIndexed
                val slot = flow.slots.getOrNull(index) ?: return@forEachIndexed
                val scatter = scatters.getOrNull(index) ?: return@forEachIndexed
                Word(
                    word = presentation.word,
                    presentation = presentation,
                    layout = layout,
                    slot = slot,
                    scatter = scatter,
                    accent = inputs.accent,
                    fontSize = fontSize,
                )
            }
        }
    }
}

/**
 * One word: its drawn text, and how far it has travelled.
 *
 * Two passes over the same measured layout rather than two composables. The plain
 * colour is the whole word; the accent and its halo are clipped to the front the
 * sweep has reached, which is a straight clip across the word at a *grapheme
 * boundary* -- the front is looked up in the measured layout, so it lands where the
 * character actually ends rather than at a fraction of the word's width.
 */
@Composable
private fun Word(
    word: LyricWord,
    presentation: LyricWordPresentation,
    layout: TextLayoutResult,
    slot: LuminousWordSlot,
    scatter: LuminousWordScatter,
    accent: Color,
    fontSize: TextUnit,
) {
    val pose = poseOf(presentation)
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .size(
                width = with(density) { layout.size.width.toDp() },
                height = with(density) { layout.size.height.toDp() },
            )
            // The block is placed by its top-left, which is how the flow measured it,
            // and turned and sized about its centre, which is how a thrown word looks
            // like it was thrown rather than swung.
            .graphicsLayer {
                translationX = slot.x + scatter.dx * pose.distance
                translationY = slot.y + scatter.dy * pose.distance
                rotationZ = scatter.rotationDegrees * pose.tilt
                scaleX = scatter.scale * pose.scale
                scaleY = scatter.scale * pose.scale
                alpha = pose.alpha
            }
            .drawBehind {
                drawWord(layout = layout, color = BodyColour, shadow = null)
                // Only the word being sung carries the accent, and it keeps it while it
                // fades back to the plain colour -- a sung line returns to being a line,
                // which is the reference's own behaviour and the reason a long track does
                // not end up tinted from end to end. It is also what keeps this cheap:
                // the halo is a blurred pass per glyph, and cloaking every word that has
                // already been sung in one would cost a screenful of them every frame.
                val accentAlpha = if (presentation.state == LyricWordState.Active) {
                    1f - presentation.settle
                } else {
                    0f
                }
                if (accentAlpha <= 0f) return@drawBehind
                val frontX = sweepFrontX(layout, word, presentation.sweep)
                clipRect(0f, 0f, frontX, size.height) {
                    // The halo is the same glyphs drawn twice with a wide shadow, which
                    // is how the reference builds its two-ring glow -- a tight one
                    // against the stroke and a broad one behind it. It is clipped with
                    // the fill it belongs to, so nothing glows ahead of the song.
                    val blur = fontSize.toPx() * GlowBlurEm
                    drawWord(layout = layout, color = accent, shadow = null, alpha = accentAlpha)
                    drawWord(
                        layout = layout,
                        color = accent,
                        shadow = Shadow(color = accent, blurRadius = blur),
                        alpha = accentAlpha,
                    )
                    drawWord(
                        layout = layout,
                        color = accent,
                        shadow = Shadow(color = accent, blurRadius = blur * GlowOuterRatio),
                        alpha = accentAlpha,
                    )
                }
            },
    )
}

/**
 * A word's pose, read off the two interpolations.
 *
 * Waiting and passed are not equal and opposite. A word arrives almost all the way to
 * its place, and settles only a little back out of it, so a sung line holds its shape
 * instead of dispersing again -- what a listener is reading must not be the part that
 * keeps moving. [tilt] multiplies the thrown angle rather than replacing it: waiting,
 * the word is turned exactly as it was thrown; sung, it is nearly upright; passed, it
 * leans a little further than the throw alone would have put it.
 */
private data class WordPose(
    val distance: Float,
    val scale: Float,
    val alpha: Float,
    val tilt: Float,
)

private fun poseOf(presentation: LyricWordPresentation): WordPose {
    val arrive = presentation.arrive
    val settle = presentation.settle
    return WordPose(
        distance = lerp(
            lerp(WaitingDistance, ActiveDistance, arrive),
            PassedDistance,
            settle,
        ),
        scale = lerp(lerp(WaitingScale, ActiveScale, arrive), PassedScale, settle),
        alpha = lerp(lerp(WaitingAlpha, ActiveAlpha, arrive), PassedAlpha, settle),
        tilt = lerp(lerp(1f, ActiveTilt, arrive), PassedTilt, settle),
    )
}

/**
 * Where in the word the glow has reached, in pixels.
 *
 * The sweep counts graphemes, so the front is placed by asking the measured layout
 * where the grapheme it is crossing actually begins -- the same lookup the shared
 * wipe uses. That keeps the front on a character even where the characters are not
 * equally wide, which for a Latin word is the difference between a wipe and a guess.
 */
private fun sweepFrontX(layout: TextLayoutResult, word: LyricWord, sweep: Float): Float {
    val count = word.graphemes.size
    if (count == 0) return layout.size.width.toFloat() * sweep
    val scaled = sweep * count
    val completed = scaled.toInt().coerceIn(0, count)
    if (completed >= count) return layout.size.width.toFloat()
    val fraction = (scaled - completed).coerceIn(0f, 1f)
    val fromX = if (completed == 0) 0f else graphemeStartX(layout, word, completed - 1)
    val toX = graphemeStartX(layout, word, completed)
    return fromX + (toX - fromX) * fraction
}

private fun graphemeStartX(layout: TextLayoutResult, word: LyricWord, index: Int): Float {
    val local = (word.graphemes[index].startOffset - word.startOffset)
        .coerceIn(0, layout.layoutInput.text.length)
    return layout.getHorizontalPosition(local, usePrimaryDirection = true)
}

private fun DrawScope.drawWord(
    layout: TextLayoutResult,
    color: Color,
    shadow: Shadow?,
    alpha: Float = 1f,
) {
    drawText(
        textLayoutResult = layout,
        color = color,
        topLeft = Offset.Zero,
        alpha = alpha,
        shadow = shadow,
    )
}

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

/** How far into an interval of [durationMillis] a moment [sinceMillis] is. */
private fun progress(sinceMillis: Long, durationMillis: Long): Float =
    (sinceMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)

/** The accent as it is sung, and the plain colour everything returns to. */
private val BodyColour = Color.White.copy(alpha = 0.92f)

/** The reference sizes its line glow from the font, the same way its sweep does. */
private const val GlowBlurEm = 0.30f
private const val GlowOuterRatio = 2.06f

/** The three poses a word travels between. */
private const val WaitingDistance = 1f
private const val ActiveDistance = 0.06f
private const val PassedDistance = 0.28f
private const val WaitingScale = 0.82f
private const val ActiveScale = 1.4f
private const val PassedScale = 1f
private const val WaitingAlpha = 0.34f
private const val ActiveAlpha = 1f
private const val PassedAlpha = 0.9f
private const val ActiveTilt = 0.25f
private const val PassedTilt = 1.15f

/** The cloud's own type size and the wrapping it is measured against. */
private const val CloudFontSp = 26f
private const val CloudLineHeightRatio = 1.35f
private const val BaseWordGapDp = 12f
private const val CloudWidthFraction = 0.94f
private const val CloudHeightFraction = 0.62f

/** The reference stage's shorter side, which the type size was chosen against. */
private const val ReferenceStageSideDp = 411f
private const val MinStageScale = 0.72f
private const val MaxStageScale = 1.45f

/** The block's breathing, as amplitude and period. */
private const val BreathDp = 14f
private const val BreathPeriodSeconds = 7f

/** How long a line spends coming into focus, and how far out of focus it starts. */
private const val EntryMillis = 420L
private const val EntryBlurDp = 9f

/** The next line, at the bottom, out of the reading path. */
private const val CueAlpha = 0.34f
private const val CueFontSp = 14
private const val CueHeightDp = 44

private const val MaxLines = 3
private const val MeasurerCacheSize = 48
