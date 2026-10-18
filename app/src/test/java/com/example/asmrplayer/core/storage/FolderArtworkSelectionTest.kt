package com.example.asmrplayer.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderArtworkSelectionTest {
    @Test fun `first audio follows natural order rather than cursor or lexicographic order`() {
        val selection = FolderArtworkSelection()
        listOf("10.mp3", "2.mp3", "1.mp3").forEach { selection.audio(FolderArtworkEntry(it, it)) }
        assertEquals("1.mp3", selection.firstAudio?.name)
        assertNull(selection.image)
    }

    @Test fun `a direct folder image remains preferred regardless of audio arrival order`() {
        val selection = FolderArtworkSelection()
        selection.audio(FolderArtworkEntry("01.mp3", "audio"))
        selection.image(FolderArtworkEntry("other.jpg", "second"))
        selection.image(FolderArtworkEntry("COVER.jpg", "first"))
        assertEquals("first", selection.image?.uri)
        assertEquals("audio", selection.firstAudio?.uri)
    }

    @Test fun `equal filenames have a deterministic tie break and preserve file version`() {
        val selection = FolderArtworkSelection()
        selection.audio(FolderArtworkEntry("a.mp3", "z", "1:2"))
        selection.audio(FolderArtworkEntry("a.mp3", "a", "3:4"))
        assertEquals(FolderArtworkEntry("a.mp3", "a", "3:4"), selection.firstAudio)
    }

    @Test fun `empty folders produce no invented cover`() {
        val selection = FolderArtworkSelection()
        assertNull(selection.firstAudio)
        assertNull(selection.image)
    }
}
