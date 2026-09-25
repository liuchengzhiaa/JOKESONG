package com.example.asmrplayer.feature.focuswall

import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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
    val blockIndex: Int,
)

private data class BlockSlot(
    val x: Int,
    val y: Int,
    val columns: Int,
    val rows: Int,
)

private const val BlockColumns = 12
private const val BlockRows = 10
private const val ItemsPerBlock = 10

/**
 * Three independently-authored exact covers for a 12 x 10 logical block. They
 * deliberately mix squares, portrait cards and two widths of landscape card.
 * Mirroring each template from a stable block hash creates variety without ever
 * making layout depend on mutable or random state.
 */
private val BaseBlockTemplates = listOf(
    listOf(
        BlockSlot(0, 0, 5, 3),
        BlockSlot(0, 3, 5, 2),
        BlockSlot(0, 5, 2, 5),
        BlockSlot(2, 5, 3, 5),
        BlockSlot(5, 0, 4, 4),
        BlockSlot(9, 0, 3, 4),
        BlockSlot(5, 4, 7, 3),
        BlockSlot(5, 7, 2, 3),
        BlockSlot(7, 7, 2, 3),
        BlockSlot(9, 7, 3, 3),
    ),
    listOf(
        BlockSlot(0, 0, 3, 5),
        BlockSlot(3, 0, 4, 5),
        BlockSlot(7, 0, 5, 3),
        BlockSlot(7, 3, 5, 2),
        BlockSlot(0, 5, 5, 3),
        BlockSlot(0, 8, 5, 2),
        BlockSlot(5, 5, 3, 5),
        BlockSlot(8, 5, 2, 5),
        BlockSlot(10, 5, 2, 2),
        BlockSlot(10, 7, 2, 3),
    ),
    listOf(
        BlockSlot(0, 0, 5, 5),
        BlockSlot(0, 5, 5, 3),
        BlockSlot(0, 8, 2, 2),
        BlockSlot(2, 8, 3, 2),
        BlockSlot(5, 0, 3, 3),
        BlockSlot(8, 0, 4, 3),
        BlockSlot(5, 3, 4, 4),
        BlockSlot(9, 3, 3, 4),
        BlockSlot(5, 7, 4, 3),
        BlockSlot(9, 7, 3, 3),
    ),
)

/**
 * Reflow for the one block containing the focused item. The focused card takes
 * the centred 6 x 6 slot. The other nine cards re-pack the surrounding frame,
 * leaving the block's outer footprint unchanged and preventing a cascade into
 * neighbouring blocks.
 */
private val FocusedBlockTemplate = listOf(
    BlockSlot(3, 2, 6, 6),
    BlockSlot(0, 0, 3, 2),
    BlockSlot(3, 0, 2, 2),
    BlockSlot(5, 0, 4, 2),
    BlockSlot(9, 0, 3, 2),
    BlockSlot(0, 2, 3, 6),
    BlockSlot(9, 2, 3, 6),
    BlockSlot(0, 8, 4, 2),
    BlockSlot(4, 8, 5, 2),
    BlockSlot(9, 8, 3, 2),
)

/**
 * Pure deterministic geometry for the focus wall. Pan and canvas zoom are not
 * inputs, so a pinch never causes cards to jump to a different template.
 */
object FocusWallLayoutEngine {
    fun layout(
        items: List<FocusWallLayoutItem>,
        viewportWidth: Float,
        viewportHeight: Float,
        orientation: FocusWallOrientation,
        density: FocusWallDensity,
        focusedItemId: String? = null,
        gapPx: Float = 4f,
    ): List<FocusWallPlacement> {
        if (items.isEmpty() || viewportWidth <= 0f || viewportHeight <= 0f) return emptyList()

        val scale = when (density) {
            FocusWallDensity.Small -> 0.84f
            FocusWallDensity.Medium -> 1f
            FocusWallDensity.Large -> 1.16f
        }
        val unit = when (orientation) {
            FocusWallOrientation.Portrait -> viewportWidth * scale / BlockColumns
            FocusWallOrientation.Landscape -> viewportHeight * 0.92f * scale / BlockRows
        }
        val blockWidth = unit * BlockColumns
        val blockHeight = unit * BlockRows
        val safeGap = gapPx.coerceIn(1f, unit * 0.08f)
        val focusedIndex = focusedItemId?.let { id -> items.indexOfFirst { it.id == id } } ?: -1
        val focusedBlock = focusedIndex.takeIf { it >= 0 }?.div(ItemsPerBlock)
        val placements = ArrayList<FocusWallPlacement>(items.size)

        val blockCount = ceil(items.size / ItemsPerBlock.toDouble()).toInt()
        repeat(blockCount) { blockIndex ->
            val start = blockIndex * ItemsPerBlock
            val end = min(items.size, start + ItemsPerBlock)
            val (rawColumn, rawRow) = mosaicCoordinate(blockIndex)
            val blockColumn = if (orientation == FocusWallOrientation.Portrait) rawRow else rawColumn
            val blockRow = if (orientation == FocusWallOrientation.Portrait) rawColumn else rawRow
            val originX = blockColumn * blockWidth - blockWidth / 2f
            val originY = blockRow * blockHeight - blockHeight / 2f
            val blockSeed = mixBlockSeed(blockColumn, blockRow, items[start].stableSeed)
            val mirrorX = (blockSeed and 1) != 0
            val mirrorY = (blockSeed and 2) != 0
            val isFocusedBlock = blockIndex == focusedBlock
            val template = if (isFocusedBlock) {
                FocusedBlockTemplate
            } else {
                BaseBlockTemplates[stableBucket(blockSeed, BaseBlockTemplates.size)]
            }
            val itemIndices = if (isFocusedBlock) {
                buildList {
                    add(focusedIndex)
                    for (index in start until end) if (index != focusedIndex) add(index)
                }
            } else {
                (start until end).toList()
            }

            itemIndices.forEachIndexed { slotIndex, itemIndex ->
                val item = items[itemIndex]
                val slot = template[slotIndex].mirrored(mirrorX, mirrorY)
                val left = originX + slot.x * unit + safeGap / 2f
                val top = originY + slot.y * unit + safeGap / 2f
                val width = slot.columns * unit - safeGap
                val height = slot.rows * unit - safeGap
                placements += FocusWallPlacement(
                    itemId = item.id,
                    index = itemIndex,
                    centerX = left + width / 2f,
                    centerY = top + height / 2f,
                    width = width,
                    height = height,
                    blockIndex = blockIndex,
                )
            }
        }

        // The visibility query performs a binary search on this ordering.
        return placements.sortedWith(
            compareBy<FocusWallPlacement> { it.centerY }.thenBy { it.centerX },
        )
    }

    private fun BlockSlot.mirrored(horizontal: Boolean, vertical: Boolean): BlockSlot = copy(
        x = if (horizontal) BlockColumns - x - columns else x,
        y = if (vertical) BlockRows - y - rows else y,
    )

    private fun mixBlockSeed(column: Int, row: Int, itemSeed: Int): Int {
        var mixed = itemSeed xor (column * 0x45D9F3B) xor (row * 0x119DE1F3)
        mixed = mixed xor (mixed ushr 16)
        mixed *= 0x45D9F3B
        return mixed xor (mixed ushr 16)
    }

    private fun stableBucket(seed: Int, count: Int): Int = (abs(seed.toLong()) % count).toInt()

    /** Balanced square spiral used for whole blocks, never individual cards. */
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

    private fun spiralCoordinate(index: Int): Pair<Int, Int> {
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
