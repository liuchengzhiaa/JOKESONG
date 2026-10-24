package com.example.asmrplayer.core.artwork

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

internal const val MaximumFolderCoverBytes = 8L * 1024 * 1024

/** Resolution, not compressed file size, approximates image clarity. No pixel buffer. */
internal data class CoverImageCandidate(val document: CoverDocument, val width: Int, val height: Int) {
    init { require(width in 1..65536 && height in 1..65536) }
    val pixels: Long get() = width.toLong() * height
}

/** Equal resolutions use stable natural-name/URI ordering, independent of traversal. */
internal fun sharperCoverImage(first: CoverImageCandidate?, second: CoverImageCandidate?): CoverImageCandidate? = when {
    first == null -> second
    second == null -> first
    first.pixels > second.pixels -> first
    second.pixels > first.pixels -> second
    CoverDocumentOrder.compare(first.document, second.document) <= 0 -> first
    else -> second
}

/** Unknown provider length is allowed; the eventual writer still enforces the byte cap. */
internal fun folderCoverLengthAllowed(version: String): Boolean =
    (version.substringBefore(':').toLongOrNull() ?: 0L) <= MaximumFolderCoverBytes

/** Even a hostile/malformed image header cannot make bounds probing read without limit. */
internal class CoverImageHeaderStream(input: InputStream, private var remaining: Long = MaximumFolderCoverBytes) : FilterInputStream(input) {
    init { require(remaining >= 0) }
    override fun read(): Int {
        if (remaining == 0L) return -1
        return `in`.read().also { if (it >= 0) remaining-- }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
        if (length == 0) return 0
        if (remaining == 0L) return -1
        return `in`.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            .also { if (it > 0) remaining -= it }
    }
    override fun skip(count: Long): Long = `in`.skip(count.coerceIn(0, remaining))
        .also { remaining -= it }
    override fun available(): Int = minOf(`in`.available().toLong(), remaining).toInt()
    override fun markSupported(): Boolean = false
    override fun mark(readlimit: Int) = Unit
    override fun reset(): Unit = throw IOException("Cover image header stream does not support reset")
}
