package com.example.asmrplayer.core.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.net.toUri
import com.example.asmrplayer.core.database.AsmrDatabase
import com.example.asmrplayer.core.database.ArtworkEntity
import com.example.asmrplayer.core.database.LibraryDao
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Durable private copies, not media tags or images written into the user's folder. */
internal class FolderCoverRepository(
    context: Context,
    private val dao: LibraryDao = AsmrDatabase.get(context).libraryDao(),
    private val directory: File = File(context.filesDir, "folder-covers"),
) {
    private val context = context.applicationContext
    private val gate = Mutex()

    suspend fun project(items: List<LibraryItem>): List<LibraryItem> = withContext(Dispatchers.IO) {
        // Match Room's bind limit even if listing pagination grows beyond today's 500 cap.
        val owners = items.asSequence().filter { it.type == LibraryItemType.Folder }
            .mapNotNull { it.documentUri }.toList()
        val covers = buildMap {
            owners.chunked(400).forEach { chunk ->
                dao.folderCoverOverrides(chunk).forEach { entry ->
                    ownedFile(entry.sourceUri)?.takeIf { it.isFile }?.let {
                        put(entry.ownerDocumentUri, it.toUri().toString())
                    }
                }
            }
        }
        projectFolderCovers(items, covers)
    }

    suspend fun set(folderUri: String, image: Uri?) = withContext(Dispatchers.IO) {
        require(folderUri.toUri().scheme == "content")
        gate.withLock {
            val key = "manual-folder:$folderUri"
            val previous = dao.folderCoverOverrides(listOf(folderUri)).singleOrNull()
            var staged: File? = null
            var committed = false
            try {
                if (image != null) {
                    require(image.scheme == "content")
                    check(directory.isDirectory || directory.mkdirs())
                    val target = File(directory, "${UUID.randomUUID()}.png")
                    staged = target
                    val bitmap = ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(context.contentResolver, image),
                    ) { decoder, info, _ ->
                        require(info.size.width > 0 && info.size.height > 0)
                        val scale = minOf(1f, 1024f / maxOf(info.size.width, info.size.height))
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.setOnPartialImageListener { false }
                    }
                    try {
                        FileOutputStream(target).use { stream ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                            stream.fd.sync()
                        }
                        check(target.length() in 1..8L * 1024 * 1024)
                    } finally {
                        bitmap.recycle()
                    }
                }
                ensureActive()
                // A cancellation between DB commit and cleanup must not delete its new image.
                withContext(NonCancellable) {
                    val target = staged
                    if (target == null) {
                        dao.deleteFolderCoverOverride(key)
                    } else {
                        dao.upsertArtwork(listOf(ArtworkEntity(
                            artworkKey = key, ownerDocumentUri = folderUri,
                            sourceKind = "manual_folder", sourceUri = target.toUri().toString(),
                            mimeType = "image/png", width = null, height = null,
                            cacheKey = target.name, modifiedAtMs = System.currentTimeMillis(),
                        )))
                    }
                    committed = true
                    // Cleanup failure must not misreport an already committed cover as failed.
                    runCatching { ownedFile(previous?.sourceUri)?.delete() }
                }
            } finally {
                if (!committed) staged?.delete()
            }
        }
    }

    /** Even a malformed DB reference can never delete/open a user media path here. */
    private fun ownedFile(value: String?): File? {
        val uri = value?.toUri() ?: return null
        if (uri.scheme != "file") return null
        val file = uri.path?.let(::File) ?: return null
        return runCatching {
            file.canonicalFile.takeIf { it.parentFile == directory.canonicalFile }
        }.getOrNull()
    }
}
