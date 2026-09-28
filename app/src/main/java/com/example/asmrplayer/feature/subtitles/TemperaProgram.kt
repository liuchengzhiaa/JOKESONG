package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextLine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** The thirteen composition families used by the native Tempera scene compiler. */
enum class TemperaShotFamily {
    Split, Band, Frame, Poster, Sparse, Cinema, Charm,
    Aperture, Signal, Corridor, Monolith, Terrain, Monogatari,
}

/** Family variations deliberately total 121, matching the reference's breadth. */
data class TemperaShotKind(val family: TemperaShotFamily, val variant: Int, val ordinal: Int)

enum class TemperaTransitionKind { BlockWipe, CameraPan, ShapeCarry }
enum class TemperaPaletteMode { CoverGradient, ThemeDuo, Monochrome }
enum class TemperaParagraphKind { Breath, Verse, Lift, Chorus, Break, Outro }
enum class TemperaSliceRole { Hero, Supporting, Echo }
enum class TemperaEnterStyle { FromLeft, FromRight, FromTop, FromBottom, Scale, Twist, Stamp }
enum class TemperaDecorMotif { Rings, Crosses, Dots, Grid, Arcs, Rulers, Fragments }

data class TemperaCameraKey(val xFraction: Float, val yFraction: Float, val scale: Float)

data class TemperaDecorSpec(
    val motif: TemperaDecorMotif,
    val hatchAngleDegrees: Float,
    val density: Int,
    val watermark: String?,
    val seed: Int,
)

/** One bounded word layer inside a shot. All placement is normalized. */
data class TemperaSlice(
    val text: String,
    val startMillis: Long,
    val endMillis: Long,
    val settleMillis: Long,
    val releaseMillis: Long,
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float,
    val fontScale: Float,
    val rotationDegrees: Float,
    val role: TemperaSliceRole,
    val enterStyle: TemperaEnterStyle,
    val trackingX: Float,
    val trackingY: Float,
)

data class TemperaShot(
    val id: Long,
    val paragraphIndex: Int,
    val lineIndex: Int,
    val startMillis: Long,
    /** Where the last lyric token in the shot finishes singing. */
    val lyricEndMillis: Long,
    /** Tiled scene tail; may extend past [lyricEndMillis]. */
    val endMillis: Long,
    val kind: TemperaShotKind,
    val paletteMode: TemperaPaletteMode,
    val mirrorArtwork: Boolean,
    val flowAngleDegrees: Float,
    val cameraStart: TemperaCameraKey,
    val cameraEnd: TemperaCameraKey,
    val decor: TemperaDecorSpec,
    val bridge: Boolean,
    val slices: List<TemperaSlice>,
) {
    val family: TemperaShotFamily get() = kind.family
}

data class TemperaParagraph(
    val index: Int,
    val kind: TemperaParagraphKind,
    val startMillis: Long,
    val endMillis: Long,
    val transition: TemperaTransitionKind?,
    val transitionStartMillis: Long,
    val transitionEndMillis: Long,
    val shots: List<TemperaShot>,
)

/** A bounded program around the active line; only this window is resident. */
data class TemperaProgram(
    val sourceRange: IntRange,
    val paragraphGapMillis: Long,
    val paragraphs: List<TemperaParagraph>,
) {
    val shots: List<TemperaShot> = paragraphs.flatMap(TemperaParagraph::shots)
}

data class TemperaShotLayer(
    val shot: TemperaShot,
    val alpha: Float,
    val translationXFraction: Float,
    val translationYFraction: Float,
    val scale: Float,
    /** 0..1 vertical reveal used only by the paragraph block-wipe. */
    val revealProgress: Float = 1f,
)

data class TemperaCameraFrame(
    val translationXFraction: Float,
    val translationYFraction: Float,
    val scale: Float,
)

data class TemperaSlicePose(
    val alpha: Float,
    val translationXFraction: Float,
    val translationYFraction: Float,
    val scale: Float,
    val rotationDegrees: Float,
    val echoAlpha: Float,
    val echoXFraction: Float,
    val echoYFraction: Float,
)

/**
 * Compiles the active lyric neighbourhood into paragraph scenes and 2–4 word shots.
 * All seeds use global line indices, so seeking away and back reproduces the frame.
 */
