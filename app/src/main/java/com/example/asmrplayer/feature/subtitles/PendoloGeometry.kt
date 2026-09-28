package com.example.asmrplayer.feature.subtitles

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Where the escapement wheel sits and how big it is.
 *
 * All of it is a function of the stage and the tuning, never of playback, so the
 * clockwork does not move when a line changes -- only its angle does. That split is
 * what lets the drawn gear and the lyric arc share one rotation value instead of
 * drifting apart.
 */
data class PendoloWheel(
    val centerX: Float,
    val centerY: Float,
    /** Radius of the drawn escapement gear. */
    val baseRadius: Float,
    /** Radius the lyric lines sit on: just outside the gear, never on top of it. */
    val lyricRadius: Float,
    /** Degrees between two adjacent lines along the arc. */
    val angleStepDegrees: Float,
    /** Width one line may occupy, already allowing for the focal line's scale-up. */
    val availableTextWidth: Float,
)

/** One line placed on the arc. */
data class PendoloLineItem(
    /** Index into the rail window's lines. */
    val offset: Int,
    /**
     * Angle from the focal axis in degrees. Negative is above the axis, positive
     * below: upcoming lines curve away downwards, the way a wheel turning clockwise
     * carries them.
     */
    val angleDegrees: Float,
    val x: Float,
    val y: Float,
    /** True for the line the wheel is anchored on. */
    val isFocal: Boolean,
    /** How many lines away from the anchor this one sits. */
    val distanceFromAnchor: Float,
    val alpha: Float,
    val scale: Float,
)

/** How many lines are visible across the arc at once. */
private const val PendoloVisibleWindowCount = 9

/** Past this angle a line has left the visible semicircle and is not drawn. */
private const val PendoloCullAngleDegrees = 90f

/** The angle a line is actually tilted by, as a fraction of its position on the arc. */
const val PendoloLineTiltFraction = 0.35f

/** How much of the rail's swing each line cancels out, so text stays readable. */
const val PendoloTextSwingCorrectionFraction = 0.65f

fun pendoloWheel(width: Float, height: Float, tuning: PendoloTuning): PendoloWheel {
    val centerX = width * tuning.wheelCenterXFraction
    val centerY = height * tuning.wheelCenterYFraction
    val shorterSide = min(width, height)
    val baseRadius = shorterSide * tuning.arcRadiusFraction
    val lyricRadius = baseRadius + shorterSide * PendoloLyricRadiusOffsetFraction
    val arcDegrees = tuning.arcAngleDegrees.toFloat()
    return PendoloWheel(
        centerX = centerX,
        centerY = centerY,
        baseRadius = baseRadius,
        lyricRadius = lyricRadius,
        angleStepDegrees = arcDegrees / (PendoloVisibleWindowCount - 1),
        availableTextWidth = max(
            PendoloMinimumTextWidth,
            min(width * PendoloTextWidthFraction, width - (centerX + baseRadius) - PendoloTextWidthGutter),
        ),
    )
}

/**
 * Places every line of [window] on the arc.
 *
 * The angle between two neighbours is not the even step: it is derived from the two
 * lines' measured heights, so a long line and a short one never overlap even though
 * the arc's angular budget is fixed. A line's *tilt* is only a third of its position
 * on the arc -- lines sit on a wheel but stay closer to horizontal than the wheel
 * would put them, which is what keeps them readable at the arc's ends.
 *
 * [lineBlockHeights] is optional and may be short; a missing height falls back to the
 * even step, so the wheel is still well formed before the text has been measured.
 */
fun pendoloLineItems(
    window: LyricRailWindow,
    wheel: PendoloWheel,
    tuning: PendoloTuning,
    lineBlockHeights: List<Float> = emptyList(),
): List<PendoloLineItem> {
    if (window.isEmpty) return emptyList()

    val angles = pendoloVisualAngles(window, wheel, lineBlockHeights)
    val activeScale = tuning.activeScale

    return window.lines.indices.mapNotNull { offset ->
        val distance = window.distanceFromAnchor(offset)
        val angleDegrees = angles[offset] ?: (distance * wheel.angleStepDegrees)
        if (abs(angleDegrees) >= PendoloCullAngleDegrees) return@mapNotNull null

        val angleRadians = Math.toRadians(angleDegrees.toDouble())
        val isFocal = window.hasAnchor && abs(distance) < PendoloFocalTolerance
        val absoluteDistance = abs(distance)

        PendoloLineItem(
            offset = offset,
            angleDegrees = angleDegrees,
            x = wheel.centerX + wheel.lyricRadius * cos(angleRadians).toFloat(),
            y = wheel.centerY + wheel.lyricRadius * sin(angleRadians).toFloat(),
            isFocal = isFocal,
            distanceFromAnchor = absoluteDistance,
            alpha = pendoloLineAlpha(angleDegrees, absoluteDistance),
            scale = if (isFocal) activeScale else max(PendoloMinimumNeighbourScale, 1f - absoluteDistance * PendoloNeighbourScaleStep),
        )
    }
}

