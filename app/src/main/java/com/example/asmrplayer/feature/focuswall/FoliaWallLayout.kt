package com.example.asmrplayer.feature.focuswall

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class FoliaSlot(
    val x: Int,
    val y: Int,
    val columns: Int,
    val rows: Int,
) {
    val centerX: Float get() = x + columns / 2f
    val centerY: Float get() = y + rows / 2f
    val aspectRatio: Float get() = columns.toFloat() / rows
    val area: Int get() = columns * rows
}

private const val FoliaBlockColumns = 12
private const val FoliaBlockRows = 8
private const val FoliaItemsPerBlock = 12

/**
 * Independently-authored exact covers for a compact 12 x 8 wall block. The
 * templates intentionally mix square, portrait and landscape cards. Stable
 * reflection per block supplies more variety without introducing random state.
 *
 * The Folia project inspired the block/reflow model, but these coordinates and
 * the assignment algorithm are original to this application.
 */
private val FoliaBaseTemplates = listOf(
    listOf(
        FoliaSlot(0, 0, 4, 3),
        FoliaSlot(0, 3, 2, 3),
        FoliaSlot(2, 3, 2, 3),
        FoliaSlot(0, 6, 2, 2),
        FoliaSlot(2, 6, 2, 2),
        FoliaSlot(4, 0, 5, 4),
        FoliaSlot(9, 0, 3, 2),
        FoliaSlot(9, 2, 3, 2),
        FoliaSlot(4, 4, 3, 4),
        FoliaSlot(7, 4, 2, 2),
        FoliaSlot(9, 4, 3, 2),
        FoliaSlot(7, 6, 5, 2),
    ),
    listOf(
        FoliaSlot(0, 0, 3, 3),
        FoliaSlot(3, 0, 5, 3),
        FoliaSlot(8, 0, 2, 3),
        FoliaSlot(10, 0, 2, 3),
        FoliaSlot(0, 3, 5, 3),
        FoliaSlot(0, 6, 2, 2),
        FoliaSlot(2, 6, 3, 2),
        FoliaSlot(5, 3, 3, 2),
        FoliaSlot(5, 5, 3, 3),
        FoliaSlot(8, 3, 4, 2),
        FoliaSlot(8, 5, 2, 3),
        FoliaSlot(10, 5, 2, 3),
    ),
)

/** Aspect of the focused cover. Square, the default, is the original 6 x 6 hero. */
enum class FoliaFocusShape {
    Square,
    Portrait,
    Landscape,
}

/** How large the focused cover grows. */
enum class FoliaFocusSize {
    Small,
    Medium,
    Large,
}

/**
 * The hero is the only card that changes shape, and it is a rectangle in every
 * preset: the eleven neighbours have to be packed around it as an exact cover, and a
 * rectangular complement is what keeps that cover four clean regions. The preset
 * decides the shape and [FoliaFocusSize] decides the area; the item's own slot then
 * decides where inside the block it lands, which is what makes a focused card grow
 * where it already is instead of jumping to a block corner.
 *
 * The sizes stop short of what the block could hold. An 8 x 8 hero leaves 32 cells
 * for eleven neighbours, and at the origins a card in the middle of a block asks for,
 * six of them come out two cells or less: the block then reads as one huge cover with
 * slivers around it rather than as a mosaic with a focus. 7 x 7 is the largest square,
 * 5 x 7 the tallest and 8 x 6 the widest that still gives every neighbour room, and
 * the cover test holds the whole table to a floor of three cells each.
 */
