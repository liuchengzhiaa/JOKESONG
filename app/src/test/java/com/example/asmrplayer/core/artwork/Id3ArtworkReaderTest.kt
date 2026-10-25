package com.example.asmrplayer.core.artwork

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class Id3ArtworkReaderTest {
    private fun number(n: Int, sync: Boolean = false): ByteArray = if (!sync) ByteBuffer.allocate(4).putInt(n).array()
        else byteArrayOf((n ushr 21 and 127).toByte(), (n ushr 14 and 127).toByte(), (n ushr 7 and 127).toByte(), (n and 127).toByte())
    private fun frame(body: ByteArray, version: Int = 3, name: String = "APIC", flags: Int = 0) =
        name.toByteArray() + number(body.size, version == 4) + byteArrayOf(0, flags.toByte()) + body
    private fun picture(data: ByteArray, type: Int = 3, encoding: Int = 0, description: ByteArray = byteArrayOf(0)) =
        byteArrayOf(encoding.toByte()) + "image/png".toByteArray() + byteArrayOf(0, type.toByte()) + description + data
    private fun tag(body: ByteArray, version: Int = 3, flags: Int = 0) =
        "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, flags.toByte()) + number(body.size, true) + body
    private fun read(data: ByteArray, limit: Long = MaximumCoverSourceBytes, checkpoint: () -> Unit = {}): Pair<Boolean?, ByteArray> {
        val file = File.createTempFile("generated-id3-", ".mp3")
        try {
            file.writeBytes(data)
            val output = ByteArrayOutputStream()
            val result = RandomAccessFile(file, "r").use { copyId3Artwork(it.channel, output, checkpoint, limit) }
            assertArrayEquals(data, file.readBytes())
            return result to output.toByteArray()
        } finally { file.delete() }
    }
    private val art = byteArrayOf(1, 2, 3, 4, 5)
    @Test fun `v23 picture copies only exact APIC bytes not following audio`() {
        val result = read(tag(frame(picture(art))) + ByteArray(100))
        assertEquals(true, result.first); assertArrayEquals(art, result.second)
    }
    @Test fun `v24 syncsafe frame size and UTF8 description are supported`() {
        val large = ByteArray(1000) { (it % 251).toByte() }
        val result = read(tag(frame(picture(large, encoding = 3, description = "hello\u0000".toByteArray()), 4), 4))
        assertEquals(true, result.first); assertArrayEquals(large, result.second)
    }
    @Test fun `metadata is skipped and front cover preferred over earlier back cover`() {
        val body = frame(ByteArray(100), name = "TIT2") + frame(picture(byteArrayOf(9), type = 4)) + frame(picture(art))
        assertArrayEquals(art, read(tag(body)).second)
    }
    @Test fun `first other picture is retained if no front cover exists`() {
        val body = frame(picture(art, type = 4)) + frame(picture(byteArrayOf(9), type = 8))
        assertArrayEquals(art, read(tag(body)).second)
    }
    @Test fun `UTF16 descriptions use aligned two byte terminators`() {
        for (encoding in listOf(1,2)) {
            val result = read(tag(frame(picture(art, encoding = encoding, description = byteArrayOf(-1,-2,65,0,0,0)))))
            assertEquals(true, result.first); assertArrayEquals(art, result.second)
        }
    }
    @Test fun `v23 and v24 extended headers skip their respective length formats`() {
        val a = read(tag(number(6) + ByteArray(6) + frame(picture(art)), flags = 64))
        val b = read(tag(number(6, true) + ByteArray(2) + frame(picture(art), 4), 4, 64))
        assertArrayEquals(art, a.second); assertArrayEquals(art, b.second)
    }
    @Test fun `other formats old tags and encoded frames retain native fallback`() {
        assertNull(read(ByteArray(20)).first)
        assertNull(read(tag(ByteArray(20), version = 2)).first)
        assertNull(read(tag(frame(picture(art)), flags = 128)).first)
        assertNull(read(tag(frame(picture(art), flags = 128))).first)
    }
    @Test fun `tags without a directly readable picture retain native fallback`() {
        assertNull(read(tag(frame(ByteArray(12), name = "TIT2"))).first)
        assertNull(read(tag(ByteArray(20))).first)
        assertNull(read(tag(frame(picture(art, description = ByteArray(5000) { 1 })))).first)
    }
    @Test fun `oversize and truncated tag or frame are rejected before copying`() {
        val source = tag(frame(picture(art)))
        assertEquals(false, read(source, limit = 8).first)
        assertEquals(false, read(source.copyOf(source.size - 1)).first)
        val bad = frame(picture(art)).also { number(1000).copyInto(it, 4) }
        assertEquals(false, read(tag(bad)).first)
    }
    @Test fun `invalid syncsafe tag size is rejected and cancellation escapes`() {
        val source = tag(frame(picture(art))).also { it[6] = -1 }
        assertEquals(false, read(source).first)
        try { read(tag(frame(picture(art))), checkpoint = { throw CancellationException() }); fail() }
        catch (_: CancellationException) { }
    }
}
