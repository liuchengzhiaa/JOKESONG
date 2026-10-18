package com.example.asmrplayer.core.artwork

import org.junit.Assert.*
import org.junit.Test

class CoverInstallTransactionTest {
    private val j = CoverInstallJournal(".jokesong-cover-id-stage.png", "cover.png", "cover.jpg", ".jokesong-cover-id-backup.jpg", "new")
    private class Store : CoverInstallStore {
        val files = mutableMapOf<String, String>()
        var journal: CoverInstallJournal? = null
        var failAt = 0
        var crashAt = 0
        var count = 0
        override fun exists(name: String) = files.containsKey(name)
        override fun hash(name: String) = files[name] ?: error("missing")
        override fun rename(from: String, to: String) {
            check(!exists(to)); ++count
            if (count == failAt) error("simulated provider failure")
            files[to] = files.remove(from) ?: error("missing")
            if (count == crashAt) throw AssertionError("simulated process death")
        }
        override fun record(journal: CoverInstallJournal) { this.journal = journal }
        override fun pending() = journal
        override fun clear() { journal = null }
    }
    private fun store() = Store().apply { files[j.stage] = "new"; files[j.old!!] = "old" }
    @Test fun `commit retains byte identical original as backup`() {
        val s = store(); CoverInstallTransaction(s).commit(j)
        assertEquals("new", s.files[j.target]); assertEquals("old", s.files[j.backup]); assertNull(s.journal)
    }
    @Test fun `first rename failure leaves original intact`() {
        val s = store(); s.failAt = 1
        try { CoverInstallTransaction(s).commit(j); fail() } catch (_: IllegalStateException) { }
        assertEquals("old", s.files[j.old]); assertNull(s.journal)
    }
    @Test fun `second rename failure rolls old cover back`() {
        val s = store(); s.failAt = 2
        try { CoverInstallTransaction(s).commit(j); fail() } catch (_: IllegalStateException) { }
        assertEquals("old", s.files[j.old]); assertFalse(s.exists(j.target)); assertNull(s.journal)
    }
    @Test fun `interruption after old move restores cover on next visit`() {
        val s = store(); s.crashAt = 1
        try { CoverInstallTransaction(s).commit(j); fail() } catch (_: AssertionError) { }
        assertNotNull(s.journal); s.crashAt = 0; CoverInstallTransaction(s).recover()
        assertEquals("old", s.files[j.old]); assertNull(s.journal)
    }
    @Test fun `interruption after promotion finalises verified new image`() {
        val s = store(); s.crashAt = 2
        try { CoverInstallTransaction(s).commit(j); fail() } catch (_: AssertionError) { }
        s.crashAt = 0; CoverInstallTransaction(s).recover()
        assertEquals("new", s.files[j.target]); assertEquals("old", s.files[j.backup]); assertNull(s.journal)
    }
    @Test fun `same extension replacement also recovers`() {
        val job = j.copy(old = j.target); val s = Store().apply { files[j.stage] = "new"; files[j.target] = "old"; crashAt = 1 }
        try { CoverInstallTransaction(s).commit(job); fail() } catch (_: AssertionError) { }
        s.crashAt = 0; CoverInstallTransaction(s).recover(); assertEquals("old", s.files[j.target])
    }
    @Test fun `automatic creation never replaces conflicting target`() {
        val s = store(); s.files[j.target] = "someone else"
        try { CoverInstallTransaction(s).commit(j.copy(old = null)); fail() } catch (_: IllegalStateException) { }
        assertEquals("someone else", s.files[j.target]); assertEquals("old", s.files[j.old]); assertNull(s.journal)
    }
    @Test fun `changed stage refused before touching original`() {
        val s = store(); s.files[j.stage] = "corrupt"
        try { CoverInstallTransaction(s).commit(j); fail() } catch (_: IllegalStateException) { }
        assertEquals("old", s.files[j.old]); assertNull(s.journal)
    }
    @Test fun `recovery conflict preserves both copies and journal`() {
        val s = store(); s.journal = j; s.files[j.backup] = "old"
        try { CoverInstallTransaction(s).recover(); fail() } catch (_: IllegalStateException) { }
        assertEquals("old", s.files[j.old]); assertEquals("old", s.files[j.backup]); assertNotNull(s.journal)
    }
    @Test fun `successful automatic creation needs no original or backup`() {
        val s = Store().apply { files[j.stage] = "new" }; CoverInstallTransaction(s).commit(j.copy(old = null))
        assertEquals(mapOf(j.target to "new"), s.files)
    }
    @Test fun `corrupt promoted target keeps journal and backup for explicit recovery`() {
        val s = store(); s.files.remove(j.stage); s.files[j.target] = "corrupt"; s.files[j.backup] = "old"; s.journal = j
        try { CoverInstallTransaction(s).recover(); fail() } catch (_: IllegalStateException) { }
        assertEquals("old", s.files[j.backup]); assertNotNull(s.journal)
    }
    @Test fun `pre move interruption does not hash or change old target`() {
        val s = Store().apply { files[j.stage] = "new"; files[j.target] = "original"; journal = j.copy(old = j.target) }
        CoverInstallTransaction(s).recover()
        assertEquals("original", s.files[j.target]); assertEquals("new", s.files[j.stage]); assertNull(s.journal)
    }
}
