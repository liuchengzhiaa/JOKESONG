package com.example.asmrplayer.core.metadata

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.*

class AudioPayloadTest {
    private fun <T> sample(bytes: ByteArray, block: (AudioFile) -> T): T {
        val file = File.createTempFile("tag-fixture", ".bin")
        try { file.writeBytes(bytes); return RandomAccessFile(file, "r").use { block(AudioFile(it.channel)) } }
        finally { file.delete() }
    }
    private val audio = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x44) + ByteArray(1024) { (it % 200).toByte() }
    private fun id3(body: String): ByteArray {
        val bytes = body.toByteArray()
        return byteArrayOf(73, 68, 51, 4, 0, 0, 0, 0, 0, bytes.size.toByte()) + bytes
    }
    @Test fun mp3TagChangesDoNotChangeAudioDigest() {
        val old = sample(id3("old") + audio) { payloadDigest(it, "mp3") }
        val fresh = sample(id3("new title longer") + audio + "TAG".toByteArray() + ByteArray(125)) { payloadDigest(it, "mp3") }
        assertEquals(old, fresh)
    }
    @Test fun changedAudioIsDetected() {
        val old = sample(audio) { payloadDigest(it, "mp3") }
        assertNotEquals(old, sample(audio + byteArrayOf(9)) { payloadDigest(it, "mp3") })
    }
    @Test fun invalidHeaderAndMalformedLengthsAreRejected() {
        try { sample(ByteArray(50)) { audioLayout(it, "mp3") }; fail() } catch (_: IllegalArgumentException) { }
        try { sample("fLaC".toByteArray() + byteArrayOf(0x80.toByte(), 0, 0, 127) + ByteArray(40)) { audioLayout(it, "flac") }; fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun flacMetadataChangesButStreamInfoAndAudioStayTheSame() {
        fun flac(comment: ByteArray): ByteArray = "fLaC".toByteArray() + byteArrayOf(0, 0, 0, 34) + ByteArray(34) +
            byteArrayOf(0x84.toByte(), 0, 0, comment.size.toByte()) + comment + audio
        assertEquals(sample(flac("old".toByteArray())) { payloadDigest(it, "flac") },
            sample(flac("new comment".toByteArray())) { payloadDigest(it, "flac") })
    }
    @Test fun mp4AudioOffsetsCanMoveWithoutChangingDigest() {
        fun box(type: String, data: ByteArray) = ByteBuffer.allocate(data.size + 8).putInt(data.size + 8).put(type.toByteArray()).put(data).array()
        val ftyp = box("ftyp", "M4A ".toByteArray())
        val a = ftyp + box("moov", byteArrayOf(1)) + box("mdat", audio)
        val b = ftyp + box("moov", ByteArray(20)) + box("mdat", audio)
        assertEquals(sample(a) { payloadDigest(it, "m4a") }, sample(b) { payloadDigest(it, "m4a") })
    }
    @Test fun wavMetadataAndOddPaddingDoNotChangePcmButFormatChangesDo() {
        fun chunk(type: String, data: ByteArray): ByteArray = ByteBuffer.allocate(data.size + 8)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).put(type.toByteArray()).putInt(data.size).put(data).array() +
            if (data.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
        fun wav(rate: Int, comment: String): ByteArray {
            val format = ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16).array()
            val chunks = chunk("fmt ", format) + chunk("LIST", comment.toByteArray()) + chunk("data", audio)
            return ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN).put("RIFF".toByteArray())
                .putInt(chunks.size + 4).put("WAVE".toByteArray()).array() + chunks
        }
        val before = sample(wav(8000, "old")) { payloadDigest(it, "wav") }
        assertEquals(before, sample(wav(8000, "a longer comment")) { payloadDigest(it, "wav") })
        assertNotEquals(before, sample(wav(16000, "old")) { payloadDigest(it, "wav") })
        val broken = wav(8000, "old").apply { this[4] = 0; this[5] = 0; this[6] = 0; this[7] = 0 }
        assertThrows(IllegalArgumentException::class.java) { sample(broken) { audioLayout(it, "wav") } }
    }
    @Test fun fourGiBAudioLengthDoesNotOverflowOrReadPayloadEagerly() {
        val length = 4L * 1024 * 1024 * 1024
        val header = ByteBuffer.allocate(40).putInt(24).put("ftyp".toByteArray()).put(ByteArray(16))
            .putInt(1).put("mdat".toByteArray()).putLong(length + 16).array()
        val channel = object : FileChannel() {
            var cursor = 0L
            override fun size() = length + header.size
            override fun position() = cursor
            override fun position(newPosition: Long): FileChannel { cursor = newPosition; return this }
            override fun read(dst: ByteBuffer): Int {
                check(cursor < header.size) { "Audio payload must not be loaded for a layout probe" }
                val n = minOf(dst.remaining(), header.size - cursor.toInt()); dst.put(header, cursor.toInt(), n); cursor += n; return n
            }
            override fun implCloseChannel() { }
            override fun read(dsts: Array<out ByteBuffer>?, offset: Int, length: Int): Long = error("unused")
            override fun read(dst: ByteBuffer?, position: Long): Int = error("unused")
            override fun write(src: ByteBuffer?): Int = error("unused")
            override fun write(srcs: Array<out ByteBuffer>?, offset: Int, length: Int): Long = error("unused")
            override fun write(src: ByteBuffer?, position: Long): Int = error("unused")
            override fun truncate(size: Long): FileChannel = error("unused")
            override fun force(metaData: Boolean) { }
            override fun transferTo(position: Long, count: Long, target: WritableByteChannel?): Long = error("unused")
            override fun transferFrom(src: ReadableByteChannel?, position: Long, count: Long): Long = error("unused")
            override fun map(mode: MapMode?, position: Long, size: Long): java.nio.MappedByteBuffer = error("unused")
            override fun lock(position: Long, size: Long, shared: Boolean): FileLock = error("unused")
            override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock = error("unused")
        }
        assertEquals(length, audioLayout(AudioFile(channel), "m4a").spans.single().length)
    }
    @Test fun oversizedTagIsRefusedWithoutAllocatingItsBody() {
        val bytes = byteArrayOf(73, 68, 51, 4, 0, 0, 0x7F, 0x7F, 0x7F, 0x7F)
        try { sample(bytes) { audioLayout(it, "mp3") }; fail() } catch (_: IllegalArgumentException) { }
    }
}
