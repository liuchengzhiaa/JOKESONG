package com.example.asmrplayer.feature.subtitles

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Geometry shared by the Claddagh guide and every lyric carried around it. */
data class CladdaghOrbit(
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float,
    val rotationDegrees: Float,
    val focalAngleRadians: Float,
    val radiansPerLine: Float,
)

/** One line's camera-like projection from the tilted lyric loop. */
data class CladdaghLinePose(
    val centerX: Float,
    val centerY: Float,
    val rotationDegrees: Float,
    val scale: Float,
    val alpha: Float,
    val blurRadiusDp: Float,
)

/**
 * The loop deliberately scales from the shorter side. A portrait phone and a
 * landscape phone therefore show the same physical orbit instead of stretching it
 * until its curvature disappears on the wider screen.
 */
fun claddaghOrbit(width: Float, height: Float): CladdaghOrbit {
    val safeWidth = width.coerceAtLeast(1f)
    val safeHeight = height.coerceAtLeast(1f)
    val shortSide = minOf(safeWidth, safeHeight)
    return CladdaghOrbit(
        centerX = safeWidth * 0.54f,
        centerY = safeHeight * 0.66f,
        radiusX = shortSide * 0.62f,
        radiusY = shortSide * 0.25f,
        rotationDegrees = -18f,
        // The focal sentence sits at the top of the unrotated ellipse. Its tangent
        // is horizontal there, so after the orbit tilt the type leans by only 18°
        // rather than standing vertically on the rightmost point.
        focalAngleRadians = (-PI / 2.0).toFloat(),
        radiansPerLine = 0.39f,
    )
}

/**
 * Projects a document-line distance onto the same ellipse every time.
 *
 * Distance zero is the line being sung. Earlier lines trail down-left and future
 * lines climb up-right; depth falls away exponentially so the rear of the loop is
 * suggested without filling the screen with an unreadable second lyric list.
 */
fun claddaghLinePose(
    distanceFromFocal: Float,
    width: Float,
    height: Float,
): CladdaghLinePose {
    val orbit = claddaghOrbit(width, height)
    val theta = orbit.focalAngleRadians + distanceFromFocal * orbit.radiansPerLine
    val localX = orbit.radiusX * cos(theta)
    val localY = orbit.radiusY * sin(theta)
    val rotation = Math.toRadians(orbit.rotationDegrees.toDouble())
    val cosRotation = cos(rotation).toFloat()
    val sinRotation = sin(rotation).toFloat()
    val x = orbit.centerX + localX * cosRotation - localY * sinRotation
    val y = orbit.centerY + localX * sinRotation + localY * cosRotation

    val derivativeX = -orbit.radiusX * sin(theta)
    val derivativeY = orbit.radiusY * cos(theta)
    val tangentX = derivativeX * cosRotation - derivativeY * sinRotation
    val tangentY = derivativeX * sinRotation + derivativeY * cosRotation
    val tangentDegrees = Math.toDegrees(atan2(tangentY, tangentX).toDouble()).toFloat()
    val depth = exp(-abs(distanceFromFocal) * DepthFalloff).coerceIn(0f, 1f)
    return CladdaghLinePose(
        centerX = x,
        centerY = y,
        rotationDegrees = tangentDegrees.coerceIn(-MaximumTypeTilt, MaximumTypeTilt),
        scale = MinimumScale + (MaximumScale - MinimumScale) * depth,
        alpha = MinimumAlpha + (MaximumAlpha - MinimumAlpha) * depth,
        blurRadiusDp = MaximumBlurDp * (1f - depth),
    )
}

private const val DepthFalloff = 0.34f
private const val MinimumScale = 0.55f
private const val MaximumScale = 1.08f
private const val MinimumAlpha = 0.10f
private const val MaximumAlpha = 0.96f
private const val MaximumBlurDp = 4.2f
private const val MaximumTypeTilt = 42f
