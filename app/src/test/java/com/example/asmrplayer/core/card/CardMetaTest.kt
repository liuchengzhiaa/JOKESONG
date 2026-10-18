package com.example.asmrplayer.core.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card's own bookkeeping, and how it behaves when it is not what was written.
 *
 * `meta.json` is the file a future version will add keys to, so the case that matters
 * most here is the one where the text is not exactly what [CardMeta.toJson] produces.
 * A card that loses its pin because a build it predates wrote a shorter file is a card
 * the owner has to find and re-pin; a card that keeps its place with the pin missing is
 * a card that just is not pinned.
 */
class CardMetaTest {

    @Test
    fun `what is written is read back unchanged`() {
        val meta = CardMeta(
            mediaId = "content://com.android.providers.media.documents/document/audio%3A42",
            pinned = true,
            source = CardSource.Local,
            createdAtMillis = 1_767_225_600_000L,
        )

        assertEquals(meta, CardMeta.parse(meta.toJson()))
    }

    @Test
    fun `a card with no song keeps a null rather than an empty id`() {
        val meta = CardMeta(mediaId = null, createdAtMillis = 7L)

        assertTrue(meta.toJson().contains("\"mediaId\": null"))
        assertNull(CardMeta.parse(meta.toJson()).mediaId)
    }

    @Test
    fun `an id full of JSON metacharacters survives the round trip`() {
        // A document URI is percent-encoded rather than quoted, but a provider is free
        // to hand back anything, and the file has to hold whatever it is either way.
        val meta = CardMeta(mediaId = "a\"b\\c\nd\te", createdAtMillis = 1L)

        assertEquals(meta.mediaId, CardMeta.parse(meta.toJson()).mediaId)
    }

    @Test
    fun `an empty file is a card with no bookkeeping rather than a failure`() {
        assertEquals(CardMeta(), CardMeta.parse(""))
    }

    @Test
    fun `a file cut off part way keeps the keys that were written`() {
        // The shape a write interrupted by the process dying actually leaves: the head
        // of the document is intact and the tail is not.
        val truncated = """
            {
              "mediaId": "content://x/1",
              "pinned": tr
        """.trimIndent()

        val parsed = CardMeta.parse(truncated)

        assertEquals("content://x/1", parsed.mediaId)
        assertEquals(false, parsed.pinned)
    }

    @Test
    fun `an unknown source falls back to local instead of dropping the card`() {
        // What an older build does with a card written by a newer one that has learned
        // to import: the provenance it does not recognise is ignored, not fatal.
        val parsed = CardMeta.parse(
            """
            {
              "source": "SomeFutureOrigin",
              "pinned": true
            }
            """.trimIndent(),
        )

        assertEquals(CardSource.Local, parsed.source)
        assertTrue(parsed.pinned)
    }

    @Test
    fun `a key this build has never heard of is ignored`() {
        val parsed = CardMeta.parse(
            """
            {
              "mediaId": "id",
              "pinned": true,
              "palette": "#ff0000",
              "createdAtMillis": 12
            }
            """.trimIndent(),
        )

        assertEquals("id", parsed.mediaId)
        assertEquals(12L, parsed.createdAtMillis)
    }

    @Test
    fun `a newline inside the id does not end the line early`() {
        // The writer escapes it, so the reader has to meet it escaped and give it back.
        val parsed = CardMeta.parse(CardMeta(mediaId = "one\ntwo", createdAtMillis = 3L).toJson())

        assertEquals("one\ntwo", parsed.mediaId)
        assertEquals(3L, parsed.createdAtMillis)
    }

    @Test
    fun `pinning never disturbs anything else about the card`() {
        val meta = CardMeta(mediaId = "id", pinned = false, createdAtMillis = 5L)

        assertEquals(meta.copy(pinned = true), meta.withPinned(true))
        assertEquals(meta, meta.withPinned(false))
    }

    @Test
    fun `a millisecond timestamp survives as a number rather than a string`() {
        val meta = CardMeta(createdAtMillis = 1_767_225_600_123L)

        assertTrue(meta.toJson().contains("\"createdAtMillis\": 1767225600123"))
        assertEquals(1_767_225_600_123L, CardMeta.parse(meta.toJson()).createdAtMillis)
    }
}
