package com.example.asmrplayer.core.audio

/**
 * Which class of output the player page should name in its top-right status mark.
 *
 * Ordering is the report order, not an enum over Android's device types: the badge
 * only ever says one of four things, and collapsing Android's forty-odd `TYPE_*`
 * constants into them is the adapter's job ([androidAudioOutputs]). Keeping the
 * enum here rather than re-exporting the platform's means [pickActiveOutput] is
 * plain Kotlin and can be tested without a device.
 */
internal enum class AudioOutputKind { Wired, Bluetooth, BuiltIn, Other }

/** One connected output, as the platform reported it. */
internal data class AudioOutputCandidate(
    val kind: AudioOutputKind,
    /** The product name, when the platform gave one worth reading out. */
    val name: String? = null,
)

/** The named output, or null when nothing about the routing is worth claiming. */
internal data class AudioOutput(val kind: AudioOutputKind, val name: String?)

/**
 * The output to name, from the outputs the platform reports as connected.
 *
 * This is a *heuristic over connected devices*, not a report of the truly active
 * route, and that distinction is deliberate. Android's real answer,
 * `AudioManager.getActivePlaybackConfigurations()`, is restricted for third-party
 * apps on recent releases and comes back empty, so an indicator built on it would
 * be blank exactly where it would be most useful. Connected-and-preferred is what is
 * actually observable, and for this app -- one owner, headphones at night -- the two
 * agree in every case that matters.
 *
 * The order is wired, then Bluetooth, then everything else. Bluetooth outranks the
 * built-in speaker because a connected speaker is never incidental; wired outranks
 * Bluetooth because plugging in is the more recent, more deliberate act, and it wins
 * on the phone even when a paired headset is still in range.
 */
internal fun pickActiveOutput(candidates: List<AudioOutputCandidate>): AudioOutput? {
    val best = candidates.minByOrNull { priority(it.kind) } ?: return null
    return AudioOutput(kind = best.kind, name = best.name)
}

private fun priority(kind: AudioOutputKind): Int = when (kind) {
    AudioOutputKind.Wired -> 0
    AudioOutputKind.Bluetooth -> 1
    AudioOutputKind.BuiltIn -> 2
    AudioOutputKind.Other -> 3
}