/**
 * The angle of each line on the arc, relative to the anchor.
 *
 * Walks outwards from the anchor in both directions, which is what makes the wheel
 * locally rigid: a line's position depends on the lines immediately beside it, so a
 * long line pushes its neighbours rather than being overlapped by them.
 */
private fun pendoloVisualAngles(
    window: LyricRailWindow,
    wheel: PendoloWheel,
    lineBlockHeights: List<Float>,
): Map<Int, Float> {
    val angles = mutableMapOf<Int, Float>()
    val anchorOffset = window.anchor.roundToOffset()
    if (anchorOffset !in window.lines.indices) return angles

    angles[anchorOffset] = 0f
    for (offset in anchorOffset + 1..window.lines.lastIndex) {
        angles[offset] = (angles[offset - 1] ?: 0f) + pendoloSpacing(
            previousHeight = lineBlockHeights.getOrElse(offset - 1) { 0f },
            currentHeight = lineBlockHeights.getOrElse(offset) { 0f },
            wheel = wheel,
        )
    }
    for (offset in anchorOffset - 1 downTo 0) {
        angles[offset] = (angles[offset + 1] ?: 0f) - pendoloSpacing(
            previousHeight = lineBlockHeights.getOrElse(offset + 1) { 0f },
            currentHeight = lineBlockHeights.getOrElse(offset) { 0f },
            wheel = wheel,
        )
    }
    return angles
}

/**
 * The angle two neighbouring lines need so their blocks clear each other.
 *
 * Two blocks of height h1 and h2 sitting on a circle of radius r need an arc of
 * (h1 + h2) / 2 each side of the point between them; the small constant is the gap
 * that keeps ascenders and descenders from touching when the measurement rounds low.
 */
private fun pendoloSpacing(
    previousHeight: Float,
    currentHeight: Float,
    wheel: PendoloWheel,
): Float {
    if (wheel.lyricRadius <= 0f) return wheel.angleStepDegrees
    val clearance = (previousHeight + currentHeight + PendoloLineGapPx) / (2f * wheel.lyricRadius)
    return max(wheel.angleStepDegrees, Math.toDegrees(clearance.toDouble()).toFloat())
}

/**
 * How visible a line is, by where it sits on the arc.
 *
 * Brightest on the focal axis and falling away on both sides, with a floor so the
 * far lines are still legible rather than vanishing. Both terms are needed: the angle
 * term alone is still at 0.56 where a line is culled, which would leave the whole arc
 * nearly equally loud, so the distance term is what actually takes the far lines down
 * to the floor. The floor is what makes the arc read as continuing past the window
 * rather than as a handful of bright lines with nothing behind them.
 */
private fun pendoloLineAlpha(angleDegrees: Float, absoluteDistance: Float): Float {
    val angleRadians = Math.toRadians(angleDegrees.toDouble())
    val byAngle = cos(angleRadians * PendoloAlphaAngleFalloff).toFloat().pow(PendoloAlphaExponent)
    val byDistance = 1f - absoluteDistance * PendoloAlphaDistanceStep
    return max(PendoloMinimumAlpha, byAngle * byDistance)
}

/**
 * Degrees the whole rail swings while a tick is in flight.
 *
 * Measured from the anchor rather than from zero, so the rail is at rest whenever the
 * spring has settled -- including after a seek, where the anchor moves without any
 * tick having been played.
 */
fun pendoloWheelRotationDegrees(
    springIndex: Float,
    anchorIndex: Float,
    wheel: PendoloWheel,
): Float = -(springIndex - anchorIndex) * wheel.angleStepDegrees

/**
 * Radians the gear has ratcheted through in total.
 *
 * Deliberately *not* measured from the anchor: this is the clockwork's absolute
 * position in the song, so it steps one tooth per line and never unwinds. Measuring
 * it from the anchor would make the gear snap back to its start on every seek, which
 * is the one thing a ratchet must not do.
 */
fun pendoloGearRotationRadians(springIndex: Float, wheel: PendoloWheel): Float =
    springIndex * Math.toRadians(wheel.angleStepDegrees.toDouble()).toFloat()

/** Rounds a fractional anchor to the line it is nearest. */
private fun Float.roundToOffset(): Int {
    val floor = kotlin.math.floor(this)
    return (if (this - floor >= 0.5f) floor + 1f else floor).toInt()
}

private const val PendoloLyricRadiusOffsetFraction = 0.06f
private const val PendoloMinimumTextWidth = 140f
private const val PendoloTextWidthFraction = 0.46f
private const val PendoloTextWidthGutter = 48f
private const val PendoloLineGapPx = 24f
private const val PendoloFocalTolerance = 0.5f
private const val PendoloMinimumNeighbourScale = 0.7f
private const val PendoloNeighbourScaleStep = 0.08f
private const val PendoloAlphaAngleFalloff = 0.75
private const val PendoloAlphaExponent = 2.5f
private const val PendoloAlphaDistanceStep = 0.18f
private const val PendoloMinimumAlpha = 0.12f
