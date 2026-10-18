package com.example.asmrplayer.core.metadata

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import java.nio.ByteBuffer
import java.security.MessageDigest

/** A second parser validates codec/seekability and first/last packets, independent of TagLib. */
internal data class AudioProbe(val mime: String, val rate: Int, val channels: Int, val packets: List<String>, val codec: List<String>)
internal fun probeAudio(fd: ParcelFileDescriptor): AudioProbe = fd.use {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(fd.fileDescriptor)
        // V1 does not verify DRM auxiliary offsets or additional track sample tables.
        // Refuse them instead of claiming that one audio track proves the whole file safe.
        check(extractor.trackCount == 1 && extractor.drmInitData == null) { "UNSUPPORTED_FORMAT" }
        val index = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("AUDIO_INVALID")
        val format = extractor.getTrackFormat(index)
        extractor.selectTrack(index)
        val buffer = ByteBuffer.allocate(1024 * 1024)
        fun packet(): String {
            check(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "UNSUPPORTED_FORMAT" }
            val size = extractor.sampleSize
            check(size in 1..buffer.capacity().toLong()) { "AUDIO_INVALID" }
            buffer.clear()
            val n = extractor.readSampleData(buffer, 0); check(n > 0 && n == size.toInt()) { "AUDIO_INVALID" }
            return MessageDigest.getInstance("SHA-256").digest(buffer.array().copyOf(n)).hex()
        }
        val first = packet()
        val packets = mutableListOf(first)
        if (format.containsKey(MediaFormat.KEY_DURATION)) {
            extractor.seekTo((format.getLong(MediaFormat.KEY_DURATION) - 1).coerceAtLeast(0), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            if (extractor.sampleSize > 0) packets += packet()
        }
        val codec = (0..2).mapNotNull { n ->
            // FLAC STREAMINFO is already hashed independently; its last-metadata-block
            // flag may legitimately change when embedding artwork.
            if (format.getString(MediaFormat.KEY_MIME) == "audio/flac" || !format.containsKey("csd-$n")) null
            else format.getByteBuffer("csd-$n")?.duplicate()?.let { bytes ->
                check(bytes.remaining() <= 65536) { "AUDIO_INVALID" }
                val data = ByteArray(bytes.remaining()); bytes.get(data)
                MessageDigest.getInstance("SHA-256").digest(data).hex()
            }
        }
        AudioProbe(format.getString(MediaFormat.KEY_MIME).orEmpty(), format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), packets, codec)
    } finally { extractor.release() }
}
