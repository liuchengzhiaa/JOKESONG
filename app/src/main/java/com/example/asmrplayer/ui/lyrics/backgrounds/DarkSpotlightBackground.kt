package com.example.asmrplayer.ui.lyrics.backgrounds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.ui.lyrics.DarkSpotlightShade
import com.example.asmrplayer.ui.lyrics.LyricCoverLayer
import com.example.asmrplayer.ui.lyrics.LyricPaletteGlowLayer
import com.example.asmrplayer.ui.lyrics.LyricsBackground
import com.example.asmrplayer.ui.lyrics.LyricsBackgroundInputs

/**
 * The artwork large behind a radial pool of darkness. This is the treatment the
 * spotlight presentation has always had, unchanged.
 */
internal object DarkSpotlightBackground : LyricsBackground {
    @Composable
    override fun Backdrop(inputs: LyricsBackgroundInputs) {
        Box(Modifier.fillMaxSize()) {
            LyricCoverLayer(
                item = inputs.item,
                scale = 1.18f,
                alpha = 0.54f,
                blurRadius = 38.dp,
            )
            LyricPaletteGlowLayer(inputs.palette, shade = DarkSpotlightShade)
        }
    }
}
