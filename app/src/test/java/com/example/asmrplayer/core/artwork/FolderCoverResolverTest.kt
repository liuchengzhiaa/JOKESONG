package com.example.asmrplayer.core.artwork

import org.junit.Assert.*
import org.junit.Test

class FolderCoverResolverTest {
    private class Store : FolderCoverStore {
        val covers = mutableMapOf<String, String>()
        val legacy = mutableMapOf<String, String>()
        val audios = mutableMapOf<String, List<CoverDocument>>()
        val folders = mutableMapOf<String, List<CoverDocument>>()
        val embedded = mutableMapOf<String, String>()
        val log = mutableListOf<String>()
        var readOnly = false
        override fun existing(folder: String) = covers[folder]
        override fun legacy(folder: String) = legacy[folder]
        override fun page(folder: String, kind: CoverChildKind, after: CoverDocument?, limit: Int): List<CoverDocument> =
            (if (kind == CoverChildKind.Audio) audios[folder] else folders[folder]).orEmpty()
                .sortedWith(CoverDocumentOrder).filter { after == null || CoverDocumentOrder.compare(it, after) > 0 }.take(limit)
        override fun embedded(audio: CoverDocument): String? { log += "audio:${audio.uri}"; return embedded[audio.uri] }
        override fun install(folder: String, source: String): String {
            if (readOnly) throw CoverWriteUnavailable(SecurityException())
            return covers.getOrPut(folder) { log += "save:$folder:$source"; "$folder/cover.png" }
        }
        fun resolve(budget: CoverLookupBudget = CoverLookupBudget(), depth: Int = 24) =
            FolderCoverResolver(this, budget, depth).resolve("root")
    }
    private fun doc(name: String) = CoverDocument(name, name)
    @Test fun `existing cover wins over legacy audio and descendants`() {
        val s = Store(); s.covers["root"] = "disk"; s.legacy["root"] = "legacy"
        s.audios["root"] = listOf(doc("1.mp3")); s.embedded["1.mp3"] = "embedded"
        assertEquals("disk", s.resolve()); assertTrue(s.log.isEmpty())
    }
    @Test fun `old explicit override migrates before automatic audio`() {
        val s = Store(); s.legacy["root"] = "private"; s.audios["root"] = listOf(doc("1.mp3"))
        assertEquals("root/cover.png", s.resolve()); assertEquals(listOf("save:root:private"), s.log)
        assertEquals("private", s.legacy["root"]) // Retain migration source for failure/recovery.
    }
    @Test fun `try later audio when first has no artwork using natural order`() {
        val s = Store(); s.audios["root"] = listOf(doc("10.mp3"), doc("2.mp3"), doc("1.mp3"))
        s.embedded["2.mp3"] = "image2"; s.embedded["10.mp3"] = "image10"
        assertEquals("root/cover.png", s.resolve())
        assertEquals(listOf("audio:1.mp3", "audio:2.mp3", "save:root:image2"), s.log)
    }
    @Test fun `direct audio wins over child cover`() {
        val s = Store(); s.audios["root"] = listOf(doc("a")); s.embedded["a"] = "art"
        s.folders["root"] = listOf(doc("child")); s.covers["child"] = "child/cover.jpg"
        s.resolve(); assertEquals("save:root:art", s.log.last())
    }
    @Test fun `empty direct audio falls through to depth first children`() {
        val s = Store(); s.audios["root"] = listOf(doc("no-art"))
        s.folders["root"] = listOf(doc("10"), doc("2"), doc("1"))
        s.covers["2"] = "two"; s.covers["10"] = "ten"
        s.resolve(); assertEquals(listOf("audio:no-art", "save:root:two"), s.log)
    }
    @Test fun `each intermediate folder receives child cover bottom up`() {
        val s = Store(); s.folders["root"] = listOf(doc("a")); s.folders["a"] = listOf(doc("b"))
        s.audios["b"] = listOf(doc("song")); s.embedded["song"] = "embedded"
        s.resolve()
        assertEquals(listOf("audio:song", "save:b:embedded", "save:a:b/cover.png", "save:root:a/cover.png"), s.log)
        s.log.clear(); s.resolve(); assertTrue(s.log.isEmpty())
    }
    @Test fun `existing grandchild cover copied upward without extracting audio`() {
        val s = Store(); s.folders["root"] = listOf(doc("a")); s.covers["a"] = "a/cover.jpg"
        s.resolve(); assertEquals(listOf("save:root:a/cover.jpg"), s.log)
    }
    @Test fun `more than one page of audio is considered`() {
        val s = Store(); s.audios["root"] = (1..70).map { doc("$it.mp3") }; s.embedded["65.mp3"] = "image"
        s.resolve(); assertEquals(65, s.log.count { it.startsWith("audio:") }); assertEquals("save:root:image", s.log.last())
    }
    @Test fun `child pagination does not stop at 32`() {
        val s = Store(); s.folders["root"] = (1..70).map { doc("child$it") }; s.covers["child40"] = "image"
        assertEquals("root/cover.png", s.resolve())
    }
    @Test fun `empty subtree yields placeholder without writing anything`() {
        val s = Store(); s.folders["root"] = listOf(doc("empty")); assertNull(s.resolve()); assertTrue(s.log.isEmpty())
    }
    @Test fun `cycles terminate without inventing artwork`() {
        val s = Store(); s.folders["root"] = listOf(doc("a")); s.folders["a"] = listOf(doc("root"))
        assertNull(s.resolve())
    }
    @Test fun `budget exhaustion is an error not a definitive no cover`() {
        val s = Store(); s.audios["root"] = listOf(doc("a"), doc("b")); s.embedded["b"] = "image"
        try { s.resolve(CoverLookupBudget(maxAudio = 1)); fail() } catch (_: IllegalStateException) { }
        assertTrue(s.covers.isEmpty()); assertEquals("root/cover.png", s.resolve())
    }
    @Test fun `folder and depth budgets stop recursion`() {
        val s = Store(); s.folders["root"] = listOf(doc("a")); s.folders["a"] = listOf(doc("b"))
        try { s.resolve(CoverLookupBudget(maxFolders = 1)); fail() } catch (_: IllegalStateException) { }
        try { s.resolve(depth = 1); fail() } catch (_: IllegalStateException) { }
        assertTrue(s.covers.isEmpty())
    }
    @Test fun `checkpoint cancellation propagates without image installation`() {
        val s = Store(); s.audios["root"] = listOf(doc("a")); s.embedded["a"] = "image"
        try { s.resolve(CoverLookupBudget({ throw java.util.concurrent.CancellationException() })); fail() }
        catch (_: java.util.concurrent.CancellationException) { }
        assertTrue(s.covers.isEmpty())
    }
    @Test fun `read only roots display source but do not pretend disk persistence`() {
        val s = Store(); s.readOnly = true; s.audios["root"] = listOf(doc("a")); s.embedded["a"] = "image"
        assertEquals("image", s.resolve()); assertTrue(s.covers.isEmpty())
    }
    @Test fun `legacy input retained when migration cannot write`() {
        val s = Store(); s.readOnly = true; s.legacy["root"] = "private"
        assertEquals("private", s.resolve()); assertEquals("private", s.legacy["root"])
    }
    @Test fun `cover recognition is exact case insensitive basename not generic images`() {
        listOf("cover.png", "COVER.JPG", "Cover.webp", "cover").forEach { assertTrue(isNamedFolderCover(it)) }
        listOf("cover1.jpg", "mycover.png", "art.jpg", ".cover.png").forEach { assertFalse(isNamedFolderCover(it)) }
        assertTrue(isFolderCoverArtifact(".jokesong-cover-id-stage.png"))
    }
    @Test fun `row budget terminates streaming scans`() {
        val b = CoverLookupBudget(maxRows = 2); b.row(); b.row()
        try { b.row(); fail() } catch (_: IllegalStateException) { }
    }
}
