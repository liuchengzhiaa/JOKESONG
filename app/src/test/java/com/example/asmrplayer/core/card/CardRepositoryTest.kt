package com.example.asmrplayer.core.card

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The two halves of the repository that can be tested off a device.
 *
 * Everything else in [CardRepository] needs a `Context` for `filesDir`, which is why the
 * parts that guard storage -- the segment check every id passes through, and the readers
 * the warehouse uses to label a card -- are static and tested directly here. The segment
 * check is the one that matters most: it is the only thing standing between a card id and
 * a path that leaves the cards root.
 */
class CardRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a card id may not be a path`() {
        // Each of these would resolve somewhere other than a card if it were joined.
        listOf(
            "..",
            ".",
            "",
            "a/b",
            "a\\b",
            "../cards",
            "cards/../cards",
            "\u0000",
            "a\u0000b",
        ).forEach { name ->
            assertFalse("\"$name\" must not be usable as a card id", CardRepository.isSafeSegment(name))
        }
    }

    @Test
    fun `an id this app generated is always usable as a path segment`() {
        val ids = List(256) { CardRepository.newCardId() }

        ids.forEach { id ->
            assertTrue("generated id \"$id\" is not a safe segment", CardRepository.isSafeSegment(id))
        }
        // Two cards struck in the same millisecond are still two cards: the id carries a
        // sortable timestamp, not a key, so it has to be unique on its own.
        assertEquals("generated ids collided", ids.size, ids.toSet().size)
    }

    @Test
    fun `the title is read back out of a generated config`() {
        val config = write("card-config.json", CardConfig(CardTextFields(title = "潮汐")).toJson())

        assertEquals("潮汐", CardRepository.readTitle(config))
    }

    @Test
    fun `a title carrying an escape comes back unescaped`() {
        val config = write(
            "card-config.json",
            CardConfig(CardTextFields(title = "a\"b\\c\nd")).toJson(),
        )

        assertEquals("a\"b\\c\nd", CardRepository.readTitle(config))
    }

    @Test
    fun `a missing file is a card with no title rather than an error`() {
        assertNull(CardRepository.readTitle(File(folder.root, "absent.json")))
    }

    @Test
    fun `a key that was never written reads as absent`() {
        val config = write("card-config.json", CardConfig(CardTextFields(title = "x")).toJson())

        assertNull(CardRepository.readValue(config, "palette"))
    }

    @Test
    fun `a missing meta file is a card with no pin and no song`() {
        assertEquals(CardMeta(), CardRepository.readMeta(File(folder.root, "absent.json")))
    }

    @Test
    fun `a meta file that is not JSON is a card rather than an exception`() {
        val meta = write("meta.json", "not json at all")

        assertEquals(CardMeta(), CardRepository.readMeta(meta))
    }

    private fun write(name: String, text: String): File =
        folder.newFile(name).apply { writeText(text) }
}