private fun foliaHeroSlot(shape: FoliaFocusShape, size: FoliaFocusSize): FoliaSlot = when (shape) {
    FoliaFocusShape.Square -> when (size) {
        FoliaFocusSize.Small -> FoliaSlot(0, 0, 4, 4)
        FoliaFocusSize.Medium -> FoliaSlot(0, 0, 6, 6)
        FoliaFocusSize.Large -> FoliaSlot(0, 0, 7, 7)
    }
    FoliaFocusShape.Portrait -> when (size) {
        FoliaFocusSize.Small -> FoliaSlot(0, 0, 3, 4)
        FoliaFocusSize.Medium -> FoliaSlot(0, 0, 4, 6)
        FoliaFocusSize.Large -> FoliaSlot(0, 0, 5, 7)
    }
    FoliaFocusShape.Landscape -> when (size) {
        FoliaFocusSize.Small -> FoliaSlot(0, 0, 5, 3)
        FoliaFocusSize.Medium -> FoliaSlot(0, 0, 7, 5)
        FoliaFocusSize.Large -> FoliaSlot(0, 0, 8, 6)
    }
}

/**
 * Columns the hero may occupy. No preset fills the block's height, so this is normally
 * the whole range the hero fits in. A taller one would have nothing above or below it
 * and only the two side bands to hold eleven neighbours, so it would have to keep at
 * least one column on each side -- that rule is here so such a preset fails loudly in
 * the cover test rather than at runtime.
 */
internal fun foliaHeroXRange(heroColumns: Int, heroRows: Int): IntRange {
    val max = FoliaBlockColumns - heroColumns
    return if (heroRows >= FoliaBlockRows) 1..(max - 1) else 0..max
}

/** Rows the hero may occupy, mirroring [foliaHeroXRange]. */
internal fun foliaHeroYRange(heroColumns: Int, heroRows: Int): IntRange {
    val max = FoliaBlockRows - heroRows
    return if (heroColumns >= FoliaBlockColumns) 1..(max - 1) else 0..max
}

/** One rectangle of a block cover, in block cells. */
internal data class FoliaCellRect(val x: Int, val y: Int, val columns: Int, val rows: Int) {
    val cells: Int get() = columns * rows
}

/** Where a preset's hero may sit, and how big it is, in block cells. */
internal data class FoliaHeroCells(
    val columns: Int,
    val rows: Int,
    val xs: IntRange,
    val ys: IntRange,
)

/**
 * Dense alternate wall modelled after Folia's observable interaction rules:
 * twelve items form each exact-cover block and only the selected block reflows.
 * The focused item's original slot decides where the hero lands, so the selected
 * cover grows where it already was, while surrounding items are assigned to their
 * nearest new slots to keep the motion around it coherent.
 */
