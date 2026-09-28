package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.ui.lyrics.WindowedLyricsVisualizer
import com.example.asmrplayer.ui.lyrics.backdrop
import com.example.asmrplayer.ui.lyrics.labelResource
import com.example.asmrplayer.ui.lyrics.visualizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsVisualizerModeTest {
    @Test
    fun `every mode round trips through its persisted ordinal`() {
        LyricsVisualizerMode.entries.forEach { mode ->
            assertSame(mode, LyricsVisualizerMode.fromOrdinal(mode.storedValue))
        }
    }

    @Test
    fun `unknown and removed persisted ordinals fall back to luminous`() {
        listOf(-1, 0, 1, 2, 4, Int.MAX_VALUE).forEach { stored ->
            assertSame(LyricsVisualizerMode.Luminous, LyricsVisualizerMode.fromOrdinal(stored))
        }
    }

    @Test
    fun `registry ids are stable and unique`() {
        val ids = LyricsVisualizerMode.entries.map(LyricsVisualizerMode::stableId)

        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all(String::isNotBlank))
    }

    /**
     * The ordinal is a persisted user setting, so the modes that existed before the
     * newer ones arrived must keep the numbers they were stored with.
     */
    @Test
    fun `the four production modes retain their legacy stored values`() {
        assertEquals(3, LyricsVisualizerMode.Luminous.storedValue)
        assertEquals(5, LyricsVisualizerMode.Pendolo.storedValue)
        assertEquals(6, LyricsVisualizerMode.Claddagh.storedValue)
        assertEquals(7, LyricsVisualizerMode.Tempera.storedValue)
        assertEquals(4, LyricsVisualizerMode.entries.size)
    }

    @Test
    fun `every mode and background has its own label`() {
        val modeLabels = LyricsVisualizerMode.entries.map { it.labelResource() }
        val backgroundLabels = LyricsVisualizerBackground.entries.map { it.labelResource() }

        assertEquals(modeLabels.size, modeLabels.toSet().size)
        assertEquals(backgroundLabels.size, backgroundLabels.toSet().size)
        assertTrue(modeLabels.none { it == 0 })
        assertTrue(backgroundLabels.none { it == 0 })
    }

    /**
     * Registry completeness. Building the objects runs no composition, so this
     * catches a mode that was added without a visualizer or a background that was
     * added without a backdrop -- which the exhaustive dispatchers already refuse to
     * compile, and which this pins down as behaviour rather than as a compiler error.
     */
    /**
     * Which shape each mode declares is a real decision, not a detail: the stage
     * wraps an ordinary mode in a per-line transition and gives a windowed one the
     * whole screen untouched. A mode that switched shape by accident would either
     * lose its transition or be rebuilt on every line change.
     */
    @Test
    fun `the escapement and lyric loop are windowed modes`() {
        val windowed = LyricsVisualizerMode.entries.filter { it.visualizer() is WindowedLyricsVisualizer }

        assertEquals(
            listOf(LyricsVisualizerMode.Pendolo, LyricsVisualizerMode.Claddagh),
            windowed,
        )
    }

    @Test
    fun `every registry entry resolves an implementation`() {
        LyricsVisualizerMode.entries.forEach { mode ->
            assertNotNull(mode.visualizer())
        }
        LyricsVisualizerBackground.entries.forEach { background ->
            assertNotNull(background.backdrop())
        }
    }
}
