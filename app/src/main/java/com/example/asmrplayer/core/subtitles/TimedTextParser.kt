package com.example.asmrplayer.core.subtitles

/**
 * Turns the text of an LRC, SRT or VTT file into one normalized document.
 *
 * The three formats disagree about nearly everything -- how a timestamp is spelled,
 * whether a line has an end, whether a block is numbered -- and every consumer
 * downstream cares about none of that. This is the only place the differences exist.
 */
fun parseTimedText(format: TimedTextFormat, source: String): TimedTextDocument {
    val text = source.removePrefix(ByteOrderMark)
    return when (format) {
        TimedTextFormat.Lrc -> parseLrc(text)
        TimedTextFormat.Srt -> parseCueFormat(text, TimedTextFormat.Srt)
        TimedTextFormat.Vtt -> parseCueFormat(text, TimedTextFormat.Vtt)
    }
}

private const val ByteOrderMark = "\uFEFF"

// region LRC

/**
 * `[mm:ss.xx]`, `[mm:ss.xxx]`, `[mm:ss]` and the `[mm:ss:xx]` variant that some
 * editors write. Minutes are allowed to run past 59, which real long-form files do.
 */
private val LrcTimeTag = Regex("""\[(\d{1,4}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")

/** `[ar:...]`, `[offset:...]` and the other bracket tags that are not times. */
private val LrcMetadataTag = Regex("""\[([A-Za-z]{1,12}):([^\]]*)\]""")

/** Enhanced LRC: a word's own time, written inside the line's text. */
private val LrcWordTag = Regex("""<(\d{1,4}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

/**
 * LRC.
 *
 * Four things in this format are easy to get wrong and all four are handled here:
 * one line can carry several time tags and then shows at each of them; `[offset:]`
 * shifts every time in the file and is a real part of the format, not metadata to
 * skip; enhanced files put a time on every word; and a bracket tag is only a
 * timestamp if it looks like one, so `[ar:...]` does not become a lyric at 0:00.
 *
 * A line with no time tag is appended to the previous lyric, which is how the format
 * wraps a long line. If the file has no time tags at all there is nothing to append
 * to and every line becomes [TimedTextDocument.untimedText] instead.
 */
private fun parseLrc(source: String): TimedTextDocument {
    val lines = mutableListOf<TimedTextLine>()
    val untimed = mutableListOf<String>()
    val pending = mutableListOf<String>()
    var offsetMillis = 0L

    for (rawLine in source.lineSequence()) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue

        val lead = leadingTimeTags(line)
        val times = lead.times
        if (times.isEmpty()) {
            val metadata = LrcMetadataTag.matchEntire(line)
            if (metadata != null) {
                if (metadata.groupValues[1].equals("offset", ignoreCase = true)) {
                    offsetMillis = metadata.groupValues[2].trim().toLongOrNull() ?: offsetMillis
                }
                continue
            }
            stripWordTags(line).trim().takeIf(String::isNotEmpty)?.let(pending::add)
            continue
        }

        val body = line.substring(lead.textStart)
        val words = parseLrcWords(body)
        val text = words.joinToString(" ") { it.text }.trim().ifEmpty { stripWordTags(body).trim() }
        if (text.isEmpty()) {
            // A bare time tag -- `[00:28.34]` with nothing after it -- marks a pause,
            // not a line anyone reads. Most of the files in a real library contain
            // some, and as lyric lines they are empty rows with nothing in them and
            // nothing to seek to. Dropping them leaves the previous line current
            // through the silence, which is what the pause means. Pending text is
            // deliberately kept: it still belongs to the line above.
            continue
        }
        for (time in times) {
            val lineIndex = lines.size
            if (pending.isNotEmpty()) {
                // Text that arrived before this timestamp belongs to the line above it
                // when there is one; at the top of the file it can only belong here.
                if (lineIndex == 0) {
                    lines += timedLine(time, null, pending.joinToString(" "), emptyList())
                } else {
                    lines[lineIndex - 1] = lines[lineIndex - 1].withAppendedText(pending.joinToString(" "))
                    pending.clear()
                }
            }
            lines += timedLine(time, null, text, words)
        }
        pending.clear()
    }

    if (pending.isNotEmpty()) {
        // A file with no timestamps at all is a lyric sheet: every line of it is a line
        // the reader should see on its own, so they are kept apart rather than being
        // joined into the one block that wrapping a timed line's continuation produces.
        if (lines.isEmpty()) {
            untimed += pending
        } else {
            lines[lines.lastIndex] = lines.last().withAppendedText(pending.joinToString(" "))
        }
    }

    if (lines.isEmpty()) return TimedTextDocument(TimedTextFormat.Lrc, untimedText = untimed)

    val shifted = if (offsetMillis == 0L) lines else lines.map { it.shiftedBy(offsetMillis) }
    return TimedTextDocument(
        format = TimedTextFormat.Lrc,
        lines = shifted.sortedBy { it.startMillis },
    )
}

