package com.example.asmrplayer.core.overlay

import org.junit.Assert.*
import org.junit.Test

class LyricColorHsvTest {
    @Test fun primaryHuesMatch() {
        assertEquals(0xFFFF0000.toInt(), LyricColorHsv(0f, 1f, 1f).toArgb())
        assertEquals(0xFF00FF00.toInt(), LyricColorHsv(120f, 1f, 1f).toArgb())
        assertEquals(0xFF0000FF.toInt(), LyricColorHsv(240f, 1f, 1f).toArgb())
    }
    @Test fun whiteAndBlackAreStable() {
        assertEquals(0xFFFFFFFF.toInt(), LyricColorHsv(200f, 0f, 1f).toArgb())
        assertEquals(0xFF000000.toInt(), LyricColorHsv(200f, 1f, 0f).toArgb())
        assertEquals(0f, LyricColorHsv.fromArgb(0).saturation, 0f)
    }
    @Test fun hueWrapsAndValuesClamp() {
        assertEquals(LyricColorHsv(0f, 1f, 1f).toArgb(), LyricColorHsv(360f, 9f, 9f).toArgb())
        assertEquals(LyricColorHsv(300f, 1f, 1f).toArgb(), LyricColorHsv(-60f, 1f, 1f).toArgb())
        assertEquals(0xFF000000.toInt(), LyricColorHsv(Float.NaN, Float.NaN, Float.NaN).toArgb())
    }
    @Test fun existingStoredColorsRoundTrip() {
        for (color in listOf(0xFFFFFFFF, 0xFF9CD9FF, 0xFFFFD477, 0xFFFFA8CB, 0xFFA4E6BC, 0xFFFF7777, 0xFF123456)) {
            assertEquals(color.toInt(), LyricColorHsv.fromArgb(color.toInt()).toArgb())
        }
    }
    @Test fun channelGridRoundTripsWithoutDrift() {
        for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) {
            val argb = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
            assertEquals(argb, LyricColorHsv.fromArgb(argb).toArgb())
        }
    }
}
