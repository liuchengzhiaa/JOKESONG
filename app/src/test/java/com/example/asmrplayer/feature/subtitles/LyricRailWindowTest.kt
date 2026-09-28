package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextLine
import com.example.asmrplayer.ui.lyrics.asRailWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricRailWindowTest {
    private val document = TimedTextDocument(
        format = TimedTextFormat.Lrc,
        lines = (0 until 50).map { index ->
            TimedTextLine(
                startMillis = (index + 1) * 1_000L,
                endMillis = null,
                text = "line $index",
            )
        },
    )

    @Test
    fun `before the first line the rail arranges itself around line zero without highlighting it`() {
        val window = document.lyricRailWindow(positionMillis = 0L)

        assertEquals(0, window.startIndex)
        assertEquals(0f, window.anchor, 0f)
        assertFalse(window.hasAnchor)
        // The geometry is already in place, so the wheel does not jump when singing starts.
        assertEquals(0f, window.distanceFromAnchor(0), 0f)
    }

    @Test
    fun `a line being sung puts the anchor on that line`() {
        val window = document.lyricRailWindow(positionMillis = 20_500L)

        assertTrue(window.hasAnchor)
        // Document line 19 (start 20_000) is anchored, and it is the eighth entry.
        assertEquals(12, window.startIndex)
        assertEquals(7f, window.anchor, 0f)
        assertEquals("line 19", window.lines[7].text)
    }

    /**
     * The wheel's clockwork is driven from the anchor's position in the *document*,
     * not in the window, so that seeking down a song does not rewind it. This is the
     * identity that makes that possible: once singing has started, `startIndex + anchor`
     * is the anchor line's document index, whatever window it happens to sit in.
     *
     * Before the first line the two differ on purpose — the anchor is pinned to line
     * zero so the wheel is already in position rather than jumping when singing starts.
     */
    @Test
    fun `the anchor's document position is the window start plus the anchor`() {
        listOf(1_000L, 20_500L, 30_400L, 49_500L).forEach { position ->
            val window = document.lyricRailWindow(positionMillis = position)

            assertEquals(
                "position $position",
                document.lineIndexAt(position).toFloat(),
                window.startIndex + window.anchor,
                1e-3f,
            )
        }
    }

    @Test
    fun `past the last line the anchor leaves the window so the wheel keeps turning`() {
        val window = document.lyricRailWindow(positionMillis = 60_000L)

        assertFalse(window.hasAnchor)
        assertEquals(window.lines.size, window.anchor.toInt())
        // Every line sits behind the anchor, which is what carries them off the arc.
        window.lines.indices.forEach { offset ->
            assertTrue(window.distanceFromAnchor(offset) < 0f)
        }
    }

    @Test
    fun `the window never grows past its radius`() {
        val radius = 7
        listOf(0L, 1_000L, 25_000L, 49_000L, 60_000L, Long.MAX_VALUE).forEach { position ->
            val window = document.lyricRailWindow(positionMillis = position, radius = radius)

            assertTrue(
                "position $position gave ${window.lines.size} lines",
                window.lines.size <= 2 * radius + 1,
            )
        }
    }

    @Test
    fun `the window is clamped to the document at both ends`() {
        val atStart = document.lyricRailWindow(positionMillis = 0L)
        assertEquals(0, atStart.startIndex)
        assertEquals(7, atStart.lines.size - 1)

        val atEnd = document.lyricRailWindow(positionMillis = 60_000L)
        assertEquals(49, atEnd.startIndex + atEnd.lines.size - 1)
        assertEquals("line 49", atEnd.lines.last().text)
    }

    @Test
    fun `distance from the anchor is monotonic across the window`() {
        val window = document.lyricRailWindow(positionMillis = 25_000L)

        val distances = window.lines.indices.map(window::distanceFromAnchor)
        assertEquals(distances.sorted(), distances)
    }

    /**
     * A rail is not always built from the document. A windowed mode holds a
     * five-line frame when it is reached the ordinary way, and that frame still has
     * to say where in the song it is, or the clockwork would rewind.
     */
    @Test
    fun `a five-line frame reads as a rail that knows its place in the song`() {
        val lines = (0 until 12).map { index ->
            TimedTextLine(startMillis = index * 1_000L, endMillis = null, text = "line $index")
        }

        val rail = frameAt(lines, index = 5).asRailWindow()

        assertEquals(5, rail.lines.size)
        assertTrue(rail.hasAnchor)
        assertEquals("line 5", rail.lines[rail.anchor.toInt()].text)
        assertEquals(5f, rail.startIndex + rail.anchor, 0f)
    }

    @Test
    fun `a frame on the first line has nothing to its left`() {
        val lines = (0 until 3).map { index ->
            TimedTextLine(startMillis = index * 1_000L, endMillis = null, text = "line $index")
        }

        val rail = frameAt(lines, index = 0).asRailWindow()

        assertEquals(0, rail.startIndex)
        assertEquals(0f, rail.anchor, 0f)
        // Nothing behind the first line, but both lines ahead of it are there.
        assertEquals(listOf("line 0", "line 1", "line 2"), rail.lines.map { it.text })
        assertEquals(0f, rail.startIndex + rail.anchor, 0f)
    }

    private fun frameAt(lines: List<TimedTextLine>, index: Int) = ImmersiveLyricsFrame(
        previousFar = lines.getOrNull(index - 2),
        previous = lines.getOrNull(index - 1),
        current = lines[index],
        next = lines.getOrNull(index + 1),
        nextFar = lines.getOrNull(index + 2),
        currentIndex = index,
        activeWordIndex = -1,
    )

    @Test
    fun `an empty document produces an empty window rather than failing`() {
        val window = TimedTextDocument(format = TimedTextFormat.Lrc).lyricRailWindow(0L)

        assertTrue(window.isEmpty)
        assertFalse(window.hasAnchor)
    }

    @Test
    fun `a document shorter than the radius is returned whole`() {
        val short = TimedTextDocument(
            format = TimedTextFormat.Lrc,
            lines = listOf(
                TimedTextLine(startMillis = 500L, endMillis = 1_000L, text = "one"),
                TimedTextLine(startMillis = 1_500L, endMillis = 2_000L, text = "two"),
            ),
        )

        val window = short.lyricRailWindow(positionMillis = 600L)

        assertEquals(2, window.lines.size)
        assertEquals(0, window.startIndex)
        assertEquals(0f, window.anchor, 0f)
        assertTrue(window.hasAnchor)
    }
}
