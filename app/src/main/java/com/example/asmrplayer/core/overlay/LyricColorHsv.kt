package com.example.asmrplayer.core.overlay

import kotlin.math.roundToInt

/** UI-friendly palette conversion, pure JVM: stored opaque ARGB schema stays compatible. */
data class LyricColorHsv(val hue: Float, val saturation: Float, val value: Float) {
    fun toArgb(): Int {
        val h = ((hue.takeIf { it.isFinite() } ?: 0f) % 360f + 360f) % 360f
        val s = (saturation.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f)
        val v = (value.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return 0xFF000000.toInt() or (((r + m) * 255).roundToInt().coerceIn(0, 255) shl 16) or
            (((g + m) * 255).roundToInt().coerceIn(0, 255) shl 8) or (b + m).times(255).roundToInt().coerceIn(0, 255)
    }

    companion object {
        fun fromArgb(argb: Int): LyricColorHsv {
            val r = ((argb ushr 16) and 255) / 255f
            val g = ((argb ushr 8) and 255) / 255f
            val b = (argb and 255) / 255f
            val high = maxOf(r, g, b)
            val delta = high - minOf(r, g, b)
            val hue = when {
                delta == 0f -> 0f
                high == r -> 60f * ((g - b) / delta % 6f)
                high == g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }
            return LyricColorHsv((hue + 360) % 360, if (high == 0f) 0f else delta / high, high)
        }
    }
}
