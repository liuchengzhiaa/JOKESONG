package com.example.asmrplayer.feature.focuswall

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class FocusWallDensity {
    Small,
    Medium,
    Large;

    val scale: Float
        get() = when (this) {
            Small -> 0.84f
            Medium -> 1f
            Large -> 1.16f
        }

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
    val blockIndex: Int,
    /**
     * Set when the card fills only part of its own rectangle -- a focused cover that is a
     * silhouette rather than a rectangle. The card is still placed and animated by its
     * rectangle; the silhouette decides what it paints and where it can be touched.
     */
    val shape: FocusWallShape? = null,
    /**
     * Letterbox the whole artwork inside the card instead of cropping it to fill. Set by
     * the 完整封面 preset, whose point is that no part of the cover is hidden.
     */
    val fitArtwork: Boolean = false,
)

/**
 * A card that is not a plain rectangle: the cells it fills, as rectangles in the card's
 * own 0..1 box.
 *
 * The cells keep the block's grid, so a silhouette is one card with a pixel outline
 * rather than a picture with a mask over it. [rects] is only ever read to build a path.
 */
@Immutable
data class FocusWallShape(val rects: List<Rect>)

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

/** Two-dimensional viewport query for the mosaic. */
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

/** Used only for subtle distance dimming; card dimensions come from the mosaic. */
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

/**
 * Canvas magnifications artwork is decoded for. The canvas zoom range is
 * [MinimumCanvasZoom]..[MaximumCanvasZoom], so these three buckets cover it.
 */
private val ArtworkDecodeScales = listOf(1f, 1.15f, MaximumCanvasZoom)

/** Largest side a single wall card ever asks the decoder for. */
private const val MaximumArtworkDecodePx = 1536

/**
 * How much larger than its laid-out size a card ends up being drawn at [zoom].
 *
 * Cards are composed at their zoom-1 layout size and the canvas magnifies them with a
 * graphics layer, so artwork decoded at the layout size is magnified along with it and
 * comes out soft. Decoding at this multiple instead keeps the bitmap at or above the
 * pixels it is painted into.
 *
 * The value is a bucket, rounded up: a pinch then re-decodes at most once per bucket
 * instead of once per frame, and every bucket it has already used stays in Coil's
 * memory cache.
 */
fun artworkDecodeScale(zoom: Float): Float =
    ArtworkDecodeScales.firstOrNull { it >= zoom } ?: ArtworkDecodeScales.last()

/**
 * Decode size to request for a card laid out at [widthPx] x [heightPx] and drawn at
 * [zoom]. Never smaller than the layout size, since a card is only ever magnified.
 *
 * This corrects the magnification the wall applies on top of the layout. It cannot
 * correct artwork whose source is smaller than the card: the request is inexact, so
 * Coil hands back the source resolution rather than upscaling it, and a
 * low-resolution cover stays soft however far the wall is zoomed in.
 */
fun artworkDecodeSize(widthPx: Float, heightPx: Float, zoom: Float): IntSize {
    val scale = artworkDecodeScale(zoom)
    return IntSize(
        width = (widthPx * scale).roundToInt().coerceIn(1, MaximumArtworkDecodePx),
        height = (heightPx * scale).roundToInt().coerceIn(1, MaximumArtworkDecodePx),
    )
}

/** Axis-aligned extent of the whole laid-out wall, in world coordinates. */
data class FocusWallWorldBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
)

/** A pan offset in screen pixels, kept plain so [clampWallPan] stays unit-testable. */
data class FocusWallPan(val x: Float, val y: Float)

/** Returns null when there is nothing to bound. */
fun wallWorldBounds(placements: List<FocusWallPlacement>): FocusWallWorldBounds? {
    if (placements.isEmpty()) return null
    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    placements.forEach { placement ->
        val halfWidth = placement.width / 2f
        val halfHeight = placement.height / 2f
        minX = min(minX, placement.centerX - halfWidth)
        maxX = max(maxX, placement.centerX + halfWidth)
        minY = min(minY, placement.centerY - halfHeight)
        maxY = max(maxY, placement.centerY + halfHeight)
    }
    return FocusWallWorldBounds(minX, maxX, minY, maxY)
}

/**
 * Keeps the viewport's *centre* inside [bounds]. Panning stays completely free
 * within that box; the clamp is a safety rail, not a snapping rule.
 *
 * It exists because release is inertial and no longer snaps to a card: without a
 * bound, one fast fling could carry the entire wall off screen with nothing left
 * to grab. Bounding the centre rather than the edges means the wall is always at
 * least partly visible, and lets content smaller than the viewport still be
 * centred.
 */
fun clampWallPan(
    panX: Float,
    panY: Float,
    zoom: Float,
    bounds: FocusWallWorldBounds?,
): FocusWallPan {
    bounds ?: return FocusWallPan(panX, panY)
    // pan = -centre * zoom, so the centre's box maps to a reversed, scaled range.
    val safeZoom = zoom.coerceAtLeast(0.01f)
    return FocusWallPan(
        x = panX.coerceIn(-bounds.maxX * safeZoom, -bounds.minX * safeZoom),
        y = panY.coerceIn(-bounds.maxY * safeZoom, -bounds.minY * safeZoom),
    )
}
