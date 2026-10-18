package com.example.asmrplayer.core.artwork

import org.junit.Assert.*
import org.junit.Test

class FolderCoverBatchTest {
    private class Store : FolderCoverStore {
        val covers = mutableMapOf<String, String>()
        val legacy = mutableMapOf<String, String>()
        val audios = mutableMapOf<String, List<CoverDocument>>()
        val folders = mutableMapOf<String, List<CoverDocument>>()
        val art = mutableMapOf<String, String>()
        val log = mutableListOf<String>()
        val failFolders = mutableSetOf<String>()
        override fun existing(folder: String) = covers[folder]
        override fun legacy(folder: String) = legacy[folder]
        override fun page(folder: String, kind: CoverChildKind, after: CoverDocument?, limit: Int): List<CoverDocument> =
            (if (kind == CoverChildKind.Audio) audios[folder] else folders[folder]).orEmpty()
                .sortedWith(CoverDocumentOrder).filter { after == null || CoverDocumentOrder.compare(it, after) > 0 }.take(limit)
        override fun embedded(audio: CoverDocument): String? { log += "audio:${audio.uri}"; return art[audio.uri] }
        override fun install(folder: String, source: String): String {
            if (folder in failFolders) throw SecurityException("read only")
            return covers.getOrPut(folder) { log += "save:$folder:$source"; "$folder/cover.png" }
        }
        fun run(checkpoint: () -> Unit = {}) = FolderCoverBatch(this, checkpoint).run("root")
    }
    private fun doc(name: String) = CoverDocument(name, name)
    @Test fun `existing root must not skip any child`() {
        val s = Store(); s.covers["root"] = "original"; s.folders["root"] = listOf(doc("a"), doc("b"))
        s.audios["a"] = listOf(doc("song")); s.art["song"] = "image"
        val c = s.run(); assertEquals(3, c.visited); assertEquals(1, c.created); assertEquals(1, c.existing); assertEquals(1, c.missing)
        assertEquals("original", s.covers["root"]); assertEquals("a/cover.png", s.covers["a"])
    }
    @Test fun `all siblings processed not just first successful child`() {
        val s = Store(); s.folders["root"] = listOf(doc("10"), doc("2"), doc("1"))
        listOf("1", "2", "10").forEach { s.audios[it] = listOf(doc("audio$it")); s.art["audio$it"] = "image$it" }
        val c = s.run(); assertEquals(4, c.created); assertEquals(4, s.covers.size)
        assertEquals("save:root:1/cover.png", s.log.last())
    }
    @Test fun `audio without art does not stop direct search and natural order wins`() {
        val s = Store(); s.audios["root"] = listOf(doc("10.mp3"), doc("2.mp3"), doc("1.mp3"))
        s.art["2.mp3"] = "two"; s.art["10.mp3"] = "ten"; s.run()
        assertEquals(listOf("audio:1.mp3", "audio:2.mp3", "save:root:two"), s.log)
    }
    @Test fun `direct audio wins but descendant is still generated`() {
        val s = Store(); s.audios["root"] = listOf(doc("root-song")); s.art["root-song"] = "root-image"
        s.folders["root"] = listOf(doc("a")); s.audios["a"] = listOf(doc("child-song")); s.art["child-song"] = "child-image"
        val c = s.run(); assertEquals(2, c.created); assertTrue(s.log.contains("save:root:root-image")); assertTrue(s.log.contains("save:a:child-image"))
    }
    @Test fun `child image fills every ancestor bottom up`() {
        val s = Store(); s.folders["root"] = listOf(doc("a")); s.folders["a"] = listOf(doc("b")); s.covers["b"] = "b/cover.jpg"
        val c = s.run(); assertEquals(2, c.created); assertEquals(1, c.existing)
        assertEquals(listOf("save:a:b/cover.jpg", "save:root:a/cover.png"), s.log)
    }
    @Test fun `rerun skips generated covers without extracting again`() {
        val s = Store(); s.folders["root"] = listOf(doc("a")); s.audios["a"] = listOf(doc("song")); s.art["song"] = "image"
        s.run(); s.log.clear(); val c = s.run(); assertEquals(0, c.created); assertEquals(2, c.existing); assertTrue(s.log.isEmpty())
    }
    @Test fun `legacy migration happens only inside explicit processing and respects disk cover`() {
        val s = Store(); s.legacy["root"] = "private"; s.run(); assertEquals("save:root:private", s.log.single())
        s.log.clear(); s.legacy["root"] = "another private"; s.run(); assertTrue(s.log.isEmpty())
    }
    @Test fun `audio paging proceeds past old lazy 256 track limit`() {
        val s = Store(); s.audios["root"] = (1..300).map { doc("$it.mp3") }; s.art["300.mp3"] = "last"
        assertEquals(1, s.run().created); assertEquals(300, s.log.count { it.startsWith("audio:") })
    }
    @Test fun `all children beyond 32 and 256 are visited`() {
        val s = Store(); s.folders["root"] = (1..300).map { doc("child$it") }
        (1..300).forEach { s.covers["child$it"] = "image$it" }
        val c = s.run(); assertEquals(301, c.visited); assertEquals(300, c.existing); assertEquals(1, c.created)
    }
    @Test fun `no art subtree remains empty without writes`() {
        val s = Store(); s.folders["root"] = listOf(doc("a")); s.audios["a"] = listOf(doc("no-art"))
        val c = s.run(); assertEquals(2, c.missing); assertEquals(0, c.failed); assertTrue(s.covers.isEmpty())
    }
    @Test fun `write failures are not counted as generated or borrowed as persisted covers`() {
        val s = Store(); s.failFolders += "a"; s.folders["root"] = listOf(doc("a")); s.audios["a"] = listOf(doc("song")); s.art["song"] = "image"
        val c = s.run(); assertEquals(0, c.created); assertEquals(2, c.failed); assertEquals(0, c.missing); assertTrue(s.covers.isEmpty())
    }
    @Test fun `bad sibling does not prevent other siblings being processed`() {
        val s = Store(); s.folders["root"] = listOf(doc("a"), doc("b")); s.failFolders += "a"
        for (f in listOf("a", "b")) { s.audios[f] = listOf(doc("$f-song")); s.art["$f-song"] = "$f-image" }
        val c = s.run(); assertEquals(3, c.visited); assertEquals(1, c.failed); assertEquals(2, c.created)
    }
    @Test fun `cancellation keeps completed covers and rerun continues idempotently`() {
        val s = Store(); s.folders["root"] = listOf(doc("a")); s.audios["root"] = listOf(doc("song")); s.art["song"] = "image"
        try { s.run { if (s.covers.containsKey("root")) throw java.util.concurrent.CancellationException() }; fail() }
        catch (_: java.util.concurrent.CancellationException) { }
        assertEquals("root/cover.png", s.covers["root"]); assertEquals(1, s.run().existing)
    }
    @Test fun `cycles terminate without duplicate processing`() {
        val s = Store(); s.folders["root"] = listOf(doc("a")); s.folders["a"] = listOf(doc("root"))
        assertEquals(2, s.run().visited)
    }
    @Test fun `safety limit fails instead of claiming entire library completed`() {
        val s = Store(); s.folders["root"] = listOf(doc("a"))
        try { FolderCoverBatch(s, {}, maxFolders = 1).run("root"); fail() } catch (_: IllegalStateException) { }
        try { FolderCoverBatch(s, {}, maxDepth = 0).run("root"); fail() } catch (_: IllegalStateException) { }
    }
    @Test fun `progress is monotonic and final counters describe every processed directory`() {
        val s = Store(); s.folders["root"] = listOf(doc("a")); val updates = mutableListOf<FolderCoverBatchCounts>()
        val result = FolderCoverBatch(s, {}, { updates += it }).run("root")
        assertEquals(result, updates.last()); assertTrue(updates.zipWithNext().all { (a, b) -> a.visited <= b.visited })
        assertEquals(result.visited, result.created + result.existing + result.failed + result.missing)
    }
    @Test fun `cover recognition remains exact case insensitive basename`() {
        listOf("cover.png", "COVER.JPG", "Cover.webp").forEach { assertTrue(isNamedFolderCover(it)) }
        listOf("cover1.jpg", "mycover.png", "art.jpg", ".cover.png").forEach { assertFalse(isNamedFolderCover(it)) }
    }
    @Test fun `row budget still terminates malformed huge directory streams`() {
        val b = CoverLookupBudget(maxRows = 2); b.row(); b.row()
        try { b.row(); fail() } catch (_: IllegalStateException) { }
    }
    @Test fun `ten thousand child folders are all visited even with covered root`() {
        val s = Store(); s.covers["root"] = "root-original"
        s.folders["root"] = (1..10_000).map { doc("child$it") }
        (1..10_000).forEach { s.covers["child$it"] = "image$it" }
        val result = s.run()
        assertEquals(10_001, result.visited); assertEquals(10_001, result.existing)
        assertEquals(0, result.created); assertTrue(s.log.isEmpty())
    }
    @Test fun `local audio parent validation rejects descendants and other volumes`() {
        assertTrue(isDirectLocalAudioChild("primary:Music", "primary:Music/song.mp3"))
        assertTrue(isDirectLocalAudioChild("primary:", "primary:song.mp3"))
        assertTrue(isDirectLocalAudioChild("ABCD:Music", "ABCD:Music/song.mp3"))
        assertFalse(isDirectLocalAudioChild("primary:Music", "primary:Music/sub/song.mp3"))
        assertFalse(isDirectLocalAudioChild("primary:Music", "ABCD:Music/song.mp3"))
        assertFalse(isDirectLocalAudioChild("primary:Music", "primary:Other/song.mp3"))
    }
}
