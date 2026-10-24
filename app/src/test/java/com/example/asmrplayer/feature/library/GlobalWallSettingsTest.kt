package com.example.asmrplayer.feature.library

import com.example.asmrplayer.core.storage.AppSettings
import com.example.asmrplayer.core.storage.FolderSnapshot
import com.example.asmrplayer.core.storage.FolderSnapshotCache
import org.junit.Assert.*
import org.junit.Test

class GlobalWallSettingsTest {
    private val settings = AppSettings(
        libraryLayoutOrdinal = 4, wallZoomPercent = 122, wallDensityPercent = 125,
        wallFocusShapeOrdinal = 2, wallFocusSizeOrdinal = 2, wallAmbientPercent = 145,
        wallLightsOut = true, wallAutoFocusPlaying = true, wallPosterTintPercent = 40,
    )

    @Test fun `cached parent child and fresh deep folder use identical global settings`() {
        val cache = FolderSnapshotCache()
        listOf("parent", "parent/child").forEachIndexed { index, key ->
            cache.put(FolderSnapshot(key, key, emptyList(), 0, false,
                focusedItemId = "focus-$index", panX = index * 10f, panY = index * -20f))
        }
        val fresh = LibraryUiState().withAppSettings(settings)
        listOf("parent", "parent/child").forEachIndexed { index, key ->
            val restored = cache.get(key)!!.toUiState(true, true, false).withAppSettings(settings)
            assertEquals(fresh.canvasZoom, restored.canvasZoom)
            assertEquals(fresh.wallDensityPercent, restored.wallDensityPercent)
            assertEquals(fresh.wallFocusShapeOrdinal, restored.wallFocusShapeOrdinal)
            assertEquals(fresh.wallFocusSizeOrdinal, restored.wallFocusSizeOrdinal)
            assertEquals(fresh.wallAmbientPercent, restored.wallAmbientPercent)
            assertEquals(fresh.wallLightsOut, restored.wallLightsOut)
            assertEquals(fresh.wallAutoFocusPlaying, restored.wallAutoFocusPlaying)
            assertEquals(fresh.wallPosterTintPercent, restored.wallPosterTintPercent)
            assertEquals("focus-$index", restored.focusedItemId)
            assertEquals(index * 10f, restored.panX)
            assertEquals(index * -20f, restored.panY)
        }
    }

    @Test fun `changing settings in a child overrides old display values on return`() {
        val original = LibraryUiState().withAppSettings(AppSettings())
        val updated = original.withAppSettings(settings)
        assertEquals(1.22f, updated.canvasZoom)
        assertEquals(125, updated.wallDensityPercent)
        assertEquals(145, updated.wallAmbientPercent)
        assertEquals(2, updated.wallFocusShapeOrdinal)
        assertEquals(2, updated.wallFocusSizeOrdinal)
        assertTrue(updated.wallLightsOut)
    }

    @Test fun `global reset works for cached states without resetting navigation`() {
        val reset = LibraryUiState(focusedItemId = "track", panX = 75f)
            .withAppSettings(settings).withAppSettings(AppSettings())
        assertEquals(4, reset.libraryLayoutOrdinal)
        assertEquals(1f, reset.canvasZoom)
        assertEquals(100, reset.wallDensityPercent)
        assertEquals(100, reset.wallAmbientPercent)
        assertFalse(reset.wallLightsOut)
        assertEquals("track", reset.focusedItemId)
        assertEquals(75f, reset.panX)
    }

    @Test fun `legacy layout canonicalizes during both initial and restored settings application`() {
        assertEquals(4, LibraryUiState().withAppSettings(AppSettings(libraryLayoutOrdinal = 3)).libraryLayoutOrdinal)
    }
}
