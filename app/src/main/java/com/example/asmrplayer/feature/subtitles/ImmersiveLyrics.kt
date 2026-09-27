package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.core.subtitles.TimedTextLine

/**
 * The small, stable window owned by the immersive lyric scene.
 *
 * The visualizer never composes the full document. It keeps two context lines on
 * either side so a line transition can move naturally without turning the scene
 * into another scrolling reader.
 */
data class ImmersiveLyricsFrame(
    val previousFar: TimedTextLine?,
    val previous: TimedTextLine?,
    val current: TimedTextLine,
    val next: TimedTextLine?,
    val nextFar: TimedTextLine?,
    val currentIndex: Int,
    /** -1 when the current line has no enhanced-LRC word active yet. */
    val activeWordIndex: Int,
)

/**
 * Projects playback time into a five-line visualizer window.
 *
 * Before the first timed line begins there is deliberately no frame: showing the
 * first line as current early would be another form of invented synchronization.
 */
fun TimedTextDocument.immersiveLyricsFrame(positionMillis: Long): ImmersiveLyricsFrame? {
    val index = lineIndexAt(positionMillis)
    return immersiveLyricsFrameAt(index, positionMillis)
}

/** Builds the same bounded window for an explicit line, used during transitions. */
fun TimedTextDocument.immersiveLyricsFrameAt(
    index: Int,
    positionMillis: Long,
): ImmersiveLyricsFrame? {
    val current = lines.getOrNull(index) ?: return null
    return ImmersiveLyricsFrame(
        previousFar = lines.getOrNull(index - 2),
        previous = lines.getOrNull(index - 1),
        current = current,
        next = lines.getOrNull(index + 1),
        nextFar = lines.getOrNull(index + 2),
        currentIndex = index,
        activeWordIndex = current.activeWordIndexAt(positionMillis),
    )
}

/** Last enhanced-LRC word whose timestamp has started, or -1 without word timing. */
fun TimedTextLine.activeWordIndexAt(positionMillis: Long): Int {
    if (words.isEmpty()) return -1
    var low = 0
    var high = words.lastIndex
    if (positionMillis < words[0].startMillis) return -1
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (words[middle].startMillis <= positionMillis) low = middle else high = middle - 1
    }
    return low
}
