package com.example.asmrplayer.feature.subtitles

/**
 * Stable set of deliberately different full-screen lyric presentations.
 *
 * This is a registry boundary rather than a list of composables in settings code:
 * storage keeps the ordinal, UI decides how each mode is drawn, and adding a mode
 * cannot change timed-text parsing or playback ownership.
 */
enum class LyricsVisualizerMode(
    val stableId: String,
    /**
     * Value written by builds that originally persisted the enum ordinal.
     *
     * The catalogue was deliberately reduced to the four modes that are being taken
     * to reference quality. Keeping the old values here means an existing install
     * does not silently turn 时计 into another mode when the enum becomes shorter.
     */
    val storedValue: Int,
) {
    /** The current line carried by a wide halo drawn from the cover palette. */
    Luminous("luminous", storedValue = 3),

    /** The current line hung from a pivot, settling into place after each change. */
    Pendolo("pendolo", storedValue = 5),

    /** Lyrics travelling around one tilted, depth-sorted loop. */
    Claddagh("claddagh", storedValue = 6),

    /** Deterministic multi-layer typography shots compiled from the lyric line. */
    Tempera("tempera", storedValue = 7),
    ;

    companion object {
        val Default: LyricsVisualizerMode = Luminous

        /**
         * Reads the legacy persisted value. Removed prototypes (0, 1, 2 and 4), an
         * unknown future value and a fresh pre-migration value all fall back to the
         * production 流光 renderer.
         */
        fun fromOrdinal(ordinal: Int): LyricsVisualizerMode =
            entries.firstOrNull { it.storedValue == ordinal } ?: Default
    }
}
