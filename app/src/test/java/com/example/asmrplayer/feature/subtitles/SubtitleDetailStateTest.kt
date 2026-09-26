package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleDetailStateTest {

    private val timed = TimedTextDocument(
        format = TimedTextFormat.Lrc,
        lines = listOf(TimedTextLine(1_000, null, "第一句")),
    )

    private val untimed = TimedTextDocument(
        format = TimedTextFormat.Lrc,
        untimedText = listOf("没有时间的歌词"),
    )

    private val blank = TimedTextDocument(format = TimedTextFormat.Srt)

    @Test
    fun `a timed document is shown as lines that can follow playback`() {
        val view = SubtitleDetailState(itemId = "a", document = timed).lyricsView()

        assertEquals(LyricsView.Timed(timed), view)
    }

    @Test
    fun `a document with no times but with words is shown as untimed`() {
        // The whole point of the two lists being separate: this must not be mistaken
        // for a timed document with one line, or the display would invent a timeline.
        val view = SubtitleDetailState(itemId = "a", document = untimed).lyricsView()

        assertEquals(LyricsView.Untimed(listOf("没有时间的歌词")), view)
    }

    @Test
    fun `a parsed document with nothing in it is reported as empty`() {
        val view = SubtitleDetailState(
            itemId = "a",
            sourceName = "Track.srt",
            document = blank,
        ).lyricsView()

        assertEquals(LyricsView.EmptyFile("Track.srt"), view)
    }

    @Test
    fun `no file and an unreadable file are told apart`() {
        // Both leave the reader looking at the artwork, and the sentence is different
        // for each: one has nothing to offer, the other has a file that did not open.
        val nothing = SubtitleDetailState(itemId = "a").lyricsView()
        val unreadable = SubtitleDetailState(itemId = "a", sourceName = "Track.lrc").lyricsView()

        assertEquals(LyricsView.NoFile(null), nothing)
        assertEquals(LyricsView.NoFile("Track.lrc"), unreadable)
    }

    @Test
    fun `reading wins over whatever was on screen before`() {
        // A queue advance loads the next track while the previous document is still
        // in hand; showing those lines would attribute one track's words to another.
        val view = SubtitleDetailState(
            itemId = "b",
            isLoading = true,
            document = timed,
        ).lyricsView()

        assertEquals(LyricsView.Loading, view)
    }

    @Test
    fun `a state knows which track it describes`() {
        val state = SubtitleDetailState(itemId = "track-a")

        assertTrue(state.describes("track-a"))
        assertFalse(state.describes("track-b"))
        assertFalse(state.describes(null))
        assertFalse(SubtitleDetailState().describes("track-a"))
    }

    @Test
    fun `an unmatched state names the track so the surface can tell it is stale`() {
        val state = SubtitleDetailState.unmatched("track-a")

        assertEquals("track-a", state.itemId)
        assertEquals(null, state.document)
        assertFalse(state.isLoading)
    }

    @Test
    fun `the matched file's name and language travel with the state`() {
        val state = SubtitleDetailState(
            itemId = "a",
            sourceName = "Track.zh.lrc",
            languageTag = "zh",
            document = timed,
        )

        assertEquals("Track.zh.lrc", state.sourceName)
        assertEquals("zh", state.languageTag)
    }
}
