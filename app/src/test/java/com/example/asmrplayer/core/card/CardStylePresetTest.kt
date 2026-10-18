package com.example.asmrplayer.core.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A preset is the whole starting look a card is opened with, and it is written into a
 * document the viewer will act on without validating. Everything here is about the
 * document staying inside what the viewer can render, and about the four presets
 * staying four distinct cards rather than four names for one.
 */
class CardStylePresetTest {

    @Test
    fun `every value sits inside the viewer's own slider`() {
        // The viewer's sliders are the authority on what it can display, and a value
        // outside one is silently clamped the moment the owner touches that slider --
        // so a card would open at one look and jump to another.
        for (preset in CardStylePreset.entries) {
            assertTrue("$preset subjectScale", preset.subjectScale in 0.95f..1.35f)
            assertTrue("$preset subjectDepth", preset.subjectDepth in -0.45f..0.45f)
            assertTrue("$preset backgroundDepth", preset.backgroundDepth in -0.4f..0.4f)
            assertTrue("$preset foil", preset.foil in 0f..1f)
        }
    }

    @Test
    fun `every preset wears a finish the viewer knows`() {
        // These four strings are the viewer's own `data-finish` values; anything else
        // resolves to no finish at all and the card opens untextured.
        val known = setOf("pearl", "silver", "gold", "original")

        assertEquals(known, CardStylePreset.entries.map { it.finish }.toSet())
    }

    @Test
    fun `no two presets write the same document`() {
        val written = CardStylePreset.entries.map { it.finish to it.foil }

        assertEquals(CardStylePreset.entries.size, written.distinct().size)
    }

    @Test
    fun `the presets share one geometry so only the material changes`() {
        // Depth is the card's scene, not its material. Two presets that differ in depth
        // as well would make the same cover look like two different photographs, and
        // the owner is choosing a finish, not re-shooting the card.
        assertEquals(1, CardStylePreset.entries.map { it.subjectDepth }.distinct().size)
        assertEquals(1, CardStylePreset.entries.map { it.backgroundDepth }.distinct().size)
        assertEquals(1, CardStylePreset.entries.map { it.subjectScale }.distinct().size)
        assertEquals(1, CardStylePreset.entries.map { it.safeAreaScale }.distinct().size)
        assertEquals(1, CardStylePreset.entries.map { it.safeAreaOffsetX }.distinct().size)
        assertEquals(1, CardStylePreset.entries.map { it.safeAreaOffsetY }.distinct().size)
    }

    @Test
    fun `the three planes are all at different depths`() {
        // Equal depths weld two planes together and the card stops reading as a stack,
        // which is the whole effect. The typography plane is the third: it is welded to
        // the face, so it must sit at zero and neither of the other two may join it.
        val preset = CardStylePreset.Default

        assertTrue(preset.subjectDepth != preset.backgroundDepth)
        assertTrue(preset.subjectDepth != 0f)
        assertTrue(preset.backgroundDepth != 0f)
    }

    @Test
    fun `the default is the reference card's own numbers`() {
        val card = CardStylePreset.Default

        assertSame(CardStylePreset.Heirloom, card)
        assertEquals("gold", card.finish)
        assertEquals(0.68f, card.foil, 0f)
        assertEquals(1.18f, card.subjectScale, 0f)
        assertEquals(0.42f, card.subjectDepth, 0f)
        assertEquals(-0.28f, card.backgroundDepth, 0f)
        assertEquals(1.08f, card.safeAreaScale, 0f)
        assertEquals(0f, card.safeAreaOffsetX, 0f)
        assertEquals(-0.035f, card.safeAreaOffsetY, 0f)
    }

    @Test
    fun `an ordinal from storage is clamped rather than trusted`() {
        assertSame(CardStylePreset.Heirloom, CardStylePreset.fromOrdinal(-1))
        assertSame(CardStylePreset.Heirloom, CardStylePreset.fromOrdinal(Int.MIN_VALUE))
        assertSame(CardStylePreset.entries.last(), CardStylePreset.fromOrdinal(Int.MAX_VALUE))
    }

    @Test
    fun `an ordinal that is in range is itself`() {
        for (preset in CardStylePreset.entries) {
            assertSame(preset, CardStylePreset.fromOrdinal(preset.ordinal))
        }
    }

    @Test
    fun `a stored finish resolves back to its preset`() {
        for (preset in CardStylePreset.entries) {
            assertSame(preset, CardStylePreset.fromFinish(preset.finish))
        }
    }

    @Test
    fun `an unknown or missing finish opens the default rather than nothing`() {
        assertSame(CardStylePreset.Default, CardStylePreset.fromFinish(null))
        assertSame(CardStylePreset.Default, CardStylePreset.fromFinish(""))
        assertSame(CardStylePreset.Default, CardStylePreset.fromFinish("platinum"))
    }
}
