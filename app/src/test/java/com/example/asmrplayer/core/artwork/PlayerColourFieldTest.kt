package com.example.asmrplayer.core.artwork

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class PlayerColourFieldTest {
    private val red = 0xFFC55065.toInt()
    private val blue = 0xFF6655B0.toInt()
    private val gold = 0xFF92754A.toInt()

    @Test fun spatialColoursRemainDistinctAndInSourceOrder() {
        val pixels = IntArray(900) { when (it % 30 / 10) { 0 -> red; 1 -> gold; else -> blue } }
        val field = playerColourField(pixels, 30, 30)!!
        assertEquals(listOf(red, gold, blue, red, gold, blue, red, gold, blue), field.colours)
        val render = renderPlayerColourField(field)
        val left = render[PlayerFieldWidth * 72]
        val right = render[PlayerFieldWidth * 72 + PlayerFieldWidth - 1]
        assertTrue((left ushr 16 and 255) > (left and 255))
        assertTrue((right and 255) > (right ushr 16 and 255))
    }

    @Test fun paleHighlightsDoNotEraseARegionsMidtoneColour() {
        val pixels = IntArray(900) { if (it % 3 == 0) red else 0xFFF8EEE0.toInt() }
        assertTrue(playerColourField(pixels, 30, 30)!!.colours.all { it == red })
        // A genuinely bright single-colour cover still keeps its hue.
        val vivid = 0xFFFF1122.toInt()
        assertTrue(playerColourField(IntArray(900) { vivid }, 30, 30)!!.colours.all { it == vivid })
    }

    @Test fun blackPaddingDarkensTheFieldWithoutChoosingBlackAsItsHue() {
        val pixels = IntArray(3600) { i ->
            if (i % 60 in 20..39 && i / 60 in 20..39) blue else 0xFF000000.toInt()
        }
        val field = playerColourField(pixels, 60, 60)!!
        assertTrue(field.darkFraction > 0.8f)
        assertTrue(field.colours.all { it == blue })
        val dark = renderPlayerColourField(field)[7000]
        val light = renderPlayerColourField(PlayerColourField(List(9) { blue }, 0f))[7000]
        assertTrue((dark and 255) < (light and 255))
    }

    @Test fun regionalShadowsAreNotFlattenedToOneGlobalBrightness() {
        val pixels = IntArray(900) { if (it % 30 < 20) blue else 0xFF000000.toInt() }
        val field = playerColourField(pixels, 30, 30)!!
        assertEquals(0f, field.regionDarkFractions[0], 0f)
        assertEquals(1f, field.regionDarkFractions[2], 0f)
        val rendered = renderPlayerColourField(field)
        assertTrue((rendered[96 * 72] and 255) > (rendered[96 * 72 + 95] and 255))
    }

    @Test fun neutralCoversDoNotAcquireInventedSaturatedHues() {
        for (gray in listOf(0xFF000000.toInt(), 0xFF777777.toInt(), 0xFFFFFFFF.toInt())) {
            val field = playerColourField(IntArray(900) { gray }, 30, 30)!!
            assertTrue(renderPlayerColourField(field).all {
                (it ushr 16 and 255) == (it ushr 8 and 255) && (it ushr 8 and 255) == (it and 255)
            })
        }
    }

    @Test fun transparentPixelsNeverContributeTheirRgb() {
        val pixels = IntArray(900) { if (it % 2 == 0) red else 0x0000FF00 }
        assertTrue(playerColourField(pixels, 30, 30)!!.colours.all { it == red })
        assertNull(playerColourField(IntArray(900), 30, 30))
    }

    @Test fun invalidInputIsRejectedWithoutGuessingDimensions() {
        assertNull(playerColourField(intArrayOf(1), 0, 1))
        assertNull(playerColourField(intArrayOf(1), Int.MAX_VALUE, Int.MAX_VALUE))
        assertNull(playerColourField(intArrayOf(1), 2, 2))
    }

    @Test fun outputIsDeterministicOpaqueAndFixedSize() {
        val field = PlayerColourField(listOf(red, blue, gold, blue, gold, red, gold, red, blue), 0.3f)
        val first = renderPlayerColourField(field)
        assertEquals(96 * 144, first.size)
        assertArrayEquals(first, renderPlayerColourField(field))
        assertTrue(first.all { it ushr 24 == 255 })
        assertTrue(first.toSet().size > 100)
    }

    @Test fun whiteForegroundRetainsAtLeastFourPointFiveContrastAcrossTheField() {
        fun linear(v: Int): Double {
            val x = v / 255.0
            return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        val colours = listOf(0xFFFFFFFF.toInt(), 0xFFFFFF00.toInt(), red, blue, gold,
            0xFF00FFFF.toInt(), 0xFFFF00FF.toInt(), 0xFF00FF00.toInt(), 0xFF000000.toInt())
        for (pixel in renderPlayerColourField(PlayerColourField(colours, 0f))) {
            val luminance = linear(pixel ushr 16 and 255) * 0.2126 +
                linear(pixel ushr 8 and 255) * 0.7152 + linear(pixel and 255) * 0.0722
            assertTrue(1.05 / (luminance + 0.05) >= 4.5)
        }
    }

    @Test fun onePixelWideImagesAreStillBounded() {
        val field = playerColourField(IntArray(10000) { blue }, 1, 10000)!!
        assertEquals(9, field.colours.size)
        assertEquals(96 * 144, renderPlayerColourField(field).size)
    }

    @Test fun generatedColourOnlyPreviewUsesTheActualProductionRenderer() {
        // Generated plain colours only; this review fixture never reads artwork/user media.
        val fields = listOf(
            PlayerColourField(List(9) { 0xFF773377.toInt() }, 0.72f, listOf(0.2f, 0.8f, 1f, 0.3f, 0.7f, 1f, 0.7f, 0.9f, 1f)),
            PlayerColourField(listOf(red, red, blue, red, gold, blue, blue, red, gold), 0.05f),
            PlayerColourField(List(9) { 0xFF999999.toInt() }, 0f),
        )
        val preview = BufferedImage(864, 432, BufferedImage.TYPE_INT_RGB)
        val graphics = preview.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            fields.forEachIndexed { i, field ->
                val bitmap = BufferedImage(96, 144, BufferedImage.TYPE_INT_ARGB)
                bitmap.setRGB(0, 0, 96, 144, renderPlayerColourField(field), 0, 96)
                graphics.drawImage(bitmap, i * 288, 0, 288, 432, null)
            }
        } finally { graphics.dispose() }
        val directory = File("build/reports/player-field").apply { mkdirs() }
        assertTrue(ImageIO.write(preview, "png", File(directory, "synthetic-fields.png")))
    }
}
