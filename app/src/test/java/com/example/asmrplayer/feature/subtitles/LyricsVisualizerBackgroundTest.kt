package com.example.asmrplayer.feature.subtitles

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsVisualizerBackgroundTest {
    @Test
    fun `every background round trips through its persisted ordinal`() {
        LyricsVisualizerBackground.entries.forEach { background ->
            assertSame(background, LyricsVisualizerBackground.fromOrdinal(background.ordinal))
        }
    }

    @Test
    fun `an unknown persisted ordinal falls back to the default background`() {
        assertSame(
            LyricsVisualizerBackground.Default,
            LyricsVisualizerBackground.fromOrdinal(-1),
        )
        assertSame(
            LyricsVisualizerBackground.Default,
            LyricsVisualizerBackground.fromOrdinal(Int.MAX_VALUE),
        )
    }

    @Test
    fun `background ids are stable and unique`() {
        val ids = LyricsVisualizerBackground.entries.map(LyricsVisualizerBackground::stableId)

        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all(String::isNotBlank))
    }

    /**
     * The whole regression guarantee of this change: an install that has never
     * chosen a background keeps the artwork treatment its mode always had.
     */
    @Test
    fun `the four production modes keep their authored default backgrounds`() {
        assertSame(
            LyricsVisualizerBackground.ColourField,
            resolveLyricsBackground(storedOrdinal = null, mode = LyricsVisualizerMode.Luminous),
        )
        assertSame(
            LyricsVisualizerBackground.BlurredCover,
            resolveLyricsBackground(storedOrdinal = null, mode = LyricsVisualizerMode.Pendolo),
        )
        assertSame(
            LyricsVisualizerBackground.SharpPoster,
            resolveLyricsBackground(storedOrdinal = null, mode = LyricsVisualizerMode.Claddagh),
        )
        assertSame(
            LyricsVisualizerBackground.ColourField,
            resolveLyricsBackground(storedOrdinal = null, mode = LyricsVisualizerMode.Tempera),
        )
    }

    @Test
    fun `following the mode means every mode resolves its own default`() {
        LyricsVisualizerMode.entries.forEach { mode ->
            assertSame(
                mode.defaultBackground,
                resolveLyricsBackground(storedOrdinal = null, mode = mode),
            )
        }
    }

    @Test
    fun `a stored background ignores the mode entirely`() {
        LyricsVisualizerMode.entries.forEach { mode ->
            LyricsVisualizerBackground.entries.forEach { chosen ->
                assertSame(
                    chosen,
                    resolveLyricsBackground(storedOrdinal = chosen.ordinal, mode = mode),
                )
            }
        }
    }

    @Test
    fun `an out of range stored background degrades to following the mode`() {
        assertSame(
            LyricsVisualizerMode.Tempera.defaultBackground,
            resolveLyricsBackground(storedOrdinal = 99, mode = LyricsVisualizerMode.Tempera),
        )
    }

    /**
     * The two modes that own a frame clock are deliberately absent here. Neither has
     * an amplitude this file could scale: the escapement's spring is snapped rather
     * than sprung at zero motion and its clock never starts (`PendoloGeometryTest`),
     * and the word cloud's throw and breath are both zero at zero motion, which is
     * asserted where that geometry lives (`LuminousLayoutTest`).
     */
    @Test
    fun `nothing moves at a motion setting of zero`() {
        assertEquals(0f, driftOffsetFor(lineIndex = 3, motionPercent = 0), Tolerance)
    }

    @Test
    fun `a motion setting below zero is treated as zero`() {
        assertEquals(0f, driftOffsetFor(lineIndex = 2, motionPercent = -40), Tolerance)
    }

    @Test
    fun `drift is a function of the line rather than of elapsed time`() {
        assertEquals(driftOffsetFor(3, 80), driftOffsetFor(3, 80), Tolerance)
        assertTrue(driftOffsetFor(0, 80) != driftOffsetFor(1, 80))
        // Periodic in the line index, so a long track keeps cycling rather than
        // walking the artwork off the screen.
        assertEquals(driftOffsetFor(0, 80), driftOffsetFor(4, 80), Tolerance)
        assertEquals(driftOffsetFor(1, 80), driftOffsetFor(-3, 80), Tolerance)
        assertTrue(abs(driftOffsetFor(0, 100)) <= 13.5f + Tolerance)
    }

    private companion object {
        const val Tolerance = 0.0001f
    }
}
