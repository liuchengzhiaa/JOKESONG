package com.example.asmrplayer.core.metadata

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MetadataTransactionTest {
    private class Crash : Error()
    private class MemoryStore : TagTransactionStore {
        val files = mutableMapOf("test.mp3" to "original audio".toByteArray())
        var saved: EditJournal? = null
        var steps = 0
        var crashAt = -1
        var failRenameTo: String? = null
        var failDelete = false
        fun mutation() { if (++steps == crashAt) throw Crash() }
        override fun journal() = saved
        override fun record(value: EditJournal) { saved = value; mutation() }
        override fun clearJournal() { saved = null; mutation() }
        override fun exists(name: String) = files.containsKey(name)
        override fun read(name: String) = ByteArrayInputStream(files.getValue(name))
        override fun create(name: String) = object : ByteArrayOutputStream() {
            override fun close() { files[name] = toByteArray(); mutation() }
        }
        override fun rename(from: String, to: String) {
            if (to == failRenameTo) throw IllegalStateException("simulated rename error")
            check(!exists(to)); files[to] = files.remove(from)!!; mutation()
        }
        override fun deleteArtifact(name: String) {
            if (failDelete) error("simulated cleanup failure")
            files.remove(name); mutation()
        }
        override fun sync(name: String) { mutation() }
    }
    private fun edit(s: MemoryStore): Boolean = MetadataTransaction(s).execute(newEditJournal("test.mp3"), {
        s.files[it] = "edited tags + original audio".toByteArray()
    })
    @Test fun successfulSaveReplacesAndCleansUp() {
        val s = MemoryStore(); assertTrue(edit(s)); assertNull(s.saved)
        assertEquals(setOf("test.mp3"), s.files.keys)
        assertEquals("edited tags + original audio", s.files.getValue("test.mp3").toString(Charsets.UTF_8))
    }
    @Test fun recoveryAtEveryDurableMutationPreservesOriginalOrCommittedNew() {
        val baseline = MemoryStore(); edit(baseline)
        for (step in 1..baseline.steps) {
            val s = MemoryStore(); s.crashAt = step
            try { edit(s) } catch (_: Crash) { }
            s.crashAt = -1
            MetadataTransaction(s).recover()
            val result = s.files.getValue("test.mp3").toString(Charsets.UTF_8)
            assertTrue("step $step", result == "original audio" || result == "edited tags + original audio")
            assertNull(s.saved); assertEquals(setOf("test.mp3"), s.files.keys)
            MetadataTransaction(s).recover() // Idempotent after restart.
        }
    }
    @Test fun renameFailureRestoresBackup() {
        val s = MemoryStore(); s.failRenameTo = "test.mp3"
        try { edit(s); fail() } catch (_: IllegalStateException) { }
        assertNotNull(s.saved) // Restore also failed: preserve backup, do not discard journal.
        assertTrue(s.files.keys.any { it.contains("backup") })
        s.failRenameTo = null; MetadataTransaction(s).recover()
        assertEquals("original audio", s.files.getValue("test.mp3").toString(Charsets.UTF_8))
    }
    @Test fun validationFailureNeverRenamesSource() {
        val s = MemoryStore()
        try { MetadataTransaction(s).execute(newEditJournal("test.mp3"), { error("bad tags") }); fail() } catch (_: IllegalStateException) { }
        assertEquals(setOf("test.mp3"), s.files.keys); assertNull(s.saved)
    }
    @Test fun sourceChangedDuringPreparationIsNotOverwritten() {
        val s = MemoryStore()
        try { MetadataTransaction(s).execute(newEditJournal("test.mp3"), {
            s.files["test.mp3"] = "external edit".toByteArray()
        }); fail() } catch (_: IllegalStateException) { }
        assertEquals("external edit", s.files.getValue("test.mp3").toString(Charsets.UTF_8)); assertNull(s.saved)
    }
    @Test fun cleanupFailureMeansSavedWithRecoveryPending() {
        val s = MemoryStore(); s.failDelete = true
        assertFalse(edit(s)); assertEquals(EditPhase.Committed, s.saved!!.phase)
        s.failDelete = false; MetadataTransaction(s).recover(); assertNull(s.saved)
        assertEquals("edited tags + original audio", s.files.getValue("test.mp3").toString(Charsets.UTF_8))
    }
    @Test fun conflictingThirdPartyDocumentIsNeverOverwrittenDuringRecovery() {
        val s = MemoryStore(); val j = newEditJournal("test.mp3")
        val oldHash = streamDigest(s.read("test.mp3"))
        s.files[j.backupName] = s.files.getValue("test.mp3")
        s.files["test.mp3"] = "third party".toByteArray()
        s.saved = j.copy(originalHash = oldHash, stageHash = "expected", phase = EditPhase.Verifying)
        try { MetadataTransaction(s).recover(); fail() } catch (_: IllegalStateException) { }
        assertEquals("third party", s.files.getValue("test.mp3").toString(Charsets.UTF_8))
        assertTrue(s.exists(j.backupName)); assertNotNull(s.saved)
    }
    @Test fun recoveryCanItselfRestartAtEveryMutation() {
        fun pending(phase: EditPhase): MemoryStore = MemoryStore().also { s ->
            val j = newEditJournal("test.mp3")
            val original = s.files.getValue("test.mp3")
            val updated = "edited tags + original audio".toByteArray()
            s.files[j.backupName] = original
            s.files["test.mp3"] = updated
            s.saved = j.copy(phase = phase, originalHash = streamDigest(ByteArrayInputStream(original)),
                stageHash = streamDigest(ByteArrayInputStream(updated)))
        }
        for (phase in listOf(EditPhase.Verifying, EditPhase.Committed)) {
            val baseline = pending(phase); MetadataTransaction(baseline).recover()
            for (step in 1..baseline.steps) {
                val s = pending(phase); s.crashAt = step
                try { MetadataTransaction(s).recover() } catch (_: Crash) { }
                s.crashAt = -1
                MetadataTransaction(s).recover()
                MetadataTransaction(s).recover()
                assertEquals(setOf("test.mp3"), s.files.keys)
                assertArrayEquals(baseline.files.getValue("test.mp3"), s.files.getValue("test.mp3"))
                assertNull(s.saved)
            }
        }
    }
    @Test fun cancellationImmediatelyBeforeCommitDoesNotRenameOriginal() {
        val s = MemoryStore()
        try {
            MetadataTransaction(s).execute(newEditJournal("test.mp3"), { s.files[it] = "updated".toByteArray() },
                checkpoint = { if (s.saved?.phase == EditPhase.Validated) throw java.util.concurrent.CancellationException() })
            fail()
        } catch (_: java.util.concurrent.CancellationException) { }
        assertEquals(setOf("test.mp3"), s.files.keys)
        assertEquals("original audio", s.files.getValue("test.mp3").toString(Charsets.UTF_8))
        assertNull(s.saved)
    }
    @Test fun unchangedMultiValueAndUnknownTagsAreRetained() {
        val old = mapOf("ARTIST" to listOf("A", "B"), "CUSTOM" to listOf("unknown"))
        assertEquals(old, editedProperties(old, mapOf("ARTIST" to "A; B")))
        val changed = editedProperties(old, mapOf("TITLE" to "新标题"))
        assertEquals(old["ARTIST"], changed["ARTIST"]); assertEquals(old["CUSTOM"], changed["CUSTOM"])
    }
    @Test fun blankExplicitEditRemovesOnlyChosenField() {
        assertEquals(mapOf("ARTIST" to listOf("A")), editedProperties(mapOf("TITLE" to listOf("old"), "ARTIST" to listOf("A")), mapOf("TITLE" to "")))
    }
    @Test fun invalidOrOversizedFieldEditsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { editedProperties(emptyMap(), mapOf("CUSTOM" to "new")) }
        assertThrows(IllegalArgumentException::class.java) { editedProperties(emptyMap(), mapOf("TITLE" to "x".repeat(2049))) }
        assertThrows(IllegalArgumentException::class.java) { editedProperties(emptyMap(), mapOf("COMMENT" to "x".repeat(8193))) }
        assertThrows(IllegalArgumentException::class.java) { editedProperties(emptyMap(), mapOf("TITLE" to "x\u0000y")) }
    }
    @Test fun onlyStrictOwnedArtifactNamesAreHidden() {
        assertTrue(isMetadataArtifactName(newEditJournal("test.mp3").stageName))
        assertFalse(isMetadataArtifactName(".__asmr-my-song.mp3")); assertFalse(isMetadataArtifactName("other.mp3"))
        assertFalse(isMetadataArtifactName(".__asmr-${"-".repeat(36)}-stage.mp3"))
        assertFalse(isMetadataArtifactName(newEditJournal("test.mp3").stageName.replace(".mp3", ".txt")))
    }
}