fun compileTemperaProgramWindow(
    lines: List<TimedTextLine>,
    format: TimedTextFormat,
    centreLineIndex: Int,
    trackSeed: Int,
    wholeLineLyrics: Boolean = false,
    settleStretchPercent: Int = 50,
): TemperaProgram {
    if (lines.isEmpty()) return TemperaProgram(IntRange.EMPTY, DefaultParagraphGapMillis, emptyList())
    val centre = centreLineIndex.coerceIn(lines.indices)
    val range = (centre - ResidentLineRadius).coerceAtLeast(0)..
        (centre + ResidentLineRadius).coerceAtMost(lines.lastIndex)
    val gapThreshold = resolveTemperaParagraphGap(lines)
    val drafts = mutableListOf<MutableList<Int>>()
    range.forEach { globalIndex ->
        val active = drafts.lastOrNull()
        val startsNew = active == null || shouldStartParagraph(
            lines = lines,
            previousIndex = active.last(),
            nextIndex = globalIndex,
            paragraphFirstIndex = active.first(),
            paragraphSize = active.size,
            gapThreshold = gapThreshold,
        )
        if (startsNew) drafts += mutableListOf(globalIndex) else active += globalIndex
    }

    var previousKind: TemperaShotKind? = null
    var previousFlow: Float? = null
    val paragraphs = drafts.mapIndexed { localParagraphIndex, indices ->
        val paragraphIndex = stableParagraphIndex(indices.first(), lines[indices.first()].startMillis)
        val paragraphKind = classifyParagraph(indices, lines, range, localParagraphIndex)
        val lyricShots = mutableListOf<TemperaShot>()
        indices.forEach { globalIndex ->
            val compiled = compileLineShots(
                line = lines[globalIndex],
                nextLineStartMillis = lines.getOrNull(globalIndex + 1)?.startMillis,
                format = format,
                lineIndex = globalIndex,
                paragraphIndex = paragraphIndex,
                paragraphKind = paragraphKind,
                trackSeed = trackSeed,
                wholeLineLyrics = wholeLineLyrics,
                settleStretchPercent = settleStretchPercent,
                previousKind = previousKind,
                previousFlow = previousFlow,
            )
            lyricShots += compiled
            previousKind = compiled.lastOrNull()?.kind ?: previousKind
            previousFlow = compiled.lastOrNull()?.flowAngleDegrees ?: previousFlow
        }
        val paragraphStart = lyricShots.firstOrNull()?.startMillis ?: lines[indices.first()].startMillis
        val paragraphLyricEnd = lyricShots.lastOrNull()?.endMillis ?: paragraphStart + DefaultLineMillis
        val nextParagraphStart = drafts.getOrNull(localParagraphIndex + 1)
            ?.firstOrNull()?.let { lines[it].startMillis }
        val bridgeShots = if (nextParagraphStart != null &&
            nextParagraphStart - paragraphLyricEnd >= BridgeMinGapMillis
        ) {
            buildBridgeShots(
                startMillis = paragraphLyricEnd,
                endMillis = nextParagraphStart,
                lineIndex = indices.last(),
                paragraphIndex = paragraphIndex,
                paragraphKind = paragraphKind,
                trackSeed = trackSeed,
                previousKind = previousKind,
                previousFlow = previousFlow,
            )
        } else emptyList()
        previousKind = bridgeShots.lastOrNull()?.kind ?: previousKind
        previousFlow = bridgeShots.lastOrNull()?.flowAngleDegrees ?: previousFlow
        val allShots = tileShotTails(lyricShots + bridgeShots, nextParagraphStart)
        val paragraphEnd = allShots.lastOrNull()?.endMillis ?: paragraphLyricEnd
        val transitionEnd = nextParagraphStart ?: paragraphEnd
        val transitionDuration = if (nextParagraphStart == null) 0L else
            ((nextParagraphStart - paragraphLyricEnd).coerceAtLeast(0L) + 300L).coerceIn(350L, 1_000L)
        val transition = nextParagraphStart?.let {
            TemperaTransitionKind.entries[positiveMod(mixSeed(trackSeed, paragraphIndex, 0x5F3759DF), 3)]
        }
        TemperaParagraph(
            index = paragraphIndex,
            kind = paragraphKind,
            startMillis = paragraphStart,
            endMillis = paragraphEnd,
            transition = transition,
            transitionStartMillis = transitionEnd - transitionDuration,
            transitionEndMillis = transitionEnd,
            shots = allShots,
        )
    }
    return TemperaProgram(range, gapThreshold, paragraphs)
}

