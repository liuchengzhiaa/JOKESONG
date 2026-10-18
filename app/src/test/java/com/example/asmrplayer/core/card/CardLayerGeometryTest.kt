package com.example.asmrplayer.core.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crop is the one thing every layer of a card shares, so an error here is not a
 * slightly-off picture: the three layers are composited with per-layer parallax and a
 * mismatch between them slides one against the others as the card tilts.
 */
class CardLayerGeometryTest {

    @Test
    fun `a wide cover is trimmed at the sides only`() {
        val crop = CardLayerGeometry.centreCrop(4000, 2000)

        assertEquals(0f, crop.top, 0.001f)
        assertEquals(2000f, crop.bottom, 0.001f)
        assertTrue(crop.left > 0f)
        assertEquals(4000f - crop.right, crop.left, 0.001f)
    }

    @Test
    fun `a tall cover is trimmed at the top and bottom only`() {
        val crop = CardLayerGeometry.centreCrop(1000, 4000)

        assertEquals(0f, crop.left, 0.001f)
        assertEquals(1000f, crop.right, 0.001f)
        assertTrue(crop.top > 0f)
        assertEquals(4000f - crop.bottom, crop.top, 0.001f)
    }

    @Test
    fun `every source shape crops to the canvas aspect ratio`() {
        val aspect = CardLayerGeometry.CanvasWidth.toFloat() / CardLayerGeometry.CanvasHeight
        for ((width, height) in listOf(4000 to 2000, 1000 to 4000, 1024 to 1536, 7 to 3, 300 to 300)) {
            val crop = CardLayerGeometry.centreCrop(width, height)
            assertEquals("${width}x$height", aspect, crop.width / crop.height, 0.001f)
        }
    }

    @Test
    fun `a cover already card shaped is not cropped at all`() {
        val crop = CardLayerGeometry.centreCrop(
            CardLayerGeometry.CanvasWidth,
            CardLayerGeometry.CanvasHeight,
        )

        assertEquals(0f, crop.left, 0.001f)
        assertEquals(0f, crop.top, 0.001f)
        assertEquals(1024f, crop.right, 0.001f)
        assertEquals(1536f, crop.bottom, 0.001f)
    }

    @Test
    fun `the crop is frame filling rather than letterboxed`() {
        // The crop always touches two opposite edges of the source and is cut short on
        // the other axis. A crop short on *both* would be a scaled-down copy floating
        // in empty space -- a visible band of nothing on the card's face.
        for ((width, height) in listOf(1000 to 1000, 4000 to 2000, 1000 to 4000, 300 to 300)) {
            val crop = CardLayerGeometry.centreCrop(width, height)
            val touches = crop.width == width.toFloat() || crop.height == height.toFloat()

            assertTrue("${width}x$height touches no edge", touches)
            assertTrue("${width}x$height was not cut at all", crop.width < width || crop.height < height)
        }
    }

    @Test
    fun `a square source is trimmed at its sides because it is wider than the card`() {
        val crop = CardLayerGeometry.centreCrop(1000, 1000)

        assertEquals(1000f, crop.height, 0.001f)
        assertTrue(crop.width < 1000f)
        assertEquals(1000f - crop.right, crop.left, 0.001f)
    }

    @Test
    fun `the same source always crops the same way`() {
        val first = CardLayerGeometry.centreCrop(3333, 1777)
        val second = CardLayerGeometry.centreCrop(3333, 1777)

        assertEquals(first, second)
    }

    @Test
    fun `the crop stays inside the source`() {
        for ((width, height) in listOf(1 to 1, 3 to 2, 2 to 3, 1920 to 1080, 1080 to 1920)) {
            val crop = CardLayerGeometry.centreCrop(width, height)
            assertTrue("${width}x$height left", crop.left >= 0f)
            assertTrue("${width}x$height top", crop.top >= 0f)
            assertTrue("${width}x$height right", crop.right <= width + 0.001f)
            assertTrue("${width}x$height bottom", crop.bottom <= height + 0.001f)
        }
    }

    @Test
    fun `a degenerate source is passed through rather than divided by`() {
        val crop = CardLayerGeometry.centreCrop(0, 500)

        assertEquals(0f, crop.width, 0.001f)
        assertEquals(500f, crop.height, 0.001f)
    }

    @Test
    fun `the warehouse preview is the card face at half size`() {
        // It is drawn into the tile at the card's aspect ratio, so the two have to agree:
        // a preview at a different proportion would be cropped again by the tile and read
        // as a badly framed card rather than as a smaller picture of a correct one.
        assertEquals(CardLayerGeometry.CanvasWidth / 2, CardLayerGeometry.PreviewWidth)
        assertEquals(CardLayerGeometry.CanvasHeight / 2, CardLayerGeometry.PreviewHeight)
        assertEquals(512, CardLayerGeometry.PreviewWidth)
        assertEquals(768, CardLayerGeometry.PreviewHeight)
        assertEquals(
            CardLayerGeometry.CanvasWidth.toFloat() / CardLayerGeometry.CanvasHeight,
            CardLayerGeometry.PreviewWidth.toFloat() / CardLayerGeometry.PreviewHeight,
            0.0001f,
        )
    }

    @Test
    fun `the canvas aspect is the card's own proportions`() {
        // Three surfaces outside the build path draw a card as itself rather than as a
        // square -- the compose form's cover preview, the warehouse tile, and the player's
        // cover slot -- so this is the one number they all size against. Asserted against
        // the canvas it is derived from rather than against 0.6667 so a change to the
        // canvas cannot leave the three of them drawing a differently shaped card than
        // the one the viewer loads.
        assertEquals(
            CardLayerGeometry.CanvasWidth.toFloat() / CardLayerGeometry.CanvasHeight,
            CardLayerGeometry.CanvasAspect,
            0.0001f,
        )
        assertEquals(2f / 3f, CardLayerGeometry.CanvasAspect, 0.0001f)
        assertTrue(CardLayerGeometry.CanvasAspect < 1f)
    }

    @Test
    fun `the preview is a quarter of the card's pixels`() {
        // The reason it is a half and not a copy: a card's `card.png` capture is already
        // 1400x1800, and a second full-size picture of the same face would double what a
        // card costs on disk to draw a 148dp tile.
        val card = CardLayerGeometry.CanvasWidth * CardLayerGeometry.CanvasHeight
        val preview = CardLayerGeometry.PreviewWidth * CardLayerGeometry.PreviewHeight

        assertEquals(card / 4, preview)
    }
}
