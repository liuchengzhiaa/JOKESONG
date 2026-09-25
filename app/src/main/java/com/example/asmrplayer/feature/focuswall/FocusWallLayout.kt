package com.example.asmrplayer.feature.focuswall

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class FocusWallDensity {
    Small,
    Medium,
    Large;

    fun next(): FocusWallDensity = entries[(ordinal + 1) % entries.size]
}

enum class FocusWallOrientation {
    Portrait,
    Landscape,
}

data class FocusWallLayoutItem(
    val id: String,
    val stableSeed: Int,
)

data class FocusWallPlacement(
    val itemId: String,
    val index: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
)

/**
 * Pure spatial layout for the focus wall. Positions are world coordinates around
 * the viewport center. Canvas zoom and pan are deliberately not inputs so pinch
 * gestures can never cause a reflow.
 */
object FocusWallLayoutEngine {
    fun layout(
        items: List<FocusWallLayoutItem>,
        viewportWidth: Float,
        viewportHeight: Float,
        orientation: FocusWallOrientation,
        density: FocusWallDensity,
    ): List<FocusWallPlacement> {
        if (items.isEmpty() || viewportWidth <= 0f || viewportHeight <= 0f) return emptyList()
        return when (orientation) {
            FocusWallOrientation.Portrait -> portrait(items, viewportWidth, viewportHeight, density)
            FocusWallOrientation.Landscape -> landscape(items, viewportWidth, viewportHeight, density)
        }
    }

    private fun portrait(
        items: List<FocusWallLayoutItem>,
        viewportWidth: Float,
        viewportHeight: Float,
        density: FocusWallDensity,
    ): List<FocusWallPlacement> {
        val baseWidth = viewportWidth * when (density) {
            FocusWallDensity.Small -> 0.48f
            FocusWallDensity.Medium -> 0.58f
            FocusWallDensity.Large -> 0.68f
        }
        val horizontalStep = baseWidth * 0.78f
        val verticalStep = min(baseWidth * 0.82f, viewportHeight * 0.25f)

        return mosaic(
            items = items,
            horizontalStep = horizontalStep,
            verticalStep = verticalStep,
        ) { item ->
            val widthVariation = 0.92f + stableBucket(item.stableSeed, 6) * 0.025f
            val aspectVariation = 0.82f + stableBucket(item.stableSeed / 11, 7) * 0.055f
            val width = min(baseWidth * widthVariation, viewportWidth * 0.72f)
            val height = (width * aspectVariation)
                .coerceIn(viewportHeight * 0.12f, viewportHeight * 0.36f)
            width to height
        }
    }

    private fun landscape(
        items: List<FocusWallLayoutItem>,
        viewportWidth: Float,
        viewportHeight: Float,
        density: FocusWallDensity,
    ): List<FocusWallPlacement> {
        val columns = when (density) {
            FocusWallDensity.Small -> 6
            FocusWallDensity.Medium -> 5
            FocusWallDensity.Large -> 4
        }
        val baseWidth = viewportWidth / (columns - 1.2f)
        val horizontalStep = baseWidth * 0.88f
        val verticalStep = min(baseWidth * 0.70f, viewportHeight * 0.46f)

        return mosaic(
            items = items,
            horizontalStep = horizontalStep,
            verticalStep = verticalStep,
        ) { item ->
            val widthVariation = 0.86f + stableBucket(item.stableSeed, 6) * 0.045f
            val aspectVariation = 0.74f + stableBucket(item.stableSeed / 13, 7) * 0.075f
            val width = min(baseWidth * widthVariation, viewportWidth * 0.34f)
            val height = (width * aspectVariation)
                .coerceIn(viewportHeight * 0.22f, viewportHeight * 0.62f)
            width to height
        }
    }

