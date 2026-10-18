package com.example.asmrplayer.core.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The text layer is welded to the card: the viewer samples `text.png` with no parallax,
 * so a slot a few pixels out is a slot that is wrong on every card ever made. These
 * numbers are transcribed from the skill's own typography script, and pinning them here
 * is what stops a later tidy-up from quietly moving the title.
 */
class CardTypographyLayoutTest {

    @Test
    fun `the design canvas is the card face`() {
        assertEquals(1024, CardTypographyLayout.CanvasWidth)
        assertEquals(1536, CardTypographyLayout.CanvasHeight)
    }

    @Test
    fun `the title sits top left on the first rule`() {
        val title = CardTypographyLayout.Title

        assertEquals(72, title.x)
        assertEquals(87, title.y)
        assertEquals(88, title.size)
        assertSame(CardTypographyLayout.Anchor.Left, title.anchor)
        assertEquals(CardTypographyLayout.Cream, title.color)
    }

    @Test
    fun `the two rules bracket the artwork window`() {
        val rules = CardTypographyLayout.Rules

        assertEquals(2, rules.size)
        assertEquals(245, rules[0].y)
        assertEquals(1280, rules[1].y)
        for (rule in rules) {
            assertEquals(70, rule.startX)
            assertEquals(954, rule.endX)
            assertEquals(2, rule.width)
        }
    }

    @Test
    fun `the centred slots are centred on the canvas`() {
        for (slot in listOf(CardTypographyLayout.Tagline, CardTypographyLayout.Technique)) {
            assertSame(CardTypographyLayout.Anchor.Centre, slot.anchor)
            assertEquals(CardTypographyLayout.CanvasWidth / 2, slot.x)
        }
    }

    @Test
    fun `the holographic mark is right aligned and grows leftward`() {
        val mark = CardTypographyLayout.Holographic

        assertSame(CardTypographyLayout.Anchor.Right, mark.anchor)
        assertEquals(CardTypographyLayout.CanvasWidth - 74, mark.x)
        assertTrue(mark.x - mark.maxWidth >= 0)
    }

    @Test
    fun `every slot fits between the margins`() {
        val slots = CardTypographyLayout.slotsFor(CardTextFields()).map { it.first }

        assertEquals(7, slots.size)
        for (slot in slots) {
            assertTrue("x of $slot", slot.x >= 0 && slot.x <= CardTypographyLayout.CanvasWidth)
            assertTrue("y of $slot", slot.y >= 0 && slot.y < CardTypographyLayout.CanvasHeight)
            assertTrue("width of $slot", slot.maxWidth > 0)
        }
    }

    @Test
    fun `slots carry the fields in drawing order`() {
        val fields = CardTextFields(
            title = "T",
            subtitle = "S",
            collection = "C",
            tagline = "G",
            technique = "Q",
            edition = "E",
        )

        val text = CardTypographyLayout.slotsFor(fields).map { it.second }

        assertEquals(listOf("S", "T", "C", "G", "Q", "E", CardTypographyLayout.HolographicText), text)
    }

    @Test
    fun `a short title is drawn at the slot's own size`() {
        val size = CardTypographyLayout.fittedSize("BOND", CardTypographyLayout.Title, 1f) { _, _ -> 10f }

        assertEquals(88f, size, 0.001f)
    }

    @Test
    fun `an overlong title shrinks until it fits`() {
        // A measurer that reports one unit of width per character per point, so the
        // arithmetic in the loop is checkable by hand.
        val widthOf: (String, Float) -> Float = { text, size -> text.length * size }
        val long = "A".repeat(30)

        val size = CardTypographyLayout.fittedSize(long, CardTypographyLayout.Title, 1f, widthOf)

        assertEquals(29f, size, 0.001f)
        assertTrue(long.length * size <= CardTypographyLayout.Title.maxWidth)
    }

    @Test
    fun `shrinking stops at the floor rather than at nothing`() {
        val widthOf: (String, Float) -> Float = { text, size -> text.length * size }
        val absurd = "A".repeat(100_000)

        val size = CardTypographyLayout.fittedSize(absurd, CardTypographyLayout.Title, 1f, widthOf)

        assertEquals(CardTypographyLayout.MinimumTextSize.toFloat(), size, 0.001f)
    }

    @Test
    fun `a blank field is never measured`() {
        var measured = false
        val widthOf: (String, Float) -> Float = { _, _ ->
            measured = true
            0f
        }

        CardTypographyLayout.fittedSize("", CardTypographyLayout.Title, 1f, widthOf)

        assertFalse(measured)
    }

    @Test
    fun `the stroke keeps the card's copy legible over artwork`() {
        assertEquals(0x10151B, CardTypographyLayout.StrokeColor)
        assertEquals(220, CardTypographyLayout.StrokeAlpha)
    }
}
