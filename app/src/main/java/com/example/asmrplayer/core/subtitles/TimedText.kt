package com.example.asmrplayer.core.subtitles

import java.util.Locale

/**
 * The three timed-text formats the library actually ships.
 *
 * The order is the format preference used when one audio file has more than one
 * candidate beside it: LRC is what this content ships, and it is the only one of the
 * three that can carry word-level timing, which the later lyric layers consume. An
 * SRT or VTT is therefore only preferred when there is no LRC.
 */
enum class TimedTextFormat {
    Lrc,
    Vtt,
    Srt,
}

/**
 * Which files this package can read, decided by extension.
 *
 * Extension only, deliberately: Android document providers report `.lrc` as
 * `text/plain` or `application/octet-stream` depending on the provider, so a MIME
 * check would either reject real files or admit every text file in the folder.
 * The extension is what a person naming a lyric file actually controls.
 */
object SupportedTimedText {
    fun formatOf(displayName: String): TimedTextFormat? =
        when (displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)) {
            "lrc" -> TimedTextFormat.Lrc
            "srt" -> TimedTextFormat.Srt
            "vtt" -> TimedTextFormat.Vtt
            else -> null
        }

    fun isSupported(displayName: String): Boolean = formatOf(displayName) != null
}

/**
 * One word inside a line, with its own start time.
 *
 * Only enhanced LRC carries these. An empty word list on a line means the line has
 * no finer timing than its own start, which is every line of SRT and VTT and most
 * LRC; consumers must treat that as "no word timing available", not as a line with
 * no words.
 */
data class TimedTextWord(
    val startMillis: Long,
    /** Null means the word runs until the next word starts. */
    val endMillis: Long?,
    val text: String,
)

/**
 * One normalized line, whatever format it came from.
 *
 * [endMillis] is null when the format does not state one, which is every LRC line:
 * the line then runs until the next line starts. That is the same rule a lyric
 * display needs anyway, so it is a real value rather than a missing one.
 */
data class TimedTextLine(
    val startMillis: Long,
    val endMillis: Long?,
    val text: String,
    val words: List<TimedTextWord> = emptyList(),
)

/**
 * A parsed timed-text file.
 *
 * A file either has usable timing or it does not, and the two cases are kept apart
 * rather than merged: [lines] is empty for a plain-text LRC that carries no
 * timestamps, and [untimedText] is empty for every file that parsed. Nothing here
 * invents a time for untimed text, because a lyric display that pretends to follow
 * playback is worse than one that admits it cannot.
 */
data class TimedTextDocument(
    val format: TimedTextFormat,
    val lines: List<TimedTextLine> = emptyList(),
    val untimedText: List<String> = emptyList(),
) {
    val isTimed: Boolean get() = lines.isNotEmpty()

    /**
     * The line that should be showing at [timeMillis]: the last one that has
     * started, or -1 before the first one starts.
     *
     * A gap between one line's stated end and the next line's start keeps showing
     * the earlier line. For lyrics that is right -- the words are still on screen
     * while the singer holds the note -- and it is also what makes the two kinds of
     * end time behave the same here.
     */
    fun lineIndexAt(timeMillis: Long): Int {
        if (lines.isEmpty()) return -1
        if (timeMillis < lines[0].startMillis) return -1
        var low = 0
        var high = lines.lastIndex
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (lines[middle].startMillis <= timeMillis) low = middle else high = middle - 1
        }
        return low
    }

    fun lineAt(timeMillis: Long): TimedTextLine? = lines.getOrNull(lineIndexAt(timeMillis))

    /** Where to seek to play [index] from its beginning, or null if it has no time. */
    fun seekMillisForLine(index: Int): Long? = lines.getOrNull(index)?.startMillis

    /**
     * The window of lines around [centreIndex] that a display should hold, clamped to
     * the document. An empty range when there is nothing to show, so a caller never
     * has to special-case an empty document.
     */
    fun neighbourhood(centreIndex: Int, radius: Int): IntRange {
        if (lines.isEmpty() || radius < 0) return IntRange.EMPTY
        val centre = centreIndex.coerceIn(0, lines.lastIndex)
        return (centre - radius).coerceAtLeast(0)..(centre + radius).coerceAtMost(lines.lastIndex)
    }
}
