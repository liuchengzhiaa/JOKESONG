package com.example.asmrplayer.core.metadata

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

internal const val MaximumTagBytes = 16L * 1024 * 1024
internal data class AudioSpan(val offset: Long, val length: Long)
internal data class AudioLayout(val spans: List<AudioSpan>, val formatBytes: ByteArray = byteArrayOf())

/** Uses the already-granted SAF FD's channel, never a reconstructed raw storage path. */
internal class AudioFile(private val channel: FileChannel) {
    fun length() = channel.size()
    fun seek(at: Long) { channel.position(at) }
    fun read(bytes: ByteArray, offset: Int, count: Int) = channel.read(ByteBuffer.wrap(bytes, offset, count))
    fun readFully(bytes: ByteArray) { val b = ByteBuffer.wrap(bytes); while (b.hasRemaining()) check(channel.read(b) > 0) }
    fun readUnsignedByte() = ByteArray(1).also(::readFully)[0].toInt() and 255
    fun readInt(): Int = ByteBuffer.wrap(ByteArray(4).also(::readFully)).int
    fun readLong(): Long = ByteBuffer.wrap(ByteArray(8).also(::readFully)).long
}

/** Independent container check. All offsets and stream lengths are 64-bit; never decodes audio. */
internal fun audioLayout(file: AudioFile, extension: String): AudioLayout {
    val size = file.length()
    fun text(at: Long, n: Int): String { file.seek(at); return ByteArray(n).also(file::readFully).toString(Charsets.ISO_8859_1) }
    fun uint(at: Long, little: Boolean = false): Long {
        file.seek(at); val v = file.readInt()
        return (if (little) Integer.reverseBytes(v) else v).toLong() and 0xFFFFFFFFL
    }
    fun span(start: Long, end: Long) = AudioSpan(start, end - start).also { require(start >= 0 && end > start && end <= size) }
    return when (extension.lowercase()) {
        "mp3" -> {
            require(size >= 4)
            var start = 0L
            if (size >= 10 && text(0, 3) == "ID3") {
                file.seek(3); val version = file.readUnsignedByte(); file.readUnsignedByte(); val flags = file.readUnsignedByte()
                require(version in 2..4)
                var length = 0L
                repeat(4) { val b = file.readUnsignedByte(); require(b < 128); length = (length shl 7) or b.toLong() }
                start = 10 + length + if (version == 4 && flags and 16 != 0) 10 else 0
                require(start <= MaximumTagBytes && start + 4 <= size)
            }
            var end = size
            if (end >= 128 && text(end - 128, 3) == "TAG") end -= 128
            if (end >= 32 && text(end - 32, 8) == "APETAGEX") {
                val length = uint(end - 20, true)
                require(length in 32..MaximumTagBytes && length <= end - start)
                end -= length
                if (end >= 32 && text(end - 32, 8) == "APETAGEX") end -= 32
            }
            file.seek(start); val h = file.readInt()
            require((h ushr 21) == 0x7FF && (h ushr 19 and 3) != 1 && (h ushr 17 and 3) != 0 &&
                (h ushr 12 and 15) in 1..14 && (h ushr 10 and 3) != 3) { "Invalid MPEG audio header" }
            AudioLayout(listOf(span(start, end)))
        }
        "flac" -> {
            require(size > 42 && text(0, 4) == "fLaC")
            var p = 4L; var last = false; var count = 0
            while (!last) {
                require(++count <= 256 && p + 4 <= size && p <= MaximumTagBytes)
                file.seek(p); val flag = file.readUnsignedByte(); last = flag and 128 != 0
                val length = (file.readUnsignedByte() shl 16) or (file.readUnsignedByte() shl 8) or file.readUnsignedByte()
                if (count == 1) require(flag and 127 == 0 && length == 34)
                p += 4L + length; require(p <= MaximumTagBytes && p < size)
            }
            file.seek(8); val streamInfo = ByteArray(34).also(file::readFully)
            AudioLayout(listOf(span(p, size)), streamInfo)
        }
        "m4a" -> {
            require(size >= 16 && text(4, 4) == "ftyp")
            var p = 0L; var nonAudio = 0L; var count = 0; val spans = mutableListOf<AudioSpan>()
            while (p < size) {
                require(++count <= 4096 && p + 8 <= size)
                val short = uint(p); val type = text(p + 4, 4)
                val header = if (short == 1L) 16L else 8L
                val length = when (short) { 0L -> size - p; 1L -> { file.seek(p + 8); file.readLong() }; else -> short }
                require(length >= header && length <= size - p)
                if (type == "mdat") { require(spans.size < 128); spans += span(p + header, p + length) }
                else { nonAudio += length; require(nonAudio <= MaximumTagBytes) }
                p += length
            }
            require(spans.isNotEmpty()); AudioLayout(spans)
        }
        "wav" -> {
            require(size >= 44 && text(0, 4) == "RIFF" && text(8, 4) == "WAVE") // RF64 deliberately refused.
            require(uint(4, true) + 8 == size)
            var p = 12L; var nonAudio = 12L; var count = 0
            var format = byteArrayOf(); val spans = mutableListOf<AudioSpan>()
            while (p < size) {
                require(++count <= 4096 && p + 8 <= size)
                val type = text(p, 4); val length = uint(p + 4, true)
                require(length <= size - p - 8)
                if (type == "data") { require(spans.size < 128); spans += span(p + 8, p + 8 + length) }
                else {
                    nonAudio += length + 8; require(nonAudio <= MaximumTagBytes)
                    if (type == "fmt ") { require(length in 16..65536); file.seek(p + 8); format = ByteArray(length.toInt()).also(file::readFully) }
                }
                p += 8 + length + (length and 1)
            }
            require(p == size && spans.isNotEmpty() && format.isNotEmpty()); AudioLayout(spans, format)
        }
        else -> error("Unsupported writable container")
    }
}

internal fun payloadDigest(file: AudioFile, extension: String, checkpoint: () -> Unit = {}): String {
    val layout = audioLayout(file, extension)
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(layout.formatBytes)
    val buffer = ByteArray(128 * 1024)
    layout.spans.forEach { part ->
        file.seek(part.offset)
        var remaining = part.length
        while (remaining > 0) {
            checkpoint(); val n = file.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(n > 0); digest.update(buffer, 0, n); remaining -= n
        }
    }
    return digest.digest().hex()
}

internal fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
