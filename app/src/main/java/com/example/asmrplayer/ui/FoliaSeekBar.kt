package com.example.asmrplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.core.media.PlaybackState

/**
 * Folia-style one-pixel timeline with a larger invisible touch band.
 *
 * The wall, full player and lyric scene share this implementation so seeking never
 * changes shape or sensitivity when the user moves between surfaces.
 */
@Composable
fun FoliaSeekBar(
    playbackState: PlaybackState,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
) {
    var scrubFraction by remember(playbackState.mediaId) { mutableStateOf<Float?>(null) }
    val shownFraction = scrubFraction ?: playbackState.progress
    val enabled = playbackState.hasDuration
    Canvas(
        modifier = modifier
            .height(22.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(shownFraction, 0f..1f)
                if (enabled) {
                    setProgress { requested ->
                        onSeekTo(playbackState.positionForFraction(requested))
                        true
                    }
                }
            }
            .pointerInput(playbackState.mediaId, playbackState.durationMs, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    fun fractionAt(x: Float): Float = (x / size.width).coerceIn(0f, 1f)
                    var latest = fractionAt(down.position.x)
                    scrubFraction = latest
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        latest = fractionAt(change.position.x)
                        scrubFraction = latest
                        change.consume()
                    } while (change.pressed)
                    onSeekTo(playbackState.positionForFraction(latest))
                    scrubFraction = null
                }
            },
    ) {
        val centerY = size.height / 2f
        val stroke = 1.dp.toPx()
        drawLine(
            color = Color.White.copy(alpha = if (enabled) 0.30f else 0.14f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = stroke,
        )
        val activeX = size.width * shownFraction.coerceIn(0f, 1f)
        drawLine(
            color = activeColor.copy(alpha = if (enabled) 0.94f else 0.34f),
            start = Offset(0f, centerY),
            end = Offset(activeX, centerY),
            strokeWidth = stroke,
        )
        if (enabled) {
            drawCircle(
                color = activeColor.copy(alpha = 0.98f),
                radius = 1.7.dp.toPx(),
                center = Offset(activeX, centerY),
            )
        }
    }
}
