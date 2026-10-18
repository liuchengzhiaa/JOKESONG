package com.example.asmrplayer.core.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import androidx.core.net.toUri
import com.example.asmrplayer.core.artwork.audioArtworkUri
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** All native mutations are restricted to a sibling stage; never pass the source FD as writable. */
class AudioTagEditor(private val context: Context) {
    companion object { private val gate = Mutex() }

    suspend fun hasPending(): Boolean = withContext(Dispatchers.IO) { gate.withLock { SafTagDocuments.pending(context) != null } }
    suspend fun recover() = withContext(Dispatchers.IO) { gate.withLock {
        SafTagDocuments.pending(context)?.let { MetadataTransaction(it).recover() }
    } }
    suspend fun load(uri: String): TagEditSnapshot = withContext(Dispatchers.IO) { gate.withLock {
        val store = SafTagDocuments.open(context, uri.toUri())
        verifyLayout(store, store.fileName)
        TagEditSnapshot(store.fileName, properties(store, store.fileName), store.version)
    } }

    suspend fun save(uri: String, snapshot: TagEditSnapshot, fields: Map<String, String>, cover: CoverEdit,
        progress: (EditPhase) -> Unit): TagSaveResult = withContext(Dispatchers.IO) { gate.withLock {
        val store = SafTagDocuments.open(context, uri.toUri())
        check(store.version == snapshot.sourceVersion && properties(store, store.fileName) == snapshot.values) { "SOURCE_CHANGED" }
        val beforePayload = payload(store, store.fileName)
        val beforeProbe = probeAudio(store.fd(store.fileName))
        val expected = editedProperties(snapshot.values, fields)
        val pictures = pictures(store, store.fileName)
        val mp4 = store.fileName.substringAfterLast('.').equals("m4a", true)
        val otherPictures = if (mp4) pictures.drop(1) else pictures.filterNot { it.pictureType == "Front Cover" }
        val changed = when (cover) {
            CoverEdit.Keep -> pictures
            CoverEdit.RemoveFront -> otherPictures.toTypedArray()
            is CoverEdit.Replace -> {
                require(cover.jpeg.size <= 4 * 1024 * 1024)
                arrayOf(Picture(cover.jpeg, "Front Cover", "Front Cover", "image/jpeg")) + otherPictures
            }
        }
        val plan = newEditJournal(store.fileName)
        val coroutine = currentCoroutineContext()
        var committedHash = ""
        val cleanupComplete = MetadataTransaction(store).execute(plan, rewriteAndValidate = { stage ->
            check(properties(store, stage) == snapshot.values && pictures(store, stage).contentEquals(pictures)) { "SOURCE_CHANGED" }
            if (fields.isNotEmpty()) check(native { TagLib.savePropertyMap(store.fd(stage, true).use { it.dup().detachFd() },
                HashMap(expected.mapValues { it.value.toTypedArray() })) }) { "TAG_WRITE_FAILED" }
            if (cover != CoverEdit.Keep) check(native { TagLib.savePictures(store.fd(stage, true).use { it.dup().detachFd() }, changed) }) { "ARTWORK_WRITE_FAILED" }
            check(properties(store, stage) == expected) { "TAG_VERIFY_FAILED" }
            val actualPictures = pictures(store, stage)
            check(if (!mp4) actualPictures.contentEquals(changed) else actualPictures.size == changed.size &&
                actualPictures.indices.all { actualPictures[it].data.contentEquals(changed[it].data) && actualPictures[it].mimeType == changed[it].mimeType }) { "ARTWORK_VERIFY_FAILED" }
            check(payload(store, stage) == beforePayload) { "AUDIO_CHANGED" }
            check(probeAudio(store.fd(stage)) == beforeProbe) { "AUDIO_CHANGED" }
            // Bounded old-tag/art snapshot, never a second retained audio file.
            writeUndo(store.sourceUri.toString(), snapshot.values, pictures)
        }, checkpoint = { coroutine.ensureActive() }, progress = { phase ->
            if (phase == EditPhase.Committed) committedHash = store.journal()?.stageHash.orEmpty()
            progress(phase)
        })
        runCatching { markUndoCommitted(committedHash) } // An undo-snapshot failure must not report a completed file write as failed.
        TagSaveResult(store.uri(store.fileName).toString(), expected["TITLE"]?.firstOrNull()?.takeIf { it.isNotBlank() } ?: store.fileName.substringBeforeLast('.'),
            expected["ARTIST"]?.firstOrNull(), audioArtworkUri(store.uri(store.fileName).toString(), null,
                "edited-${System.currentTimeMillis()}"), !cleanupComplete)
    } }

