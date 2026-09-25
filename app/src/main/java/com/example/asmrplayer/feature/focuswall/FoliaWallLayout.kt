package com.example.asmrplayer.feature.focuswall

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One card's footprint in a block, in block cells: the rectangle it is placed by, and,
 * when it is not a plain rectangle, the cells it actually fills.
 *
 * [x] and [y] are always the bounding box's corner, so a shaped card still has a centre
 * and an aspect ratio to be moved and animated with. A card that carries [cells] fills
 * exactly those and is never cut into more cards, because halving it would tear a
 * silhouette in two.
 */
internal data class FoliaPiece(
    val x: Int,
    val y: Int,
    val columns: Int,
    val rows: Int,
    val cells: List<Int> = emptyList(),
) {
    val centerX: Float get() = x + columns / 2f
    val centerY: Float get() = y + rows / 2f
    val aspectRatio: Float get() = columns.toFloat() / rows
    val area: Int get() = if (cells.isEmpty()) columns * rows else cells.size
    val isRectangular: Boolean get() = cells.isEmpty()

    /** The block cells this card fills, packed as `row * FoliaBlockColumns + column`. */
    val filledCells: List<Int>
        get() = if (cells.isNotEmpty()) {
            cells
        } else {
            (y until y + rows).flatMap { row ->
                (x until x + columns).map { column -> foliaCell(column, row) }
            }
        }
}

private const val FoliaBlockColumns = 12
private const val FoliaBlockRows = 8
private const val FoliaItemsPerBlock = 12

/**
 * Smallest area a neighbour may be given, in block cells. Below this a card is a sliver
 * rather than a cover, and the block reads as one huge focus with debris around it.
 */
private const val FoliaMinPieceCells = 3

/** A block cell as one number, so a cell set can be a plain list. */
private fun foliaCell(column: Int, row: Int): Int = row * FoliaBlockColumns + column

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
        FoliaPiece(0, 0, 4, 3),
        FoliaPiece(0, 3, 2, 3),
        FoliaPiece(2, 3, 2, 3),
        FoliaPiece(0, 6, 2, 2),
        FoliaPiece(2, 6, 2, 2),
        FoliaPiece(4, 0, 5, 4),
        FoliaPiece(9, 0, 3, 2),
        FoliaPiece(9, 2, 3, 2),
        FoliaPiece(4, 4, 3, 4),
        FoliaPiece(7, 4, 2, 2),
        FoliaPiece(9, 4, 3, 2),
        FoliaPiece(7, 6, 5, 2),
    ),
    listOf(
        FoliaPiece(0, 0, 3, 3),
        FoliaPiece(3, 0, 5, 3),
        FoliaPiece(8, 0, 2, 3),
        FoliaPiece(10, 0, 2, 3),
        FoliaPiece(0, 3, 5, 3),
        FoliaPiece(0, 6, 2, 2),
        FoliaPiece(2, 6, 3, 2),
        FoliaPiece(5, 3, 3, 2),
        FoliaPiece(5, 5, 3, 3),
        FoliaPiece(8, 3, 4, 2),
        FoliaPiece(8, 5, 2, 3),
        FoliaPiece(10, 5, 2, 3),
    ),
)

/** Aspect of the focused cover. Square, the default, is the original 6 x 6 hero. */
enum class FoliaFocusShape {
    Square,
    Portrait,
    Landscape,

    /**
     * A square that shows the whole cover: the artwork is letterboxed inside the card
     * instead of being cropped to fill it, so nothing is hidden whatever the artwork's
     * own proportions are.
     */
    FullCover,

    /** An eight by six pixel heart, centred in the block. */
    Heart,

    /** A pixel disc eight cells across, centred in the block. */
    Circle;

    /**
     * Silhouettes are drawn cell by cell rather than as a rectangle, so they are written
     * out for one place in the block and have one size each: there is no second size to
     * grow into. They also ignore the tapped card's slot, unlike a rectangular hero.
     */
    val isSilhouette: Boolean get() = this == Heart || this == Circle
}

/** How large the focused cover grows. */
enum class FoliaFocusSize {
    Small,
    Medium,
    Large,
}

