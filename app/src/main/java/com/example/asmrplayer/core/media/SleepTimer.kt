package com.example.asmrplayer.core.media

import java.util.Locale

/**
 * What the sleep timer offers, in minutes. Zero is "off" rather than a duration of
 * zero, so the timer has exactly one representation of not being armed.
 *
 * The steps are wider apart further out because the choice is made before sleep, not
 * during it: five-minute resolution matters in the first half hour and not at all
 * past an hour.
 */
val SleepTimerChoices: List<Int> = listOf(0, 5, 10, 15, 30, 45, 60, 90)

/**
 * When a timer set for [minutes] from [nowMs] runs out, or null when it is not armed.
 *
 * The clock is a parameter rather than a call to `SystemClock` so the arithmetic can
 * be tested off-device, and so the caller -- which is the only side that knows
 * whether it is counting wall time or uptime -- gets to choose.
 */
fun sleepDeadlineFrom(minutes: Int, nowMs: Long): Long? =
    if (minutes <= 0) null else nowMs + minutes * 60_000L

/**
 * The countdown on the timer row, or null when there is nothing to show.
 *
 * Rounds *up* to the next whole second, which is the round that keeps the label
 * honest at both ends: 0:01 is still showing while any part of a second is left, so
 * the row never reads 0:00 for a timer that has not fired, and the label disappears
 * on the same tick the service pauses rather than a second earlier.
 *
 * Hours only appear when there are hours: a fourteen-minute timer reading `0:14:00`
 * is harder to take in at a glance in the dark than `14:00`.
 */
fun sleepRemainingLabel(expiresAtMs: Long?, nowMs: Long): String? {
    if (expiresAtMs == null || expiresAtMs <= 0L) return null
    val remainingMs = expiresAtMs - nowMs
    if (remainingMs <= 0L) return null
    val totalSeconds = (remainingMs + 999L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
