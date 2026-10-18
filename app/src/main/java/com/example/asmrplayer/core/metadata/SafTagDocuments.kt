package com.example.asmrplayer.core.metadata

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract as Docs
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import androidx.core.net.toUri
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Narrow local-provider adapter. Never reconstructs /storage paths or requests broad storage. */
internal class SafTagDocuments private constructor(private val context: Context, private val base: JSONObject) : TagTransactionStore {
    private val resolver = context.contentResolver
    val sourceUri: Uri = base.getString("source").toUri()
    val parentUri: Uri = base.getString("parent").toUri()
    val fileName: String = base.getString("name")
    val mime: String = base.getString("mime")
    val version: String = base.getString("version")
    private val journalFile = journalFile(context)
    private val known = mutableMapOf<String, Uri>()
    init {
        require(sourceUri.authority == "com.android.externalstorage.documents" && parentUri.authority == sourceUri.authority)
        val id = Docs.getDocumentId(sourceUri)
        require(id.startsWith("primary:") && Docs.getTreeDocumentId(sourceUri) == Docs.getTreeDocumentId(parentUri))
        require(id.substringBeforeLast('/', "primary:") == Docs.getDocumentId(parentUri))
        require(id.substringAfter(':').substringAfterLast('/') == fileName && !isMetadataArtifactName(fileName))
    }

    companion object {
        private fun journalFile(context: Context) = AtomicFile(File(context.noBackupFilesDir, "metadata-edit-journal.json"))
        fun pending(context: Context): SafTagDocuments? {
            val file = journalFile(context)
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
            val json = file.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
            return SafTagDocuments(context, json)
        }
        fun open(context: Context, uri: Uri): SafTagDocuments {
            check(pending(context) == null) { "RECOVERY_REQUIRED" }
            require(uri.authority == "com.android.externalstorage.documents" && Docs.isTreeUri(uri)) { "UNSUPPORTED_PROVIDER" }
            val id = Docs.getDocumentId(uri); val root = Docs.getTreeDocumentId(uri)
            require(id.startsWith("primary:") && (root == "primary:" || id.startsWith(root.trimEnd('/') + "/"))) { "UNSUPPORTED_PROVIDER" }
            check(context.contentResolver.persistedUriPermissions.any {
                it.isReadPermission && it.isWritePermission && it.uri.authority == uri.authority &&
                    runCatching { Docs.getTreeDocumentId(it.uri) == root }.getOrDefault(false)
            }) { "WRITE_GRANT_REQUIRED" }
            val parentId = id.substringBeforeLast('/', "primary:")
            require(parentId.startsWith("primary:") && parentId != id) { "UNSUPPORTED_PROVIDER" }
            val parent = Docs.buildDocumentUriUsingTree(uri, parentId)
            val info = info(context, uri)
            require(info.name.substringAfterLast('.').lowercase() in WritableAudioExtensions && !isMetadataArtifactName(info.name)) { "UNSUPPORTED_FORMAT" }
            val required = Docs.Document.FLAG_SUPPORTS_WRITE or Docs.Document.FLAG_SUPPORTS_RENAME or Docs.Document.FLAG_SUPPORTS_DELETE
            check(info.flags and required == required && info(context, parent).flags and Docs.Document.FLAG_DIR_SUPPORTS_CREATE != 0) { "READ_ONLY_PROVIDER" }
            context.contentResolver.openFileDescriptor(uri, "r")!!.use { fd ->
                check(OsConstants.S_ISREG(Os.fstat(fd.fileDescriptor).st_mode)) { "NON_SEEKABLE_FILE" }
                check(fd.statSize > 0 && fd.statSize == info.size) { "UNKNOWN_FILE_SIZE" }
                val fs = Os.fstatvfs(fd.fileDescriptor)
                check(fs.f_bavail * fs.f_frsize > info.size + 64L * 1024 * 1024) { "INSUFFICIENT_SPACE" }
            }
            return SafTagDocuments(context, JSONObject().put("source", uri.toString()).put("parent", parent.toString())
                .put("name", info.name).put("mime", info.mime).put("version", "${info.size}:${info.modified}"))
        }
        data class Info(val name: String, val mime: String, val size: Long, val modified: Long, val flags: Int)
        fun info(context: Context, uri: Uri): Info {
            val columns = arrayOf(Docs.Document.COLUMN_DISPLAY_NAME, Docs.Document.COLUMN_MIME_TYPE,
                Docs.Document.COLUMN_SIZE, Docs.Document.COLUMN_LAST_MODIFIED, Docs.Document.COLUMN_FLAGS)
            return context.contentResolver.query(uri, columns, null, null, null)?.use { c ->
                check(c.moveToFirst()) { "DOCUMENT_MISSING" }
                Info(c.getString(0), c.getString(1) ?: "application/octet-stream", c.getLong(2), c.getLong(3), c.getInt(4))
            } ?: error("DOCUMENT_MISSING")
        }
    }

