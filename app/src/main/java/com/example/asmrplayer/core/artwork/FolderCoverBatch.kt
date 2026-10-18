package com.example.asmrplayer.core.artwork

import java.util.concurrent.CancellationException

/** Separate from browsing. Counts contain no filenames or media contents. */
data class FolderCoverBatchCounts(
    val visited: Int = 0, val created: Int = 0, val existing: Int = 0,
    val missing: Int = 0, val failed: Int = 0,
)
enum class FolderCoverBatchStatus { Idle, Running, Completed, Cancelled, Failed }
data class FolderCoverBatchState(
    val status: FolderCoverBatchStatus = FolderCoverBatchStatus.Idle,
    val counts: FolderCoverBatchCounts = FolderCoverBatchCounts(),
)

/** Visits EVERY child even when its parent already has a cover. Bounded pages, no image list. */
internal class FolderCoverBatch(
    private val store: FolderCoverStore,
    private val checkpoint: () -> Unit,
    private val onProgress: (FolderCoverBatchCounts) -> Unit = {},
    private val maxFolders: Int = 50_000,
    private val maxDepth: Int = 128,
) {
    private val visited = HashSet<String>()
    private var counts = FolderCoverBatchCounts()
    fun run(root: String): FolderCoverBatchCounts {
        visit(root, 0)
        onProgress(counts)
        return counts
    }
    private fun visit(folder: String, depth: Int): String? {
        checkpoint()
        if (folder in visited) return null
        check(visited.size < maxFolders && depth <= maxDepth) { "COVER_BATCH_LIMIT" }
        visited += folder
        counts = counts.copy(visited = counts.visited + 1)
        onProgress(counts)
        var failed = false
        fun <T> attempt(block: () -> T): T? = try { checkpoint(); block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true; null }
        var cover = attempt { store.existing(folder) }
        if (cover != null) counts = counts.copy(existing = counts.existing + 1)
        if (cover == null && !failed) {
            val source = attempt {
                store.legacy(folder) ?: findAudioCover(folder)
            }
            if (source != null) {
                cover = attempt { store.install(folder, source) }
                if (cover != null) counts = counts.copy(created = counts.created + 1)
            }
        }
        var firstChildCover: String? = null
        var childErrors = false
        var after: CoverDocument? = null
        while (true) {
            checkpoint()
            val page = attempt { checkedPage(folder, CoverChildKind.Folder, after) } ?: break
            for (child in page) {
                val previousErrors = counts.failed
                val childCover = visit(child.uri, depth + 1)
                if (counts.failed > previousErrors) childErrors = true
                if (firstChildCover == null) firstChildCover = childCover
            }
            if (page.size < 32) break
            after = page.last()
        }
        if (cover == null && !failed && firstChildCover != null) {
            cover = attempt { store.install(folder, firstChildCover) }
            if (cover != null) counts = counts.copy(created = counts.created + 1)
        }
        if (cover == null && childErrors) failed = true
        if (failed) counts = counts.copy(failed = counts.failed + 1)
        else if (cover == null) counts = counts.copy(missing = counts.missing + 1)
        onProgress(counts)
        return cover
    }
    private fun findAudioCover(folder: String): String? {
        var after: CoverDocument? = null
        while (true) {
            checkpoint()
            val page = checkedPage(folder, CoverChildKind.Audio, after)
            for (audio in page) {
                checkpoint()
                store.embedded(audio)?.let { return it }
            }
            if (page.size < 32) return null
            after = page.last()
        }
    }
    private fun checkedPage(folder: String, kind: CoverChildKind, after: CoverDocument?): List<CoverDocument> {
        val page = store.page(folder, kind, after, 32)
        check(page.size <= 32 && page.zipWithNext().all { (a, b) -> CoverDocumentOrder.compare(a, b) < 0 })
        check(after == null || page.all { CoverDocumentOrder.compare(it, after) > 0 })
        return page
    }
}
