package com.example.asmrplayer.ui.lyrics.backgrounds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.ui.lyrics.LyricCoverLayer
import com.example.asmrplayer.ui.lyrics.LyricPaletteGlowLayer
import com.example.asmrplayer.ui.lyrics.LyricsBackground
import com.example.asmrplayer.ui.lyrics.LyricsBackgroundInputs
import com.example.asmrplayer.ui.lyrics.SharpPosterShade

/**
 * The artwork kept readable, darkened towards its left edge. This is the treatment
 * the poster presentation has always had, unchanged.
 */
internal object SharpPosterBackground : LyricsBackground {
    @Composable
    override fun Backdrop(inputs: LyricsBackgroundInputs) {
        Box(Modifier.fillMaxSize()) {
            LyricCoverLayer(
                item = inputs.item,
                scale = 1.02f,
                alpha = 0.88f,
                blurRadius = 2.dp,
            )
            LyricPaletteGlowLayer(inputs.palette, shade = SharpPosterShade)
        }
    }
}
