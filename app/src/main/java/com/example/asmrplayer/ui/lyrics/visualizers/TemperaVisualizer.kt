package com.example.asmrplayer.ui.lyrics.visualizers

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.feature.subtitles.TemperaArtworkSource
import com.example.asmrplayer.feature.subtitles.TemperaColorMode
import com.example.asmrplayer.feature.subtitles.TemperaDecorMotif
import com.example.asmrplayer.feature.subtitles.TemperaImageDepth
import com.example.asmrplayer.feature.subtitles.TemperaPaletteMode
import com.example.asmrplayer.feature.subtitles.TemperaShot
import com.example.asmrplayer.feature.subtitles.TemperaShotFamily
import com.example.asmrplayer.feature.subtitles.TemperaSlice
import com.example.asmrplayer.feature.subtitles.TemperaSliceRole
import com.example.asmrplayer.feature.subtitles.clamped
import com.example.asmrplayer.feature.subtitles.compileTemperaProgramWindow
import com.example.asmrplayer.feature.subtitles.temperaCameraFrame
import com.example.asmrplayer.feature.subtitles.temperaShotLayers
import com.example.asmrplayer.feature.subtitles.temperaSlicePose
import com.example.asmrplayer.ui.lyrics.GlowingCurrentLineText
import com.example.asmrplayer.ui.lyrics.LyricsSceneInputs
import com.example.asmrplayer.ui.lyrics.ProgramLyricsVisualizer
import com.example.asmrplayer.ui.lyrics.lyricsSceneIsRunning
import com.example.asmrplayer.ui.lyrics.rememberLyricsScenePosition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Native Tempera runtime: a persistent, absolute-time PV rather than a decorated
 * lyric page. A bounded document window compiles into paragraphs and multiple shots;
 * outgoing and incoming shots coexist during their hand-off.
 */
internal object TemperaVisualizer : ProgramLyricsVisualizer {
    override fun transition(durationMillis: Int, direction: Int): ContentTransform =
        fadeIn().togetherWith(fadeOut())

