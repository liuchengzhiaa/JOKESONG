package com.example.asmrplayer.ui.lyrics.backgrounds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.ui.lyrics.LyricsBackground
import com.example.asmrplayer.ui.lyrics.LyricsBackgroundInputs

/**
 * The colour field with a faint grid laid over it.
 *
 * The seam echoes the wall's bento cells, so stepping from the wall into the full
 * player keeps one structural idea rather than switching visual languages. It is
 * drawn at very low contrast on purpose: structure you notice is structure that is
 * competing with the lyric.
 */
internal object LatticeBackground : LyricsBackground {
    @Composable
    override fun Backdrop(inputs: LyricsBackgroundInputs) {
        Box(Modifier.fillMaxSize()) {
            ColourFieldBackground.Backdrop(inputs)
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val step = LatticeStep.toPx()
                        val hairline = LatticeStroke.toPx()
                        val seam = Color.Black.copy(alpha = LatticeAlpha)
                        onDrawBehind {
                            var x = step
                            while (x < size.width) {
                                drawLine(
                                    color = seam,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = hairline,
                                )
                                x += step
                            }
                            var y = step
                            while (y < size.height) {
                                drawLine(
                                    color = seam,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = hairline,
                                )
                                y += step
                            }
                        }
                    },
            )
        }
    }
}

private val LatticeStep = 92.dp
private val LatticeStroke = 1.dp
private const val LatticeAlpha = 0.18f
