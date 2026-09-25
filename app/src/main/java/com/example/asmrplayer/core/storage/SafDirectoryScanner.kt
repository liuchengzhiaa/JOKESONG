package com.example.asmrplayer.core.storage

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import com.example.asmrplayer.core.model.NaturalNameComparator
import java.util.PriorityQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class DirectoryListing(
    val folderName: String,
    val items: List<LibraryItem>,
    val supportedItemCount: Int,
    val isTruncated: Boolean,
)

class SafDirectoryScanner(
    private val contentResolver: ContentResolver,
    private val retainedItemLimit: Int = 500,
    private val artworkProbeLimit: Int = 48,
) {
    init {
        require(retainedItemLimit > 0)
        require(artworkProbeLimit >= 0)
    }

    fun rootDocumentUri(treeUri: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        DocumentsContract.getTreeDocumentId(treeUri),
    )

    suspend fun scan(treeUri: Uri, folderUri: Uri): DirectoryListing = withContext(Dispatchers.IO) {
        val parentDocumentId = DocumentsContract.getDocumentId(folderUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val retainedItems = PriorityQueue(retainedItemLimit, DisplayComparator.reversed())
        var supportedItemCount = 0
        var currentFolderArtwork: ArtworkCandidate? = null

        queryChildren(childrenUri)?.use { cursor ->
            val documentIdColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                ensureActive()
                val documentId = cursor.getString(documentIdColumn) ?: continue
                val displayName = cursor.getString(displayNameColumn)?.takeIf(String::isNotBlank) ?: continue
                val mimeType = cursor.getString(mimeTypeColumn)
                val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                if (!isDirectory && SupportedArtwork.isSupported(displayName, mimeType)) {
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    currentFolderArtwork = earlierArtwork(
                        currentFolderArtwork,
                        ArtworkCandidate(displayName, documentUri.toString()),
                    )
                    continue
                }
                if (!isDirectory && !SupportedAudio.isSupported(displayName, mimeType)) continue

                supportedItemCount++
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                val item = LibraryItem(
                    id = documentUri.toString(),
                    title = if (isDirectory) displayName else displayName.substringBeforeLast('.', displayName),
                    subtitle = if (isDirectory) "" else SupportedAudio.extensionOf(displayName),
                    type = if (isDirectory) LibraryItemType.Folder else LibraryItemType.Audio,
                    paletteSeed = documentId.hashCode() and Int.MAX_VALUE,
                    documentUri = documentUri.toString(),
                )
                retainSmallest(retainedItems, item)
            }
        }

        val sortedItems = retainedItems.toList().sortedWith(DisplayComparator)
        val itemsWithArtwork = sortedItems.mapIndexed { index, item ->
            when {
                item.type == LibraryItemType.Audio -> item.copy(
                    artworkUri = currentFolderArtwork?.documentUri,
                )
                index < artworkProbeLimit -> {
                    val probe = item.documentUri?.let(Uri::parse)?.let { childFolderUri ->
                        queryDirectFolder(treeUri, childFolderUri)
                    }
                    item.copy(
                        artworkUri = probe?.artworkUri,
                        childFolderCount = probe?.childFolderCount ?: 0,
                        audioCount = probe?.audioCount ?: 0,
                    )
                }
                else -> item
            }
        }

        DirectoryListing(
            folderName = queryDisplayName(folderUri),
            items = itemsWithArtwork,
            supportedItemCount = supportedItemCount,
            isTruncated = supportedItemCount > retainedItemLimit,
        )
    }

    private fun retainSmallest(queue: PriorityQueue<LibraryItem>, item: LibraryItem) {
        if (queue.size < retainedItemLimit) {
            queue += item
        } else if (DisplayComparator.compare(item, queue.peek()) < 0) {
            queue.poll()
            queue += item
        }
    }

    private fun queryChildren(uri: Uri): Cursor? {
        val queryArguments = Bundle().apply {
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            )
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
        }
        return try {
            contentResolver.query(uri, ChildProjection, queryArguments, null)
        } catch (_: IllegalArgumentException) {
            contentResolver.query(uri, ChildProjection, null, null, null)
        } catch (_: UnsupportedOperationException) {
            contentResolver.query(uri, ChildProjection, null, null, null)
        }
    }

    private fun queryDisplayName(uri: Uri): String =
        contentResolver.query(uri, NameProjection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
        } ?: ""

    private suspend fun queryDirectFolder(treeUri: Uri, folderUri: Uri): FolderProbe {
        val documentId = DocumentsContract.getDocumentId(folderUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        var candidate: ArtworkCandidate? = null
        var childFolderCount = 0
        var audioCount = 0
        queryChildren(childrenUri)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val name = cursor.getString(nameColumn)?.takeIf(String::isNotBlank) ?: continue
                val mimeType = cursor.getString(mimeColumn)
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    childFolderCount++
                    continue
                }
                if (SupportedAudio.isSupported(name, mimeType)) {
                    audioCount++
                    continue
                }
                if (!SupportedArtwork.isSupported(name, mimeType)) continue
                val childId = cursor.getString(idColumn) ?: continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                candidate = earlierArtwork(candidate, ArtworkCandidate(name, uri.toString()))
            }
        }
        return FolderProbe(
            artworkUri = candidate?.documentUri,
            childFolderCount = childFolderCount,
            audioCount = audioCount,
        )
    }

    private fun earlierArtwork(
        current: ArtworkCandidate?,
        candidate: ArtworkCandidate,
    ): ArtworkCandidate = when {
        current == null -> candidate
        NaturalNameComparator.compare(candidate.displayName, current.displayName) < 0 -> candidate
        else -> current
    }

    private companion object {
        val ChildProjection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val NameProjection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

        data class ArtworkCandidate(
            val displayName: String,
            val documentUri: String,
        )

        data class FolderProbe(
            val artworkUri: String?,
            val childFolderCount: Int,
            val audioCount: Int,
        )

        val DisplayComparator = Comparator<LibraryItem> { left, right ->
            val typeComparison = when {
                left.type == right.type -> 0
                left.type == LibraryItemType.Folder -> -1
                else -> 1
            }
            typeComparison.takeIf { it != 0 }
                ?: NaturalNameComparator.compare(left.title, right.title).takeIf { it != 0 }
                ?: left.id.compareTo(right.id)
        }
    }
}
