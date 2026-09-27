package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLyricsTest {
    private val document = TimedTextDocument(
        format = TimedTextFormat.Lrc,
        lines = listOf(
            TimedTextLine(1_000, null, "first"),
            TimedTextLine(2_000, null, "second"),
            TimedTextLine(3_000, null, "third"),
        ),
    )

    @Test
    fun `cover frame contains only previous current and next lines`() {
        assertEquals(
            CoverLyricsFrame("first", "second", "third", 1),
            document.coverLyricsFrame(2_500),
        )
    }

    @Test
    fun `cover frame clamps its context at document edges`() {
        assertEquals(CoverLyricsFrame(null, "first", "second", 0), document.coverLyricsFrame(1_000))
        assertEquals(CoverLyricsFrame("second", "third", null, 2), document.coverLyricsFrame(9_000))
    }

    @Test
    fun `cover frame stays absent before timing begins or without timing`() {
        assertNull(document.coverLyricsFrame(999))
        assertNull(
            TimedTextDocument(
                format = TimedTextFormat.Lrc,
                untimedText = listOf("plain text"),
            ).coverLyricsFrame(4_000),
        )
    }

    @Test
    fun `cover glow uses the same bounded smart timeline as immersive lyrics`() {
        val frame = document.coverLyricsFrame(2_500)!!

        val timeline = document.coverLyricsGlowTimeline(frame, LyricsWordGlowMode.Smart)!!

        assertEquals(LyricGlowTimingSource.Estimated, timeline.source)
        assertTrue(timeline.segments.isNotEmpty())
        assertNull(document.coverLyricsGlowTimeline(frame, LyricsWordGlowMode.Off))
    }

    @Test
    fun `outgoing cover frame cannot borrow another line timing`() {
        val stale = CoverLyricsFrame("first", "no longer second", "third", 1)

        assertNull(document.coverLyricsGlowTimeline(stale, LyricsWordGlowMode.Smart))
    }

    @Test
    fun `bright palette receives a stronger readability shade`() {
        val dark = coverLyricsShadeAlpha(listOf(0xFF101010.toInt()), 0xFF000000.toInt())
        val bright = coverLyricsShadeAlpha(listOf(0xFFFFFFFF.toInt()), 0xFF000000.toInt())

        assertTrue(bright > dark)
        assertTrue(dark in 0.36f..0.72f)
        assertTrue(bright in 0.36f..0.72f)
    }

    @Test
    fun `shade uses fallback when extracted palette is empty`() {
        assertEquals(
            coverLyricsShadeAlpha(listOf(0xFF909090.toInt()), 0xFF000000.toInt()),
            coverLyricsShadeAlpha(emptyList(), 0xFF909090.toInt()),
            0.0001f,
        )
    }
}
