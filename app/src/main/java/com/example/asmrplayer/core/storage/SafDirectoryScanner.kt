package com.example.asmrplayer.core.storage

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import com.example.asmrplayer.core.artwork.audioArtworkUri
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import com.example.asmrplayer.core.model.NaturalNameComparator
import com.example.asmrplayer.core.subtitles.SupportedTimedText
import com.example.asmrplayer.core.subtitles.TimedTextCandidate
import com.example.asmrplayer.core.subtitles.TimedTextMatch
import com.example.asmrplayer.core.subtitles.matchTimedText
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
    /**
     * The lyrics and subtitles found in this folder, keyed by the [LibraryItem.id] of
     * the audio each one belongs to -- the id rather than the title because the player
     * holds the item it is playing and has to look the lyrics up with it.
     *
     * Empty when the folder has none, which is the ordinary case rather than a failure.
     */
    val timedText: Map<String, TimedTextMatch> = emptyMap(),
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
        val trackArtworkCandidates = mutableListOf<ArtworkCandidate>()
        val artworkVersions = mutableMapOf<String, String>()
        val timedTextCandidates = mutableListOf<TimedTextCandidate>()

        queryChildren(childrenUri)?.use { cursor ->
            val documentIdColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                ensureActive()
                val documentId = cursor.getString(documentIdColumn) ?: continue
                val displayName = cursor.getString(displayNameColumn)?.takeIf(String::isNotBlank) ?: continue
                if (com.example.asmrplayer.core.metadata.isMetadataArtifactName(displayName)) continue
                val mimeType = cursor.getString(mimeTypeColumn)
                val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                if (!isDirectory && SupportedArtwork.isSupported(displayName, mimeType)) {
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    if (trackArtworkCandidates.size < retainedItemLimit) trackArtworkCandidates += ArtworkCandidate(displayName, documentUri.toString())
                    currentFolderArtwork = earlierArtwork(
                        currentFolderArtwork,
                        ArtworkCandidate(displayName, documentUri.toString()),
                    )
                    continue
                }
                val timedTextFormat = if (isDirectory) null else SupportedTimedText.formatOf(displayName)
                if (timedTextFormat != null) {
                    // Recognised before the audio test so a lyric file can never be
                    // listed as a track, and bounded to the same count as the display
                    // items because the children arrive in display order: a folder of
                    // 500 tracks each with its own lyric file must not retain more
                    // than the folder it belongs to does.
                    if (timedTextCandidates.size < retainedItemLimit) {
                        timedTextCandidates += TimedTextCandidate(
                            displayName = displayName,
                            documentUri = DocumentsContract
                                .buildDocumentUriUsingTree(treeUri, documentId)
                                .toString(),
                            format = timedTextFormat,
                            // A provider is allowed to omit either column, so an absent
                            // value is 0 and simply makes the cache key coarser.
                            sizeBytes = sizeColumn.takeIf { it >= 0 }
                                ?.let { cursor.getLong(it) } ?: 0L,
                            modifiedAtMs = modifiedColumn.takeIf { it >= 0 }
                                ?.let { cursor.getLong(it) } ?: 0L,
                        )
                    }
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
                val evicted = retainedItems.peek()?.takeIf {
                    retainedItems.size == retainedItemLimit && DisplayComparator.compare(item, it) < 0
                }
                val retained = retainSmallest(retainedItems, item)
                evicted?.let { artworkVersions.remove(it.id) }
                if (retained && !isDirectory) {
                    artworkVersions[item.id] = "${sizeColumn.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L}:${modifiedColumn.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L}"
                }
            }
        }

        val sortedItems = retainedItems.toList().sortedWith(DisplayComparator)
        val itemsWithArtwork = sortedItems.mapIndexed { index, item ->
            when {
                item.type == LibraryItemType.Audio -> item.copy(
                    artworkUri = audioArtworkUri(
                        audio = item.documentUri!!,
                        fallback = trackArtworkCandidates.firstOrNull {
                            it.displayName.substringBeforeLast('.').equals(item.title, ignoreCase = true)
                        }?.documentUri ?: currentFolderArtwork?.documentUri,
                        version = artworkVersions[item.id].orEmpty(),
                    ),
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
            // Matched against the retained items rather than every child, because an
            // item that is not listed cannot be played and so cannot show lyrics. The
            // handle is the item id, which is what the player holds later.
            timedText = matchTimedText(
                audioTracks = itemsWithArtwork
                    .filter { it.type == LibraryItemType.Audio }
                    .associate { it.id to it.title },
                candidates = timedTextCandidates,
            ),
        )
    }

    private fun retainSmallest(queue: PriorityQueue<LibraryItem>, item: LibraryItem): Boolean {
        if (queue.size < retainedItemLimit) {
            queue += item
            return true
        } else if (DisplayComparator.compare(item, queue.peek()) < 0) {
            queue.poll()
            queue += item
            return true
        }
        return false
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
