package com.example.asmrplayer.ui.lyrics

import androidx.compose.ui.graphics.Color
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.feature.subtitles.ImmersiveLyricsFrame
import com.example.asmrplayer.feature.subtitles.LuminousTuning
import com.example.asmrplayer.feature.subtitles.LyricsWordGlowMode
import com.example.asmrplayer.feature.subtitles.PendoloTuning
import com.example.asmrplayer.feature.subtitles.TemperaTuning

/**
 * Everything a [LyricsVisualizer] may draw from, resolved once by the host.
 *
 * A visualizer owns only how the lyric looks; the clock, the parsed document, the
 * palette and the transport all stay with the host, so no mode can drift into
 * owning playback or parsing.
 */
internal data class LyricsSceneInputs(
    val item: LibraryItem,
    val frame: ImmersiveLyricsFrame,
    val format: TimedTextFormat,
    val positionMs: Long,
    val isPlaying: Boolean,
    val accent: Color,
    val palette: List<Color>,
    /** True when the stage is too short for the five-line window. */
    val compact: Boolean,
    val wordGlowMode: LyricsWordGlowMode,
    val motionPercent: Int,
    val onSeekTo: (Long) -> Unit,
    /**
     * A mode's own parameters. Only the mode they belong to reads them, and each
     * carries the reference's own defaults, so a mode with no settings yet still
     * draws something designed rather than something zeroed.
     */
    val luminous: LuminousTuning = LuminousTuning(),
    val pendolo: PendoloTuning = PendoloTuning(),
    /** Direct manipulation commits the wheel's final position through the host. */
    val onPendoloTuningChange: (PendoloTuning) -> Unit = {},
    val tempera: TemperaTuning = TemperaTuning(),
)

/**
 * What a [LyricsBackground] may draw from -- deliberately much less than a
 * visualizer gets.
 *
 * A background is never given the lyric text, the current line or the frame, so it
 * cannot quietly take over typography and the two registries stay genuinely
 * orthogonal. [lineIndex] is the one piece of playback state a background may see,
 * and it exists so that a boundary-driven background (drift) can move on a line
 * change without needing a frame loop to keep moving.
 */
internal data class LyricsBackgroundInputs(
    val item: LibraryItem,
    val palette: List<Color>,
    val lineIndex: Int,
    val motionPercent: Int,
)