/**
 * The hero's bounding box. Square, portrait, landscape and 完整封面 are rectangles: the
 * preset decides the shape and [FoliaFocusSize] the area, and the item's own slot then
 * decides where inside the block the rectangle lands, which is what makes a focused card
 * grow where it already is instead of jumping to a block corner.
 *
 * The sizes stop short of what the block could hold. An 8 x 8 hero leaves 32 cells
 * for eleven neighbours, and at the origins a card in the middle of a block asks for,
 * six of them come out two cells or less: the block then reads as one huge cover with
 * slivers around it rather than as a mosaic with a focus. 7 x 7 is the largest square,
 * 5 x 7 the tallest and 8 x 6 the widest that still gives every neighbour room, and
 * the cover test holds the whole table to a floor of three cells each.
 *
 * Heart and circle are silhouettes, and their box is simply where their mask sits in the
 * block. That place is fixed: the outline of a shape that is not a rectangle cannot be
 * re-cut for every slot a card might have been tapped in, so those two ignore the tapped
 * card rather than growing in place.
 */
private fun foliaHeroSlot(shape: FoliaFocusShape, size: FoliaFocusSize): FoliaPiece = when (shape) {
    FoliaFocusShape.Square -> when (size) {
        FoliaFocusSize.Small -> FoliaPiece(0, 0, 4, 4)
        FoliaFocusSize.Medium -> FoliaPiece(0, 0, 6, 6)
        FoliaFocusSize.Large -> FoliaPiece(0, 0, 7, 7)
    }
    FoliaFocusShape.Portrait -> when (size) {
        FoliaFocusSize.Small -> FoliaPiece(0, 0, 3, 4)
        FoliaFocusSize.Medium -> FoliaPiece(0, 0, 4, 6)
        FoliaFocusSize.Large -> FoliaPiece(0, 0, 5, 7)
    }
    FoliaFocusShape.Landscape -> when (size) {
        FoliaFocusSize.Small -> FoliaPiece(0, 0, 5, 3)
        FoliaFocusSize.Medium -> FoliaPiece(0, 0, 7, 5)
        FoliaFocusSize.Large -> FoliaPiece(0, 0, 8, 6)
    }
    // The whole cover is the square card with the artwork fitted inside it instead of
    // cropped to fill; the footprint the neighbours have to cover is the square's.
    FoliaFocusShape.FullCover -> foliaHeroSlot(FoliaFocusShape.Square, size)
    FoliaFocusShape.Heart -> FoliaHeart.piece
    FoliaFocusShape.Circle -> FoliaCircle.piece
}

/** A silhouette hero and the neighbour regions that cover the rest of the block. */
private data class FoliaSilhouette(val piece: FoliaPiece, val regions: List<FoliaPiece>)

/**
 * The heart the focused cover becomes, as the columns it fills on each block row: eight
 * wide and six tall, centred in the block, with a two-cell dip between the lobes. The dip
 * has to be two cells wide to stay symmetric, which is what makes eight the narrowest
 * heart this block can draw, and the two-by-two pocket it leaves is a card of its own
 * between the lobes.
 */
private val FoliaHeartSpans = listOf(
    1 to 2..4,
    1 to 7..9,
    2 to 2..4,
    2 to 7..9,
    3 to 2..9,
    4 to 3..8,
    5 to 4..7,
    6 to 5..6,
)

/**
 * The circle the focused cover becomes: eight cells across and eight rows tall, so it is
 * centred in the block exactly, with rows of four, six and then eight cells. Four corners
 * of its box are left over, and each is an L-shaped neighbour.
 */
private val FoliaCircleSpans = listOf(
    0 to 4..7,
    1 to 3..8,
    2 to 2..9,
    3 to 2..9,
    4 to 2..9,
    5 to 2..9,
    6 to 3..8,
    7 to 4..7,
)

/**
 * The heart's eleven neighbours, hand-written the way the Bento wall's focused block is.
 * The complement of a silhouette cannot be cut out of rectangles by halving them the way
 * a rectangular hero's can: the heart's sloping edge leaves pockets one and two cells
 * wide. Two regions are therefore given as explicit cells -- one L on each side of the
 * heart's point, whose two-by-two box shows three cells -- and the rest are rectangles.
 * Heart and neighbours together cover the block exactly once; [foliaSilhouetteCover]
 * checks that rather than trusting it.
 */
