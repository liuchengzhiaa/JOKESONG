package com.example.asmrplayer.ui.lyrics.backgrounds

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.feature.subtitles.driftOffsetFor
import com.example.asmrplayer.ui.lyrics.LyricCoverLayer
import com.example.asmrplayer.ui.lyrics.LyricPaletteGlowLayer
import com.example.asmrplayer.ui.lyrics.LyricsBackground
import com.example.asmrplayer.ui.lyrics.LyricsBackgroundInputs
import com.example.asmrplayer.ui.lyrics.SharpPosterShade
import kotlin.math.roundToInt

/**
 * The readable artwork, shifted a little each time the line changes.
 *
 * This is the one background that moves, and it moves on a line boundary rather than
 * on a clock: the offset is a function of the line index, so the artwork sits exactly
 * where the line says it should, seeking backwards returns it, and a paused line
 * holds perfectly still. At a motion setting of zero the offset is zero and no
 * animation runs at all.
 */
internal object DriftBackground : LyricsBackground {
    @Composable
    override fun Backdrop(inputs: LyricsBackgroundInputs) {
        val settleMillis = (
            DriftSettleMillis * inputs.motionPercent.coerceIn(0, 100) / 100f
            ).roundToInt()
        val offsetDp by animateFloatAsState(
            targetValue = driftOffsetFor(inputs.lineIndex, inputs.motionPercent),
            animationSpec = tween(durationMillis = settleMillis, easing = FastOutSlowInEasing),
            label = "lyric background drift",
        )
        Box(Modifier.fillMaxSize()) {
            LyricCoverLayer(
                item = inputs.item,
                scale = 1.10f,
                alpha = 0.84f,
                blurRadius = 2.dp,
                modifier = Modifier.graphicsLayer { translationX = offsetDp.dp.toPx() },
            )
            LyricPaletteGlowLayer(inputs.palette, shade = SharpPosterShade)
        }
    }
}

private const val DriftSettleMillis = 620
