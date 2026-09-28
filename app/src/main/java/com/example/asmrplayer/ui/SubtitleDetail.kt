package com.example.asmrplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.asmrplayer.R
import com.example.asmrplayer.core.media.PlaybackCommand
import com.example.asmrplayer.core.media.PlaybackState
import com.example.asmrplayer.core.media.toggleCommandFor
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.subtitles.TimedTextLine
import com.example.asmrplayer.feature.player.formatPlaybackTime
import com.example.asmrplayer.feature.subtitles.LuminousTuning
import com.example.asmrplayer.feature.subtitles.PendoloTuning
import com.example.asmrplayer.feature.subtitles.TemperaTuning
import com.example.asmrplayer.feature.subtitles.LyricsFollowing
import com.example.asmrplayer.feature.subtitles.LyricsView
import com.example.asmrplayer.feature.subtitles.LyricsVisualizerMode
import com.example.asmrplayer.feature.subtitles.LyricsWordGlowMode
import com.example.asmrplayer.feature.subtitles.SubtitleDetailState
import com.example.asmrplayer.feature.subtitles.lyricsView
import com.example.asmrplayer.ui.lyrics.ImmersiveTimedLyricsScene
import com.example.asmrplayer.ui.theme.artworkBrush

/**
 * Full-screen lyrics over the artwork, opened by tapping the player's cover.
 *
 * What this screen may say is decided entirely by [SubtitleDetailState], and it is
 * careful about the difference between the five things that can be true: still
 * reading, no file, a file with nothing in it, a file with words but no times, and a
 * file that actually follows the audio. Only the last one moves on its own. A file
 * without timestamps is shown whole and said to be static, because a lyric display
 * that pretends to follow playback is worse than one that admits it cannot.
 */
@Composable
fun SubtitleArtworkDetail(
    item: LibraryItem,
    subtitles: SubtitleDetailState,
    playbackState: PlaybackState,
    motionPercent: Int,
    visualizerMode: LyricsVisualizerMode,
    wordGlowMode: LyricsWordGlowMode,
    backgroundOrdinal: Int?,
    luminousTuning: LuminousTuning,
    pendoloTuning: PendoloTuning,
    temperaTuning: TemperaTuning,
    onVisualizerModeChange: (LyricsVisualizerMode) -> Unit,
    onBackgroundChange: (Int?) -> Unit,
    onMotionPercentChange: (Int) -> Unit,
    onWordGlowModeChange: (LyricsWordGlowMode) -> Unit,
    onLuminousTuningChange: (LuminousTuning) -> Unit,
    onPendoloTuningChange: (PendoloTuning) -> Unit,
    onTemperaTuningChange: (TemperaTuning) -> Unit,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // A state that belongs to the track that was playing a moment ago is not this
    // track's lyrics; the load is already on its way, so this is the reading state.
    val view = if (subtitles.describes(item.id)) subtitles.lyricsView() else LyricsView.Loading
    // Position is only this track's position when the session is on this track. The
    // player can outlive its queue -- stepping into a track the queue does not hold
    // leaves the session playing something else -- and following the wrong clock
    // would highlight the wrong line for the whole track.
    val positionMs = playbackState.positionMs.takeIf { playbackState.isCurrent(item.id) }

    // The immersive scene is intentionally reserved for a timed document following
    // this exact Media3 item. Everything else keeps the proven reader and its honest
    // loading/no-file/untimed states instead of fabricating a visualizer timeline.
    if (view is LyricsView.Timed && positionMs != null) {
        ImmersiveTimedLyricsScene(
            item = item,
            document = view.document,
            playbackState = playbackState,
            motionPercent = motionPercent,
            visualizerMode = visualizerMode,
            wordGlowMode = wordGlowMode,
            backgroundStoredOrdinal = backgroundOrdinal,
            luminousTuning = luminousTuning,
            pendoloTuning = pendoloTuning,
            temperaTuning = temperaTuning,
            onVisualizerModeChange = onVisualizerModeChange,
            onBackgroundChange = onBackgroundChange,
            onMotionPercentChange = onMotionPercentChange,
            onWordGlowModeChange = onWordGlowModeChange,
            onLuminousTuningChange = onLuminousTuningChange,
            onPendoloTuningChange = onPendoloTuningChange,
            onTemperaTuningChange = onTemperaTuningChange,
            onTogglePlayPause = onTogglePlayPause,
            onSeekTo = onSeekTo,
            onPrevious = onPrevious,
            onNext = onNext,
            modifier = modifier,
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PlayerBackground),
    ) {
        ArtworkBackdrop(item, dimmed = view is LyricsView.Timed || view is LyricsView.Untimed)

        Column(modifier = Modifier.fillMaxSize()) {
            SubtitleDetailHeader(item.title, onBack)
            SubtitleSourceCaption(subtitles, view)
            Box(Modifier.weight(1f)) {
                when (view) {
                    LyricsView.Loading -> Notice(
                        title = stringResource(R.string.subtitle_reading),
                        icon = null,
                        showProgress = true,
                    )
                    is LyricsView.NoFile -> Notice(
                        title = view.sourceName
                            ?.let { stringResource(R.string.subtitle_unreadable, it) }
                            ?: stringResource(R.string.subtitle_no_match),
                        icon = Icons.Default.Subtitles,
                    )
                    is LyricsView.EmptyFile -> Notice(
                        title = stringResource(
                            R.string.subtitle_empty_file,
                            view.sourceName ?: item.title,
                        ),
                        icon = Icons.Default.Subtitles,
                    )
                    is LyricsView.Timed -> TimedLyrics(
                        view = view,
                        itemId = item.id,
                        positionMs = positionMs,
                        onSeekToLine = onSeekTo.takeIf { positionMs != null },
                    )
                    is LyricsView.Untimed -> UntimedLyrics(view.text)
                }
            }
            PlaybackFooter(
                playbackState = playbackState,
                onTogglePlayPause = onTogglePlayPause,
                isCurrentItem = positionMs != null,
            )
        }
    }
}

