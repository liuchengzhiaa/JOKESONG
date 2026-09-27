package com.example.asmrplayer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import coil3.compose.AsyncImage
import com.example.asmrplayer.R
import com.example.asmrplayer.core.artwork.CoverAccentLoader
import com.example.asmrplayer.core.media.PlaybackCommand
import com.example.asmrplayer.core.media.PlaybackState
import com.example.asmrplayer.core.media.toggleCommandFor
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextLine
import com.example.asmrplayer.feature.player.formatPlaybackTime
import com.example.asmrplayer.feature.subtitles.ImmersiveLyricsFrame
import com.example.asmrplayer.feature.subtitles.LyricsVisualizerMode
import com.example.asmrplayer.feature.subtitles.LyricsWordGlowMode
import com.example.asmrplayer.feature.subtitles.immersiveLyricsFrame
import com.example.asmrplayer.feature.subtitles.immersiveLyricsFrameAt
import com.example.asmrplayer.feature.subtitles.lyricGlowTimeline
import com.example.asmrplayer.ui.theme.artworkAccent
import com.example.asmrplayer.ui.theme.artworkBrush
import kotlin.math.roundToInt

/**
 * One full-screen timed-lyric scene. It is deliberately a view over the existing
 * Media3 clock and parsed document: no player, parser or frame loop lives here.
 */
