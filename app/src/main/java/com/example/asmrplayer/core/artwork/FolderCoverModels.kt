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


/** Only for the known local ExternalStorageProvider's document IDs, not filesystem paths. */
internal fun isDirectLocalAudioChild(folderId: String, audioId: String): Boolean =
    audioId.substringBeforeLast('/', audioId.substringBefore(':') + ":") == folderId
