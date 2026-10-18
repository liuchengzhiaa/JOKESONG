package com.example.asmrplayer.core.artwork

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/** Nine source-region colours, not a bitmap or a palette expanded with invented hues. */
internal data class PlayerColourField(
    val colours: List<Int>,
    val darkFraction: Float,
    val regionDarkFractions: List<Float> = List(9) { darkFraction },
) {
    init {
        require(colours.size == 9 && darkFraction in 0f..1f)
        require(regionDarkFractions.size == 9 && regionDarkFractions.all { it in 0f..1f })
    }
}

/** Bounded thumbnail analysis. Black padding affects brightness, not the selected hue. */
internal fun playerColourField(pixels: IntArray, width: Int, height: Int): PlayerColourField? {
    if (width <= 0 || height <= 0 || width.toLong() * height != pixels.size.toLong()) return null
    val buckets = Array(9) { HashMap<Int, FieldBucket>() }
    val midBuckets = Array(9) { HashMap<Int, FieldBucket>() }
    val averages = Array(9) { FieldBucket() }
    val global = FieldBucket()
    val regionVisible = IntArray(9)
    val regionDark = IntArray(9)
    var visible = 0
    var dark = 0
    val step = ((pixels.size.toLong() + 4095) / 4096).toInt().coerceAtLeast(1)
    for (index in pixels.indices step step) {
        val argb = pixels[index]
        if (argb ushr 24 < 160) continue
        visible++
        val r = argb ushr 16 and 255
        val g = argb ushr 8 and 255
        val b = argb and 255
        val high = maxOf(r, g, b)
        val low = minOf(r, g, b)
        global.add(r, g, b)
        val cell = ((index / width).toLong() * 3 / height).toInt().coerceAtMost(2) * 3 +
            ((index % width).toLong() * 3 / width).toInt().coerceAtMost(2)
        regionVisible[cell]++
        if (high < 30) { dark++; regionDark[cell]++; continue }
        averages[cell].add(r, g, b)
        // Preserve neutral regions as neutral, but prefer meaningful colour over text/paper.
        if (high - low >= high * 0.12f) {
            val key = (r shr 5 shl 6) or (g shr 5 shl 3) or (b shr 5)
            buckets[cell].getOrPut(key) { FieldBucket() }.add(r, g, b)
            if (high in 45..210 && high - low >= high * 0.20f) {
                midBuckets[cell].getOrPut(key) { FieldBucket() }.add(r, g, b)
            }
        }
    }
    if (visible == 0) return null
    val midCandidates = midBuckets.flatMap { it.values }
    val candidates = midCandidates.ifEmpty { buckets.flatMap { it.values } }
    val fallback = candidates.maxWithOrNull(compareBy<FieldBucket> { it.score() }.thenBy { it.argb() })
        ?.argb() ?: global.argb()
    return PlayerColourField(List(9) { cell ->
        midBuckets[cell].values.ifEmpty { buckets[cell].values }.maxWithOrNull(compareBy<FieldBucket> { it.score() }.thenBy { it.argb() })
            ?.argb() ?: averages[cell].takeIf { it.count > 0 }?.argb() ?: fallback
    }, dark.toFloat() / visible, List(9) { cell ->
        if (regionVisible[cell] == 0) dark.toFloat() / visible
        else regionDark[cell].toFloat() / regionVisible[cell]
    })
}

private class FieldBucket {
    var count = 0
    private var red = 0L
    private var green = 0L
    private var blue = 0L
    fun add(r: Int, g: Int, b: Int) { count++; red += r; green += g; blue += b }
    fun argb(): Int = (0xFF shl 24) or ((red / count).toInt() shl 16) or
        ((green / count).toInt() shl 8) or (blue / count).toInt()
    fun score(): Long {
        val r = red / count; val g = green / count; val b = blue / count
        return count * (128L + maxOf(r, g, b) - minOf(r, g, b))
    }
}

internal const val PlayerFieldWidth = 96
internal const val PlayerFieldHeight = 144

/** One small static texture per cover. No image structures, frame loop or full-screen blur. */
internal fun renderPlayerColourField(field: PlayerColourField): IntArray {
    val tones = field.colours.mapIndexed { index, colour ->
        fieldTone(colour, field.darkFraction * 0.45f + field.regionDarkFractions[index] * 0.55f)
    }
    return IntArray(PlayerFieldWidth * PlayerFieldHeight) { index ->
        val x = (index % PlayerFieldWidth).toDouble() / (PlayerFieldWidth - 1)
        val y = (index / PlayerFieldWidth).toDouble() / (PlayerFieldHeight - 1)
        var red = 0.0; var green = 0.0; var blue = 0.0; var total = 0.0
        tones.forEachIndexed { cell, channels ->
            val cx = (cell % 3) * 0.60 - 0.10
            val cy = (cell / 3) * 0.65 - 0.15
            val distance = ((x - cx) / 0.48).pow(2) + ((y - cy) / 0.52).pow(2)
            val weight = exp(-distance * 1.7)
            red += channels[0] * weight; green += channels[1] * weight; blue += channels[2] * weight
            total += weight
        }
        val edge = 1.0 - 0.10 * maxOf(abs(x - 0.5), abs(y - 0.5))
        // Fixed sub-pixel dither reduces banding without a time-driven noise pass.
        val noise = (((index * 73 + index / PlayerFieldWidth * 151) and 255) / 255.0 - 0.5) * 0.7
        fun channel(value: Double) = (value / total * edge * 255 + noise).roundToInt().coerceIn(0, 255)
        (0xFF shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    }
}

private fun fieldTone(argb: Int, dark: Float): DoubleArray {
    val rgb = doubleArrayOf((argb ushr 16 and 255) / 255.0, (argb ushr 8 and 255) / 255.0, (argb and 255) / 255.0)
    val high = rgb.max()
    val value = (0.18 + high * 0.54) * (1.0 - dark * 0.50)
    val saturation = if (high > 0) 1.0 - rgb.min() / high else 0.0
    val chroma = if (saturation > 0) minOf(1.24, 1.0 / saturation) else 1.0
    val channels = DoubleArray(3) { channel ->
        if (high == 0.0) value else (value * (1.0 - (1.0 - rgb[channel] / high) * chroma)).coerceAtLeast(0.0)
    }
    // Keep white titles/current lyrics readable without forcing every cover to the same brightness.
    if (fieldLuminance(channels) > 0.17) {
        var low = 0.0; var upper = 1.0
        repeat(12) {
            val mid = (low + upper) / 2
            if (fieldLuminance(DoubleArray(3) { channels[it] * mid }) > 0.17) upper = mid else low = mid
        }
        repeat(3) { channels[it] *= low }
    }
    return channels
}

private fun fieldLuminance(rgb: DoubleArray): Double {
    fun linear(v: Double) = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    return linear(rgb[0]) * 0.2126 + linear(rgb[1]) * 0.7152 + linear(rgb[2]) * 0.0722
}