    private inline fun mosaic(
        items: List<FocusWallLayoutItem>,
        horizontalStep: Float,
        verticalStep: Float,
        size: (FocusWallLayoutItem) -> Pair<Float, Float>,
    ): List<FocusWallPlacement> = items.mapIndexed { index, item ->
            val (column, row) = mosaicCoordinate(index)
            val (width, height) = size(item)
            FocusWallPlacement(
                itemId = item.id,
                index = index,
                centerX = column * horizontalStep,
                centerY = row * verticalStep,
                width = width,
                height = height,
            )
        }.sortedWith(compareBy<FocusWallPlacement> { it.centerY }.thenBy { it.centerX })

    private fun stableBucket(seed: Int, count: Int): Int = (abs(seed.toLong()) % count).toInt()

    /**
     * Keeps the first visible ring balanced around the hero, then continues on a
     * deterministic square spiral. Small folders therefore fill both sides of
     * the screen instead of growing as a one-sided chain.
     */
    private fun mosaicCoordinate(index: Int): Pair<Int, Int> = when (index) {
        0 -> 0 to 0
        1 -> -1 to 0
        2 -> 1 to 0
        3 -> 0 to -1
        4 -> 0 to 1
        5 -> -1 to -1
        6 -> 1 to -1
        7 -> -1 to 1
        8 -> 1 to 1
        else -> spiralCoordinate(index)
    }

    /** Maps the remaining sorted item positions onto an outward square spiral. */
    private fun spiralCoordinate(index: Int): Pair<Int, Int> {
        if (index == 0) return 0 to 0
        val layer = ceil((sqrt(index + 1.0) - 1.0) / 2.0).toInt()
        val legLength = layer * 2
        val maxIndex = (layer * 2 + 1) * (layer * 2 + 1) - 1
        val distanceFromEnd = maxIndex - index
        val side = distanceFromEnd / legLength
        val offset = distanceFromEnd % legLength
        return when (side) {
            0 -> (layer - offset) to -layer
            1 -> -layer to (-layer + offset)
            2 -> (-layer + offset) to layer
            else -> layer to (layer - offset)
        }
    }
}

/**
 * Returns a bounded slice of the y-sorted scene before the more precise x-axis
 * clipping is performed by the canvas. This avoids walking tens of thousands of
 * placements for every drag frame.
 */
fun placementsNearVerticalRange(
    placements: List<FocusWallPlacement>,
    minCenterY: Float,
    maxCenterY: Float,
    edgePaddingItems: Int = 8,
): List<FocusWallPlacement> {
    if (placements.isEmpty()) return emptyList()

    var low = 0
    var high = placements.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (placements[middle].centerY < minCenterY) low = middle + 1 else high = middle
    }
    val start = max(0, low - edgePaddingItems)

    low = start
    high = placements.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (placements[middle].centerY <= maxCenterY) low = middle + 1 else high = middle
    }
    val endExclusive = min(placements.size, low + edgePaddingItems)
    return placements.subList(start, endExclusive)
}

/**
 * Two-dimensional viewport query for the mosaic. The binary y lookup avoids a
 * full-scene walk; the x filter prevents a wide row in a 40,000-item library from
 * producing an unbounded composition set.
 */
fun placementsNearWorldRect(
    placements: List<FocusWallPlacement>,
    minCenterX: Float,
    maxCenterX: Float,
    minCenterY: Float,
    maxCenterY: Float,
): List<FocusWallPlacement> = placementsNearVerticalRange(
    placements = placements,
    minCenterY = minCenterY,
    maxCenterY = maxCenterY,
    edgePaddingItems = 0,
).filter { it.centerX in minCenterX..maxCenterX }

/**
 * Cover-flow depth projection. The item crossing the viewport centre is always
 * visually dominant; cards recede smoothly instead of changing size in steps.
 */
fun focusWallDepthScale(
    offsetFromCenterX: Float,
    offsetFromCenterY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): Float {
    val normalizedX = offsetFromCenterX / (viewportWidth * 0.48f).coerceAtLeast(1f)
    val normalizedY = offsetFromCenterY / (viewportHeight * 0.48f).coerceAtLeast(1f)
    val proximity = (1f - hypot(normalizedX, normalizedY)).coerceIn(0f, 1f)
    return 0.52f + proximity * 0.48f
}
