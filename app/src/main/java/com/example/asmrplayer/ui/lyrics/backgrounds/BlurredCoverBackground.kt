package com.example.asmrplayer.ui.lyrics.backgrounds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.ui.lyrics.BlurredCoverShade
import com.example.asmrplayer.ui.lyrics.LyricCoverLayer
import com.example.asmrplayer.ui.lyrics.LyricPaletteGlowLayer
import com.example.asmrplayer.ui.lyrics.LyricsBackground
import com.example.asmrplayer.ui.lyrics.LyricsBackgroundInputs

/**
 * The artwork large, dim and heavily blurred, with the palette light field glowing
 * through it. This is the treatment the flow presentation has always had, unchanged.
 */
internal object BlurredCoverBackground : LyricsBackground {
    @Composable
    override fun Backdrop(inputs: LyricsBackgroundInputs) {
        Box(Modifier.fillMaxSize()) {
            LyricCoverLayer(
                item = inputs.item,
                scale = 1.12f,
                alpha = 0.72f,
                blurRadius = 26.dp,
            )
            LyricPaletteGlowLayer(inputs.palette, shade = BlurredCoverShade)
        }
    }
}
