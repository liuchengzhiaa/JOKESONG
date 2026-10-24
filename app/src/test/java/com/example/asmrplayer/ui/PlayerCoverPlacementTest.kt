package com.example.asmrplayer.ui

import org.junit.Assert.*
import org.junit.Test

class PlayerCoverPlacementTest {
    @Test fun `square portrait and landscape covers anchor lyrics halfway to actual seek line`() {
        for (aspect in listOf(0.5f, 1f, 1.78f, 4f)) {
            for (percent in listOf(70, 100, 115)) {
                val height = 690
                val lyricHeight = 112
                val art = fitPlayerArtwork(400f, (height - lyricHeight).toFloat(), 340f, aspect, percent)
                val artHeight = art.second.toInt()
                val (artY, lyricY) = coverPageVerticalPlacement(height, artHeight, lyricHeight, 25)
                val expectedCenter = (artY + artHeight + height + 25) / 2f
                assertEquals(expectedCenter, lyricY + lyricHeight / 2f, 1f)
                assertTrue(lyricY >= artY + artHeight)
                assertTrue(lyricY + lyricHeight <= height)
            }
        }
    }

    @Test fun `lyrics move up from the old bottom anchored position`() {
        val (_, top) = coverPageVerticalPlacement(690, 340, 112, 25)
        assertTrue(top < 690 - 112)
    }

    @Test fun `short viewport and large text clamp without colliding with controls or cover`() {
        val (artY, lyricsY) = coverPageVerticalPlacement(200, 80, 100, 25)
        assertTrue(lyricsY >= artY + 80)
        assertTrue(lyricsY + 100 <= 200)
    }

    @Test fun `no lyrics leaves the cover vertically centred`() {
        val (artY, _) = coverPageVerticalPlacement(690, 340, 0, 25)
        assertEquals(175, artY)
    }
}
