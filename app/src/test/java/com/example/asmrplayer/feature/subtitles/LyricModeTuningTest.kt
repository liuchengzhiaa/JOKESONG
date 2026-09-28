package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.settings.levelFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** One continuous parameter: how to read it, how to set it, and where it is legal. */
private class Knob(
    val name: String,
    val range: IntRange,
    val read: (PendoloTuning) -> Int,
    val write: (PendoloTuning, Int) -> PendoloTuning,
)

class LyricModeTuningTest {
    private val knobs = listOf(
        Knob("wheelCenterXPerMille", PendoloWheelCenterXRange, PendoloTuning::wheelCenterXPerMille) { t, v ->
            t.copy(wheelCenterXPerMille = v)
        },
        Knob("wheelCenterYPerMille", PendoloWheelCenterYRange, PendoloTuning::wheelCenterYPerMille) { t, v ->
            t.copy(wheelCenterYPerMille = v)
        },
        Knob("arcRadiusPercent", PendoloArcRadiusRange, PendoloTuning::arcRadiusPercent) { t, v ->
            t.copy(arcRadiusPercent = v)
        },
        Knob("arcAngleDegrees", PendoloArcAngleRange, PendoloTuning::arcAngleDegrees) { t, v ->
            t.copy(arcAngleDegrees = v)
        },
        Knob("tickSnappinessPerTen", PendoloTickSnappinessRange, PendoloTuning::tickSnappinessPerTen) { t, v ->
            t.copy(tickSnappinessPerTen = v)
        },
        Knob("activeScalePercent", PendoloActiveScaleRange, PendoloTuning::activeScalePercent) { t, v ->
            t.copy(activeScalePercent = v)
        },
    )

    /**
     * A default that sits outside its own range is a bug nobody sees until they open
     * the panel: the slider would show a value it cannot produce.
     */
    @Test
    fun `every default is inside the range it is stored in`() {
        knobs.forEach { knob ->
            assertTrue(
                "${knob.name} default ${knob.read(PendoloTuning())} is outside ${knob.range}",
                knob.read(PendoloTuning()) in knob.range,
            )
        }
        assertTrue(LuminousTuning().breathingFloatPercent in LuminousBreathingFloatRange)
        assertTrue(LuminousTuning().wordSpacingPercent in LuminousWordSpacingRange)
    }

    @Test
    fun `every range is wide enough to slide across`() {
        knobs.forEach { knob ->
            assertTrue("${knob.name} range is degenerate", knob.range.last > knob.range.first)
        }
    }

    @Test
    fun `a value below its range clamps to the left end`() {
        knobs.forEach { knob ->
            val out = knob.write(PendoloTuning(), knob.range.first - 1_000).clamped()

            assertEquals("${knob.name} did not clamp low", knob.range.first, knob.read(out))
        }
    }

    @Test
    fun `a value above its range clamps to the right end`() {
        knobs.forEach { knob ->
            val out = knob.write(PendoloTuning(), knob.range.last + 1_000).clamped()

            assertEquals("${knob.name} did not clamp high", knob.range.last, knob.read(out))
        }
    }

    @Test
    fun `clamping leaves a legal tuning exactly as it was`() {
        val tuned = PendoloTuning(
            wheelCenterXPerMille = 180,
            wheelCenterYPerMille = 640,
            arcRadiusPercent = 55,
            arcAngleDegrees = 120,
            tickSnappinessPerTen = 35,
            activeScalePercent = 140,
            gearDecor = PendoloGearDecor.Full,
            centreGradient = false,
            coverOnDial = true,
            lineGlow = true,
        )

        assertEquals(tuned, tuned.clamped())
    }

    /**
     * The knob's own position is what the slider and the number beside it both read,
     * so it has to be a pure function of the value and the range.
     */
    @Test
    fun `the ends of every range sit at the ends of the control`() {
        knobs.forEach { knob ->
            assertEquals("${knob.name} left end", 0f, levelFraction(knob.range.first, knob.range), 0f)
            assertEquals("${knob.name} right end", 1f, levelFraction(knob.range.last, knob.range), 0f)
        }
    }

    @Test
    fun `derived fractions are the stored integers over their scale`() {
        val tuning = PendoloTuning(
            wheelCenterXPerMille = 250,
            wheelCenterYPerMille = 650,
            arcRadiusPercent = 55,
            tickSnappinessPerTen = 25,
            activeScalePercent = 140,
        )

        assertEquals(0.25f, tuning.wheelCenterXFraction, 1e-4f)
        assertEquals(0.65f, tuning.wheelCenterYFraction, 1e-4f)
        assertEquals(0.55f, tuning.arcRadiusFraction, 1e-4f)
        assertEquals(2.5f, tuning.tickSnappiness, 1e-4f)
        assertEquals(1.4f, tuning.activeScale, 1e-4f)
    }

