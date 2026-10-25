package com.example.asmrplayer.core.storage

import org.junit.Assert.*
import org.junit.Test

class FolderArtworkSelectionTest {
    @Test fun `only direct cover basenames are eligible`() {
        val selection = FolderArtworkSelection()
        listOf("other.jpg", "cover2.png", ".jokesong-cover-test-stage.jpg").forEach {
            selection.image(FolderArtworkEntry(it, it))
        }
        assertNull(selection.image)
        selection.image(FolderArtworkEntry("COVER.png", "cover"))
        assertEquals("cover", selection.image?.uri)
    }
    @Test fun `cover choice is deterministic regardless of cursor order`() {
        for (names in listOf(listOf("cover.webp", "cover.png", "cover.jpg"), listOf("cover.jpg", "cover.png", "cover.webp"))) {
            val selection = FolderArtworkSelection()
            names.forEach { selection.image(FolderArtworkEntry(it, it)) }
            assertEquals("cover.jpg", selection.image?.name)
        }
    }
    @Test fun `equal names have deterministic URI tie break and preserve version`() {
        val selection = FolderArtworkSelection()
        selection.image(FolderArtworkEntry("cover.png", "z", "1:2"))
        selection.image(FolderArtworkEntry("cover.png", "a", "3:4"))
        assertEquals(FolderArtworkEntry("cover.png", "a", "3:4"), selection.image)
    }
    @Test fun `empty folder has no invented artwork`() { assertNull(FolderArtworkSelection().image) }
}