/** A line's leading time tags, and where its text starts once they are consumed. */
private class LrcLead(val times: List<Long>, val textStart: Int)

/** Every time tag that opens [line], in order. Stops at the first thing that is not one. */
private fun leadingTimeTags(line: String): LrcLead {
    val times = mutableListOf<Long>()
    var cursor = 0
    while (cursor < line.length) {
        val match = LrcTimeTag.find(line, cursor) ?: break
        if (match.range.first != cursor) break
        times += lrcTagMillis(match)
        cursor = match.range.last + 1
    }
    return LrcLead(times, cursor)
}

private fun lrcTagMillis(match: MatchResult): Long {
    val fraction = match.groupValues[3]
    return clockToMillis(
        hours = 0L,
        minutes = match.groupValues[1].toLongOrNull() ?: 0L,
        seconds = match.groupValues[2].toLongOrNull() ?: 0L,
        fraction = fraction,
    )
}

/**
 * The words of an enhanced LRC line, with each word running until the next one
 * starts. A file that only tags the line's start yields no words, so a consumer can
 * tell "no word timing" from "one word".
 */
private fun parseLrcWords(body: String): List<TimedTextWord> {
    val matches = LrcWordTag.findAll(body).toList()
    if (matches.isEmpty()) return emptyList()
    val texts = matches.mapIndexed { index, match ->
        val end = if (index + 1 < matches.size) matches[index + 1].range.first else body.length
        body.substring(match.range.last + 1, end)
    }
    val starts = matches.map { lrcTagMillis(it) }
    return matches.indices.mapNotNull { index ->
        val text = texts[index].trim()
        if (text.isEmpty()) return@mapNotNull null
        TimedTextWord(
            startMillis = starts[index],
            endMillis = starts.getOrNull(index + 1),
            text = text,
        )
    }
}

private fun stripWordTags(line: String): String = LrcWordTag.replace(line, "")

private fun timedLine(
    startMillis: Long,
    endMillis: Long?,
    text: String,
    words: List<TimedTextWord>,
): TimedTextLine = TimedTextLine(
    startMillis = startMillis.coerceAtLeast(0L),
    endMillis = endMillis,
    text = text,
    words = words,
)

private fun TimedTextLine.withAppendedText(extra: String): TimedTextLine =
    copy(text = listOf(text, extra).filter(String::isNotEmpty).joinToString(" "))

private fun TimedTextLine.shiftedBy(offsetMillis: Long): TimedTextLine = copy(
    startMillis = (startMillis + offsetMillis).coerceAtLeast(0L),
    endMillis = endMillis?.let { (it + offsetMillis).coerceAtLeast(0L) },
    words = words.map { word ->
        word.copy(
            startMillis = (word.startMillis + offsetMillis).coerceAtLeast(0L),
            endMillis = word.endMillis?.let { (it + offsetMillis).coerceAtLeast(0L) },
        )
    },
)

// endregion

// region SRT and VTT

/** `HH:MM:SS,mmm`, `HH:MM:SS.mmm` and VTT's hour-less `MM:SS.mmm`. */
private val CueTimestamp = Regex("""(\d{1,3}):(\d{1,2})(?::(\d{1,2}))?[.,](\d{1,3})""")

/** VTT blocks that describe the file rather than carry a cue. */
private val VttNonCueBlock = Regex("""^(NOTE|STYLE|REGION)\b""")

/**
 * SRT and VTT, which are the same thing with different punctuation and a header.
 *
 * A block whose timestamps do not parse is skipped rather than failing the file: a
 * subtitle file with one broken cue still has every other cue worth showing, and the
 * alternative is showing none of them.
 *
 * Multi-line cue text is joined with a space. The line break in the file is a
 * reading hint for a fixed-width subtitle renderer; this display wraps to its own
 * width, so keeping the original break would fight it.
 */
