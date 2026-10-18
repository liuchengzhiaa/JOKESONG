package com.example.asmrplayer.feature.focuswall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusWallSpatialIndexTest {
    private val grid = List(40_000) { i ->
        FocusWallPlacement("$i", i, (i % 200 - 100) * 100f, (i / 200 - 100) * 100f, 90f, 90f, i / 12)
    }

    @Test fun `large scene query examines only local buckets and preserves order`() {
        val result = FocusWallSpatialIndex(grid).query(-250f, 250f, -250f, 250f)
        assertEquals(grid.filter { it.centerX in -250f..250f && it.centerY in -250f..250f }, result.placements)
        assertTrue("visited ${result.candidatesExamined}", result.candidatesExamined < 80)
    }

    @Test fun `boundaries negative coordinates and mixed widths match brute force`() {
        val placements = grid.take(550).mapIndexed { index, p -> p.copy(width = 10f + index % 300) }
        val index = FocusWallSpatialIndex(placements)
        for (x in -10000..10000 step 777) {
            val result = index.query(x.toFloat(), x + 700f, -10000f, -9800f)
            assertEquals(placements.filter { it.centerX in x.toFloat()..x + 700f && it.centerY in -10000f..-9800f }, result.placements)
        }
    }

    @Test fun `huge sparse query is bounded and returns every item in original order`() {
        assertEquals(grid, FocusWallSpatialIndex(grid).query(-Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE).placements)
    }

    @Test fun `empty invalid and inverted rectangles have no candidates`() {
        assertTrue(FocusWallSpatialIndex(emptyList()).query(0f, 1f, 0f, 1f).placements.isEmpty())
        val index = FocusWallSpatialIndex(grid)
        assertTrue(index.query(Float.NaN, 1f, 0f, 1f).placements.isEmpty())
        assertTrue(index.query(2f, 1f, 0f, 1f).placements.isEmpty())
    }

    @Test fun `asynchronous scene uses exactly the established geometry engines`() {
        val items = List(500) { FocusWallLayoutItem("$it", it) }
        for (style in FocusWallStyle.entries) {
            val scene = FocusWallScene(FocusWallSceneRequest(
                "folder", items, 1080f, 1800f, FocusWallOrientation.Portrait, 100, "25", 2f,
                style, FoliaFocusShape.Square, FoliaFocusSize.Medium, null,
            ))
            val expected = when (style) {
                FocusWallStyle.Bento -> FocusWallLayoutEngine.layout(items, 1080f, 1800f, FocusWallOrientation.Portrait, FocusWallDensity.Medium, 1f, "25", 2f)
                FocusWallStyle.Folia -> FoliaWallLayoutEngine.layout(items, 1080f, 1800f, FocusWallOrientation.Portrait, FocusWallDensity.Medium, 1f, "25", 2f)
            }
            assertEquals(expected, scene.placements)
            assertEquals(expected.associateBy { it.itemId }, scene.placementById)
            assertEquals(wallWorldBounds(expected), scene.bounds)
        }
    }

    @Test fun `forty thousand real Folia placements keep a local query bounded`() {
        val scene = FocusWallScene(FocusWallSceneRequest(
            "large", List(40_000) { FocusWallLayoutItem("$it", it) },
            1080f, 1800f, FocusWallOrientation.Portrait, 100, "20000", 2f,
            FocusWallStyle.Folia, FoliaFocusShape.Square, FoliaFocusSize.Medium, null,
        ))
        val query = scene.spatialIndex.query(-1000f, 1000f, -1500f, 1500f)
        assertEquals(40_000, scene.placements.size)
        assertEquals(scene.placements.filter { it.centerX in -1000f..1000f && it.centerY in -1500f..1500f }, query.placements)
        assertTrue("visited ${query.candidatesExamined}", query.candidatesExamined < 1_000)
    }
}
