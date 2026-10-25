package com.example.asmrplayer.core.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

internal data class DirectFolderArtwork(val artworkUri: String?)

/** Read-only direct cover lookup. No child counts, audio metadata, image decode or recursion. */
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
    )
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(
        treeUri, DocumentsContract.getDocumentId(folderUri),
    )
    val selection = FolderArtworkSelection()
    onRow()
    val cursor = resolver.query(children, columns, null, null, null)
        ?: throw IOException("FOLDER_COVER_QUERY_FAILED")
    cursor.use {
        val id = it.getColumnIndexOrThrow(columns[0])
        val name = it.getColumnIndexOrThrow(columns[1])
        val mime = it.getColumnIndexOrThrow(columns[2])
        while (it.moveToNext()) {
            onRow()
            val label = it.getString(name)?.takeIf(String::isNotBlank) ?: continue
            if (!com.example.asmrplayer.core.artwork.isNamedFolderCover(label)) continue
            val type = it.getString(mime)
            if (type == DocumentsContract.Document.MIME_TYPE_DIR || !SupportedArtwork.isSupported(label, type)) continue
            val docId = it.getString(id) ?: continue
            selection.image(FolderArtworkEntry(label,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()))
        }
    }
    onRow()
    return DirectFolderArtwork(selection.image?.uri)
}
