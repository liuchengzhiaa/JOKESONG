package com.example.asmrplayer.core.settings

/**
 * Where a continuous setting sits on its own control, from 0 at the range's left
 * end to 1 at its right end.
 *
 * The level icon is drawn from this rather than from the percentage itself,
 * because the settings do not share a range: ambient light runs to 150 and density
 * only to 130, so "100%" is a different position in each. The position is also the
 * only reading that matches the slider directly underneath it, which is what makes
 * a shape worth showing instead of the number.
 */
internal fun levelFraction(value: Int, range: IntRange): Float {
    val span = range.last - range.first
    if (span <= 0) return 0f
    return ((value - range.first).toFloat() / span).coerceIn(0f, 1f)
}
