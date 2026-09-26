package com.example.asmrplayer.core.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleMatchingTest {

    private fun candidate(
        displayName: String,
        format: TimedTextFormat = SupportedTimedText.formatOf(displayName)!!,
        uriSuffix: String = displayName,
    ) = TimedTextCandidate(
        displayName = displayName,
        documentUri = "content://tree/folder/$uriSuffix",
        format = format,
        sizeBytes = 1_024,
        modifiedAtMs = 1_700_000_000_000,
    )

    /**
     * Tracks are keyed by a handle that reads like a document URI, because that is what
     * the scanner passes and what the returned map is keyed by.
     */
    private fun tracks(vararg titles: String): Map<String, String> =
        titles.associate { "content://tree/folder/$it.mp3" to it }

    @Test
    fun `a file named after the track belongs to it and carries no language`() {
        val matches = matchTimedText(
            audioTracks = tracks("Track"),
            candidates = listOf(candidate("Track.lrc")),
        )

        val match = matches.getValue("content://tree/folder/Track.mp3")
        assertEquals("Track", match.audioTitle)
        assertEquals("Track.lrc", match.candidate.displayName)
        assertNull(match.languageTag)
    }

    @Test
    fun `the result is keyed by the caller's handle, not by the title`() {
        val matches = matchTimedText(
            audioTracks = mapOf("a-uri-of-my-choosing" to "Track"),
            candidates = listOf(candidate("Track.lrc")),
        )

        assertEquals(setOf("a-uri-of-my-choosing"), matches.keys)
    }

    @Test
    fun `a language suffix still matches and records the language`() {
        val matches = matchTimedText(
            audioTracks = tracks("Track"),
            candidates = listOf(candidate("Track.zh.lrc")),
        )

        val match = matches.getValue("content://tree/folder/Track.mp3")
        assertEquals("Track.zh.lrc", match.candidate.displayName)
        assertEquals("zh", match.languageTag)
    }

    @Test
    fun `matching ignores case on both sides`() {
        val matches = matchTimedText(
            audioTracks = tracks("TRACK"),
            candidates = listOf(candidate("track.LRC")),
        )

        assertEquals(
            "track.LRC",
            matches.getValue("content://tree/folder/TRACK.mp3").candidate.displayName,
        )
    }

    @Test
    fun `a suffix that is really part of the name is not read as a language`() {
        // The audio title is the file's name with its extension removed, so the
        // scanner hands over `Track.Part.01` whole; only the candidate's own last
        // dot can be a language.
        val matches = matchTimedText(
            audioTracks = tracks("Track.Part.01"),
            candidates = listOf(candidate("Track.Part.01.lrc")),
        )

        val match = matches.getValue("content://tree/folder/Track.Part.01.mp3")
        assertNull(match.languageTag)
        assertEquals("Track.Part.01.lrc", match.candidate.displayName)
    }

    @Test
    fun `an untagged file beats a translated one`() {
        val matches = matchTimedText(
            audioTracks = tracks("Track"),
            candidates = listOf(candidate("Track.zh.lrc"), candidate("Track.lrc")),
        )

        val match = matches.getValue("content://tree/folder/Track.mp3")
        assertEquals("Track.lrc", match.candidate.displayName)
        assertNull(match.languageTag)
    }

    @Test
    fun `chinese beats another language`() {
        val matches = matchTimedText(
            audioTracks = tracks("Track"),
            candidates = listOf(candidate("Track.en.srt"), candidate("Track.zh.srt")),
        )

        val match = matches.getValue("content://tree/folder/Track.mp3")
        assertEquals("Track.zh.srt", match.candidate.displayName)
        assertEquals("zh", match.languageTag)
    }

    @Test
    fun `chinese beats another language whatever order the folder was listed in`() {
        val matches = matchTimedText(
            audioTracks = tracks("Track"),
            candidates = listOf(candidate("Track.zh.srt"), candidate("Track.en.srt")),
        )

        assertEquals(
            "Track.zh.srt",
            matches.getValue("content://tree/folder/Track.mp3").candidate.displayName,
        )
    }

    @Test
    fun `lrc beats an srt of the same name`() {
        val matches = matchTimedText(
            audioTracks = tracks("Track"),
            candidates = listOf(candidate("Track.srt"), candidate("Track.lrc")),
        )

        assertEquals(
            "Track.lrc",
            matches.getValue("content://tree/folder/Track.mp3").candidate.displayName,
        )
    }

    @Test
    fun `a version marker is peeled off rather than read as a language`() {
        // `Track.new.lrc` is the corrected file beside `Track.mp3`. Read as a tag it
        // would look for `Track`, which happens to be right here -- but it would also
        // record `new` as the language the file was translated into, and it would stop
        // the search at the first marker instead of continuing to the track's name.
        val matches = matchTimedText(
            audioTracks = tracks("Track"),
            candidates = listOf(candidate("Track.new.lrc")),
        )

        val match = matches.getValue("content://tree/folder/Track.mp3")
        assertEquals("Track.new.lrc", match.candidate.displayName)
        assertNull(match.languageTag)
    }

    @Test
    fun `a version marker after a real language still resolves to the track`() {
        val matches = matchTimedText(
            audioTracks = tracks("Track"),
            candidates = listOf(candidate("Track.zh.new.lrc")),
        )

        val match = matches.getValue("content://tree/folder/Track.mp3")
        assertEquals("Track.zh.new.lrc", match.candidate.displayName)
        assertEquals("zh", match.languageTag)
    }

    @Test
    fun `a segment that is neither a tag nor a marker ends the search`() {
        // `Part` is part of a name, so `Track.Part.lrc` is a file about some other
        // track -- and peeling past it would let it claim `Track`'s lyrics.
        val matches = matchTimedText(
            audioTracks = tracks("Track"),
            candidates = listOf(candidate("Track.Part.lrc")),
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `two language tags in one name are not guessed at`() {
        val matches = matchTimedText(
            audioTracks = tracks("Track"),
            candidates = listOf(candidate("Track.zh.en.lrc")),
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `the plain name beats a version-marked one`() {
        val matches = matchTimedText(
            audioTracks = tracks("Track"),
            candidates = listOf(candidate("Track.new.lrc"), candidate("Track.lrc")),
        )

        assertEquals(
            "Track.lrc",
            matches.getValue("content://tree/folder/Track.mp3").candidate.displayName,
        )
    }

    @Test
    fun `one lyric file reaches every file that shares the title`() {
        // The same track in two formats is two items with one title, and each of them
        // is played on its own, so each of them has to be able to show the lyrics.
        val matches = matchTimedText(
            audioTracks = mapOf(
                "content://tree/folder/Track.mp3" to "Track",
                "content://tree/folder/Track.flac" to "Track",
            ),
            candidates = listOf(candidate("Track.lrc")),
        )

        assertEquals(2, matches.size)
        assertEquals("Track.lrc", matches.getValue("content://tree/folder/Track.flac").candidate.displayName)
    }

    @Test
    fun `each track gets its own file`() {
        val matches = matchTimedText(
            audioTracks = tracks("Chapter 1", "Chapter 2"),
            candidates = listOf(candidate("Chapter 1.lrc"), candidate("Chapter 2.lrc")),
        )

        assertEquals(2, matches.size)
        assertEquals(
            "Chapter 1.lrc",
            matches.getValue("content://tree/folder/Chapter 1.mp3").candidate.displayName,
        )
        assertEquals(
            "Chapter 2.lrc",
            matches.getValue("content://tree/folder/Chapter 2.mp3").candidate.displayName,
        )
    }

    @Test
    fun `a file matching no audio is left out`() {
        val matches = matchTimedText(
            audioTracks = tracks("Track"),
            candidates = listOf(candidate("Something Else.lrc"), candidate("Track.zh.lrc")),
        )

        assertEquals(1, matches.size)
        assertEquals(
            "Track.zh.lrc",
            matches.getValue("content://tree/folder/Track.mp3").candidate.displayName,
        )
    }

    @Test
    fun `an empty folder matching nothing is not an error`() {
        assertTrue(matchTimedText(audioTracks = emptyMap(), candidates = emptyList()).isEmpty())
        assertTrue(
            matchTimedText(audioTracks = tracks("Track"), candidates = emptyList()).isEmpty(),
        )
    }

    @Test
    fun `a language tag is two or three letters and optional subtags`() {
        assertEquals("zh", languageTagOf("Track.zh"))
        assertEquals("zh-Hans", languageTagOf("Track.zh-Hans"))
        assertEquals("en-US", languageTagOf("Track.en-US"))
        assertEquals("chi", languageTagOf("Track.chi"))
        assertNull(languageTagOf("Track"))
        assertNull(languageTagOf("Track.Part"))
        assertNull(languageTagOf("Track.Part.01"))
        assertNull(languageTagOf("Track.international"))
        assertNull(languageTagOf("Track."))
        assertNull(languageTagOf(".zh"))
    }

    @Test
    fun `a version marker suffix is refused as a language tag`() {
        assertNull(languageTagOf("Track.new"))
        assertNull(languageTagOf("Track.mix"))
        assertNull(languageTagOf("Track.old"))
        // Only the last segment is considered, and here it is the marker, not the tag.
        assertNull(languageTagOf("Track.zh.new"))
        assertEquals("zh", languageTagOf("Track.zh"))
    }

    @Test
    fun `a candidate's cache key is built from the file's identity`() {
        assertEquals(
            TimedTextKey("content://tree/folder/Track.lrc", 1_024, 1_700_000_000_000),
            candidate("Track.lrc").cacheKey(),
        )
    }
}
