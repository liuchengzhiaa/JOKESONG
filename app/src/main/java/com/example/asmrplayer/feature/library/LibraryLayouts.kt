package com.example.asmrplayer.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewComfy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.asmrplayer.R
import com.example.asmrplayer.core.media.PlaybackState
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import com.example.asmrplayer.feature.folderSummaryLine
import com.example.asmrplayer.ui.theme.artworkBrush

/**
 * Detailed list: largest covers, two-line titles and full secondary detail.
 * Every mode below is a pure projection of the already sorted folder listing, so
 * switching between them changes nothing but presentation.
 */
@Composable
fun DetailedLibraryList(
    items: List<LibraryItem>,
    playbackState: PlaybackState,
    onOpen: (LibraryItem) -> Unit,
    onPlay: (LibraryItem) -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(items = items, key = LibraryItem::id) { item ->
            DetailedRow(
                item = item,
                playbackState = playbackState,
                onOpen = { onOpen(item) },
                onPlay = { onPlay(item) },
                onTogglePlayPause = onTogglePlayPause,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

/** Compact list: one dense line per item, for fast scrolling of large folders. */
@Composable
fun CompactLibraryList(
    items: List<LibraryItem>,
    playbackState: PlaybackState,
    onOpen: (LibraryItem) -> Unit,
    onPlay: (LibraryItem) -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(items = items, key = LibraryItem::id) { item ->
            CompactRow(
                item = item,
                playbackState = playbackState,
                onOpen = { onOpen(item) },
                onPlay = { onPlay(item) },
                onTogglePlayPause = onTogglePlayPause,
            )
        }
    }
}

/** Cover grid: square artwork with the title beneath, adaptive to the viewport. */
@Composable
fun LibraryGrid(
    items: List<LibraryItem>,
    playbackState: PlaybackState,
    onOpen: (LibraryItem) -> Unit,
    onPlay: (LibraryItem) -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 132.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items = items, key = LibraryItem::id) { item ->
            GridCell(
                item = item,
                playbackState = playbackState,
                onOpen = { onOpen(item) },
                onPlay = { onPlay(item) },
                onTogglePlayPause = onTogglePlayPause,
            )
        }
    }
}

/** Switching affordance shared by all library presentation modes. */
@Composable
fun LibraryLayoutSwitcher(
    selected: LibraryLayoutMode,
    onSelect: (LibraryLayoutMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = selected.icon(),
                    contentDescription = stringResource(
                        R.string.layout_current,
                        stringResource(selected.labelRes),
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LibraryLayoutMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.labelRes)) },
                    onClick = {
                        expanded = false
                        if (mode != selected) onSelect(mode)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = mode.icon(),
                            contentDescription = null,
                            tint = if (mode == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    trailingIcon = if (mode == selected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.layout_selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailedRow(
    item: LibraryItem,
    playbackState: PlaybackState,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onTogglePlayPause: () -> Unit,
) {
    val isCurrent = playbackState.isCurrent(item.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryThumbnail(item = item, size = 54.dp, isPlaying = isCurrent && playbackState.isPlaying)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.Bold else null,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.listDetail(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.durationLabel?.let { label ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlayPauseAction(
            item = item,
            playbackState = playbackState,
            onPlay = onPlay,
            onTogglePlayPause = onTogglePlayPause,
        )
    }
}

@Composable
private fun CompactRow(
    item: LibraryItem,
    playbackState: PlaybackState,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onTogglePlayPause: () -> Unit,
) {
    val isCurrent = playbackState.isCurrent(item.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.type.icon(),
            contentDescription = stringResource(
                if (item.type == LibraryItemType.Folder) R.string.folder_type else R.string.audio_type,
            ),
            modifier = Modifier.size(18.dp),
            tint = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = item.title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else null,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        item.durationLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlayPauseAction(
            item = item,
            playbackState = playbackState,
            onPlay = onPlay,
            onTogglePlayPause = onTogglePlayPause,
            compact = true,
        )
    }
}

@Composable
private fun GridCell(
    item: LibraryItem,
    playbackState: PlaybackState,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onTogglePlayPause: () -> Unit,
) {
    val isCurrent = playbackState.isCurrent(item.id)
    Column(modifier = Modifier.clickable(onClick = onOpen)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(artworkBrush(item.paletteSeed)),
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
                            0f to Color(0x00000000),
                            1f to Color(0xB305070B),
                        ),
                    ),
            )
            Icon(
                imageVector = item.type.icon(),
                contentDescription = stringResource(
                    if (item.type == LibraryItemType.Folder) R.string.folder_type else R.string.audio_type,
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .size(24.dp)
                    .background(Color(0xB814171D), CircleShape)
                    .padding(5.dp),
                tint = Color.White,
            )
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = stringResource(R.string.now_playing),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(24.dp)
                        .background(Color(0xD014171D), CircleShape)
                        .padding(5.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            // Only audio gets a button. A folder listing never contains the folder
            // itself as a playable track, and the whole cell already opens it, so a
            // folder cover carries no affordance glyph of its own.
            if (item.type != LibraryItemType.Folder) {
                IconButton(
                    onClick = { if (isCurrent) onTogglePlayPause() else onPlay() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(34.dp)
                        .background(Color(0xB314171D), CircleShape),
                ) {
                    Icon(
                        imageVector = if (isCurrent && playbackState.isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = stringResource(
                            if (isCurrent && playbackState.isPlaying) R.string.pause else R.string.play,
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else null,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = item.listDetail(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LibraryThumbnail(
    item: LibraryItem,
    size: Dp,
    isPlaying: Boolean,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(artworkBrush(item.paletteSeed)),
        contentAlignment = Alignment.Center,
    ) {
        if (item.artworkUri != null) {
            AsyncImage(
                model = item.artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = item.type.icon(),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size / 2),
            )
        }
        if (isPlaying) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x8C000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = stringResource(R.string.now_playing),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PlayPauseAction(
    item: LibraryItem,
    playbackState: PlaybackState,
    onPlay: () -> Unit,
    onTogglePlayPause: () -> Unit,
    compact: Boolean = false,
) {
    // A folder owns no playable queue of its own -- the folder listing excludes
    // child folders -- so it gets no trailing action at all rather than a play
    // button that could only ever do nothing. The row itself opens the folder.
    if (item.type == LibraryItemType.Folder) return
    val isCurrent = playbackState.isCurrent(item.id)
    val isPlaying = isCurrent && playbackState.isPlaying
    IconButton(
        onClick = { if (isCurrent) onTogglePlayPause() else onPlay() },
        modifier = if (compact) Modifier.size(34.dp) else Modifier,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
            tint = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun LibraryItem.listDetail(): String = when (type) {
    LibraryItemType.Folder -> folderSummaryLine(childFolderCount, audioCount)
    LibraryItemType.Audio -> subtitle.ifBlank { stringResource(R.string.audio_type) }
}

private fun LibraryItemType.icon(): ImageVector = when (this) {
    LibraryItemType.Folder -> Icons.Default.Folder
    LibraryItemType.Audio -> Icons.Default.LibraryMusic
}

private fun LibraryLayoutMode.icon(): ImageVector = when (this) {
    LibraryLayoutMode.DetailedList -> Icons.AutoMirrored.Filled.ViewList
    LibraryLayoutMode.CompactList -> Icons.AutoMirrored.Filled.List
    LibraryLayoutMode.Grid -> Icons.Default.GridView
    LibraryLayoutMode.FocusWall -> Icons.Default.ViewComfy
    LibraryLayoutMode.FoliaWall -> Icons.Default.SpaceDashboard
}
