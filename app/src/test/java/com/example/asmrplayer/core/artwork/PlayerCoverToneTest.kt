package com.example.asmrplayer.core.artwork

import org.junit.Assert.*
import org.junit.Test

class PlayerCoverToneTest {
    @Test fun paleHighlightsDoNotTurnBrownArtIntoPinkGround() {
        val pale = 0xFFF0CEC8.toInt()
        val wood = 0xFF976846.toInt()
        assertEquals(wood, playerCoverArgb(IntArray(80) { pale } + IntArray(20) { wood }))
    }
    @Test fun colourfulBlueArtKeepsItsOwnFamily() {
        val blue = 0xFF345BA1.toInt()
        assertEquals(blue, playerCoverArgb(IntArray(100) { blue }))
    }
    @Test fun transparentAndNeutralArtYieldExplicitFallback() {
        assertNull(playerCoverArgb(intArrayOf(0x00335588, 0xFFFFFFFF.toInt(), 0xFF121212.toInt())))
    }
    @Test fun cacheKeysIsolateTrackAndVersion() {
        assertEquals(artworkCacheKey("a:1"), artworkCacheKey("a:1"))
        assertNotEquals(artworkCacheKey("a:1"), artworkCacheKey("b:1"))
        assertNotEquals(artworkCacheKey("a:1"), artworkCacheKey("a:2"))
    }
}
