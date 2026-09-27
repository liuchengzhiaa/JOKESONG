package com.example.asmrplayer.feature.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsVisualizerModeTest {
    @Test
    fun `every mode round trips through its persisted ordinal`() {
        LyricsVisualizerMode.entries.forEach { mode ->
            assertSame(mode, LyricsVisualizerMode.fromOrdinal(mode.ordinal))
        }
    }

    @Test
    fun `unknown persisted ordinal falls back to flow`() {
        assertSame(LyricsVisualizerMode.Flow, LyricsVisualizerMode.fromOrdinal(-1))
        assertSame(LyricsVisualizerMode.Flow, LyricsVisualizerMode.fromOrdinal(Int.MAX_VALUE))
    }

    @Test
    fun `registry ids are stable and unique`() {
        val ids = LyricsVisualizerMode.entries.map(LyricsVisualizerMode::stableId)

        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all(String::isNotBlank))
    }
}
