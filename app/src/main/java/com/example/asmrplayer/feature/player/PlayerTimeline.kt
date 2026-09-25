package com.example.asmrplayer.feature.player

import kotlin.math.roundToLong

data class PlayerTimeline(
    val durationMs: Long,
    val positionMs: Long = 0L,
    val bookmarksMs: List<Long> = emptyList(),
) {
    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    fun withDuration(newDurationMs: Long): PlayerTimeline {
        val duration = newDurationMs.coerceAtLeast(0L)
        return copy(
            durationMs = duration,
            positionMs = positionMs.coerceIn(0L, duration),
            bookmarksMs = bookmarksMs.filter { it <= duration },
        )
    }

    fun seekToProgress(progress: Float): PlayerTimeline = copy(
        positionMs = (durationMs * progress.coerceIn(0f, 1f)).roundToLong(),
    )

    fun skipBy(deltaMs: Long): PlayerTimeline = copy(
        positionMs = (positionMs + deltaMs).coerceIn(0L, durationMs),
    )

    fun addBookmark(): PlayerTimeline {
        if (durationMs <= 0L) return this
        val normalizedPosition = positionMs.coerceIn(0L, durationMs)
        if (bookmarksMs.any { kotlin.math.abs(it - normalizedPosition) < 1_000L }) return this
        return copy(bookmarksMs = (bookmarksMs + normalizedPosition).sorted())
    }

    fun removeBookmark(positionMs: Long): PlayerTimeline = copy(
        bookmarksMs = bookmarksMs.filterNot { it == positionMs },
    )
}

fun parseDurationLabel(label: String?): Long? {
    val parts = label?.trim()?.split(':') ?: return null
    if (parts.size !in 2..3) return null
    val numbers = parts.map { it.toLongOrNull() ?: return null }
    if (numbers.any { it < 0L }) return null
    if (numbers.drop(1).any { it >= 60L }) return null

    val seconds = if (numbers.size == 2) {
        numbers[0] * 60L + numbers[1]
    } else {
        numbers[0] * 3_600L + numbers[1] * 60L + numbers[2]
    }
    return seconds * 1_000L
}

fun formatPlaybackTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
    } else {
        "$minutes:${seconds.twoDigits()}"
    }
}

private fun Long.twoDigits(): String = toString().padStart(2, '0')
