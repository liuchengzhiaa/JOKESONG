package com.example.asmrplayer.feature.focuswall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape and size presets are a table of hero footprints, and the only thing that
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
                                hero.piece.copy(x = heroX, y = heroY),
                                pieces.first(),
                            )

                            val cells = mutableSetOf<Pair<Int, Int>>()
                            var cellCount = 0
                            pieces.forEach { piece ->
                                assertTrue("$where produced an empty piece", piece.area > 0)
                                assertTrue(
                                    "$where made a ${piece.columns}x${piece.rows} piece",
                                    piece.columns > 0 && piece.rows > 0,
                                )
                                assertTrue(
                                    "$where placed a piece outside the block",
                                    piece.x >= 0 && piece.y >= 0 &&
                                        piece.x + piece.columns <= BlockColumns &&
                                        piece.y + piece.rows <= BlockRows,
                                )
                                val covered = piece.coveredCells()
                                // A shaped piece draws the cells it lists, so a cell it
                                // lists outside its own box would be drawn off its own
                                // card. Rectangles have no cell list, so this is only a
                                // statement about the shaped ones.
                                covered.forEach { (x, y) ->
                                    assertTrue(
                                        "$where listed a cell outside the ${piece.columns}x${piece.rows} box",
                                        x in piece.x until piece.x + piece.columns &&
                                            y in piece.y until piece.y + piece.rows,
                                    )
                                }
                                cellCount += covered.size
                                covered.forEach { cell ->
                                    assertTrue("$where stacked two cards on $cell", cells.add(cell))
                                }
                            }
                            assertEquals("$where did not cover the block", BlockCells, cellCount)
                            assertEquals("$where did not cover the block", BlockCells, cells.size)

                            // A neighbour of one or two cells reads as a sliver beside
                            // the hero rather than as a card, which is what the sizes
                            // and the silhouettes both stop short of.
                            pieces.drop(1).forEach { piece ->
                                assertTrue(
                                    "$where made a ${piece.columns}x${piece.rows} sliver",
                                    piece.area >= MinNeighbourCells,
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
        FoliaFocusShape.entries.filterNot { it.isSilhouette }.forEach { shape ->
            val areas = FoliaFocusSize.entries.map { size ->
                FoliaWallLayoutEngine.heroCells(shape, size).piece.area
            }
            assertEquals("$shape preset areas are not ascending: $areas", areas.sorted(), areas)
            assertEquals("$shape reuses one area for two sizes: $areas", areas.size, areas.distinct().size)
        }
    }

    @Test
    fun `each shape keeps the aspect it is named for`() {
        FoliaFocusShape.entries.forEach { shape ->
            FoliaFocusSize.entries.forEach { size ->
                val hero = FoliaWallLayoutEngine.heroCells(shape, size).piece
                val where = "$shape/$size is ${hero.columns}x${hero.rows}"
                when (shape) {
                    FoliaFocusShape.Square, FoliaFocusShape.FullCover ->
                        assertEquals(where, hero.columns, hero.rows)
                    FoliaFocusShape.Portrait -> assertTrue(where, hero.rows > hero.columns)
                    FoliaFocusShape.Landscape -> assertTrue(where, hero.columns > hero.rows)
                    FoliaFocusShape.Heart, FoliaFocusShape.Circle ->
                        assertTrue(where, hero.columns >= 4 && hero.rows >= 4)
                }
            }
        }
    }

    /**
     * 完整封面 shows the whole artwork but is otherwise the square preset, so it has to
     * have the square's footprint: it is the same card, drawn with the artwork fitted
     * inside it instead of cropped to fill.
     */
    @Test
    fun `the full-cover shape is the square presets`() {
        FoliaFocusSize.entries.forEach { size ->
            assertEquals(
                "完整封面/$size does not match the square preset",
                FoliaWallLayoutEngine.heroCells(FoliaFocusShape.Square, size).piece,
                FoliaWallLayoutEngine.heroCells(FoliaFocusShape.FullCover, size).piece,
            )
        }
    }

    /**
     * A silhouette is drawn once, for one place in the block, so it takes the same origin
     * whatever card was tapped and whichever size is selected -- which is what lets the
     * eleven regions beside it be hand-written. The rectangular presets are the ones that
     * follow the tapped card.
     */
    @Test
    fun `a silhouette ignores the tapped card and the size preset`() {
        FoliaFocusShape.entries.filter { it.isSilhouette }.forEach { shape ->
            val reference = FoliaWallLayoutEngine.focusCoverCells(shape, FoliaFocusSize.Small, 0, 0, 0)
            FoliaFocusSize.entries.forEach { size ->
                (0 until BlockColumns).forEach { heroX ->
                    (0 until BlockRows).forEach { heroY ->
                        assertEquals(
                            "$shape/$size moved with the tapped card at ($heroX, $heroY)",
                            reference,
                            FoliaWallLayoutEngine.focusCoverCells(shape, size, heroX, heroY, 0),
                        )
                    }
                }
            }
            assertEquals(
                "$shape has one origin, not a range",
                FoliaWallLayoutEngine.heroCells(shape, FoliaFocusSize.Small).xs,
                FoliaWallLayoutEngine.heroCells(shape, FoliaFocusSize.Large).xs,
            )
        }
    }

    /**
     * The heart and the circle are the wall's two silhouettes: what makes them read as
     * shapes rather than as a rectangle with a picture in it is that the hero is one
     * connected block of cells, mirrored down the block's centre line, and that it fills
     * its own bounding box on the middle row rather than only at a corner.
     */
    @Test
    fun `the silhouettes are connected and mirrored about the block centre`() {
        FoliaFocusShape.entries.filter { it.isSilhouette }.forEach { shape ->
            val hero = FoliaWallLayoutEngine.heroCells(shape, FoliaFocusSize.Medium).piece
            val cells = hero.coveredCells()
            val where = "$shape hero is ${hero.columns}x${hero.rows}"

            assertTrue("$where is too small to read as a shape", hero.area >= MinNeighbourCells * 4)
            assertTrue("$where does not keep every cell inside its own box", cells.all { (x, y) ->
                x in hero.x until hero.x + hero.columns && y in hero.y until hero.y + hero.rows
            })
            assertEquals(
                "$where is not mirrored about the block's centre line",
                cells,
                cells.map { (x, y) -> (BlockColumns - 1 - x) to y }.toSet(),
            )
            assertConnected(cells, where)

            // A silhouette has to be narrower than its own box somewhere, or it is a
            // rectangle; and it has to reach both sides somewhere, or its outline is
            // decoration rather than a shape.
            val rowWidths = (hero.y until hero.y + hero.rows).map { row ->
                cells.count { it.second == row }
            }
            assertTrue("$where never reaches both sides of its box", rowWidths.any { it == hero.columns })
            assertTrue("$where fills its box on every row, so it is a rectangle", rowWidths.any { it < hero.columns })
        }
    }

    /**
     * The two silhouettes are different shapes: the circle is symmetric top to bottom,
     * the heart is not, and no row of the circle is wider than its own middle.
     */
    @Test
    fun `the circle is round and the heart is not`() {
        val circle = FoliaWallLayoutEngine.heroCells(FoliaFocusShape.Circle, FoliaFocusSize.Medium).piece
        val circleCells = circle.coveredCells()
        assertEquals(
            "the circle is not mirrored top to bottom",
            circleCells,
            circleCells.map { (x, y) -> x to (BlockRows - 1 - y) }.toSet(),
        )
        val widths = (circle.y until circle.y + circle.rows).map { row ->
            circleCells.count { it.second == row }
        }
        val middle = widths.size / 2
        assertEquals(
            "the circle does not widen towards its middle",
            widths.take(middle).sorted(),
            widths.take(middle),
        )
        assertEquals(
            "the circle does not narrow after its middle",
            widths.drop(middle).sortedDescending(),
            widths.drop(middle),
        )

        val heart = FoliaWallLayoutEngine.heroCells(FoliaFocusShape.Heart, FoliaFocusSize.Medium).piece
        val heartWidths = (heart.y until heart.y + heart.rows).map { row ->
            heart.coveredCells().count { it.second == row }
        }
        assertEquals(
            "the heart is not narrowest at its point",
            heartWidths.min(),
            heartWidths.last(),
        )
        assertNotEquals(
            "the heart should have a dip between its lobes",
            heartWidths.first(),
            heartWidths.max(),
        )
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

    /**
     * The cells a piece fills, as (column, row), whether it is a rectangle or a shape.
     * This decodes the packing `FoliaPiece.filledCells` documents.
     */
    private fun FoliaPiece.coveredCells(): Set<Pair<Int, Int>> =
        filledCells.map { cell -> (cell % BlockColumns) to (cell / BlockColumns) }.toSet()

    private fun assertConnected(cells: Set<Pair<Int, Int>>, where: String) {
        val start = cells.first()
        val seen = mutableSetOf(start)
        val pending = ArrayDeque(listOf(start))
        while (pending.isNotEmpty()) {
            val (x, y) = pending.removeFirst()
            listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1).forEach { next ->
                if (next in cells && seen.add(next)) pending.addLast(next)
            }
        }
        assertEquals("$where is not one connected shape", cells.size, seen.size)
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
