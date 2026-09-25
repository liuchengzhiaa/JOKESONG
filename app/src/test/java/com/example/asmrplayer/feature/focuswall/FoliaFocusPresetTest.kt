package com.example.asmrplayer.feature.focuswall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape and size presets are a table of hero rectangles, and the only thing that
 * makes one legal is that the eleven neighbours can still tile the rest of the block.
 * These tests enumerate the table instead of checking a preset at a time, so adding a
 * row to it is what gets verified -- not the row that was added by hand.
 */
class FoliaFocusPresetTest {
    @Test
    fun `every focus preset covers its block exactly at every legal origin`() {
        FoliaFocusShape.entries.forEach { shape ->
            FoliaFocusSize.entries.forEach { size ->
                val hero = FoliaWallLayoutEngine.heroCells(shape, size)
                hero.xs.forEach { heroX ->
                    hero.ys.forEach { heroY ->
                        (0..1).forEach { variant ->
                            val pieces = FoliaWallLayoutEngine.focusCoverCells(shape, size, heroX, heroY, variant)
                            val where = "$shape/$size at ($heroX, $heroY) variant $variant"

                            assertEquals("$where did not produce twelve slots", 12, pieces.size)
                            assertEquals(
                                "$where did not put the hero first",
                                FoliaCellRect(heroX, heroY, hero.columns, hero.rows),
                                pieces.first(),
                            )

                            val cells = mutableListOf<Pair<Int, Int>>()
                            pieces.forEach { piece ->
                                assertTrue("$where produced an empty piece", piece.cells > 0)
                                for (x in piece.x until piece.x + piece.columns) {
                                    for (y in piece.y until piece.y + piece.rows) {
                                        assertTrue(
                                            "$where left the block at ($x, $y)",
                                            x in 0 until BlockColumns && y in 0 until BlockRows,
                                        )
                                        cells += x to y
                                    }
                                }
                            }
                            assertEquals("$where did not cover the block", BlockCells, cells.size)
                            assertEquals("$where stacked two cards", cells.size, cells.toSet().size)

                            // A neighbour of one or two cells reads as a sliver beside
                            // the hero rather than as a card, which is what the sizes
                            // deliberately stop short of.
                            pieces.drop(1).forEach { piece ->
                                assertTrue(
                                    "$where made a ${piece.columns}x${piece.rows} sliver",
                                    piece.cells >= MinNeighbourCells,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `the size preset grows the hero`() {
        FoliaFocusShape.entries.forEach { shape ->
            val areas = FoliaFocusSize.entries.map { size ->
                FoliaWallLayoutEngine.heroCells(shape, size).let { it.columns * it.rows }
            }
            assertEquals("$shape preset areas are not ascending: $areas", areas.sorted(), areas)
            assertEquals("$shape reuses one area for two sizes: $areas", areas.size, areas.distinct().size)
        }
    }

    @Test
    fun `each shape keeps the aspect it is named for`() {
        FoliaFocusShape.entries.forEach { shape ->
            FoliaFocusSize.entries.forEach { size ->
                val hero = FoliaWallLayoutEngine.heroCells(shape, size)
                val where = "$shape/$size is ${hero.columns}x${hero.rows}"
                when (shape) {
                    FoliaFocusShape.Square -> assertEquals(where, hero.columns, hero.rows)
                    FoliaFocusShape.Portrait -> assertTrue(where, hero.rows > hero.columns)
                    FoliaFocusShape.Landscape -> assertTrue(where, hero.columns > hero.rows)
                }
            }
        }
    }

    /**
     * A hero that filled the block's height would leave only the two side bands for
     * eleven neighbours, so the origins it may take are narrower than the block. No
     * preset is that tall today; the rule is here so one that is still works.
     */
    @Test
    fun `a hero as tall as the block keeps room on both sides`() {
        (0..10).forEach { heroColumns ->
            val xs = foliaHeroXRange(heroColumns, BlockRows)
            assertTrue("a full-height $heroColumns-wide hero had nowhere to go", !xs.isEmpty())
            assertTrue("a full-height $heroColumns-wide hero reached the edge", xs.first >= 1)
            assertTrue("a full-height $heroColumns-wide hero reached the edge", xs.last <= BlockColumns - heroColumns - 1)
        }
    }

    /** The same rule on the other axis, for a hero as wide as the block. */
    @Test
    fun `a hero as wide as the block keeps room above and below`() {
        (0..6).forEach { heroRows ->
            val ys = foliaHeroYRange(BlockColumns, heroRows)
            assertTrue("a full-width $heroRows-tall hero had nowhere to go", !ys.isEmpty())
            assertTrue("a full-width $heroRows-tall hero reached the edge", ys.first >= 1)
            assertTrue("a full-width $heroRows-tall hero reached the edge", ys.last <= BlockRows - heroRows - 1)
        }
    }

    private companion object {
        /** `FoliaBlockColumns`, which is private to the engine. */
        const val BlockColumns = 12

        /** `FoliaBlockRows`. */
        const val BlockRows = 8

        /** `FoliaBlockColumns * FoliaBlockRows`. */
        const val BlockCells = 96

        /** A card this small is a sliver, not a neighbour. */
        const val MinNeighbourCells = 3
    }
}