    @Composable
    override fun Program(document: TimedTextDocument, inputs: LyricsSceneInputs) {
        val tuning = inputs.tempera.clamped()
        // Shift the bounded compiler window only once per eight source lines. The
        // program therefore survives ordinary line changes like Folia's Pixi scene,
        // while still never retaining an unbounded ASMR transcript.
        val programWindowAnchor = ((inputs.frame.currentIndex / ProgramWindowStride) * ProgramWindowStride +
            ProgramWindowStride / 2).coerceAtMost(document.lines.lastIndex)
        val program = remember(
            document,
            programWindowAnchor,
            inputs.item.paletteSeed,
            tuning.wholeLineLyrics,
            tuning.glyphSettleStretchPercent,
        ) {
            compileTemperaProgramWindow(
                lines = document.lines,
                format = document.format,
                centreLineIndex = programWindowAnchor,
                trackSeed = inputs.item.paletteSeed,
                wholeLineLyrics = tuning.wholeLineLyrics,
                settleStretchPercent = tuning.glyphSettleStretchPercent,
            )
        }
        val running = lyricsSceneIsRunning(inputs.isPlaying, inputs.motionPercent)
        val position = rememberLyricsScenePosition(inputs.positionMs, running)
        // The frame clock is read only from draw/layer lambdas below. Smooth motion
        // therefore invalidates render nodes, not the AsyncImage and Text trees.
        val coarseShotIndex = program.shots.indexOfLast { it.startMillis <= inputs.positionMs }.coerceAtLeast(0)
        val residentShots = remember(program, coarseShotIndex) {
            if (program.shots.isEmpty()) emptyList() else {
                val start = (coarseShotIndex - 3).coerceAtLeast(0)
                val end = (coarseShotIndex + 3).coerceAtMost(program.shots.lastIndex)
                program.shots.subList(start, end + 1)
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val targetAspect = tuning.stageAspect.widthOverHeight
            val stageModifier = when {
                targetAspect == null -> Modifier.fillMaxSize()
                maxWidth.value / maxHeight.value <= targetAspect -> Modifier.fillMaxWidth().aspectRatio(targetAspect)
                else -> Modifier.fillMaxHeight().aspectRatio(targetAspect)
            }
            BoxWithConstraints(stageModifier.clipToBounds()) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val stageScale = (minOf(maxWidth.value, maxHeight.value) / ReferenceStageDp)
                    .coerceIn(MinStageScale, MaxStageScale)
                residentShots.forEach { shot ->
                    key(shot.id) {
                        val artwork = resolveArtwork(shot, inputs, tuning.imageFrequencyPercent)
                        Box(
                            Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    val layer = temperaShotLayers(
                                        program, position.value, inputs.motionPercent, tuning.enableTransitions,
                                    ).firstOrNull { it.shot.id == shot.id }
                                    if (layer == null) return@drawWithContent
                                    val reveal = layer.revealProgress.coerceIn(0f, 1f)
                                    if (reveal >= 1f) drawContent() else {
                                        clipRect(top = size.height * (1f - reveal)) {
                                            this@drawWithContent.drawContent()
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    val layer = temperaShotLayers(
                                        program, position.value, inputs.motionPercent, tuning.enableTransitions,
                                    ).firstOrNull { it.shot.id == shot.id }
                                    if (layer == null) {
                                        alpha = 0f
                                        return@graphicsLayer
                                    }
                                    val camera = temperaCameraFrame(
                                        shot, position.value, tuning.cameraIntensityPercent,
                                    )
                                    translationX = (layer.translationXFraction + camera.translationXFraction) * widthPx
                                    translationY = (layer.translationYFraction + camera.translationYFraction) * heightPx
                                    scaleX = layer.scale * camera.scale
                                    scaleY = layer.scale * camera.scale
                                    alpha = layer.alpha
                                    clip = true
                                },
                        ) {
                            if (tuning.imageDepth == TemperaImageDepth.BehindType) {
                                TemperaArtwork(shot, artwork)
                            }
                            if (tuning.showBlocks) {
                                TemperaBlocks(shot, inputs.accent, inputs.palette, Modifier.fillMaxSize())
                            }
                            if (tuning.showDecor) {
                                TemperaDecor(shot, inputs.accent, Modifier.fillMaxSize())
                                shot.decor.watermark?.let {
                                    Text(
                                        text = it,
                                        color = Color.White.copy(alpha = 0.055f),
                                        fontSize = (112f * stageScale).sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .graphicsLayer { rotationZ = shot.flowAngleDegrees * 0.15f },
                                    )
                                }
                            }
                            val ink = temperaInk(shot, inputs, tuning.colorMode, tuning.textInversion)
                            shot.slices.sortedBy { if (it.role == TemperaSliceRole.Hero) 1 else 0 }
                                .forEach { slice ->
                                    TemperaTypeSlice(
                                        slice = slice,
                                        positionMillis = { position.value },
                                        motionPercent = inputs.motionPercent * tuning.glyphMotionPercent / 100,
                                        widthPx = widthPx,
                                        heightPx = heightPx,
                                        stageScale = stageScale,
                                        ink = ink,
                                    )
                                }
                            if (tuning.imageDepth == TemperaImageDepth.InFrontOfType) {
                                TemperaArtwork(shot, artwork)
                            }
                            if (tuning.showCornerMarks) TemperaCornerMarks(inputs.accent, Modifier.fillMaxSize())
                        }
                    }
                }
                TemperaPrintFinish(inputs.accent, Modifier.fillMaxSize())

                // Folia keeps a stable subtitle strip even while expressive type is
                // scattered across the composition.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = if (inputs.compact) 20.dp else 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GlowingCurrentLineText(
                        inputs = inputs,
                        fontSize = (if (inputs.compact) 14 else 17).sp,
                        lineHeight = (if (inputs.compact) 19 else 23).sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        edgeSoftnessPx = 9f,
                        glowStrength = 0.32f,
                    )
                }
            }
        }
    }
}

private data class TemperaArtworkSelection(val uri: String, val libraryImage: Boolean)

