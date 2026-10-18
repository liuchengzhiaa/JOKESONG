package com.example.asmrplayer.core.metadata

import org.junit.Assert.*
import org.junit.Test

class AudioCoverEditPolicyTest {
    @Test fun `cover-only save ignores all hidden tag fields`() {
        val draft = mapOf("TITLE" to "unsubmitted draft", "ARTIST" to "", "COMMENT" to "changed")
        assertTrue(metadataFieldsForSave(draft, coverOnly = true).isEmpty())
        assertEquals("unsubmitted draft", draft["TITLE"])
    }

    @Test fun `cover-only save preserves multi-value unknown and oversized untouched tags`() {
        val original = mapOf(
            "TITLE" to listOf("original title"), "ARTIST" to listOf("A", "B"),
            "CUSTOM" to listOf("unknown", "retained"), "COMMENT" to listOf("x".repeat(9000)),
        )
        val fields = metadataFieldsForSave(mapOf("TITLE" to "wrong", "COMMENT" to "short"), coverOnly = true)
        assertEquals(original, editedProperties(original, fields))
    }

    @Test fun `full editor retains existing explicit field edit behavior`() {
        val fields = mapOf("TITLE" to "new title")
        assertSame(fields, metadataFieldsForSave(fields, coverOnly = false))
        assertEquals(mapOf("TITLE" to listOf("new title")),
            editedProperties(mapOf("TITLE" to listOf("old")), metadataFieldsForSave(fields, coverOnly = false)))
    }
}
