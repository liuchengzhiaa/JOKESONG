package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendoloGeometryTest {
    private val stageWidth = 1_080f
    private val stageHeight = 2_000f
    private val wheel = pendoloWheel(stageWidth, stageHeight, PendoloTuning())

    /** The even step at the default tuning: 100 degrees across nine visible lines. */
    private val evenStep = 12.5f

    @Test
    fun `the default wheel puts its hub on the left edge, halfway down`() {
        assertEquals(0f, wheel.centerX, 1e-3f)
        assertEquals(stageHeight / 2f, wheel.centerY, 1e-3f)
    }

    @Test
    fun `the lyric ring sits just outside the drawn gear`() {
        assertEquals(1_080f * 0.42f, wheel.baseRadius, 1e-3f)
        assertTrue(wheel.lyricRadius > wheel.baseRadius)
        assertEquals(1_080f * 0.06f, wheel.lyricRadius - wheel.baseRadius, 1e-3f)
    }

    @Test
    fun `the radius follows the stage's shorter side, so a landscape stage is not oversized`() {
        val landscape = pendoloWheel(width = 2_000f, height = 1_080f, tuning = PendoloTuning())

        assertEquals(wheel.baseRadius, landscape.baseRadius, 1e-3f)
        assertEquals(wheel.lyricRadius, landscape.lyricRadius, 1e-3f)
    }

    @Test
    fun `the arc angle is divided across the visible lines`() {
        val wide = pendoloWheel(stageWidth, stageHeight, PendoloTuning(arcAngleDegrees = 160))

        assertEquals(evenStep, wheel.angleStepDegrees, 1e-3f)
        assertEquals(20f, wide.angleStepDegrees, 1e-3f)
    }

    @Test
    fun `a line always gets room to be read, however far right the hub is pushed`() {
        val pinned = pendoloWheel(
            width = 400f,
            height = 800f,
            tuning = PendoloTuning(wheelCenterXPerMille = 400),
        )

        assertEquals(140f, pinned.availableTextWidth, 1e-3f)
    }

    @Test
    fun `the line being sung sits on the focal axis at the lyric radius`() {
        val items = pendoloLineItems(rail(lineCount = 15, anchor = 7f), wheel, PendoloTuning())

        val focal = items.single { it.isFocal }
        assertEquals(7, focal.offset)
        assertEquals(0f, focal.angleDegrees, 1e-3f)
        assertEquals(wheel.centerX + wheel.lyricRadius, focal.x, 1e-3f)
        assertEquals(wheel.centerY, focal.y, 1e-3f)
        assertEquals(0f, focal.distanceFromAnchor, 1e-3f)
    }

    @Test
    fun `unmeasured lines fall back to the even step, symmetric about the anchor`() {
        val items = pendoloLineItems(rail(lineCount = 15, anchor = 7f), wheel, PendoloTuning())
        val byOffset = items.associateBy { it.offset }

        for (step in 1..4) {
            assertEquals(step * evenStep, byOffset.getValue(7 + step).angleDegrees, 1e-3f)
            assertEquals(-step * evenStep, byOffset.getValue(7 - step).angleDegrees, 1e-3f)
        }
    }

    @Test
    fun `a taller pair of lines is given more arc than the even step`() {
        val heights = List(15) { 200f }

        val items = pendoloLineItems(rail(lineCount = 15, anchor = 7f), wheel, PendoloTuning(), heights)
        val neighbour = items.single { it.offset == 8 }

        // Two 200px blocks on a 518px ring need ~23 degrees, not the even 12.5.
        assertTrue(
            "neighbour angle was ${neighbour.angleDegrees}",
            neighbour.angleDegrees > evenStep,
        )
        assertEquals(23.43f, neighbour.angleDegrees, 0.05f)
    }

    @Test
    fun `a line is never given less arc than the even step, however short it is`() {
        val items = pendoloLineItems(
            window = rail(lineCount = 15, anchor = 7f),
            wheel = wheel,
            tuning = PendoloTuning(),
            lineBlockHeights = List(15) { 1f },
        )

        items.filter { it.offset != 7 }.forEach { item ->
            val steps = item.offset - 7
            assertTrue(
                "offset ${item.offset} was closer than the even step",
                kotlin.math.abs(item.angleDegrees) >= kotlin.math.abs(steps) * evenStep - 1e-3f,
            )
        }
    }

    @Test
    fun `alpha only fades as a line moves away from the anchor`() {
        val items = pendoloLineItems(rail(lineCount = 15, anchor = 7f), wheel, PendoloTuning())
            .sortedBy { it.distanceFromAnchor }

        assertEquals(1f, items.first().alpha, 1e-3f)
        items.zipWithNext().forEach { (near, far) ->
            assertTrue(
                "alpha rose from ${near.alpha} to ${far.alpha} at distance ${far.distanceFromAnchor}",
                far.alpha <= near.alpha + 1e-4f,
            )
        }
    }

    @Test
    fun `far lines are faint but still drawn`() {
        val items = pendoloLineItems(rail(lineCount = 15, anchor = 7f), wheel, PendoloTuning())
        val far = items.single { it.offset == 0 }

        assertEquals(0.12f, far.alpha, 1e-3f)
    }

    @Test
    fun `lines past the visible semicircle are dropped rather than wrapped`() {
        val wide = pendoloWheel(stageWidth, stageHeight, PendoloTuning(arcAngleDegrees = 160))

        val items = pendoloLineItems(rail(lineCount = 15, anchor = 7f), wide, PendoloTuning(arcAngleDegrees = 160))

        // Five steps of 20 degrees is 100, which is past the cull; four is 80, which is not.
        assertEquals((3..11).toList(), items.map { it.offset })
    }

    @Test
    fun `only the line being sung is scaled up`() {
        val items = pendoloLineItems(rail(lineCount = 15, anchor = 7f), wheel, PendoloTuning())

        val enlarged = items.filter { it.scale > 1f }
        assertEquals(1, enlarged.size)
        assertTrue(enlarged.single().isFocal)
        assertEquals(1.25f, enlarged.single().scale, 1e-3f)
    }

    @Test
    fun `neighbours shrink with distance but never below the floor`() {
        val items = pendoloLineItems(rail(lineCount = 15, anchor = 7f), wheel, PendoloTuning())
            .filter { !it.isFocal }
            .sortedBy { it.distanceFromAnchor }

        items.zipWithNext().forEach { (near, far) ->
            assertTrue(far.scale <= near.scale + 1e-4f)
        }
        assertTrue(items.all { it.scale >= 0.7f })
    }

    @Test
    fun `before the first line nothing is sung, though the wheel is already in position`() {
        val items = pendoloLineItems(
            window = rail(lineCount = 8, anchor = 0f, hasAnchor = false),
            wheel = wheel,
            tuning = PendoloTuning(),
        )

        assertTrue(items.none { it.isFocal })
        // Line zero still sits on the axis, so the wheel does not jump when singing starts.
        assertEquals(0f, items.single { it.offset == 0 }.angleDegrees, 1e-3f)
    }

    @Test
    fun `the wheel is level whenever the spring has settled on the anchor`() {
        val settled = pendoloWheelRotationDegrees(springIndex = 42f, anchorIndex = 42f, wheel = wheel)

        assertEquals(0f, settled, 1e-3f)
    }

    @Test
    fun `a tick in flight swings the rail one step per line`() {
        val midTick = pendoloWheelRotationDegrees(springIndex = 7.5f, anchorIndex = 7f, wheel = wheel)

        // Half a step past the anchor is half a step of rail, and against the wheel's turn.
        assertEquals(-0.5f * evenStep, midTick, 1e-3f)
    }

    @Test
    fun `the gear keeps its place in the song after a seek`() {
        val level = pendoloWheelRotationDegrees(springIndex = 42f, anchorIndex = 42f, wheel = wheel)
        val gear = pendoloGearRotationRadians(springIndex = 42f, wheel = wheel)

        assertEquals(0f, level, 1e-3f)
        // A ratchet does not rewind: seeking to line 42 leaves the clockwork where line 42
        // put it, not back at its start.
        assertEquals(42f * Math.toRadians(evenStep.toDouble()).toFloat(), gear, 1e-3f)
        assertTrue(gear > 0f)
    }

    @Test
    fun `a zero-sized stage divides by nothing`() {
        val degenerate = pendoloWheel(width = 0f, height = 0f, tuning = PendoloTuning())
        val items = pendoloLineItems(rail(lineCount = 3, anchor = 1f), degenerate, PendoloTuning())

        assertEquals(0f, degenerate.lyricRadius, 0f)
        assertEquals(3, items.size)
        assertFalse(items.any { it.x.isNaN() || it.y.isNaN() })
    }

    @Test
    fun `an empty window produces nothing to draw`() {
        val items = pendoloLineItems(
            window = LyricRailWindow(emptyList(), startIndex = 0, anchor = 0f, hasAnchor = false),
            wheel = wheel,
            tuning = PendoloTuning(),
        )

        assertTrue(items.isEmpty())
    }

    private fun rail(lineCount: Int, anchor: Float, hasAnchor: Boolean = true) = LyricRailWindow(
        lines = (0 until lineCount).map { index ->
            TimedTextLine(startMillis = index * 1_000L, endMillis = null, text = "line $index")
        },
        startIndex = 0,
        anchor = anchor,
        hasAnchor = hasAnchor,
    )
}
