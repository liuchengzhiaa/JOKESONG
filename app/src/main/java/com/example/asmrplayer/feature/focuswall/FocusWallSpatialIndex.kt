package com.example.asmrplayer.feature.focuswall

import kotlin.math.floor
import kotlin.math.max

internal data class WallQuery(val placements: List<FocusWallPlacement>, val candidatesExamined: Int)

/** Immutable two-axis index. A drag must not scan a whole horizontal strip of a large library. */
internal class FocusWallSpatialIndex(placements: List<FocusWallPlacement>) {
    private data class Entry(val order: Int, val placement: FocusWallPlacement)
    private val cellWidth = placements.maxOfOrNull { it.width }?.coerceAtLeast(1f) ?: 1f
    private val cellHeight = placements.maxOfOrNull { it.height }?.coerceAtLeast(1f) ?: 1f
    private val buckets = HashMap<Long, MutableList<Entry>>()

    init {
        placements.forEachIndexed { order, placement ->
            buckets.getOrPut(key(cell(placement.centerX, cellWidth), cell(placement.centerY, cellHeight))) {
                ArrayList()
            }.add(Entry(order, placement))
        }
    }

    fun query(minX: Float, maxX: Float, minY: Float, maxY: Float): WallQuery {
        if (buckets.isEmpty() || !minX.isFinite() || !maxX.isFinite() ||
            !minY.isFinite() || !maxY.isFinite() || minX > maxX || minY > maxY
        ) return WallQuery(emptyList(), 0)
        val left = cell(minX, cellWidth)
        val right = cell(maxX, cellWidth)
        val top = cell(minY, cellHeight)
        val bottom = cell(maxY, cellHeight)
        val columns = right.toLong() - left + 1
        val rows = bottom.toLong() - top + 1
        val matches = ArrayList<Entry>()
        var examined = 0
        fun inspect(entries: List<Entry>) {
            examined += entries.size
            entries.forEach { entry ->
                if (entry.placement.centerX in minX..maxX && entry.placement.centerY in minY..maxY) {
                    matches += entry
                }
            }
        }
        // A giant/degenerate query must not enumerate billions of empty grid cells.
        if (columns > buckets.size || rows > max(1, buckets.size).toLong() / columns) {
            buckets.values.forEach(::inspect)
        } else {
            for (x in left..right) for (y in top..bottom) buckets[key(x, y)]?.let(::inspect)
        }
        matches.sortBy { it.order }
        return WallQuery(matches.map { it.placement }, examined)
    }

    private fun cell(value: Float, size: Float): Int = floor(value.toDouble() / size).toInt()
    private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)
}
