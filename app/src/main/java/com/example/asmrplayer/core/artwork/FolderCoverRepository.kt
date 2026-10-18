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
}