    private fun find(name: String): Uri? {
        known[name]?.let { return it }
        val children = Docs.buildChildDocumentsUriUsingTree(parentUri, Docs.getDocumentId(parentUri))
        val columns = arrayOf(Docs.Document.COLUMN_DOCUMENT_ID, Docs.Document.COLUMN_DISPLAY_NAME)
        var result: Uri? = null
        resolver.query(children, columns, null, null, null)?.use { c ->
            var count = 0
            while (c.moveToNext()) {
                check(++count <= 100_000) { "DIRECTORY_TOO_LARGE" }
                if (c.getString(1) == name) { check(result == null) { "NAME_CONFLICT" }; result = Docs.buildDocumentUriUsingTree(parentUri, c.getString(0)) }
            }
        } ?: error("PARENT_UNREADABLE")
        return result?.also { known[name] = it }
    }
    fun uri(name: String): Uri = find(name) ?: error("DOCUMENT_MISSING")
    fun fd(name: String, writable: Boolean = false): ParcelFileDescriptor {
        if (writable) require(isMetadataArtifactName(name) && name.contains("-stage.")) { "ORIGINAL_IS_READ_ONLY" }
        return resolver.openFileDescriptor(uri(name), if (writable) "rw" else "r") ?: error("DOCUMENT_UNREADABLE")
    }
    override fun exists(name: String) = find(name) != null
    override fun read(name: String): InputStream = ParcelFileDescriptor.AutoCloseInputStream(fd(name))
    override fun create(name: String): OutputStream {
        require(isMetadataArtifactName(name) && !exists(name))
        val created = Docs.createDocument(resolver, parentUri, mime, name) ?: error("CREATE_FAILED")
        try {
            check(info(context, created).name == name) { "PROVIDER_RENAMED_TEMP" }
            known[name] = created
            return resolver.openOutputStream(created, "wt") ?: error("CREATE_FAILED")
        } catch (error: Exception) { runCatching { Docs.deleteDocument(resolver, created) }; throw error }
    }
    override fun rename(from: String, to: String) {
        require(from == fileName || isMetadataArtifactName(from))
        require(to == fileName || isMetadataArtifactName(to))
        check(!exists(to)) { "NAME_CONFLICT" }
        val moved = Docs.renameDocument(resolver, uri(from), to) ?: error("RENAME_FAILED")
        known.remove(from); known[to] = moved
        check(info(context, moved).name == to) { "PROVIDER_RENAMED_TEMP" }
    }
    override fun deleteArtifact(name: String) {
        require(isMetadataArtifactName(name) && name != fileName)
        check(Docs.deleteDocument(resolver, uri(name))) { "CLEANUP_FAILED" }; known.remove(name)
    }
    override fun sync(name: String) { require(isMetadataArtifactName(name)); fd(name, true).use { Os.fsync(it.fileDescriptor) } }
    override fun journal(): EditJournal? {
        val pending = pending(context) ?: return null
        check(pending.base.getString("source") == sourceUri.toString()) { "RECOVERY_REQUIRED" }
        val j = pending.base
        require(isMetadataArtifactName(j.getString("stage")) && isMetadataArtifactName(j.getString("backup")))
        require(j.getString("stage").substringBefore("-stage.") == j.getString("backup").substringBefore("-backup."))
        require(j.optString("originalHash").let { it.isEmpty() || it.matches(Regex("[0-9a-f]{64}")) })
        require(j.optString("stageHash").let { it.isEmpty() || it.matches(Regex("[0-9a-f]{64}")) })
        return EditJournal(j.getString("name"), j.getString("stage"), j.getString("backup"),
            j.optString("originalHash"), j.optString("stageHash"), EditPhase.valueOf(j.getString("phase")))
    }
    override fun record(value: EditJournal) {
        val json = JSONObject(base.toString()).put("stage", value.stageName).put("backup", value.backupName)
            .put("originalHash", value.originalHash).put("stageHash", value.stageHash).put("phase", value.phase.name)
        val output = journalFile.startWrite()
        try { output.write(json.toString().toByteArray()); journalFile.finishWrite(output) }
        catch (error: Exception) { journalFile.failWrite(output); throw error }
    }
    override fun clearJournal() = journalFile.delete()
}
