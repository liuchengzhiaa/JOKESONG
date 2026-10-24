package com.example.asmrplayer.core.annotations

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.example.asmrplayer.core.storage.SupportedAudio

internal data class PortableDocument(val uri: String, val name: String, val mime: String,
    val size: Long, val modified: Long) {
    val folder: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
}

/** Scoped SAF lookups only. No filesystem paths, broad storage, or fuzzy basename matching. */
internal class PortableDocumentTree(private val resolver: ContentResolver, val root: String) {
    private val tree = Uri.parse(root)
    private val rootId = DocumentsContract.getTreeDocumentId(tree)
    private val externalStorage = tree.authority == "com.android.externalstorage.documents"
    private val cache = object : LinkedHashMap<String, PortableDocument>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PortableDocument>?) = size > 128
    }
    fun relative(uri: String): String {
        require(AnnotationPolicy.sameTree(root, uri))
        if (externalStorage) return PortableAnnotations.relative(rootId, DocumentsContract.getDocumentId(Uri.parse(uri)))
        val ids = DocumentsContract.findDocumentPath(resolver, Uri.parse(uri))?.path
            ?: throw UnsupportedOperationException("Provider does not expose document ancestry")
        val start = ids.indexOf(rootId)
        require(start >= 0 && ids.size - start <= 129)
        return PortableAnnotations.path(ids.drop(start + 1).joinToString("/") { id ->
            requireNotNull(metadata(DocumentsContract.buildDocumentUriUsingTree(tree, id))).name
                .also { require('/' !in it) }
        })
    }
    fun resolve(path: String): PortableDocument? {
        PortableAnnotations.path(path)
        cache[path]?.let { return it }
        if (externalStorage) {
            val id = if (path.isEmpty()) rootId else PortableAnnotations.documentPrefix(rootId) + path
            return metadata(DocumentsContract.buildDocumentUriUsingTree(tree, id))?.takeIf {
                path.isEmpty() || it.name == path.substringAfterLast('/')
            }?.also { cache[path] = it }
        }
        if (path.isEmpty()) return metadata(DocumentsContract.buildDocumentUriUsingTree(tree, rootId))?.also { cache[path] = it }
        val parent = resolve(path.substringBeforeLast('/', ""))?.takeIf { it.folder } ?: return null
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(Uri.parse(parent.uri)))
        var result: PortableDocument? = null
        resolver.query(children, Projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == path.substringAfterLast('/')) {
                    // Same-name siblings are ambiguous; never arbitrarily pick one.
                    if (result != null) return null
                    result = PortableDocument(DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)).toString(),
                        cursor.getString(1), cursor.getString(2).orEmpty(), cursor.getLong(3), cursor.getLong(4))
                }
            }
        }
        return result?.also { cache[path] = it }
    }
    fun compatible(doc: PortableDocument, kind: String): Boolean =
        if (kind == "Folder") doc.folder else !doc.folder && SupportedAudio.isSupported(doc.name, doc.mime)

    private fun metadata(uri: Uri): PortableDocument? = try {
        resolver.query(uri, Projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null else PortableDocument(uri.toString(), c.getString(1), c.getString(2).orEmpty(), c.getLong(3), c.getLong(4))
        }
    } catch (_: java.io.FileNotFoundException) { null }
    private companion object {
        val Projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
    }
}
