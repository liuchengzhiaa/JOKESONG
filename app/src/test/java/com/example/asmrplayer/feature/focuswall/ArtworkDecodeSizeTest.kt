package com.example.asmrplayer.feature.focuswall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkDecodeSizeTest {
    @Test
    fun `a card that is not magnified decodes at its layout size`() {
        assertEquals(320, artworkDecodeSize(widthPx = 320f, heightPx = 200f, zoom = 1f).width)
        assertEquals(200, artworkDecodeSize(widthPx = 320f, heightPx = 200f, zoom = 1f).height)
    }

    @Test
    fun `zooming out never shrinks the request below the layout size`() {
        // MinimumCanvasZoom is below 1, and a card that is drawn smaller than its
        // layout is not blurred by it -- asking for less would only risk decoding a
        // second, smaller copy of the same artwork.
        val size = artworkDecodeSize(widthPx = 320f, heightPx = 200f, zoom = MinimumCanvasZoom)
        assertEquals(320, size.width)
        assertEquals(200, size.height)
    }

    @Test
    fun `the decode scale rounds up into a bucket and never decreases`() {
        val scales = listOf(MinimumCanvasZoom, 0.9f, 1f, 1.05f, 1.2f, MaximumCanvasZoom, 3f)
            .map(::artworkDecodeScale)
        assertEquals(scales.sorted(), scales)
        assertTrue("magnified zoom must decode above the layout size", artworkDecodeScale(1.2f) > 1f)
        // Whole-number buckets: a pinch re-decodes a handful of times, not per frame.
        assertEquals(3, scales.distinct().size)
    }

    @Test
    fun `an oversized card is capped instead of decoding an enormous bitmap`() {
        val size = artworkDecodeSize(widthPx = 4000f, heightPx = 3000f, zoom = MaximumCanvasZoom)
        assertEquals(1536, size.width)
        assertEquals(1536, size.height)
    }
}
