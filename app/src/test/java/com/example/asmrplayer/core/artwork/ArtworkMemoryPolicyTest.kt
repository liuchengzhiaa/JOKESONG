package com.example.asmrplayer.core.artwork

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkMemoryPolicyTest {
    private val mib = 1024L * 1024

    @Test fun `small heaps retain room for the player and active decode`() {
        assertEquals(8 * mib, artworkMemoryCacheBytes(64 * mib))
        assertEquals(16 * mib, artworkMemoryCacheBytes(128 * mib))
    }

    @Test fun `large heaps do not grow the cache without bound`() {
        assertEquals(64 * mib, artworkMemoryCacheBytes(512 * mib))
        assertEquals(64 * mib, artworkMemoryCacheBytes(4_096 * mib))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid heap is not silently accepted`() {
        artworkMemoryCacheBytes(0)
    }
}
