package com.example.asmrplayer.core.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The config is read by a vendored page that has no schema of its own, so a malformed
 * document is a card that does not open, with the reason visible only in a console.
 * These tests pin the document rather than describing it.
 */
class CardConfigTest {

    private val reference = CardTextFields(
        title = "BOND",
        subtitle = "共生",
        collection = "个人全息典藏",
        tagline = "光与影，彼此相依。",
        technique = "全息浮雕",
        edition = "001 / 001",
        description = "共生 · BOND",
    )

    @Test
    fun `the document is exactly the shape the viewer reads`() {
        val expected = """
            {
              "title": "BOND",
              "subtitle": "共生",
              "collection": "个人全息典藏",
              "tagline": "光与影，彼此相依。",
              "technique": "全息浮雕",
              "edition": "001 / 001",
              "description": "共生 · BOND",
              "assets": {
                "model": "./assets/card.glb",
                "subject": "./assets/subject.png",
                "background": "./assets/background.png",
                "text": "./assets/text.png"
              },
              "parameters": {
                "subjectScale": 1.18,
                "subjectDepth": 0.42,
                "backgroundDepth": -0.28,
                "foil": 0.68
              },
              "safeArea": {
                "scale": 1.08,
                "offset": [0, -0.03]
              },
              "appearance": {
                "finish": "gold"
              }
            }

        """.trimIndent()

        assertEquals(expected, CardConfig(reference, CardStylePreset.Heirloom).toJson())
    }

    @Test
    fun `every asset path is relative so the card can live anywhere`() {
        val json = CardConfig(reference).toJson()

        for (path in listOf(
            CardConfig.ModelPath,
            CardConfig.SubjectPath,
            CardConfig.BackgroundPath,
            CardConfig.TextPath,
        )) {
            assertTrue(path, json.contains("\"$path\""))
            assertTrue(path, path.startsWith("./"))
        }
    }

    @Test
    fun `the layer depths are all different so the planes can separate`() {
        val config = CardConfig(reference)
        val preset = config.preset

        // Equal depths would weld two planes together and the card would stop reading as
        // a stack -- which is the whole effect.
        assertTrue(preset.subjectDepth != preset.backgroundDepth)
    }

    @Test
    fun `the relief-only keys are absent rather than defaulted`() {
        val json = CardConfig(reference).toJson()

        // Our layers are generated at the card face's own proportions, so the viewer's
        // own fit is the correct one; writing `sourceMode` would switch it to relief.
        assertFalse(json.contains("sourceMode"))
        assertFalse(json.contains("artworkFit"))
        assertFalse(json.contains("lineart"))
        assertFalse(json.contains("effects"))
    }

    @Test
    fun `a quote in a title cannot end the string it is in`() {
        val json = CardConfig(reference.copy(title = "say \"hi\"")).toJson()

        assertTrue(json.contains("\"title\": \"say \\\"hi\\\"\""))
    }

    @Test
    fun `a backslash and a newline survive the trip`() {
        val json = CardConfig(reference.copy(subtitle = "a\\b", tagline = "x\ny")).toJson()

        assertTrue(json.contains("\"subtitle\": \"a\\\\b\""))
        assertTrue(json.contains("\"tagline\": \"x\\ny\""))
        assertFalse(json.contains("x\ny"))
    }

    @Test
    fun `a control character is escaped rather than written raw`() {
        val json = CardConfig(reference.copy(title = "a\u0001b")).toJson()

        assertTrue(json.contains("a\\u0001b"))
    }

    @Test
    fun `numbers are written at the precision the sliders step in`() {
        assertEquals("1", number(1f))
        assertEquals("1.18", number(1.18f))
        assertEquals("-0.28", number(-0.28f))
        assertEquals("0", number(0f))
    }

    @Test
    fun `a blank card is legal and simply draws nothing`() {
        val json = CardConfig(CardTextFields()).toJson()

        assertTrue(json.contains("\"title\": \"\""))
    }

    @Test
    fun `a new card is pre-filled from the song`() {
        val fields = CardTextFields.fromItem(
            com.example.asmrplayer.core.model.LibraryItem(
                id = "1",
                title = "Rain on Glass",
                subtitle = "Someone",
                type = com.example.asmrplayer.core.model.LibraryItemType.Audio,
                paletteSeed = 1,
            ),
        )

        assertEquals("Rain on Glass", fields.title)
        assertEquals("Someone", fields.subtitle)
        assertEquals(CardTextFields.DefaultEdition, fields.edition)
        assertEquals(CardTextFields.DefaultCollection, fields.collection)
        assertTrue(fields.description.contains("Rain on Glass"))
    }

    @Test
    fun `trimming leaves an intentional single character alone`() {
        val fields = CardTextFields(title = "  A  ", subtitle = "   ").trimmed()

        assertEquals("A", fields.title)
        assertEquals("", fields.subtitle)
    }
}
