package com.example.asmrplayer.core.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedArtworkTest {
    @Test
    fun `accepts supported raster artwork`() {
        listOf("cover.jpg", "cover.JPEG", "cover.png", "cover.webp", "cover.avif").forEach { name ->
            assertTrue(SupportedArtwork.isSupported(name, null))
        }
    }

    @Test
    fun `accepts image mime and rejects unrelated documents`() {
        assertTrue(SupportedArtwork.isSupported("cover", "image/png"))
        assertFalse(SupportedArtwork.isSupported("notes.txt", "text/plain"))
        assertFalse(SupportedArtwork.isSupported("vector.svg", "image/svg+xml"))
    }
}
