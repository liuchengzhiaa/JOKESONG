package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.core.subtitles.TimedTextLine

/**
 * The bounded slice of a document a rail visualizer lays out as a single object.
 *
 * The immersive window ([ImmersiveLyricsFrame]) is the wrong shape for a rail. It
 * holds two lines on either side of one line that must exist, because its modes
 * replace that line on every boundary. A rail instead shows a long arc of lines at
 * once and has to keep turning after the last one has been sung, so it needs a
 * different projection with two properties the other one deliberately refuses:
 * an anchor that may sit *outside* the document, and no line that must exist.
 *
 * It is bounded the same way, though -- a fixed radius, never the whole document --
 * so a rail cannot quietly become a scrolling reader.
 */
data class LyricRailWindow(
    /** At most `2 * radius + 1` lines around the anchor, clamped to the document. */
    val lines: List<TimedTextLine>,
    /** Document index of [lines]' first entry; 0 when [lines] is empty. */
    val startIndex: Int,
    /**
     * Where the rail is anchored, **in [lines] coordinates**. Fractional between two
     * lines while a tick is in flight; one past the last entry once the final line
     * has finished, which is what lets the wheel keep turning off the end of the
     * song instead of stopping dead on the last lyric.
     */
    val anchor: Float,
    /** False when nothing is being sung: before the first line, or past the last. */
    val hasAnchor: Boolean,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /** How far [offset] (an index into [lines]) sits from the anchor, in lines. */
    fun distanceFromAnchor(offset: Int): Float = offset - anchor
}

/**
 * How many lines either side of the anchor a rail draws.
 *
 * Seven rather than the two the immersive window uses: a rail's whole point is that
 * the line being sung is one point on a long arc, and the arc needs enough company
 * around it to read as a wheel rather than as a list with two neighbours.
 */
const val LyricRailRadius = 7

/**
 * Projects playback time into a rail window.
 *
 * Before the first line starts the rail still arranges itself around line 0 -- it
 * simply has nothing to highlight, which is the honest reading of "not started yet"
 * and keeps the wheel's geometry from jumping when the singing does begin.
 */
fun TimedTextDocument.lyricRailWindow(
    positionMillis: Long,
    radius: Int = LyricRailRadius,
): LyricRailWindow {
    if (lines.isEmpty()) {
        return LyricRailWindow(lines = emptyList(), startIndex = 0, anchor = 0f, hasAnchor = false)
    }

    val current = lineIndexAt(positionMillis)
    val pastEnd = current >= lines.lastIndex && positionMillis > lastLineEnd()
    val anchorIndex = if (pastEnd) lines.size else current.coerceAtLeast(0)

    // The window is centred on a real line even when the anchor is off the end, so
    // the rail always draws the lines nearest to where it currently points.
    val centre = anchorIndex.coerceIn(0, lines.lastIndex)
    val range = neighbourhood(centre, radius)
    return LyricRailWindow(
        lines = lines.subList(range.first, range.last + 1),
        startIndex = range.first,
        anchor = (anchorIndex - range.first).toFloat(),
        hasAnchor = !pastEnd && current >= 0,
    )
}

/** The end of the final line, which LRC never states and so has to be assumed. */
private fun TimedTextDocument.lastLineEnd(): Long {
    val last = lines.last()
    return last.endMillis ?: (last.startMillis + RailLastLineFallbackMillis)
}

/** How long the last line is assumed to keep sounding when the file states no end. */
private const val RailLastLineFallbackMillis = 5_000L
