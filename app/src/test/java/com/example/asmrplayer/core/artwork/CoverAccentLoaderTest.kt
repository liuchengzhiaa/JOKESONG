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

    @Test
    fun `representative palette keeps distinct populated families in stable order`() {
        val red = argb(255, 176, 54, 48)
        val blue = argb(255, 44, 80, 196)
        val green = argb(255, 42, 156, 88)
        val pixels = IntArray(120) { index ->
            when {
                index < 60 -> red
                index < 96 -> blue
                else -> green
            }
        }

        val first = representativeCoverArgbs(pixels, maxColors = 4)
        val second = representativeCoverArgbs(pixels, maxColors = 4)

        assertEquals(first, second)
        assertEquals(3, first.size)
        assertTrue(red(first.first()) > blue(first.first()))
        assertTrue(first.any { blue(it) > red(it) })
        assertTrue(first.any { green(it) > red(it) && green(it) > blue(it) })
    }

    @Test
    fun `palette rejects transparent and near black padding and respects the limit`() {
        val pixels = intArrayOf(
            argb(0, 230, 40, 40),
            argb(255, 3, 4, 5),
            argb(255, 170, 60, 70),
            argb(255, 45, 90, 190),
            argb(255, 40, 155, 90),
        )

        val palette = representativeCoverArgbs(pixels, maxColors = 2)

        assertEquals(2, palette.size)
        assertTrue(palette.none { red(it) < 20 && green(it) < 20 && blue(it) < 20 })
        assertTrue(representativeCoverArgbs(pixels, maxColors = 0).isEmpty())
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    private fun red(color: Int): Int = color ushr 16 and 0xFF
    private fun green(color: Int): Int = color ushr 8 and 0xFF
    private fun blue(color: Int): Int = color and 0xFF
}
