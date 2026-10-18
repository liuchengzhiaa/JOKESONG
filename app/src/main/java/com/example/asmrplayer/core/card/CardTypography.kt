package com.example.asmrplayer.core.card

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Draws the card's transparent text layer.
 *
 * The geometry lives in [CardTypographyLayout]; this only turns it into paint calls.
 * Two things are not straight transcriptions:
 *
 * - The vertical anchor. The source script anchors a string by its **ascender** line,
 *   while Skia draws from the **baseline**, so each slot's y is shifted down by the
 *   font's own ascent. Reading the metric keeps the two renderers agreeing on where a
 *   line sits without hard-coding a number that changes with the typeface.
 * - The outline. Pillow takes a fill and a stroke fill in one call; a Skia `Paint`
 *   carries one colour, so each string is drawn twice, outline first. A stroke of
 *   width 2 is centred on the glyph edge, which is the same pixel as Pillow's
 *   `stroke_width=1` growing outward on both sides.
 */
internal object CardTypography {

    fun render(
        fields: CardTextFields,
        typeface: Typeface,
        width: Int = CardTypographyLayout.CanvasWidth,
        height: Int = CardTypographyLayout.CanvasHeight,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val xScale = width.toFloat() / CardTypographyLayout.CanvasWidth
        val yScale = height.toFloat() / CardTypographyLayout.CanvasHeight
        val outline = maxOf(1f, Math.round(xScale).toFloat()) * 2f

        val measure = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = outline
            color = withAlpha(CardTypographyLayout.StrokeColor, CardTypographyLayout.StrokeAlpha)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            style = Paint.Style.FILL
        }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = opaque(CardTypographyLayout.Gold)
            style = Paint.Style.FILL
        }

        CardTypographyLayout.Rules.forEach { placement ->
            val thickness = maxOf(1f, Math.round(placement.width * xScale).toFloat())
            val centre = placement.y * yScale
            canvas.drawRect(
                placement.startX * xScale,
                centre - thickness / 2f,
                placement.endX * xScale,
                centre + thickness / 2f,
                rule,
            )
        }

        CardTypographyLayout.slotsFor(fields).forEach { (slot, text) ->
            // A blank field is a slot with nothing in it, which is legal everywhere.
            if (text.isEmpty()) return@forEach
            val size = CardTypographyLayout.fittedSize(text, slot, xScale) { value, atSize ->
                measure.textSize = atSize
                measure.measureText(value)
            }
            stroke.textSize = size
            fill.textSize = size
            fill.color = opaque(slot.color)
            val baseline = slot.y * yScale - measure.fontMetrics.ascent
            val x = slot.x * xScale
            canvas.drawText(text, x, baseline, stroke)
            canvas.drawText(text, x, baseline, fill)
        }
        return bitmap
    }

    private fun opaque(rgb: Int): Int = 0xFF000000.toInt() or (rgb and 0xFFFFFF)

    private fun withAlpha(rgb: Int, alpha: Int): Int = (alpha shl 24) or (rgb and 0xFFFFFF)
}
