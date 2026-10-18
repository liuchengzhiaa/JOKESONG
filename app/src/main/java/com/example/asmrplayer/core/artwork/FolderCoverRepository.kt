package com.example.asmrplayer.core.artwork

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.example.asmrplayer.core.model.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Folder identity is on disk. Room/private files are read only as legacy migration inputs. */
internal class FolderCoverRepository(context: Context) {
    private val context = context.applicationContext
    private val revision = java.util.concurrent.atomic.AtomicLong(System.nanoTime())

    suspend fun project(items: List<LibraryItem>): List<LibraryItem> = withContext(Dispatchers.IO) {
        projectFolderCovers(items) { folderUri, previous ->
            val folder = folderUri.toUri()
            val tree = DocumentsContract.buildTreeDocumentUri(folder.authority!!,
                DocumentsContract.getTreeDocumentId(folder))
            val version = previous?.toUri()?.getQueryParameter("version").orEmpty()
            folderArtworkUri(tree.toString(), folderUri, "$version:${revision.get()}")
        }
    }

    suspend fun set(folderUri: String, image: Uri?) = withContext(Dispatchers.IO) {
        require(image != null && image.scheme == "content") { "COVER_CONTENT_IMAGE_REQUIRED" }
        val folder = folderUri.toUri()
        val tree = DocumentsContract.buildTreeDocumentUri(folder.authority!!,
            DocumentsContract.getTreeDocumentId(folder))
        val coroutine = currentCoroutineContext()
        val checkpoint = { coroutine.ensureActive() }
        FolderCoverFiles.locked(checkpoint) {
            SafFolderCoverStore(context, tree, CoverLookupBudget(checkpoint))
                .save(folderUri, image, replace = true)
            revision.incrementAndGet()
        }
    }
    suspend fun setFromAudio(folderUri: String, audioUri: String): Boolean = withContext(Dispatchers.IO) {
        val folder = folderUri.toUri()
        val tree = DocumentsContract.buildTreeDocumentUri(folder.authority!!, DocumentsContract.getTreeDocumentId(folder))
        val coroutine = currentCoroutineContext()
        val checkpoint = { coroutine.ensureActive() }
        FolderCoverFiles.locked(checkpoint) {
            val store = SafFolderCoverStore(context, tree, CoverLookupBudget(checkpoint))
            // Verify the selected track belongs to the captured directory, not a later navigation target.
            require(folder.authority == "com.android.externalstorage.documents")
            val audio = store.audioDocument(audioUri)
                .let { it.copy(version = "${it.version}:${System.nanoTime()}") }
            require(isDirectLocalAudioChild(DocumentsContract.getDocumentId(folder),
                DocumentsContract.getDocumentId(audioUri.toUri())))
            require(DocumentsContract.isChildDocument(context.contentResolver, folder, audioUri.toUri()))
            val image = store.embedded(audio) ?: return@locked false
            store.save(folderUri, image.toUri(), replace = true)
            revision.incrementAndGet()
            true
        }
    }
    suspend fun processTree(tree: Uri, onProgress: (FolderCoverBatchCounts) -> Unit): FolderCoverBatchCounts =
        withContext(Dispatchers.IO) {
            val coroutine = currentCoroutineContext()
            val checkpoint = { coroutine.ensureActive() }
            val budget = CoverLookupBudget(checkpoint, maxFolders = 50_000, maxAudio = 250_000, maxRows = 100_000_000)
            val raw = SafFolderCoverStore(context, tree, budget)
            val store = object : FolderCoverStore by raw {
                override fun existing(folder: String): String? = FolderCoverFiles.locked(checkpoint) { raw.existing(folder) }
                override fun install(folder: String, source: String): String = FolderCoverFiles.locked(checkpoint) { raw.install(folder, source) }
            }
            try {
                FolderCoverBatch(store, checkpoint, onProgress).run(
                    DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)).toString())
            } finally { revision.incrementAndGet() }
        }
}
