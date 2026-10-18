package com.example.asmrplayer.core.card

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The name a saved card lands under comes from the page, which builds it from the
 * card's own title -- free text, typed by hand, in a language with no spaces in it. The
 * media store rejects a name that still holds a separator or a reserved character, and
 * an insert that throws is a save that silently does nothing, so the cleaning is worth
 * pinning rather than trusting.
 */
class CardExporterTest {

    @Test
    fun `a plain title gains the extension`() {
        assertEquals("BOND.png", CardExporter.displayName("BOND"))
    }

    @Test
    fun `a name that already carries the extension does not gain a second`() {
        assertEquals("card.png", CardExporter.displayName("card.png"))
    }

    @Test
    fun `a path from the page is reduced to its last segment`() {
        assertEquals("front.png", CardExporter.displayName("cards/front"))
        assertEquals("front.png", CardExporter.displayName("cards\\front"))
        assertEquals("front.png", CardExporter.displayName("../../front"))
        assertEquals("front.png", CardExporter.displayName("C:/cards/front.png"))
    }

    @Test
    fun `the characters a filesystem reserves are dropped`() {
        assertEquals("0123.png", CardExporter.displayName("01:23"))
        assertEquals("awb.png", CardExporter.displayName("a*w?b"))
        assertEquals("sayhipng.png", CardExporter.displayName("say\"hi\"p<n>g"))
    }

    @Test
    fun `a trailing dot is dropped rather than read as an extension`() {
        assertEquals("card.png", CardExporter.displayName("card."))
        assertEquals("card.png", CardExporter.displayName("card.png."))
    }

    @Test
    fun `control characters never reach the store`() {
        assertEquals("ab.png", CardExporter.displayName("a\tb"))
        assertEquals("ab.png", CardExporter.displayName("a\nb"))
        assertEquals("ab.png", CardExporter.displayName("a\u0000b"))
    }

    @Test
    fun `surrounding whitespace is trimmed but a single character is kept`() {
        assertEquals("A.png", CardExporter.displayName("  A  "))
        assertEquals("共生.png", CardExporter.displayName("\t共生\n"))
    }

    @Test
    fun `a name the store will not take falls back to one it will`() {
        assertEquals("art-card.png", CardExporter.displayName(""))
        assertEquals("art-card.png", CardExporter.displayName("   "))
        assertEquals("art-card.png", CardExporter.displayName("..."))
        assertEquals("art-card.png", CardExporter.displayName(".png"))
        assertEquals("art-card.png", CardExporter.displayName("cards/"))
        assertEquals("art-card.png", CardExporter.displayName("<>:|?*"))
    }

    @Test
    fun `a very long title is cut to the length the store takes`() {
        val name = CardExporter.displayName("a".repeat(200))

        assertEquals(96 + ".png".length, name.length)
        assertEquals("a".repeat(96) + ".png", name)
    }

    @Test
    fun `a long title keeps its head rather than its tail`() {
        val name = CardExporter.displayName("BOND".repeat(50))

        assertEquals("BOND".repeat(24) + ".png", name)
    }
}
