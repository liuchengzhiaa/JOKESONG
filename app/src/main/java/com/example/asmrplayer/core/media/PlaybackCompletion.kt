package com.example.asmrplayer.core.media

private const val CompletionFraction = 0.95
private const val CompletionRemainingMs = 30_000L

/** Product completion rule, kept pure so the service and restore worker agree. */
fun shouldMarkPlaybackCompleted(positionMs: Long, durationMs: Long): Boolean {
    if (durationMs <= 0L) return false
    val position = positionMs.coerceIn(0L, durationMs)
    val fractionReached = position.toDouble() / durationMs >= CompletionFraction
    val nearEnd = durationMs > CompletionRemainingMs &&
        durationMs - position <= CompletionRemainingMs
    return fractionReached || nearEnd
}
