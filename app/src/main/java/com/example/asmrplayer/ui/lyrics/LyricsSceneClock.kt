package com.example.asmrplayer.ui.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * The frame loops a full-screen lyric mode is allowed to run on.
 *
 * Every other surface in this app is forbidden one, and each of these is deliberately
 * the narrowest loop that can exist:
 *
 * - it is created inside a mode's own composition, so leaving the scene disposes it
 *   and no visibility bookkeeping is needed -- being composed *is* being visible;
 * - it only advances while `running`, which callers derive from
 *   [lyricsSceneIsRunning]: a paused scene, an idle one, and a listener who has
 *   turned motion off all get exactly zero frames rather than a loop that spins and
 *   draws nothing;
 * - when it stops, what it had is kept, so a paused mode holds the pose it had
 *   instead of snapping back to a zero phase.
 *
 * A mode may run one loop per thing it is genuinely animating -- the escapement runs
 * a clock, the word cloud runs a clock and a smoothed position -- but each is a
 * distinct reading rather than a general-purpose ticker, which is why they are
 * separate functions here and not one "onFrame" callback.
 */

/**
 * Seconds the scene has been running, accumulated frame by frame.
 *
 * A movement that *accumulates* -- a wheel that keeps turning, a seconds hand that
 * steps once a second -- cannot be derived from a difference against a start time,
 * because the time it spent stopped is not time that passed. This clock adds up only
 * the frames it actually saw, so a paused scene holds its pose and a resumed one
 * carries on from there instead of jumping forward.
 */
@Composable
internal fun rememberLyricsSceneElapsedSeconds(running: Boolean): State<Float> {
    val elapsed = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    elapsed.floatValue += (now - previous) / NanosPerSecond
                }
                previous = now
            }
        }
    }
    return elapsed
}

/**
 * The playback position, filled in between Media3's own updates.
 *
 * Media3 is the clock of record and stays it: every position it reports restarts this
 * loop from that value. What the loop adds is the gaps -- a position that only changed
 * a few times a second would make a word arrive in visible steps, and a cloud of words
 * all arriving at once is the one thing this presentation is for. Paused, or with
 * motion off, the position simply *is* what Media3 last said, which is why a stopped
 * scene shows a stopped picture rather than a drifting one.
 */
@Composable
internal fun rememberLyricsScenePosition(
    positionMs: Long,
    running: Boolean,
): State<Long> {
    val position = remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, running) {
        position.longValue = positionMs
        if (!running) return@LaunchedEffect
        var base = 0L
        while (true) {
            withFrameNanos { now ->
                // The first frame of this position anchors the extrapolation, so the
                // reported value is exact at the moment Media3 reported it.
                if (base == 0L) base = now
                position.longValue = positionMs + (now - base) / NanosPerMilli
            }
        }
    }
    return position
}

/** Frame times are nanoseconds; every rate here is expressed per second. */
private const val NanosPerSecond = 1_000_000_000f
private const val NanosPerMilli = 1_000_000L

/**
 * Whether a mode should be moving at all.
 *
 * Both halves are load-bearing. A paused scene must be still because a moving
 * picture next to stopped audio is a lie about what is playing; a scene at 0% motion
 * must be still because that is what the setting means everywhere else in the app.
 */
internal fun lyricsSceneIsRunning(isPlaying: Boolean, motionPercent: Int): Boolean =
    isPlaying && motionPercent > 0
