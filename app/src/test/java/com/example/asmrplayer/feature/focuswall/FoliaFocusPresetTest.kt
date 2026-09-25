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
                            assertCover(
                                pieces = FoliaWallLayoutEngine
                                    .focusCoverCells(shape, size, heroX, heroY, variant),
                                hero = hero.piece.copy(x = heroX, y = heroY),
                                where = "$shape/$size at ($heroX, $heroY) variant $variant",
                            )
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
                    FoliaFocusShape.Square -> assertEquals(where, hero.columns, hero.rows)
                    // 完整封面 is left out on purpose: its proportions are the cover's,
                    // not the preset's. Without a cover to read they fall back to the
                    // square, which is checked on its own below.
                    FoliaFocusShape.FullCover -> Unit
                    FoliaFocusShape.Portrait -> assertTrue(where, hero.rows > hero.columns)
                    FoliaFocusShape.Landscape -> assertTrue(where, hero.columns > hero.rows)
                    FoliaFocusShape.Heart, FoliaFocusShape.Circle ->
                        assertTrue(where, hero.columns >= 4 && hero.rows >= 4)
                }
            }
        }
    }

    /**
     * 完整封面 draws the artwork inside a card cut to the artwork's own shape, so its hero
     * is not a preset at all: it is whichever rectangle the block can cover comes closest
     * to the cover's proportions. The card is then still letterboxed by whatever is left
     * over, which is why the search only has to come near rather than match.
     */
    @Test
    fun `the full-cover hero is the closest rectangle to the cover's shape`() {
        CoverAspects.forEach { aspect ->
            FoliaFocusSize.entries.forEach { size ->
                val hero = FoliaWallLayoutEngine
                    .heroCells(FoliaFocusShape.FullCover, size, aspect)
                    .piece
                val error = maxOf(hero.aspectRatio, aspect) / minOf(hero.aspectRatio, aspect)

                assertTrue(
                    "完整封面/$size put a $aspect cover in a ${hero.columns}x${hero.rows} card",
                    error <= MaxAspectError,
                )
                assertTrue(
                    "完整封面/$size chose a ${hero.columns}x${hero.rows} card the block cannot cover",
                    hero in FoliaHeroRectangles,
                )
            }
        }
    }

    /**
     * A square cover -- and a cover the wall has not read yet, which is the frame or two
     * between focusing a card and its artwork being decoded -- still gets exactly the
     * square presets, so the wall does not shift under a folder that was already using
     * 完整封面 before it could follow a cover's shape.
     */
    @Test
    fun `a square or unread cover keeps the square presets`() {
        FoliaFocusSize.entries.forEach { size ->
            val square = FoliaWallLayoutEngine.heroCells(FoliaFocusShape.Square, size).piece
            assertEquals(
                "完整封面/$size is not the square preset before the cover has been read",
                square,
                FoliaWallLayoutEngine.heroCells(FoliaFocusShape.FullCover, size, null).piece,
            )
            assertEquals(
                "完整封面/$size is not the square preset for a square cover",
                square,
                FoliaWallLayoutEngine.heroCells(FoliaFocusShape.FullCover, size, 1f).piece,
            )
        }
    }

    /**
     * The size preset still chooses the hero of a full cover: it narrows the search to
     * areas around the one its square preset would have taken, and the shape picks within
     * that. So the card grows with the size wherever the block holds more than one
     * rectangle of the cover's shape.
     */
    @Test
    fun `the size preset still chooses the full-cover hero`() {
        CoverAspects.filter { it != 1f / 2f }.forEach { aspect ->
            val areas = FoliaFocusSize.entries.map { size ->
                FoliaWallLayoutEngine.heroCells(FoliaFocusShape.FullCover, size, aspect).piece.area
            }

            assertEquals("完整封面/$aspect does not grow with the size", areas.sorted(), areas)
            assertNotEquals("完整封面/$aspect ignores the size preset", areas.first(), areas.last())
        }
    }

    /**
     * The other side of that trade: for a shape the block only holds small, the shape wins
     * over the size. A one-to-two cover has one rectangle within tolerance -- 3 x 6, and
     * the largest is 4 x 7 at 13 per cent off -- so all three sizes give the same hero
     * rather than a large card the cover would sit letterboxed inside. This is the
     * documented trade-off, and it is pinned here so it cannot change quietly.
     */
    @Test
    fun `a shape the block has no large rectangle for keeps the size of its shape`() {
        val heroes = FoliaFocusSize.entries.map { size ->
            FoliaWallLayoutEngine.heroCells(FoliaFocusShape.FullCover, size, 1f / 2f).piece
        }

        assertEquals("完整封面 no longer follows a one-to-two cover exactly", listOf(FoliaPiece(0, 0, 3, 6)), heroes.distinct())
    }

    /**
     * Every rectangle the full-cover rule can return has to be one the block can still
     * cover: the hero's shape and the area left for eleven neighbours are one decision,
     * and the rule chooses the shape without knowing that. The table is a measured edge
     * rather than a formula, so this re-derives it instead of trusting it.
     */
    @Test
    fun `every rectangle a full cover can choose covers its block exactly`() {
        FoliaHeroRectangles.forEach { hero ->
            val where = "完整封面 ${hero.columns}x${hero.rows}"
            assertTrue("$where is not a rectangle the block can hold", hero.area in 1..BlockCells)
            foliaHeroXRange(hero.columns, hero.rows).forEach { heroX ->
                foliaHeroYRange(hero.columns, hero.rows).forEach { heroY ->
                    (0..1).forEach { variant ->
                        val pieces = FoliaWallLayoutEngine
                            .coverCellsForHero(hero, heroX, heroY, variant)
                        assertCover(pieces, hero.copy(x = heroX, y = heroY), "$where at ($heroX, $heroY) v$variant")
                    }
                }
            }
        }
    }

    /** The table itself: five heights, each with the widths the block was measured to hold. */
    @Test
    fun `the full-cover rectangle table has one row per usable height`() {
        assertEquals(
            "the full-cover rectangle table changed shape",
            listOf(3, 4, 5, 6, 7),
            FoliaHeroRectangles.map { it.rows }.distinct(),
        )
        assertTrue(
            "a full-cover rectangle reaches the block's own height, which cannot be covered",
            FoliaHeroRectangles.none { it.rows >= BlockRows },
        )
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
     * Asserts that [pieces] is an exact cover of the block around [hero]: twelve slots, the
     * hero first and where it was asked for, every cell of the block painted exactly once,
     * nothing drawn outside it, and no neighbour small enough to read as a sliver. A piece
     * this small is also the one that paints no cell at all.
     */
    private fun assertCover(pieces: List<FoliaPiece>, hero: FoliaPiece, where: String) {
        assertEquals("$where did not produce twelve slots", 12, pieces.size)
        assertEquals("$where did not put the hero first", hero, pieces.first())

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
            // A shaped piece draws the cells it lists, so a cell it lists outside its own
            // box would be drawn off its own card. Rectangles have no cell list, so this
            // is only a statement about the shaped ones.
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

        pieces.drop(1).forEach { piece ->
            assertTrue(
                "$where made a ${piece.columns}x${piece.rows} sliver",
                piece.area >= MinNeighbourCells,
            )
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

        /**
         * A spread of cover shapes: wider than the block is, taller than it is, square,
         * and the near-square ratios an album cover actually comes in.
         */
        val CoverAspects = listOf(
            2f,
            16f / 9f,
            3f / 2f,
            4f / 3f,
            1f,
            3f / 4f,
            2f / 3f,
            0.625f,
            9f / 16f,
            1f / 2f,
        )

        /**
         * How far a full-cover hero may miss the cover's proportions, which is the
         * engine's own tolerance. Above it the search drops the size preset's area band
         * rather than keep a card of the wrong shape.
         */
        const val MaxAspectError = 1.10f
    }
}
