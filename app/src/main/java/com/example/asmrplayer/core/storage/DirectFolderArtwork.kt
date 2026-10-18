package com.example.asmrplayer.core.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.example.asmrplayer.core.artwork.audioArtworkUri
import com.example.asmrplayer.core.metadata.isMetadataArtifactName

internal data class DirectFolderArtwork(
    val artworkUri: String?,
    val childFolderCount: Int,
    val audioCount: Int,
)

/** Only enumerates direct children. Never opens audio, decodes art or recurses while scanning. */
internal fun readDirectFolderArtwork(
    resolver: ContentResolver,
    treeUri: Uri,
    folderUri: Uri,
    onRow: () -> Unit = {},
): DirectFolderArtwork {
    val columns = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(
        treeUri, DocumentsContract.getDocumentId(folderUri),
    )
    val selection = FolderArtworkSelection()
    var folders = 0
    var audios = 0
    resolver.query(children, columns, null, null, null)?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(columns[0])
        val name = cursor.getColumnIndexOrThrow(columns[1])
        val mime = cursor.getColumnIndexOrThrow(columns[2])
        val size = cursor.getColumnIndex(columns[3])
        val modified = cursor.getColumnIndex(columns[4])
        while (cursor.moveToNext()) {
            onRow()
            val label = cursor.getString(name)?.takeIf(String::isNotBlank) ?: continue
            if (isMetadataArtifactName(label)) continue
            val type = cursor.getString(mime)
            if (type == DocumentsContract.Document.MIME_TYPE_DIR) { folders++; continue }
            val image = SupportedArtwork.isSupported(label, type)
            val audio = !image && SupportedAudio.isSupported(label, type)
            if (audio) audios++
            if (!audio && !image) continue
            val docId = cursor.getString(id) ?: continue
            val entry = FolderArtworkEntry(
                label,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString(),
                "${if (size >= 0) cursor.getLong(size) else 0L}:${if (modified >= 0) cursor.getLong(modified) else 0L}",
            )
            if (audio) selection.audio(entry) else selection.image(entry)
        }
    }
    return DirectFolderArtwork(
        artworkUri = selection.image?.uri ?: selection.firstAudio?.let {
            audioArtworkUri(audio = it.uri, fallback = null, version = it.version)
        },
        childFolderCount = folders,
        audioCount = audios,
    )
}
