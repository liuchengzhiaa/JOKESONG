package com.example.asmrplayer.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import com.example.asmrplayer.core.artwork.*
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.feature.focuswall.wallMotionDuration
import com.example.asmrplayer.ui.theme.artworkAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Blended source-region colours, cached independently of the playback-position ticker. */
@Composable
internal fun PlayerBackdrop(
    item: LibraryItem,
    facts: CoverFacts?,
    loading: Boolean,
    motionPercent: Int,
    modifier: Modifier = Modifier,
) {
    var texture by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(facts?.playerField, loading, item.paletteSeed, facts?.playerToneArgb) {
        // Hold the old field during a track's bounded read, not after a failed/missing read.
        if (loading && texture != null) return@LaunchedEffect
        val field = playerBackdropField(facts, artworkAccent(item.paletteSeed).toArgb())
        texture = withContext(Dispatchers.Default) {
            val pixels = renderPlayerColourField(field)
            Bitmap.createBitmap(pixels, PlayerFieldWidth, PlayerFieldHeight, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }
    Crossfade(
        targetState = texture,
        animationSpec = tween(wallMotionDuration(700, motionPercent)),
        modifier = modifier.fillMaxSize(),
        label = "playerColourField",
    ) { bitmap ->
        Canvas(Modifier.fillMaxSize()) {
            if (bitmap == null) drawRect(Color(0xFF18151C))
            else drawImage(bitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Medium)
        }
    }
}

internal fun playerBackdropField(facts: CoverFacts?, fallbackArgb: Int): PlayerColourField =
    facts?.playerField ?: PlayerColourField(List(9) { facts?.playerToneArgb ?: fallbackArgb }, 0.20f)
