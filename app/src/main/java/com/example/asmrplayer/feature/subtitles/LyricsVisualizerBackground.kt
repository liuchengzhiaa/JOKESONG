package com.example.asmrplayer.feature.subtitles

/**
 * Stable set of full-screen backdrops for the immersive lyric scene.
 *
 * Backgrounds are the second registry and are deliberately orthogonal to
 * [LyricsVisualizerMode]: a background owns the artwork treatment and the shade
 * laid over it, never the lyric typography, and any background combines with any
 * mode. The ordinal is a persisted user setting, so entries may only be appended.
 */
enum class LyricsVisualizerBackground(val stableId: String) {
    /** Artwork large, dim and heavily blurred behind the palette light field. */
    BlurredCover("blurred_cover"),

    /** Artwork nearly unscaled and readable, darkened towards its left edge. */
    SharpPoster("sharp_poster"),

    /** Artwork large behind a radial pool of darkness. */
    DarkSpotlight("dark_spotlight"),

    /** No artwork at all: the cover palette's own light field on a near-black base. */
    ColourField("colour_field"),

    /** The colour field with a faint grid, echoing the wall's bento seams. */
    Lattice("lattice"),

    /** The readable artwork, shifted slightly each time the line changes. */
    Drift("drift"),
    ;

    companion object {
        val Default: LyricsVisualizerBackground = BlurredCover

        fun fromOrdinal(ordinal: Int): LyricsVisualizerBackground =
            entries.getOrNull(ordinal) ?: Default
    }
}

/**
 * The background a mode falls back to.
 *
 * Every mode declares one, so a user who never opens the background picker sees the
 * treatment that mode was designed around -- and for the original three that is
 * exactly the treatment they had before backgrounds existed as a separate choice.
 */
val LyricsVisualizerMode.defaultBackground: LyricsVisualizerBackground
    get() = when (this) {
        LyricsVisualizerMode.Luminous -> LyricsVisualizerBackground.ColourField
        LyricsVisualizerMode.Pendolo -> LyricsVisualizerBackground.BlurredCover
        LyricsVisualizerMode.Claddagh -> LyricsVisualizerBackground.SharpPoster
        LyricsVisualizerMode.Tempera -> LyricsVisualizerBackground.ColourField
    }

/**
 * Resolves the stored choice.
 *
 * `null` is not "unset" but a real choice -- follow the mode -- which is what keeps
 * an install that predates this setting looking exactly as it did, and an
 * out-of-range ordinal degrades the same way instead of failing.
 */
fun resolveLyricsBackground(
    storedOrdinal: Int?,
    mode: LyricsVisualizerMode,
): LyricsVisualizerBackground = storedOrdinal
    ?.let { ordinal -> LyricsVisualizerBackground.entries.getOrNull(ordinal) }
    ?: mode.defaultBackground