private fun resolveArtwork(shot: TemperaShot, inputs: LyricsSceneInputs, frequency: Int): TemperaArtworkSelection? {
    if (positiveIndex(shot.id xor 0x51A7L, 100) >= frequency.coerceIn(0, 100)) return null
    return when (inputs.tempera.artworkSource) {
        TemperaArtworkSource.Cover -> inputs.item.artworkUri?.let { TemperaArtworkSelection(it, false) }
        TemperaArtworkSource.ImageLibrary -> inputs.tempera.imageUris.takeIf(List<String>::isNotEmpty)
            ?.let { TemperaArtworkSelection(it[positiveIndex(shot.id, it.size)], true) }
            ?: inputs.item.artworkUri?.let { TemperaArtworkSelection(it, false) }
    }
}

@Composable
private fun TemperaArtwork(shot: TemperaShot, selection: TemperaArtworkSelection?) {
    if (selection == null) return
    val artworkUri = selection.uri
    val common = Modifier.graphicsLayer {
        alpha = if (selection.libraryImage) 0.88f else when (shot.paletteMode) {
                TemperaPaletteMode.CoverGradient -> 0.70f
                TemperaPaletteMode.ThemeDuo -> 0.52f
                TemperaPaletteMode.Monochrome -> 0.38f
            }
        scaleX = if (shot.mirrorArtwork) -1f else 1f
    }
    if (selection.libraryImage) {
        val alignment = listOf(
            Alignment.BottomStart,
            Alignment.BottomCenter,
            Alignment.BottomEnd,
            Alignment.CenterStart,
            Alignment.CenterEnd,
        )[positiveIndex(shot.id ushr 7, 5)]
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                artworkUri,
                null,
                Modifier
                    .align(alignment)
                    .fillMaxWidth(0.76f)
                    .fillMaxHeight(0.78f)
                    .then(common),
                contentScale = ContentScale.Fit,
            )
        }
        return
    }
    when (shot.family) {
        TemperaShotFamily.Split -> Box(Modifier.fillMaxSize()) {
            AsyncImage(artworkUri, null, Modifier.align(if (shot.mirrorArtwork) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight(0.86f).fillMaxWidth(0.60f).then(common), contentScale = ContentScale.Crop)
        }
        TemperaShotFamily.Frame -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AsyncImage(artworkUri, null, Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.68f).clip(RoundedCornerShape(3.dp)).then(common), contentScale = ContentScale.Crop)
        }
        TemperaShotFamily.Sparse -> Box(Modifier.fillMaxSize(), contentAlignment = if (shot.kind.variant and 1 == 0) Alignment.TopEnd else Alignment.BottomStart) {
            AsyncImage(artworkUri, null, Modifier.fillMaxWidth(0.46f).fillMaxHeight(0.42f).padding(24.dp).clip(RoundedCornerShape(2.dp)).then(common), contentScale = ContentScale.Crop)
        }
        TemperaShotFamily.Charm,
        TemperaShotFamily.Aperture,
        -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AsyncImage(artworkUri, null, Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.60f).clip(CircleShape).then(common), contentScale = ContentScale.Crop)
        }
        TemperaShotFamily.Terrain -> Box(Modifier.fillMaxSize()) {
            AsyncImage(artworkUri, null, Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.60f).then(common), contentScale = ContentScale.Crop)
        }
        TemperaShotFamily.Cinema -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AsyncImage(artworkUri, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(common), contentScale = ContentScale.Crop)
        }
        else -> AsyncImage(
            artworkUri,
            null,
            Modifier.fillMaxSize().then(common).graphicsLayer {
                rotationZ = if (shot.family == TemperaShotFamily.Band) 4f else 0f
                scaleX *= 1.08f
                scaleY = 1.08f
            },
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun TemperaBlocks(shot: TemperaShot, accent: Color, palette: List<Color>, modifier: Modifier) {
    val secondary = palette.getOrNull(1) ?: Color.White
    Canvas(modifier) {
        val ink = accent.copy(alpha = 0.30f)
        val variant = shot.kind.variant
        when (shot.family) {
            TemperaShotFamily.Split -> {
                val split = 0.38f + (variant % 4) * 0.06f
                drawRect(Color.Black.copy(alpha = 0.36f), size = Size(size.width * split, size.height))
                drawRect(accent.copy(alpha = 0.16f), topLeft = Offset(size.width * split, 0f), size = Size(size.width * (1f - split), size.height))
                drawLine(secondary.copy(alpha = 0.5f), Offset(size.width * split, 0f), Offset(size.width * split, size.height), 1.dp.toPx())
            }
            TemperaShotFamily.Band -> {
                val lift = (variant % 5) * 0.035f
                val band = Path().apply {
                    moveTo(-size.width * 0.1f, size.height * (0.68f - lift))
                    lineTo(size.width * 1.1f, size.height * (0.17f + lift))
                    lineTo(size.width * 1.1f, size.height * (0.38f + lift))
                    lineTo(-size.width * 0.1f, size.height * (0.87f - lift)); close()
                }
                drawPath(band, Brush.linearGradient(listOf(accent.copy(alpha = 0.58f), secondary.copy(alpha = 0.22f))))
            }
            TemperaShotFamily.Frame -> repeat(3 + variant % 3) { index ->
                val inset = (16 + index * 19).dp.toPx()
                drawRect(accent.copy(alpha = 0.34f - index * 0.05f), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke((1.5f - index * 0.15f).dp.toPx()))
            }
            TemperaShotFamily.Poster -> {
                drawRect(Color.Black.copy(alpha = 0.46f), Offset(size.width * 0.08f, size.height * 0.12f), Size(size.width * 0.84f, size.height * 0.76f))
                drawRect(accent.copy(alpha = 0.30f), Offset(size.width * 0.08f, size.height * (0.12f + (variant % 3) * 0.18f)), Size(size.width * 0.84f, size.height * 0.12f))
            }
            TemperaShotFamily.Sparse -> repeat(3 + variant % 4) { index ->
                val x = size.width * (0.12f + ((shot.decor.seed + index * 31).ushr(1) % 70) / 100f)
                val y = size.height * (0.12f + ((shot.decor.seed + index * 47).ushr(2) % 70) / 100f)
                drawRect(if (index % 2 == 0) ink else secondary.copy(alpha = 0.16f), Offset(x, y), Size((18 + index * 5).dp.toPx(), (7 + index * 3).dp.toPx()))
            }
            TemperaShotFamily.Cinema -> {
                drawRect(Color.Black.copy(alpha = 0.72f), size = Size(size.width, size.height * 0.12f))
                drawRect(Color.Black.copy(alpha = 0.72f), Offset(0f, size.height * 0.88f), Size(size.width, size.height * 0.12f))
                drawLine(accent.copy(alpha = 0.5f), Offset(0f, size.height * 0.86f), Offset(size.width, size.height * 0.86f), 1.dp.toPx())
            }
            TemperaShotFamily.Charm -> repeat(8 + variant % 5) { index ->
                val x = size.width * (0.08f + positiveIndex((shot.id + index * 37).toLong(), 83) / 100f)
                val y = size.height * (0.10f + positiveIndex((shot.id + index * 53).toLong(), 79) / 100f)
                drawCircle(if (index % 2 == 0) accent.copy(alpha = 0.25f) else secondary.copy(alpha = 0.18f), (7 + index % 4 * 5).dp.toPx(), Offset(x, y), style = if (index % 3 == 0) Stroke(1.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill)
            }
            TemperaShotFamily.Aperture -> repeat(4 + variant % 3) { index ->
                drawCircle(accent.copy(alpha = 0.34f - index * 0.045f), minOf(size.width, size.height) * (0.40f - index * 0.055f), style = Stroke((2f - index * 0.18f).dp.toPx()))
            }
            TemperaShotFamily.Signal -> repeat(6 + variant % 4) { index ->
                val y = size.height * (0.16f + index * 0.10f)
                val width = size.width * (0.22f + positiveIndex(shot.id + index * 13L, 62) / 100f)
                drawRect(if (index % 3 == 0) accent.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.12f), Offset(if (index % 2 == 0) 0f else size.width - width, y), Size(width, (2 + index % 3 * 3).dp.toPx()))
            }
            TemperaShotFamily.Corridor -> {
                val vanishing = Offset(size.width * (0.46f + variant % 4 * 0.025f), size.height * 0.36f)
                listOf(0f, .18f, .36f, .64f, .82f, 1f).forEach { x -> drawLine(ink, Offset(size.width * x, size.height), vanishing, 1.dp.toPx()) }
                repeat(5) { index -> val t = index / 5f; drawLine(ink, Offset(size.width * t * .5f, size.height * (.36f + t * .64f)), Offset(size.width * (1f - t * .5f), size.height * (.36f + t * .64f)), 1.dp.toPx()) }
            }
            TemperaShotFamily.Monolith -> {
                val left = 0.08f + variant % 4 * 0.04f
                drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = .54f), Color.Black.copy(alpha = .56f))), Offset(size.width * left, size.height * .10f), Size(size.width * (0.82f - left), size.height * .76f))
                drawLine(Color.White.copy(alpha = .24f), Offset(size.width * (left + .03f), size.height * .12f), Offset(size.width * (left + .03f), size.height * .82f), 1.dp.toPx())
            }
            TemperaShotFamily.Terrain -> repeat(5 + variant % 4) { index ->
                val y = size.height * (.43f + index * .075f)
                drawLine(accent.copy(alpha = .34f - index * .035f), Offset(0f, y), Offset(size.width, y - size.height * (.03f + index * .008f)), (1.6f - index * .12f).dp.toPx())
            }
            TemperaShotFamily.Monogatari -> {
                drawRect(if (variant % 2 == 0) Color.Black.copy(alpha = .54f) else accent.copy(alpha = .28f))
                drawRect(secondary.copy(alpha = .16f), Offset(size.width * .08f, 0f), Size(size.width * .045f, size.height))
            }
        }
    }
}

