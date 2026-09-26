package com.example.asmrplayer.core.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedTextDocumentTest {

    /** Lines at 10s, 20s and 30s, each with no stated end -- the plain LRC shape. */
    private val document = TimedTextDocument(
        format = TimedTextFormat.Lrc,
        lines = listOf(
            TimedTextLine(10_000, null, "第一句"),
            TimedTextLine(20_000, null, "第二句"),
            TimedTextLine(30_000, null, "第三句"),
        ),
    )

    private val untimed = TimedTextDocument(
        format = TimedTextFormat.Lrc,
        untimedText = listOf("没有时间的歌词"),
    )

    @Test
    fun `before the first line nothing is showing`() {
        assertEquals(-1, document.lineIndexAt(0))
        assertEquals(-1, document.lineIndexAt(9_999))
        assertNull(document.lineAt(9_999))
    }

    @Test
    fun `a line shows from its own start until the next one starts`() {
        assertEquals(0, document.lineIndexAt(10_000))
        assertEquals(0, document.lineIndexAt(19_999))
        assertEquals(1, document.lineIndexAt(20_000))
        assertEquals(2, document.lineIndexAt(30_000))
    }

    @Test
    fun `after the last line the last line stays up`() {
        assertEquals(2, document.lineIndexAt(31_000))
        assertEquals(2, document.lineIndexAt(Long.MAX_VALUE))
        assertEquals("第三句", document.lineAt(600_000)?.text)
    }

    @Test
    fun `a gap keeps showing the earlier line`() {
        // An SRT whose cues do not touch: the line is still on screen while the
        // next one has not started, which is the same rule the LRC lines follow.
        val gapped = TimedTextDocument(
            format = TimedTextFormat.Srt,
            lines = listOf(
                TimedTextLine(1_000, 2_000, "前面"),
                TimedTextLine(9_000, 10_000, "后面"),
            ),
        )

        assertEquals(0, gapped.lineIndexAt(5_000))
    }

    @Test
    fun `seeking to a line is seeking to its start`() {
        assertEquals(20_000L, document.seekMillisForLine(1))
        assertNull(document.seekMillisForLine(3))
        assertNull(document.seekMillisForLine(-1))
    }

    @Test
    fun `the neighbourhood is the window around the centre`() {
        // One line either side, cut off at the ends of the document rather than
        // reaching past them.
        assertEquals(0..1, document.neighbourhood(centreIndex = 0, radius = 1))
        assertEquals(0..2, document.neighbourhood(centreIndex = 1, radius = 1))
        assertEquals(1..2, document.neighbourhood(centreIndex = 2, radius = 1))
        // A radius wider than the document is the whole document, not an error.
        assertEquals(0..2, document.neighbourhood(centreIndex = 1, radius = 5))
        assertEquals(1..1, document.neighbourhood(centreIndex = 1, radius = 0))
    }

    @Test
    fun `a neighbourhood of nothing is empty rather than an error`() {
        assertEquals(IntRange.EMPTY, document.neighbourhood(centreIndex = 1, radius = -1))
        assertEquals(IntRange.EMPTY, untimed.neighbourhood(centreIndex = 0, radius = 2))
        // -1 is what lineIndexAt reports before the first line; a display that asks
        // for the window around "nothing yet" must still get the opening lines.
        assertEquals(0..1, document.neighbourhood(centreIndex = -1, radius = 1))
    }

    @Test
    fun `an untimed document shows nothing on the timeline`() {
        assertFalse(untimed.isTimed)
        assertEquals(-1, untimed.lineIndexAt(0))
        assertNull(untimed.lineAt(0))
        assertNull(untimed.seekMillisForLine(0))
        assertEquals(listOf("没有时间的歌词"), untimed.untimedText)
    }

    @Test
    fun `a parsed document with no times has no lines`() {
        val parsed = parseTimedText(TimedTextFormat.Lrc, "只有文字\n")

        assertFalse(parsed.isTimed)
        assertEquals(-1, parsed.lineIndexAt(0))
    }

    @Test
    fun `a line with no word timing says so by having no words`() {
        // Empty, not a single word spanning the line: a consumer must be able to tell
        // "no finer timing available" from "one word that lasts the whole line".
        assertTrue(document.lines.all { it.words.isEmpty() })
    }
}
