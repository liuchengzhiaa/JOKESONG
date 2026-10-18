package com.example.asmrplayer.core.card

import java.nio.FloatBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The segmenter's confidence mask becomes the subject layer's alpha channel, and every
 * way that can go wrong is quiet: a mask taken at the wrong size stretches the cut-out
 * across the card, and a missing floor leaves a faint wash of the model's uncertainty
 * over the whole frame. Both are visible only on a phone, as a card that looks subtly
 * wrong, so they are pinned here instead.
 */
class SubjectMaskTest {

    private val opaque = 0xFFFFFFFF.toInt()

    /** Alpha zero. The colour is white regardless, which is why this is not `0`. */
    private val transparent = 0x00FFFFFF

    private fun buffer(vararg values: Float): FloatBuffer = FloatBuffer.wrap(floatArrayOf(*values))

    @Test
    fun `the floor is where the mask says it is`() {
        assertEquals(0.35f, SubjectMask.ConfidenceFloor, 0f)
    }

    @Test
    fun `confidence at or below the floor is fully transparent`() {
        assertEquals(0f, SubjectMask.alpha(0f), 0f)
        assertEquals(0f, SubjectMask.alpha(SubjectMask.ConfidenceFloor), 0f)
        assertEquals(0f, SubjectMask.alpha(-1f), 0f)
    }

    @Test
    fun `certainty is fully opaque and cannot overflow it`() {
        assertEquals(1f, SubjectMask.alpha(1f), 0f)
        assertEquals(1f, SubjectMask.alpha(2f), 0f)
    }

    @Test
    fun `the range above the floor is stretched back over the whole of it`() {
        // Without the rescale everything between the floor and certainty would be
        // squeezed into the top of the range and every edge would read as a hard cut.
        val floor = SubjectMask.ConfidenceFloor
        val middle = floor + (1f - floor) / 2f

        assertEquals(0.5f, SubjectMask.alpha(middle), 0.0001f)
        assertTrue(SubjectMask.alpha(floor + 0.01f) > 0f)
    }

    @Test
    fun `more confidence never means less alpha`() {
        var previous = 0f
        var confidence = -0.2f
        while (confidence <= 1.2f) {
            val alpha = SubjectMask.alpha(confidence)
            assertTrue("at $confidence", alpha >= previous)
            assertTrue("at $confidence", alpha in 0f..1f)
            previous = alpha
            confidence += 0.01f
        }
    }

    @Test
    fun `a mask that matches the input canvas is taken at its word`() {
        assertEquals(8 to 12, SubjectMask.size(FloatBuffer.allocate(8 * 12), 8, 12))
    }

    @Test
    fun `a mask at another resolution is recovered from its aspect ratio`() {
        // 24 floats at the canvas's own 2:3 shape is a 4x6 mask, which is what a
        // segmenter running on a downscaled copy of the frame would hand back.
        assertEquals(4 to 6, SubjectMask.size(FloatBuffer.allocate(24), 8, 12))
    }

    @Test
    fun `a buffer that is not a rectangle is refused rather than guessed at`() {
        assertNull(SubjectMask.size(FloatBuffer.allocate(7), 8, 12))
        assertNull(SubjectMask.size(FloatBuffer.allocate(5), 8, 12))
        assertNull(SubjectMask.size(FloatBuffer.allocate(100), 8, 12))
    }

    @Test
    fun `an empty buffer or a degenerate canvas is refused`() {
        assertNull(SubjectMask.size(FloatBuffer.allocate(0), 8, 12))
        assertNull(SubjectMask.size(FloatBuffer.allocate(24), 0, 12))
        assertNull(SubjectMask.size(FloatBuffer.allocate(24), 8, 0))
        assertNull(SubjectMask.size(FloatBuffer.allocate(24), -8, 12))
    }

    @Test
    fun `the confidence lands in the alpha channel and nowhere else`() {
        // The colour is white because nothing reads it: the mask is composited with
        // DST_IN, which looks at the source alpha alone.
        val pixels = SubjectMask.pixels(
            buffer(0f, 1f, SubjectMask.ConfidenceFloor, 2f),
            2,
            2,
        )

        assertEquals(4, pixels.size)
        assertEquals(transparent, pixels[0])
        assertEquals(opaque, pixels[1])
        assertEquals(transparent, pixels[2])
        assertEquals(opaque, pixels[3])
    }

    @Test
    fun `a short buffer leaves the rest transparent rather than repeating it`() {
        val pixels = SubjectMask.pixels(buffer(1f, 1f), 2, 2)

        assertEquals(opaque, pixels[0])
        assertEquals(opaque, pixels[1])
        assertEquals(transparent, pixels[2])
        assertEquals(transparent, pixels[3])
    }

    @Test
    fun `a buffer left part way through is read from its start`() {
        // The buffer comes out of a segmentation result that may already have been
        // touched; reading from wherever it was left would skew the whole mask.
        val source = buffer(1f, 0f)
        source.position(1)

        val pixels = SubjectMask.pixels(source, 2, 1)

        assertEquals(opaque, pixels[0])
        assertEquals(transparent, pixels[1])
    }
}
