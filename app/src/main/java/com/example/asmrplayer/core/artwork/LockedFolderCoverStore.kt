package com.example.asmrplayer.core.artwork

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/** Serialises folder writes only: audio extraction/cache has a separate lock. */
internal object FolderCoverFiles {
    private val lock = ReentrantLock()
    fun <T> locked(checkpoint: () -> Unit = {}, block: () -> T): T {
        while (!lock.tryLock(100, TimeUnit.MILLISECONDS)) checkpoint()
        try { checkpoint(); return block() } finally { lock.unlock() }
    }

}

/**
 * Explicit forwarding keeps every interface entry point in this concrete class.
 * Do not use interface delegation here: an incremental build shipped a stale
 * anonymous delegate without image(), causing AbstractMethodError at runtime.
 * Only recovery and installation take the folder-write lock; probing stays outside.
 */
internal class LockedFolderCoverStore(
    private val source: FolderCoverStore,
    private val checkpoint: () -> Unit = {},
) : FolderCoverStore {
    override fun existing(folder: String): String? =
        FolderCoverFiles.locked(checkpoint) { source.existing(folder) }
    override fun legacy(folder: String): String? = source.legacy(folder)
    override fun page(folder: String, kind: CoverChildKind, after: CoverDocument?, limit: Int): List<CoverDocument> =
        source.page(folder, kind, after, limit)
    override fun image(candidate: CoverDocument): CoverImageCandidate? = source.image(candidate)
    override fun embedded(audio: CoverDocument): String? = source.embedded(audio)
    override fun install(folder: String, source: String): String =
        FolderCoverFiles.locked(checkpoint) { this.source.install(folder, source) }
}
