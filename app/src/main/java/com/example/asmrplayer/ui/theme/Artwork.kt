package com.example.asmrplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val ArtworkPalettes = listOf(
    listOf(Color(0xFF315C72), Color(0xFF101927)),
    listOf(Color(0xFF74586A), Color(0xFF211420)),
    listOf(Color(0xFF476458), Color(0xFF101B17)),
    listOf(Color(0xFF73584B), Color(0xFF251711)),
    listOf(Color(0xFF4E557C), Color(0xFF121526)),
)

/**
 * Deterministic placeholder cover for an item whose artwork has not resolved
 * yet. The same seed always produces the same gradient.
 */
fun artworkBrush(seed: Int): Brush = Brush.linearGradient(ArtworkPalettes[(seed - 1).mod(ArtworkPalettes.size)])

/** Accent used while a real cover is absent or its colour sample is loading. */
fun artworkAccent(seed: Int): Color = ArtworkPalettes[(seed - 1).mod(ArtworkPalettes.size)].first()
