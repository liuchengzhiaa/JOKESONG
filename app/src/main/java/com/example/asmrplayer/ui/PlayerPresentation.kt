package com.example.asmrplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.example.asmrplayer.core.card.CardLayerGeometry
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asmrplayer.R
import com.example.asmrplayer.core.storage.AppPreferences
import com.example.asmrplayer.core.storage.AppSettings
import com.example.asmrplayer.feature.subtitles.SubtitleDetailState
import com.example.asmrplayer.feature.subtitles.LyricsView
import com.example.asmrplayer.feature.subtitles.lyricsView
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Decoder dimensions (including orientation) win; colour extraction must not gate shape. */
internal fun playerArtworkAspect(decoded: Float?, sampled: Float?): Float =
    decoded?.takeIf { it.isFinite() && it > 0f }
        ?: sampled?.takeIf { it.isFinite() && it > 0f } ?: 1f

/** Full artwork fit. Neither the source proportions nor the viewport may be violated. */
internal fun fitPlayerArtwork(width: Float, height: Float, preferredWidth: Float, aspect: Float, percent: Int): Pair<Float, Float> {
    val ratio = aspect.takeIf { it.isFinite() && it > 0f } ?: 1f
    val w = minOf(preferredWidth * percent.coerceIn(70, 115) / 100f, width * 0.96f, height * 0.88f * ratio).coerceAtLeast(0f)
    return w to w / ratio
}

/** Actual artwork bottom, not its larger allocation, anchors the three-line window. */
internal fun coverPageVerticalPlacement(
    height: Int, artworkHeight: Int, lyricsHeight: Int, progressOffset: Int,
): Pair<Int, Int> {
    val artworkTop = ((height - lyricsHeight - artworkHeight) / 2).coerceAtLeast(0)
    val artworkBottom = artworkTop + artworkHeight
    val lyricsTop = ((artworkBottom + height + progressOffset - lyricsHeight) / 2)
        .coerceIn(artworkBottom.coerceAtMost(height - lyricsHeight), height - lyricsHeight)
    return artworkTop to lyricsTop
}

/** Measure once per layout, retaining intrinsic art proportions and the existing cover scale. */
@Composable
internal fun PlayerCoverPage(
    showingCard: Boolean,
    artworkAspect: Float,
    coverPercent: Int,
    pair: LyricPair,
    lyricsPercent: Int,
    artwork: @Composable (Modifier) -> Unit,
) {
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box { artwork(Modifier.fillMaxSize()) }
            Box { PlayerCoverLyrics(pair, lyricsPercent) }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val lyrics = measurables[1].measure(Constraints(
            minWidth = width, maxWidth = width, maxHeight = height / 2,
        ))
        val availableHeight = (height - lyrics.height).coerceAtLeast(0)
        val dimensions = if (showingCard) {
            val aspect = CardLayerGeometry.CanvasAspect
            val h = minOf(availableHeight * 0.86f, width * 0.86f / aspect, 520.dp.toPx())
            h * aspect to h
        } else fitPlayerArtwork(width.toFloat(), availableHeight.toFloat(), width * 0.85f, artworkAspect, coverPercent)
        val art = measurables[0].measure(Constraints.fixed(
            dimensions.first.roundToInt().coerceIn(0, width),
            dimensions.second.roundToInt().coerceIn(0, availableHeight),
        ))
        // The page ends 14 dp before a 22 dp seek touch band: its line is 25 dp away.
        val (artY, lyricsY) = coverPageVerticalPlacement(height, art.height, lyrics.height, 25.dp.roundToPx())
        layout(width, height) {
            art.placeRelative((width - art.width) / 2, artY)
            lyrics.placeRelative(0, lyricsY)
        }
    }
}

