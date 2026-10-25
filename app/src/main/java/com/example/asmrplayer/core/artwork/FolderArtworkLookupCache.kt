package com.example.asmrplayer.core.artwork

import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/** Metadata only: no bitmaps, media bytes, grants or disk writes. Key includes scan/write revision. */
internal class FolderArtworkLookupCache(private val capacity: Int = 256) {
    init { require(capacity > 0) }
    private data class Result(val uri: String?)
    private val entries = LinkedHashMap<String, Result>(16, 0.75f, true)
    // Fixed-size stripes bound coordination memory. Never hold the LRU monitor during provider I/O.
    private val stripes = Array(32) { ReentrantLock() }

    fun getOrLoad(key: String, checkpoint: () -> Unit = {}, load: () -> String?): String? {
        checkpoint()
        synchronized(entries) { entries[key] }?.let { return it.uri }
        val stripe = stripes[(key.hashCode() and Int.MAX_VALUE) % stripes.size]
        while (!stripe.tryLock(50, TimeUnit.MILLISECONDS)) checkpoint()
        try {
            checkpoint()
            synchronized(entries) { entries[key] }?.let { return it.uri }
            val result = Result(load()) // Exceptions/cancellation are NOT negative results.
            checkpoint()
            synchronized(entries) {
                entries[key] = result
                while (entries.size > capacity) entries.remove(entries.keys.first())
            }
            return result.uri
        } finally { stripe.unlock() }
    }
}