/** Compatibility helper retained for focused tests and previews. */
fun compileTemperaShot(
    line: TimedTextLine,
    nextLineStartMillis: Long?,
    format: TimedTextFormat,
    lineIndex: Int,
    trackSeed: Int,
): TemperaShot = compileLineShots(
    line, nextLineStartMillis, format, lineIndex,
    stableParagraphIndex(lineIndex, line.startMillis), TemperaParagraphKind.Verse,
    trackSeed, true, 50, null, null,
).first()

/** Current and outgoing shot layers for a seek-stable, overlap-based hand-off. */
fun temperaShotLayers(
    program: TemperaProgram,
    positionMillis: Long,
    motionPercent: Int,
    transitionsEnabled: Boolean = true,
): List<TemperaShotLayer> {
    val shots = program.shots
    if (shots.isEmpty()) return emptyList()
    val currentIndex = shots.indexOfLast { it.startMillis <= positionMillis }.coerceAtLeast(0)
    val current = shots[currentIndex]
    val motion = motionPercent.coerceIn(0, 200) / 100f
    if (!transitionsEnabled || motion <= 0f || currentIndex == 0) {
        return listOf(TemperaShotLayer(current, 1f, 0f, 0f, 1f))
    }
    val boundary = program.paragraphs.indexOfFirst {
        it.transition != null && positionMillis in it.transitionStartMillis..it.transitionEndMillis
    }
    if (boundary >= 0 && boundary < program.paragraphs.lastIndex) {
        val outgoingParagraph = program.paragraphs[boundary]
        val incomingParagraph = program.paragraphs[boundary + 1]
        val outgoing = outgoingParagraph.shots.lastOrNull { it.startMillis <= positionMillis }
            ?: outgoingParagraph.shots.last()
        val incoming = incomingParagraph.shots.first()
        val raw = ((positionMillis - outgoingParagraph.transitionStartMillis).toFloat() /
            (outgoingParagraph.transitionEndMillis - outgoingParagraph.transitionStartMillis).coerceAtLeast(1L))
            .coerceIn(0f, 1f)
        val progress = easeInOut(raw)
        val angle = outgoing.flowAngleDegrees * PI.toFloat() / 180f
        val dx = cos(angle)
        val dy = sin(angle)
        return when (outgoingParagraph.transition) {
            TemperaTransitionKind.BlockWipe -> listOf(
                TemperaShotLayer(outgoing, 1f, 0f, 0f, 1f),
                TemperaShotLayer(incoming, 1f, 0f, 0f, 1f, revealProgress = progress * 1.18f),
            )
            TemperaTransitionKind.CameraPan -> listOf(
                TemperaShotLayer(outgoing, 1f - (progress * .18f), dx * progress * .96f * motion, dy * progress * .96f * motion, 1f + .03f * progress),
                TemperaShotLayer(incoming, (raw / .18f).coerceIn(0f, 1f), -dx * (1f - progress) * .96f * motion, -dy * (1f - progress) * .96f * motion, 1.03f - .03f * progress),
            )
            TemperaTransitionKind.ShapeCarry -> listOf(
                TemperaShotLayer(outgoing, 1f - progress, dx * progress * .20f * motion, dy * progress * .20f * motion, 1f + .07f * progress),
                TemperaShotLayer(incoming, progress, -dx * (1f - progress) * .16f * motion, -dy * (1f - progress) * .16f * motion, 1.07f - .07f * progress),
            )
            null -> emptyList()
        }
    }
    val previous = shots[currentIndex - 1]
    val handoff = ((current.lyricEndMillis - current.startMillis) * 0.30f)
        .toLong().coerceIn(MinHandoffMillis, MaxHandoffMillis)
    val raw = ((positionMillis - current.startMillis).toFloat() / handoff.coerceAtLeast(1L)).coerceIn(0f, 1f)
    if (raw >= 1f) return listOf(TemperaShotLayer(current, 1f, 0f, 0f, 1f))
    val progress = easeInOut(raw)
    val angle = current.flowAngleDegrees * PI.toFloat() / 180f
    val dx = cos(angle)
    val dy = sin(angle)
    return listOf(
        TemperaShotLayer(
            previous,
            1f - ((progress - 0.72f) / 0.28f).coerceIn(0f, 1f),
            dx * progress * 0.18f * motion,
            dy * progress * 0.18f * motion,
            1f + 0.035f * progress * motion,
        ),
        TemperaShotLayer(
            current,
            (raw / 0.32f).coerceIn(0f, 1f),
            -dx * (1f - progress) * 0.14f * motion,
            -dy * (1f - progress) * 0.14f * motion,
            0.97f + 0.03f * progress,
        ),
    )
}

