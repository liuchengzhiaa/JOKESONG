package com.example.asmrplayer.core.artwork

import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** true=picture copied, false=unsafe/truncated metadata, null=use native fallback. */
internal fun copyId3Artwork(channel: FileChannel, output: OutputStream, checkpoint: () -> Unit = {},
    maxTagBytes: Long = MaximumCoverSourceBytes): Boolean? {
    require(maxTagBytes > 0)
    val length = channel.size()
    fun bytes(at: Long, count: Int): ByteArray {
        if (at < 0 || count < 0 || at > length - count) throw IOException("ID3_TRUNCATED")
        checkpoint()
        val buffer = ByteBuffer.allocate(count)
        var offset = at
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, offset)
            if (n <= 0) throw IOException("ID3_TRUNCATED")
            offset += n
        }
        return buffer.array()
    }
    fun number(b: ByteArray, start: Int, sync: Boolean): Long? {
        var result = 0L
        for (i in start until start + 4) {
            val v = b[i].toInt() and 255
            if (sync && v > 127) return null
            result = (result shl if (sync) 7 else 8) or v.toLong()
        }
        return result
    }
    if (length < 10) return null
    val header = bytes(0, 10)
    if (header.copyOfRange(0, 3).toString(Charsets.ISO_8859_1) != "ID3") return null
    val version = header[3].toInt() and 255
    if (version !in 3..4) return null
    val flags = header[5].toInt() and 255
    val tagSize = number(header, 6, true) ?: return false
    if (tagSize > maxTagBytes || tagSize > length - 10) return false
    if (flags and 0x80 != 0) return null // Unsynchronised tags retain the native fallback.
    val end = 10 + tagSize
    var position = 10L
    if (flags and 0x40 != 0) {
        if (end - position < 4) return false
        val extended = number(bytes(position, 4), 0, version == 4) ?: return false
        val skip = extended + if (version == 3) 4 else 0
        if (skip < 6 || skip > end - position) return false
        position += skip
    }
    data class Picture(val offset: Long, val size: Long, val front: Boolean)
    fun picture(start: Long, size: Long): Picture? {
        val stop = start + size
        var at = start
        if (size < 4) return null
        val encoding = bytes(at++, 1)[0].toInt() and 255
        if (encoding !in 0..3) return null
        val mime = StringBuilder()
        var terminated = false
        while (at < stop && mime.length < 256) {
            val c = bytes(at++, 1)[0].toInt() and 255
            if (c == 0) { terminated = true; break }
            mime.append(c.toChar())
        }
        if (!terminated || mime.isEmpty() || mime.toString() == "-->" || at >= stop) return null
        val type = bytes(at++, 1)[0].toInt() and 255
        val step = if (encoding == 1 || encoding == 2) 2 else 1
        var descriptionBytes = 0
        terminated = false
        while (at <= stop - step && descriptionBytes < 4096) {
            val part = bytes(at, step); at += step; descriptionBytes += step
            if (part.all { it == 0.toByte() }) { terminated = true; break }
        }
        if (!terminated || at >= stop) return null
        return Picture(at, stop - at, type == 3)
    }
    var chosen: Picture? = null
    var frames = 0
    while (position <= end - 10) {
        checkpoint()
        if (++frames > 4096) return false
        val frame = bytes(position, 10)
        if (frame.take(4).all { it == 0.toByte() }) break
        if (!frame.take(4).all { it.toInt().toChar() in 'A'..'Z' || it.toInt().toChar() in '0'..'9' }) return false
        val size = number(frame, 4, version == 4) ?: return false
        position += 10
        if (size > end - position) return false
        if (frame.copyOfRange(0, 4).toString(Charsets.ISO_8859_1) == "APIC") {
            val formatFlags = frame[9].toInt() and 255
            if (formatFlags and (if (version == 3) 0xE0 else 0x4F) == 0) {
                val candidate = picture(position, size)
                if (candidate != null && (chosen == null || candidate.front)) chosen = candidate
                if (chosen?.front == true) break
            }
        }
        position += size
    }
    val selected = chosen ?: return null
    if (selected.size > MaximumCoverSourceBytes) return false
    var at = selected.offset
    var remaining = selected.size
    val buffer = ByteBuffer.allocate(32 * 1024)
    while (remaining > 0) {
        checkpoint()
        buffer.clear(); buffer.limit(minOf(buffer.capacity().toLong(), remaining).toInt())
        val n = channel.read(buffer, at)
        if (n <= 0) throw IOException("ID3_TRUNCATED")
        output.write(buffer.array(), 0, n)
        at += n; remaining -= n
    }
    return true
}
