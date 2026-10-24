package com.example.asmrplayer.feature.focuswall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusWallLayoutTest {
    private val items = List(40) { index ->
        FocusWallLayoutItem(id = "item-$index", stableSeed = index * 7919)
    }

    @Test
    fun `centre proximity remains available for subtle depth treatment`() {
        val centre = focusWallDepthScale(0f, 0f, 1080f, 1800f)
        val nearby = focusWallDepthScale(0f, 540f, 1080f, 1800f)
        val edge = focusWallDepthScale(900f, 1400f, 1080f, 1800f)

        assertTrue(centre > nearby)
        assertTrue(nearby > edge)
    }

    @Test
    fun `world bounds enclose every placement and honour mirroring`() {
        val placements = layout(FocusWallOrientation.Portrait, FocusWallDensity.Medium)
        val bounds = requireNotNull(wallWorldBounds(placements))

        placements.forEach { placement ->
            assertTrue(placement.itemId, placement.centerX - placement.width / 2f >= bounds.minX - 0.01f)
            assertTrue(placement.itemId, placement.centerX + placement.width / 2f <= bounds.maxX + 0.01f)
            assertTrue(placement.itemId, placement.centerY - placement.height / 2f >= bounds.minY - 0.01f)
            assertTrue(placement.itemId, placement.centerY + placement.height / 2f <= bounds.maxY + 0.01f)
        }
        // The mirrored template variants must fill all four quadrants.
        assertTrue(bounds.minX < 0f && bounds.maxX > 0f)
        assertTrue(bounds.minY < 0f && bounds.maxY > 0f)
    }

    @Test
    fun `world bounds are absent when there is nothing to bound`() {
        assertEquals(null, wallWorldBounds(emptyList()))
    }

    @Test
    fun `pan clamp is a no-op without bounds`() {
        assertEquals(
            FocusWallPan(120f, -80f),
            clampWallPan(120f, -80f, zoom = 1f, bounds = null),
        )
    }

    @Test
    fun `pan clamp stops the viewport centre from leaving the wall`() {
        val bounds = FocusWallWorldBounds(minX = -100f, maxX = 100f, minY = -200f, maxY = 200f)

        // Inside the wall, free movement is preserved -- this is a rail, not a snap.
        assertEquals(FocusWallPan(50f, -30f), clampWallPan(50f, -30f, zoom = 1f, bounds = bounds))
        // Past the edge, it stops exactly at the wall's own extent.
        assertEquals(FocusWallPan(100f, 200f), clampWallPan(9_000f, 9_000f, zoom = 1f, bounds = bounds))
        assertEquals(FocusWallPan(-100f, -200f), clampWallPan(-9_000f, -9_000f, zoom = 1f, bounds = bounds))
    }

    @Test
    fun `pan clamp follows canvas zoom`() {
        val bounds = FocusWallWorldBounds(minX = -100f, maxX = 100f, minY = -100f, maxY = 100f)

        assertEquals(FocusWallPan(100f, 0f), clampWallPan(9_000f, 0f, zoom = 1f, bounds = bounds))
        assertEquals(FocusWallPan(200f, 0f), clampWallPan(9_000f, 0f, zoom = 2f, bounds = bounds))
        assertEquals(FocusWallPan(50f, 0f), clampWallPan(9_000f, 0f, zoom = 0.5f, bounds = bounds))
    }

    @Test
    fun `pan clamp survives a degenerate zoom`() {
        val bounds = FocusWallWorldBounds(minX = -100f, maxX = 100f, minY = -100f, maxY = 100f)

        val clamped = clampWallPan(9_000f, 9_000f, zoom = 0f, bounds = bounds)

        assertTrue(clamped.x.isFinite() && clamped.y.isFinite())
    }

    @Test
    fun `visibility query stays bounded in a large scene`() {
        val largeItems = List(40_000) { index ->
            FocusWallLayoutItem("large-$index", index)
        }
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

        assertTrue(visible.size < 200)
        assertTrue(visible.isNotEmpty())
    }

    private fun layout(
        orientation: FocusWallOrientation,
        density: FocusWallDensity,
        focusedItemId: String? = null,
        densityScale: Float = density.scale,
    ) = FoliaWallLayoutEngine.layout(
        items = items,
        viewportWidth = 1080f,
        viewportHeight = 1800f,
        orientation = orientation,
        density = density,
        densityScale = densityScale,
        focusedItemId = focusedItemId,
    )
}
