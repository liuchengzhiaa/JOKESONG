package com.example.asmrplayer.ui.lyrics.backgrounds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.asmrplayer.ui.lyrics.ColourFieldGlowAlphas
import com.example.asmrplayer.ui.lyrics.LyricPaletteGlowLayer
import com.example.asmrplayer.ui.lyrics.LyricsBackground
import com.example.asmrplayer.ui.lyrics.LyricsBackgroundInputs
import com.example.asmrplayer.ui.lyrics.SceneBaseShade

/**
 * No artwork: the cover's own palette carried as a wider, brighter light field over
 * the scene's near-black base. The point is that the colour survives the cover being
 * taken away, so the scene still belongs to this track and not to the player chrome.
 */
internal object ColourFieldBackground : LyricsBackground {
    @Composable
    override fun Backdrop(inputs: LyricsBackgroundInputs) {
        Box(Modifier.fillMaxSize()) {
            LyricPaletteGlowLayer(
                palette = inputs.palette,
                shade = SceneBaseShade,
                glowAlphas = ColourFieldGlowAlphas,
            )
        }
    }
}