@Composable
internal fun ImmersiveTimedLyricsScene(
    item: LibraryItem,
    document: TimedTextDocument,
    sourceName: String?,
    languageTag: String?,
    playbackState: PlaybackState,
    motionPercent: Int,
    visualizerMode: LyricsVisualizerMode,
    wordGlowMode: LyricsWordGlowMode,
    onVisualizerModeChange: (LyricsVisualizerMode) -> Unit,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var extractedPalette by remember(item.artworkUri) { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(item.artworkUri) {
        extractedPalette = CoverAccentLoader.resolve(context, item.artworkUri)?.paletteArgbs.orEmpty()
    }
    val fallbackAccent = artworkAccent(item.paletteSeed)
    val palette = remember(extractedPalette, fallbackAccent) {
        extractedPalette.map(::Color).ifEmpty { listOf(fallbackAccent) }
    }
    val accent = lerp(palette.first(), Color.White, 0.28f)
    val positionMs = playbackState.positionMs
    val frame = remember(document, positionMs) { document.immersiveLyricsFrame(positionMs) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PlayerBackground),
    ) {
        ImmersiveLyricsBackdrop(item = item, palette = palette, mode = visualizerMode)

        ImmersiveLyricsHeader(
            item = item,
            sourceName = sourceName,
            languageTag = languageTag,
            visualizerMode = visualizerMode,
            onVisualizerModeChange = onVisualizerModeChange,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (frame == null) {
            // The first line has not started. Keeping it dim and unhighlighted is a
            // truthful preview rather than declaring that playback has reached it.
            document.lines.firstOrNull()?.let { first ->
                Text(
                    text = first.text,
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 34.dp),
                )
            }
        } else {
            ImmersiveLyricsTransition(
                document = document,
                frame = frame,
                positionMs = positionMs,
                format = document.format,
                accent = accent,
                mode = visualizerMode,
                wordGlowMode = wordGlowMode,
                isPlaying = playbackState.isPlaying,
                motionPercent = motionPercent,
                onSeekTo = onSeekTo,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 26.dp, top = 76.dp, end = 26.dp, bottom = 142.dp),
            )
        }

        ImmersiveLyricsTransport(
            playbackState = playbackState,
            accent = accent,
            onTogglePlayPause = onTogglePlayPause,
            onSeekTo = onSeekTo,
            onPrevious = onPrevious,
            onNext = onNext,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ImmersiveLyricsBackdrop(
    item: LibraryItem,
    palette: List<Color>,
    mode: LyricsVisualizerMode,
) {
    Box(Modifier.fillMaxSize()) {
        if (item.artworkUri != null) {
            AsyncImage(
                model = item.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = when (mode) {
                            LyricsVisualizerMode.Flow -> 1.12f
                            LyricsVisualizerMode.Poster -> 1.02f
                            LyricsVisualizerMode.Spotlight -> 1.18f
                        }
                        scaleX = scale
                        scaleY = scale
                        alpha = when (mode) {
                            LyricsVisualizerMode.Flow -> 0.72f
                            LyricsVisualizerMode.Poster -> 0.88f
                            LyricsVisualizerMode.Spotlight -> 0.54f
                        }
                    }
                    .blur(
                        when (mode) {
                            LyricsVisualizerMode.Flow -> 26.dp
                            LyricsVisualizerMode.Poster -> 2.dp
                            LyricsVisualizerMode.Spotlight -> 38.dp
                        },
                    ),
            )
        } else {
            Box(Modifier.fillMaxSize().background(artworkBrush(item.paletteSeed)))
        }

        // These brushes are rebuilt only when the focused artwork changes. There
        // is no infinite ambient animation and therefore no visualizer frame loop.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val first = palette.firstOrNull() ?: Color(0xFF315C72)
                    val second = palette.getOrElse(1) { first }
                    val third = palette.getOrElse(2) { second }
                    val upperGlow = Brush.radialGradient(
                        colors = listOf(first.copy(alpha = 0.50f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.18f),
                        radius = size.maxDimension * 0.70f,
                    )
                    val lowerGlow = Brush.radialGradient(
                        colors = listOf(second.copy(alpha = 0.36f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.78f),
                        radius = size.maxDimension * 0.72f,
                    )
                    val sideGlow = Brush.radialGradient(
                        colors = listOf(third.copy(alpha = 0.22f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.28f),
                        radius = size.maxDimension * 0.48f,
                    )
                    val shade = when (mode) {
                        LyricsVisualizerMode.Flow -> Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.54f),
                                Color.Black.copy(alpha = 0.63f),
                                Color.Black.copy(alpha = 0.88f),
                            ),
                        )
                        LyricsVisualizerMode.Poster -> Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.82f),
                                Color.Black.copy(alpha = 0.48f),
                                Color.Black.copy(alpha = 0.66f),
                            ),
                        )
                        LyricsVisualizerMode.Spotlight -> Brush.radialGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.36f),
                                Color.Black.copy(alpha = 0.88f),
                            ),
                            center = androidx.compose.ui.geometry.Offset(
                                size.width * 0.5f,
                                size.height * 0.48f,
                            ),
                            radius = size.maxDimension * 0.72f,
                        )
                    }
                    onDrawBehind {
                        drawRect(upperGlow)
                        drawRect(lowerGlow)
                        drawRect(sideGlow)
                        drawRect(shade)
                    }
                },
        )
    }
}

@Composable
private fun ImmersiveLyricsHeader(
    item: LibraryItem,
    sourceName: String?,
    languageTag: String?,
    visualizerMode: LyricsVisualizerMode,
    onVisualizerModeChange: (LyricsVisualizerMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.26f), CircleShape),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.back_to_player),
                tint = Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val source = sourceName?.let { name ->
                if (languageTag == null) name else "$name · $languageTag"
            } ?: item.subtitle
            Text(
                source,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VisualizerModePicker(
            selectedMode = visualizerMode,
            onModeSelected = onVisualizerModeChange,
        )
    }
}

@Composable
private fun VisualizerModePicker(
    selectedMode: LyricsVisualizerMode,
    onModeSelected: (LyricsVisualizerMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.26f), CircleShape),
        ) {
            Icon(
                Icons.Default.Animation,
                stringResource(R.string.lyrics_visualizer_choose),
                tint = Color.White,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LyricsVisualizerMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.labelResource())) },
                    onClick = {
                        expanded = false
                        onModeSelected(mode)
                    },
                    trailingIcon = {
                        if (mode == selectedMode) {
                            Icon(Icons.Default.Check, null)
                        }
                    },
                )
            }
        }
    }
}

