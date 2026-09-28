package com.example.asmrplayer.ui.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asmrplayer.core.subtitles.TimedTextLine
import com.example.asmrplayer.feature.subtitles.lyricGlowTimeline
import com.example.asmrplayer.ui.GlowingLyricText

/**
 * The current line, drawn by the one wipe renderer the whole app shares.
 *
 * Every mode goes through here, so the per-grapheme glow policy -- including the
 * exact/estimated distinction -- reads identically in all of them and cannot be
 * reimplemented differently by a new mode.
 */
@Composable
internal fun GlowingCurrentLineText(
    inputs: LyricsSceneInputs,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    textAlign: TextAlign,
    maxLines: Int,
    overflow: TextOverflow,
    modifier: Modifier = Modifier,
    edgeSoftnessPx: Float = 0f,
    glowStrength: Float = 0f,
) {
    val line = inputs.frame.current
    val timeline = remember(line, inputs.frame.next?.startMillis, inputs.format, inputs.wordGlowMode) {
        line.lyricGlowTimeline(
            format = inputs.format,
            nextLineStartMillis = inputs.frame.next?.startMillis,
            mode = inputs.wordGlowMode,
        )
    }
    GlowingLyricText(
        text = line.text,
        timeline = timeline,
        positionMs = inputs.positionMs,
        isPlaying = inputs.isPlaying,
        accent = inputs.accent,
        motionPercent = inputs.motionPercent,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
        edgeSoftnessPx = edgeSoftnessPx,
        glowStrength = glowStrength,
    )
}

/**
 * A neighbouring line, held at a fixed height so the current line does not jump as
 * the window moves. Tapping one seeks to it.
 */
@Composable
internal fun ContextLyric(
    line: TimedTextLine?,
    alpha: Float,
    fontSize: Int,
    height: Int = 52,
    onSeekTo: (Long) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (line != null) {
            Text(
                text = line.text,
                color = Color.White.copy(alpha = alpha),
                fontSize = fontSize.sp,
                lineHeight = (fontSize + 6).sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeekTo(line.startMillis) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
