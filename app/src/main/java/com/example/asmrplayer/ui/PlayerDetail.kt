package com.example.asmrplayer.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.asmrplayer.R
import com.example.asmrplayer.core.media.PlaybackState
import com.example.asmrplayer.core.metadata.LocalAudioMetadata
import com.example.asmrplayer.core.metadata.LocalAudioMetadataReader
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.feature.player.PlayerTimeline
import com.example.asmrplayer.feature.player.formatPlaybackTime
import com.example.asmrplayer.feature.player.parseDurationLabel
import com.example.asmrplayer.ui.theme.artworkBrush
import java.util.Locale

private const val SkipSeconds = 10

/**
 * The immersive player's own palette. It is deliberately not the theme's: the player
 * is near-black regardless of light or dark, and the subtitle surface that opens over
 * the same artwork reads the same colours, so these two are shared rather than
 * copied. `PlayerPrimaryText` stays private because only this file has a use for it.
 */
internal val PlayerBackground = Color(0xFF090A0D)
internal val PlayerSecondaryText = Color(0xFFAAAAB2)

private val PlayerPrimaryText = Color(0xFFF4F4F6)

private enum class PlayerPanel { Queue, Bookmarks, Chapters }

/**
 * Full player surface. Every control drives the shared Media3 session through
 * the callbacks; position and duration come from [playbackState] rather than
 * being modelled here.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerDetail(
    item: LibraryItem,
    queueItems: List<LibraryItem>,
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onQueueItemSelected: (LibraryItem) -> Unit,
    onArtworkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reader = remember(context) { LocalAudioMetadataReader(context.applicationContext) }
    var metadata by remember(item.id) {
        mutableStateOf(LocalAudioMetadata(durationMs = parseDurationLabel(item.durationLabel)))
    }
    var bookmarks by remember(item.id) { mutableStateOf(emptyList<Long>()) }
    var scrubFraction by remember(item.id) { mutableStateOf<Float?>(null) }
    var panel by remember(item.id) { mutableStateOf<PlayerPanel?>(null) }

    LaunchedEffect(item.id, item.documentUri) {
        metadata = reader.read(item.documentUri).let { loaded ->
            loaded.copy(durationMs = loaded.durationMs ?: parseDurationLabel(item.durationLabel))
        }
    }

    val isCurrent = playbackState.isCurrent(item.id)
    val durationMs = if (isCurrent && playbackState.hasDuration) {
        playbackState.durationMs
    } else {
        metadata.durationMs ?: 0L
    }
    val timeline = PlayerTimeline(
        durationMs = durationMs,
        positionMs = if (isCurrent) playbackState.positionMs else 0L,
        bookmarksMs = bookmarks,
    )
    val isPlaying = isCurrent && playbackState.isPlaying

    panel?.let { selectedPanel ->
        ModalBottomSheet(
            onDismissRequest = { panel = null },
            containerColor = Color(0xFF17181D),
            contentColor = PlayerPrimaryText,
        ) {
            PlayerPanelContent(
                panel = selectedPanel,
                currentMediaId = playbackState.mediaId,
                queueItems = queueItems,
                timeline = timeline,
                onAddBookmark = { bookmarks = timeline.addBookmark().bookmarksMs },
                onRemoveBookmark = { bookmarks = timeline.removeBookmark(it).bookmarksMs },
                onSeekTo = onSeekTo,
                onQueueItemSelected = {
                    panel = null
                    onQueueItemSelected(it)
                },
            )
        }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(PlayerBackground)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                PowerampArtwork(
                    item = item,
                    onClick = onArtworkClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 650.dp)
                        .aspectRatio(1f),
                )
            }
            PlayerControls(
                item = item,
                metadata = metadata,
                timeline = timeline,
                playbackState = playbackState,
                queueItems = queueItems,
                isCurrent = isCurrent,
                isPlaying = isPlaying,
                scrubFraction = scrubFraction,
                onScrub = { scrubFraction = it },
                onScrubFinished = {
                    scrubFraction?.let { fraction ->
                        onSeekTo(timeline.seekToProgress(fraction).positionMs)
                    }
                    scrubFraction = null
                },
                onPlayPause = onTogglePlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeekBy = onSeekBy,
                onPanelSelected = { panel = it },
                modifier = Modifier
                    .weight(0.92f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(PlayerBackground)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PowerampArtwork(
                item = item,
                onClick = onArtworkClick,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            PlayerControls(
                item = item,
                metadata = metadata,
                timeline = timeline,
                playbackState = playbackState,
                queueItems = queueItems,
                isCurrent = isCurrent,
                isPlaying = isPlaying,
                scrubFraction = scrubFraction,
                onScrub = { scrubFraction = it },
                onScrubFinished = {
                    scrubFraction?.let { fraction ->
                        onSeekTo(timeline.seekToProgress(fraction).positionMs)
                    }
                    scrubFraction = null
                },
                onPlayPause = onTogglePlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeekBy = onSeekBy,
                onPanelSelected = { panel = it },
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PowerampArtwork(
    item: LibraryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(artworkBrush(item.paletteSeed))
            .clickable(onClick = onClick),
    ) {
        if (item.artworkUri != null) {
            AsyncImage(
                model = item.artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, Color(0xA6000000)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(Color(0xB8000000), CircleShape)
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.Subtitles, null, tint = Color.White, modifier = Modifier.size(17.dp))
            Text(
                stringResource(R.string.tap_artwork_for_subtitles),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun PlayerControls(
    item: LibraryItem,
    metadata: LocalAudioMetadata,
    timeline: PlayerTimeline,
    playbackState: PlaybackState,
    queueItems: List<LibraryItem>,
    isCurrent: Boolean,
    isPlaying: Boolean,
    scrubFraction: Float?,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onSeekBy: (Long) -> Unit,
    onPanelSelected: (PlayerPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val queueIndex = queueItems.indexOfFirst { it.id == item.id }
    Column(modifier = modifier) {
        Text(
            item.title,
            color = PlayerPrimaryText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            metadata.artist ?: stringResource(R.string.unknown_artist),
            color = PlayerSecondaryText,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        metadata.album?.let { album ->
            Text(
                album,
                color = PlayerSecondaryText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(13.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                item.subtitle.uppercase(Locale.ROOT),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
            if (queueIndex >= 0) {
                Text(
                    stringResource(R.string.queue_position, queueIndex + 1, queueItems.size),
                    color = PlayerSecondaryText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        val shownProgress = scrubFraction ?: timeline.progress
        Slider(
            value = shownProgress,
            onValueChange = { if (isCurrent) onScrub(it) },
            onValueChangeFinished = onScrubFinished,
            enabled = timeline.durationMs > 0L && isCurrent,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color(0xFF3C3D43),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatPlaybackTime(timeline.seekToProgress(shownProgress).positionMs),
                color = PlayerSecondaryText,
            )
            Text(
                if (timeline.durationMs > 0L) {
                    formatPlaybackTime(timeline.durationMs)
                } else {
                    stringResource(R.string.unknown_duration)
                },
                color = PlayerSecondaryText,
            )
        }
        if (!isCurrent) {
            Text(
                stringResource(R.string.player_not_current_item),
                color = PlayerSecondaryText,
                style = MaterialTheme.typography.labelSmall,
            )
        } else if (playbackState.errorMessage != null) {
            Text(
                stringResource(R.string.player_error, playbackState.errorMessage),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous_track), tint = PlayerPrimaryText)
            }
            IconButton(
                onClick = { onSeekBy(-SkipSeconds * 1_000L) },
                enabled = isCurrent && timeline.durationMs > 0L,
            ) {
                Icon(Icons.Default.Replay10, stringResource(R.string.skip_back_seconds, SkipSeconds), tint = PlayerPrimaryText)
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = isCurrent || playbackState.hasQueue,
                modifier = Modifier.size(68.dp),
                shape = CircleShape,
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (isPlaying) R.string.pause else R.string.play),
                    Modifier.size(35.dp),
                )
            }
            IconButton(
                onClick = { onSeekBy(SkipSeconds * 1_000L) },
                enabled = isCurrent && timeline.durationMs > 0L,
            ) {
                Icon(Icons.Default.Forward10, stringResource(R.string.skip_forward_seconds, SkipSeconds), tint = PlayerPrimaryText)
            }
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                Icon(Icons.Default.SkipNext, stringResource(R.string.next_track), tint = PlayerPrimaryText)
            }
        }

        Spacer(Modifier.height(17.dp))
        HorizontalDivider(color = Color(0xFF2A2B30))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            PlayerTool(
                icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                label = stringResource(R.string.queue),
                detail = queueItems.size.toString(),
                onClick = { onPanelSelected(PlayerPanel.Queue) },
            )
            PlayerTool(
                icon = { Icon(Icons.Default.Bookmark, null) },
                label = stringResource(R.string.bookmarks),
                detail = timeline.bookmarksMs.size.toString(),
                onClick = { onPanelSelected(PlayerPanel.Bookmarks) },
            )
            PlayerTool(
                icon = { Icon(Icons.Default.ViewList, null) },
                label = stringResource(R.string.chapters),
                detail = stringResource(R.string.not_available_short),
                onClick = { onPanelSelected(PlayerPanel.Chapters) },
            )
        }
    }
}

@Composable
private fun PlayerTool(
    icon: @Composable () -> Unit,
    label: String,
    detail: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(3.dp))
        Text(label, color = PlayerPrimaryText, style = MaterialTheme.typography.labelMedium)
        Text(detail, color = PlayerSecondaryText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PlayerPanelContent(
    panel: PlayerPanel,
    currentMediaId: String?,
    queueItems: List<LibraryItem>,
    timeline: PlayerTimeline,
    onAddBookmark: () -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onQueueItemSelected: (LibraryItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 540.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 30.dp),
    ) {
        Text(
            stringResource(
                when (panel) {
                    PlayerPanel.Queue -> R.string.queue
                    PlayerPanel.Bookmarks -> R.string.bookmarks
                    PlayerPanel.Chapters -> R.string.chapters
                },
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        when (panel) {
            PlayerPanel.Queue -> queueItems.forEachIndexed { index, queueItem ->
                val isCurrent = queueItem.id == currentMediaId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onQueueItemSelected(queueItem) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text((index + 1).toString(), Modifier.width(34.dp), color = PlayerSecondaryText)
                    Icon(
                        Icons.Default.LibraryMusic,
                        null,
                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else PlayerSecondaryText,
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            queueItem.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isCurrent) FontWeight.Bold else null,
                        )
                        Text(queueItem.subtitle.uppercase(Locale.ROOT), color = PlayerSecondaryText)
                    }
                }
                HorizontalDivider(color = Color(0xFF303137))
            }
            PlayerPanel.Bookmarks -> {
                Button(
                    onClick = onAddBookmark,
                    enabled = timeline.durationMs > 0L,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.BookmarkAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_bookmark_at, formatPlaybackTime(timeline.positionMs)))
                }
                Spacer(Modifier.height(10.dp))
                if (timeline.bookmarksMs.isEmpty()) {
                    PanelMessage(stringResource(R.string.no_bookmarks))
                } else {
                    timeline.bookmarksMs.forEach { bookmark ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeekTo(bookmark) }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(formatPlaybackTime(bookmark), Modifier.weight(1f))
                            IconButton(onClick = { onRemoveBookmark(bookmark) }) {
                                Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete_bookmark))
                            }
                        }
                    }
                }
            }
            PlayerPanel.Chapters -> PanelMessage(stringResource(R.string.no_embedded_chapters))
        }
    }
}

@Composable
private fun PanelMessage(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 26.dp),
        color = PlayerSecondaryText,
        textAlign = TextAlign.Center,
    )
}

// The subtitle surface lives in ui/SubtitleDetail.kt. It keeps the immersive
// player's palette, so the two constants above are shared rather than duplicated.
