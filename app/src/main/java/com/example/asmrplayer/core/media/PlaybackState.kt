package com.example.asmrplayer.core.media

/**
 * Observable snapshot of the Media3 session, as seen from the UI.
 *
 * Every player surface (focus card, full player) reads this one value instead of
 * keeping local progress state, so they can never disagree about what is playing.
 */
data class PlaybackState(
    val isConnected: Boolean = false,
    val mediaId: String? = null,
    val title: String? = null,
    /** Presentation metadata retained by Media3 even when the library is elsewhere. */
    val artworkUri: String? = null,
    val documentUri: String? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasQueue: Boolean = false,
    val errorMessage: String? = null,
) {
    val hasDuration: Boolean get() = durationMs > 0L

    val progress: Float
        get() = if (hasDuration) {
            (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    fun isCurrent(itemId: String?): Boolean = itemId != null && itemId == mediaId

    /** Maps a slider fraction onto a seekable position. */
    fun positionForFraction(fraction: Float): Long =
        if (hasDuration) (durationMs * fraction.coerceIn(0f, 1f)).toLong() else 0L
}

/** What a play/pause affordance should do for the current [PlaybackState]. */
sealed interface PlaybackCommand {
    data object Play : PlaybackCommand

    data object Pause : PlaybackCommand

    /** Nothing is queued, so the control is inert. */
    data object None : PlaybackCommand
}

fun toggleCommandFor(state: PlaybackState): PlaybackCommand = when {
    !state.hasQueue -> PlaybackCommand.None
    state.playWhenReady -> PlaybackCommand.Pause
    else -> PlaybackCommand.Play
}
