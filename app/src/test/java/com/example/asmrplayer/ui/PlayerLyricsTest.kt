package com.example.asmrplayer.ui

import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLyricsTest {
    private val document = TimedTextDocument(
        format = TimedTextFormat.Lrc,
        lines = listOf(
            TimedTextLine(startMillis = 5_000L, endMillis = null, text = "first"),
            TimedTextLine(startMillis = 10_000L, endMillis = null, text = "second"),
            TimedTextLine(startMillis = 15_000L, endMillis = null, text = "third"),
        ),
    )

    @Test
    fun `no document and no position both mean nothing to show`() {
        assertTrue(lyricPairAt(null, 12_000L).isEmpty)
        assertTrue(lyricPairAt(document, null).isEmpty)
        assertTrue(lyricPairAt(null, null).isEmpty)
    }

    @Test
    fun `a document with no timed lines has no pair`() {
        val untimed = TimedTextDocument(format = TimedTextFormat.Lrc, untimedText = listOf("words"))

        assertTrue(lyricPairAt(untimed, 12_000L).isEmpty)
    }

    @Test
    fun `before the first line the upcoming line takes the top slot`() {
        val pair = lyricPairAt(document, 0L)

        // The intro of a song is exactly when a listener wants to see what is coming,
        // so the block is not blank just because nothing has been sung yet.
        assertNull(pair.current)
        assertEquals("first", pair.next)
    }

    @Test
    fun `the line being sung leads and the one after it follows`() {
        val pair = lyricPairAt(document, 11_000L)

        assertEquals("second", pair.current)
        assertEquals("third", pair.next)
    }

    @Test
    fun `a line keeps the top slot until the next one starts`() {
        val pair = lyricPairAt(document, 14_999L)

        assertEquals("second", pair.current)
        assertEquals("third", pair.next)
    }

    @Test
    fun `the last line has nothing after it`() {
        val pair = lyricPairAt(document, 20_000L)

        assertEquals("third", pair.current)
        assertNull(pair.next)
        assertFalse(pair.isEmpty)
    }

    @Test
    fun `the exact start of a line is that line`() {
        assertEquals("first", lyricPairAt(document, 5_000L).current)
        assertEquals("third", lyricPairAt(document, 15_000L).current)
    }
    @Test fun `previous current next form the cover window`() {
        val pair = lyricPairAt(document, 11_000L)
        assertEquals("first", pair.previous)
        assertEquals("second", pair.current)
        assertEquals("third", pair.next)
    }
    @Test fun `first and last windows have no invented neighbours`() {
        assertNull(lyricPairAt(document, 5_000L).previous)
        val last = lyricPairAt(document, 20_000L)
        assertEquals("second", last.previous)
        assertNull(last.next)
    }
}