fun temperaCameraFrame(
    shot: TemperaShot,
    positionMillis: Long,
    intensityPercent: Int,
): TemperaCameraFrame {
    val intensity = intensityPercent.coerceIn(0, 200) / 100f
    val duration = (shot.endMillis - shot.startMillis).coerceAtLeast(1L)
    val t = easeInOut(((positionMillis - shot.startMillis).toFloat() / duration).coerceIn(0f, 1f))
    return TemperaCameraFrame(
        lerp(shot.cameraStart.xFraction, shot.cameraEnd.xFraction, t) * intensity,
        lerp(shot.cameraStart.yFraction, shot.cameraEnd.yFraction, t) * intensity,
        1f + (lerp(shot.cameraStart.scale, shot.cameraEnd.scale, t) - 1f) * intensity,
    )
}

/** Absolute-time word entrance, echo, sung swell and centre-out release. */
fun temperaSlicePose(
    slice: TemperaSlice,
    positionMillis: Long,
    motionPercent: Int,
): TemperaSlicePose {
    val motion = motionPercent.coerceIn(0, 200) / 100f
    if (motion <= 0f) return TemperaSlicePose(1f, 0f, 0f, 1f, slice.rotationDegrees, 0f, 0f, 0f)
    val duration = (slice.settleMillis - slice.startMillis).coerceAtLeast(1L)
    val linear = ((positionMillis - slice.startMillis).toFloat() / duration).coerceIn(0f, 1f)
    val progress = easeOutBack(linear)
    val revealWindow = duration.coerceAtMost(MaxRevealMillis)
    val alpha = ((positionMillis - slice.startMillis).toFloat() / revealWindow).coerceIn(0f, 1f)
    val travel = EntranceTravelFraction * motion * (1f - progress)
    val direction = when (slice.enterStyle) {
        TemperaEnterStyle.FromLeft -> -1f to 0f
        TemperaEnterStyle.FromRight -> 1f to 0f
        TemperaEnterStyle.FromTop -> 0f to -1f
        TemperaEnterStyle.FromBottom -> 0f to 1f
        else -> 0f to 0f
    }
    val sungDuration = (slice.endMillis - slice.startMillis).coerceAtLeast(1L)
    val attack = (sungDuration * 0.30f).coerceAtMost(180f)
    val swellIn = ((positionMillis - slice.startMillis) / attack).coerceIn(0f, 1f)
    val swellOut = ((positionMillis - slice.endMillis) / CurrentDecayMillis.toFloat()).coerceIn(0f, 1f)
    val swell = easeInOut(swellIn) * (1f - easeInOut(swellOut)) * CurrentScaleSwell
    val releaseStart = maxOf(slice.endMillis, slice.settleMillis)
    val release = ((positionMillis - releaseStart).toFloat() /
        (slice.releaseMillis - releaseStart).coerceAtLeast(1L)).coerceIn(0f, 1f)
    val releaseSpread = easeInOut(release) * ReleaseTracking
    val scaleLoss = when (slice.enterStyle) {
        TemperaEnterStyle.Scale -> 0.55f
        TemperaEnterStyle.Stamp -> -0.34f
        else -> 0.22f
    }
    val twist = if (slice.enterStyle == TemperaEnterStyle.Twist) 52f else 16f
    return TemperaSlicePose(
        alpha,
        direction.first * travel + slice.trackingX * releaseSpread,
        direction.second * travel + slice.trackingY * releaseSpread,
        1f - (1f - progress) * scaleLoss * motion + swell,
        slice.rotationDegrees + (1f - progress) * twist * motion,
        (1f - linear).coerceIn(0f, 1f) * 0.46f * motion,
        direction.first * 0.025f * (1f - linear) * motion,
        direction.second * 0.025f * (1f - linear) * motion,
    )
}

