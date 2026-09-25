package com.example.asmrplayer.feature.focuswall

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

internal const val MinimumCanvasZoom = 0.72f
internal const val MaximumCanvasZoom = 1.35f

/**
 * Focus-wall viewport that lives outside the wall composable.
 *
 * Hoisting it keeps the canvas zoom, pan and density alive while the user
 * switches to a list or grid layout and back, so changing layout never resets
 * where they were. It is seeded from the folder snapshot so returning to a
 * previously visited folder restores its viewport too.
 */
@Stable
class FocusWallViewportState(
    initialZoom: Float = 1f,
    initialPanX: Float = 0f,
    initialPanY: Float = 0f,
    initialDensityOrdinal: Int = FocusWallDensity.Medium.ordinal,
    restored: Boolean = false,
) {
    var zoom by mutableFloatStateOf(initialZoom.coerceIn(MinimumCanvasZoom, MaximumCanvasZoom))

    var pan by mutableStateOf(Offset(initialPanX, initialPanY))

    var densityOrdinal by mutableIntStateOf(
        initialDensityOrdinal.coerceIn(FocusWallDensity.entries.indices),
    )

    /**
     * True while the viewport comes from a restored snapshot. The first focus
     * reconciliation then keeps the restored pan instead of animating over it.
     */
    var restored by mutableStateOf(restored)

    val density: FocusWallDensity get() = FocusWallDensity.entries[densityOrdinal]
}
