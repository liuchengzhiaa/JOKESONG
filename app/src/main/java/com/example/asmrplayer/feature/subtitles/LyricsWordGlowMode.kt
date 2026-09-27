package com.example.asmrplayer.feature.subtitles

/**
 * How the immersive lyric scene obtains the timeline for its moving glow.
 *
 * Estimation is deliberately a presentation concern. [Smart] never adds guessed
 * words to the parsed timed-text document, so callers can still tell exactly what
 * the source file supplied.
 */
enum class LyricsWordGlowMode(val stableId: String) {
    /** Draw the current line normally, without a moving highlight. */
    Off("off"),

    /** Prefer source word timing and estimate grapheme timing for line-only text. */
    Smart("smart"),

    /** Animate only when the source file supplied real word timestamps. */
    ExactOnly("exact_only"),
    ;

    companion object {
        val Default: LyricsWordGlowMode = Smart

        fun fromOrdinal(ordinal: Int): LyricsWordGlowMode =
            entries.getOrNull(ordinal) ?: Default
    }
}
