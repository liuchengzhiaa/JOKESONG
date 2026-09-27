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
    fun `snapshot distinguishes future active and completed graphemes`() {
        val line = TimedTextLine(1_000L, 2_000L, "AB")
        val timeline = line.lyricGlowTimeline(
            TimedTextFormat.Vtt,
            nextLineStartMillis = null,
            mode = LyricsWordGlowMode.Smart,
        )!!

        assertEquals(LyricGlowSnapshot(0, -1), timeline.snapshotAt(999L))
        assertEquals(LyricGlowSnapshot(0, 0), timeline.snapshotAt(1_100L))
        assertEquals(LyricGlowSnapshot(1, 1), timeline.snapshotAt(1_600L))
        assertEquals(LyricGlowSnapshot(2, -1), timeline.snapshotAt(2_000L))
        assertTrue(timeline.nextBoundaryAfter(1_100L)!! > 1_100L)
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