private fun LyricsVisualizerMode.labelResource(): Int = when (this) {
    LyricsVisualizerMode.Flow -> R.string.lyrics_visualizer_flow
    LyricsVisualizerMode.Poster -> R.string.lyrics_visualizer_poster
    LyricsVisualizerMode.Spotlight -> R.string.lyrics_visualizer_spotlight
}

@Composable
private fun ImmersiveLyricsTransition(
    document: TimedTextDocument,
    frame: ImmersiveLyricsFrame,
    positionMs: Long,
    format: TimedTextFormat,
    accent: Color,
    mode: LyricsVisualizerMode,
    wordGlowMode: LyricsWordGlowMode,
    isPlaying: Boolean,
    motionPercent: Int,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = (380 * motionPercent.coerceIn(0, 100) / 100f).roundToInt()
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val compact = maxHeight < 300.dp
        if (duration == 0) {
            ImmersiveLyricsStack(
                frame = frame,
                format = format,
                positionMs = positionMs,
                isPlaying = isPlaying,
                accent = accent,
                compact = compact,
                mode = mode,
                wordGlowMode = wordGlowMode,
                motionPercent = motionPercent,
                onSeekTo = onSeekTo,
            )
        } else {
            AnimatedContent(
                targetState = frame.currentIndex,
                transitionSpec = {
                    lyricSceneTransition(
                        durationMillis = duration,
                        direction = if (targetState >= initialState) 1 else -1,
                        mode = mode,
                    )
                },
                label = "immersive lyric line",
            ) { targetIndex ->
                val targetFrame = remember(document, targetIndex, positionMs) {
                    document.immersiveLyricsFrameAt(targetIndex, positionMs)
                }
                if (targetFrame != null) {
                    ImmersiveLyricsStack(
                        targetFrame,
                        format,
                        positionMs,
                        isPlaying,
                        accent,
                        compact,
                        mode,
                        wordGlowMode,
                        motionPercent,
                        onSeekTo,
                    )
                }
            }
        }
    }
}

private fun lyricSceneTransition(
    durationMillis: Int,
    direction: Int,
    mode: LyricsVisualizerMode,
): ContentTransform = when (mode) {
    LyricsVisualizerMode.Flow ->
        (fadeIn(tween(durationMillis)) +
            slideInVertically(tween(durationMillis)) { direction * it / 12 })
            .togetherWith(
                fadeOut(tween(durationMillis)) +
                    slideOutVertically(tween(durationMillis)) { -direction * it / 12 },
            )
    LyricsVisualizerMode.Poster ->
        (fadeIn(tween(durationMillis)) +
            slideInHorizontally(tween(durationMillis)) { direction * it / 10 })
            .togetherWith(
                fadeOut(tween(durationMillis)) +
                    slideOutHorizontally(tween(durationMillis)) { -direction * it / 10 },
            )
    LyricsVisualizerMode.Spotlight ->
        (fadeIn(tween(durationMillis)) + scaleIn(tween(durationMillis), initialScale = 0.96f))
            .togetherWith(
                fadeOut(tween(durationMillis)) +
                    scaleOut(tween(durationMillis), targetScale = 1.04f),
            )
}

@Composable
private fun ImmersiveLyricsStack(
    frame: ImmersiveLyricsFrame,
    format: TimedTextFormat,
    positionMs: Long,
    isPlaying: Boolean,
    accent: Color,
    compact: Boolean,
    mode: LyricsVisualizerMode,
    wordGlowMode: LyricsWordGlowMode,
    motionPercent: Int,
    onSeekTo: (Long) -> Unit,
) {
    when (mode) {
        LyricsVisualizerMode.Flow ->
            FlowLyricsStack(
                frame,
                format,
                positionMs,
                isPlaying,
                accent,
                compact,
                wordGlowMode,
                motionPercent,
                onSeekTo,
            )
        LyricsVisualizerMode.Poster ->
            PosterLyricsStack(
                frame,
                format,
                positionMs,
                isPlaying,
                accent,
                compact,
                wordGlowMode,
                motionPercent,
                onSeekTo,
            )
        LyricsVisualizerMode.Spotlight ->
            SpotlightLyricsStack(
                frame,
                format,
                positionMs,
                isPlaying,
                accent,
                compact,
                wordGlowMode,
                motionPercent,
                onSeekTo,
            )
    }
}

