package com.example.asmrplayer.feature.subtitles

import kotlin.math.floor
import kotlin.math.sin

/**
 * The word cloud's geometry, as pure functions.
 *
 * Two things are being decided here and they are independent, which is why they are
 * two functions. [luminousWordFlow] says where each word would sit if nothing were
 * scattered -- a wrapped, centred block, which is what a listener sees with motion
 * turned off. [luminousWordScatter] says how far each word is thrown from that place,
 * and it is deterministic in the line's own start time, so the same line always
 * scatters the same way: seeking back to it reproduces the frame, and a still
 * screenshot is reproducible rather than merely similar.
 *
 * Determinism comes from a hash of the line's start time and the word's index, not
 * from a random number generator with state. That matters more than the exact
 * numbers: a mode that reshuffled on every recomposition would be unusable, and the
 * values themselves are only required to be well spread over 0..1, not to match the
 * reference's generator bit for bit.
 */

/** A word's place in the wrapped block, before any scatter. The word's top-left. */
data class LuminousWordSlot(val x: Float, val y: Float)

/** The wrapped block: where each word goes, and how big the block came out. */
data class LuminousWordFlow(
    val slots: List<LuminousWordSlot>,
    val width: Float,
    val height: Float,
)

/** How a word is thrown from its slot. */
data class LuminousWordScatter(
    val dx: Float,
    val dy: Float,
    val rotationDegrees: Float,
    val scale: Float,
)

/**
 * Wraps measured words into a centred block, greedily, one row at a time.
 *
 * Words are laid out in the order they are sung, so the block reads left to right
 * like a line of text even while its words are scattered around their places. A word
 * wider than the block is given a row of its own and centred on it, overflowing
 * symmetrically rather than being pushed off one edge.
 */
fun luminousWordFlow(
    wordWidths: List<Float>,
    maxWidth: Float,
    spacing: Float,
    lineHeight: Float,
): LuminousWordFlow {
    if (wordWidths.isEmpty()) return LuminousWordFlow(emptyList(), 0f, 0f)
    val width = maxWidth.coerceAtLeast(1f)
    val rows = mutableListOf<List<Int>>()
    var row = mutableListOf<Int>()
    var rowWidth = 0f
    wordWidths.forEachIndexed { index, wordWidth ->
        val grown = if (row.isEmpty()) wordWidth else rowWidth + spacing + wordWidth
        if (row.isNotEmpty() && grown > width) {
            rows += row
            row = mutableListOf(index)
            rowWidth = wordWidth
        } else {
            row += index
            rowWidth = grown
        }
    }
    if (row.isNotEmpty()) rows += row

    val slots = MutableList(wordWidths.size) { LuminousWordSlot(0f, 0f) }
    rows.forEachIndexed { rowIndex, indices ->
        val total = indices.sumOf { wordWidths[it].toDouble() }.toFloat() +
            spacing * (indices.size - 1).coerceAtLeast(0)
        var x = (width - total) / 2f
        indices.forEach { index ->
            slots[index] = LuminousWordSlot(x = x, y = rowIndex * lineHeight)
            x += wordWidths[index] + spacing
        }
    }
    return LuminousWordFlow(slots = slots, width = width, height = rows.size * lineHeight)
}

/**
 * How far a word is thrown from its slot, and how it is turned and sized.
 *
 * Everything scales with [motion], which is the global motion setting as a fraction:
 * at zero the scatter is exactly identity, so the cloud *is* the wrapped block and
 * the mode is still. At full motion the throw and the tilt are the reference's own
 * widest values, and the size range widens with them -- its two profiles, expressed
 * as the ends of one ramp instead of a switch between them.
 *
 * Turning [tuning]'s word rotation off costs only the tilt: a word still arrives from
 * somewhere and still settles, which is what the parameter is for.
 */
fun luminousWordScatter(
    index: Int,
    seed: Long,
    motion: Float,
    tuning: LuminousTuning,
): LuminousWordScatter {
    val level = motion.coerceIn(0f, 1f)
    val spread = ReferenceSpreadPx * level
    val rotate = if (tuning.wordRotation) ReferenceRotateDegrees * level else 0f
    val baseScale = ReferenceBaseScale - level * ChaoticScaleDrop
    val scaleRange = ReferenceScaleRange + level * ChaoticScaleGain
    return LuminousWordScatter(
        dx = bipolar(seed, index, RandomOffsetX) * spread,
        dy = bipolar(seed, index, RandomOffsetY) * spread,
        rotationDegrees = bipolar(seed, index, RandomOffsetRotation) * rotate,
        scale = baseScale + random(seed, index, RandomOffsetScale) * scaleRange,
    )
}

/**
 * The block's breathing offset for a cycle position, as the reference moves it: up
 * to the full amplitude, back through home, a shorter dip the other way, and home.
 *
 * [phase] wraps, so a caller can keep adding to it; [amplitude] at zero returns zero
 * for every phase, which is what makes the breath part of the motion setting rather
 * than a separate switch.
 */
fun luminousBreathOffset(phase: Float, amplitude: Float): Float {
    if (amplitude == 0f) return 0f
    val wrapped = phase - floor(phase)
    val key = when {
        wrapped < 0.25f -> lerp(0f, -1f, wrapped / 0.25f)
        wrapped < 0.50f -> lerp(-1f, 0f, (wrapped - 0.25f) / 0.25f)
        wrapped < 0.75f -> lerp(0f, BreathDip, (wrapped - 0.50f) / 0.25f)
        else -> lerp(BreathDip, 0f, (wrapped - 0.75f) / 0.25f)
    }
    return key * amplitude
}

/**
 * A deterministic value in 0..1 for one measurement of one word of one line.
 *
 * The line's start time is the seed, so two lines never share a layout and a line
 * that comes round again -- a chorus, a repeat after a seek -- comes back identical.
 * The index is spaced by a stride that shares no factor with the small integer
 * offsets, so the four measurements of one word do not land in a pattern.
 */
private fun random(seed: Long, index: Int, offset: Int): Float {
    val value = sin(seed.toDouble() + index * WordSeedStride + offset) * RandomScale
    return (value - floor(value)).toFloat().coerceIn(0f, 1f)
}

/** The same measurement, centred: -1..1, so a scatter lands either side of the slot. */
private fun bipolar(seed: Long, index: Int, offset: Int): Float =
    (random(seed, index, offset) - 0.5f) * 2f

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

/** The reach of a fully scattered word, and the widest tilt. */
private const val ReferenceSpreadPx = 60f
private const val ReferenceRotateDegrees = 30f

/** The reference's own size range at rest, widened to its chaotic range at full motion. */
private const val ReferenceBaseScale = 1.1f
private const val ReferenceScaleRange = 0.2f
private const val ChaoticScaleDrop = 0.3f
private const val ChaoticScaleGain = 0.4f

/** How far the block dips below home on the way round its cycle. */
private const val BreathDip = 0.45f

private const val WordSeedStride = 7.0
private const val RandomScale = 10_000.0

private const val RandomOffsetX = 1
private const val RandomOffsetY = 2
private const val RandomOffsetRotation = 3
private const val RandomOffsetScale = 4
