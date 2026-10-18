package com.example.asmrplayer.core.artwork

internal data class CoverInstallJournal(
    val stage: String, val target: String, val old: String?, val backup: String, val hash: String,
)
internal interface CoverInstallStore {
    fun exists(name: String): Boolean
    fun hash(name: String): String
    fun rename(from: String, to: String)
    fun record(journal: CoverInstallJournal)
    fun pending(): CoverInstallJournal?
    fun clear()
}

/** Recoverable two-renames, NOT an atomic SAF replace. Never delete an old cover/backup. */
internal class CoverInstallTransaction(private val store: CoverInstallStore) {
    fun recover() {
        val j = store.pending() ?: return
        if (!store.exists(j.stage) && store.exists(j.target)) {
            check(store.hash(j.target) == j.hash) { "COVER_RECOVERY_VERIFY_FAILED" }
            store.clear() // New image already committed; keep old backup.
            return
        }
        check(!store.exists(j.target) || j.target == j.old) { "COVER_RECOVERY_CONFLICT" }
        if (j.old != null) {
            if (store.exists(j.backup)) {
                check(!store.exists(j.old)) { "COVER_RECOVERY_CONFLICT" }
                store.rename(j.backup, j.old)
            } else check(store.exists(j.old)) { "COVER_RECOVERY_REQUIRED" }
        }
        store.clear() // Pre-commit interruption/rollback. Unused stage is hidden, never artwork.
    }
    fun commit(j: CoverInstallJournal) {
        check(store.pending() == null) { "COVER_RECOVERY_REQUIRED" }
        check(isFolderCoverArtifact(j.stage) && isFolderCoverArtifact(j.backup))
        check(isNamedFolderCover(j.target) && (j.old == null || isNamedFolderCover(j.old)))
        check(store.hash(j.stage) == j.hash) { "COVER_STAGE_CHANGED" }
        check(j.old == null || store.exists(j.old)) { "COVER_ORIGINAL_CHANGED" }
        check(!store.exists(j.backup) && (j.target == j.old || !store.exists(j.target))) { "COVER_NAME_CONFLICT" }
        store.record(j)
        try {
            j.old?.let { store.rename(it, j.backup) }
            store.rename(j.stage, j.target)
            check(store.hash(j.target) == j.hash) { "COVER_VERIFY_FAILED" }
            store.clear()
        } catch (failure: Exception) {
            // If rollback is unsafe leave the journal, new stage and old backup for recovery.
            runCatching { recover() }
            throw failure
        }
    }
}