fun resolveTemperaParagraphGap(lines: List<TimedTextLine>): Long {
    val intervals = lines.zipWithNext { first, second -> second.startMillis - first.startMillis }
        .filter { it in 200L..12_000L }.sorted()
    if (intervals.isEmpty()) return DefaultParagraphGapMillis
    return (intervals[intervals.size / 2] * 22L / 10L).coerceIn(1_200L, 6_000L)
}

fun temperaShotKindCount(): Int = ShotVariantCounts.sum()

private fun compileLineShots(
    line: TimedTextLine,
    nextLineStartMillis: Long?,
    format: TimedTextFormat,
    lineIndex: Int,
    paragraphIndex: Int,
    paragraphKind: TemperaParagraphKind,
    trackSeed: Int,
    wholeLineLyrics: Boolean,
    settleStretchPercent: Int,
    previousKind: TemperaShotKind?,
    previousFlow: Float?,
): List<TemperaShot> {
    // Untimed LRC lines do not own the entire silence before the next timestamp.
    // Capping their inferred lyric tail leaves long instrumental gaps available to
    // the bridge-shot compiler instead of freezing one composition for minutes.
    val inferredEnd = nextLineStartMillis?.coerceAtMost(line.startMillis + DefaultLineMillis)
    val lineEnd = (line.endMillis ?: inferredEnd ?: (line.startMillis + DefaultLineMillis))
        .coerceAtLeast(line.startMillis + 1L)
    val words = line.lyricWordTimeline(format, lineEnd, LyricsWordGlowMode.Smart)?.words.orEmpty()
        .ifEmpty { listOf(LyricWord(line.text.ifBlank { "•" }, 0, line.text.length, line.startMillis, lineEnd, emptyList())) }
    val chunks = if (wholeLineLyrics) listOf(words.take(MaxSlicesPerShot)) else {
        val result = mutableListOf<List<LyricWord>>()
        var cursor = 0
        while (cursor < words.size) {
            val seed = mixSeed(trackSeed, lineIndex, cursor + line.text.hashCode())
            val desired = 2 + positiveMod(seed, 3)
            var end = (cursor + desired).coerceAtMost(words.size)
            while (end > cursor + 1 && words[end - 1].endMillis - words[cursor].startMillis > TargetShotMillis) end--
            result += words.subList(cursor, end).take(MaxSlicesPerShot)
            cursor = end
        }
        result
    }
    var lastKind = previousKind
    var lastFlow = previousFlow
    return chunks.mapIndexed { shotIndex, chunk ->
        val seed = mixSeed(trackSeed, lineIndex * 31 + shotIndex, chunk.joinToString("") { it.text }.hashCode())
        var ordinal = positiveMod(seed, temperaShotKindCount())
        if (lastKind?.ordinal == ordinal) ordinal = (ordinal + 1) % temperaShotKindCount()
        val kind = kindAt(ordinal)
        val flow = resolveFlowAngle(lastFlow, seed)
        val start = chunk.first().startMillis
        val lyricEnd = chunk.last().endMillis.coerceAtLeast(start + 1L)
        val shotEnd = chunks.getOrNull(shotIndex + 1)?.firstOrNull()?.startMillis ?: lineEnd
        val camera = buildCamera(kind, seed, flow)
        TemperaShot(
            id = (seed.toLong() shl 32) xor start xor ordinal.toLong(),
            paragraphIndex, lineIndex, start, lyricEnd, shotEnd.coerceAtLeast(lyricEnd), kind,
            TemperaPaletteMode.entries[positiveMod(seed ushr 9, 3)], seed and 1 == 0,
            flow, camera.first, camera.second,
            buildDecor(paragraphKind, kind, chunk, seed), false,
            buildSlices(chunk, kind, lineEnd, seed, settleStretchPercent),
        ).also { lastKind = kind; lastFlow = flow }
    }
}

