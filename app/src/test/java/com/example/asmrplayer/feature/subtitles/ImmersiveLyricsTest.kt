package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextLine
import com.example.asmrplayer.core.subtitles.TimedTextWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImmersiveLyricsTest {
    private val lines = (0 until 6).map { index ->
        TimedTextLine(
            startMillis = 1_000L + index * 1_000L,
            endMillis = null,
            text = "line $index",
        )
    }
    private val document = TimedTextDocument(TimedTextFormat.Lrc, lines = lines)

    @Test
    fun `scene keeps only two context lines on either side`() {
        val frame = document.immersiveLyricsFrame(3_500L)!!

        assertEquals("line 0", frame.previousFar?.text)
        assertEquals("line 1", frame.previous?.text)
        assertEquals("line 2", frame.current.text)
        assertEquals("line 3", frame.next?.text)
        assertEquals("line 4", frame.nextFar?.text)
        assertEquals(2, frame.currentIndex)
    }

    @Test
    fun `scene clamps context without fabricating edge lines`() {
        val first = document.immersiveLyricsFrame(1_000L)!!
        val last = document.immersiveLyricsFrame(99_000L)!!

        assertNull(first.previousFar)
        assertNull(first.previous)
        assertEquals("line 1", first.next?.text)
        assertEquals("line 4", last.previous?.text)
        assertNull(last.next)
        assertNull(last.nextFar)
    }

    @Test
    fun `scene is absent before the first timed line`() {
        assertNull(document.immersiveLyricsFrame(999L))
    }

    @Test
    fun `enhanced lrc word selection follows the last started word`() {
        val line = TimedTextLine(
            startMillis = 1_000L,
            endMillis = null,
            text = "one two three",
            words = listOf(
                TimedTextWord(1_000L, 1_400L, "one"),
                TimedTextWord(1_400L, 1_900L, "two"),
                TimedTextWord(1_900L, null, "three"),
            ),
        )

        assertEquals(-1, line.activeWordIndexAt(999L))
        assertEquals(0, line.activeWordIndexAt(1_000L))
        assertEquals(1, line.activeWordIndexAt(1_899L))
        assertEquals(2, line.activeWordIndexAt(9_000L))
    }

    @Test
    fun `ordinary timed line reports no active word`() {
        assertEquals(-1, lines.first().activeWordIndexAt(4_000L))
    }
}
