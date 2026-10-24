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
internal const val MinimumWallDensityPercent = 70
internal const val MaximumWallDensityPercent = 130
internal const val MaximumAmbientPercent = 150

/**
 * What every continuous wall setting starts at, and what its reset row restores.
 * It matches the storage defaults in `AppSettings`: they
 * are deliberately the same number, because a reset that landed anywhere else
 * would be a second, invisible default.
 */
internal const val DefaultWallPercent = 100

/**
 * Folia viewport that lives outside the wall composable.
 *
 * Hoisting retains the viewport across list/grid switches. Only pan is seeded
 * from a folder snapshot; all display controls are synchronized from the shared
 * global preferences, so returning to a folder cannot restore stale settings.
 */
@Stable
class FocusWallViewportState(
    initialZoom: Float = 1f,
    initialPanX: Float = 0f,
    initialPanY: Float = 0f,
    initialDensityPercent: Int = 100,
    initialFocusShapeOrdinal: Int = FoliaFocusShape.Square.ordinal,
    initialFocusSizeOrdinal: Int = FoliaFocusSize.Medium.ordinal,
    initialAmbientPercent: Int = 100,
    restored: Boolean = false,
) {
    var zoom by mutableFloatStateOf(initialZoom.coerceIn(MinimumCanvasZoom, MaximumCanvasZoom))

    var pan by mutableStateOf(Offset(initialPanX, initialPanY))

    var densityPercent by mutableIntStateOf(
        initialDensityPercent.coerceIn(MinimumWallDensityPercent, MaximumWallDensityPercent),
    )

    /** Shape of the focused cover, shared globally across folders. */
    var focusShapeOrdinal by mutableIntStateOf(
        initialFocusShapeOrdinal.coerceIn(FoliaFocusShape.entries.indices),
    )

    /** How large the focused cover grows. Folia only. */
    var focusSizeOrdinal by mutableIntStateOf(
        initialFocusSizeOrdinal.coerceIn(FoliaFocusSize.entries.indices),
    )

    /** Strength of the cover-derived full-wall light. Folia only. */
    var ambientPercent by mutableIntStateOf(
        initialAmbientPercent.coerceIn(0, MaximumAmbientPercent),
    )

    /**
     * True while the viewport comes from a restored snapshot. The first focus
     * reconciliation then keeps the restored pan instead of animating over it.
     */
    var restored by mutableStateOf(restored)

    val densityScale: Float get() = densityPercent / 100f

    val focusShape: FoliaFocusShape get() = FoliaFocusShape.entries[focusShapeOrdinal]

    val focusSize: FoliaFocusSize get() = FoliaFocusSize.entries[focusSizeOrdinal]

    val ambientMultiplier: Float get() = ambientPercent / 100f
}
