package com.example.asmrplayer.ui

import com.example.asmrplayer.core.artwork.CoverFacts
import com.example.asmrplayer.core.artwork.PlayerColourField
import org.junit.Assert.*
import org.junit.Test

class PlayerBackdropTest {
    private val red = 0xFF993344.toInt()
    private val blue = 0xFF334488.toInt()
    @Test fun spatialSourceFieldWinsOverDominantAndWallPalette() {
        val field = PlayerColourField(List(9) { if (it % 3 == 0) red else blue }, 0.1f)
        val facts = CoverFacts(0xFF00FF00.toInt(), listOf(0xFF00FF00.toInt()), 1000, 1000,
            rawPaletteArgbs = listOf(red), playerField = field)
        assertSame(field, playerBackdropField(facts, 0))
    }
    @Test fun minorityRegionsAreNotDiscardedAsOneDominantFamily() {
        val field = PlayerColourField(List(9) { if (it == 8) blue else red }, 0f)
        val facts = CoverFacts(red, listOf(red), 300, 300, playerField = field)
        assertEquals(blue, playerBackdropField(facts, 0).colours.last())
    }
    @Test fun noArtworkUsesAnExplicitDeterministicFallback() {
        assertEquals(List(9) { red }, playerBackdropField(null, red).colours)
    }
    @Test fun olderFactsWithoutSpatialSamplesStillHaveAnUsableFallback() {
        val facts = CoverFacts(red, listOf(red), 100, 100)
        assertEquals(List(9) { red }, playerBackdropField(facts, blue).colours)
    }
}
