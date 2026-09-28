package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextLine

/**
 * The word cloud's timing, built on the grapheme timeline rather than beside it.
 *
 * A scattered-word presentation needs a second granularity that the wipe does not:
 * the *display word*, which is what gets displaced, scaled and coloured as a unit.
 * Rather than estimate word times again -- and risk the two disagreeing about the
 * same line -- this groups the segments [lyricGlowTimeline] already produced, so a
 * word's interval is exactly the span its own graphemes were given, and an exact
 * word timing still wins over the estimate everywhere.
 *
 * Where the boundaries fall is [displayWordSpans], which follows the same rule the
 * estimator uses: whitespace separates words, and a run containing CJK is split per
 * character, because that is what a Chinese lyric line means by a word. Punctuation
 * attaches to the word before it rather than floating alone.
 */

/** One display word: what it says, where it came from, and when it is sung. */
data class LyricWord(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val startMillis: Long,
    val endMillis: Long,
    /** The graphemes inside this word, in order, each with its own interval. */
    val graphemes: List<LyricGlowSegment>,
)

/**
 * How a word is being presented, which is a question about playback and not about
 * animation: a word is waiting until its own interval starts, active while it is
 * being sung, and passed afterwards.
 */
enum class LyricWordState {
    Waiting,
    Active,
    Passed,
}

/**
 * A word's state and how far it has travelled between the three poses.
 *
 * [arrive] and [settle] are continuous because a state that snapped would read as a
 * glitch rather than as an entrance; [state] is the discrete answer beside them, so
 * a caller that only wants to know whether a word has been sung does not have to
 * interpret an interpolation.
 */
data class LyricWordPresentation(
    val word: LyricWord,
    val state: LyricWordState,
    /** How far the glow has crossed this word, 0..1, grapheme by grapheme. */
    val sweep: Float,
    /** 0 while waiting, 1 once the word has taken its sung pose. */
    val arrive: Float,
    /** 0 until the word ends, then 1 once it has settled back. */
    val settle: Float,
)

/** The words of one line, and how trustworthy their intervals are. */
data class LyricWordTimeline(
    val source: LyricGlowTimingSource,
    val words: List<LyricWord>,
) {
    /**
     * Every word's presentation at one moment.
     *
     * [motion] at zero means the picture is still: every word takes its sung pose and
     * stays there, so a listener who has turned motion off reads the line as plain
     * text instead of watching it assemble. The sweep is deliberately *not* gated --
     * it is a readout of playback, not an animation, exactly as the wipe is in every
     * other mode.
     */
    fun presentationAt(positionMillis: Long, motion: Float): List<LyricWordPresentation> {
        val still = motion <= 0f
        return words.map { word ->
            val state = when {
                positionMillis >= word.endMillis -> LyricWordState.Passed
                positionMillis >= word.startMillis -> LyricWordState.Active
                else -> LyricWordState.Waiting
            }
            LyricWordPresentation(
                word = word,
                state = state,
                sweep = word.sweepAt(positionMillis),
                arrive = if (still) 1f else progress(positionMillis - word.startMillis, ArriveMillis),
                settle = if (still) 0f else progress(positionMillis - word.endMillis, SettleMillis),
            )
        }
    }
}

/** How far the glow has crossed one word. */
fun LyricWord.sweepAt(positionMillis: Long): Float {
    if (positionMillis >= endMillis) return 1f
    if (positionMillis <= startMillis) return 0f
    val total = graphemes.size.coerceAtLeast(1)
    var completed = 0
    var partial = 0f
    graphemes.forEach { grapheme ->
        if (positionMillis >= grapheme.endMillis) {
            completed += 1
        } else if (positionMillis >= grapheme.startMillis) {
            val duration = (grapheme.endMillis - grapheme.startMillis).coerceAtLeast(1L)
            partial = ((positionMillis - grapheme.startMillis).toFloat() / duration.toFloat())
                .coerceIn(0f, 1f)
        }
    }
    return ((completed + partial) / total).coerceIn(0f, 1f)
}

/**
 * Where a word can be drawn: a contiguous range of the line's display text.
 *
 * Exposed so the boundary rule itself can be tested without a parsed document --
 * "what counts as a word" is the one part of this file that is a judgement call.
 */
internal data class WordSpan(val start: Int, val end: Int)

/**
 * Splits a line into the words a cloud would scatter.
 *
 * The rule is the one the timing estimator already uses, so the two agree on the
 * same line: whitespace ends a word, and a run that contains CJK is split per
 * character -- Chinese lyrics are written without spaces, so a character *is* the
 * unit a cloud can move. Punctuation never starts a word of its own unless it has
 * nothing to follow, so "好，" travels with the character it belongs to instead of
 * drifting off by itself.
 */
internal fun displayWordSpans(text: String): List<WordSpan> {
    val graphemes = graphemeRanges(text, 0, text.length)
    val spans = mutableListOf<WordSpan>()
    var start = -1
    var end = -1
    var containsCjk = false

    fun flush() {
        if (start >= 0) spans += WordSpan(start, end)
        start = -1
        end = -1
        containsCjk = false
    }

    graphemes.forEach { range ->
        val codePoint = text.codePointAt(range.start)
        when {
            text.substring(range.start, range.end).isBlank() -> flush()
            codePoint.isPunctuationCodePoint() -> {
                if (start < 0) start = range.start
                end = range.end
            }
            else -> {
                val cjk = codePoint.isCjkCodePoint()
                if (start >= 0 && (containsCjk || cjk)) flush()
                if (start < 0) start = range.start
                containsCjk = containsCjk || cjk
                end = range.end
            }
        }
    }
    flush()
    return spans
}

/**
 * The word projection of one line, or null when there is nothing to project -- word
 * glow turned off, or a line with no text to time.
 */
fun TimedTextLine.lyricWordTimeline(
    format: TimedTextFormat,
    nextLineStartMillis: Long?,
    mode: LyricsWordGlowMode,
): LyricWordTimeline? {
    val timeline = lyricGlowTimeline(format, nextLineStartMillis, mode) ?: return null
    val spans = displayWordSpans(text)
    if (spans.isEmpty()) return null

    // Untimed text is the one thing the grapheme timeline cannot place: exact word
    // timing skips whatever sits between two tags. Such a word is shown at whatever
    // the previous one ended at rather than dropped, so the line never loses text --
    // it is simply a word that never glows, which is the truth about it.
    var cursor = timeline.segments.firstOrNull()?.startMillis ?: startMillis
    val words = spans.map { span ->
        val segments = timeline.segments.filter { it.endOffset > span.start && it.startOffset < span.end }
        val wordStart = segments.firstOrNull()?.startMillis ?: cursor
        val wordEnd = (segments.lastOrNull()?.endMillis ?: cursor).coerceAtLeast(wordStart)
        cursor = wordEnd
        LyricWord(
            text = text.substring(span.start, span.end),
            startOffset = span.start,
            endOffset = span.end,
            startMillis = wordStart,
            endMillis = wordEnd,
            graphemes = segments,
        )
    }
    return LyricWordTimeline(timeline.source, words).takeIf { it.words.isNotEmpty() }
}

/** How far into an interval of [durationMillis] a moment [sinceMillis] is. */
private fun progress(sinceMillis: Long, durationMillis: Long): Float =
    (sinceMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)

/** How long a word takes to travel in, and to settle back after it is sung. */
private const val ArriveMillis = 260L
private const val SettleMillis = 320L
