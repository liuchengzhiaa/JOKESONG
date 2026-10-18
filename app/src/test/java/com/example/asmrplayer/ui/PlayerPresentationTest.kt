package com.example.asmrplayer.ui

import org.junit.Assert.*
import org.junit.Test

class PlayerPresentationTest {
    @Test fun landscapeArtworkKeepsItsRatio() {
        val (w, h) = fitPlayerArtwork(400f, 500f, 340f, 4f / 3, 100)
        assertEquals(340f, w, 0.01f)
        assertEquals(255f, h, 0.01f)
    }
    @Test fun portraitArtworkFitsHeightWithoutCropping() {
        val (w, h) = fitPlayerArtwork(400f, 300f, 340f, 0.5f, 100)
        assertEquals(132f, w, 0.01f)
        assertEquals(264f, h, 0.01f)
    }
    @Test fun squareAndInvalidRatioHaveSafeFallback() {
        for (aspect in listOf(1f, 0f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val (w, h) = fitPlayerArtwork(400f, 500f, 340f, aspect, 100)
            assertEquals(w, h, 0.01f)
        }
    }
    @Test fun sizingCannotOverflowItsViewport() {
        val (w, h) = fitPlayerArtwork(400f, 500f, 340f, 1.5f, 500)
        assertTrue(w <= 384f)
        assertTrue(h <= 440f)
        assertEquals(1.5f, w / h, 0.001f)
    }
}
