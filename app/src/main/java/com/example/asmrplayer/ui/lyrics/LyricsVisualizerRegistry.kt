package com.example.asmrplayer.ui.lyrics

import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.feature.subtitles.ImmersiveLyricsFrame
import com.example.asmrplayer.feature.subtitles.LyricRailWindow
import com.example.asmrplayer.feature.subtitles.LyricsVisualizerMode
import com.example.asmrplayer.ui.lyrics.visualizers.CladdaghVisualizer
import com.example.asmrplayer.ui.lyrics.visualizers.LuminousVisualizer
import com.example.asmrplayer.ui.lyrics.visualizers.PendoloVisualizer
import com.example.asmrplayer.ui.lyrics.visualizers.TemperaVisualizer

/**
 * One full-screen lyric presentation.
 *
 * The two members are different in kind on purpose. [transition] is pure and owns
 * only how a line change reads, so it can be reasoned about (and varied) without a
 * composition; [Content] owns the lines themselves and nothing else. A visualizer
 * never sees the player, the document or the backdrop.
 */
internal interface LyricsVisualizer {
    /**
     * How the change to another line reads. [durationMillis] already carries the
     * global motion setting: the stage never calls this with zero.
     */
    fun transition(durationMillis: Int, direction: Int): ContentTransform

    @Composable
    fun Content(inputs: LyricsSceneInputs)
}

/**
 * A presentation that arranges a whole span of lines at once and moves them as one
 * object, rather than replacing a single line on every boundary.
 *
 * This is a second *shape* rather than a flag, because the difference is not a
 * setting: the stage wraps an ordinary visualizer in a per-line transition, and
 * doing that to a rail would rebuild the entire wheel on every line change. A mode
 * declares which it is by which of the two it implements, and the stage cannot get
 * it wrong without failing to compile.
 *
 * A rail is given the full stage rather than the inset box the single-line modes
 * draw in, because its geometry is a function of the whole screen -- its anchor sits
 * on an edge of the viewport and the arc is measured against the shorter side.
 */
internal interface WindowedLyricsVisualizer : LyricsVisualizer {
    @Composable
    fun Window(rail: LyricRailWindow, inputs: LyricsSceneInputs)

    /**
     * A rail is never shown through [Content] -- the stage routes a windowed mode to
     * [Window] before it can reach here. It answers anyway, with the five-line frame
     * the stage has already resolved, because a mode that silently drew nothing when
     * it was called the wrong way would be a bug nobody could see.
     */
    @Composable
    override fun Content(inputs: LyricsSceneInputs) {
        Window(rail = inputs.frame.asRailWindow(), inputs = inputs)
    }
}

/**
 * A visualizer backed by a persistent, absolute-time scene program.
 *
 * Folia-style PV modes cannot be mounted inside the ordinary per-line
 * [androidx.compose.animation.AnimatedContent]: one source line may compile to
 * several overlapping shots and a paragraph transition can start before the next
 * lyric does. This shape gives such a mode the parsed document while keeping
 * parsing, playback and seeking in the host.
 */
internal interface ProgramLyricsVisualizer : LyricsVisualizer {
    @Composable
    fun Program(document: TimedTextDocument, inputs: LyricsSceneInputs)

    @Composable
    override fun Content(inputs: LyricsSceneInputs) {
        // Program modes are routed before this fallback can be reached. Keeping a
        // visible fallback is safer than silently returning an empty scene.
        Program(
            document = TimedTextDocument(inputs.format, listOf(inputs.frame.current)),
            inputs = inputs,
        )
    }
}

/**
 * The five-line window the stage resolved, read as a rail.
 *
 * The anchor is where the line being sung sits among the lines present, and the
 * window's start is worked back from it, so a rail built this way still knows its
 * position in the song and its clockwork does not rewind.
 */
internal fun ImmersiveLyricsFrame.asRailWindow(): LyricRailWindow {
    val before = listOfNotNull(previousFar, previous)
    val after = listOfNotNull(next, nextFar)
    return LyricRailWindow(
        lines = before + current + after,
        startIndex = (currentIndex - before.size).coerceAtLeast(0),
        anchor = before.size.toFloat(),
        hasAnchor = true,
    )
}

/**
 * The registry itself. The `when` is exhaustive and has no `else`: a new mode does
 * not compile until it has a visualizer and a transition, which is what makes the
 * set replaceable rather than merely long.
 */
internal fun LyricsVisualizerMode.visualizer(): LyricsVisualizer = when (this) {
    LyricsVisualizerMode.Luminous -> LuminousVisualizer
    LyricsVisualizerMode.Pendolo -> PendoloVisualizer
    LyricsVisualizerMode.Claddagh -> CladdaghVisualizer
    LyricsVisualizerMode.Tempera -> TemperaVisualizer
}