@Composable
private fun TemperaDecor(shot: TemperaShot, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        val colour = accent.copy(alpha = 0.16f)
        val count = shot.decor.density
        val angle = shot.decor.hatchAngleDegrees * PI.toFloat() / 180f
        repeat(count) { index ->
            val t = (index + 1f) / (count + 1f)
            when (shot.decor.motif) {
                TemperaDecorMotif.Rings -> drawCircle(colour, minOf(size.width, size.height) * (.06f + t * .32f), Offset(size.width * .5f, size.height * .48f), style = Stroke(.8.dp.toPx()))
                TemperaDecorMotif.Crosses -> { val p = Offset(size.width * t, size.height * ((index * 37 % count + 1f) / (count + 1f))); val r = (5 + index % 4 * 3).dp.toPx(); drawLine(colour, p - Offset(r, 0f), p + Offset(r, 0f), 1.dp.toPx()); drawLine(colour, p - Offset(0f, r), p + Offset(0f, r), 1.dp.toPx()) }
                TemperaDecorMotif.Dots -> drawCircle(colour, (2 + index % 4).dp.toPx(), Offset(size.width * t, size.height * ((index * 53 % count + 1f) / (count + 1f))))
                TemperaDecorMotif.Grid -> { drawLine(colour, Offset(size.width * t, 0f), Offset(size.width * t, size.height), .7.dp.toPx()); drawLine(colour, Offset(0f, size.height * t), Offset(size.width, size.height * t), .7.dp.toPx()) }
                TemperaDecorMotif.Arcs -> drawArc(colour, index * 22f, 110f, false, Offset(size.width * .12f, size.height * .14f), Size(size.width * .76f, size.height * .68f), style = Stroke(.8.dp.toPx()))
                TemperaDecorMotif.Rulers -> { val x = size.width * (.06f + .88f * t); drawLine(colour, Offset(x, size.height * .08f), Offset(x, size.height * (.08f + .025f * (1 + index % 3))), 1.dp.toPx()) }
                TemperaDecorMotif.Fragments -> { val x = size.width * t; val y = size.height * ((index * 41 % count + 1f) / (count + 1f)); drawLine(colour, Offset(x, y), Offset(x + cos(angle) * 22.dp.toPx(), y + sin(angle) * 22.dp.toPx()), 1.dp.toPx()) }
            }
        }
        // Deterministic hatch/screentone, compiled once per shot.
        val spacing = (16 + shot.kind.variant % 6 * 3).dp.toPx()
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(Color.White.copy(alpha = .035f), Offset(x, 0f), Offset(x + cos(angle) * size.height, size.height), .7.dp.toPx())
            x += spacing
        }
    }
}

