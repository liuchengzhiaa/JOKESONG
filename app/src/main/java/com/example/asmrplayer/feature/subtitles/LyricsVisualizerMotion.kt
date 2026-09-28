package com.example.asmrplayer.feature.subtitles

/**
 * Motion amplitudes shared by background visualizers.
 *
 * These are pure functions rather than constants read inside composables so that
 * "the global motion setting at 0% is motionless" is a property that can be
 * asserted instead of eyeballed. They are amplitudes and not time: callers drive
 * them from a line change, never from a frame loop, so a still line still costs no
 * frames and a paused scene is genuinely still.
 *
 * The two modes that own a frame clock keep their own amplitudes beside their own
 * geometry instead -- `LuminousLayout` and `PendoloGeometry` -- because those
 * amplitudes are read every frame and belong with the rest of that mode's maths.
 */

/**
 * Sideways offset of the drifting artwork, in dp.
 *
 * Keyed by the line index rather than by elapsed time, so the offset is a function
 * of playback position: seeking back to a line puts the artwork back exactly where
 * it was, and no running clock is needed to hold it there.
 */
fun driftOffsetFor(lineIndex: Int, motionPercent: Int): Float {
    val fraction = motionFraction(motionPercent)
    if (fraction <= 0f) return 0f
    val phase = ((lineIndex % DriftSteps) + DriftSteps) % DriftSteps
    return (phase - (DriftSteps - 1) / 2f) * DriftStepDp * fraction
}

/**
 * The global motion setting as a fraction of full travel.
 *
 * Public inside the module because a mode's own parameters are absolute positions
 * that must still be scaled by this: the wheel keeps its shape at any motion setting
 * and only moves less. A setting below zero reads as zero and one above 100 as 100,
 * so a stored value that predates a range change cannot produce over-travel.
 */
internal fun motionFraction(motionPercent: Int): Float =
    motionPercent.coerceIn(0, 100) / 100f

private const val DriftSteps = 4
private const val DriftStepDp = 9f
