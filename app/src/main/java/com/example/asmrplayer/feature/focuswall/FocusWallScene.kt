package com.example.asmrplayer.feature.focuswall

import androidx.compose.ui.geometry.Size

internal data class FocusWallSceneRequest(
    val contentKey: String,
    val items: List<FocusWallLayoutItem>,
    val width: Float,
    val height: Float,
    val orientation: FocusWallOrientation,
    val densityPercent: Int,
    val focusedItemId: String?,
    val gap: Float,
    val style: FocusWallStyle,
    val shape: FoliaFocusShape,
    val size: FoliaFocusSize,
    val artworkAspect: Float?,
)

/** Constructed off-main, then swapped as one immutable scene. No geometry changes. */
internal class FocusWallScene(val request: FocusWallSceneRequest) {
    val placements = when (request.style) {
        FocusWallStyle.Bento -> FocusWallLayoutEngine.layout(
            request.items, request.width, request.height, request.orientation,
            FocusWallDensity.Medium, request.densityPercent / 100f, request.focusedItemId, request.gap,
        )
        FocusWallStyle.Folia -> FoliaWallLayoutEngine.layout(
            request.items, request.width, request.height, request.orientation,
            FocusWallDensity.Medium, request.densityPercent / 100f, request.focusedItemId, request.gap,
            request.shape, request.size, request.artworkAspect,
        )
    }
    val placementById = placements.associateBy(FocusWallPlacement::itemId)
    val spatialIndex = FocusWallSpatialIndex(placements)
    val bounds = wallWorldBounds(placements)
    val halfSize = Size(
        placements.maxOfOrNull { it.width / 2 } ?: 0f,
        placements.maxOfOrNull { it.height / 2 } ?: 0f,
    )
}
