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
    @Test fun `lazy endpoint publication preserves identity and raw snapshot`() {
        val raw = folder("content://folder/1")
        val projected = projectFolderCovers(listOf(raw)) { uri, _ -> "lazy:$uri" }.single()
        assertEquals(raw.copy(artworkUri = "lazy:content://folder/1"), projected)
        assertEquals("automatic:content://folder/1", raw.artworkUri)
    }
    @Test fun `previous version is available for stable revision composition`() {
        val raw = folder("content://folder/1")
        assertEquals("automatic:content://folder/1:revision", projectFolderCovers(listOf(raw)) { _, old -> "$old:revision" }.single().artworkUri)
    }
    @Test fun `same title folders use exact separate document identities`() {
        val a = folder("content://folder/a"); val b = folder("content://folder/b")
        val shown = projectFolderCovers(listOf(a, b)) { uri, _ -> "lazy:$uri" }
        assertEquals("lazy:content://folder/a", shown[0].artworkUri)
        assertEquals("lazy:content://folder/b", shown[1].artworkUri)
    }
    @Test fun `audio and null identities never enter folder resolution`() {
        val raw = listOf(folder("content://track").copy(type = LibraryItemType.Audio), folder("missing").copy(documentUri = null))
        assertEquals(raw, projectFolderCovers(raw) { _, _ -> error("Must not resolve") })
    }
}