private fun buildSlices(
    words: List<LyricWord>,
    kind: TemperaShotKind,
    lineEndMillis: Long,
    seed: Int,
    settleStretchPercent: Int,
): List<TemperaSlice> {
    val hero = words.indices.maxByOrNull { words[it].text.trim().length } ?: 0
    val centre = words.lastIndex.coerceAtLeast(1) / 2f
    return words.take(MaxSlicesPerShot).mapIndexed { index, word ->
        val slot = temperaSlot(kind, index, words.size, seed)
        val stretch = settleStretchPercent.coerceIn(0, 100) / 100f
        val settleWindow = (BaseSettleMillis +
            (word.endMillis - word.startMillis - BaseSettleMillis) * stretch)
            .toLong().coerceIn(MinSettleMillis, MaxSettleMillis)
        val role = when { index == hero -> TemperaSliceRole.Hero; index >= 5 -> TemperaSliceRole.Echo; else -> TemperaSliceRole.Supporting }
        TemperaSlice(
            word.text, word.startMillis, word.endMillis,
            (word.startMillis + settleWindow).coerceAtMost(lineEndMillis), lineEndMillis,
            slot.x, slot.y, slot.width,
            slot.scale * when (role) { TemperaSliceRole.Hero -> 1.48f; TemperaSliceRole.Supporting -> 0.82f; TemperaSliceRole.Echo -> 0.72f },
            slot.rotation, role,
            TemperaEnterStyle.entries[positiveMod(seed + index * 31, 7)],
            (index - centre) * 0.12f, ((index and 1) * 2 - 1) * 0.035f,
        )
    }
}

private fun buildBridgeShots(
    startMillis: Long,
    endMillis: Long,
    lineIndex: Int,
    paragraphIndex: Int,
    paragraphKind: TemperaParagraphKind,
    trackSeed: Int,
    previousKind: TemperaShotKind?,
    previousFlow: Float?,
): List<TemperaShot> {
    val count = ceil((endMillis - startMillis) / BridgeMaxMillis.toDouble()).toInt().coerceIn(1, 3)
    val length = (endMillis - startMillis) / count
    var lastKind = previousKind
    var lastFlow = previousFlow
    return List(count) { index ->
        val seed = mixSeed(trackSeed, paragraphIndex, 0xB71D6E + index)
        var ordinal = positiveMod(seed, temperaShotKindCount())
        if (lastKind?.ordinal == ordinal) ordinal = (ordinal + 1) % temperaShotKindCount()
        val kind = kindAt(ordinal)
        val flow = resolveFlowAngle(lastFlow, seed)
        val start = startMillis + length * index
        val end = if (index == count - 1) endMillis else startMillis + length * (index + 1)
        val camera = buildCamera(kind, seed, flow)
        TemperaShot(
            (seed.toLong() shl 32) xor start, paragraphIndex, lineIndex, start, end, end, kind,
            TemperaPaletteMode.entries[positiveMod(seed ushr 9, 3)], seed and 1 == 0,
            flow, camera.first, camera.second,
            buildDecor(paragraphKind, kind, emptyList(), seed), true, emptyList(),
        ).also { lastKind = kind; lastFlow = flow }
    }
}

private fun tileShotTails(shots: List<TemperaShot>, nextParagraphStart: Long?): List<TemperaShot> =
    shots.mapIndexed { index, shot ->
        shot.copy(endMillis = (shots.getOrNull(index + 1)?.startMillis ?: nextParagraphStart ?: shot.endMillis)
            .coerceAtLeast(shot.lyricEndMillis))
    }

private data class TemperaSlot(val x: Float, val y: Float, val width: Float, val scale: Float, val rotation: Float)

