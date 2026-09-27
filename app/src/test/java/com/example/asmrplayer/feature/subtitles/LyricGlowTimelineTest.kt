package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextLine
import com.example.asmrplayer.core.subtitles.TimedTextWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricGlowTimelineTest {
    @Test
    fun `smart mode prefers exact word timing and divides it into graphemes`() {
        val line = TimedTextLine(
            startMillis = 1_000L,
            endMillis = null,
            text = "one two",
            words = listOf(
                TimedTextWord(1_000L, 1_600L, "one"),
                TimedTextWord(1_600L, 2_200L, "two"),
            ),
        )

        val timeline = line.lyricGlowTimeline(
            TimedTextFormat.Lrc,
            nextLineStartMillis = 3_000L,
            mode = LyricsWordGlowMode.Smart,
        )!!

        assertEquals(LyricGlowTimingSource.Exact, timeline.source)
        assertEquals(6, timeline.segments.size)
        assertEquals(1_000L, timeline.segments.first().startMillis)
        assertEquals(2_200L, timeline.segments.last().endMillis)
    }

    @Test
    fun `smart mode estimates an ordinary lrc line from its next line`() {
        val line = TimedTextLine(1_000L, null, "你好 world")

        val timeline = line.lyricGlowTimeline(
            TimedTextFormat.Lrc,
            nextLineStartMillis = 5_000L,
            mode = LyricsWordGlowMode.Smart,
        )!!

        assertEquals(LyricGlowTimingSource.Estimated, timeline.source)
        assertEquals(7, timeline.segments.size)
        assertEquals(1_000L, timeline.segments.first().startMillis)
        assertEquals(4_600L, timeline.segments.last().endMillis)
    }

    @Test
    fun `exact-only mode refuses a line-only estimate`() {
        val line = TimedTextLine(1_000L, null, "ordinary line")

        assertNull(
            line.lyricGlowTimeline(
                TimedTextFormat.Lrc,
                nextLineStartMillis = 4_000L,
                mode = LyricsWordGlowMode.ExactOnly,
            ),
        )
    }

    @Test
    fun `smart lrc estimate caps an implausibly long gap`() {
        val line = TimedTextLine(1_000L, null, "你好")
        val timeline = line.lyricGlowTimeline(
            TimedTextFormat.Lrc,
            nextLineStartMillis = 30_000L,
            mode = LyricsWordGlowMode.Smart,
        )!!

        // Two graphemes read at 500 ms each, plus a two-second tail, then the
        // visualizer uses 90% of that bounded interval for the sweep.
        assertEquals(3_700L, timeline.segments.last().endMillis)
    }

    @Test
    fun `sweep distinguishes future active and completed graphemes`() {
        val line = TimedTextLine(1_000L, 2_000L, "AB")
        val timeline = line.lyricGlowTimeline(
            TimedTextFormat.Vtt,
            nextLineStartMillis = null,
            mode = LyricsWordGlowMode.Smart,
        )!!

        // Two graphemes over 1_000..1_900: A holds 1_000..1_450, B holds 1_450..1_900.
        val beforeStart = timeline.sweepAt(999L)
        assertEquals(0, beforeStart.completedCount)
        assertEquals(-1, beforeStart.activeIndex)

        val onA = timeline.sweepAt(1_100L)
        assertEquals(0, onA.completedCount)
        assertEquals(0, onA.activeIndex)
        assertEquals(2f / 9f, onA.fraction, 1e-4f)

        val onB = timeline.sweepAt(1_600L)
        assertEquals(1, onB.completedCount)
        assertEquals(1, onB.activeIndex)
        assertEquals(1f / 3f, onB.fraction, 1e-4f)

        val afterEnd = timeline.sweepAt(2_000L)
        assertEquals(2, afterEnd.completedCount)
        assertEquals(-1, afterEnd.activeIndex)
    }

    @Test
    fun `the wipe front crosses a grapheme boundary instead of stepping over it`() {
        val line = TimedTextLine(1_000L, 2_000L, "AB")
        val timeline = line.lyricGlowTimeline(
            TimedTextFormat.Vtt,
            nextLineStartMillis = null,
            mode = LyricsWordGlowMode.Smart,
        )!!

        // The front, expressed in graphemes, must never go backwards and must
        // never advance by a whole grapheme in one millisecond. This is the
        // property a per-grapheme highlight cannot satisfy.
        var previous = -1f
        for (position in 900L..2_100L) {
            val sweep = timeline.sweepAt(position)
            val front = sweep.completedCount + sweep.fraction
            assertTrue("front went backwards at $position", front >= previous)
            assertTrue("front jumped at $position", front - previous <= 1f)
            previous = front
        }
        assertEquals(2f, previous, 1e-3f)

        // The A/B boundary at 1_450 ms specifically: continuous across it, where
        // the old discrete projection stepped by a full grapheme.
        val before = timeline.sweepAt(1_449L).let { it.completedCount + it.fraction }
        val after = timeline.sweepAt(1_450L).let { it.completedCount + it.fraction }
        assertTrue("boundary stepped by ${after - before}", after - before < 0.01f)
    }

    @Test
    fun `an unstarted line leaves the wipe on its first knot and a finished line on its last`() {
        val line = TimedTextLine(1_000L, 2_000L, "AB")
        val timeline = line.lyricGlowTimeline(
            TimedTextFormat.Vtt,
            nextLineStartMillis = null,
            mode = LyricsWordGlowMode.Smart,
        )!!

        assertEquals(LyricWipeFront(0, 0, 0f), timeline.wipeFrontAt(900L))
        assertEquals(LyricWipeFront(2, 2, 1f), timeline.wipeFrontAt(2_100L))

        // Halfway through A the front is heading for B's left edge, not sitting
        // on A's own.
        val onA = timeline.wipeFrontAt(1_225L)
        assertEquals(0, onA.fromOffset)
        assertEquals(1, onA.toOffset)
        assertEquals(0.5f, onA.fraction, 1e-4f)
    }

    @Test
    fun `the wipe front aims at the next grapheme so untimed gaps are swept too`() {
        val line = TimedTextLine(
            startMillis = 1_000L,
            endMillis = null,
            text = "one two three",
            words = listOf(
                TimedTextWord(1_000L, 1_600L, "one"),
                TimedTextWord(2_000L, 2_600L, "three"),
            ),
        )
        val timeline = line.lyricGlowTimeline(
            TimedTextFormat.Lrc,
            nextLineStartMillis = 3_000L,
            mode = LyricsWordGlowMode.Smart,
        )!!

        // "one" holds offsets 0..3 and "three" starts at 8, so the last grapheme
        // of the first word interpolates across the untimed " two " instead of
        // teleporting over it when the word changes.
        val inWord = timeline.wipeFrontAt(1_500L)
        assertEquals(2, inWord.fromOffset)
        assertEquals(8, inWord.toOffset)

        // Between the two words the front waits on the next knot, which is
        // exactly where that interpolation was heading.
        val inGap = timeline.wipeFrontAt(1_800L)
        assertEquals(8, inGap.fromOffset)
        assertEquals(8, inGap.toOffset)
    }

    @Test
    fun `every wipe front stays inside the text it wipes`() {
        val line = TimedTextLine(
            startMillis = 1_000L,
            endMillis = null,
            text = "one two three",
            words = listOf(
                TimedTextWord(1_000L, 1_600L, "one"),
                TimedTextWord(2_000L, 2_600L, "three"),
            ),
        )
        val timeline = line.lyricGlowTimeline(
            TimedTextFormat.Lrc,
            nextLineStartMillis = 3_000L,
            mode = LyricsWordGlowMode.Smart,
        )!!

        // The renderer hands these offsets straight to TextLayoutResult lookups,
        // which are only defined inside 0..text.length.
        for (position in 900L..3_100L) {
            val front = timeline.wipeFrontAt(position)
            assertTrue("from ${front.fromOffset} at $position", front.fromOffset in 0..line.text.length)
            assertTrue("to ${front.toOffset} at $position", front.toOffset in front.fromOffset..line.text.length)
            assertTrue("fraction ${front.fraction} at $position", front.fraction in 0f..1f)
        }
    }

    @Test
    fun `combining sequence stays one display grapheme`() {
        val line = TimedTextLine(0L, 1_000L, "e\u0301")
        val timeline = line.lyricGlowTimeline(
            TimedTextFormat.Srt,
            nextLineStartMillis = null,
            mode = LyricsWordGlowMode.Smart,
        )!!

        assertEquals(1, timeline.segments.size)
        assertEquals(0, timeline.segments.single().startOffset)
        assertEquals(line.text.length, timeline.segments.single().endOffset)
    }
}
