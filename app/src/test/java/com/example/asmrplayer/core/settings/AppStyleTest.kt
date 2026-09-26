package com.example.asmrplayer.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppStyleTest {
    @Test
    fun `accent ordinal is safely clamped`() {
        assertEquals(UiAccentPalette.Ocean, UiAccentPalette.fromOrdinal(-1))
        assertEquals(UiAccentPalette.Rose, UiAccentPalette.fromOrdinal(999))
    }

    @Test
    fun `style ordinal is safely clamped`() {
        assertEquals(UiStylePreset.Balanced, UiStylePreset.fromOrdinal(-1))
        assertEquals(UiStylePreset.Custom, UiStylePreset.fromOrdinal(999))
    }

    @Test
    fun `one tap presets carry complete global style values`() {
        UiStylePreset.entries.filterNot { it == UiStylePreset.Custom }.forEach { preset ->
            assertEquals(true, preset.motionPercent in 0..100)
            assertEquals(true, preset.uiScalePercent in 85..120)
            assertEquals(true, preset.accent in UiAccentPalette.entries)
        }
    }
}