private fun temperaSlot(kind: TemperaShotKind, index: Int, count: Int, seed: Int): TemperaSlot {
    val t = if (count <= 1) 0.5f else index.toFloat() / (count - 1)
    val jitter = signedUnit(seed + index * 97) * 0.045f
    val flip = if (kind.variant and 1 == 0) 1f else -1f
    return when (kind.family) {
        TemperaShotFamily.Split -> TemperaSlot(if (index % 2 == 0) 0.24f else 0.72f, 0.24f + index % 3 * 0.21f, 0.46f, 1.02f, flip * 3f)
        TemperaShotFamily.Band -> TemperaSlot(0.14f + t * 0.72f, 0.72f - t * 0.48f + jitter, 0.46f, 1.12f, flip * (7f + kind.variant))
        TemperaShotFamily.Frame -> TemperaSlot(if (index % 2 == 0) 0.26f else 0.70f, if (index < 2) 0.28f else 0.62f, 0.42f, 0.96f, if (index % 2 == 0) 0f else 90f)
        TemperaShotFamily.Poster -> TemperaSlot(0.16f + t * 0.63f, 0.31f + index % 2 * 0.28f, 0.62f, 1.28f - index * 0.07f, flip * 2f)
        TemperaShotFamily.Sparse -> TemperaSlot(0.12f + positiveMod(seed + index * 53, 76) / 100f, 0.14f + positiveMod(seed + index * 71, 68) / 100f, 0.34f, 0.82f, flip * (4f + index * 2f))
        TemperaShotFamily.Cinema -> TemperaSlot(0.5f, 0.40f + (index - count / 2f) * 0.13f, 0.82f, 1.10f, 0f)
        TemperaShotFamily.Charm -> TemperaSlot(0.18f + positiveMod(seed + index * 53, 65) / 100f, 0.18f + positiveMod((seed ushr 2) + index * 71, 58) / 100f, 0.38f, 0.94f, -8f + positiveMod(seed + index * 19, 17))
        TemperaShotFamily.Aperture -> { val a = index.toFloat() / count.coerceAtLeast(1) * 2f * PI.toFloat(); TemperaSlot(0.5f + cos(a) * 0.27f, 0.47f + sin(a) * 0.22f, 0.38f, 0.96f, a * 7f) }
        TemperaShotFamily.Signal -> TemperaSlot(0.18f + t * 0.64f, 0.22f + (index % 3) * 0.22f, 0.39f, 0.98f, if (index % 2 == 0) -2f else 2f)
        TemperaShotFamily.Corridor -> TemperaSlot(0.5f + (t - 0.5f) * 0.34f, 0.18f + t * 0.57f, 0.56f - t * 0.18f, 1.18f - t * 0.40f, (t - 0.5f) * 8f)
        TemperaShotFamily.Monolith -> TemperaSlot(0.50f + jitter, 0.30f + index * 0.11f, 0.86f, 1.34f - index * 0.07f, flip * 2f)
        TemperaShotFamily.Terrain -> TemperaSlot(0.15f + t * 0.70f, 0.68f - abs(t - 0.5f) * 0.24f, 0.43f, 1.02f, (t - 0.5f) * 14f)
        TemperaShotFamily.Monogatari -> TemperaSlot(0.12f + t * 0.42f, 0.24f + index * 0.17f, 0.84f, 1.42f - index * 0.10f, 0f)
    }
}

private fun kindAt(ordinal: Int): TemperaShotKind {
    var remaining = ordinal.coerceIn(0, temperaShotKindCount() - 1)
    TemperaShotFamily.entries.forEachIndexed { index, family ->
        val count = ShotVariantCounts[index]
        if (remaining < count) return TemperaShotKind(family, remaining, ordinal)
        remaining -= count
    }
    return TemperaShotKind(TemperaShotFamily.Monogatari, 0, 0)
}

private fun buildCamera(kind: TemperaShotKind, seed: Int, flowDegrees: Float): Pair<TemperaCameraKey, TemperaCameraKey> {
    val radians = flowDegrees * PI.toFloat() / 180f
    val amount = when (kind.family) {
        TemperaShotFamily.Cinema, TemperaShotFamily.Corridor -> 0.052f
        TemperaShotFamily.Sparse, TemperaShotFamily.Charm -> 0.025f
        else -> 0.036f
    }
    val side = signedUnit(seed ushr 3) * 0.012f
    return TemperaCameraKey(-cos(radians) * amount + side, -sin(radians) * amount, 1.01f) to
        TemperaCameraKey(cos(radians) * amount - side, sin(radians) * amount, 1.055f)
}

private fun buildDecor(paragraphKind: TemperaParagraphKind, kind: TemperaShotKind, words: List<LyricWord>, seed: Int): TemperaDecorSpec {
    val sparse = paragraphKind in setOf(TemperaParagraphKind.Breath, TemperaParagraphKind.Break, TemperaParagraphKind.Outro)
    return TemperaDecorSpec(
        TemperaDecorMotif.entries[positiveMod(seed ushr 7, TemperaDecorMotif.entries.size)],
        -55f + positiveMod(seed ushr 11, 111),
        if (sparse) 4 + positiveMod(seed, 4) else 8 + positiveMod(seed, 8),
        words.maxByOrNull { it.text.length }?.text?.takeIf(String::isNotBlank),
        seed xor kind.ordinal,
    )
}

