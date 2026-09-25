package com.example.asmrplayer.feature.focuswall

import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoliaWallLayoutTest {
    private val items = List(48) { index ->
        FocusWallLayoutItem(id = "item-$index", stableSeed = index * 7919)
    }

    /** Block 0 and block 1 are complete, so every focused layout here covers a whole block. */
    private val completeBlockItems = 24

    @Test
    fun `layout is deterministic and tightly packs twelve mixed cards per block`() {
        val first = layout()
        val second = layout()
        val block = first.filter { it.blockIndex == 0 }
        val ratios = block.map { it.width / it.height }

        assertEquals(first, second)
        assertEquals(12, block.size)
        assertTrue(ratios.any { it < 0.8f })
        assertTrue(ratios.any { it in 0.9f..1.1f })
        assertTrue(ratios.any { it > 1.5f })
        assertTrue(block.map { it.width to it.height }.distinct().size >= 6)
        assertNoOverlap(block)
    }

    @Test
    fun `different original slots select different focus gears`() {
        val firstFocus = layout(focusedItemId = "item-0")
            .associateBy(FocusWallPlacement::itemId)
            .getValue("item-0")
        val laterFocus = layout(focusedItemId = "item-8")
            .associateBy(FocusWallPlacement::itemId)
            .getValue("item-8")

        assertEquals(firstFocus.width, firstFocus.height, 0.01f)
        assertEquals(laterFocus.width, laterFocus.height, 0.01f)
        assertNotEquals(firstFocus.centerX to firstFocus.centerY, laterFocus.centerX to laterFocus.centerY)
    }

    @Test
    fun `focus reflows only the selected block`() {
        val base = layout()
        val focusedTop = layout(focusedItemId = "item-0")
        val focusedBottom = layout(focusedItemId = "item-8")
        val topHero = focusedTop.associateBy(FocusWallPlacement::itemId).getValue("item-0")
        val bottomHero = focusedBottom.associateBy(FocusWallPlacement::itemId).getValue("item-8")

        assertNoOverlap(focusedTop.filter { it.blockIndex == topHero.blockIndex })
        assertNoOverlap(focusedBottom.filter { it.blockIndex == bottomHero.blockIndex })

        val baseById = base.associateBy(FocusWallPlacement::itemId)
        focusedTop.filter { it.blockIndex != topHero.blockIndex }.forEach { placement ->
            assertEquals(baseById.getValue(placement.itemId), placement)
        }
    }

    /**
     * The complaint this replaced: a focused cover always jumped to a corner of its
     * block. The hero is a fixed 6 x 6 square, so the closest it can get to the card
     * the user tapped is the nearest place that square fits -- and that is what it
     * must take. The old quadrant mirroring missed that by up to 1.6 units for a
     * card in the middle of its block, so this bound does fail without the fix.
     */
    @Test
    fun `the focused hero lands as close to the tapped card as its shape allows`() {
        val base = layout().associateBy(FocusWallPlacement::itemId)
        (0 until completeBlockItems).forEach { index ->
            val id = "item-$index"
            val hero = layout(focusedItemId = id)
                .associateBy(FocusWallPlacement::itemId)
                .getValue(id)
            val original = base.getValue(id)
            val reachable = (0..6).flatMap { heroX ->
                (0..2).map { heroY -> heroX + 3f to heroY + 3f }
            }.minOf { (centerX, centerY) ->
                distance(original.centerX, original.centerY, centerX, centerY)
            }
            val actual = distance(original.centerX, original.centerY, hero.centerX, hero.centerY)

            assertTrue(
                "$id moved $actual where $reachable was reachable",
                actual <= reachable + 0.05f,
            )
        }
    }

    /**
     * The hero sits where the tapped card was, so its origin now varies with that
     * card instead of collapsing onto the four block corners. Quadrant mirroring
     * could only ever reach those four; block 0 on its own now reaches seven
     * distinct origins, three of them interior, and the thresholds below are what
     * that one block already guarantees.
     */
    @Test
    fun `focused heroes are no longer confined to block corners`() {
        val corners = setOf(0 to 0, 6 to 0, 0 to 2, 6 to 2)
        val origins = (0 until completeBlockItems).map { index ->
            heroOrigin("item-$index")
        }
        val interior = origins.count { it !in corners }

        assertTrue("only ${origins.distinct().size} distinct origins", origins.distinct().size >= 7)
        assertTrue("only $interior of ${origins.size} interior", interior >= 3)
    }

    /**
     * The cover generator is the only thing standing between a moved hero and a
     * block with a hole or two stacked cards in it, so every focused layout is
     * checked for an exact cover of the twelve by eight block.
     */
    @Test
    fun `every focus shape remains an exact cover of its block`() {
        (0 until completeBlockItems).forEach { index ->
            val id = "item-$index"
            val focused = layout(focusedItemId = id)
            val blockIndex = focused.associateBy(FocusWallPlacement::itemId).getValue(id).blockIndex
            val block = focused.filter { it.blockIndex == blockIndex }
            val unit = blockUnit(block)

            val cells = block.sumOf { placement ->
                val columns = ((placement.width + GapPx) / unit).roundToInt()
                val rows = ((placement.height + GapPx) / unit).roundToInt()
                assertTrue("$id produced a sliver", columns >= 1 && rows >= 1)
                columns * rows
            }

            assertEquals("$id did not cover its block", BlockCells, cells)
            assertNoOverlap(block)
        }
    }

    /**
     * The same guarantee as `every focus shape remains an exact cover of its block`,
     * for every shape and size the panel can ask for. This is the one that would catch
     * a preset the cover generator cannot honour, since the engine would otherwise
     * throw while placing the neighbours.
     */
    @Test
    fun `every focus preset still lays out a whole block without a hole`() {
        FoliaFocusShape.entries.forEach { shape ->
            FoliaFocusSize.entries.forEach { size ->
                (0 until completeBlockItems).forEach { index ->
                    val id = "item-$index"
                    val focused = layout(focusedItemId = id, shape = shape, size = size)
                    val blockIndex = focused.associateBy(FocusWallPlacement::itemId).getValue(id).blockIndex
                    val block = focused.filter { it.blockIndex == blockIndex }
                    val where = "$shape/$size on $id"

                    assertEquals("$where lost a card", 12, block.size)
                    val unit = blockUnit(block)
                    val cells = block.sumOf { placement ->
                        val columns = ((placement.width + GapPx) / unit).roundToInt()
                        val rows = ((placement.height + GapPx) / unit).roundToInt()
                        assertTrue("$where produced a sliver", columns >= 1 && rows >= 1)
                        columns * rows
                    }

                    assertEquals("$where did not cover its block", BlockCells, cells)
                    assertNoOverlap(block)
                }
            }
        }
    }

    @Test
    fun `large scene visibility remains bounded`() {
        val largeItems = List(40_000) { index -> FocusWallLayoutItem("large-$index", index) }
        val placements = FoliaWallLayoutEngine.layout(
            items = largeItems,
            viewportWidth = 1080f,
            viewportHeight = 1800f,
            orientation = FocusWallOrientation.Portrait,
            density = FocusWallDensity.Medium,
        )
        val visible = placementsNearWorldRect(
            placements = placements,
            minCenterX = -1000f,
            maxCenterX = 1000f,
            minCenterY = -1500f,
            maxCenterY = 1500f,
        )

        assertTrue(visible.isNotEmpty())
        assertTrue(visible.size < 260)
    }

    private fun layout(
        focusedItemId: String? = null,
        shape: FoliaFocusShape = FoliaFocusShape.Square,
        size: FoliaFocusSize = FoliaFocusSize.Medium,
    ) = FoliaWallLayoutEngine.layout(
        items = items,
        viewportWidth = 1080f,
        viewportHeight = 1800f,
        orientation = FocusWallOrientation.Portrait,
        density = FocusWallDensity.Medium,
        focusedItemId = focusedItemId,
        focusShape = shape,
        focusSize = size,
    )

    /**
     * Recovers the hero's slot in block coordinates. An unfocused block is an exact
     * cover whose top-left cell starts at the block origin, so the block origin and
     * the cell size can both be read back off it.
     */
    private fun heroOrigin(itemId: String): Pair<Int, Int> {
        val hero = layout(focusedItemId = itemId)
            .associateBy(FocusWallPlacement::itemId)
            .getValue(itemId)
        val block = layout().filter { it.blockIndex == hero.blockIndex }
        val unit = blockUnit(block)
        val originX = block.minOf { it.centerX - it.width / 2f }
        val originY = block.minOf { it.centerY - it.height / 2f }
        return ((hero.centerX - hero.width / 2f - originX) / unit).roundToInt() to
            ((hero.centerY - hero.height / 2f - originY) / unit).roundToInt()
    }

    /** Cell size in pixels, read from the width of a full block. */
    private fun blockUnit(block: List<FocusWallPlacement>): Float {
        val extent = block.maxOf { it.centerX + it.width / 2f } -
            block.minOf { it.centerX - it.width / 2f }
        return (extent + GapPx) / FoliaBlockColumnsForTest
    }

    private fun distance(fromX: Float, fromY: Float, toX: Float, toY: Float): Float {
        val dx = fromX - toX
        val dy = fromY - toY
        return sqrt(dx * dx + dy * dy)
    }

    private fun assertNoOverlap(block: List<FocusWallPlacement>) {
        block.forEachIndexed { index, first ->
            block.drop(index + 1).forEach { second ->
                assertTrue("${first.itemId} overlaps ${second.itemId}", separated(first, second))
            }
        }
    }

    private fun separated(first: FocusWallPlacement, second: FocusWallPlacement): Boolean {
        val firstLeft = first.centerX - first.width / 2f
        val firstRight = first.centerX + first.width / 2f
        val firstTop = first.centerY - first.height / 2f
        val firstBottom = first.centerY + first.height / 2f
        val secondLeft = second.centerX - second.width / 2f
        val secondRight = second.centerX + second.width / 2f
        val secondTop = second.centerY - second.height / 2f
        val secondBottom = second.centerY + second.height / 2f
        return firstRight <= secondLeft || secondRight <= firstLeft ||
            firstBottom <= secondTop || secondBottom <= firstTop
    }

    private companion object {
        /** `FoliaWallLayoutEngine`'s default gutter, and the layout above uses it. */
        const val GapPx = 2f

        /** `FoliaBlockColumns`, which is private to the engine. */
        const val FoliaBlockColumnsForTest = 12

        /** `FoliaBlockColumns * FoliaBlockRows`. */
        const val BlockCells = 96
    }
}
