package com.example.asmrplayer.ui.lyrics

import androidx.compose.runtime.Composable
import com.example.asmrplayer.feature.subtitles.LyricsVisualizerBackground
import com.example.asmrplayer.ui.lyrics.backgrounds.BlurredCoverBackground
import com.example.asmrplayer.ui.lyrics.backgrounds.ColourFieldBackground
import com.example.asmrplayer.ui.lyrics.backgrounds.DarkSpotlightBackground
import com.example.asmrplayer.ui.lyrics.backgrounds.DriftBackground
import com.example.asmrplayer.ui.lyrics.backgrounds.LatticeBackground
import com.example.asmrplayer.ui.lyrics.backgrounds.SharpPosterBackground

/**
 * One full-screen backdrop: the artwork treatment plus the shade over it.
 *
 * A background is handed [LyricsBackgroundInputs] and nothing else, so it physically
 * cannot reach the lyric text. That is the orthogonality between the two registries,
 * enforced by the type rather than by a comment.
 */
internal interface LyricsBackground {
    @Composable
    fun Backdrop(inputs: LyricsBackgroundInputs)
}

/** Exhaustive like the visualizer registry: a new background must be implemented. */
internal fun LyricsVisualizerBackground.backdrop(): LyricsBackground = when (this) {
    LyricsVisualizerBackground.BlurredCover -> BlurredCoverBackground
    LyricsVisualizerBackground.SharpPoster -> SharpPosterBackground
    LyricsVisualizerBackground.DarkSpotlight -> DarkSpotlightBackground
    LyricsVisualizerBackground.ColourField -> ColourFieldBackground
    LyricsVisualizerBackground.Lattice -> LatticeBackground
    LyricsVisualizerBackground.Drift -> DriftBackground
}
