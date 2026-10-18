package com.example.asmrplayer.core.card

/**
 * Where every piece of card copy goes, in the design canvas's own coordinates.
 *
 * This is a port of the skill's `scripts/generate_typography.py`, which is the
 * authoritative description of the card's text layer: the viewer samples `text.png`
 * with no parallax, so a slot that is a few pixels off is welded to the wrong place on
 * the card and cannot be corrected later. Everything here is therefore a literal
 * transcription -- the same 1024x1536 canvas, the same nine placements, the same two
 * colours, the same `max(1, round(scale))` stroke -- rather than a re-design.
 *
 * Kept free of `android.graphics` on purpose: the whole of the layout is arithmetic,
 * so it can be checked in a plain JVM unit test, and only the drawing needs a device.
 */
internal object CardTypographyLayout {

    const val CanvasWidth = 1024
    const val CanvasHeight = 1536

    /** The smallest size the shrink loop will go to, below which it stops shrinking. */
    const val MinimumTextSize = 10

    /** The floor the source script puts under every size, applied before the loop. */
    const val MinimumRenderedSize = 8

    const val Gold = 0xF4D087
    const val Cream = 0xFFF1CE

    /** Ink of the thin outline every glyph carries, so copy stays legible over artwork. */
    const val StrokeColor = 0x10151B
    const val StrokeAlpha = 220

    /** PIL anchors a string by its ascender line; Android draws from the baseline. */
    enum class Anchor { Left, Centre, Right }

    /**
     * One placement.
     *
     * [y] is the ascender line the source anchors to, not a baseline: converting it is
     * the drawing layer's job, because only it knows the font's metrics.
     */
    data class Slot(
        val x: Int,
        val y: Int,
        val size: Int,
        val anchor: Anchor,
        val color: Int,
        val maxWidth: Int,
    )

    data class Rule(val y: Int, val startX: Int, val endX: Int, val width: Int)

    val Subtitle = Slot(72, 49, 25, Anchor.Left, Gold, maxWidth = 850)
    val Title = Slot(72, 87, 88, Anchor.Left, Cream, maxWidth = 875)
    val Collection = Slot(76, 195, 22, Anchor.Left, Gold, maxWidth = 850)
    val Tagline = Slot(512, 1300, 31, Anchor.Centre, Gold, maxWidth = 850)
    val Technique = Slot(512, 1346, 69, Anchor.Centre, Cream, maxWidth = 850)
    val Edition = Slot(72, 1467, 20, Anchor.Left, Gold, maxWidth = 850)

    /**
     * The fixed word in the bottom-right corner. Not an editable field: it is part of
     * the card's furniture, and the reference cards all carry it verbatim.
     */
    val Holographic = Slot(950, 1467, 18, Anchor.Right, Gold, maxWidth = 380)
    const val HolographicText = "HOLOGRAPHIC"

    val Rules = listOf(
        Rule(y = 245, startX = 70, endX = 954, width = 2),
        Rule(y = 1280, startX = 70, endX = 954, width = 2),
    )

    /** Every slot, in drawing order, paired with the field it prints. */
    fun slotsFor(fields: CardTextFields): List<Pair<Slot, String>> = listOf(
        Subtitle to fields.subtitle,
        Title to fields.title,
        Collection to fields.collection,
        Tagline to fields.tagline,
        Technique to fields.technique,
        Edition to fields.edition,
        Holographic to HolographicText,
    )

    /**
     * The size a string is actually drawn at.
     *
     * The source shrinks a string one point at a time until it fits its slot's width or
     * reaches [MinimumTextSize], and does so rather than wrapping or truncating: the
     * card face has no room for a second line and a clipped title would read as a
     * mistake. An empty string is never measured, because a text measurer asked for the
     * width of nothing is free to answer anything.
     */
    fun fittedSize(
        text: String,
        slot: Slot,
        scale: Float,
        widthOf: (String, Float) -> Float,
    ): Float {
        var size = maxOf(MinimumRenderedSize.toFloat(), slot.size * scale)
        if (text.isEmpty()) return size
        val limit = slot.maxWidth * scale
        while (widthOf(text, size) > limit && size > MinimumTextSize) {
            size -= 1f
        }
        return size
    }
}
