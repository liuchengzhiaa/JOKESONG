package com.example.asmrplayer.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderSnapshotCacheTest {
    private fun snapshot(
        key: String,
        folderTitle: String = key,
        focusedItemId: String? = null,
    ) = FolderSnapshot(
        folderKey = key,
        folderTitle = folderTitle,
        items = emptyList(),
        supportedItemCount = 0,
        isTruncated = false,
        focusedItemId = focusedItemId,
    )

    @Test
    fun `restores the snapshot of a visited folder`() {
        val cache = FolderSnapshotCache(capacity = 4)
        cache.put(snapshot("folder-a", folderTitle = "A", focusedItemId = "track-2"))

        val restored = cache.get("folder-a")

        assertEquals("A", restored?.folderTitle)
        assertEquals("track-2", restored?.focusedItemId)
    }

    @Test
    fun `unknown folders have no snapshot`() {
        val cache = FolderSnapshotCache(capacity = 4)

        assertNull(cache.get("folder-a"))
        assertFalse(cache.contains("folder-a"))
    }

    @Test
    fun `evicts the least recently used folder beyond capacity`() {
        val cache = FolderSnapshotCache(capacity = 2)
        cache.put(snapshot("folder-a"))
        cache.put(snapshot("folder-b"))

        // Reading folder-a makes folder-b the least recently used entry.
        cache.get("folder-a")
        cache.put(snapshot("folder-c"))

        assertEquals(2, cache.size)
        assertTrue(cache.contains("folder-a"))
        assertTrue(cache.contains("folder-c"))
        assertFalse(cache.contains("folder-b"))
    }

    @Test
    fun `invalidate drops the folder so it is scanned again`() {
        val cache = FolderSnapshotCache(capacity = 4)
        cache.put(snapshot("folder-a"))

        cache.invalidate("folder-a")

        assertFalse(cache.contains("folder-a"))
        assertEquals(0, cache.size)
    }

    @Test
    fun `update only mutates a resident snapshot`() {
        val cache = FolderSnapshotCache(capacity = 4)

        val missing = cache.update("folder-a") { it.copy(focusedItemId = "track-1") }
        assertFalse(missing)
        assertEquals(0, cache.size)

        cache.put(snapshot("folder-a"))
        val updated = cache.update("folder-a") { it.copy(focusedItemId = "track-1") }

        assertTrue(updated)
        assertEquals("track-1", cache.get("folder-a")?.focusedItemId)
    }

    @Test
    fun `viewport is carried with the folder snapshot`() {
        val cache = FolderSnapshotCache(capacity = 4)
        cache.put(snapshot("folder-a"))
        cache.update("folder-a") {
            it.copy(
                canvasZoom = 1.2f,
                panX = -40f,
                panY = 12f,
                wallDensityPercent = 124,
                wallAmbientPercent = 143,
            )
        }

        val restored = cache.get("folder-a")

        assertEquals(1.2f, restored?.canvasZoom ?: 0f, 0.0001f)
        assertEquals(-40f, restored?.panX ?: 0f, 0.0001f)
        assertEquals(12f, restored?.panY ?: 0f, 0.0001f)
        assertEquals(124, restored?.wallDensityPercent)
        assertEquals(143, restored?.wallAmbientPercent)
    }

    @Test
    fun `clear empties every folder`() {
        val cache = FolderSnapshotCache(capacity = 4)
        cache.put(snapshot("folder-a"))
        cache.put(snapshot("folder-b"))

        cache.clear()

        assertEquals(0, cache.size)
    }

    @Test
    fun `update all changes settings without dropping cached listings`() {
        val cache = FolderSnapshotCache(capacity = 4)
        cache.put(snapshot("folder-a").copy(wallDensityPercent = 125))
        cache.put(snapshot("folder-b").copy(wallDensityPercent = 75))

        cache.updateAll { it.copy(wallDensityPercent = 100) }

        assertEquals(2, cache.size)
        assertEquals(100, cache.get("folder-a")?.wallDensityPercent)
        assertEquals(100, cache.get("folder-b")?.wallDensityPercent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `capacity must be positive`() {
        FolderSnapshotCache(capacity = 0)
    }
}
