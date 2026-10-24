package com.example.asmrplayer.core.annotations

import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned, password-encrypted, bounded streaming format; never Java deserialization. */
object PortableAnnotations {
    const val MaxItems = 50_000
    const val MaxTags = 20_000
    const val MaxBytes = 128L * 1024 * 1024
    private const val ChunkSize = 64 * 1024
    private val Magic = "JKTAGS01".toByteArray(StandardCharsets.US_ASCII)

    data class Item(val path: String, val kind: String, val title: String, val subtitle: String,
        val score: Int?, val tags: List<String>, val lyricPath: String? = null,
        val lyricFormat: String? = null, val lyricLanguage: String? = null)

    fun path(raw: String): String {
        require(raw.length <= 8192 && raw.none { it == '\\' || it == '\u0000' || Character.isISOControl(it) })
        require(raw.count { it == '/' } < 128)
        require(raw.isEmpty() || raw.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
        return raw
    }
    fun relative(rootId: String, documentId: String): String = when {
        documentId == rootId -> ""
        documentId.startsWith(documentPrefix(rootId)) -> path(documentId.substring(documentPrefix(rootId).length))
        else -> throw IllegalArgumentException("Document is not beneath root")
    }
    fun documentPrefix(rootId: String): String = rootId.trimEnd('/').let { if (it.endsWith(':')) it else "$it/" }
    fun mergeTags(existing: List<String>, incoming: List<String>): List<String> =
        (existing + incoming).map(AnnotationPolicy::tagName).distinctBy(AnnotationPolicy::tagKey)
            .also { require(it.size <= AnnotationPolicy.MaxTagsPerItem) }

    fun validate(item: Item): Item {
        path(item.path)
        require(item.kind in listOf("Audio", "Folder") && (item.kind == "Folder" || item.path.isNotEmpty()))
        require(item.score == null || (item.kind == "Audio" && item.score in 0..100))
        require(item.tags.size <= AnnotationPolicy.MaxTagsPerItem)
        item.tags.forEach(AnnotationPolicy::tagName)
        item.lyricPath?.let {
            path(it)
            require(item.kind == "Audio" && it.isNotEmpty() && it.substringBeforeLast('/', "") == item.path.substringBeforeLast('/', ""))
        }
        return item
    }

    class RecordsWriter(output: OutputStream) {
        private val out = DataOutputStream(output)
        private var tags = 0
        private var items = 0
        fun tag(name: String) { require(++tags <= MaxTags); out.writeByte(1); text(out, AnnotationPolicy.tagName(name), 160) }
        fun item(item: Item) { require(++items <= MaxItems); out.writeByte(2); writeItem(out, validate(item)) }
        fun finish() { out.writeByte(0); out.flush() }
    }
    fun readRecords(input: InputStream, onTag: (String) -> Unit, onItem: (Item) -> Unit) {
        val data = DataInputStream(input)
        var tags = 0; var items = 0
        while (true) when (data.readUnsignedByte()) {
            0 -> { require(data.read() == -1) { "Trailing backup data" }; return }
            1 -> { require(++tags <= MaxTags); onTag(AnnotationPolicy.tagName(text(data, 160))) }
            2 -> { require(++items <= MaxItems); onItem(readItem(data)) }
            else -> throw IOException("Unknown backup record")
        }
    }
    fun itemBytes(item: Item): ByteArray = ByteArrayOutputStream().also { writeItem(DataOutputStream(it), validate(item)) }.toByteArray()
    fun itemFromBytes(bytes: ByteArray): Item = DataInputStream(bytes.inputStream()).use { input ->
        readItem(input).also { require(input.read() == -1) }
    }
    private fun writeItem(out: DataOutputStream, item: Item) {
        text(out, item.path, 32768); text(out, item.kind, 16); text(out, item.title, 4096); text(out, item.subtitle, 256)
        out.writeInt(item.score ?: -1); out.writeInt(item.tags.size)
        item.tags.forEach { text(out, AnnotationPolicy.tagName(it), 160) }
        text(out, item.lyricPath.orEmpty(), 32768); text(out, item.lyricFormat.orEmpty(), 16)
        text(out, item.lyricLanguage.orEmpty(), 64)
    }
    private fun readItem(input: DataInputStream): Item {
        val path = text(input, 32768); val kind = text(input, 16); val title = text(input, 4096); val subtitle = text(input, 256)
        val score = input.readInt().also { require(it in -1..100) }.takeIf { it >= 0 }
        val count = input.readInt().also { require(it in 0..AnnotationPolicy.MaxTagsPerItem) }
        val tags = List(count) { AnnotationPolicy.tagName(text(input, 160)) }
        return validate(Item(path, kind, title, subtitle, score, tags, text(input, 32768).ifEmpty { null },
            text(input, 16).ifEmpty { null }, text(input, 64).ifEmpty { null }))
    }
    private fun text(out: DataOutputStream, value: String, max: Int) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8); require(bytes.size <= max)
        out.writeInt(bytes.size); out.write(bytes)
    }
    private fun text(input: DataInputStream, max: Int): String {
        val bytes = ByteArray(input.readInt().also { require(it in 0..max) }); input.readFully(bytes)
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }

    /** Each 64KiB frame is authenticated independently; mandatory final frame rejects truncation. */
    fun encryptedOutput(output: OutputStream, password: CharArray): OutputStream {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(8).also(SecureRandom()::nextBytes)
        val header = Magic + salt + nonce
        val key = key(password, salt)
        output.write(header)
        return object : OutputStream() {
            private val out = DataOutputStream(output)
            private val buffer = ByteArray(ChunkSize)
            private var used = 0; private var frame = 0; private var total = 0L; private var closed = false
            private fun emit(length: Int) {
                val encrypted = crypt(Cipher.ENCRYPT_MODE, key, header, nonce, frame++, buffer.copyOf(length))
                out.writeInt(length); out.write(encrypted)
            }
            override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                check(!closed); require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
                total += length; require(total <= MaxBytes)
                var pos = offset; var left = length
                while (left > 0) {
                    val count = minOf(left, ChunkSize - used)
                    bytes.copyInto(buffer, used, pos, pos + count); used += count; pos += count; left -= count
                    if (used == ChunkSize) { emit(used); used = 0 }
                }
            }
            override fun close() {
                if (closed) return
                try { if (used > 0) emit(used); emit(0); out.flush() }
                finally { closed = true; buffer.fill(0); key.fill(0); out.close() }
            }
        }
    }
    fun decryptedInput(input: InputStream, password: CharArray): InputStream {
        val data = DataInputStream(input)
        val header = ByteArray(32); data.readFully(header)
        require(header.copyOfRange(0, 8).contentEquals(Magic)) { "Unsupported backup version" }
        val nonce = header.copyOfRange(24, 32)
        val key = key(password, header.copyOfRange(8, 24))
        return object : InputStream() {
            private var buffer = ByteArray(0); private var pos = 0; private var frame = 0
            private var total = 0L; private var ended = false
            private fun next(): Boolean {
                if (ended) return false
                val length = data.readInt().also { require(it in 0..ChunkSize) }
                total += length; require(total <= MaxBytes)
                val encrypted = ByteArray(length + 16); data.readFully(encrypted)
                buffer.fill(0)
                buffer = crypt(Cipher.DECRYPT_MODE, key, header, nonce, frame++, encrypted, length)
                pos = 0
                if (length == 0) { ended = true; require(data.read() == -1) { "Trailing encrypted data" }; return false }
                return true
            }
            override fun read(): Int = if (pos == buffer.size && !next()) -1 else buffer[pos++].toInt() and 255
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
                if (length == 0) return 0
                if (pos == buffer.size && !next()) return -1
                val count = minOf(length, buffer.size - pos); buffer.copyInto(bytes, offset, pos, pos + count); pos += count
                return count
            }
            override fun close() { buffer.fill(0); key.fill(0); data.close() }
        }
    }
    private fun key(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun crypt(mode: Int, key: ByteArray, header: ByteArray, prefix: ByteArray, index: Int,
        bytes: ByteArray, plainLength: Int = bytes.size): ByteArray {
        val frame = ByteBuffer.allocate(8).putInt(index).putInt(plainLength).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, prefix + ByteBuffer.allocate(4).putInt(index).array()))
        cipher.updateAAD(header + frame)
        return cipher.doFinal(bytes)
    }
}
