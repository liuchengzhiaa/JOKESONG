package com.example.asmrplayer.core.artwork

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class FolderArtworkLookupCacheTest {
    @Test fun `positive and absent covers share bounded metadata cache`() {
        val cache = FolderArtworkLookupCache(); var reads = 0
        repeat(10) {
            assertEquals("cover", cache.getOrLoad("folder") { reads++; "cover" })
            assertNull(cache.getOrLoad("empty") { reads++; null })
        }
        assertEquals(2, reads)
    }
    @Test fun `new scan or manual write revision refreshes positive and negative results`() {
        val cache = FolderArtworkLookupCache()
        assertNull(cache.getOrLoad("folder:scan1:write1") { null })
        assertEquals("new-cover", cache.getOrLoad("folder:scan1:write2") { "new-cover" })
        assertEquals("external-change", cache.getOrLoad("folder:scan2:write2") { "external-change" })
    }
    @Test fun `LRU eviction keeps recently requested entries without unbounded growth`() {
        val cache = FolderArtworkLookupCache(2)
        cache.getOrLoad("a") { "a" }; cache.getOrLoad("b") { "b" }
        cache.getOrLoad("a") { error("must hit") }
        cache.getOrLoad("c") { "c" }
        cache.getOrLoad("a") { error("must retain hot entry") }
        var retried = false
        assertEquals("b2", cache.getOrLoad("b") { retried = true; "b2" }); assertTrue(retried)
    }
    @Test fun `provider errors and cancellation do not become permanent missing covers`() {
        val cache = FolderArtworkLookupCache()
        try { cache.getOrLoad("a") { throw java.io.IOException() }; fail() } catch (_: java.io.IOException) { }
        try { cache.getOrLoad("a", { throw CancellationException() }) { fail(); null }; fail() }
        catch (_: CancellationException) { }
        var checks = 0
        try { cache.getOrLoad("a", { if (++checks == 3) throw CancellationException() }) { "not-published" }; fail() }
        catch (_: CancellationException) { }
        assertEquals("retry", cache.getOrLoad("a") { "retry" })
    }
    @Test fun `parallel size requests resolve the same folder once`() {
        val cache = FolderArtworkLookupCache(); val calls = AtomicInteger()
        val start = CountDownLatch(1); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = (1..8).map { pool.submit(Callable {
                start.await(); cache.getOrLoad("same-folder") {
                    calls.incrementAndGet(); entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); "cover"
                }
            }) }
            start.countDown(); assertTrue(entered.await(5, TimeUnit.SECONDS)); release.countDown()
            tasks.forEach { assertEquals("cover", it.get(5, TimeUnit.SECONDS)) }; assertEquals(1, calls.get())
        } finally { release.countDown(); pool.shutdownNow() }
    }
    @Test fun `waiting request can cancel without poisoning producer`() {
        val cache = FolderArtworkLookupCache(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val producer = pool.submit(Callable { cache.getOrLoad("a") { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); "cover" } })
            assertTrue(entered.await(5, TimeUnit.SECONDS)); var checks = 0
            try { cache.getOrLoad("a", { if (++checks == 2) throw CancellationException() }) { fail(); null }; fail() }
            catch (_: CancellationException) { }
            release.countDown(); assertEquals("cover", producer.get(5, TimeUnit.SECONDS))
            assertEquals("cover", cache.getOrLoad("a") { error("must hit") })
        } finally { release.countDown(); pool.shutdownNow() }
    }
    @Test fun `different stripe loads are not blocked by one slow provider call`() {
        val cache = FolderArtworkLookupCache(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val producer = pool.submit(Callable { cache.getOrLoad("a") { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); "a" } })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals("b", cache.getOrLoad("b") { "b" })
            release.countDown(); assertEquals("a", producer.get(5, TimeUnit.SECONDS))
        } finally { release.countDown(); pool.shutdownNow() }
    }
    @Test(expected = IllegalArgumentException::class) fun `zero capacity refused`() { FolderArtworkLookupCache(0) }
}
