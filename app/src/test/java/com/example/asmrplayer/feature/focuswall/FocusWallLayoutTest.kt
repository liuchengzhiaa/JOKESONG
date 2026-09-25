package com.example.asmrplayer.feature.focuswall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusWallLayoutTest {
    private val items = List(40) { index ->
        FocusWallLayoutItem(id = "item-$index", stableSeed = index * 7919)
    }

    @Test
    fun `layout is deterministic for identical input`() {
        val first = layout(FocusWallOrientation.Portrait, FocusWallDensity.Medium)
        val second = layout(FocusWallOrientation.Portrait, FocusWallDensity.Medium)

        assertEquals(first, second)
    }

    @Test
    fun `portrait layout is a balanced two dimensional mosaic with centered hero`() {
        val placements = layout(FocusWallOrientation.Portrait, FocusWallDensity.Medium)
        val firstRing = placements.filter { it.index <= 8 }
        val hero = firstRing.single { it.index == 0 }

        assertEquals(0f, hero.centerX)
        assertEquals(0f, hero.centerY)
        assertTrue(firstRing.any { it.centerX < 0f })
        assertTrue(firstRing.any { it.centerX > 0f })
        assertTrue(firstRing.any { it.centerY < 0f })
        assertTrue(firstRing.any { it.centerY > 0f })
    }

    @Test
    fun `landscape layout occupies both axes`() {
        val placements = layout(FocusWallOrientation.Landscape, FocusWallDensity.Medium)
        val hero = placements.single { it.index == 0 }

        assertTrue(placements.map { it.centerX }.distinct().size >= 5)
        assertTrue(placements.map { it.centerY }.distinct().size >= 2)
        assertEquals(0f, hero.centerX)
        assertEquals(0f, hero.centerY)
    }

    @Test
    fun `density changes geometry without changing item order`() {
        val small = layout(FocusWallOrientation.Portrait, FocusWallDensity.Small)
        val large = layout(FocusWallOrientation.Portrait, FocusWallDensity.Large)

        assertEquals(small.map { it.itemId }, large.map { it.itemId })
        assertTrue(large.first().width > small.first().width)
    }

    @Test
    fun `centre cover is larger than surrounding covers`() {
        val centre = focusWallDepthScale(0f, 0f, 1080f, 1800f)
        val nearby = focusWallDepthScale(0f, 540f, 1080f, 1800f)
        val edge = focusWallDepthScale(900f, 1400f, 1080f, 1800f)

        assertTrue(centre > nearby)
        assertTrue(nearby > edge)
    }

    @Test
    fun `visibility query stays bounded in a large scene`() {
        val largeItems = List(40_000) { index ->
            FocusWallLayoutItem("large-$index", index)
        }
        val placements = FocusWallLayoutEngine.layout(
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

        assertTrue(visible.size < 100)
        assertTrue(visible.isNotEmpty())
    }

    private fun layout(
        orientation: FocusWallOrientation,
        density: FocusWallDensity,
    ) = FocusWallLayoutEngine.layout(
        items = items,
        viewportWidth = 1080f,
        viewportHeight = 1800f,
        orientation = orientation,
        density = density,
    )
}
