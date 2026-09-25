package com.example.asmrplayer.core.model

import kotlin.random.Random

object DiscoverySampler {
    fun <T> sample(
        items: List<T>,
        requestedCount: Int,
        seed: Long,
        pinned: T? = null,
    ): List<T> {
        if (requestedCount <= 0 || items.isEmpty()) return emptyList()

        val uniqueItems = items.distinct()
        val limit = requestedCount.coerceAtMost(uniqueItems.size)
        val candidates = uniqueItems.filterNot { it == pinned }.shuffled(Random(seed))

        if (pinned == null || pinned !in uniqueItems) {
            return candidates.take(limit)
        }

        return buildList(limit) {
            add(pinned)
            addAll(candidates.take(limit - 1))
        }
    }
}
