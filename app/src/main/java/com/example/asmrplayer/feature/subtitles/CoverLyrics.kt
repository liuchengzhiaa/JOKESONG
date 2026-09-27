package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextDocument
import kotlin.math.max
import kotlin.math.pow

/** The bounded lyric context painted on one focused playing cover. */
data class CoverLyricsFrame(
    val previous: String?,
    val current: String,
    val next: String?,
    val currentIndex: Int,
)

/**
 * Reduces a complete timed document to at most three strings. The wall never owns
 * or lays out the full subtitle file; the shared parsed document remains the source.
 */
fun TimedTextDocument.coverLyricsFrame(positionMillis: Long): CoverLyricsFrame? {
    val index = lineIndexAt(positionMillis)
    val current = lines.getOrNull(index) ?: return null
    return CoverLyricsFrame(
        previous = lines.getOrNull(index - 1)?.text,
        current = current.text,
        next = lines.getOrNull(index + 1)?.text,
        currentIndex = index,
    )
}

/**
 * Resolves a cover frame back to its one bounded source line and builds the same
 * presentation timeline used by the immersive scene. The text equality guard keeps
 * an outgoing AnimatedContent frame from borrowing timing from the next line.
 */
fun TimedTextDocument.coverLyricsGlowTimeline(
    frame: CoverLyricsFrame,
    mode: LyricsWordGlowMode,
): LyricGlowTimeline? {
    val line = lines.getOrNull(frame.currentIndex)?.takeIf { it.text == frame.current }
        ?: return null
    return line.lyricGlowTimeline(
        format = format,
        nextLineStartMillis = lines.getOrNull(frame.currentIndex + 1)?.startMillis,
        mode = mode,
    )
}

/**
 * Black shade needed behind cover lyrics. Bright palettes receive more shade,
 * while dark artwork keeps more of its detail. The bounded palette is already in
 * memory from the focus border/ambient-light decode, so this performs no I/O.
 */
fun coverLyricsShadeAlpha(paletteArgbs: List<Int>, fallbackArgb: Int): Float {
    val colours = paletteArgbs.ifEmpty { listOf(fallbackArgb) }
    val brightest = colours.maxOfOrNull(::relativeLuminance) ?: 0f
    return (0.36f + brightest * 0.42f).coerceIn(0.36f, 0.72f)
}

private fun relativeLuminance(argb: Int): Float {
    fun channel(shift: Int): Float {
        val encoded = ((argb shr shift) and 0xFF) / 255f
        return if (encoded <= 0.04045f) encoded / 12.92f else {
            ((encoded + 0.055f) / 1.055f).pow(2.4f)
        }
    }
    val red = channel(16)
    val green = channel(8)
    val blue = channel(0)
    return max(0f, 0.2126f * red + 0.7152f * green + 0.0722f * blue)
}
