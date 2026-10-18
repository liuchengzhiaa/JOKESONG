package com.example.asmrplayer.core.artwork

import com.example.asmrplayer.core.model.NaturalNameComparator

internal data class CoverDocument(val uri: String, val name: String, val version: String = "")
internal enum class CoverChildKind { Audio, Folder }
internal val CoverDocumentOrder = Comparator<CoverDocument> { a, b ->
    NaturalNameComparator.compare(a.name, b.name).takeIf { it != 0 } ?: a.uri.compareTo(b.uri)
}
internal fun isNamedFolderCover(name: String): Boolean =
    name.substringBeforeLast('.', name).equals("cover", ignoreCase = true)
internal fun isFolderCoverArtifact(name: String): Boolean = name.startsWith(".jokesong-cover-")

/** Cursor paging is implemented with a bounded top-N heap, never the whole tree in memory. */
internal interface FolderCoverStore {
    fun existing(folder: String): String?
    fun legacy(folder: String): String? = null
    fun page(folder: String, kind: CoverChildKind, after: CoverDocument?, limit: Int): List<CoverDocument>
    fun embedded(audio: CoverDocument): String?
    /** Must recheck existing cover before installing; automatic mode must never replace it. */
    fun install(folder: String, source: String): String
}

internal class CoverLookupBudget(
    private val checkpoint: () -> Unit = {},
    private val maxFolders: Int = 256,
    private val maxAudio: Int = 256,
    private val maxRows: Int = 100_000,
) {
    private var folders = 0
    private var audio = 0
    private var rows = 0
    fun check() = checkpoint()
    fun folder() { check(); check(++folders <= maxFolders) { "COVER_FOLDER_BUDGET" } }
    fun audio() { check(); check(++audio <= maxAudio) { "COVER_AUDIO_BUDGET" } }
    fun row() { check(); check(++rows <= maxRows) { "COVER_ROW_BUDGET" } }
}

/** One displayed folder at a time: cover -> old explicit choice -> all direct audio -> DFS. */
internal class FolderCoverResolver(
    private val store: FolderCoverStore,
    private val budget: CoverLookupBudget,
    private val maxDepth: Int = 24,
) {
    private val visited = HashSet<String>()
    fun resolve(folder: String): String? = visit(folder, 0)
    private fun visit(folder: String, depth: Int): String? {
        budget.check()
        check(depth <= maxDepth) { "COVER_DEPTH_BUDGET" }
        if (!visited.add(folder)) return null
        budget.folder()
        store.existing(folder)?.let { return it }
        store.legacy(folder)?.let { return install(folder, it) }
        for (kind in CoverChildKind.entries) {
            var after: CoverDocument? = null
            while (true) {
                budget.check()
                val page = store.page(folder, kind, after, 32)
                check(page.size <= 32 && page.zipWithNext().all { (a, b) -> CoverDocumentOrder.compare(a, b) < 0 })
                check(after == null || page.all { CoverDocumentOrder.compare(it, after) > 0 })
                for (entry in page) {
                    val source = if (kind == CoverChildKind.Audio) {
                        budget.audio()
                        store.embedded(entry)
                    } else visit(entry.uri, depth + 1)
                    source?.let { return install(folder, it) }
                }
                if (page.size < 32) break
                after = page.last()
            }
        }
        return null
    }
    private fun install(folder: String, source: String): String {
        budget.check()
        // Read-only roots still display a truthful source. It is NOT reported as persisted.
        return try { store.install(folder, source) } catch (_: CoverWriteUnavailable) { source }
    }
}
internal class CoverWriteUnavailable(cause: Exception) : Exception(cause)
