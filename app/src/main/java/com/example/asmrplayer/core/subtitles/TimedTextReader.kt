package com.example.asmrplayer.core.subtitles

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a timed-text file's text through the document provider.
 *
 * The read is bounded. A lyric file is a few kilobytes and this app is not going to
 * load an arbitrary file the user's storage happens to hold under a `.lrc` name into
 * memory to find that out.
 */
class TimedTextReader(
    private val contentResolver: ContentResolver,
    private val maximumBytes: Int = MaximumTimedTextBytes,
) {
    /** Returns the decoded text, or null when the document cannot be read at all. */
    suspend fun read(documentUri: Uri): String? = withContext(Dispatchers.IO) {
        val bytes = try {
            contentResolver.openInputStream(documentUri)?.use { it.readAtMost(maximumBytes) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            // A persisted grant can be revoked between sessions; the caller shows the
            // same "no timed text" state it shows for a file that is not there.
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        if (bytes == null || bytes.isEmpty()) return@withContext null
        decodeTimedText(bytes)
    }
}

/** 2 MiB: generous for a long track's lyrics, far below anything that would hurt. */
const val MaximumTimedTextBytes: Int = 2 * 1024 * 1024

private fun InputStream.readAtMost(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var total = 0
    while (total < limit) {
        val read = read(buffer, total, limit - total)
        if (read < 0) break
        total += read
    }
    return buffer.copyOf(total)
}

/**
 * Decodes a lyric file whose encoding the provider does not tell us.
 *
 * Android reports these as `text/plain` regardless, and a Chinese-shaped library
 * contains both encodings in practice: modern files are UTF-8, and files written by
 * older Windows tools are GBK. UTF-8 is tried strictly first, because a byte sequence
 * that is valid UTF-8 and valid GBK at once is overwhelmingly more likely to be the
 * UTF-8 the author saved, and GBK would turn it into different characters rather than
 * failing loudly.
 *
 * A file cut off by the read bound can end mid-character, so the last attempt decodes
 * with malformed input replaced instead of throwing: losing the final character of an
 * oversized file is better than showing nothing.
 */
internal fun decodeTimedText(bytes: ByteArray): String {
    val body = bytes.withoutByteOrderMark()
    decodeStrictly(body, Charsets.UTF_8)?.let { return it }
    ChineseFallback?.let { fallback -> decodeStrictly(body, fallback)?.let { return it } }
    return String(body, Charsets.UTF_8)
}

private fun decodeStrictly(bytes: ByteArray, charset: Charset): String? = try {
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    null
}

/**
 * GBK when the runtime has it, which Android and the JDK both do. Null rather than an
 * exception when it does not, so a missing charset degrades to UTF-8 instead of
 * failing the read.
 */
private val ChineseFallback: Charset? = runCatching { Charset.forName("GBK") }.getOrNull()

private val Utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
private val Utf16LittleEndianBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
private val Utf16BigEndianBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

/**
 * Drops a byte-order mark and decodes what it says about the rest of the file.
 *
 * Windows Notepad's "Unicode" option writes UTF-16LE, which is a plausible thing to
 * find beside a track on this owner's storage, and its BOM is the only way to know.
 * The UTF-8 BOM is simply removed; UTF-8 is the default.
 */
private fun ByteArray.withoutByteOrderMark(): ByteArray = when {
    startsWith(Utf16LittleEndianBom) -> decodeAndReencode(Charsets.UTF_16LE, drop = 2)
    startsWith(Utf16BigEndianBom) -> decodeAndReencode(Charsets.UTF_16BE, drop = 2)
    startsWith(Utf8Bom) -> copyOfRange(Utf8Bom.size, size)
    else -> this
}

private fun ByteArray.decodeAndReencode(charset: Charset, drop: Int): ByteArray =
    String(copyOfRange(drop, size), charset).toByteArray(Charsets.UTF_8)

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    return prefix.indices.all { this[it] == prefix[it] }
}