@Composable
private fun TemperaCornerMarks(accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        val inset = 13.dp.toPx(); val arm = 23.dp.toPx(); val stroke = 1.dp.toPx(); val c = accent.copy(alpha = .55f)
        drawLine(c, Offset(inset, inset), Offset(inset + arm, inset), stroke); drawLine(c, Offset(inset, inset), Offset(inset, inset + arm), stroke)
        drawLine(c, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - arm, size.height - inset), stroke)
        drawLine(c, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - arm), stroke)
    }
}

@Composable
private fun TemperaPrintFinish(accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .42f)), center = center, radius = size.maxDimension * .72f))
        repeat(72) { index ->
            val x = positiveIndex(index * 7919L, 10_000) / 10_000f * size.width
            val y = positiveIndex(index * 3571L + 17L, 10_000) / 10_000f * size.height
            drawCircle(if (index % 7 == 0) accent.copy(alpha = .045f) else Color.White.copy(alpha = .025f), .55.dp.toPx(), Offset(x, y))
        }
    }
}

@Composable
private fun TemperaTypeSlice(
    slice: TemperaSlice,
    positionMillis: () -> Long,
    motionPercent: Int,
    widthPx: Float,
    heightPx: Float,
    stageScale: Float,
    ink: Color,
) {
    val fontSize = (BaseTypeSp * slice.fontScale * stageScale).coerceIn(MinTypeSp, MaxTypeSp)
    val colour = when (slice.role) {
        TemperaSliceRole.Hero -> ink
        TemperaSliceRole.Supporting -> ink.copy(alpha = .90f)
        TemperaSliceRole.Echo -> ink.copy(alpha = .48f)
    }
    Box(
        Modifier.fillMaxWidth(slice.widthFraction).height((fontSize * 1.9f).dp).graphicsLayer {
            val pose = temperaSlicePose(slice, positionMillis(), motionPercent)
            translationX = slice.xFraction * widthPx - size.width / 2f + pose.translationXFraction * widthPx
            translationY = slice.yFraction * heightPx - size.height / 2f + pose.translationYFraction * heightPx
            rotationZ = pose.rotationDegrees; scaleX = pose.scale; scaleY = pose.scale; alpha = pose.alpha
        },
        contentAlignment = Alignment.Center,
    ) {
        TemperaSliceText(slice, colour.copy(alpha = .55f), fontSize, Modifier.graphicsLayer {
            val pose = temperaSlicePose(slice, positionMillis(), motionPercent)
            translationX = pose.echoXFraction * widthPx * 2f
            translationY = pose.echoYFraction * heightPx * 2f
            alpha = pose.echoAlpha * .55f
        })
        TemperaSliceText(slice, colour, fontSize, Modifier.graphicsLayer {
            val pose = temperaSlicePose(slice, positionMillis(), motionPercent)
            translationX = pose.echoXFraction * widthPx
            translationY = pose.echoYFraction * heightPx
            alpha = pose.echoAlpha
        })
        TemperaSliceText(slice, colour, fontSize, Modifier)
    }
}

