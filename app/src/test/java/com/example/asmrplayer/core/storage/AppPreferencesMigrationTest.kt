package com.example.asmrplayer.core.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesMigrationTest {
    @Test
    fun `stored glow mode wins over legacy switch`() {
        assertEquals(2, resolveLyricsWordGlowModeOrdinal(2, legacyEnabled = false))
    }

    @Test
    fun `legacy off remains off`() {
        assertEquals(0, resolveLyricsWordGlowModeOrdinal(null, legacyEnabled = false))
    }

    @Test
    fun `legacy on and a new install both become smart`() {
        assertEquals(
            AppSettings.DefaultLyricsWordGlowModeOrdinal,
            resolveLyricsWordGlowModeOrdinal(null, legacyEnabled = true),
        )
        assertEquals(
            AppSettings.DefaultLyricsWordGlowModeOrdinal,
            resolveLyricsWordGlowModeOrdinal(null, legacyEnabled = null),
        )
    }
}
