package com.example.asmrplayer.core.artwork

internal data class CoverDirectoryRow(val document: CoverDocument, val kind: Int) {
    companion object { const val Audio = 0; const val Folder = 1; const val Cover = 2 }
}

/** One folder in memory at a time; sorted results are spooled to the run's private disk index. */
internal class CoverDirectoryRows(private val maxBytes: Int = 16 * 1024 * 1024, private val maxEntries: Int = 50_000) {
    private val rows = ArrayList<CoverDirectoryRow>()
    private var bytes = 0L
    fun add(document: CoverDocument, kind: Int) {
        bytes += 192L + 2L * (document.uri.length + document.name.length + document.version.length)
        check(rows.size < maxEntries && bytes <= maxBytes) { "COVER_DIRECTORY_INDEX_LIMIT" }
        rows += CoverDirectoryRow(document, kind)
    }
    fun sorted(): List<CoverDirectoryRow> {
        rows.sortWith { a, b -> CoverDocumentOrder.compare(a.document, b.document) }
        return rows
    }
}