@Composable
private fun TemperaSliceText(slice: TemperaSlice, colour: Color, fontSize: Float, modifier: Modifier) {
    Text(
        text = slice.text,
        color = colour,
        fontSize = fontSize.sp,
        fontWeight = if (slice.role == TemperaSliceRole.Hero) FontWeight.Black else FontWeight.Bold,
        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        lineHeight = (fontSize * 1.02f).sp,
        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .42f), Offset(0f, 2f), 7f)),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun temperaInk(shot: TemperaShot, inputs: LyricsSceneInputs, mode: TemperaColorMode, inversion: Boolean): Color {
    if (inversion) return if ((inputs.palette.firstOrNull() ?: inputs.accent).luminance() > .48f) Color(0xFF151218) else Color.White
    return when (mode) {
        TemperaColorMode.Duo -> if (shot.kind.variant and 1 == 0) Color.White else inputs.accent
        TemperaColorMode.Mono -> if (shot.lineIndex and 1 == 0) Color.White else Color(0xFFE2E2E2)
        TemperaColorMode.CoverGradient -> inputs.accent
    }
}

private fun positiveIndex(value: Long, size: Int): Int = (((value % size) + size) % size).toInt()

private const val BaseTypeSp = 34f
private const val MinTypeSp = 17f
private const val MaxTypeSp = 78f
private const val ReferenceStageDp = 411f
private const val MinStageScale = .72f
private const val MaxStageScale = 1.35f
private const val ProgramWindowStride = 8
