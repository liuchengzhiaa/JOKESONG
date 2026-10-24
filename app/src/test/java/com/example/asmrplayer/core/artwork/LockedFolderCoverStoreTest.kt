package com.example.asmrplayer.core.artwork

import java.lang.reflect.Modifier
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class LockedFolderCoverStoreTest {
    private class Store : FolderCoverStore {
        val calls = mutableListOf<String>()
        val doc = CoverDocument("image", "art.png")
        val facts = CoverImageCandidate(doc, 64, 32)
        val covers = mutableMapOf<String, String>()
        override fun existing(folder: String): String? { calls += "existing:$folder"; return covers[folder] }
        override fun legacy(folder: String): String? { calls += "legacy:$folder"; return null }
        override fun page(folder: String, kind: CoverChildKind, after: CoverDocument?, limit: Int): List<CoverDocument> {
            calls += "page:$folder:$kind:${after?.uri}:$limit"
            return if (kind == CoverChildKind.Image && after == null) listOf(doc) else emptyList()
        }
        override fun image(candidate: CoverDocument): CoverImageCandidate? {
            calls += "image:${candidate.uri}"; return facts.takeIf { candidate == doc }
        }
        override fun embedded(audio: CoverDocument): String? { calls += "embedded:${audio.uri}"; return "embedded" }
        override fun install(folder: String, source: String): String {
            calls += "install:$folder:$source"; return covers.getOrPut(folder) { "$folder/cover.png" }
        }
    }
    @Test fun `all interface methods have explicit concrete implementations`() {
        FolderCoverStore::class.java.declaredMethods.filterNot { it.isSynthetic }.forEach { method ->
            val declared = LockedFolderCoverStore::class.java.getDeclaredMethod(method.name, *method.parameterTypes)
            assertFalse(method.name, Modifier.isAbstract(declared.modifiers))
        }
    }
    @Test fun `all reads forward arguments without entering write checkpoint`() {
        val raw = Store(); val store = LockedFolderCoverStore(raw) { fail("Read must not take write lock") }
        assertNull(store.legacy("root"))
        assertEquals(listOf(raw.doc), store.page("root", CoverChildKind.Image, null, 7))
        assertTrue(store.page("root", CoverChildKind.Image, raw.doc, 9).isEmpty())
        assertEquals(raw.facts, store.image(raw.doc)); assertNull(store.image(CoverDocument("bad", "bad")))
        assertEquals("embedded", store.embedded(raw.doc))
        assertTrue(raw.calls.contains("page:root:Image:image:9"))
    }
    @Test fun `writes and recovery retain checkpoint and no overwrite`() {
        val raw = Store(); var checkpoints = 0; val store = LockedFolderCoverStore(raw) { checkpoints++ }
        assertNull(store.existing("root")); assertEquals("root/cover.png", store.install("root", "first"))
        assertEquals("root/cover.png", store.install("root", "second")); assertEquals(3, checkpoints)
    }
    @Test fun `cancellation escapes before recovery or write`() {
        val raw = Store(); val store = LockedFolderCoverStore(raw) { throw CancellationException() }
        for (operation in listOf<() -> Any?>({ store.existing("root") }, { store.install("root", "image") })) {
            try { operation(); fail("Expected cancellation") } catch (_: CancellationException) { }
        }
        assertTrue(raw.calls.isEmpty())
        // Use another thread: the lock is reentrant, so this thread could hide a leak.
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "cover-lock-test").apply { isDaemon = true }
        }
        try {
            assertNull(worker.submit<String?> { LockedFolderCoverStore(raw).existing("root") }
                .get(5, java.util.concurrent.TimeUnit.SECONDS))
        } finally { worker.shutdownNow() }
    }
    @Test fun `batch dispatches image through production wrapper and reruns safely`() {
        val raw = Store(); val store = LockedFolderCoverStore(raw)
        val first = FolderCoverBatch(store, {}).run("root")
        assertEquals(1, first.created); assertEquals(0, first.failed)
        assertTrue(raw.calls.contains("image:image")); assertTrue(raw.calls.contains("install:root:image"))
        raw.calls.clear(); val second = FolderCoverBatch(store, {}).run("root")
        assertEquals(0, second.created); assertEquals(1, second.existing)
        assertFalse(raw.calls.any { it.startsWith("image:") || it.startsWith("install:") })
    }
}
