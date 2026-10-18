package com.example.asmrplayer.core.artwork

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.util.LruCache
import com.example.asmrplayer.core.storage.readDirectFolderArtwork
import androidx.core.net.toUri
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

internal fun artworkCacheKey(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal fun audioArtworkUri(audio: String, fallback: String?, version: String): String = Uri.Builder()
    .scheme("content").authority(AudioArtworkProvider.Authority).appendPath("track")
    .appendQueryParameter("audio", audio).appendQueryParameter("fallback", fallback)
    .appendQueryParameter("version", version).build().toString()

internal fun folderArtworkUri(tree: String, folder: String, version: String): String = Uri.Builder()
    .scheme("content").authority(AudioArtworkProvider.Authority).appendPath("folder")
    .appendQueryParameter("tree", tree).appendQueryParameter("folder", folder)
    .appendQueryParameter("version", version).build().toString()

/** Private read-only lazy artwork endpoint. Never opens audio during directory enumeration. */
class AudioArtworkProvider : ContentProvider() {
    companion object {
        const val Authority = "com.example.asmrplayer.audioartwork"
        private const val MaxImageBytes = 8 * 1024 * 1024
        private const val MaxCacheEntries = 192
        private const val MaxCacheBytes = 64L * 1024 * 1024
    }
    private val gate = Any()
    private val missing = LruCache<String, Boolean>(192)
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String = "image/*"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = openFile(uri, mode, null)

    override fun openFile(uri: Uri, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        signal?.throwIfCanceled()
        if (mode != "r" || uri.authority != Authority) throw FileNotFoundException("Invalid artwork request")
        // Do not hold the audio cache monitor while walking/writing folder covers.
        if (uri.path == "/folder") return openFolderArtwork(uri, signal)
        return openTrackArtwork(uri, mode)
    }
    private fun openTrackArtwork(uri: Uri, mode: String): ParcelFileDescriptor = synchronized(gate) {
        if (mode != "r" || uri.authority != Authority) throw FileNotFoundException("Invalid artwork request")
        if (uri.path != "/track") throw FileNotFoundException("Invalid artwork request")
        val ctx = context ?: throw FileNotFoundException("Artwork provider unavailable")
        val audio = uri.getQueryParameter("audio")?.let(Uri::parse)
            ?.takeIf { it.scheme == "content" && it.authority != Authority }
            ?: throw FileNotFoundException("Invalid audio source")
        val directory = File(ctx.cacheDir, "track-artwork-v1").apply { mkdirs() }
        val key = artworkCacheKey(uri.toString())
        val target = File(directory, "$key.art")
        if (target.isFile) {
            target.setLastModified(System.currentTimeMillis())
            return@synchronized ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        if (missing.get(key) != true) {
            val retriever = MediaMetadataRetriever()
            val bytes = try {
                retriever.setDataSource(ctx, audio)
                retriever.embeddedPicture?.takeIf { it.size <= MaxImageBytes }
            } catch (_: RuntimeException) { null }
            finally { runCatching { retriever.release() } }
            if (bytes != null) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    val temporary = File.createTempFile("artwork-", ".tmp", directory)
                    try {
                        temporary.writeBytes(bytes)
                        if (!temporary.renameTo(target)) throw FileNotFoundException("Cannot cache artwork")
                    } finally { temporary.delete() }
                    val files = directory.listFiles().orEmpty()
                    var total = files.sumOf { it.length() }
                    var count = files.count { it.extension == "art" }
                    files.filter { it != target && it.extension == "art" }
                         .sortedBy { it.lastModified() }.forEach { old ->
                            if (total > MaxCacheBytes || count > MaxCacheEntries) {
                                val size = old.length()
                                if (old.delete()) { total -= size; count-- }
                            }
                        }
                    return@synchronized ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }
            missing.put(key, true)
        }
        val fallback = uri.getQueryParameter("fallback")?.let(Uri::parse)
            ?.takeIf { it.scheme == "content" && it.authority != Authority }
            ?: throw FileNotFoundException("No artwork for this audio")
        ctx.contentResolver.openFileDescriptor(fallback, "r") ?: throw FileNotFoundException("Fallback artwork unavailable")
    }

    /** Direct cover metadata only. Never joins processing/writing or holds its lock. */
    private fun openFolderArtwork(uri: Uri, signal: CancellationSignal?): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("Artwork provider unavailable")
        val tree = uri.getQueryParameter("tree")?.let(Uri::parse)
        val folder = uri.getQueryParameter("folder")?.let(Uri::parse)
        if (tree == null || folder == null || tree.scheme != "content" ||
            folder.scheme != "content" || !DocumentsContract.isTreeUri(tree) ||
            tree.authority != folder.authority || tree.authority == Authority
        ) throw FileNotFoundException("Invalid folder source")
        val target = try {
            // Display is strictly read-only: no folder-write lock, journal, DB, audio or recursion.
            val root = DocumentsContract.getTreeDocumentId(tree)
            require(DocumentsContract.getTreeDocumentId(folder) == root)
            readDirectFolderArtwork(ctx.contentResolver, tree, folder) { signal?.throwIfCanceled() }.artworkUri
        } catch (cancelled: android.os.OperationCanceledException) { throw cancelled }
          catch (error: Exception) { throw FileNotFoundException("Folder cover unavailable: ${error.message}").apply { initCause(error) } }
            ?: throw FileNotFoundException("Folder has no artwork")
        signal?.throwIfCanceled()
        val source = target.toUri()
        return ctx.contentResolver.openFileDescriptor(source, "r", signal)
            ?: throw FileNotFoundException("Folder artwork unavailable")
    }
}