/** Three-slot reading window, independent of paragraph length and with stable height. */
@Composable
internal fun PlayerCoverLyrics(pair: LyricPair, percent: Int) {
    if (pair.isEmpty) return
    Column(Modifier.fillMaxWidth().heightIn(min = 112.dp, max = 164.dp)
        .padding(horizontal = 28.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
        listOf(pair.previous, pair.current, pair.next).forEachIndexed { index, text ->
            Text(text.orEmpty(), color = Color.White.copy(alpha = if (index == 1) 1f else 0.38f),
                fontSize = (14f * percent / 100).sp, lineHeight = (20f * percent / 100).sp,
                fontWeight = if (index == 1) FontWeight.Medium else FontWeight.Normal,
                maxLines = if (index == 1) 2 else 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
        }
    }
}

/** A real pageable lyric surface, sharing the cover page's header, backdrop and controls. */
@Composable
internal fun PlayerPlainLyrics(subtitles: SubtitleDetailState, itemId: String, positionMs: Long?, textPercent: Int, onSeekTo: ((Long) -> Unit)?) {
    val view = if (subtitles.describes(itemId)) subtitles.lyricsView() else LyricsView.Loading
    val timed = (view as? LyricsView.Timed)?.document
    val lines = remember(view) { timed?.lines?.map { it.text } ?: (view as? LyricsView.Untimed)?.text.orEmpty() }
    val current = positionMs?.let { timed?.lineIndexAt(it) } ?: -1
    val state = rememberLazyListState()
    var following by remember(itemId) { mutableStateOf(true) }
    // Auto-follow scrolls don't count as a finger taking over the list.
    LaunchedEffect(state) {
        state.interactionSource.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.DragInteraction.Start) following = false
        }
    }
    LaunchedEffect(current, following) {
        if (following && current >= 0) state.animateScrollToItem((current - 1).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        if (lines.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (view == LyricsView.Loading) CircularProgressIndicator()
            else Text(when (view) {
                is LyricsView.EmptyFile -> stringResource(R.string.subtitle_empty_file, view.sourceName.orEmpty())
                is LyricsView.NoFile -> if (view.sourceName == null) stringResource(R.string.subtitle_no_match)
                    else stringResource(R.string.subtitle_unreadable, view.sourceName)
                else -> stringResource(R.string.subtitle_no_match)
            }, color = PlayerPrimaryText.copy(alpha = 0.6f))
        } else Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.06f to Color.Black,
                            0.88f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                    },
                contentPadding = PaddingValues(top = 32.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                itemsIndexed(lines) { index, text ->
                    Text(text, fontSize = (18f * textPercent / 100).sp,
                        lineHeight = (25f * textPercent / 100).sp,
                        fontWeight = if (index == current) FontWeight.Medium else FontWeight.Normal,
                        color = PlayerPrimaryText.copy(alpha = if (index == current) 1f else 0.38f),
                        modifier = Modifier.fillMaxWidth().then(if (timed != null && onSeekTo != null)
                            Modifier.clickable { onSeekTo(timed.lines[index].startMillis) } else Modifier))
                }
            }
            if (!following && current >= 0) TextButton(onClick = { following = true }, modifier = Modifier.align(Alignment.BottomEnd)) {
                Text(stringResource(R.string.subtitle_return_to_current))
            }
        }
        Text(stringResource(R.string.player_lyric_source, subtitles.sourceName.takeIf { subtitles.describes(itemId) } ?: stringResource(R.string.player_lyrics)),
            color = PlayerPrimaryText.copy(alpha = 0.65f), fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
internal fun PlayerSizingSettings(settings: AppSettings, onChange: (Int, Int, Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(stringResource(R.string.player_sizing), style = MaterialTheme.typography.titleMedium)
        SizingSlider(R.string.player_cover_size, settings.playerCoverPercent, 70f..115f) { onChange(it, settings.playerTitlePercent, settings.playerLyricsPercent) }
        SizingSlider(R.string.player_title_size, settings.playerTitlePercent, 75f..140f) { onChange(settings.playerCoverPercent, it, settings.playerLyricsPercent) }
        SizingSlider(R.string.player_lyric_size, settings.playerLyricsPercent, 75f..150f) { onChange(settings.playerCoverPercent, settings.playerTitlePercent, it) }
        TextButton(onClick = { onChange(100, 100, 100) }) { Text(stringResource(R.string.player_sizing_reset)) }
    }
}

@Composable
private fun SizingSlider(label: Int, stored: Int, range: ClosedFloatingPointRange<Float>, onCommit: (Int) -> Unit) {
    var preview by remember(stored) { mutableFloatStateOf(stored.toFloat()) }
    Text(stringResource(R.string.player_sizing_value, stringResource(label), preview.roundToInt()))
    Slider(value = preview, onValueChange = { preview = it }, valueRange = range,
        onValueChangeFinished = { onCommit(preview.roundToInt()) })
}

@Composable
internal fun PlayerSizingPreferences() {
    val context = LocalContext.current
    val prefs = remember(context) { AppPreferences(context.applicationContext) }
    val settings by prefs.settings.collectAsState(AppSettings())
    val scope = rememberCoroutineScope()
    PlayerSizingSettings(settings) { cover, title, lyrics -> scope.launch { prefs.setPlayerSizing(cover, title, lyrics) } }
}