private val FoliaHeart = FoliaSilhouette(
    piece = foliaSilhouettePiece(FoliaHeartSpans),
    regions = listOf(
        FoliaPiece(0, 0, FoliaBlockColumns, 1),
        FoliaPiece(0, 7, FoliaBlockColumns, 1),
        FoliaPiece(0, 1, 2, 6),
        FoliaPiece(10, 1, 2, 6),
        FoliaPiece(5, 1, 2, 2),
        FoliaPiece(2, 4, 1, 3),
        FoliaPiece(9, 4, 1, 3),
        FoliaPiece(
            3, 5, 2, 2,
            cells = listOf(foliaCell(3, 5), foliaCell(3, 6), foliaCell(4, 6)),
        ),
        FoliaPiece(
            7, 5, 2, 2,
            cells = listOf(foliaCell(8, 5), foliaCell(7, 6), foliaCell(8, 6)),
        ),
    ),
)

/** The circle's neighbours: two side bands and the four corners its box leaves over. */
private val FoliaCircle = FoliaSilhouette(
    piece = foliaSilhouettePiece(FoliaCircleSpans),
    regions = listOf(
        FoliaPiece(0, 0, 2, FoliaBlockRows),
        FoliaPiece(10, 0, 2, FoliaBlockRows),
        FoliaPiece(
            2, 0, 2, 2,
            cells = listOf(foliaCell(2, 0), foliaCell(3, 0), foliaCell(2, 1)),
        ),
        FoliaPiece(
            8, 0, 2, 2,
            cells = listOf(foliaCell(8, 0), foliaCell(9, 0), foliaCell(9, 1)),
        ),
        FoliaPiece(
            2, 6, 2, 2,
            cells = listOf(foliaCell(2, 6), foliaCell(2, 7), foliaCell(3, 7)),
        ),
        FoliaPiece(
            8, 6, 2, 2,
            cells = listOf(foliaCell(9, 6), foliaCell(9, 7), foliaCell(8, 7)),
        ),
    ),
)

/** The hero a silhouette's row spans describe, with the block cells it fills. */
private fun foliaSilhouettePiece(spans: List<Pair<Int, IntRange>>): FoliaPiece {
    val firstColumn = spans.minOf { it.second.first }
    val lastColumn = spans.maxOf { it.second.last }
    val firstRow = spans.minOf { it.first }
    val lastRow = spans.maxOf { it.first }
    return FoliaPiece(
        x = firstColumn,
        y = firstRow,
        columns = lastColumn - firstColumn + 1,
        rows = lastRow - firstRow + 1,
        cells = spans.flatMap { (row, columns) ->
            columns.map { column -> foliaCell(column, row) }
        },
    )
}