@Composable
private fun FlowLyricsStack(
    frame: ImmersiveLyricsFrame,
    format: TimedTextFormat,
    positionMs: Long,
    isPlaying: Boolean,
    accent: Color,
    compact: Boolean,
    wordGlowMode: LyricsWordGlowMode,
    motionPercent: Int,
    onSeekTo: (Long) -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 760.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!compact) {
            ContextLyric(frame.previousFar, alpha = 0.18f, fontSize = 13, onSeekTo = onSeekTo)
        }
        ContextLyric(
            frame.previous,
            alpha = 0.40f,
            fontSize = if (compact) 14 else 17,
            height = if (compact) 36 else 52,
            onSeekTo = onSeekTo,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 88.dp else 132.dp)
                .clickable { onSeekTo(frame.current.startMillis) },
            contentAlignment = Alignment.Center,
        ) {
            GlowingCurrentLineText(
                frame = frame,
                format = format,
                positionMs = positionMs,
                isPlaying = isPlaying,
                accent = accent,
                wordGlowMode = wordGlowMode,
                motionPercent = motionPercent,
                fontSize = (if (compact) 24 else 30).sp,
                lineHeight = (if (compact) 30 else 38).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ContextLyric(
            frame.next,
            alpha = 0.46f,
            fontSize = if (compact) 14 else 17,
            height = if (compact) 36 else 52,
            onSeekTo = onSeekTo,
        )
        if (!compact) {
            ContextLyric(frame.nextFar, alpha = 0.20f, fontSize = 13, onSeekTo = onSeekTo)
        }
    }
}