    /**
     * The defaults are the reference's own, so a first run looks designed rather than
     * like a page of zeroes. Changing one is a deliberate act, not an accident.
     */
    @Test
    fun `defaults match the reference`() {
        val pendolo = PendoloTuning()

        assertEquals(0, pendolo.wheelCenterXPerMille)
        assertEquals(500, pendolo.wheelCenterYPerMille)
        assertEquals(42, pendolo.arcRadiusPercent)
        assertEquals(100, pendolo.arcAngleDegrees)
        assertEquals(20, pendolo.tickSnappinessPerTen)
        assertEquals(125, pendolo.activeScalePercent)
        assertSame(PendoloGearDecor.Subtle, pendolo.gearDecor)
        assertTrue(pendolo.centreGradient)
        // The dial and the glow are the two things the reference leaves off by default.
        assertFalse(pendolo.coverOnDial)
        assertFalse(pendolo.lineGlow)

        val luminous = LuminousTuning()
        assertTrue(luminous.wordRotation)
        assertEquals(100, luminous.breathingFloatPercent)
        assertEquals(70, luminous.wordSpacingPercent)
    }

    @Test
    fun `an out-of-range gear decor degrades to the reference default`() {
        assertSame(PendoloGearDecor.None, PendoloGearDecor.fromOrdinal(0))
        assertSame(PendoloGearDecor.Subtle, PendoloGearDecor.fromOrdinal(1))
        assertSame(PendoloGearDecor.Full, PendoloGearDecor.fromOrdinal(2))
        assertSame(PendoloGearDecor.Default, PendoloGearDecor.fromOrdinal(-1))
        assertSame(PendoloGearDecor.Default, PendoloGearDecor.fromOrdinal(Int.MAX_VALUE))
    }

    /**
     * The header draws its settings button where this is true, so the predicate and
     * the tunings above have to agree about which modes those are. The count is pinned
     * too: a new mode has to come here and say whether it has parameters, rather
     * than quietly inheriting a button that opens nothing.
     */
    @Test
    fun `the three modes with real parameters advertise them`() {
        assertEquals(4, LyricsVisualizerMode.entries.size)
        assertEquals(
            listOf(
                LyricsVisualizerMode.Luminous,
                LyricsVisualizerMode.Pendolo,
                LyricsVisualizerMode.Tempera,
            ),
            LyricsVisualizerMode.entries.filter { it.hasTunings },
        )
    }

    @Test
    fun `the luminous tuning clamps its own pair`() {
        val high = LuminousTuning(breathingFloatPercent = 9_999, wordSpacingPercent = 9_999).clamped()
        val low = LuminousTuning(breathingFloatPercent = -9_999, wordSpacingPercent = -9_999).clamped()

        assertEquals(LuminousBreathingFloatRange.last, high.breathingFloatPercent)
        assertEquals(LuminousWordSpacingRange.last, high.wordSpacingPercent)
        assertEquals(LuminousBreathingFloatRange.first, low.breathingFloatPercent)
        assertEquals(LuminousWordSpacingRange.first, low.wordSpacingPercent)
        assertEquals(LuminousTuning(), LuminousTuning().clamped())
    }

    @Test
    fun `a stored value that predates a range change still produces a usable wheel`() {
        val ancient = PendoloTuning(arcRadiusPercent = 3, arcAngleDegrees = 400).clamped()
        val wheel = pendoloWheel(width = 1_080f, height = 2_000f, tuning = ancient)

        assertTrue(wheel.baseRadius > 0f)
        assertTrue(wheel.lyricRadius > wheel.baseRadius)
        assertTrue(wheel.angleStepDegrees > 0f)
        assertTrue(wheel.availableTextWidth > 0f)
    }

    @Test
    fun `tempera image library is bounded deterministic and ignores unusable entries`() {
        val raw = buildList {
            add("")
            add("content://z")
            add("content://a")
            add("content://z")
            repeat(MaxTemperaLibraryImages + 10) { add("content://image/$it") }
        }

        val safe = TemperaTuning(imageUris = raw).clamped()

        assertEquals(MaxTemperaLibraryImages, safe.imageUris.size)
        assertEquals(safe.imageUris.sorted(), safe.imageUris)
        assertEquals(safe.imageUris.distinct(), safe.imageUris)
        assertTrue(safe.imageUris.none(String::isBlank))
    }

    @Test
    fun `tempera defaults to a four by three cover stage`() {
        val defaults = TemperaTuning()

        assertSame(TemperaArtworkSource.Cover, defaults.artworkSource)
        assertSame(TemperaStageAspect.LandscapeFourThree, defaults.stageAspect)
        assertEquals(4f / 3f, defaults.stageAspect.widthOverHeight!!, 0f)
        assertEquals(100, defaults.cameraIntensityPercent)
        assertEquals(100, defaults.glyphMotionPercent)
        assertEquals(50, defaults.glyphSettleStretchPercent)
        assertSame(TemperaColorMode.Duo, defaults.colorMode)
        assertTrue(defaults.showBlocks)
        assertTrue(defaults.showDecor)
        assertTrue(defaults.enableTransitions)
    }

    @Test
    fun `tempera continuous controls are clamped to their documented ranges`() {
        val safe = TemperaTuning(
            cameraIntensityPercent = 999,
            glyphMotionPercent = -10,
            glyphSettleStretchPercent = 140,
            imageFrequencyPercent = -1,
        ).clamped()

        assertEquals(200, safe.cameraIntensityPercent)
        assertEquals(0, safe.glyphMotionPercent)
        assertEquals(100, safe.glyphSettleStretchPercent)
        assertEquals(0, safe.imageFrequencyPercent)
    }
}