object FoliaWallLayoutEngine {
    fun layout(
        items: List<FocusWallLayoutItem>,
        viewportWidth: Float,
        viewportHeight: Float,
        orientation: FocusWallOrientation,
        density: FocusWallDensity,
        focusedItemId: String? = null,
        gapPx: Float = 2f,
        focusShape: FoliaFocusShape = FoliaFocusShape.Square,
        focusSize: FoliaFocusSize = FoliaFocusSize.Medium,
    ): List<FocusWallPlacement> {
        if (items.isEmpty() || viewportWidth <= 0f || viewportHeight <= 0f) return emptyList()

        val scale = when (density) {
            FocusWallDensity.Small -> 0.84f
            FocusWallDensity.Medium -> 1f
            FocusWallDensity.Large -> 1.16f
        }
        val unit = when (orientation) {
            FocusWallOrientation.Portrait -> viewportWidth * scale / FoliaBlockColumns
            FocusWallOrientation.Landscape -> viewportHeight * 0.94f * scale / FoliaBlockRows
        }
        val blockWidth = unit * FoliaBlockColumns
        val blockHeight = unit * FoliaBlockRows
        val safeGap = gapPx.coerceIn(0.5f, unit * 0.06f)
        val focusedIndex = focusedItemId?.let { id -> items.indexOfFirst { it.id == id } } ?: -1
        val focusedBlock = focusedIndex.takeIf { it >= 0 }?.div(FoliaItemsPerBlock)
        val hero = foliaHeroSlot(focusShape, focusSize)
        val placements = ArrayList<FocusWallPlacement>(items.size)

        val blockCount = ceil(items.size / FoliaItemsPerBlock.toDouble()).toInt()
        repeat(blockCount) { blockIndex ->
            val start = blockIndex * FoliaItemsPerBlock
            val end = min(items.size, start + FoliaItemsPerBlock)
            val itemCount = end - start
            val (rawColumn, rawRow) = foliaMosaicCoordinate(blockIndex)
            val blockColumn = if (orientation == FocusWallOrientation.Portrait) rawRow else rawColumn
            val blockRow = if (orientation == FocusWallOrientation.Portrait) rawColumn else rawRow
            val originX = blockColumn * blockWidth - blockWidth / 2f
            val originY = blockRow * blockHeight - blockHeight / 2f
            val blockSeed = mixFoliaSeed(blockColumn, blockRow, items[start].stableSeed)
            val mirrorX = (blockSeed and 1) != 0
            val mirrorY = (blockSeed and 2) != 0
            val baseSlots = FoliaBaseTemplates[stableFoliaBucket(blockSeed, FoliaBaseTemplates.size)]
                .map { it.mirrored(mirrorX, mirrorY) }
            val localFocusedIndex = if (blockIndex == focusedBlock) focusedIndex - start else -1
            val slotsByItem = if (localFocusedIndex >= 0) {
                focusedSlotsByItem(
                    baseSlots = baseSlots,
                    focusedSlotIndex = localFocusedIndex,
                    itemCount = itemCount,
                    seed = blockSeed xor items[focusedIndex].stableSeed,
                    heroColumns = hero.columns,
                    heroRows = hero.rows,
                )
            } else {
                baseSlots.take(itemCount)
            }

            for (localIndex in 0 until itemCount) {
                val itemIndex = start + localIndex
                val item = items[itemIndex]
                val slot = slotsByItem[localIndex]
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

        return placements.sortedWith(
            compareBy<FocusWallPlacement> { it.centerY }.thenBy { it.centerX },
        )
    }

    private fun focusedSlotsByItem(
        baseSlots: List<FoliaSlot>,
        focusedSlotIndex: Int,
        itemCount: Int,
        seed: Int,
        heroColumns: Int,
        heroRows: Int,
    ): List<FoliaSlot> {
        // The hero keeps the configured focus shape, so the closest it can get to
        // "where the card already was" is to centre that shape on the card's own
        // centre and clamp it inside the block. A card in the middle of a block
        // therefore grows almost exactly in place; only a card that already sat in
        // a corner still ends up in one.
        val originalFocusSlot = baseSlots[focusedSlotIndex]
        val heroX = (originalFocusSlot.centerX - heroColumns / 2f)
            .roundToInt()
            .coerceIn(foliaHeroXRange(heroColumns, heroRows))
        val heroY = (originalFocusSlot.centerY - heroRows / 2f)
            .roundToInt()
            .coerceIn(foliaHeroYRange(heroColumns, heroRows))
        val targetSlots = foliaFocusCover(
            heroX = heroX,
            heroY = heroY,
            heroColumns = heroColumns,
            heroRows = heroRows,
            variant = stableFoliaBucket(seed, 2),
        )
        // A hero whose complement cannot yield one slot per remaining item would
        // otherwise leave the assignment loop below with nothing to pick and crash
        // on a -1 index. Failing here instead names the shape that does not fit.
        require(targetSlots.size >= itemCount) {
            "a ${heroColumns}x$heroRows hero cannot cover a $itemCount-item block"
        }

        val assignment = arrayOfNulls<FoliaSlot>(itemCount)
        assignment[focusedSlotIndex] = targetSlots.first()
        val remainingItems = (0 until itemCount).filter { it != focusedSlotIndex }.toMutableSet()
        val remainingSlots = (1 until targetSlots.size).toMutableSet()

        // Repeatedly commit the globally nearest pair. This keeps neighbours near
        // their old positions and is deterministic even for an incomplete block.
        while (remainingItems.isNotEmpty()) {
            var bestItem = -1
            var bestSlot = -1
            var bestCost = Float.POSITIVE_INFINITY
            remainingItems.sorted().forEach { itemIndex ->
                remainingSlots.sorted().forEach { targetIndex ->
                    val cost = movementCost(baseSlots[itemIndex], targetSlots[targetIndex])
                    if (
                        cost < bestCost ||
                        (cost == bestCost && (itemIndex < bestItem ||
                            (itemIndex == bestItem && targetIndex < bestSlot)))
                    ) {
                        bestCost = cost
                        bestItem = itemIndex
                        bestSlot = targetIndex
                    }
                }
            }
            assignment[bestItem] = targetSlots[bestSlot]
            remainingItems.remove(bestItem)
            remainingSlots.remove(bestSlot)
        }

        return assignment.map { requireNotNull(it) }
    }

    /**
     * Builds an exact cover of the 12 x 8 block whose first slot is the
     * [heroColumns] x [heroRows] hero at ([heroX], [heroY]). The hero's shape is
     * fixed, so it is the rest of the block that has to move around it.
     *
     * The complement of any rectangular window is four rectangles: a strip above
     * it, a strip below it, and a band on each side that spans only the hero's own
     * rows. Splitting each of those by recursive halving fills the block with
     * pieces that stay close to square, and a hero in a corner reproduces the
     * tightly packed corner arrangement the wall used before the hero could move.
     */
    private fun foliaFocusCover(
        heroX: Int,
        heroY: Int,
        heroColumns: Int,
        heroRows: Int,
        variant: Int,
    ): List<FoliaSlot> {
        require(heroX in 0..FoliaBlockColumns - heroColumns) { "heroX out of block" }
        require(heroY in 0..FoliaBlockRows - heroRows) { "heroY out of block" }
        val hero = FoliaSlot(heroX, heroY, heroColumns, heroRows)
        val regions = listOf(
            FoliaSlot(0, 0, FoliaBlockColumns, heroY),
            FoliaSlot(
                x = 0,
                y = heroY + heroRows,
                columns = FoliaBlockColumns,
                rows = FoliaBlockRows - heroY - heroRows,
            ),
            FoliaSlot(0, heroY, heroX, heroRows),
            FoliaSlot(
                x = heroX + heroColumns,
                y = heroY,
                columns = FoliaBlockColumns - heroX - heroColumns,
                rows = heroRows,
            ),
        ).filterNot { it.columns <= 0 || it.rows <= 0 }

        val counts = allocateFoliaPieces(regions, FoliaItemsPerBlock - 1, variant)
        return listOf(hero) + regions.flatMapIndexed { index, region ->
            splitFoliaRegion(region, counts[index], variant + index)
        }
    }

    /**
     * The hero a preset produces and every origin it may take. The cover test
     * enumerates exactly this, so a preset that cannot be covered fails there rather
     * than on the phone.
     */
    internal fun heroCells(shape: FoliaFocusShape, size: FoliaFocusSize): FoliaHeroCells {
        val hero = foliaHeroSlot(shape, size)
        return FoliaHeroCells(
            columns = hero.columns,
            rows = hero.rows,
            xs = foliaHeroXRange(hero.columns, hero.rows),
            ys = foliaHeroYRange(hero.columns, hero.rows),
        )
    }

    /**
     * The exact cover a preset produces at one origin, hero first. Exposed for the
     * same reason: the eleven neighbours have to partition the rest of the block
     * whatever the preset and wherever the hero lands.
     */
    internal fun focusCoverCells(
        shape: FoliaFocusShape,
        size: FoliaFocusSize,
        heroX: Int,
        heroY: Int,
        variant: Int,
    ): List<FoliaCellRect> {
        val hero = foliaHeroSlot(shape, size)
        return foliaFocusCover(heroX, heroY, hero.columns, hero.rows, variant)
            .map { FoliaCellRect(it.x, it.y, it.columns, it.rows) }
    }

    /**
     * Hands out [budget] pieces over [regions] in proportion to their area, one at
     * a time, always to whichever region is furthest below its share. The total is
     * therefore exact, and no region is asked for more pieces than it has cells
     * along its longer side -- which is what keeps slivers out.
     *
     * A region that reaches its cap stops taking pieces, so a budget the regions
     * cannot hold comes back short rather than overfilling one of them. The caller
     * turns that into a failure, and the cover test is what keeps it from reaching
     * a phone.
     */
    private fun allocateFoliaPieces(
        regions: List<FoliaSlot>,
        budget: Int,
        variant: Int,
    ): List<Int> {
        val areas = regions.map { it.area }
        val caps = regions.map { minOf(it.area, maxOf(it.columns, it.rows)) }
        val totalArea = areas.sum()
        val counts = IntArray(regions.size) { 1 }
        val tieBreak = variant % regions.size

        repeat(budget - regions.size) {
            var best = -1
            var bestScore = Double.NEGATIVE_INFINITY
            regions.indices.forEach { index ->
                if (counts[index] >= caps[index]) return@forEach
                val shortfall = areas[index] * budget.toDouble() / totalArea - counts[index]
                val score = shortfall + if (index == tieBreak) 0.001 else 0.0
                if (score > bestScore) {
                    bestScore = score
                    best = index
                }
            }
            if (best < 0) return@repeat
            counts[best]++
        }
        return counts.toList()
    }

    /**
     * Cuts [region] into [pieces] rectangles by halving it along its longer side,
     * giving each half the share of the pieces that its area deserves. Halving
     * keeps both halves non-empty and keeps repeating until every piece is one
     * rectangle, so the result always partitions [region] exactly.
     */
    private fun splitFoliaRegion(region: FoliaSlot, pieces: Int, axisSeed: Int): List<FoliaSlot> {
        if (pieces <= 1) return listOf(region)
        val splitByRows = when {
            region.columns > region.rows -> false
            region.rows > region.columns -> true
            else -> (axisSeed and 1) == 0
        }
        val extent = if (splitByRows) region.rows else region.columns
        if (extent < 2) return listOf(region)

        val firstPieces = pieces / 2
        val secondPieces = pieces - firstPieces
        val cut = (extent * firstPieces.toDouble() / pieces).roundToInt().coerceIn(1, extent - 1)
        val first = if (splitByRows) {
            FoliaSlot(region.x, region.y, region.columns, cut)
        } else {
            FoliaSlot(region.x, region.y, cut, region.rows)
        }
        val second = if (splitByRows) {
            FoliaSlot(region.x, region.y + cut, region.columns, region.rows - cut)
        } else {
            FoliaSlot(region.x + cut, region.y, region.columns - cut, region.rows)
        }
        return splitFoliaRegion(first, firstPieces, axisSeed + 1) +
            splitFoliaRegion(second, secondPieces, axisSeed + 1)
    }

    private fun movementCost(from: FoliaSlot, to: FoliaSlot): Float {
        val dx = from.centerX - to.centerX
        val dy = from.centerY - to.centerY
        val aspectPenalty = abs(from.aspectRatio - to.aspectRatio) * 0.35f
        return dx * dx + dy * dy + aspectPenalty
    }

    private fun FoliaSlot.mirrored(horizontal: Boolean, vertical: Boolean): FoliaSlot = copy(
        x = if (horizontal) FoliaBlockColumns - x - columns else x,
        y = if (vertical) FoliaBlockRows - y - rows else y,
    )

    private fun mixFoliaSeed(column: Int, row: Int, itemSeed: Int): Int {
        var mixed = itemSeed xor (column * 0x45D9F3B) xor (row * 0x119DE1F3)
        mixed = mixed xor (mixed ushr 16)
        mixed *= 0x45D9F3B
        return mixed xor (mixed ushr 16)
    }

    private fun stableFoliaBucket(seed: Int, count: Int): Int =
        (abs(seed.toLong()) % count).toInt()

    private fun foliaMosaicCoordinate(index: Int): Pair<Int, Int> = when (index) {
        0 -> 0 to 0
        1 -> -1 to 0
        2 -> 1 to 0
        3 -> 0 to -1
        4 -> 0 to 1
        5 -> -1 to -1
        6 -> 1 to -1
        7 -> -1 to 1
        8 -> 1 to 1
        else -> foliaSpiralCoordinate(index)
    }

    private fun foliaSpiralCoordinate(index: Int): Pair<Int, Int> {
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
