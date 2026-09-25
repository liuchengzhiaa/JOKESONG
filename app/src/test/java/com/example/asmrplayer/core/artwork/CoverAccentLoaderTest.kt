package com.example.asmrplayer.core.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverAccentLoaderTest {
    @Test
    fun `dominant family wins over a smaller contrasting patch`() {
        val mutedRed = argb(255, 168, 62, 58)
        val blue = argb(255, 40, 70, 210)
        val result = dominantCoverArgb(IntArray(100) { if (it < 78) mutedRed else blue })

        assertNotNull(result)
        val color = requireNotNull(result)
        assertTrue(red(color) > blue(color))
    }

    @Test
    fun `transparent pixels do not determine accent`() {
        val transparentGreen = argb(0, 0, 255, 0)
        val visiblePurple = argb(255, 145, 66, 170)
        val result = dominantCoverArgb(IntArray(80) { if (it < 70) transparentGreen else visiblePurple })

        assertNotNull(result)
        val color = requireNotNull(result)
        assertTrue(blue(color) > green(color))
        assertEquals(255, color ushr 24 and 0xFF)
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    private fun red(color: Int): Int = color ushr 16 and 0xFF
    private fun green(color: Int): Int = color ushr 8 and 0xFF
    private fun blue(color: Int): Int = color and 0xFF
}