    private fun verifyLayout(store: SafTagDocuments, name: String) =
        ParcelFileDescriptor.AutoCloseInputStream(store.fd(name)).use { audioLayout(AudioFile(it.channel), name.substringAfterLast('.')) }
    private fun payload(store: SafTagDocuments, name: String) =
        ParcelFileDescriptor.AutoCloseInputStream(store.fd(name)).use { payloadDigest(AudioFile(it.channel), name.substringAfterLast('.')) }
    private fun properties(store: SafTagDocuments, name: String): Map<String, List<String>> {
        verifyLayout(store, name)
        val result = native { store.fd(name).use { TagLib.getMetadata(it.dup().detachFd(), false) } } ?: error("TAG_READ_FAILED")
        val map = result.propertyMap.mapValues { it.value.toList() }
        check(map.size <= 256 && map.values.sumOf { list -> list.sumOf { it.length.toLong() } } <= 512 * 1024) { "TAGS_TOO_LARGE" }
        val audio = native { store.fd(name).use { TagLib.getAudioProperties(it.dup().detachFd()) } } ?: error("AUDIO_INVALID")
        check(audio.length > 0 && audio.sampleRate > 0 && audio.channels in 1..32) { "AUDIO_INVALID" }
        return map
    }
    private fun pictures(store: SafTagDocuments, name: String): Array<Picture> {
        val result = native { store.fd(name).use { TagLib.getPictures(it.dup().detachFd()) } }
        check(result.size <= 16 && result.sumOf { it.data.size.toLong() } <= 8L * 1024 * 1024) { "ARTWORK_TOO_LARGE" }
        return result
    }
    private inline fun <T> native(block: () -> T): T = try { block() }
        catch (error: LinkageError) { throw IllegalStateException("TAG_ENGINE_UNAVAILABLE", error) }

    suspend fun loadCover(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            require(info.size.width > 0 && info.size.height > 0)
            val ratio = minOf(1f, 1024f / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1), (info.size.height * ratio).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output))
                check(output.size() <= 4 * 1024 * 1024) { "ARTWORK_TOO_LARGE" }; output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private fun writeUndo(uri: String, values: Map<String, List<String>>, pictures: Array<Picture>) {
        val root = File(context.noBackupFilesDir, "metadata-undo").apply { mkdirs() }
        val file = AtomicFile(File(root, "latest.json"))
        val tags = JSONObject(); values.forEach { (k, v) -> tags.put(k, JSONArray(v)) }
        val token = java.util.UUID.randomUUID().toString()
        val art = JSONArray(); pictures.forEachIndexed { index, picture ->
            val name = "$token-$index.picture"
            File(root, name).outputStream().use { it.write(picture.data); it.fd.sync() }
            art.put(JSONObject().put("type", picture.pictureType).put("mime", picture.mimeType)
                .put("description", picture.description).put("file", name))
        }
        val json = JSONObject().put("source", uri).put("tags", tags).put("pictures", art).put("committed", false)
        val out = file.startWrite()
        try {
            out.write(json.toString().toByteArray()); file.finishWrite(out)
            root.listFiles()?.filter { it.extension == "picture" && !it.name.startsWith(token) }?.forEach { it.delete() }
        }
        catch (error: Exception) { file.failWrite(out); throw error }
    }

    private fun markUndoCommitted(hash: String) {
        check(hash.length == 64)
        val file = AtomicFile(File(context.noBackupFilesDir, "metadata-undo/latest.json"))
        val json = file.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
            .put("committed", true).put("committedHash", hash)
        val output = file.startWrite()
        try { output.write(json.toString().toByteArray()); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
}
