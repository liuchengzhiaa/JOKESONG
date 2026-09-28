package com.example.asmrplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalVinylPlacementTest {
    @Test
    fun storedPositionScalesToCurrentSafeViewport() {
        assertEquals(0f, globalVinylCoordinate(0, 720f), 0.001f)
        assertEquals(360f, globalVinylCoordinate(500, 720f), 0.001f)
        assertEquals(720f, globalVinylCoordinate(1_000, 720f), 0.001f)
    }

    @Test
    fun settlingSnapsToNearestHorizontalEdgeAndPreservesHeight() {
        assertEquals(
            GlobalVinylPlacement(xPerMille = 0, yPerMille = 250),
            settleGlobalVinylPlacement(
                xPx = 99f,
                yPx = 100f,
                availableWidthPx = 400f,
                availableHeightPx = 400f,
            ),
        )
        assertEquals(
            GlobalVinylPlacement(xPerMille = 1_000, yPerMille = 750),
            settleGlobalVinylPlacement(
                xPx = 301f,
                yPx = 300f,
                availableWidthPx = 400f,
                availableHeightPx = 400f,
            ),
        )
    }

    @Test
    fun invalidCoordinatesAreClampedBeforePersistence() {
        assertEquals(
            GlobalVinylPlacement(xPerMille = 0, yPerMille = 1_000),
            settleGlobalVinylPlacement(
                xPx = -20f,
                yPx = 900f,
                availableWidthPx = 400f,
                availableHeightPx = 400f,
            ),
        )
    }
}
