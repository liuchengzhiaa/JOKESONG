package com.example.asmrplayer.core.subtitles

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The encoding half of reading a lyric file.
 *
 * Only [decodeTimedText] is exercised; reading the bytes needs a content resolver, and
 * what can go wrong there is a missing file rather than a wrong character.
 */
class TimedTextDecodingTest {

    private val sample = "[00:01.00]第一句\n[00:05.00]第二句\n"

    @Test
    fun `utf8 with no mark is the default`() {
        assertEquals(sample, decodeTimedText(sample.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `a utf8 mark is dropped rather than shown`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            sample.toByteArray(Charsets.UTF_8)

        assertEquals(sample, decodeTimedText(bytes))
    }

    @Test
    fun `gbk is decoded when the bytes are not valid utf8`() {
        // "中文" in GBK. Not valid UTF-8, which is exactly what makes it a fallback
        // candidate rather than a file the strict UTF-8 pass would have mangled.
        val bytes = byteArrayOf(0xD6.toByte(), 0xD0.toByte(), 0xCE.toByte(), 0xC4.toByte())

        assertEquals("中文", decodeTimedText(bytes))
    }

    @Test
    fun `utf16 little endian is decoded through its mark`() {
        // What Notepad's "Unicode" option writes, which is a plausible thing to find
        // beside a track on this owner's storage.
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            sample.toByteArray(Charset.forName("UTF-16LE"))

        assertEquals(sample, decodeTimedText(bytes))
    }

    @Test
    fun `utf16 big endian is decoded through its mark`() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            sample.toByteArray(Charset.forName("UTF-16BE"))

        assertEquals(sample, decodeTimedText(bytes))
    }

    @Test
    fun `bytes in neither encoding are replaced rather than throwing`() {
        // 0xFF is not a UTF-8 sequence and not a GBK lead byte, so both strict passes
        // fail. Returning nothing would lose a file that is mostly readable, so the
        // final pass substitutes instead.
        val bytes = "abc".toByteArray(Charsets.UTF_8) +
            byteArrayOf(0xFF.toByte()) +
            "def".toByteArray(Charsets.UTF_8)

        val text = decodeTimedText(bytes)
        assertTrue(text, text.startsWith("abc"))
        assertTrue(text, text.endsWith("def"))
        assertTrue(text, text.contains('�'))
    }

    @Test
    fun `an empty file decodes to empty text`() {
        assertEquals("", decodeTimedText(ByteArray(0)))
    }
}
