package com.example.asmrplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.asmrplayer.core.artwork.CoverAccentLoader
import com.example.asmrplayer.core.artwork.CoverFacts
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.ui.theme.artworkBrush

/** Cached raw dominant colour family, painted as a smooth field without an artwork overlay. */
@Composable
internal fun PlayerBackdrop(item: LibraryItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var facts by remember(item.id, item.artworkUri) { mutableStateOf<CoverFacts?>(null) }

    // Null first, then the real palette. The page paints the placeholder gradient in
    // the gap, so the only thing the wait costs is the colour, never a blank screen.
    LaunchedEffect(item.id, item.artworkUri) {
        facts = CoverAccentLoader.resolve(context, item.artworkUri)
    }

    // Keep the dominant colour family, not the wall's brightness-balanced lights.
    val stops = playerTonalStops(facts)
    val wash = if (stops.isEmpty()) artworkBrush(item.paletteSeed)
        else Brush.verticalGradient(0f to stops[0], 0.32f to stops[1], 1f to stops[2])
    val glow = playerTonalStops(facts, hueOffset = 20f).getOrNull(1)?.copy(alpha = 0.55f)
    Box(modifier.fillMaxSize().background(wash).drawWithCache {
        val light = glow?.let { Brush.radialGradient(
            listOf(it, Color.Transparent), center = Offset(size.width, size.height * 0.62f),
            radius = size.width * 1.1f,
        ) }
        onDrawBehind { if (light != null) drawRect(light) }
    })
}

internal fun playerTonalStops(facts: CoverFacts?, hueOffset: Float = 0f): List<Color> {
    val dominant = facts?.playerToneArgb ?: return emptyList()
    val color = Color(dominant)
    val high = maxOf(color.red, color.green, color.blue)
    val low = minOf(color.red, color.green, color.blue)
    val delta = high - low
    val hue = when {
        delta < 0.001f -> 0f
        high == color.red -> 60f * ((color.green - color.blue) / delta % 6f)
        high == color.green -> 60f * ((color.blue - color.red) / delta + 2f)
        else -> 60f * ((color.red - color.green) / delta + 4f)
    }.let { (it + hueOffset + 360f) % 360f }
    val saturation = (if (high > 0f) delta / high else 0f).coerceIn(0.50f, 0.68f)
    // Preserve hue, not source brightness. Salt references have a dark rich crown
    // and a softer mid/bottom field, rather than the prior additive pink whitening.
    return listOf(
        Color.hsv(hue, (saturation + 0.28f).coerceAtMost(0.90f), 0.29f),
        Color.hsv((hue + 354f) % 360f, saturation, 0.55f),
        Color.hsv((hue + 350f) % 360f, saturation, 0.54f),
    )
}
