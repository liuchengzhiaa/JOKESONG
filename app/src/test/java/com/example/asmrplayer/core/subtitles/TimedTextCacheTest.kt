package com.example.asmrplayer.core.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class TimedTextCacheTest {

    private fun key(name: String, modifiedAtMs: Long = 1_000) = TimedTextKey(
        documentUri = "content://tree/folder/$name",
        sizeBytes = 512,
        modifiedAtMs = modifiedAtMs,
    )

    private fun document(text: String) = TimedTextDocument(
        format = TimedTextFormat.Lrc,
        lines = listOf(TimedTextLine(0, null, text)),
    )

    @Test
    fun `what was put in comes back out`() {
        val cache = TimedTextCache()
        cache.put(key("Track.lrc"), document("第一句"))

        assertEquals("第一句", cache.get(key("Track.lrc"))?.lines?.single()?.text)
        assertEquals(1, cache.size)
    }

    @Test
    fun `a miss is null rather than an error`() {
        val cache = TimedTextCache()
        assertNull(cache.get(key("Never Seen.lrc")))
    }

    @Test
    fun `editing the file in place misses the cache`() {
        // Same URI, different modified time: this is what fixing a mistimed line looks
        // like from here, and serving the old lines would be the whole bug.
        val cache = TimedTextCache()
        cache.put(key("Track.lrc", modifiedAtMs = 1_000), document("旧的"))

        assertNull(cache.get(key("Track.lrc", modifiedAtMs = 2_000)))
    }

    @Test
    fun `the least recently read entry is the one evicted`() {
        val cache = TimedTextCache(capacity = 2)
        cache.put(key("A.lrc"), document("A"))
        cache.put(key("B.lrc"), document("B"))

        // Reading A is what makes B the least recently used.
        cache.get(key("A.lrc"))
        cache.put(key("C.lrc"), document("C"))

        assertEquals(2, cache.size)
        assertNull(cache.get(key("B.lrc")))
        assertEquals("A", cache.get(key("A.lrc"))?.lines?.single()?.text)
        assertEquals("C", cache.get(key("C.lrc"))?.lines?.single()?.text)
    }

    @Test
    fun `re-reading the same file does not grow the cache`() {
        val cache = TimedTextCache(capacity = 2)
        repeat(5) { cache.put(key("Track.lrc"), document("第 $it 次")) }

        assertEquals(1, cache.size)
        assertEquals("第 4 次", cache.get(key("Track.lrc"))?.lines?.single()?.text)
    }

    @Test
    fun `clearing empties it`() {
        val cache = TimedTextCache()
        cache.put(key("Track.lrc"), document("第一句"))
        cache.clear()

        assertEquals(0, cache.size)
        assertNull(cache.get(key("Track.lrc")))
    }

    @Test
    fun `a capacity of zero is refused rather than silently caching nothing`() {
        try {
            TimedTextCache(capacity = 0)
            fail("expected a capacity of 0 to be refused")
        } catch (_: IllegalArgumentException) {
            // The message is not the contract; refusing is.
        }
    }
}
