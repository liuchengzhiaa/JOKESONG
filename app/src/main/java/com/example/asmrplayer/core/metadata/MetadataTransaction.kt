package com.example.asmrplayer.core.metadata

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

enum class EditPhase { Preparing, Validated, BackingUp, Installing, Verifying, Committed, Recovering }
data class EditJournal(val originalName: String, val stageName: String, val backupName: String,
    val originalHash: String = "", val stageHash: String = "", val phase: EditPhase = EditPhase.Preparing)

/** Every method is relative to one verified SAF parent; no arbitrary path operations. */
internal interface TagTransactionStore {
    fun journal(): EditJournal?
    fun record(value: EditJournal)
    fun clearJournal()
    fun exists(name: String): Boolean
    fun read(name: String): InputStream
    fun create(name: String): OutputStream
    fun rename(from: String, to: String)
    fun deleteArtifact(name: String)
    fun sync(name: String)
}

internal fun streamDigest(input: InputStream, checkpoint: () -> Unit = {}): String = input.use {
    val md = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(128 * 1024)
    while (true) { checkpoint(); val n = it.read(buffer); if (n < 0) break; check(n > 0); md.update(buffer, 0, n) }
    md.digest().hex()
}

/** Journaled, recoverable two-rename commit; intentionally NOT an atomic swap. */
internal class MetadataTransaction(private val store: TagTransactionStore) {
    fun execute(plan: EditJournal, rewriteAndValidate: (String) -> Unit,
        checkpoint: () -> Unit = {}, progress: (EditPhase) -> Unit = {}): Boolean {
        check(store.journal() == null) { "RECOVERY_REQUIRED" }
        require(plan.stageName.startsWith(".__asmr-") && plan.backupName.startsWith(".__asmr-"))
        check(!store.exists(plan.stageName) && !store.exists(plan.backupName))
        var current = plan
        fun record(phase: EditPhase) { current = current.copy(phase = phase); store.record(current); progress(phase) }
        record(EditPhase.Preparing)
        try {
            val originalDigest = MessageDigest.getInstance("SHA-256")
            store.read(plan.originalName).use { input -> store.create(plan.stageName).use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    checkpoint(); val n = input.read(buffer); if (n < 0) break; check(n > 0)
                    output.write(buffer, 0, n); originalDigest.update(buffer, 0, n)
                }
            } }
            current = current.copy(originalHash = originalDigest.digest().hex()); store.record(current)
            rewriteAndValidate(plan.stageName)
            store.sync(plan.stageName)
            current = current.copy(stageHash = streamDigest(store.read(plan.stageName), checkpoint))
            check(streamDigest(store.read(plan.originalName), checkpoint) == current.originalHash) { "SOURCE_CHANGED" }
            record(EditPhase.Validated)
            checkpoint() // Last cancellation point: never interrupt the source-name mutation sequence.
            record(EditPhase.BackingUp)
            store.rename(plan.originalName, plan.backupName)
            record(EditPhase.Installing)
            store.rename(plan.stageName, plan.originalName)
            record(EditPhase.Verifying)
            check(streamDigest(store.read(plan.originalName)) == current.stageHash) { "FINAL_VERIFY_FAILED" }
            record(EditPhase.Committed)
        } catch (error: Exception) {
            // Unexpected provider state is kept for explicit recovery, never overwritten blindly.
            runCatching { recover() }
            throw error
        }
        // A cleanup failure is a successful edit WITH a retained journal/backup, not a failed save.
        return runCatching { recover(); true }.getOrDefault(false)
    }

    fun recover() {
        val j = store.journal() ?: return
        fun hash(name: String) = if (store.exists(name)) streamDigest(store.read(name)) else null
        val original = hash(j.originalName)
        val backup = hash(j.backupName)
        if (j.phase == EditPhase.Committed) {
            check(original == j.stageHash && original.isNotEmpty()) { "RECOVERY_CONFLICT" }
            if (backup != null) { check(backup == j.originalHash); store.deleteArtifact(j.backupName) }
        } else if (backup != null) {
            check(backup == j.originalHash && backup.isNotEmpty()) { "RECOVERY_CONFLICT" }
            store.record(j.copy(phase = EditPhase.Recovering))
            if (original != null) {
                if (original == j.originalHash) { store.deleteArtifact(j.backupName) }
                else {
                    check(original == j.stageHash && !store.exists(j.stageName)) { "RECOVERY_CONFLICT" }
                    store.rename(j.originalName, j.stageName)
                    store.rename(j.backupName, j.originalName)
                }
            } else store.rename(j.backupName, j.originalName)
            check(hash(j.originalName) == j.originalHash)
        } else {
            // Preparing can have an empty digest if killed halfway through the copy.
            val untouched = j.phase in setOf(EditPhase.Preparing, EditPhase.Validated, EditPhase.BackingUp)
            check(original != null && (untouched || j.originalHash.isEmpty() || original == j.originalHash)) { "RECOVERY_CONFLICT" }
        }
        if (store.exists(j.stageName)) store.deleteArtifact(j.stageName)
        store.clearJournal()
    }
}
