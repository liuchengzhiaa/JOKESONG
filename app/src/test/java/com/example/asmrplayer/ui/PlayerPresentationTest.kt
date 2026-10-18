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
    @Test fun displayedDecoderShapeDoesNotDependOnPaletteSuccess() {
        assertEquals(0.6f, playerArtworkAspect(0.6f, null), 0f)
        assertEquals(16f / 9f, playerArtworkAspect(16f / 9f, 1f), 0f)
    }
    @Test fun invalidDecoderShapeFallsBackToSampleThenSquare() {
        assertEquals(1.8f, playerArtworkAspect(Float.NaN, 1.8f), 0f)
        assertEquals(1f, playerArtworkAspect(0f, Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, playerArtworkAspect(null, null), 0f)
    }
    @Test fun tallAndWideReferencesKeepTheirRatiosAtEveryUserSize() {
        for (aspect in listOf(0.60f, 16f / 9f, 1f)) {
            for (percent in listOf(70, 100, 115)) {
                val (w, h) = fitPlayerArtwork(400f, 500f, 340f, playerArtworkAspect(aspect, null), percent)
                assertEquals(aspect, w / h, 0.0001f)
                assertTrue(w <= 384f && h <= 440f)
            }
        }
    }
}
