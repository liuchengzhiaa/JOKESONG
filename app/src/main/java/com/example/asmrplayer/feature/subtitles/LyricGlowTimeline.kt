package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextLine
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.roundToLong

enum class LyricGlowTimingSource {
    Exact,
    Estimated,
}

/** One display grapheme and the presentation-only interval assigned to it. */
data class LyricGlowSegment(
    val startOffset: Int,
    val endOffset: Int,
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * A bounded, source-labelled projection used only by lyric presentation surfaces.
 * Offsets are UTF-16 offsets so they can be applied directly to AnnotatedString.
 */
data class LyricGlowTimeline(
    val source: LyricGlowTimingSource,
    val segments: List<LyricGlowSegment>,
) {
    fun snapshotAt(positionMillis: Long): LyricGlowSnapshot {
        var completedCount = 0
        var activeIndex = -1
        segments.forEachIndexed { index, segment ->
            if (positionMillis >= segment.endMillis) {
                completedCount = index + 1
            } else if (activeIndex < 0 && positionMillis >= segment.startMillis) {
                activeIndex = index
            }
        }
        return LyricGlowSnapshot(
            completedCount = completedCount,
            activeIndex = activeIndex,
        )
    }

    /** Next moment at which [snapshotAt] can change. */
    fun nextBoundaryAfter(positionMillis: Long): Long? = segments
        .asSequence()
        .flatMap { sequenceOf(it.startMillis, it.endMillis) }
        .filter { it > positionMillis }
        .minOrNull()
}

data class LyricGlowSnapshot(
    val completedCount: Int,
    val activeIndex: Int,
)

/**
 * Builds the glow timeline without mutating or enriching the parsed document.
 *
 * Exact word timing always wins. Smart fallback mirrors Folia's visible behaviour:
 * it uses the next line as an LRC end, reserves a short tail hold, gives CJK display
 * characters equal weight, makes punctuation brief, and gives longer Latin words a
 * little more time. It remains an estimate and is labelled as such by [source].
 */
fun TimedTextLine.lyricGlowTimeline(
    format: TimedTextFormat,
    nextLineStartMillis: Long?,
    mode: LyricsWordGlowMode,
): LyricGlowTimeline? {
    if (mode == LyricsWordGlowMode.Off) return null
    if (words.isNotEmpty()) {
        return exactGlowTimeline(nextLineStartMillis)?.takeIf { it.segments.isNotEmpty() }
    }
    if (mode == LyricsWordGlowMode.ExactOnly) return null
    return estimatedGlowTimeline(format, nextLineStartMillis)
        .takeIf { it.segments.isNotEmpty() }
}

private fun TimedTextLine.exactGlowTimeline(nextLineStartMillis: Long?): LyricGlowTimeline? {
    val fallbackEnd = effectiveLineEnd(nextLineStartMillis)
    val segments = buildList {
        var searchFrom = 0
        words.forEachIndexed { wordIndex, word ->
            val matchedStart = text.indexOf(word.text, startIndex = searchFrom)
                .takeIf { it >= 0 }
                ?: text.indexOf(word.text).takeIf { it >= 0 }
                ?: return@forEachIndexed
            val matchedEnd = (matchedStart + word.text.length).coerceAtMost(text.length)
            val graphemes = graphemeRanges(text, matchedStart, matchedEnd)
            if (graphemes.isEmpty()) return@forEachIndexed

            val nextWordStart = words.getOrNull(wordIndex + 1)?.startMillis
            val wordEnd = (word.endMillis ?: nextWordStart ?: fallbackEnd)
                .coerceAtLeast(word.startMillis + 1L)
            addEvenSegments(graphemes, word.startMillis, wordEnd)
            searchFrom = matchedEnd
        }
    }
    return LyricGlowTimeline(LyricGlowTimingSource.Exact, segments)
}

private fun TimedTextLine.estimatedGlowTimeline(
    format: TimedTextFormat,
    nextLineStartMillis: Long?,
): LyricGlowTimeline {
    val tokens = estimatedTokens(text)
    if (tokens.isEmpty()) {
        return LyricGlowTimeline(LyricGlowTimingSource.Estimated, emptyList())
    }

    val rawEnd = effectiveLineEnd(nextLineStartMillis)
    val rawDuration = (rawEnd - startMillis).coerceAtLeast(MinimumLineDurationMillis)
    val readableDuration = if (format == TimedTextFormat.Lrc && rawDuration > LongGapMillis) {
        val graphemeCount = tokens.sumOf { it.graphemes.size }.coerceAtLeast(1)
        minOf(rawDuration, graphemeCount * ReadingMillisPerGrapheme + ReadingTailMillis)
    } else {
        rawDuration
    }
    val activeDuration = (readableDuration * ActiveLineFraction)
        .roundToLong()
        .coerceAtLeast(MinimumLineDurationMillis)

    val punctuationCount = tokens.count(EstimatedToken::punctuationOnly)
    val fixedPunctuationDuration = minOf(
        punctuationCount * PunctuationMillis,
        (activeDuration / 3).coerceAtLeast(0L),
    )
    val weightedDuration = (activeDuration - fixedPunctuationDuration).coerceAtLeast(1L)
    val totalWeight = tokens.filterNot(EstimatedToken::punctuationOnly)
        .sumOf(EstimatedToken::weight)
        .takeIf { it > 0.0 }
        ?: 1.0
    val punctuationDuration = if (punctuationCount == 0) {
        0.0
    } else {
        fixedPunctuationDuration.toDouble() / punctuationCount
    }

    var cursor = startMillis.toDouble()
    val activeEnd = startMillis + activeDuration
    val segments = buildList {
        tokens.forEachIndexed { tokenIndex, token ->
            val tokenDuration = if (token.punctuationOnly) {
                punctuationDuration
            } else {
                weightedDuration * token.weight / totalWeight
            }
            val tokenStart = cursor.roundToLong()
            val tokenEnd = if (tokenIndex == tokens.lastIndex) {
                activeEnd
            } else {
                (cursor + tokenDuration).roundToLong().coerceAtMost(activeEnd)
            }.coerceAtLeast(tokenStart + 1L)
            addEvenSegments(token.graphemes, tokenStart, tokenEnd)
            cursor += tokenDuration
        }
    }
    return LyricGlowTimeline(LyricGlowTimingSource.Estimated, segments)
}

private fun TimedTextLine.effectiveLineEnd(nextLineStartMillis: Long?): Long =
    (endMillis ?: nextLineStartMillis ?: (startMillis + LastLineFallbackMillis))
        .coerceAtLeast(startMillis + MinimumLineDurationMillis)

private data class GraphemeRange(val start: Int, val end: Int)

private data class EstimatedToken(
    val graphemes: List<GraphemeRange>,
    val weight: Double,
    val punctuationOnly: Boolean,
)

private fun estimatedTokens(text: String): List<EstimatedToken> {
    val graphemes = graphemeRanges(text, 0, text.length)
    val result = mutableListOf<EstimatedToken>()
    var index = 0
    while (index < graphemes.size) {
        while (index < graphemes.size && text.substring(graphemes[index].start, graphemes[index].end).isBlank()) {
            index += 1
        }
        if (index >= graphemes.size) break
        val tokenStart = index
        while (index < graphemes.size && !text.substring(graphemes[index].start, graphemes[index].end).isBlank()) {
            index += 1
        }
        val tokenGraphemes = graphemes.subList(tokenStart, index)
        val containsCjk = tokenGraphemes.any { range ->
            text.codePointAt(range.start).isCjkCodePoint()
        }
        if (containsCjk) {
            tokenGraphemes.forEach { range ->
                val punctuation = text.codePointAt(range.start).isPunctuationCodePoint()
                result += EstimatedToken(
                    graphemes = listOf(range),
                    weight = if (punctuation) 0.0 else 1.0,
                    punctuationOnly = punctuation,
                )
            }
        } else {
            val punctuationOnly = tokenGraphemes.all { range ->
                text.codePointAt(range.start).isPunctuationCodePoint()
            }
            result += EstimatedToken(
                graphemes = tokenGraphemes,
                weight = if (punctuationOnly) 0.0 else 1.0 + tokenGraphemes.size * 0.15,
                punctuationOnly = punctuationOnly,
            )
        }
    }
    return result
}

private fun graphemeRanges(text: String, start: Int, end: Int): List<GraphemeRange> {
    if (start >= end) return emptyList()
    val slice = text.substring(start, end)
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(slice)
    return buildList {
        var localStart = iterator.first()
        var localEnd = iterator.next()
        while (localEnd != BreakIterator.DONE) {
            add(GraphemeRange(start + localStart, start + localEnd))
            localStart = localEnd
            localEnd = iterator.next()
        }
    }
}

private fun MutableList<LyricGlowSegment>.addEvenSegments(
    graphemes: List<GraphemeRange>,
    startMillis: Long,
    endMillis: Long,
) {
    if (graphemes.isEmpty()) return
    val duration = (endMillis - startMillis).coerceAtLeast(graphemes.size.toLong())
    graphemes.forEachIndexed { index, range ->
        val start = startMillis + duration * index / graphemes.size
        val end = if (index == graphemes.lastIndex) {
            endMillis.coerceAtLeast(start + 1L)
        } else {
            (startMillis + duration * (index + 1) / graphemes.size).coerceAtLeast(start + 1L)
        }
        add(
            LyricGlowSegment(
                startOffset = range.start,
                endOffset = range.end,
                startMillis = start,
                endMillis = end,
            ),
        )
    }
}

private fun Int.isCjkCodePoint(): Boolean = when (Character.UnicodeScript.of(this)) {
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL,
    -> true
    else -> false
}

private fun Int.isPunctuationCodePoint(): Boolean = when (Character.getType(this)) {
    Character.CONNECTOR_PUNCTUATION.toInt(),
    Character.DASH_PUNCTUATION.toInt(),
    Character.START_PUNCTUATION.toInt(),
    Character.END_PUNCTUATION.toInt(),
    Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
    Character.FINAL_QUOTE_PUNCTUATION.toInt(),
    Character.OTHER_PUNCTUATION.toInt(),
    -> true
    else -> false
}

private const val ActiveLineFraction = 0.90
private const val MinimumLineDurationMillis = 100L
private const val LastLineFallbackMillis = 5_000L
private const val LongGapMillis = 5_000L
private const val ReadingMillisPerGrapheme = 500L
private const val ReadingTailMillis = 2_000L
private const val PunctuationMillis = 50L
