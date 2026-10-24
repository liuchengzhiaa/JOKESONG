package com.example.asmrplayer.core.annotations

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.example.asmrplayer.core.artwork.audioArtworkUri
import com.example.asmrplayer.core.artwork.folderArtworkUri
import com.example.asmrplayer.core.database.AsmrDatabase
import com.example.asmrplayer.core.storage.RootPreferences
import com.example.asmrplayer.core.subtitles.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class BackupPreview(val items: Int, val matched: Int, val missing: Int, val tags: Int)

/** Private, bounded staging; no annotation is changed until full authentication + confirmation. */
internal class AnnotationImportStage(context: Context, val root: String) : AutoCloseable {
    private val file = File.createTempFile("annotation-import-", ".db", context.cacheDir)
    val db = SQLiteDatabase.openOrCreateDatabase(file, null).apply {
        execSQL("PRAGMA page_size=4096")
        rawQuery("PRAGMA max_page_count=65536", null).use { require(it.moveToFirst()) } // <=256 MiB.
        execSQL("CREATE TABLE tags(nameKey TEXT PRIMARY KEY, name TEXT NOT NULL)")
        execSQL("CREATE TABLE records(path TEXT PRIMARY KEY, payload BLOB NOT NULL, uri TEXT, art TEXT, parent TEXT, lyric TEXT, lyricName TEXT, lyricSize INTEGER, lyricModified INTEGER)")
        execSQL("CREATE UNIQUE INDEX resolved_uri ON records(uri)")
    }
    var count = 0; var matched = 0; var tags = 0
    fun tag(name: String) {
        val normalized = AnnotationPolicy.tagName(name)
        val row = ContentValues().apply { put("nameKey", AnnotationPolicy.tagKey(normalized)); put("name", normalized) }
        if (db.insertWithOnConflict("tags", null, row, SQLiteDatabase.CONFLICT_IGNORE) != -1L) tags++
        require(tags <= PortableAnnotations.MaxTags)
    }
    fun item(record: PortableAnnotations.Item) {
        record.tags.forEach(::tag)
        db.insertOrThrow("records", null, ContentValues().apply { put("path", record.path); put("payload", PortableAnnotations.itemBytes(record)) })
        require(++count <= PortableAnnotations.MaxItems)
    }
    fun preview() = BackupPreview(count, matched, count - matched, tags)
    override fun close() {
        db.close()
        SQLiteDatabase.deleteDatabase(file) // Only this session's generated private staging file.
    }
}

internal class AnnotationBackupStore(private val context: Context) {
    private val database = AsmrDatabase.get(context)
    private val dao = database.annotationDao()
    private suspend fun requireRoot(root: String) { require(RootPreferences(context).rootUri.first()?.toString() == root) }

