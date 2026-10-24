package com.example.asmrplayer.core.annotations

import java.io.*
import org.junit.Assert.*
import org.junit.Test

class PortableAnnotationsTest {
    private fun encode(bytes: ByteArray, password: String = "correct-password"): ByteArray = ByteArrayOutputStream().also { out ->
        PortableAnnotations.encryptedOutput(out, password.toCharArray()).use { it.write(bytes) }
    }.toByteArray()
    private fun decode(bytes: ByteArray, password: String = "correct-password"): ByteArray =
        PortableAnnotations.decryptedInput(bytes.inputStream(), password.toCharArray()).use { it.readBytes() }
    private fun records(items: List<PortableAnnotations.Item>, tags: List<String> = listOf("助眠")): ByteArray = ByteArrayOutputStream().also { out ->
        PortableAnnotations.RecordsWriter(out).apply { tags.forEach(::tag); items.forEach(::item); finish() }
    }.toByteArray()
    @Test fun encryptedRoundTripRetainsUnicodeFoldersAudioZeroAndNull() {
        val items = listOf(
            PortableAnnotations.Item("日本語/作品", "Folder", "作品", "", null, listOf("助眠", "Rain")),
            PortableAnnotations.Item("日本語/作品/01.mp3", "Audio", "曲目", "MP3", 0, listOf("助眠"), "日本語/作品/01.zh.lrc", "Lrc", "zh"),
            PortableAnnotations.Item("other.flac", "Audio", "Other", "FLAC", 90, emptyList()))
        val collected = mutableListOf<PortableAnnotations.Item>(); val tags = mutableListOf<String>()
        PortableAnnotations.decryptedInput(encode(records(items)).inputStream(), "correct-password".toCharArray()).use {
            PortableAnnotations.readRecords(it, tags::add, collected::add)
        }
        assertEquals(items, collected); assertEquals(listOf("助眠"), tags)
    }
    @Test fun encryptsEmptyAndMultipleFramesWithoutWholeFileBuffering() {
        for (size in listOf(0, 1, 65535, 65536, 65537, 140000)) {
            val bytes = ByteArray(size) { (it % 251).toByte() }
            assertArrayEquals(bytes, decode(encode(bytes)))
        }
    }
    @Test fun encryptionUsesFreshSaltAndNonce() {
        val plain = records(emptyList())
        assertFalse(encode(plain).contentEquals(encode(plain)))
    }
    @Test fun wrongPasswordOrModifiedAuthenticatedFramesFail() {
        val encrypted = encode(ByteArray(70000) { 37 })
        assertTrue(runCatching { decode(encrypted, "wrong-password") }.isFailure)
        for (offset in listOf(9, 25, 40, 66000, encrypted.lastIndex)) {
            val bad = encrypted.copyOf(); bad[offset] = (bad[offset].toInt() xor 1).toByte()
            assertTrue("offset $offset", runCatching { decode(bad) }.isFailure)
        }
    }
    @Test fun rejectsTruncationMissingFinalFrameAndTrailingData() {
        val encrypted = encode(ByteArray(70000))
        for (cut in listOf(0, 7, 31, 65588, encrypted.size - 20, encrypted.size - 1))
            assertTrue("cut $cut", runCatching { decode(encrypted.copyOf(cut)) }.isFailure)
        assertTrue(runCatching { decode(encrypted + byteArrayOf(7)) }.isFailure)
    }
    @Test fun rejectsReorderedAndDuplicatedFrames() {
        val encrypted = encode(ByteArray(140000) { (it % 131).toByte() })
        val first = encrypted.copyOfRange(32, 32 + 65556)
        val second = encrypted.copyOfRange(32 + 65556, 32 + 2 * 65556)
        val tail = encrypted.copyOfRange(32 + 2 * 65556, encrypted.size)
        assertTrue(runCatching { decode(encrypted.copyOfRange(0, 32) + second + first + tail) }.isFailure)
        assertTrue(runCatching { decode(encrypted.copyOfRange(0, 32) + first + first + tail) }.isFailure)
    }
    @Test fun rejectsUnsupportedFormatAndAbsurdLengthsBeforeAllocation() {
        assertTrue(runCatching { decode(ByteArray(40)) }.isFailure)
        val data = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeByte(1); writeInt(Int.MAX_VALUE) } }.toByteArray()
        assertTrue(runCatching { PortableAnnotations.readRecords(data.inputStream(), {}, {}) }.isFailure)
        val frame = encode(byteArrayOf(1)).copyOf()
        java.nio.ByteBuffer.wrap(frame, 32, 4).putInt(Int.MAX_VALUE)
        assertTrue(runCatching { decode(frame) }.isFailure)
    }
    @Test fun recordFormatRequiresTerminatorAndNoTrailingBytes() {
        val good = records(emptyList(), emptyList())
        assertTrue(runCatching { PortableAnnotations.readRecords(good.copyOf(0).inputStream(), {}, {}) }.isFailure)
        assertTrue(runCatching { PortableAnnotations.readRecords((good + byteArrayOf(1)).inputStream(), {}, {}) }.isFailure)
        assertTrue(runCatching { PortableAnnotations.readRecords(byteArrayOf(127).inputStream(), {}, {}) }.isFailure)
    }
    @Test fun relativePathsSurviveNewVolumeAndRootLocation() {
        val relative = PortableAnnotations.relative("primary:Old/Music", "primary:Old/Music/作品/a%20b.mp3")
        assertEquals("作品/a%20b.mp3", relative)
        assertEquals("ABCD-1234:New/Music/作品/a%20b.mp3", PortableAnnotations.documentPrefix("ABCD-1234:New/Music") + relative)
        assertEquals("song.mp3", PortableAnnotations.relative("primary:", "primary:song.mp3"))
        assertEquals("", PortableAnnotations.relative("primary:Music", "primary:Music"))
        assertTrue(runCatching { PortableAnnotations.relative("primary:Music", "primary:MusicOther/song.mp3") }.isFailure)
    }
    @Test fun rejectsTraversalAbsoluteBackslashAndExcessiveDepth() {
        for (path in listOf("/a", "../a", "a/../b", "a//b", "a/.", "a/", "C:\\a", "a\u0000b", "a/".repeat(130) + "b"))
            assertTrue(path, runCatching { PortableAnnotations.path(path) }.isFailure)
        assertEquals("a/%2e%2e.mp3", PortableAnnotations.path("a/%2e%2e.mp3")) // literal filename, never decoded twice
    }
    @Test fun importedRecordsRespectTypeScoreAndLyricDirectory() {
        val good = PortableAnnotations.Item("a.mp3", "Audio", "a", "MP3", 90, listOf("Rain"))
        assertEquals(good, PortableAnnotations.itemFromBytes(PortableAnnotations.itemBytes(good)))
        for (bad in listOf(good.copy(score = 101), good.copy(kind = "Other"), good.copy(path = ""),
            good.copy(kind = "Folder"), good.copy(lyricPath = "elsewhere/a.lrc"), good.copy(tags = List(129) { "x" })))
            assertTrue(runCatching { PortableAnnotations.validate(bad) }.isFailure)
    }
    @Test fun mergeIsIdempotentAndKeepsExistingTagSpelling() {
        assertEquals(listOf("Rain", "助眠"), PortableAnnotations.mergeTags(listOf("Rain"), listOf("ＲＡＩＮ", "助眠")))
        assertEquals(List(128) { "tag$it" }, PortableAnnotations.mergeTags(List(128) { "tag$it" }, List(128) { "tag$it" }))
        assertTrue(runCatching { PortableAnnotations.mergeTags(List(128) { "tag$it" }, listOf("extra")) }.isFailure)
    }
}
