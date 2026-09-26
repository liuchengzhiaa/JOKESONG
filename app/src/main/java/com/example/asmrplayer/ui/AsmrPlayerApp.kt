package com.example.asmrplayer.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.R
import com.example.asmrplayer.core.media.PlaybackState
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import com.example.asmrplayer.feature.focuswall.FocusCoverWall
import com.example.asmrplayer.feature.focuswall.FocusWallStyle
import com.example.asmrplayer.feature.focuswall.FocusWallViewportState
import com.example.asmrplayer.feature.focuswall.canRevealPlayingItem
import com.example.asmrplayer.feature.focuswall.shouldAutoFocusPlayingItem
import com.example.asmrplayer.feature.library.CompactLibraryList
import com.example.asmrplayer.feature.library.DetailedLibraryList
import com.example.asmrplayer.feature.library.LibraryGrid
import com.example.asmrplayer.feature.library.LibraryLayoutMode
import com.example.asmrplayer.feature.library.LibraryLayoutSwitcher
import com.example.asmrplayer.feature.library.LibraryUiState
import com.example.asmrplayer.feature.subtitles.SubtitleDetailState

private data class Destination(
    val label: Int,
    val icon: @Composable () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsmrPlayerApp(
    libraryState: LibraryUiState,
    playbackState: PlaybackState,
    subtitleState: SubtitleDetailState,
    onChooseRoot: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: (LibraryItem) -> Unit,
    onNavigateUp: () -> Unit,
    onFocusedItemChange: (String?) -> Unit,
    onViewportChange: (Float, Float, Float) -> Unit,
    onLibraryLayoutChange: (Int) -> Unit,
    onWallDensityChange: (Int) -> Unit,
    onWallFocusShapeChange: (Int) -> Unit,
    onWallFocusSizeChange: (Int) -> Unit,
    onWallAmbientStrengthChange: (Int) -> Unit,
    onWallLightsOutChange: (Boolean) -> Unit,
    onWallAutoFocusPlayingChange: (Boolean) -> Unit,
    onWallPosterTintStrengthChange: (Int) -> Unit,
    onMotionPercentChange: (Int) -> Unit,
    onUiStylePresetChange: (Int) -> Unit,
    onUiAccentPaletteChange: (Int) -> Unit,
    onUiScalePercentChange: (Int) -> Unit,
    onResetAppearanceDefaults: () -> Unit,
    onOpenSubtitleDetail: (String) -> Unit,
    onCloseSubtitleDetail: () -> Unit,
    onPlayItem: (LibraryItem) -> Unit,
    onRevealPlayingItem: (String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    var selectedDestination by rememberSaveable { mutableIntStateOf(0) }
    var fullPlayerItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSubtitleDetail by rememberSaveable { mutableStateOf(false) }
    val layoutMode = LibraryLayoutMode.fromOrdinal(libraryState.libraryLayoutOrdinal)

    val itemById = remember(libraryState.items) { libraryState.items.associateBy(LibraryItem::id) }
    val nowPlayingItem = playbackState.mediaId?.let(itemById::get)
    val openedItem = fullPlayerItemId?.let(itemById::get)
    // While the player is open it follows the session, so next/previous update it.
    val fullPlayerItem = if (fullPlayerItemId == null) null else nowPlayingItem ?: openedItem
    val playerQueue = remember(libraryState.items) {
        libraryState.items.filter { it.type == LibraryItemType.Audio }
    }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val destinations = listOf(
        Destination(R.string.library) { Icon(Icons.Default.LibraryMusic, null) },
        Destination(R.string.search) { Icon(Icons.Default.Search, null) },
        Destination(R.string.playlists) { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) },
        Destination(R.string.settings) { Icon(Icons.Default.Settings, null) },
    )

    BackHandler(
        enabled = fullPlayerItem == null && (selectedDestination != 0 || libraryState.canNavigateUp),
    ) {
        if (selectedDestination != 0) selectedDestination = 0 else onNavigateUp()
    }
    BackHandler(enabled = showSubtitleDetail) {
        showSubtitleDetail = false
    }

    // The surface always shows the lyrics of whatever the player is on, so a queue
    // advance while it is open loads the next track's file instead of leaving the
    // previous track's lines on screen.
    LaunchedEffect(showSubtitleDetail, fullPlayerItem?.id) {
        val itemId = fullPlayerItem?.id
        if (showSubtitleDetail && itemId != null) {
            onOpenSubtitleDetail(itemId)
        } else {
            onCloseSubtitleDetail()
        }
    }
    BackHandler(enabled = fullPlayerItem != null && !showSubtitleDetail) {
        fullPlayerItemId = null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = if (fullPlayerItem != null) Color(0xFF090A0D) else MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (showSubtitleDetail) {
                            stringResource(R.string.subtitle_detail)
                        } else fullPlayerItem?.title
                            ?: libraryState.folderTitle.ifBlank { stringResource(R.string.folders) },
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    when {
                        fullPlayerItem != null -> {
                            IconButton(
                                onClick = {
                                    if (showSubtitleDetail) {
                                        showSubtitleDetail = false
                                    } else {
                                        fullPlayerItemId = null
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.back_to_library),
                                )
                            }
                        }
                        selectedDestination == 0 && libraryState.canNavigateUp -> {
                            IconButton(onClick = onNavigateUp) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.navigate_up),
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (fullPlayerItem == null && selectedDestination == 0 && libraryState.hasRoot) {
                        IconButton(onClick = onRefresh, enabled = !libraryState.isScanning) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.refresh_current_folder))
                        }
                    }
                    if (fullPlayerItem == null && selectedDestination == 0) {
                        IconButton(onClick = onChooseRoot) {
                            Icon(Icons.Default.FolderOpen, stringResource(R.string.choose_library_root))
                        }
                    }
                },
                colors = if (fullPlayerItem != null) {
                    // The player's bar is immersive chrome: a fixed near-black surface
                    // that deliberately ignores the theme's light and dark surfaces. The
                    // accent's light variant is unreadable on it, so this bar stays
                    // neutral rather than half-following a palette it is not part of.
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0xFF090A0D),
                        titleContentColor = Color(0xFFF4F4F6),
                        navigationIconContentColor = Color(0xFFF4F4F6),
                        actionIconContentColor = Color(0xFFF4F4F6),
                    )
                } else {
                    // Title, back arrow and the refresh / folder actions all take the
                    // global accent. Without this the setting reached the bottom
                    // navigation bar and nothing else, so the top bar read as if it
                    // were not part of the app's appearance at all.
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        titleContentColor = MaterialTheme.colorScheme.primary,
                        navigationIconContentColor = MaterialTheme.colorScheme.primary,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        },
        bottomBar = {
            if (fullPlayerItem == null && !(isLandscape && selectedDestination == 0)) {
                NavigationBar {
                    destinations.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = selectedDestination == index,
                            onClick = { selectedDestination = index },
                            icon = destination.icon,
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (fullPlayerItem != null) {
                if (showSubtitleDetail) {
                    SubtitleArtworkDetail(
                        item = fullPlayerItem,
                        subtitles = subtitleState,
                        playbackState = playbackState,
                        onTogglePlayPause = onTogglePlayPause,
                        onSeekTo = onSeekTo,
                    )
                } else {
                    PlayerDetail(
                        item = fullPlayerItem,
                        queueItems = playerQueue,
                        playbackState = playbackState,
                        onTogglePlayPause = onTogglePlayPause,
                        onPrevious = onPrevious.takeIf { playbackState.hasPrevious },
                        onNext = onNext.takeIf { playbackState.hasNext },
                        onSeekTo = onSeekTo,
                        onSeekBy = onSeekBy,
                        onQueueItemSelected = { selected ->
                            fullPlayerItemId = selected.id
                            onPlayItem(selected)
                        },
                        onArtworkClick = { showSubtitleDetail = true },
                    )
                }
            } else if (selectedDestination == 0) {
                LibraryContent(
                    state = libraryState,
                    playbackState = playbackState,
                    layoutMode = layoutMode,
                    onLayoutModeChange = { onLibraryLayoutChange(it.ordinal) },
                    onChooseRoot = onChooseRoot,
                    onRefresh = onRefresh,
                    onOpenFolder = onOpenFolder,
                    onOpenPlayer = {
                        showSubtitleDetail = false
                        fullPlayerItemId = it.id
                    },
                    onFocusedItemChange = onFocusedItemChange,
                    onViewportChange = onViewportChange,
                    onWallDensityChange = onWallDensityChange,
                    onWallFocusShapeChange = onWallFocusShapeChange,
                    onWallFocusSizeChange = onWallFocusSizeChange,
                    onWallAmbientStrengthChange = onWallAmbientStrengthChange,
                    onWallLightsOutChange = onWallLightsOutChange,
                    onWallAutoFocusPlayingChange = onWallAutoFocusPlayingChange,
                    onWallPosterTintStrengthChange = onWallPosterTintStrengthChange,
                    onMotionPercentChange = onMotionPercentChange,
                    onPlayItem = onPlayItem,
                    onRevealPlayingItem = onRevealPlayingItem,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                )
            } else if (selectedDestination == 3) {
                SettingsScreen(
                    state = libraryState,
                    onChooseRoot = onChooseRoot,
                    onRefresh = onRefresh,
                    onUiStylePresetChange = onUiStylePresetChange,
                    onUiAccentPaletteChange = onUiAccentPaletteChange,
                    onMotionPercentChange = onMotionPercentChange,
                    onUiScalePercentChange = onUiScalePercentChange,
                    onResetAppearanceDefaults = onResetAppearanceDefaults,
                )
            } else {
                MessageState(
                    title = stringResource(destinations[selectedDestination].label),
                    message = stringResource(R.string.feature_coming_soon),
                )
            }
        }
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    playbackState: PlaybackState,
    layoutMode: LibraryLayoutMode,
    onLayoutModeChange: (LibraryLayoutMode) -> Unit,
    onChooseRoot: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: (LibraryItem) -> Unit,
    onOpenPlayer: (LibraryItem) -> Unit,
    onFocusedItemChange: (String?) -> Unit,
    onViewportChange: (Float, Float, Float) -> Unit,
    onWallDensityChange: (Int) -> Unit,
    onWallFocusShapeChange: (Int) -> Unit,
    onWallFocusSizeChange: (Int) -> Unit,
    onWallAmbientStrengthChange: (Int) -> Unit,
    onWallLightsOutChange: (Boolean) -> Unit,
    onWallAutoFocusPlayingChange: (Boolean) -> Unit,
    onWallPosterTintStrengthChange: (Int) -> Unit,
    onMotionPercentChange: (Int) -> Unit,
    onPlayItem: (LibraryItem) -> Unit,
    onRevealPlayingItem: (String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    val focusedItemId = state.focusedItemId
    BackHandler(enabled = focusedItemId != null) { onFocusedItemChange(null) }

    // A reveal the wall cannot honour is offered disabled rather than as a tap that
    // does nothing, so both walls see the same answer.
    val playingItemId = playbackState.mediaId
    val listedItemIds = remember(state.items) { state.items.mapTo(mutableSetOf(), LibraryItem::id) }
    val revealPlayingItem: (() -> Unit)? = playingItemId
        ?.takeIf { canRevealPlayingItem(it, listedItemIds, state.hasPlaybackOrigin) }
        ?.let { id -> { onRevealPlayingItem(id) } }

    // Auto-focus is a Folia wall command, not a global navigation policy. It is
    // dormant in list/grid/Bento modes and follows the same reachability rules as
    // the explicit "focus current" command when Media3 advances the queue.
    var lastObservedPlayingItemId by remember { mutableStateOf(playingItemId) }
    LaunchedEffect(playingItemId) {
        val previousItemId = lastObservedPlayingItemId
        lastObservedPlayingItemId = playingItemId
        val reachable = canRevealPlayingItem(
            playingItemId,
            listedItemIds,
            state.hasPlaybackOrigin,
        )
        if (
            shouldAutoFocusPlayingItem(
                enabled = state.wallAutoFocusPlaying,
                foliaVisible = layoutMode == LibraryLayoutMode.FoliaWall,
                previousItemId = previousItemId,
                currentItemId = playingItemId,
                reachable = reachable,
            )
        ) {
            playingItemId?.let(onRevealPlayingItem)
        }
    }

    // Lives above the layout switch so changing layout keeps the wall viewport,
    // and is recreated per folder from the snapshot the ViewModel restored.
    val viewportState = remember(state.folderKey) {
        FocusWallViewportState(
            initialZoom = state.canvasZoom,
            initialPanX = state.panX,
            initialPanY = state.panY,
            initialDensityPercent = state.wallDensityPercent,
            initialFocusShapeOrdinal = state.wallFocusShapeOrdinal,
            initialFocusSizeOrdinal = state.wallFocusSizeOrdinal,
            initialAmbientPercent = state.wallAmbientPercent,
            restored = state.isRestored,
        )
    }
    // The alternate wall is a separate presentation, so switching between the
    // two keeps each wall exactly where the user left it during this folder
    // session instead of making them fight over one pan/zoom state.
    val foliaViewportState = remember(state.folderKey) {
        FocusWallViewportState(
            initialZoom = state.canvasZoom,
            // Bento snapshot coordinates describe a different world geometry.
            // Start the alternate wall at its own origin; a shared focused item
            // is then centred by the wall's normal focus reconciliation.
            initialPanX = 0f,
            initialPanY = 0f,
            initialDensityPercent = state.wallDensityPercent,
            initialFocusShapeOrdinal = state.wallFocusShapeOrdinal,
            initialFocusSizeOrdinal = state.wallFocusSizeOrdinal,
            initialAmbientPercent = state.wallAmbientPercent,
            restored = false,
        )
    }
    val activeWallViewportState = when (layoutMode) {
        LibraryLayoutMode.FocusWall -> viewportState
        LibraryLayoutMode.FoliaWall -> foliaViewportState
        else -> null
    }
    LaunchedEffect(activeWallViewportState) {
        activeWallViewportState ?: return@LaunchedEffect
        snapshotFlow {
            Triple(
                activeWallViewportState.zoom,
                activeWallViewportState.pan.x,
                activeWallViewportState.pan.y,
            )
        }.collect { (zoom, panX, panY) -> onViewportChange(zoom, panX, panY) }
    }

    val openItem: (LibraryItem) -> Unit = { item ->
        when {
            item.type == LibraryItemType.Folder -> onOpenFolder(item)
            // Same rule as the wall: the item that is already playing opens the
            // full player instead of restarting.
            playbackState.isCurrent(item.id) -> onOpenPlayer(item)
            else -> onPlayItem(item)
        }
    }

    when {
        !state.hasRoot -> RootSelectionState(onChooseRoot)
        state.isScanning && state.items.isEmpty() -> LoadingState()
        state.hasError && state.items.isEmpty() -> ErrorState(onRefresh, onChooseRoot)
        state.items.isEmpty() -> EmptyFolderState(onRefresh)
        else -> Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryLayoutSwitcher(selected = layoutMode, onSelect = onLayoutModeChange)
                Spacer(Modifier.weight(1f))
                if (playbackState.isPlaying) {
                    Text(
                        text = stringResource(R.string.now_playing),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (state.isScanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            // Restoring from the in-memory snapshot is an implementation detail and
            // is deliberately not announced: the folder simply looks the way it was
            // left. Only real problems and real truncation still speak up.
            if (state.isTruncated || state.hasError) {
                Text(
                    text = when {
                        state.hasError -> stringResource(R.string.library_scan_failed_short)
                        else -> stringResource(
                            R.string.library_items_truncated,
                            state.supportedItemCount,
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when (layoutMode) {
                LibraryLayoutMode.FocusWall -> FocusCoverWall(
                    modifier = Modifier.fillMaxSize(),
                    items = state.items,
                    contentKey = state.folderKey,
                    focusedItemId = focusedItemId,
                    playbackState = playbackState,
                    viewportState = viewportState,
                    style = FocusWallStyle.Bento,
                    onFocusedItemChange = onFocusedItemChange,
                    onRevealPlayingItem = revealPlayingItem,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onDensityChange = onWallDensityChange,
                    onFocusShapeChange = onWallFocusShapeChange,
                    onFocusSizeChange = onWallFocusSizeChange,
                    onAmbientStrengthChange = onWallAmbientStrengthChange,
                    lightsOut = state.wallLightsOut,
                    autoFocusPlaying = state.wallAutoFocusPlaying,
                    posterTintPercent = state.wallPosterTintPercent,
                    motionPercent = state.motionPercent,
                    onLightsOutChange = onWallLightsOutChange,
                    onAutoFocusPlayingChange = onWallAutoFocusPlayingChange,
                    onPosterTintStrengthChange = onWallPosterTintStrengthChange,
                    onMotionPercentChange = onMotionPercentChange,
                    onActivate = { item ->
                        if (item.type == LibraryItemType.Folder) {
                            onOpenFolder(item)
                        } else {
                            onOpenPlayer(item)
                        }
                    },
                    onPlay = onPlayItem,
                )
                LibraryLayoutMode.FoliaWall -> FocusCoverWall(
                    modifier = Modifier.fillMaxSize(),
                    items = state.items,
                    contentKey = "${state.folderKey}:folia",
                    focusedItemId = focusedItemId,
                    playbackState = playbackState,
                    viewportState = foliaViewportState,
                    style = FocusWallStyle.Folia,
                    onFocusedItemChange = onFocusedItemChange,
                    onRevealPlayingItem = revealPlayingItem,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onDensityChange = onWallDensityChange,
                    onFocusShapeChange = onWallFocusShapeChange,
                    onFocusSizeChange = onWallFocusSizeChange,
                    onAmbientStrengthChange = onWallAmbientStrengthChange,
                    lightsOut = state.wallLightsOut,
                    autoFocusPlaying = state.wallAutoFocusPlaying,
                    posterTintPercent = state.wallPosterTintPercent,
                    motionPercent = state.motionPercent,
                    onLightsOutChange = onWallLightsOutChange,
                    onAutoFocusPlayingChange = onWallAutoFocusPlayingChange,
                    onPosterTintStrengthChange = onWallPosterTintStrengthChange,
                    onMotionPercentChange = onMotionPercentChange,
                    onActivate = { item ->
                        if (item.type == LibraryItemType.Folder) {
                            onOpenFolder(item)
                        } else {
                            onOpenPlayer(item)
                        }
                    },
                    onPlay = onPlayItem,
                )
                LibraryLayoutMode.DetailedList -> DetailedLibraryList(
                    modifier = Modifier.fillMaxSize(),
                    items = state.items,
                    playbackState = playbackState,
                    onOpen = openItem,
                    onPlay = onPlayItem,
                    onTogglePlayPause = onTogglePlayPause,
                )
                LibraryLayoutMode.CompactList -> CompactLibraryList(
                    modifier = Modifier.fillMaxSize(),
                    items = state.items,
                    playbackState = playbackState,
                    onOpen = openItem,
                    onPlay = onPlayItem,
                    onTogglePlayPause = onTogglePlayPause,
                )
                LibraryLayoutMode.Grid -> LibraryGrid(
                    modifier = Modifier.fillMaxSize(),
                    items = state.items,
                    playbackState = playbackState,
                    onOpen = openItem,
                    onPlay = onPlayItem,
                    onTogglePlayPause = onTogglePlayPause,
                )
            }
        }
    }
}

@Composable
private fun RootSelectionState(onChooseRoot: () -> Unit) {
    MessageState(
        title = stringResource(R.string.choose_library_root_title),
        message = stringResource(R.string.choose_library_root_description),
        action = {
            Button(onClick = onChooseRoot) {
                Text(stringResource(R.string.choose_folder))
            }
        },
    )
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.scanning_folder),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun EmptyFolderState(onRefresh: () -> Unit) {
    MessageState(
        title = stringResource(R.string.empty_folder_title),
        message = stringResource(R.string.empty_folder_description),
        action = {
            Button(onClick = onRefresh) {
                Text(stringResource(R.string.refresh))
            }
        },
    )
}

@Composable
private fun ErrorState(onRefresh: () -> Unit, onChooseRoot: () -> Unit) {
    MessageState(
        title = stringResource(R.string.library_scan_failed_title),
        message = stringResource(R.string.library_scan_failed_description),
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
                Button(onClick = onChooseRoot) { Text(stringResource(R.string.choose_again)) }
            }
        },
    )
}

@Composable
private fun MessageState(
    title: String,
    message: String,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            action?.invoke()
        }
    }
}
