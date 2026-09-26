package com.example.asmrplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The crest is authored in a fixed 24 x 24 box and scaled to whatever it is drawn at. */
private const val CrestViewport = 24f

private const val CrestCentreX = 12f
private const val CrestCentreY = 13.4f

/** Wing band radii, chosen so the crescent clears the spade by about one cell. */
private const val WingInnerRadius = 7.4f
private const val WingOuterRadius = 9.0f

/** How far each wing sweeps around the spade: 59 degrees either side of horizontal. */
private const val WingSweep = 118f

/**
 * A decorative crest, poured full by a level.
 *
 * A continuous setting used to end its row in a percentage, which reads as data
 * rather than as a control and is the one part of the wall tools that cannot be
 * taken in at a glance. This is the same reading as the slider underneath, drawn
 * as a shape: the faint crest is the range, and the coloured part is where the
 * setting currently sits, rising from the bottom the way a gauge fills.
 *
 * The crest is symmetric and closed, so the fill is a single clipped rectangle
 * rather than any per-part geometry.
 */
@Composable
internal fun CrestLevelIcon(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
) {
    val level = fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / CrestViewport
        if (unit <= 0f) return@Canvas
        scale(unit, unit, pivot = Offset.Zero) {
            clipPath(CrestPath) {
                drawRect(color = track, size = Size(CrestViewport, CrestViewport))
                val filled = CrestViewport * level
                if (filled > 0f) {
                    drawRect(
                        color = tint,
                        topLeft = Offset(0f, CrestViewport - filled),
                        size = Size(CrestViewport, filled),
                    )
                }
            }
        }
    }
}

/**
 * The crest: a spade flanked by two mirrored crescent wings, crowned by two dots
 * and closed by a third below the tip. Its parts do not overlap, so a single clip
 * of the whole path fills all of them.
 */
private val CrestPath: Path = Path().apply {
    // Spade: a rounded dome narrowing to a point, with the little foot below it.
    moveTo(12f, 3.2f)
    cubicTo(7.4f, 8.4f, 6.6f, 12.2f, 9.6f, 14.8f)
    cubicTo(11.0f, 16.2f, 11.4f, 17.4f, 11.3f, 19.2f)
    lineTo(12.7f, 19.2f)
    cubicTo(12.6f, 17.4f, 13.0f, 16.2f, 14.4f, 14.8f)
    cubicTo(17.4f, 12.2f, 16.6f, 8.4f, 12f, 3.2f)
    close()

    wing(startAngle = 180f - WingSweep / 2f)
    wing(startAngle = -(WingSweep / 2f))

    addOval(Rect(CrestCentreX - 5.55f, 1.65f, CrestCentreX - 3.65f, 3.55f))
    addOval(Rect(CrestCentreX + 3.65f, 1.65f, CrestCentreX + 5.55f, 3.55f))
    addOval(Rect(CrestCentreX - 1.0f, 20.6f, CrestCentreX + 1.0f, 22.6f))
}

/** A crescent band swept around the spade, drawn outer edge first and inner back. */
private fun Path.wing(startAngle: Float) {
    arcTo(arcRect(WingOuterRadius), startAngle, WingSweep, forceMoveTo = true)
    arcTo(
        arcRect(WingInnerRadius),
        startAngle + WingSweep,
        -WingSweep,
        forceMoveTo = false,
    )
    close()
}

private fun arcRect(radius: Float): Rect = Rect(
    left = CrestCentreX - radius,
    top = CrestCentreY - radius,
    right = CrestCentreX + radius,
    bottom = CrestCentreY + radius,
)
