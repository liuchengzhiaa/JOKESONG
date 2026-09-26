package com.example.asmrplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.asmrplayer.core.settings.UiAccentPalette

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ED0FF),
    onPrimary = Color(0xFF003354),
    secondary = Color(0xFFC5C7FF),
    background = Color(0xFF090B10),
    surface = Color(0xFF10131A),
    surfaceVariant = Color(0xFF20242E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00639A),
    secondary = Color(0xFF565D8C),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun AsmrPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentPalette: UiAccentPalette = UiAccentPalette.Ocean,
    uiScalePercent: Int = 100,
    content: @Composable () -> Unit,
) {
    val accent = accentPalette.accent(darkTheme)
    val onAccent = accentContentColor(accent)
    val base = if (darkTheme) DarkColors else LightColors
    val scheme = remember(darkTheme, accentPalette) {
        base.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = if (darkTheme) 0.30f else 0.18f)
                .compositeOver(base.surface),
            onPrimaryContainer = accent,
            secondary = accent,
            secondaryContainer = accent.copy(alpha = if (darkTheme) 0.22f else 0.14f)
                .compositeOver(base.surface),
            onSecondaryContainer = accent,
        )
    }
    val baseDensity = LocalDensity.current
    val uiScale = uiScalePercent.coerceIn(85, 120) / 100f
    val scaledDensity = remember(baseDensity, uiScale) {
        Density(
            density = baseDensity.density * uiScale,
            fontScale = baseDensity.fontScale * uiScale,
        )
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * The accent as it appears in a given theme. Internal rather than private because
 * the settings swatches must show the same colour the app will actually use, and a
 * second copy of these values would eventually disagree with this one.
 *
 * Light and dark are different colours on purpose: the light variant has to hold up
 * as text on a white surface, the dark one on a near-black one.
 */
internal fun UiAccentPalette.accent(darkTheme: Boolean): Color = when (this) {
    UiAccentPalette.Ocean -> if (darkTheme) Color(0xFF9ED0FF) else Color(0xFF00639A)
    UiAccentPalette.Lavender -> if (darkTheme) Color(0xFFC8B6FF) else Color(0xFF6547A8)
    UiAccentPalette.Amber -> if (darkTheme) Color(0xFFFFC66D) else Color(0xFF8B5000)
    UiAccentPalette.Mint -> if (darkTheme) Color(0xFF7DE2C1) else Color(0xFF006B57)
    UiAccentPalette.Rose -> if (darkTheme) Color(0xFFFFB0C8) else Color(0xFF9C3155)
}

/** What stays readable on top of [accent]: dark ink on the pale variants, white on the deep ones. */
internal fun accentContentColor(accent: Color): Color =
    if (accent.luminance() > 0.52f) Color(0xFF101216) else Color.White

private fun Color.compositeOver(background: Color): Color {
    val outAlpha = alpha + background.alpha * (1f - alpha)
    if (outAlpha <= 0f) return Color.Transparent
    return Color(
        red = (red * alpha + background.red * background.alpha * (1f - alpha)) / outAlpha,
        green = (green * alpha + background.green * background.alpha * (1f - alpha)) / outAlpha,
        blue = (blue * alpha + background.blue * background.alpha * (1f - alpha)) / outAlpha,
        alpha = outAlpha,
    )
}
