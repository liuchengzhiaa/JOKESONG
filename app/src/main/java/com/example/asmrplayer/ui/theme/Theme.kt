package com.example.asmrplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