private fun FoliaFocusShape.silhouette(): FoliaSilhouette? = when (this) {
    FoliaFocusShape.Heart -> FoliaHeart
    FoliaFocusShape.Circle -> FoliaCircle
    else -> null
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

/**
 * The hero a preset produces, and every origin it may take. A silhouette has exactly one
 * origin in both directions: its outline is drawn for one place in the block.
 */
internal data class FoliaHeroCells(
    val piece: FoliaPiece,
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
                    shape = focusShape,
                    size = focusSize,
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
                    shape = slot.foliaShape(unit, safeGap),
                    fitArtwork = itemIndex == focusedIndex &&
                        focusShape == FoliaFocusShape.FullCover,
                )
            }
        }

        return placements.sortedWith(
            compareBy<FocusWallPlacement> { it.centerY }.thenBy { it.centerX },
        )
    }

    private fun focusedSlotsByItem(
        baseSlots: List<FoliaPiece>,
        focusedSlotIndex: Int,
        itemCount: Int,
        seed: Int,
        shape: FoliaFocusShape,
        size: FoliaFocusSize,
    ): List<FoliaPiece> {
        // The hero keeps the configured focus shape, so the closest it can get to
        // "where the card already was" is to centre that shape on the card's own
        // centre and clamp it inside the block. A card in the middle of a block
        // therefore grows almost exactly in place; only a card that already sat in
        // a corner still ends up in one. A silhouette cannot be re-cut for every
        // slot, so it takes the one place it is drawn for instead.
        val hero = foliaHeroSlot(shape, size)
        val originalFocusSlot = baseSlots[focusedSlotIndex]
        val heroX = if (shape.isSilhouette) {
            hero.x
        } else {
            (originalFocusSlot.centerX - hero.columns / 2f)
                .roundToInt()
                .coerceIn(foliaHeroXRange(hero.columns, hero.rows))
        }
        val heroY = if (shape.isSilhouette) {
            hero.y
        } else {
            (originalFocusSlot.centerY - hero.rows / 2f)
                .roundToInt()
                .coerceIn(foliaHeroYRange(hero.columns, hero.rows))
        }
        val targetSlots = foliaFocusCover(
            shape = shape,
            size = size,
            heroX = heroX,
            heroY = heroY,
            variant = stableFoliaBucket(seed, 2),
        )
        // A hero whose complement cannot yield one slot per remaining item would
        // otherwise leave the assignment loop below with nothing to pick and crash
        // on a -1 index. Failing here instead names the shape that does not fit.
        require(targetSlots.size >= itemCount) {
            "a $shape $size focus cannot cover a $itemCount-item block"
        }

        val assignment = arrayOfNulls<FoliaPiece>(itemCount)
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
     * Builds an exact cover of the 12 x 8 block whose first slot is the preset's hero.
     * The hero's shape is fixed, so it is the rest of the block that has to move
     * around it.
     */
    private fun foliaFocusCover(
        shape: FoliaFocusShape,
        size: FoliaFocusSize,
        heroX: Int,
        heroY: Int,
        variant: Int,
    ): List<FoliaPiece> {
        val silhouette = shape.silhouette()
        if (silhouette != null) return foliaSilhouetteCover(shape, silhouette, variant)
        return foliaRectCover(foliaHeroSlot(shape, size), heroX, heroY, variant)
    }

    /**
     * The cover of a silhouette hero, whose neighbours are the hand-written regions
     * beside its mask. Its outline leaves pockets no halving can produce, so unlike a
     * rectangle's it is authored rather than generated -- and authored twice: once as
     * the hero's cells and once as the regions beside them.
     *
     * Those two halves have to add up to the block and never overlap, and nothing at
     * the call site can check that, so this does: a mistyped cell becomes a loud
     * failure here instead of a block with a hole in it on the phone. The check is 96
     * cells and runs only while a silhouette block is laid out.
     */
    private fun foliaSilhouetteCover(
        shape: FoliaFocusShape,
        silhouette: FoliaSilhouette,
        variant: Int,
    ): List<FoliaPiece> {
        val covered = HashSet<Int>(FoliaBlockColumns * FoliaBlockRows)
        (listOf(silhouette.piece) + silhouette.regions).forEach { piece ->
            piece.filledCells.forEach { cell ->
                require(covered.add(cell)) {
                    "$shape covers cell $cell twice"
                }
            }
        }
        require(covered.size == FoliaBlockColumns * FoliaBlockRows) {
            "$shape covers ${covered.size} of the block's ${FoliaBlockColumns * FoliaBlockRows} cells"
        }
        val counts = allocateFoliaPieces(silhouette.regions, FoliaItemsPerBlock - 1, variant)
        return listOf(silhouette.piece) + silhouette.regions.flatMapIndexed { index, region ->
            splitFoliaRegion(region, counts[index], variant + index)
        }
    }

    /**
     * Builds an exact cover of the block whose first slot is the [hero] rectangle at
     * ([heroX], [heroY]).
     *
     * The complement of any rectangular window is four rectangles: a strip above
     * it, a strip below it, and a band on each side that spans only the hero's own
     * rows. Splitting each of those by recursive halving fills the block with
     * pieces that stay close to square, and a hero in a corner reproduces the
     * tightly packed corner arrangement the wall used before the hero could move.
     */
    private fun foliaRectCover(
        slot: FoliaPiece,
        heroX: Int,
        heroY: Int,
        variant: Int,
    ): List<FoliaPiece> {
        require(heroX in 0..FoliaBlockColumns - slot.columns) { "heroX out of block" }
        require(heroY in 0..FoliaBlockRows - slot.rows) { "heroY out of block" }
        val hero = slot.copy(x = heroX, y = heroY)
        val regions = listOf(
            FoliaPiece(0, 0, FoliaBlockColumns, heroY),
            FoliaPiece(
                x = 0,
                y = heroY + hero.rows,
                columns = FoliaBlockColumns,
                rows = FoliaBlockRows - heroY - hero.rows,
            ),
            FoliaPiece(0, heroY, heroX, hero.rows),
            FoliaPiece(
                x = heroX + hero.columns,
                y = heroY,
                columns = FoliaBlockColumns - heroX - hero.columns,
                rows = hero.rows,
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
            piece = hero,
            xs = if (shape.isSilhouette) hero.x..hero.x else foliaHeroXRange(hero.columns, hero.rows),
            ys = if (shape.isSilhouette) hero.y..hero.y else foliaHeroYRange(hero.columns, hero.rows),
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
    ): List<FoliaPiece> = foliaFocusCover(shape, size, heroX, heroY, variant)

    /**
     * Hands out [budget] pieces over [regions] in proportion to their area, one at
     * a time, always to whichever region is furthest below its share. The total is
     * therefore exact, and no region is asked for more pieces than it has cells
     * along its longer side, nor more than it has cells for at
     * [FoliaMinPieceCells] apiece -- which is what keeps slivers out.
     *
     * A region that reaches its cap stops taking pieces, so a budget the regions
     * cannot hold comes back short rather than overfilling one of them. The caller
     * turns that into a failure, and the cover test is what keeps it from reaching
     * a phone.
     */
    private fun allocateFoliaPieces(
        regions: List<FoliaPiece>,
        budget: Int,
        variant: Int,
    ): List<Int> {
        val areas = regions.map { it.area }
        val caps = regions.map { region ->
            when {
                // A card that is not a rectangle cannot be halved into two cards
                // without tearing its shape in two, so it is never split.
                !region.isRectangular -> 1
                else -> minOf(
                    region.area,
                    maxOf(region.columns, region.rows),
                    region.area / FoliaMinPieceCells,
                )
            }
        }
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
    private fun splitFoliaRegion(region: FoliaPiece, pieces: Int, axisSeed: Int): List<FoliaPiece> {
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
            FoliaPiece(region.x, region.y, region.columns, cut)
        } else {
            FoliaPiece(region.x, region.y, cut, region.rows)
        }
        val second = if (splitByRows) {
            FoliaPiece(region.x, region.y + cut, region.columns, region.rows - cut)
        } else {
            FoliaPiece(region.x + cut, region.y, region.columns - cut, region.rows)
        }
        return splitFoliaRegion(first, firstPieces, axisSeed + 1) +
            splitFoliaRegion(second, secondPieces, axisSeed + 1)
    }

    /**
     * Where a shaped card's cells sit inside it, in its own 0..1 box, or null for a card
     * that is a plain rectangle.
     *
     * Each cell keeps the block's grid and is pulled in by half a gutter on every side
     * that faces another card's cell, and not pulled in where the cell beside it belongs
     * to this same card. The gutter between two cards therefore comes out exactly [gap]
     * everywhere; the outline of a silhouette lands exactly on its own box, so it neither
     * spills onto its neighbours nor floats inside itself; and the cells of one card meet
     * edge to edge, leaving no seam across the artwork.
     *
     * Both the cells and the card's own box are measured from the block's corner, so the
     * two are in one coordinate system: the block's place on the wall cancels out, and
     * this is the same box the placement is built from.
     */
    private fun FoliaPiece.foliaShape(unit: Float, gap: Float): FocusWallShape? {
        if (isRectangular) return null
        val left = x * unit + gap / 2f
        val top = y * unit + gap / 2f
        val width = columns * unit - gap
        val height = rows * unit - gap
        val filled = cells.toHashSet()
        fun isOtherCard(column: Int, row: Int): Boolean =
            column !in 0 until FoliaBlockColumns ||
                row !in 0 until FoliaBlockRows ||
                foliaCell(column, row) !in filled

        return FocusWallShape(
            rects = cells.map { cell ->
                val column = cell % FoliaBlockColumns
                val row = cell / FoliaBlockColumns
                val cellLeft = if (isOtherCard(column - 1, row)) gap / 2f else 0f
                val cellTop = if (isOtherCard(column, row - 1)) gap / 2f else 0f
                val cellRight = unit - if (isOtherCard(column + 1, row)) gap / 2f else 0f
                val cellBottom = unit - if (isOtherCard(column, row + 1)) gap / 2f else 0f
                Rect(
                    left = (column * unit + cellLeft - left) / width,
                    top = (row * unit + cellTop - top) / height,
                    right = (column * unit + cellRight - left) / width,
                    bottom = (row * unit + cellBottom - top) / height,
                )
            },
        )
    }

    private fun movementCost(from: FoliaPiece, to: FoliaPiece): Float {
        val dx = from.centerX - to.centerX
        val dy = from.centerY - to.centerY
        val aspectPenalty = abs(from.aspectRatio - to.aspectRatio) * 0.35f
        return dx * dx + dy * dy + aspectPenalty
    }

    private fun FoliaPiece.mirrored(horizontal: Boolean, vertical: Boolean): FoliaPiece = copy(
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
