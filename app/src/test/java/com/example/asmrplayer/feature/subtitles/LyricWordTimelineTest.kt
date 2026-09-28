package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextLine
import com.example.asmrplayer.core.subtitles.TimedTextWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricWordTimelineTest {
    /**
     * What counts as a word is the one judgement call in the projection, so it is
     * tested on its own: whitespace separates, CJK splits per character because a
     * Chinese lyric line is written without spaces, and punctuation stays with the
     * character it belongs to rather than drifting off on its own.
     */
    @Test
    fun `latin words split on whitespace`() {
        assertEquals(
            listOf("hello", "world"),
            displayWordSpans("hello world").map { "hello world".substring(it.start, it.end) },
        )
        assertEquals(
            listOf("I", "love", "you"),
            displayWordSpans("  I  love   you  ")
                .map { "  I  love   you  ".substring(it.start, it.end) },
        )
    }

    @Test
    fun `a cjk run splits per character and keeps its punctuation attached`() {
        val text = "你好，世界"
        assertEquals(
            listOf("你", "好，", "世", "界"),
            displayWordSpans(text).map { text.substring(it.start, it.end) },
        )
    }

    @Test
    fun `punctuation that has nothing to follow starts a word`() {
        val text = "……然后"
        assertEquals(
            listOf("……", "然", "后"),
            displayWordSpans(text).map { text.substring(it.start, it.end) },
        )
    }

    @Test
    fun `word spans are ordered, disjoint and cover every non-blank character`() {
        val text = "one 二字 three"
        val spans = displayWordSpans(text)

        assertEquals(spans.sortedBy { it.start }, spans)
        spans.zipWithNext().forEach { (first, second) ->
            assertTrue("$first overlaps $second", first.end <= second.start)
        }
        assertEquals("one二字three", spans.map { text.substring(it.start, it.end) }.joinToString(""))
    }

    @Test
    fun `blank text produces no words at all`() {
        assertTrue(displayWordSpans("").isEmpty())
        assertTrue(displayWordSpans("   ").isEmpty())
    }

    @Test
    fun `an exact word timing is projected word for word`() {
        val line = TimedTextLine(
            startMillis = 1_000L,
            endMillis = 3_000L,
            text = "hold me now",
            words = listOf(
                TimedTextWord(startMillis = 1_000L, endMillis = 1_600L, text = "hold"),
                TimedTextWord(startMillis = 1_600L, endMillis = 2_200L, text = "me"),
                TimedTextWord(startMillis = 2_200L, endMillis = 3_000L, text = "now"),
            ),
        )

        val timeline = requireNotNull(
            line.lyricWordTimeline(TimedTextFormat.Lrc, nextLineStartMillis = 4_000L, mode = LyricsWordGlowMode.Smart),
        )

        assertSame(LyricGlowTimingSource.Exact, timeline.source)
        assertEquals(listOf("hold", "me", "now"), timeline.words.map { it.text })
        assertEquals(1_000L, timeline.words[0].startMillis)
        assertEquals(1_600L, timeline.words[1].startMillis)
        assertEquals(3_000L, timeline.words[2].endMillis)
    }

    @Test
    fun `a line with no word timing is estimated and says so`() {
        val line = TimedTextLine(startMillis = 1_000L, endMillis = null, text = "光 落 下 来")

        val timeline = requireNotNull(
            line.lyricWordTimeline(TimedTextFormat.Lrc, nextLineStartMillis = 5_000L, mode = LyricsWordGlowMode.Smart),
        )

        assertSame(LyricGlowTimingSource.Estimated, timeline.source)
        assertEquals(listOf("光", "落", "下", "来"), timeline.words.map { it.text })
    }

    @Test
    fun `word intervals never go backwards and stay inside the line`() {
        val line = TimedTextLine(startMillis = 2_000L, endMillis = null, text = "一二三四五六")

        val timeline = requireNotNull(
            line.lyricWordTimeline(TimedTextFormat.Lrc, nextLineStartMillis = 9_000L, mode = LyricsWordGlowMode.Smart),
        )

        timeline.words.forEach { word ->
            assertTrue("${word.text} ends before it starts", word.endMillis >= word.startMillis)
        }
        timeline.words.zipWithNext().forEach { (first, second) ->
            assertEquals("${second.text} starts before ${first.text} ends", first.endMillis, second.startMillis)
        }
        assertTrue(timeline.words.first().startMillis >= 2_000L)
        assertTrue(timeline.words.last().endMillis <= 9_000L)
    }

    @Test
    fun `every grapheme of the line is inside exactly one word`() {
        val line = TimedTextLine(startMillis = 500L, endMillis = 4_000L, text = "夜 风 轻轻")

        val timeline = requireNotNull(
            line.lyricWordTimeline(TimedTextFormat.Lrc, nextLineStartMillis = 5_000L, mode = LyricsWordGlowMode.Smart),
        )

        val covered = timeline.words.flatMap { word -> word.graphemes.map { it.startOffset } }
        val all = graphemeRanges(line.text, 0, line.text.length)
            .map { it.start }
            .filter { !line.text.substring(it, it + 1).isBlank() }
        assertEquals(all, covered)
    }

    @Test
    fun `the same line projects to the same words every time`() {
        val line = TimedTextLine(startMillis = 3_000L, endMillis = null, text = "repeat me")

        val first = line.lyricWordTimeline(TimedTextFormat.Lrc, 8_000L, LyricsWordGlowMode.Smart)
        val second = line.lyricWordTimeline(TimedTextFormat.Lrc, 8_000L, LyricsWordGlowMode.Smart)

        assertEquals(first, second)
    }

    @Test
    fun `turning word glow off produces no timeline at all`() {
        val line = TimedTextLine(startMillis = 1_000L, endMillis = 2_000L, text = "quiet")

        assertNull(line.lyricWordTimeline(TimedTextFormat.Lrc, 3_000L, LyricsWordGlowMode.Off))
    }

    @Test
    fun `an exact word timing that skips text keeps that text in the cloud`() {
        // "啊" sits between two timed words and carries no timing of its own. It is
        // shown at the moment its predecessor ended rather than being dropped.
        val line = TimedTextLine(
            startMillis = 1_000L,
            endMillis = 4_000L,
            text = "wait 啊 sing",
            words = listOf(
                TimedTextWord(startMillis = 1_000L, endMillis = 2_000L, text = "wait"),
                TimedTextWord(startMillis = 3_000L, endMillis = 4_000L, text = "sing"),
            ),
        )

        val timeline = requireNotNull(
            line.lyricWordTimeline(TimedTextFormat.Lrc, nextLineStartMillis = 5_000L, mode = LyricsWordGlowMode.ExactOnly),
        )

        assertEquals(listOf("wait", "啊", "sing"), timeline.words.map { it.text })
        val untimed = timeline.words[1]
        assertEquals(2_000L, untimed.startMillis)
        assertEquals(2_000L, untimed.endMillis)
        assertTrue(untimed.graphemes.isEmpty())
    }

    @Test
    fun `a word is waiting, sung and then passed`() {
        val line = TimedTextLine(
            startMillis = 1_000L,
            endMillis = 3_000L,
            text = "one two",
            words = listOf(
                TimedTextWord(startMillis = 1_000L, endMillis = 2_000L, text = "one"),
                TimedTextWord(startMillis = 2_000L, endMillis = 3_000L, text = "two"),
            ),
        )
        val timeline = requireNotNull(
            line.lyricWordTimeline(TimedTextFormat.Lrc, 4_000L, LyricsWordGlowMode.ExactOnly),
        )

        val states = listOf(500L, 1_500L, 2_500L, 3_500L).map { position ->
            timeline.presentationAt(position, motion = 1f).map { it.state }
        }

        assertEquals(listOf(LyricWordState.Waiting, LyricWordState.Waiting), states[0])
        assertEquals(listOf(LyricWordState.Active, LyricWordState.Waiting), states[1])
        assertEquals(listOf(LyricWordState.Passed, LyricWordState.Active), states[2])
        assertEquals(listOf(LyricWordState.Passed, LyricWordState.Passed), states[3])
    }

    @Test
    fun `at zero motion every word is already in its sung pose`() {
        val line = TimedTextLine(startMillis = 1_000L, endMillis = 1_500L, text = "still")

        val timeline = requireNotNull(
            line.lyricWordTimeline(TimedTextFormat.Lrc, 2_000L, LyricsWordGlowMode.Smart),
        )

        // Before the line has started, and after it has ended: the pose does not
        // depend on playback, which is what "motion off is a still picture" means.
        listOf(0L, 1_200L, 9_000L).forEach { position ->
            val word = timeline.presentationAt(position, motion = 0f).single()
            assertEquals("position $position", 1f, word.arrive, 0f)
            assertEquals("position $position", 0f, word.settle, 0f)
        }
    }

    @Test
    fun `a word arrives over its own opening and settles only after it ends`() {
        val line = TimedTextLine(
            startMillis = 1_000L,
            endMillis = 2_000L,
            text = "arriving",
            words = listOf(TimedTextWord(startMillis = 1_000L, endMillis = 2_000L, text = "arriving")),
        )
        val timeline = requireNotNull(
            line.lyricWordTimeline(TimedTextFormat.Lrc, 3_000L, LyricsWordGlowMode.ExactOnly),
        )

        fun wordAt(position: Long) = timeline.presentationAt(position, motion = 1f).single()

        assertEquals(0f, wordAt(1_000L).arrive, 0f)
        assertEquals(0.5f, wordAt(1_130L).arrive, 0.02f)
        assertEquals(1f, wordAt(1_400L).arrive, 0f)
        // Nothing settles while the word is still being sung, however long the word.
        assertEquals(0f, wordAt(1_999L).settle, 0f)
        assertEquals(0.5f, wordAt(2_160L).settle, 0.02f)
        assertEquals(1f, wordAt(2_500L).settle, 0f)
    }

    @Test
    fun `the glow crosses a word grapheme by grapheme`() {
        val line = TimedTextLine(
            startMillis = 0L,
            endMillis = 2_000L,
            text = "abcd",
            words = listOf(TimedTextWord(startMillis = 0L, endMillis = 2_000L, text = "abcd")),
        )
        val timeline = requireNotNull(
            line.lyricWordTimeline(TimedTextFormat.Lrc, 3_000L, LyricsWordGlowMode.ExactOnly),
        )
        val word = timeline.words.single()

        assertEquals(0f, word.sweepAt(-1L), 0f)
        assertEquals(0.5f, word.sweepAt(1_000L), 0.01f)
        assertEquals(1f, word.sweepAt(2_000L), 0f)
        assertTrue(word.sweepAt(500L) < word.sweepAt(1_500L))
    }

    @Test
    fun `an untimed word never glows but still has a place in the cloud`() {
        val line = TimedTextLine(startMillis = 1_000L, endMillis = 4_000L, text = "wait 啊 sing")

        val timeline = requireNotNull(
            line.lyricWordTimeline(TimedTextFormat.Lrc, 5_000L, LyricsWordGlowMode.Smart),
        )

        timeline.words.forEach { word ->
            assertEquals("${word.text} has no graphemes to sweep", 0f, word.sweepAt(word.startMillis), 0f)
        }
    }
}
