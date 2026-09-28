package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperaProgramTest {
    @Test
    fun `the same track line always compiles the same shot`() {
        val line = TimedTextLine(1_000L, 4_000L, "编织那没有诗意的神文之诗")

        val first = shot(line, lineIndex = 7, seed = 42)
        val second = shot(line, lineIndex = 7, seed = 42)

        assertEquals(first, second)
    }

    @Test
    fun `different lines produce a useful spread of authored families`() {
        val families = (0 until 64).map { index ->
            shot(
                TimedTextLine(index * 1_000L, index * 1_000L + 900L, "line $index colour"),
                lineIndex = index,
                seed = 91,
            ).family
        }.toSet()

        assertTrue(families.size >= 6)
    }

    @Test
    fun `expressive slices are bounded even for a very long subtitle`() {
        val line = TimedTextLine(0L, 12_000L, "这是一条非常非常长而且不应该创建无界面组合树的字幕")

        val compiled = shot(line, lineIndex = 0, seed = 8)

        assertTrue(compiled.slices.size <= 8)
        assertTrue(compiled.slices.all { it.text.isNotBlank() })
    }

    @Test
    fun `shot timing is absolute and clamped to its lyric line`() {
        val compiled = shot(
            TimedTextLine(2_000L, null, "one two three four"),
            lineIndex = 2,
            seed = 3,
            nextLineStart = 5_500L,
        )

        assertEquals(2_000L, compiled.startMillis)
        assertEquals(5_500L, compiled.endMillis)
        assertTrue(compiled.slices.all { it.startMillis >= compiled.startMillis })
        assertTrue(compiled.slices.all { it.settleMillis in it.startMillis..compiled.endMillis })
    }

    @Test
    fun `slice pose is seek stable and respects zero motion`() {
        val slice = shot(
            TimedTextLine(1_000L, 4_000L, "alpha beta"),
            lineIndex = 1,
            seed = 5,
        ).slices.first()

        val before = temperaSlicePose(slice, slice.startMillis - 1L, motionPercent = 100)
        val settled = temperaSlicePose(slice, slice.settleMillis, motionPercent = 100)
        val repeated = temperaSlicePose(slice, slice.settleMillis, motionPercent = 100)
        val still = temperaSlicePose(slice, slice.startMillis - 1L, motionPercent = 0)

        assertEquals(0f, before.alpha, Tolerance)
        assertEquals(settled, repeated)
        assertEquals(1f, settled.alpha, Tolerance)
        assertTrue(settled.scale in 0.95f..1.06f)
        assertEquals(1f, still.alpha, Tolerance)
        assertEquals(0f, still.translationXFraction, Tolerance)
        assertEquals(0f, still.translationYFraction, Tolerance)
    }

    @Test
    fun `track seed participates in the program identity`() {
        val line = TimedTextLine(0L, 2_000L, "same lyric")

        assertNotEquals(shot(line, 0, 1).id, shot(line, 0, 2).id)
    }

    @Test
    fun `tempera exposes one hundred and twenty one deterministic shot profiles`() {
        assertEquals(121, temperaShotKindCount())
    }

    @Test
    fun `a source line becomes several short shots by default`() {
        val line = TimedTextLine(
            0L,
            8_000L,
            "one two three four five six seven eight nine ten",
        )

        val program = compileTemperaProgramWindow(
            lines = listOf(line),
            format = TimedTextFormat.Lrc,
            centreLineIndex = 0,
            trackSeed = 41,
        )

        assertTrue(program.shots.size >= 3)
        assertTrue(program.shots.all { it.slices.size in 1..4 })
        assertTrue(program.shots.zipWithNext().all { (first, second) -> first.endMillis == second.startMillis })
    }

    @Test
    fun `program compilation keeps only a bounded line neighbourhood`() {
        val lines = List(100) { index ->
            TimedTextLine(index * 1_000L, index * 1_000L + 800L, "line $index has words")
        }

        val program = compileTemperaProgramWindow(lines, TimedTextFormat.Lrc, 50, 7)

        assertTrue(program.sourceRange.count() <= 29)
        assertTrue(program.shots.all { it.lineIndex in program.sourceRange })
    }

    @Test
    fun `shot handoff overlaps outgoing and incoming compositions`() {
        val program = compileTemperaProgramWindow(
            listOf(TimedTextLine(0L, 8_000L, "one two three four five six seven eight")),
            TimedTextFormat.Lrc,
            0,
            52,
        )
        val second = program.shots[1]

        val layers = temperaShotLayers(program, second.startMillis + 100L, 100)

        assertEquals(2, layers.size)
        assertEquals(program.shots[0].id, layers[0].shot.id)
        assertEquals(second.id, layers[1].shot.id)
        assertTrue(layers[0].alpha > 0f)
        assertTrue(layers[1].alpha > 0f)
    }

    @Test
    fun `instrumental gaps compile lyric free bridge shots`() {
        val program = compileTemperaProgramWindow(
            lines = listOf(
                TimedTextLine(0L, 1_000L, "first phrase"),
                TimedTextLine(8_000L, 9_000L, "second phrase"),
            ),
            format = TimedTextFormat.Srt,
            centreLineIndex = 0,
            trackSeed = 6,
        )

        assertTrue(program.shots.any { it.bridge && it.slices.isEmpty() })
    }

    @Test
    fun `paragraph transition pre rolls the incoming scene`() {
        val program = compileTemperaProgramWindow(
            lines = listOf(
                TimedTextLine(0L, null, "first phrase"),
                TimedTextLine(12_000L, null, "second phrase"),
            ),
            format = TimedTextFormat.Lrc,
            centreLineIndex = 0,
            trackSeed = 17,
        )
        val outgoing = program.paragraphs.first()
        val sample = (outgoing.transitionStartMillis + outgoing.transitionEndMillis) / 2L

        val layers = temperaShotLayers(program, sample, 100, transitionsEnabled = true)

        assertEquals(2, layers.size)
        assertNotEquals(layers[0].shot.paragraphIndex, layers[1].shot.paragraphIndex)
    }

    private fun shot(
        line: TimedTextLine,
        lineIndex: Int,
        seed: Int,
        nextLineStart: Long? = line.endMillis,
    ): TemperaShot = compileTemperaShot(
        line = line,
        nextLineStartMillis = nextLineStart,
        format = TimedTextFormat.Lrc,
        lineIndex = lineIndex,
        trackSeed = seed,
    )

    private companion object {
        const val Tolerance = 0.0001f
    }
}
