package com.example.asmrplayer.core.media

import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueTest {
    private fun audio(id: String, documentUri: String? = "content://media/$id") = LibraryItem(
        id = id,
        title = id,
        subtitle = "OGG",
        type = LibraryItemType.Audio,
        paletteSeed = 1,
        documentUri = documentUri,
    )

    private fun folder(id: String) = LibraryItem(
        id = id,
        title = id,
        subtitle = "",
        type = LibraryItemType.Folder,
        paletteSeed = 1,
        documentUri = "content://media/$id",
    )

    @Test
    fun `queue keeps display order and starts at the selected audio`() {
        val items = listOf(audio("track-1"), audio("track-2"), audio("track-3"))

        val plan = buildFolderQueue(items, items[1])

        assertEquals(listOf("track-1", "track-2", "track-3"), plan?.items?.map(LibraryItem::id))
        assertEquals(1, plan?.startIndex)
        assertEquals("track-2", plan?.startItem?.id)
    }

    @Test
    fun `child folders never enter the audio queue`() {
        val items = listOf(folder("disc-1"), audio("track-1"), folder("disc-2"), audio("track-2"))

        val plan = buildFolderQueue(items, items[3])

        assertEquals(listOf("track-1", "track-2"), plan?.items?.map(LibraryItem::id))
        assertEquals(1, plan?.startIndex)
    }

    @Test
    fun `an audio outside the listing plays alone instead of a different track`() {
        val items = listOf(audio("track-1"), audio("track-2"))

        val plan = buildFolderQueue(items, audio("track-9"))

        assertEquals(listOf("track-9"), plan?.items?.map(LibraryItem::id))
        assertEquals(0, plan?.startIndex)
    }

    @Test
    fun `a folder listing without audio has nothing to play`() {
        val items = listOf(folder("disc-1"), folder("disc-2"))

        assertNull(buildFolderQueue(items, null))
        assertNull(buildFolderQueue(items, folder("disc-1")))
    }

    @Test
    fun `audio without a document uri is not queued`() {
        val items = listOf(audio("track-1", documentUri = null), audio("track-2"))

        val plan = buildFolderQueue(items, items[1])

        assertEquals(listOf("track-2"), plan?.items?.map(LibraryItem::id))
    }

    @Test
    fun `an empty folder still plays a selected audio that has a uri`() {
        val selected = audio("track-1")

        val plan = buildFolderQueue(emptyList(), selected)

        assertEquals(listOf("track-1"), plan?.items?.map(LibraryItem::id))
        assertEquals(0, plan?.startIndex)
    }
}
