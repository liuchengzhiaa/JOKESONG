package com.example.asmrplayer.core.annotations

import org.junit.Assert.*
import org.junit.Test
import com.example.asmrplayer.core.model.*
import com.example.asmrplayer.core.subtitles.*

class AnnotationPolicyTest {
    @Test fun normalizedTagsShareIdentity() {
        assertEquals("Rain Sound", AnnotationPolicy.tagName("  Ｒａｉｎ   Sound  "))
        assertEquals(AnnotationPolicy.tagKey("RAIN"), AnnotationPolicy.tagKey("rain"))
        assertEquals("助眠", AnnotationPolicy.tagName("助眠"))
    }
    @Test fun labelsAreBoundedByCodePoints() {
        assertEquals("🌙".repeat(40), AnnotationPolicy.tagName("🌙".repeat(40)))
        for (bad in listOf("", "  ", "a".repeat(41), "a\u0000b")) {
            assertTrue(runCatching { AnnotationPolicy.tagName(bad) }.isFailure)
        }
    }
    @Test fun scoreDistinguishesZeroFromUnrated() {
        assertNull(AnnotationPolicy.score("")); assertNull(AnnotationPolicy.score(" "))
        for (n in 0..100) assertEquals(n, AnnotationPolicy.score(n.toString()))
        assertEquals(90, AnnotationPolicy.score(" 90 "))
    }
    @Test fun rejectsFractionalNegativeAndOutOfRangeScores() {
        for (bad in listOf("-1", "101", "90.5", "９０", "+90", "NaN", "1e2", "999999999999999"))
            assertTrue(bad, runCatching { AnnotationPolicy.score(bad) }.isFailure)
    }
    @Test fun searchWildcardsAreLiteral() {
        assertEquals("%a\\%b\\_c\\\\d%", AnnotationPolicy.containsPattern("a%b_c\\d"))
        assertEquals(102, AnnotationPolicy.containsPattern("x".repeat(500)).length)
    }
    @Test fun previousRootCannotOwnAnActiveItemsAnnotation() {
        val root = "content://docs/tree/primary%3AMusic"
        assertTrue(AnnotationPolicy.sameTree(root, "$root/document/primary%3AMusic%2Fsong.mp3"))
        assertFalse(AnnotationPolicy.sameTree(root, "content://docs/tree/primary%3AOther/document/song"))
        assertFalse(AnnotationPolicy.sameTree(root, "content://evil/tree/primary%3AMusic/document/song"))
        assertFalse(AnnotationPolicy.sameTree(root, "file:///song"))
        assertFalse(AnnotationPolicy.sameTree("bad url", "bad"))
    }
    @Test fun capturedItemsRetainTypeIdentityAndSubtitleForExternalPlayback() {
        val raw = LibraryItem("audio", "Song", "MP3", LibraryItemType.Audio, paletteSeed = 123,
            documentUri = "audio", artworkUri = "art")
        val match = TimedTextMatch("Song", TimedTextCandidate("Song.lrc", "lyric", TimedTextFormat.Lrc, 123, 456), "zh")
        val captured = UserItemEntity.capture("root", raw, "parent", match)
        assertEquals(raw, captured.item()); assertEquals(match, captured.timedText())
        assertNull(captured.score)
        assertNull(captured.copy(lyricFormat = "Unknown").timedText())
        val bytes = java.io.ByteArrayOutputStream().also { out -> java.io.ObjectOutputStream(out).use { it.writeObject(captured) } }.toByteArray()
        assertEquals(captured, java.io.ObjectInputStream(bytes.inputStream()).use { it.readObject() })
    }
}
