package com.example.asmrplayer.feature.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsWordGlowModeTest {
    @Test
    fun `every mode round trips through its persisted ordinal`() {
        LyricsWordGlowMode.entries.forEach { mode ->
            assertSame(mode, LyricsWordGlowMode.fromOrdinal(mode.ordinal))
        }
    }

    @Test
    fun `unknown persisted ordinal falls back to smart`() {
        assertSame(LyricsWordGlowMode.Smart, LyricsWordGlowMode.fromOrdinal(-1))
        assertSame(LyricsWordGlowMode.Smart, LyricsWordGlowMode.fromOrdinal(Int.MAX_VALUE))
    }

    @Test
    fun `registry ids stay unique`() {
        val ids = LyricsWordGlowMode.entries.map(LyricsWordGlowMode::stableId)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all(String::isNotBlank))
    }
}