@Composable
private fun SubtitleDetailHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.28f), CircleShape),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.back_to_player),
                tint = Color.White,
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.size(44.dp))
    }
}

/** The artwork as a backdrop, flattened harder when there is text to read over it. */
@Composable
private fun ArtworkBackdrop(item: LibraryItem, dimmed: Boolean) {
    Box(Modifier.fillMaxSize()) {
        if (item.artworkUri != null) {
            AsyncImage(
                model = item.artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(Modifier.fillMaxSize().background(artworkBrush(item.paletteSeed)))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (dimmed) {
                            listOf(Color(0xE6000000), Color(0xB3000000), Color(0xF2000000))
                        } else {
                            listOf(Color(0x22000000), Color.Transparent, Color(0xD9000000))
                        },
                    ),
                ),
        )
    }
}

/**
 * Which file was matched, shown so the reader can tell `Track.zh.lrc` from
 * `Track.en.srt` without leaving the app. Nothing else can answer that question.
 */
@Composable
private fun SubtitleSourceCaption(subtitles: SubtitleDetailState, view: LyricsView) {
    val name = subtitles.sourceName ?: return
    // When there is nothing to show, the notice below already names the file.
    if (view !is LyricsView.Timed && view !is LyricsView.Untimed) return

    val languageTag = subtitles.languageTag
    Text(
        text = if (languageTag == null) {
            stringResource(R.string.subtitle_source, name)
        } else {
            stringResource(R.string.subtitle_source_language, name, languageTag)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        color = PlayerSecondaryText,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Notice(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    showProgress: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showProgress) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.height(16.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
        }
        Text(title, color = Color.White, textAlign = TextAlign.Center)
    }
}

/**
 * The lyric list, following playback until the reader scrolls it away.
 *
 * The current line is not pinned to the exact middle: the list is anchored a few
 * lines earlier so the line being sung sits below the top with what comes next
 * already on screen. Where the highlight sits exactly is a question for the visual
 * layer that will draw over this one.
 */
@Composable
private fun TimedLyrics(
    view: LyricsView.Timed,
    itemId: String,
    positionMs: Long?,
    onSeekToLine: ((Long) -> Unit)?,
) {
    val lines = view.document.lines
    val listState = rememberLazyListState()
    var isFollowing by remember(itemId) { mutableStateOf(true) }
    // True only while the effect below is the one moving the list. A scroll the list
    // started itself must not count as the reader taking over, and the two are
    // indistinguishable from `isScrollInProgress` alone.
    val followingScroll = remember(itemId) { mutableStateOf(false) }
    val currentIndex = positionMs?.let(view.document::lineIndexAt) ?: -1

    LaunchedEffect(currentIndex, isFollowing) {
        if (!LyricsFollowing.shouldFollow(currentIndex, isFollowing)) return@LaunchedEffect
        followingScroll.value = true
        try {
            listState.animateScrollToItem(
                LyricsFollowing.anchorIndex(currentIndex, lines.size),
            )
        } finally {
            followingScroll.value = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !followingScroll.value) isFollowing = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                LyricLine(
                    line = line,
                    isCurrent = index == currentIndex,
                    onClick = onSeekToLine?.let { seek -> { seek(line.startMillis) } },
                )
            }
        }

        if (!isFollowing && positionMs != null && currentIndex >= 0) {
            FilledTonalButton(
                onClick = { isFollowing = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            ) {
                Icon(Icons.Default.VerticalAlignCenter, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.subtitle_return_to_current))
            }
        }
    }
}

@Composable
private fun LyricLine(line: TimedTextLine, isCurrent: Boolean, onClick: (() -> Unit)?) {
    Text(
        text = line.text,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 28.dp, vertical = 4.dp),
        color = if (isCurrent) Color.White else PlayerSecondaryText,
        fontSize = if (isCurrent) 19.sp else 17.sp,
        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
    )
}

/** Words with no timeline: shown whole, and said to be static. */
@Composable
private fun UntimedLyrics(text: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
    ) {
        Text(
            stringResource(R.string.subtitle_untimed_notice),
            modifier = Modifier.padding(bottom = 16.dp),
            color = PlayerSecondaryText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        text.forEach { line ->
            Text(
                text = line,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                color = Color.White,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Keeps the transport on this screen so the timing can be checked without leaving it:
 * verifying that a lyric file is in sync means seeking around while reading it.
 */
@Composable
private fun PlaybackFooter(
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    isCurrentItem: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        if (!isCurrentItem) {
            Text(
                stringResource(R.string.subtitle_not_current),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                color = PlayerSecondaryText,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onTogglePlayPause,
                enabled = toggleCommandFor(playbackState) != PlaybackCommand.None,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    if (playbackState.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(
                        if (playbackState.playWhenReady) R.string.pause else R.string.play,
                    ),
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = "${formatPlaybackTime(playbackState.positionMs)} / " +
                    formatPlaybackTime(playbackState.durationMs),
                color = PlayerSecondaryText,
                fontSize = 13.sp,
            )
        }
    }
}
