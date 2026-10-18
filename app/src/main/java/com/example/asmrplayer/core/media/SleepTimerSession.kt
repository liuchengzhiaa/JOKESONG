package com.example.asmrplayer.core.media

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The wire between the player page and the service for the sleep timer.
 *
 * The timer lives in [PlaybackService] and not in the UI, and that is the whole
 * reason this file exists. [PlaybackConnection] disconnects whenever the UI stops, so
 * a countdown kept there would be cancelled by the owner locking the screen -- which
 * is the one thing he is certain to do between setting a sleep timer and needing it.
 * The service outlives the screen, so the countdown has to be the service's.
 *
 * Two carriers, because the two directions have different shapes:
 *
 *  - Arming it is a *command*: the UI asks the service to do something it cannot do
 *    itself, so it goes over a [SessionCommand] and the answer arrives as a
 *    [SessionResult].
 *  - Reading it back is *state*: the deadline has to be visible to whoever is drawing
 *    the countdown, including a UI that connected after the timer was already armed.
 *    Session extras are the only part of a Media3 session that is readable on connect
 *    without a round trip, so the deadline rides there.
 */
internal const val SleepTimerAction = "com.example.asmrplayer.SLEEP_TIMER"

/** Minutes to arm, or 0 to cancel. */
internal const val SleepTimerMinutesKey = "minutes"

/** Wall-clock millis the timer fires at, or 0 when it is not armed. */
internal const val SleepTimerDeadlineKey = "deadlineMs"

/**
 * The choice that armed it, or 0.
 *
 * The deadline alone is almost enough -- it says when the timer fires -- but not
 * quite: a list of durations cannot tell which of its rows is the live one from a
 * countdown, because the countdown is always a little less than the duration chosen.
 * Carrying the choice as well makes the selected row exact rather than inferred.
 */
internal const val SleepTimerArmedKey = "armedMinutes"

/**
 * The one command this app adds to a session.
 *
 * The empty bundle is the command's own extra payload and stays empty: what the
 * command carries travels in the arguments of the call, which is the thing Media3
 * matches against when it decides whether a controller may send it at all.
 */
internal val SleepTimerCommand = SessionCommand(SleepTimerAction, Bundle.EMPTY)

/** Everything a surface needs to draw the timer, read in one go. */
internal data class SleepTimerSnapshot(val expiresAtMs: Long?, val minutes: Int?)

private val SleepTimerOff = SleepTimerSnapshot(expiresAtMs = null, minutes = null)

/**
 * The armed timer as the session is currently publishing it.
 *
 * Both halves are read from one `sessionExtras` call rather than two, so a UI can
 * never draw a deadline from one update next to a duration from the next.
 */
internal fun MediaController.sleepTimerSnapshot(): SleepTimerSnapshot {
    val extras = sessionExtras
    return SleepTimerSnapshot(
        expiresAtMs = extras.getLong(SleepTimerDeadlineKey).takeIf { it > 0L },
        minutes = extras.getInt(SleepTimerArmedKey).takeIf { it > 0 },
    )
}

/** The same snapshot for a player with no timer on it at all. */
internal fun noSleepTimer(): SleepTimerSnapshot = SleepTimerOff

/**
 * Answers "the session is not ours to command" without throwing on the caller.
 *
 * The code is taken from [SessionError] rather than the `RESULT_ERROR_*` alias of the
 * same name on [SessionResult]: the two read alike but only the first is the one
 * `SessionResult(int)` declares itself to accept.
 */
internal fun notSupportedResult(): ListenableFuture<SessionResult> =
    Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
