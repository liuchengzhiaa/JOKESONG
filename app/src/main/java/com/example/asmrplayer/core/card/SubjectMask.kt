package com.example.asmrplayer.core.card

import java.nio.FloatBuffer
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The arithmetic that turns the segmenter's confidence mask into an alpha channel.
 *
 * Free of `android.graphics` so it can be checked in a plain JVM unit test: both halves
 * are pure functions of numbers, and both have failure modes that are invisible on a
 * device until a card looks subtly wrong.
 */
internal object SubjectMask {

    /**
     * Below this, a pixel is background. Above it, the mask keeps its own gradient.
     *
     * A hard threshold at zero would keep a faint wash of the model's uncertainty over
     * the whole frame -- the subject plane would be a rectangle of slightly-visible
     * background rather than a cut-out, which is exactly the ghosting the separate depth
     * planes exist to avoid.
     */
    const val ConfidenceFloor = 0.35f

    /** Clips the bottom of [confidence]'s range and rescales the rest to 0..1. */
    fun alpha(confidence: Float): Float {
        if (confidence <= ConfidenceFloor) return 0f
        return ((confidence - ConfidenceFloor) / (1f - ConfidenceFloor)).coerceIn(0f, 1f)
    }

    /**
     * The mask's pixel dimensions, derived from the buffer's length.
     *
     * The mask is documented to match the input image, and that is checked first. The
     * rest of this exists so a mask at some other resolution is still usable rather than
     * silently thrown away: the aspect ratio is the one property that must hold, so the
     * height is solved from it and the width is only accepted if it divides the buffer
     * exactly. Null means the buffer is not a rectangle at all, and the caller falls
     * back to treating the whole cover as the subject.
     */
    fun size(buffer: FloatBuffer, canvasWidth: Int, canvasHeight: Int): Pair<Int, Int>? {
        if (canvasWidth <= 0 || canvasHeight <= 0) return null
        val floats = buffer.capacity()
        if (floats <= 0) return null
        if (floats == canvasWidth * canvasHeight) return canvasWidth to canvasHeight
        val height = sqrt(floats.toDouble() * canvasHeight / canvasWidth).roundToInt()
        if (height <= 0) return null
        val width = floats / height
        return if (width <= 0 || width * height != floats) null else width to height
    }

    /** The mask as an ARGB pixel array, with the confidence in the alpha channel only. */
    fun pixels(buffer: FloatBuffer, width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        buffer.rewind()
        for (index in pixels.indices) {
            val confidence = if (buffer.remaining() > 0) buffer.get() else 0f
            val channel = (alpha(confidence) * 255f).roundToInt().coerceIn(0, 255)
            // The colour is white because it is never read: the mask is composited with
            // DST_IN, which looks at the source alpha and nothing else.
            pixels[index] = channel shl 24 or 0x00FFFFFF
        }
        return pixels
    }
}
