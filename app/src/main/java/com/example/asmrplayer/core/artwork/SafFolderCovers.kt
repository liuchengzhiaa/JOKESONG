package com.example.asmrplayer.core.artwork

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract as Docs
import android.system.Os
import android.util.AtomicFile
import androidx.core.net.toUri
import com.example.asmrplayer.core.database.AsmrDatabase
import com.example.asmrplayer.core.metadata.isMetadataArtifactName
import com.example.asmrplayer.core.storage.SupportedArtwork
import com.example.asmrplayer.core.storage.SupportedAudio
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest
import java.util.PriorityQueue
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

internal class SafFolderCoverStore(
    private val context: Context,
    private val tree: Uri,
    private val budget: CoverLookupBudget,
    private val catalog: CoverDirectoryCatalog? = null,
) : FolderCoverStore {
    private val resolver = context.contentResolver
    private val root = Docs.getTreeDocumentId(tree)
    private val columns = arrayOf(Docs.Document.COLUMN_DOCUMENT_ID, Docs.Document.COLUMN_DISPLAY_NAME,
        Docs.Document.COLUMN_MIME_TYPE, Docs.Document.COLUMN_SIZE, Docs.Document.COLUMN_LAST_MODIFIED)

    private fun checked(folder: String): Uri {
        val uri = folder.toUri()
        require(uri.scheme == "content" && Docs.isTreeUri(uri) && uri.authority == tree.authority)
        require(Docs.getTreeDocumentId(uri) == root)
        val id = Docs.getDocumentId(uri)
        // For opaque providers use isChildDocument; never infer a filesystem path.
        val rootUri = Docs.buildDocumentUriUsingTree(tree, root)
        require(id == root || Docs.isChildDocument(resolver, rootUri, uri)) { "COVER_OUTSIDE_ROOT" }
        return uri
    }
    private fun rows(folder: String, accept: (CoverDocument, String?) -> Unit) {
        val parent = checked(folder)
        val children = Docs.buildChildDocumentsUriUsingTree(tree, Docs.getDocumentId(parent))
        resolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                budget.row()
                val name = c.getString(1) ?: continue
                if (isMetadataArtifactName(name) || isFolderCoverArtifact(name)) continue
                val doc = Docs.buildDocumentUriUsingTree(tree, c.getString(0)).toString()
                accept(CoverDocument(doc, name, "${c.getLong(3)}:${c.getLong(4)}"), c.getString(2))
            }
        } ?: throw FileNotFoundException("COVER_DIRECTORY_UNREADABLE")
    }
    override fun existing(folder: String): String? {
        recover(folder)
        if (catalog != null) {
            prepareCatalog(folder)
            return catalog.cover(folder)
        }
        return covers(folder).firstOrNull()?.uri
    }
    private fun prepareCatalog(folder: String) {
        catalog!!.prepare(folder) {
            val entries = CoverDirectoryRows()
            rows(folder) { doc, mime ->
                val kind = when {
                    mime == Docs.Document.MIME_TYPE_DIR -> CoverDirectoryRow.Folder
                    isNamedFolderCover(doc.name) && SupportedArtwork.isSupported(doc.name, mime) -> CoverDirectoryRow.Cover
                    SupportedArtwork.isSupported(doc.name, mime) -> CoverDirectoryRow.Image
                    SupportedAudio.isSupported(doc.name, mime) -> CoverDirectoryRow.Audio
                    else -> null
                }
                if (kind != null) entries.add(doc, kind)
            }
            entries.sorted()
        }
    }
    // Two entries suffice to refuse ambiguous manual replacement; no unbounded image list.
    private fun covers(folder: String): List<CoverDocument> {
        val best = PriorityQueue(2, CoverDocumentOrder.reversed())
        rows(folder) { doc, mime ->
            if (isNamedFolderCover(doc.name) && SupportedArtwork.isSupported(doc.name, mime)) {
                if (best.size < 2) best.add(doc)
                else if (CoverDocumentOrder.compare(doc, best.peek()) < 0) { best.poll(); best.add(doc) }
            }
        }
        return best.sortedWith(CoverDocumentOrder)
    }
    override fun page(folder: String, kind: CoverChildKind, after: CoverDocument?, limit: Int): List<CoverDocument> {
        if (catalog != null) {
            prepareCatalog(folder)
            return catalog.page(folder, kind, after, limit)
        }
        val best = PriorityQueue(limit, CoverDocumentOrder.reversed())
        rows(folder) { doc, mime ->
            val eligible = when (kind) {
                CoverChildKind.Folder -> mime == Docs.Document.MIME_TYPE_DIR
                CoverChildKind.Audio -> mime != Docs.Document.MIME_TYPE_DIR && SupportedAudio.isSupported(doc.name, mime)
                CoverChildKind.Image -> mime != Docs.Document.MIME_TYPE_DIR && !isNamedFolderCover(doc.name) && SupportedArtwork.isSupported(doc.name, mime)
            }
            if (eligible && (after == null || CoverDocumentOrder.compare(doc, after) > 0)) {
                if (best.size < limit) best.add(doc)
                else if (CoverDocumentOrder.compare(doc, best.peek()) < 0) { best.poll(); best.add(doc) }
            }
        }
        return best.sortedWith(CoverDocumentOrder)
    }
    override fun legacy(folder: String): String? {
        if (!File(context.filesDir, "folder-covers").isDirectory) return null
        val row = runBlocking { AsmrDatabase.get(context).libraryDao().folderCoverOverrides(listOf(folder)).singleOrNull() }
        val file = ownedLegacyCover(context, row?.sourceUri)
        return file?.takeIf { it.isFile }?.toUri()?.toString()
    }
    fun audioDocument(uri: String): CoverDocument {
        val audio = uri.toUri()
        require(audio.scheme == "content" && audio.authority == tree.authority && Docs.getTreeDocumentId(audio) == root)
        return resolver.query(audio, columns, null, null, null)?.use { c ->
            check(c.moveToFirst()) { "COVER_AUDIO_MISSING" }
            CoverDocument(uri, c.getString(1), "${c.getLong(3)}:${c.getLong(4)}")
        } ?: error("COVER_AUDIO_MISSING")
    }
    /** Read dimensions only. Images are copied/fully validated only after selection. */
    override fun image(candidate: CoverDocument): CoverImageCandidate? {
        budget.check()
        if (!folderCoverLengthAllowed(candidate.version)) return null
        val uri = candidate.uri.toUri()
        require(uri.scheme == "content" && uri.authority == tree.authority && Docs.getTreeDocumentId(uri) == root)
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val input = resolver.openInputStream(uri) ?: return null
            CoverImageHeaderStream(input).use { limited -> BitmapFactory.decodeStream(limited, null, bounds) }
            budget.check()
            if (bounds.outWidth !in 1..65536 || bounds.outHeight !in 1..65536) null
            else CoverImageCandidate(candidate, bounds.outWidth, bounds.outHeight)
        } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
          catch (_: Exception) { null }
    }
    override fun embedded(audio: CoverDocument): String? {
        budget.audio()
        val uri = audioArtworkUri(audio.uri, null, audio.version)
        // The track provider has a bounded positive/negative cache; fallback is explicitly absent.
        return try { resolver.openFileDescriptor(uri.toUri(), "r")?.use { uri } }
        catch (_: FileNotFoundException) { null }
    }
    override fun install(folder: String, source: String): String = save(folder, source.toUri(), replace = false)

    /** The original chosen image is copied byte-for-byte. No tag, alias or source-image change. */
    fun save(folder: String, source: Uri, replace: Boolean): String {
        val parent = checked(folder)
        requireWritable(parent)
        recover(folder)
        val old = covers(folder)
        if (!replace && old.isNotEmpty()) return old.first().uri
        check(old.size <= 1) { "MULTIPLE_COVER_FILES" }
        val local = File.createTempFile("cover-copy-", ".tmp", context.cacheDir)
        try {
            openSource(source).use { input -> local.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    budget.check()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= MaximumFolderCoverBytes) { "COVER_IMAGE_TOO_LARGE" }
                    output.write(buffer, 0, count)
                }
            } }
            val mime = validateFolderCoverImage(local)
            val extension = when (mime) {
                "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"
                "image/bmp", "image/x-ms-bmp" -> "bmp"; "image/heif", "image/heic" -> "heif"
                "image/avif" -> "avif"; else -> error("COVER_UNSUPPORTED_IMAGE")
            }
            val token = UUID.randomUUID().toString()
            val stageName = ".jokesong-cover-$token-stage.$extension"
            val stage = Docs.createDocument(resolver, parent, mime, stageName) ?: error("COVER_CREATE_FAILED")
            try {
                check(info(stage).first == stageName) { "COVER_PROVIDER_CHANGED_NAME" }
                resolver.openFileDescriptor(stage, "rw")!!.let { fd ->
                    ParcelFileDescriptor.AutoCloseOutputStream(fd).use { output ->
                        local.inputStream().use { it.copyTo(output, 32 * 1024) }
                        output.flush()
                        Os.fsync(fd.fileDescriptor)
                    }
                }
                check(info(stage).second and Docs.Document.FLAG_SUPPORTS_RENAME != 0) { "COVER_RENAME_UNSUPPORTED" }
                old.singleOrNull()?.let {
                    check(info(it.uri.toUri()).second and Docs.Document.FLAG_SUPPORTS_RENAME != 0) { "COVER_RENAME_UNSUPPORTED" }
                }
                val txStore = TransactionStore(parent)
                val sourceHash = sha256(local.inputStream())
                check(txStore.hash(stageName) == sourceHash) { "COVER_STAGE_VERIFY_FAILED" }
                // Detect an intervening external cover edit before moving anything.
                check(covers(folder) == old) { "COVER_ORIGINAL_CHANGED" }
                budget.check() // No cancellation point inside the rename/recovery transaction.
                val j = CoverInstallJournal(stageName, "cover.$extension", old.singleOrNull()?.name,
                    ".jokesong-cover-$token-backup.${old.singleOrNull()?.name?.substringAfterLast('.') ?: extension}",
                    sourceHash)
                CoverInstallTransaction(txStore).commit(j)
                return txStore.find(j.target)!!.toString()
            } catch (error: Exception) {
                // Do not delete a stage needed by a pending transaction or an old-image backup.
                if (TransactionStore(parent).pending() == null) runCatching {
                    if (info(stage).first == stageName) Docs.deleteDocument(resolver, stage)
                }
                throw error
            }
        } finally { local.delete() }
    }
    private fun openSource(uri: Uri): InputStream {
        if (uri.scheme == "file") return (ownedLegacyCover(context, uri.toString())
            ?: error("COVER_UNOWNED_FILE")).inputStream()
        require(uri.scheme == "content")
        return resolver.openInputStream(uri) ?: error("COVER_SOURCE_UNREADABLE")
    }
    private fun requireWritable(parent: Uri) {
        // Known local provider only. SAF flags do not certify cloud rename/replacement semantics.
        require(parent.authority == "com.android.externalstorage.documents") { "COVER_UNSUPPORTED_PROVIDER" }
        check(resolver.persistedUriPermissions.any {
            it.isReadPermission && it.isWritePermission && it.uri.authority == tree.authority &&
                runCatching { Docs.getTreeDocumentId(it.uri) == root }.getOrDefault(false)
        }) { "COVER_WRITE_GRANT_REQUIRED" }
        check(info(parent).second and Docs.Document.FLAG_DIR_SUPPORTS_CREATE != 0) { "COVER_READ_ONLY" }
    }
    private fun recover(folder: String) {
        val parent = checked(folder)
        val store = TransactionStore(parent)
        if (store.pending() != null) {
            requireWritable(parent)
            CoverInstallTransaction(store).recover()
        }
    }
    private fun info(uri: Uri): Pair<String, Int> = resolver.query(uri,
        arrayOf(Docs.Document.COLUMN_DISPLAY_NAME, Docs.Document.COLUMN_FLAGS), null, null, null)?.use {
        check(it.moveToFirst()) { "COVER_DOCUMENT_MISSING" }; it.getString(0) to it.getInt(1)
    } ?: error("COVER_DOCUMENT_MISSING")

    private inner class TransactionStore(private val parent: Uri) : CoverInstallStore {
        // Stable across granting a different ancestor tree for the same physical document.
        private val identity = "${parent.authority}:${Docs.getDocumentId(parent)}"
        private val journal = AtomicFile(File(context.noBackupFilesDir, "folder-cover-${artworkCacheKey(identity)}.json"))
        private val known = mutableMapOf<String, Uri>()
        fun find(name: String): Uri? {
            known[name]?.let { return it } // Reuse positive IDs, NEVER negative conflict checks.
            val children = Docs.buildChildDocumentsUriUsingTree(tree, Docs.getDocumentId(parent))
            var found: Uri? = null
            resolver.query(children, columns, null, null, null)?.use { c ->
                var count = 0
                while (c.moveToNext()) {
                    check(++count <= 100_000) { "COVER_ROW_BUDGET" }
                    if (c.getString(1) == name) {
                        check(found == null) { "COVER_NAME_CONFLICT" }
                        found = Docs.buildDocumentUriUsingTree(tree, c.getString(0))
                    }
                }
            } ?: error("COVER_DIRECTORY_UNREADABLE")
            return found?.also { known[name] = it }
        }
        override fun exists(name: String) = find(name) != null
        override fun hash(name: String): String = sha256(resolver.openInputStream(find(name) ?: error("COVER_DOCUMENT_MISSING"))!!)
        override fun rename(from: String, to: String) {
            check(!exists(to)) { "COVER_NAME_CONFLICT" }
            val uri = find(from) ?: error("COVER_DOCUMENT_MISSING")
            check(info(uri).second and Docs.Document.FLAG_SUPPORTS_RENAME != 0) { "COVER_RENAME_UNSUPPORTED" }
            val moved = Docs.renameDocument(resolver, uri, to) ?: error("COVER_RENAME_FAILED")
            known.remove(from)
            known[to] = moved
            check(info(moved).first == to) { "COVER_PROVIDER_CHANGED_NAME" }
        }
        override fun record(journal: CoverInstallJournal) {
            val json = JSONObject().put("folder", identity).put("stage", journal.stage)
                .put("target", journal.target).put("old", journal.old ?: JSONObject.NULL)
                .put("backup", journal.backup).put("hash", journal.hash)
            val output = this.journal.startWrite()
            try { output.write(json.toString().toByteArray()); this.journal.finishWrite(output) }
            catch (error: Exception) { this.journal.failWrite(output); throw error }
        }
        override fun pending(): CoverInstallJournal? {
            if (!journal.baseFile.exists() && !File(journal.baseFile.path + ".bak").exists()) return null
            val json = journal.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
            check(json.getString("folder") == identity)
            val j = CoverInstallJournal(json.getString("stage"), json.getString("target"),
                if (json.isNull("old")) null else json.getString("old"), json.getString("backup"), json.getString("hash"))
            check(listOfNotNull(j.stage, j.target, j.old, j.backup).all { '/' !in it && '\\' !in it })
            check(isFolderCoverArtifact(j.stage) && isFolderCoverArtifact(j.backup) && isNamedFolderCover(j.target))
            check(j.old == null || isNamedFolderCover(j.old))
            check(j.hash.matches(Regex("[0-9a-f]{64}")))
            return j
        }
        override fun clear() = journal.delete()
    }
}

internal fun ownedLegacyCover(context: Context, value: String?): File? {
    val uri = value?.toUri() ?: return null
    if (uri.scheme != "file") return null
    val file = uri.path?.let(::File) ?: return null
    return runCatching { file.canonicalFile.takeIf {
        it.parentFile == File(context.filesDir, "folder-covers").canonicalFile
    } }.getOrNull()
}
private fun sha256(input: InputStream): String = input.use {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(32 * 1024)
    var total = 0L
    while (true) {
        val count = it.read(buffer)
        if (count < 0) break
        total += count; check(total <= 32L * 1024 * 1024) { "COVER_HASH_TOO_LARGE" }
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { b -> "%02x".format(b) }
}
internal fun validateFolderCoverImage(file: File): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    check(bounds.outWidth in 1..65536 && bounds.outHeight in 1..65536) { "COVER_INVALID_IMAGE" }
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val scale = minOf(1f, 128f / maxOf(info.size.width, info.size.height))
        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
            (info.size.height * scale).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setOnPartialImageListener { false }
    }
    bitmap.recycle()
    return bounds.outMimeType ?: error("COVER_INVALID_IMAGE")
}
