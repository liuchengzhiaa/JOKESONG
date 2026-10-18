package com.example.asmrplayer.ui

import com.example.asmrplayer.core.artwork.CoverFacts
import org.junit.Assert.*
import org.junit.Test

class PlayerBackdropTest {
    private fun facts(raw: List<Int>) = CoverFacts(0xFF00FF00.toInt(), listOf(0xFF00FF00.toInt()), 1000, 1000, raw)
    @Test fun tonalBackdropUsesUnbalancedDominantFamily() {
        val stops = playerTonalStops(facts(listOf(0xFFAA4422.toInt())))
        assertEquals(3, stops.size)
        assertTrue(stops.all { it.red > it.green && it.green > it.blue })
    }
    @Test fun minorityColorsDoNotCreateUnrelatedCornerHues() {
        assertEquals(playerTonalStops(facts(listOf(0xFFAA4422.toInt()))),
            playerTonalStops(facts(listOf(0xFFAA4422.toInt(), 0xFF00FFFF.toInt()))))
    }
    @Test fun noArtworkKeepsTheFallbackExplicit() {
        assertTrue(playerTonalStops(null).isEmpty())
        assertTrue(playerTonalStops(facts(emptyList())).isEmpty())
    }
    @Test fun tonalChannelsStayBoundedAndOpaque() {
        for (argb in listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0022.toInt())) {
            for (color in playerTonalStops(facts(listOf(argb)))) {
                assertEquals(1f, color.alpha, 0f)
                assertTrue(color.red in 0f..1f && color.green in 0f..1f && color.blue in 0f..1f)
            }
        }
    }
}
