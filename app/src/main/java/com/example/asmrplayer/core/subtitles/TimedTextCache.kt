package com.example.asmrplayer.core.subtitles

/**
 * What makes a parsed document still valid.
 *
 * Size and modified time are part of the key rather than the URI alone, so editing a
 * file in place -- which is how a mistimed lyric gets fixed -- misses the cache
 * instead of serving the old lines forever.
 */
data class TimedTextKey(
    val documentUri: String,
    val sizeBytes: Long,
    val modifiedAtMs: Long,
)

/** A candidate's own key, so a caller never assembles one by hand. */
fun TimedTextCandidate.cacheKey(): TimedTextKey = TimedTextKey(
    documentUri = documentUri,
    sizeBytes = sizeBytes,
    modifiedAtMs = modifiedAtMs,
)

/**
 * Bounded least-recently-used store of parsed documents.
 *
 * Tiny on purpose. One person reads one track's lyrics at a time, a parsed document is
 * the whole file in memory, and the cost of a miss is re-reading one small text file
 * from a document provider that is already open. Keeping a large cache would trade a
 * bounded few kilobytes for an unbounded share of a large library's text.
 *
 * Reads count as use, so skipping back and forth inside the track being read never
 * evicts that track's own lines.
 */
class TimedTextCache(private val capacity: Int = DefaultCapacity) {
    init {
        require(capacity > 0) { "TimedTextCache capacity must be positive" }
    }

    private val entries = object : LinkedHashMap<TimedTextKey, TimedTextDocument>(
        /* initialCapacity = */ 8,
        /* loadFactor = */ 0.75f,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<TimedTextKey, TimedTextDocument>,
        ): Boolean = size > capacity
    }

    val size: Int get() = entries.size

    fun get(key: TimedTextKey): TimedTextDocument? = entries[key]

    fun put(key: TimedTextKey, document: TimedTextDocument) {
        entries[key] = document
    }

    fun clear() {
        entries.clear()
    }

    companion object {
        const val DefaultCapacity: Int = 4
    }
}
