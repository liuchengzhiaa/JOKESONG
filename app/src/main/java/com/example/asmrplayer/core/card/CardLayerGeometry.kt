package com.example.asmrplayer.core.card

/**
 * How a cover of any shape becomes a card layer of exactly one shape.
 *
 * Every layer of a card is composited on one shared canvas, so all three must be
 * derived from the same crop. `art-direction.md` is explicit that layers produced on
 * different crops cannot be registered afterwards, which makes this the single place
 * the crop is allowed to be decided.
 *
 * Free of `android.graphics` so the arithmetic can be checked in a JVM unit test: the
 * cases that matter (a tall cover, a wide one, one already card-shaped, one smaller
 * than the canvas) are all just numbers.
 */
internal object CardLayerGeometry {

    const val CanvasWidth = CardTypographyLayout.CanvasWidth
    const val CanvasHeight = CardTypographyLayout.CanvasHeight

    /**
     * The size of the flat preview written beside the three layers.
     *
     * Half the face, because the only thing that reads it is a tile in the warehouse
     * grid, which draws it at a fraction of that. Storing it at the face's own size would
     * add megabytes to every card for pixels nothing ever shows.
     */
    const val PreviewWidth = CanvasWidth / 2
    const val PreviewHeight = CanvasHeight / 2

    /**
     * The card face's own proportions, width over height.
     *
     * Public because two surfaces outside the build path have to draw the card as itself
     * rather than as a square: the compose form's cover preview, and the player's cover
     * slot, where a square frame around a portrait card is what leaves the card sitting
     * in a wide margin. Derived from the canvas rather than written as 1024f / 1536f so
     * the three cannot drift.
     */
    val CanvasAspect: Float get() = CanvasWidth.toFloat() / CanvasHeight

    /** Where a source rectangle in the source image is copied from, as floats. */
    data class Crop(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /**
     * The largest centred rectangle of [sourceWidth]x[sourceHeight] that has the
     * canvas's aspect ratio.
     *
     * Frame-filling, not letterboxing: a card whose artwork floated inside a bar of
     * empty space would read as a mistake at card size, and the reference cards all
     * fill the face. The excess is taken from both sides equally, which is what keeps
     * a centred subject centred -- an off-centre crop would move the subject out from
     * under the typography that is placed relative to the canvas, not to the subject.
     */
    fun centreCrop(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int = CanvasWidth,
        targetHeight: Int = CanvasHeight,
    ): Crop {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return Crop(0f, 0f, sourceWidth.toFloat(), sourceHeight.toFloat())
        }
        val targetAspect = targetWidth.toFloat() / targetHeight
        val sourceAspect = sourceWidth.toFloat() / sourceHeight
        return if (sourceAspect > targetAspect) {
            // Wider than the card: trim the sides.
            val kept = sourceHeight * targetAspect
            val inset = (sourceWidth - kept) / 2f
            Crop(inset, 0f, inset + kept, sourceHeight.toFloat())
        } else {
            // Taller than the card: trim the top and bottom.
            val kept = sourceWidth / targetAspect
            val inset = (sourceHeight - kept) / 2f
            Crop(0f, inset, sourceWidth.toFloat(), inset + kept)
        }
    }

    /**
     * How far the background is magnified before it is blurred.
     *
     * The background sits on its own depth plane, so at the extremes of the viewer's
     * tilt it slides against the subject. Magnifying it slightly means those edges are
     * still covered by the layer rather than pulling a bare card edge into view.
     */
    const val BackgroundOverscan = 1.06f

    /** The edge, in pixels, the background and subject are reduced to before blurring. */
    const val BlurSourceEdge = 64

    /**
     * The fractional height of the bands top and bottom that the typography occupies.
     *
     * `art-direction.md` asks for roughly the top and bottom 15% to be kept quiet so
     * the copy stays legible; the generated background darkens them rather than
     * relying on the cover happening to be plain there.
     */
    const val TypographyBandFraction = 0.15f
}
