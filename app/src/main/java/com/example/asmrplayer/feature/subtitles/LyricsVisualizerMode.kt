package com.example.asmrplayer.feature.subtitles

/**
 * Stable set of deliberately different full-screen lyric presentations.
 *
 * This is a registry boundary rather than a list of composables in settings code:
 * storage keeps the ordinal, UI decides how each mode is drawn, and adding a mode
 * cannot change timed-text parsing or playback ownership.
 */
enum class LyricsVisualizerMode(val stableId: String) {
    /** Five-tier centred lyric flow over a soft artwork light field. */
    Flow("flow"),

    /** Folia-like left-aligned typography over a clearer poster. */
    Poster("poster"),

    /** One dominant line and a restrained next-line cue. */
    Spotlight("spotlight"),
    ;

    companion object {
        val Default: LyricsVisualizerMode = Flow

        fun fromOrdinal(ordinal: Int): LyricsVisualizerMode =
            entries.getOrNull(ordinal) ?: Default
    }
}
