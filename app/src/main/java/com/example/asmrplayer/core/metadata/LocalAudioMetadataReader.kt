package com.example.asmrplayer.core.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalAudioMetadata(
    val durationMs: Long? = null,
    val artist: String? = null,
    val album: String? = null,
    val title: String? = null,
)

class LocalAudioMetadataReader(private val context: Context) {
    suspend fun read(documentUri: String?): LocalAudioMetadata = withContext(Dispatchers.IO) {
        if (documentUri.isNullOrBlank()) return@withContext LocalAudioMetadata()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(documentUri))
            LocalAudioMetadata(
                durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L },
                artist = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    .cleanMetadataValue(),
                album = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    .cleanMetadataValue(),
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).cleanMetadataValue(),
            )
        } catch (_: RuntimeException) {
            LocalAudioMetadata()
        } finally {
            retriever.release()
        }
    }
}

private fun String?.cleanMetadataValue(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
