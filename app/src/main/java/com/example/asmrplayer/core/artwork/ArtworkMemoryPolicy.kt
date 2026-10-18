package com.example.asmrplayer.core.artwork

/** Leave heap for playback, text, visible images and in-flight decoding, not just the cache. */
internal fun artworkMemoryCacheBytes(maxHeapBytes: Long): Long {
    require(maxHeapBytes > 0)
    return (maxHeapBytes / 8).coerceIn(4L * 1024 * 1024, 64L * 1024 * 1024)
}
