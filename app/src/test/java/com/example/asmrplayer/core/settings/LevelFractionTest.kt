package com.example.asmrplayer.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The level icon reads its fill from this, so the numbers here are the ones the
 * owner will see drawn as a crest on the wall tools panel.
 */
class LevelFractionTest {
    private fun assertFraction(expected: Float, actual: Float) {
        assertEquals(expected, actual, 1e-6f)
    }

    @Test
    fun `the ends of the range are empty and full`() {
        assertFraction(0f, levelFraction(70, 70..130))
        assertFraction(1f, levelFraction(130, 70..130))
    }

    @Test
    fun `the midpoint of the range is half`() {
        assertFraction(0.5f, levelFraction(100, 70..130))
    }

    @Test
    fun `the same percentage sits at a different level in a different range`() {
        // 100% is the reset value of every continuous wall setting, so it is worth
        // being explicit that it is not the same fill in each of them: the crest
        // shows position on the control, not the number.
        assertFraction(0.5f, levelFraction(100, 70..130))
        assertFraction(2f / 3f, levelFraction(100, 0..150))
        assertFraction(28f / 63f, levelFraction(100, 72..135))
    }

    @Test
    fun `values outside the range are clamped to its ends`() {
        assertFraction(0f, levelFraction(0, 70..130))
        assertFraction(1f, levelFraction(150, 70..130))
        assertFraction(1f, levelFraction(Int.MAX_VALUE, 0..100))
    }

    @Test
    fun `a range with no span is empty rather than undefined`() {
        assertFraction(0f, levelFraction(100, 100..100))
        // Defensive: an inverted range would divide by a negative span, and a crest
        // drawn from a negative fill is worse than one drawn empty.
        assertFraction(0f, levelFraction(100, 130..70))
    }
}
