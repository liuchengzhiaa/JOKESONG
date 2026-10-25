package com.example.asmrplayer.core.artwork

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal data class CoverPixelSize(val width: Int, val height: Int)

/** Integer arithmetic preserves aspect and never rounds either side to zero. */
internal fun sampledCoverSize(width: Int, height: Int, edge: Int = MaximumPreparedCoverEdge): CoverPixelSize {
    require(width in 1..65536 && height in 1..65536 && edge > 0)
    val longest = maxOf(width, height)
    return if (longest <= edge) CoverPixelSize(width, height) else CoverPixelSize(
        (width.toLong() * edge / longest).toInt().coerceAtLeast(1),
        (height.toLong() * edge / longest).toInt().coerceAtLeast(1),
    )
}
internal fun canCopyCoverUnchanged(bytes: Long, width: Int, height: Int): Boolean =
    bytes in 1..MaximumFolderCoverBytes && width in 1..MaximumPreparedCoverEdge && height in 1..MaximumPreparedCoverEdge

/** Streams to private disk only. Does not close either caller-owned stream. */
internal fun copyCoverSource(input: InputStream, output: OutputStream, checkpoint: () -> Unit = {},
    limit: Long = MaximumCoverSourceBytes): Long {
    require(limit >= 0)
    val buffer = ByteArray(32 * 1024)
    var total = 0L
    while (true) {
        checkpoint()
        val count = input.read(buffer)
        if (count < 0) return total
        check(count.toLong() <= limit - total) { "COVER_SOURCE_TOO_LARGE" }
        output.write(buffer, 0, count)
        total += count
    }
}

/** Native encoders may turn an IOException into false; keep an explicit overflow flag. */
internal class CoverEncodedOutput(private val output: OutputStream, private val limit: Long = MaximumFolderCoverBytes) : OutputStream() {
    var exceeded = false; private set
    private var written = 0L
    init { require(limit >= 0) }
    private fun reserve(count: Int) {
        if (count.toLong() > limit - written) { exceeded = true; throw IOException("COVER_ENCODED_LIMIT") }
        written += count
    }
    override fun write(value: Int) { reserve(1); output.write(value) }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        reserve(length); output.write(bytes, offset, length)
    }
    override fun flush() = output.flush()
    override fun close() = output.close()
}