@Composable
private fun PosterLyricsStack(
    frame: ImmersiveLyricsFrame,
    format: TimedTextFormat,
    positionMs: Long,
    isPlaying: Boolean,
    accent: Color,
    compact: Boolean,
    wordGlowMode: LyricsWordGlowMode,
    motionPercent: Int,
    onSeekTo: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 860.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        frame.previous?.let { line ->
            Text(
                line.text,
                color = Color.White.copy(alpha = 0.34f),
                fontSize = (if (compact) 13 else 17).sp,
                lineHeight = (if (compact) 18 else 23).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeekTo(line.startMillis) }
                    .padding(vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(if (compact) 8.dp else 18.dp))
        GlowingCurrentLineText(
            frame = frame,
            format = format,
            positionMs = positionMs,
            isPlaying = isPlaying,
            accent = accent,
            wordGlowMode = wordGlowMode,
            motionPercent = motionPercent,
            fontSize = (if (compact) 27 else 40).sp,
            lineHeight = (if (compact) 33 else 49).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            maxLines = if (compact) 2 else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSeekTo(frame.current.startMillis) }
                .padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
        frame.next?.let { line ->
            Text(
                line.text,
                color = Color.White.copy(alpha = 0.52f),
                fontSize = (if (compact) 14 else 20).sp,
                lineHeight = (if (compact) 19 else 27).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeekTo(line.startMillis) }
                    .padding(vertical = 6.dp),
            )
        }
        if (!compact) {
            frame.nextFar?.let { line ->
                Text(
                    line.text,
                    color = Color.White.copy(alpha = 0.20f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeekTo(line.startMillis) }
                        .padding(top = 10.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SpotlightLyricsStack(
    frame: ImmersiveLyricsFrame,
    format: TimedTextFormat,
    positionMs: Long,
    isPlaying: Boolean,
    accent: Color,
    compact: Boolean,
    wordGlowMode: LyricsWordGlowMode,
    motionPercent: Int,
    onSeekTo: (Long) -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 760.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlowingCurrentLineText(
            frame = frame,
            format = format,
            positionMs = positionMs,
            isPlaying = isPlaying,
            accent = accent,
            wordGlowMode = wordGlowMode,
            motionPercent = motionPercent,
            fontSize = (if (compact) 28 else 38).sp,
            lineHeight = (if (compact) 34 else 47).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = if (compact) 2 else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSeekTo(frame.current.startMillis) }
                .padding(vertical = 12.dp),
        )
        Box(
            Modifier
                .padding(vertical = if (compact) 8.dp else 18.dp)
                .size(width = 42.dp, height = 2.dp)
                .background(accent.copy(alpha = 0.84f), CircleShape),
        )
        frame.next?.let { line ->
            Text(
                line.text,
                color = Color.White.copy(alpha = 0.44f),
                fontSize = (if (compact) 13 else 17).sp,
                lineHeight = (if (compact) 18 else 23).sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeekTo(line.startMillis) }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ContextLyric(
    line: TimedTextLine?,
    alpha: Float,
    fontSize: Int,
    height: Int = 52,
    onSeekTo: (Long) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (line != null) {
            Text(
                text = line.text,
                color = Color.White.copy(alpha = alpha),
                fontSize = fontSize.sp,
                lineHeight = (fontSize + 6).sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeekTo(line.startMillis) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun GlowingCurrentLineText(
    frame: ImmersiveLyricsFrame,
    format: TimedTextFormat,
    positionMs: Long,
    isPlaying: Boolean,
    accent: Color,
    wordGlowMode: LyricsWordGlowMode,
    motionPercent: Int,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    textAlign: TextAlign,
    maxLines: Int,
    overflow: TextOverflow,
    modifier: Modifier = Modifier,
) {
    val line = frame.current
    val timeline = remember(line, frame.next?.startMillis, format, wordGlowMode) {
        line.lyricGlowTimeline(
            format = format,
            nextLineStartMillis = frame.next?.startMillis,
            mode = wordGlowMode,
        )
    }
    GlowingLyricText(
        text = line.text,
        timeline = timeline,
        positionMs = positionMs,
        isPlaying = isPlaying,
        accent = accent,
        motionPercent = motionPercent,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

@Composable
private fun ImmersiveLyricsTransport(
    playbackState: PlaybackState,
    accent: Color,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var scrubFraction by remember(playbackState.mediaId) { mutableStateOf<Float?>(null) }
    val shownFraction = scrubFraction ?: playbackState.progress

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.56f)),
                ),
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Slider(
            value = shownFraction,
            onValueChange = { scrubFraction = it },
            onValueChangeFinished = {
                scrubFraction?.let(playbackState::positionForFraction)?.let(onSeekTo)
                scrubFraction = null
            },
            enabled = playbackState.hasDuration,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatPlaybackTime(playbackState.positionForFraction(shownFraction)),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 11.sp,
            )
            Text(
                formatPlaybackTime(playbackState.durationMs),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 11.sp,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                Icon(
                    Icons.Default.SkipPrevious,
                    stringResource(R.string.previous_track),
                    tint = Color.White.copy(alpha = if (onPrevious != null) 0.86f else 0.30f),
                )
            }
            FilledIconButton(
                onClick = onTogglePlayPause,
                enabled = toggleCommandFor(playbackState) != PlaybackCommand.None,
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
            ) {
                Icon(
                    if (playbackState.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (playbackState.playWhenReady) R.string.pause else R.string.play),
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                Icon(
                    Icons.Default.SkipNext,
                    stringResource(R.string.next_track),
                    tint = Color.White.copy(alpha = if (onNext != null) 0.86f else 0.30f),
                )
            }
        }
    }
}