private fun resolveFlowAngle(previous: Float?, seed: Int): Float {
    val target = -90f + signedUnit(seed) * 32f
    return previous?.let { it * 0.72f + target * 0.28f } ?: target
}

private fun shouldStartParagraph(lines: List<TimedTextLine>, previousIndex: Int, nextIndex: Int, paragraphFirstIndex: Int, paragraphSize: Int, gapThreshold: Long): Boolean {
    if (nextIndex != previousIndex + 1 || paragraphSize >= MaxParagraphLines) return true
    if (lines[nextIndex].startMillis - lines[paragraphFirstIndex].startMillis >= MaxParagraphMillis) return true
    val nextStart = lines.getOrNull(previousIndex + 1)?.startMillis
    val previousEnd = lines[previousIndex].endMillis
        ?: nextStart?.coerceAtMost(lines[previousIndex].startMillis + DefaultLineMillis)
        ?: (lines[previousIndex].startMillis + DefaultLineMillis)
    return lines[nextIndex].startMillis - previousEnd >= gapThreshold
}

private fun classifyParagraph(indices: List<Int>, lines: List<TimedTextLine>, range: IntRange, localIndex: Int): TemperaParagraphKind = when {
    indices.last() == lines.lastIndex -> TemperaParagraphKind.Outro
    indices.size == 1 -> TemperaParagraphKind.Breath
    localIndex > 0 && lines[indices.first()].startMillis -
        (lines[indices.first() - 1].endMillis ?: lines[indices.first() - 1].startMillis) > 4_000L -> TemperaParagraphKind.Break
    indices.sumOf { lines[it].text.length } / indices.size >= 18 -> TemperaParagraphKind.Lift
    stableParagraphIndex(indices.first(), lines[indices.first()].startMillis) % 4 == 2 -> TemperaParagraphKind.Chorus
    indices.first() == range.first -> TemperaParagraphKind.Verse
    else -> TemperaParagraphKind.Verse
}

private fun stableParagraphIndex(firstLine: Int, startMillis: Long): Int =
    positiveMod(firstLine * 31 + (startMillis / 1_000L).toInt(), 1_000_003)

private fun mixSeed(trackSeed: Int, index: Int, textHash: Int): Int {
    var value = trackSeed xor (index * -0x61c88647) xor textHash
    value = value xor (value ushr 16)
    value *= -0x7a143595
    return value xor (value ushr 13)
}

private fun positiveMod(value: Int, divisor: Int): Int = ((value % divisor) + divisor) % divisor
private fun signedUnit(seed: Int): Float = positiveMod(seed, 2_001) / 1_000f - 1f
private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction
private fun easeInOut(value: Float): Float = value * value * (3f - 2f * value)
private fun easeOutBack(value: Float): Float { val shifted = value - 1f; return 1f + shifted * shifted * ((BackOvershoot + 1f) * shifted + BackOvershoot) }

private val ShotVariantCounts = intArrayOf(11, 10, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9)
private const val ResidentLineRadius = 14
private const val MaxParagraphLines = 7
private const val MaxParagraphMillis = 26_000L
private const val DefaultParagraphGapMillis = 2_400L
private const val DefaultLineMillis = 5_000L
private const val TargetShotMillis = 2_200L
private const val MaxSlicesPerShot = 8
private const val BridgeMinGapMillis = 1_200L
private const val BridgeMaxMillis = 5_000L
private const val MinHandoffMillis = 400L
private const val MaxHandoffMillis = 1_100L
private const val BaseSettleMillis = 340L
private const val MinSettleMillis = 220L
private const val MaxSettleMillis = 4_800L
private const val MaxRevealMillis = 1_350L
private const val CurrentDecayMillis = 260L
private const val CurrentScaleSwell = 0.05f
private const val ReleaseTracking = 0.055f
private const val EntranceTravelFraction = 0.22f
private const val BackOvershoot = 0.38f
