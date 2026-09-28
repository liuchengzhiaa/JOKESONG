package com.example.asmrplayer.feature.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuminousLayoutTest {
    private val tuning = LuminousTuning()

    @Test
    fun `words that fit are laid out in one centred row`() {
        val flow = luminousWordFlow(
            wordWidths = listOf(40f, 60f, 40f),
            maxWidth = 300f,
            spacing = 10f,
            lineHeight = 40f,
        )

        assertEquals(3, flow.slots.size)
        // 40 + 10 + 60 + 10 + 40 = 160, centred in 300.
        assertEquals(70f, flow.slots[0].x, 0.01f)
        assertEquals(120f, flow.slots[1].x, 0.01f)
        assertEquals(190f, flow.slots[2].x, 0.01f)
        flow.slots.forEach { assertEquals(0f, it.y, 0f) }
        assertEquals(40f, flow.height, 0f)
        assertEquals(300f, flow.width, 0f)
    }

    @Test
    fun `a row that would overflow wraps instead`() {
        val flow = luminousWordFlow(
            wordWidths = listOf(100f, 100f, 100f),
            maxWidth = 210f,
            spacing = 10f,
            lineHeight = 30f,
        )

        // Two words per row at most: 100 + 10 + 100 = 210 fits exactly, so the third
        // word starts a second row.
        assertEquals(0f, flow.slots[0].y, 0f)
        assertEquals(0f, flow.slots[1].y, 0f)
        assertEquals(30f, flow.slots[2].y, 0f)
        assertEquals(60f, flow.height, 0f)
        assertEquals(0f, flow.slots[0].x, 0.01f)
        assertEquals(110f, flow.slots[1].x, 0.01f)
        assertEquals(55f, flow.slots[2].x, 0.01f)
    }

    @Test
    fun `a word wider than the block is centred on a row of its own`() {
        val flow = luminousWordFlow(
            wordWidths = listOf(400f, 50f),
            maxWidth = 200f,
            spacing = 10f,
            lineHeight = 25f,
        )

        // It overflows both edges equally rather than being pushed off one of them.
        assertEquals(-100f, flow.slots[0].x, 0.01f)
        // And the next word still gets a row of its own, centred on it.
        assertEquals(25f, flow.slots[1].y, 0f)
        assertEquals(75f, flow.slots[1].x, 0.01f)
    }

    @Test
    fun `no words is an empty block rather than a failed one`() {
        val flow = luminousWordFlow(emptyList(), maxWidth = 200f, spacing = 8f, lineHeight = 30f)

        assertTrue(flow.slots.isEmpty())
        assertEquals(0f, flow.width, 0f)
        assertEquals(0f, flow.height, 0f)
    }

    @Test
    fun `at zero motion a word is not thrown at all`() {
        val scatter = luminousWordScatter(index = 3, seed = 12_000L, motion = 0f, tuning = tuning)

        assertEquals(0f, scatter.dx, 0f)
        assertEquals(0f, scatter.dy, 0f)
        assertEquals(0f, scatter.rotationDegrees, 0f)
        // The size a word rests at is still a size -- zero motion is a still picture,
        // not a page of identical type.
        assertTrue(scatter.scale in 1.1f..1.3f)
    }

    @Test
    fun `at full motion the throw is the reference's own reach`() {
        val scatters = (0 until 40).map { luminousWordScatter(it, seed = 7_000L, motion = 1f, tuning = tuning) }

        scatters.forEach {
            assertTrue("dx ${it.dx}", it.dx in -60f..60f)
            assertTrue("dy ${it.dy}", it.dy in -60f..60f)
            assertTrue("rotation ${it.rotationDegrees}", it.rotationDegrees in -30f..30f)
            assertTrue("scale ${it.scale}", it.scale in 0.8f..1.4f)
        }
        // Well spread rather than politely identical: a cloud where every word lands
        // in the same place is not a cloud.
        assertTrue(scatters.map { it.dx }.distinct().size > 20)
    }

    @Test
    fun `the throw grows with motion`() {
        val calm = (0 until 20).map { luminousWordScatter(it, 9_000L, 0.25f, tuning) }
        val loud = (0 until 20).map { luminousWordScatter(it, 9_000L, 1f, tuning) }

        val calmReach = calm.maxOf { kotlin.math.abs(it.dx) + kotlin.math.abs(it.dy) }
        val loudReach = loud.maxOf { kotlin.math.abs(it.dx) + kotlin.math.abs(it.dy) }
        assertTrue("$calmReach should be under $loudReach", calmReach < loudReach)
    }

    /**
     * The property the whole projection exists for: a line is laid out from its own
     * start time, so coming back to it -- a repeat, a chorus, a seek -- reproduces
     * the picture instead of reshuffling it.
     */
    @Test
    fun `a line always lays itself out the same way`() {
        val first = (0 until 8).map { luminousWordScatter(it, seed = 61_000L, motion = 1f, tuning = tuning) }
        val again = (0 until 8).map { luminousWordScatter(it, seed = 61_000L, motion = 1f, tuning = tuning) }

        assertEquals(first, again)
    }

    @Test
    fun `two lines do not share a layout`() {
        val first = (0 until 8).map { luminousWordScatter(it, seed = 61_000L, motion = 1f, tuning = tuning) }
        val second = (0 until 8).map { luminousWordScatter(it, seed = 62_000L, motion = 1f, tuning = tuning) }

        assertNotEquals(first, second)
    }

    @Test
    fun `turning word rotation off leaves everything else in place`() {
        val turned = luminousWordScatter(2, 5_000L, 1f, tuning.copy(wordRotation = false))
        val tilted = luminousWordScatter(2, 5_000L, 1f, tuning)

        assertEquals(0f, turned.rotationDegrees, 0f)
        assertEquals(tilted.dx, turned.dx, 0f)
        assertEquals(tilted.dy, turned.dy, 0f)
        assertEquals(tilted.scale, turned.scale, 0f)
    }

    @Test
    fun `the breath follows the reference's path round its cycle`() {
        val amplitude = 20f

        assertEquals(0f, luminousBreathOffset(0f, amplitude), 0.01f)
        assertEquals(-amplitude, luminousBreathOffset(0.25f, amplitude), 0.01f)
        assertEquals(0f, luminousBreathOffset(0.5f, amplitude), 0.01f)
        assertEquals(amplitude * 0.45f, luminousBreathOffset(0.75f, amplitude), 0.01f)
        assertEquals(0f, luminousBreathOffset(1f, amplitude), 0.01f)
        // It wraps, so a caller can keep adding to the phase without a modulo.
        assertEquals(-amplitude, luminousBreathOffset(1.25f, amplitude), 0.01f)
        assertEquals(-amplitude, luminousBreathOffset(-0.75f, amplitude), 0.01f)
    }

    @Test
    fun `no amplitude is no breath at any phase`() {
        listOf(-0.3f, 0f, 0.25f, 0.5f, 0.75f, 3f).forEach { phase ->
            assertEquals("phase $phase", 0f, luminousBreathOffset(phase, 0f), 0f)
        }
    }
}