private fun parseCueFormat(source: String, format: TimedTextFormat): TimedTextDocument {
    val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
    val body = if (format == TimedTextFormat.Vtt) skipVttHeader(normalized) else normalized
    val lines = mutableListOf<TimedTextLine>()

    for (block in body.split(BlankLine)) {
        val blockLines = block.lines().map(String::trim).filter(String::isNotEmpty)
        if (blockLines.isEmpty()) continue
        if (format == TimedTextFormat.Vtt && VttNonCueBlock.containsMatchIn(blockLines.first())) continue

        val cueIndex = blockLines.indexOfFirst { it.contains(CueArrow) }
        if (cueIndex < 0) continue
        val (start, end) = parseCueRange(blockLines[cueIndex]) ?: continue
        val text = blockLines.drop(cueIndex + 1)
            .joinToString(" ") { cleanCueText(it) }
            .trim()
        if (text.isEmpty()) continue
        lines += TimedTextLine(startMillis = start, endMillis = end, text = text)
    }

    return TimedTextDocument(
        format = format,
        // A subtitle file with cues out of order would break the binary search that
        // finds the current line, and the sort is stable so equal starts keep their
        // file order.
        lines = lines.sortedBy { it.startMillis },
    )
}

/**
 * The blank line between two cues. A regex rather than a literal, and passed to
 * `split` as one: a trailing-whitespace-only line is still a separator in files that
 * were edited by hand, and a `String` delimiter would be matched literally and never
 * separate anything at all.
 */
private val BlankLine = Regex("""\n\s*\n""")

private const val CueArrow = "-->"

/** Drops the `WEBVTT` line and anything the header declares before the first cue. */
private fun skipVttHeader(source: String): String {
    val lines = source.lines()
    val headerIndex = lines.indexOfFirst(String::isNotBlank)
    if (headerIndex < 0) return source
    if (!lines[headerIndex].trim().startsWith("WEBVTT")) return source
    val headerEnd = lines.drop(headerIndex).indexOfFirst(String::isBlank)
    return if (headerEnd < 0) "" else lines.drop(headerIndex + headerEnd + 1).joinToString("\n")
}

private fun parseCueRange(line: String): Pair<Long, Long>? {
    val arrow = line.indexOf(CueArrow)
    if (arrow < 0) return null
    val start = parseCueTimestamp(line.substring(0, arrow)) ?: return null
    val end = parseCueTimestamp(line.substring(arrow + CueArrow.length)) ?: return null
    return start to end
}

private fun parseCueTimestamp(value: String): Long? {
    // Cue settings follow the timestamp (VTT) and the arrow's right-hand side can
    // carry them, so the first timestamp in the text is the one that counts.
    val match = CueTimestamp.find(value) ?: return null
    val first = match.groupValues[1].toLongOrNull() ?: return null
    val second = match.groupValues[2].toLongOrNull() ?: return null
    val third = match.groupValues[3]
    return if (third.isEmpty()) {
        clockToMillis(hours = 0L, minutes = first, seconds = second, fraction = match.groupValues[4])
    } else {
        clockToMillis(
            hours = first,
            minutes = second,
            seconds = third.toLongOrNull() ?: return null,
            fraction = match.groupValues[4],
        )
    }
}

/**
 * A cue's text with its markup removed. SRT files in the wild carry both `<i>`-style
 * tags and ASS override blocks, and VTT adds voice spans and entity escapes.
 */
private fun cleanCueText(text: String): String = text
    .replace(Tag, "")
    .replace(AssOverride, "")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&nbsp;", " ")
    .replace("&#39;", "'")
    .trim()

private val Tag = Regex("""<[^>]*>""")

/**
 * Every literal brace and bracket in this file is escaped, and that is not a style
 * choice: Android compiles these with ICU, which rejects an unescaped `}` as a syntax
 * error where the JVM's `java.util.regex` reads it as a literal. The unit tests run on
 * the JVM, so a pattern like `\{[^}]*}` passes every test and then crashes the app the
 * first time a subtitle is opened. Escaping is the only thing that keeps the two
 * engines agreeing.
 */
private val AssOverride = Regex("""\{[^}]*\}""")

// endregion

/**
 * A fraction of a second, however many digits the file wrote: `.5`, `.50` and `.500`
 * are all half a second.
 *
 * Read strictly, LRC counts hundredths and SRT/VTT count milliseconds, so one digit
 * would mean 50 ms in one format and 500 ms in another. A file that writes a single
 * digit is following neither spec, and the reading that survives both is the plain
 * one: the digits are a fraction of a second.
 */
private fun clockToMillis(hours: Long, minutes: Long, seconds: Long, fraction: String): Long {
    val fractionMillis = when (fraction.length) {
        0 -> 0L
        1 -> fraction.toLong() * 100
        2 -> fraction.toLong() * 10
        else -> fraction.take(3).toLong()
    }
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + fractionMillis
}
