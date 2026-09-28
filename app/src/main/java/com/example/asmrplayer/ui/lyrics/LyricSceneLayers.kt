package com.example.asmrplayer.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.ui.theme.artworkBrush

/**
 * The two layers every background is built from.
 *
 * They are extracted so that a background is a *choice* of artwork treatment and
 * shade rather than a copy of the whole drawing pass: the three original looks are
 * now three parameter sets over these two layers, which is what keeps them
 * pixel-identical to what they were before backgrounds became a separate setting.
 */

/** The artwork at a given scale, opacity and blur, falling back to its palette gradient. */
@Composable
internal fun LyricCoverLayer(
    item: LibraryItem,
    scale: Float,
    alpha: Float,
    blurRadius: Dp,
    modifier: Modifier = Modifier,
) {
    if (item.artworkUri != null) {
        AsyncImage(
            model = item.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .blur(blurRadius),
        )
    } else {
        Box(modifier.fillMaxSize().background(artworkBrush(item.paletteSeed)))
    }
}

/** A shade brush, built from the canvas it will cover. */
internal typealias ShadeBrush = (Size) -> Brush

/**
 * The palette light field and the shade over it, drawn in one pass.
 *
 * The brushes are rebuilt only when the palette changes. There is no ambient
 * animation here and therefore no visualizer frame loop.
 */
@Composable
internal fun LyricPaletteGlowLayer(
    palette: List<Color>,
    shade: ShadeBrush,
    modifier: Modifier = Modifier,
    glowAlphas: List<Float> = DefaultGlowAlphas,
) {
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val first = palette.firstOrNull() ?: FallbackPaletteColour
                val second = palette.getOrElse(1) { first }
                val third = palette.getOrElse(2) { second }
                val upperGlow = Brush.radialGradient(
                    colors = listOf(first.copy(alpha = glowAlphas[0]), Color.Transparent),
                    center = Offset(size.width * 0.16f, size.height * 0.18f),
                    radius = size.maxDimension * 0.70f,
                )
                val lowerGlow = Brush.radialGradient(
                    colors = listOf(second.copy(alpha = glowAlphas[1]), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.78f),
                    radius = size.maxDimension * 0.72f,
                )
                val sideGlow = Brush.radialGradient(
                    colors = listOf(third.copy(alpha = glowAlphas[2]), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.28f),
                    radius = size.maxDimension * 0.48f,
                )
                val shadeBrush = shade(size)
                onDrawBehind {
                    drawRect(upperGlow)
                    drawRect(lowerGlow)
                    drawRect(sideGlow)
                    drawRect(shadeBrush)
                }
            },
    )
}

/** The three light pools every background starts from. */
private val DefaultGlowAlphas = listOf(0.50f, 0.36f, 0.22f)

/** A brighter, wider field for the backgrounds that show no artwork at all. */
internal val ColourFieldGlowAlphas = listOf(0.62f, 0.48f, 0.34f)

/** Shown while a cover has no extracted palette yet, as before. */
private val FallbackPaletteColour = Color(0xFF315C72)

/** The shade the flow presentation has always used over its blurred artwork. */
internal val BlurredCoverShade: ShadeBrush = {
    Brush.verticalGradient(
        listOf(
            Color.Black.copy(alpha = 0.54f),
            Color.Black.copy(alpha = 0.63f),
            Color.Black.copy(alpha = 0.88f),
        ),
    )
}

/** The shade the poster presentation has always used over its readable artwork. */
internal val SharpPosterShade: ShadeBrush = {
    Brush.horizontalGradient(
        listOf(
            Color.Black.copy(alpha = 0.82f),
            Color.Black.copy(alpha = 0.48f),
            Color.Black.copy(alpha = 0.66f),
        ),
    )
}

/** The shade the spotlight presentation has always used, centred on the canvas. */
internal val DarkSpotlightShade: ShadeBrush = { size ->
    Brush.radialGradient(
        listOf(
            Color.Black.copy(alpha = 0.36f),
            Color.Black.copy(alpha = 0.88f),
        ),
        center = Offset(size.width * 0.5f, size.height * 0.48f),
        radius = size.maxDimension * 0.72f,
    )
}

/** A gentler base for the backgrounds that show no artwork at all. */
internal val SceneBaseShade: ShadeBrush = {
    Brush.verticalGradient(
        listOf(
            Color.Black.copy(alpha = 0.24f),
            Color.Black.copy(alpha = 0.28f),
            Color.Black.copy(alpha = 0.44f),
        ),
    )
}