    suspend fun export(root: String, password: CharArray, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        requireRoot(root)
        require(password.size >= 8 && password.any { !it.isWhitespace() })
        val tree = PortableDocumentTree(context.contentResolver, root)
        val file = File.createTempFile("annotation-export-", ".jst", context.cacheDir)
        try {
            FileOutputStream(file).use { raw -> PortableAnnotations.encryptedOutput(raw, password).use { encrypted ->
                val writer = PortableAnnotations.RecordsWriter(encrypted)
                database.withTransaction {
                    var tagAfter = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val page = dao.exportTags(root, tagAfter); if (page.isEmpty()) break
                        page.forEach { writer.tag(it.name) }; tagAfter = page.last().id
                    }
                    var after = ""; var count = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val page = dao.exportItems(root, after); if (page.isEmpty()) break
                        for (item in page) {
                            currentCoroutineContext().ensureActive()
                            writer.item(PortableAnnotations.Item(tree.relative(item.documentUri), item.kind, item.title, item.subtitle,
                                item.score, dao.assigned(root, item.documentUri).map { it.name },
                                item.lyricUri?.let { tree.relative(it) }, item.lyricFormat, item.lyricLanguage))
                            progress(++count)
                        }
                        after = page.last().documentUri
                    }
                }
                writer.finish()
            } }
            requireRoot(root)
            file
        } catch (e: Throwable) { file.delete(); throw e }
        finally { password.fill('\u0000') }
    }

    suspend fun prepare(root: String, source: Uri, password: CharArray, progress: (Int) -> Unit): AnnotationImportStage = withContext(Dispatchers.IO) {
        requireRoot(root)
        val stage = AnnotationImportStage(context, root)
        try {
            val job = currentCoroutineContext()
            context.contentResolver.openInputStream(source)?.buffered()?.use { input ->
                PortableAnnotations.decryptedInput(input, password).use { decrypted ->
                    stage.db.beginTransaction()
                    try {
                        PortableAnnotations.readRecords(decrypted, { job.ensureActive(); stage.tag(it) }, {
                            job.ensureActive(); stage.item(it); progress(stage.count)
                        })
                        stage.db.setTransactionSuccessful()
                    } finally { stage.db.endTransaction() }
                }
            } ?: error("Backup unavailable")
            // Mandatory final authenticated frame has been consumed. Only now resolve paths.
            val tree = PortableDocumentTree(context.contentResolver, root)
            stage.db.rawQuery("SELECT path,payload FROM records ORDER BY path", null).use { c ->
                var resolved = 0
                while (c.moveToNext()) {
                    job.ensureActive()
                    val record = PortableAnnotations.itemFromBytes(c.getBlob(1))
                    val doc = tree.resolve(record.path)
                    if (doc != null && tree.compatible(doc, record.kind)) {
                        val parent = if (record.path.isEmpty()) null else tree.resolve(record.path.substringBeforeLast('/', ""))
                        val version = "${doc.size}:${doc.modified}"
                        val parentArt = parent?.let { folderArtworkUri(root, it.uri, version) }
                        val artwork = if (doc.folder) folderArtworkUri(root, doc.uri, version) else audioArtworkUri(doc.uri, parentArt, version)
                        val lyric = record.lyricPath?.let(tree::resolve)?.takeIf { !it.folder }
                        val candidate = lyric?.let { l -> TimedTextFormat.entries.firstOrNull { it.name == record.lyricFormat }?.let {
                            TimedTextCandidate(l.name, l.uri, it, l.size, l.modified)
                        } }
                        val match = candidate?.let { matchTimedText(mapOf(doc.uri to doc.name.substringBeforeLast('.')), listOf(it))[doc.uri] }
                        stage.db.update("records", ContentValues().apply {
                            put("uri", doc.uri); put("art", artwork); put("parent", parent?.uri)
                            put("lyric", match?.candidate?.documentUri); put("lyricName", match?.candidate?.displayName)
                            put("lyricSize", match?.candidate?.sizeBytes ?: 0); put("lyricModified", match?.candidate?.modifiedAtMs ?: 0)
                        }, "path=?", arrayOf(record.path))
                        stage.matched++
                    }
                    progress(stage.count + ++resolved)
                }
            }
            requireRoot(root)
            stage
        } catch (e: Throwable) { stage.close(); throw e }
        finally { password.fill('\u0000') }
    }

    suspend fun commit(stage: AnnotationImportStage, progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        requireRoot(stage.root)
        database.withTransaction {
            stage.db.rawQuery("SELECT name FROM tags ORDER BY nameKey", null).use { tags ->
                while (tags.moveToNext()) { currentCoroutineContext().ensureActive(); dao.restoreTag(stage.root, tags.getString(0)) }
            }
            stage.db.rawQuery("SELECT payload,uri,art,parent,lyric,lyricName,lyricSize,lyricModified FROM records WHERE uri IS NOT NULL ORDER BY path", null).use { c ->
                var count = 0
                while (c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val item = PortableAnnotations.itemFromBytes(c.getBlob(0)); val uri = c.getString(1)
                    val snapshot = UserItemEntity(stage.root, uri, item.kind, item.title, item.subtitle,
                        c.getString(2), uri.hashCode(), c.getString(3), item.score, c.getString(4), c.getString(5),
                        item.lyricFormat, c.getLong(6), c.getLong(7), item.lyricLanguage)
                    dao.mergeImport(snapshot, item.tags)
                    progress(++count)
                }
            }
            requireRoot(stage.root)
        }
    }

    suspend fun copyExport(file: File, destination: Uri) = withContext(Dispatchers.IO) {
        val expected = java.security.MessageDigest.getInstance("SHA-256")
        context.contentResolver.openOutputStream(destination, "wt")?.use { out ->
            FileInputStream(file).use { input ->
                val bytes = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(bytes); if (count < 0) break
                    out.write(bytes, 0, count); expected.update(bytes, 0, count)
                }
                out.flush()
            }
        } ?: error("Export destination unavailable")
        // Do not call an unverified provider write a successful backup.
        val actual = java.security.MessageDigest.getInstance("SHA-256")
        var length = 0L
        context.contentResolver.openInputStream(destination)?.use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(bytes); if (count < 0) break
                length += count; require(length <= file.length())
                actual.update(bytes, 0, count)
            }
        } ?: error("Export read-back unavailable")
        require(length == file.length() && java.security.MessageDigest.isEqual(expected.digest(), actual.digest()))
    }
}
