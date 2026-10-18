package com.example.asmrplayer.core.artwork

import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import org.junit.Assert.*
import org.junit.Test

class FolderCoverProjectionTest {
    private fun folder(uri: String, title: String = "work") = LibraryItem(
        id = uri, title = title, subtitle = "summary", type = LibraryItemType.Folder,
        paletteSeed = 42, documentUri = uri, artworkUri = "automatic:$uri",
    )

    @Test fun `manual cover wins without mutating automatic snapshot or identity`() {
        val raw = folder("content://folder/1")
        val projected = projectFolderCovers(listOf(raw), mapOf(raw.documentUri!! to "file:///private.png")).single()
        assertEquals(raw.copy(artworkUri = "file:///private.png"), projected)
        assertEquals("automatic:content://folder/1", raw.artworkUri)
    }

    @Test fun `reset or missing override restores original automatic artwork`() {
        val raw = folder("content://folder/1")
        assertSame(raw, projectFolderCovers(listOf(raw), emptyMap()).single())
    }

    @Test fun `same title in different folders never shares override`() {
        val a = folder("content://folder/a")
        val b = folder("content://folder/b")
        val shown = projectFolderCovers(listOf(a, b), mapOf(a.documentUri!! to "private"))
        assertEquals("private", shown[0].artworkUri)
        assertSame(b, shown[1])
    }

    @Test fun `audio items and null identities are never overridden`() {
        val audio = folder("content://track").copy(type = LibraryItemType.Audio)
        val missing = folder("missing").copy(documentUri = null)
        val raw = listOf(audio, missing)
        assertEquals(raw, projectFolderCovers(raw, mapOf("content://track" to "private", "missing" to "private")))
    }
}
