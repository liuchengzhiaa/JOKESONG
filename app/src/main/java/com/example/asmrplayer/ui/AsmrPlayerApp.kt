package com.example.asmrplayer.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.ScaffoldDefaults
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.R
import com.example.asmrplayer.core.media.PlaybackState
import com.example.asmrplayer.core.media.RepeatMode
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import com.example.asmrplayer.core.settings.ToolClusterStyle
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
import com.example.asmrplayer.feature.subtitles.LuminousTuning
import com.example.asmrplayer.feature.subtitles.LyricsVisualizerMode
import com.example.asmrplayer.feature.subtitles.LyricsWordGlowMode
import com.example.asmrplayer.feature.subtitles.PendoloTuning
import com.example.asmrplayer.feature.subtitles.SubtitleDetailState
import com.example.asmrplayer.feature.subtitles.TemperaTuning
import com.example.asmrplayer.ui.card.CardStudioScreen

private data class Destination(
    val label: Int,
    val icon: @Composable () -> Unit,
)

/** Still compact, but larger than the initial two-fifths treatment. */
private const val LibraryHeaderScale = 0.55f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsmrPlayerApp(
    libraryState: LibraryUiState,
    playbackState: PlaybackState,
    subtitleState: SubtitleDetailState,
    onChooseRoot: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: (LibraryItem) -> Unit,
    onSetFolderCover: suspend (String, android.net.Uri?) -> Boolean,
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
    onToolClusterStyleChange: (Int) -> Unit,
    onCardCoverByDefaultChange: (Boolean) -> Unit,
    onGlobalVinylPositionChange: (Int, Int) -> Unit,
    onLyricsVisualizerModeChange: (Int) -> Unit,
    onLyricsBackgroundChange: (Int?) -> Unit,
    onLyricsWordGlowModeChange: (Int) -> Unit,
    onLuminousTuningChange: (LuminousTuning) -> Unit,
    onPendoloTuningChange: (PendoloTuning) -> Unit,
    onTemperaTuningChange: (TemperaTuning) -> Unit,
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
    onRepeatModeChange: (RepeatMode) -> Unit,
    onSleepTimerChange: (Int) -> Unit,
    onMetadataSaved: (String, com.example.asmrplayer.core.metadata.TagSaveResult) -> Unit = { _, _ -> },
) {
    MetadataRecoveryPrompt(onRecovered = onRefresh)
    var selectedDestination by rememberSaveable { mutableIntStateOf(0) }
    var fullPlayerItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var playerSection by rememberSaveable(fullPlayerItemId) { mutableIntStateOf(0) }
    var showSubtitleDetail by rememberSaveable { mutableStateOf(false) }
    var showCardStudio by rememberSaveable { mutableStateOf(false) }
    // What the studio starts a new card from, captured when it is opened rather than
    // read while it is on screen. Opened from the player it seeds from that song;
    // opened from Settings it seeds from nothing, because the studio reached from
    // Settings is a browsing session and not a song's page. Reading a live item instead
    // would let the last song ever opened in the player leak into that.
    var studioSeedId by rememberSaveable { mutableStateOf<String?>(null) }
    var studioSeedItem by remember { mutableStateOf<LibraryItem?>(null) }
    // Whether the studio was opened to make one specific card or to browse them. The
    // song's own page opens it to make one, and goes straight to the form; Settings
    // opens it to browse, and gets the room.
    var studioStartAtCompose by rememberSaveable { mutableStateOf(false) }
    // Bumped when the studio finishes a card. The player reads its own song's card once
    // per item, and a card made while that page is still open is exactly the change it
    // could not otherwise see.
    var cardRevision by remember { mutableIntStateOf(0) }
    val layoutMode = LibraryLayoutMode.fromOrdinal(libraryState.libraryLayoutOrdinal)
    val libraryHeaderHeight = TopAppBarDefaults.TopAppBarExpandedHeight * LibraryHeaderScale
    val headerActionModifier = if (selectedDestination == 0) {
        // Keep independent 48 dp wide hit targets; only the visible row is shorter.
        Modifier.width(48.dp).height(libraryHeaderHeight)
    } else {
        Modifier
    }
    val headerIconModifier = if (selectedDestination == 0) Modifier.size(20.dp) else Modifier

    val itemById = remember(libraryState.items) { libraryState.items.associateBy(LibraryItem::id) }
    val nowPlayingItem = playbackState.mediaId?.let(itemById::get)
    var retainedNowPlayingItem by remember { mutableStateOf<LibraryItem?>(null) }
    val sessionNowPlayingItem = playbackState.mediaId?.let { mediaId ->
        nowPlayingItem ?: retainedNowPlayingItem?.takeIf { it.id == mediaId } ?: LibraryItem(
            id = mediaId,
            title = playbackState.title.orEmpty().ifBlank { stringResource(R.string.now_playing) },
            subtitle = stringResource(R.string.local_audio),
            type = LibraryItemType.Audio,
            paletteSeed = mediaId.hashCode(),
            documentUri = playbackState.documentUri,
            artworkUri = playbackState.artworkUri,
        )
    }
    LaunchedEffect(playbackState.mediaId, sessionNowPlayingItem) {
        retainedNowPlayingItem = when {
            playbackState.mediaId == null -> null
            sessionNowPlayingItem != null -> sessionNowPlayingItem
            else -> retainedNowPlayingItem
        }
    }
    val openedItem = fullPlayerItemId?.let { itemId ->
        itemById[itemId] ?: retainedNowPlayingItem?.takeIf { it.id == itemId }
    }
    // While the player is open it follows the session, so next/previous update it.
    val fullPlayerItem = if (fullPlayerItemId == null) null else sessionNowPlayingItem ?: openedItem
    var retainedFullPlayerItem by remember { mutableStateOf<LibraryItem?>(null) }
    LaunchedEffect(fullPlayerItem) {
        if (fullPlayerItem != null) retainedFullPlayerItem = fullPlayerItem
    }
    val playerQueue = remember(libraryState.items) {
        libraryState.items.filter { it.type == LibraryItemType.Audio }
    }
    // The captured item is the exact object that was open; the id finds it again after a
    // configuration change, when only saveable state survives.
    val studioItem = studioSeedItem
        ?: studioSeedId?.let { id -> itemById[id] ?: retainedNowPlayingItem?.takeIf { it.id == id } }
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

    // One parsed-document stream serves both lyric surfaces. The full-screen reader
    // wins while it is open; otherwise a wall requests lyrics only when the visual
    // subject is also the current Media3 item. Moving focus or leaving a wall clears
    // the old document immediately, so another cover can never inherit its words.
    val focusedPlayingWallItemId = playbackState.mediaId?.takeIf { mediaId ->
        fullPlayerItem == null &&
            selectedDestination == 0 &&
            (layoutMode == LibraryLayoutMode.FocusWall ||
                layoutMode == LibraryLayoutMode.FoliaWall) &&
            libraryState.focusedItemId == mediaId
    }
    // The full player is another lyric surface now: its header carries the line being
    // sung and the line after it. It asks for the same document the scene does, so the
    // two never disagree and the file is parsed once.
    val requestedSubtitleItemId = fullPlayerItem?.id ?: focusedPlayingWallItemId
    LaunchedEffect(requestedSubtitleItemId) {
        if (requestedSubtitleItemId != null) {
            onOpenSubtitleDetail(requestedSubtitleItemId)
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
        // The lyric renderer is the whole window. Its chrome applies transient
        // system-bar insets to itself, so Scaffold must not reserve another opaque
        // strip above and below the stage while that route is open.
        contentWindowInsets = if (showSubtitleDetail || showCardStudio || fullPlayerItem != null) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            // The lyric scene owns its small translucent back/title overlay. Keeping
            // the app bar here would make it a player page with a visualizer below,
            // rather than the full-screen scene promised by phase 5.
            if (!showSubtitleDetail && !showCardStudio && fullPlayerItem == null) CenterAlignedTopAppBar(
                expandedHeight = if (selectedDestination == 0) {
                    libraryHeaderHeight
                } else {
                    TopAppBarDefaults.TopAppBarExpandedHeight
                },
                title = {
                    Text(
                        text = if (selectedDestination == 0) {
                            libraryState.folderTitle.ifBlank { stringResource(R.string.folders) }
                        } else {
                            stringResource(destinations[selectedDestination].label)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.let { titleStyle ->
                            if (selectedDestination == 0) {
                                titleStyle.copy(
                                    fontSize = titleStyle.fontSize * LibraryHeaderScale,
                                    lineHeight = titleStyle.lineHeight * LibraryHeaderScale,
                                )
                            } else titleStyle
                        },
                    )
                },
                actions = {
                    if (selectedDestination == 0 && libraryState.hasRoot) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !libraryState.isScanning,
                            modifier = headerActionModifier,
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                stringResource(R.string.refresh_current_folder),
                                modifier = headerIconModifier,
                            )
                        }
                    }
                    if (selectedDestination == 0) {
                        IconButton(onClick = onChooseRoot, modifier = headerActionModifier) {
                            Icon(
                                Icons.Default.FolderOpen,
                                stringResource(R.string.choose_library_root),
                                modifier = headerIconModifier,
                            )
                        }
                    }
                },
                colors =
                    // Title, back arrow and the refresh / folder actions all take the
                    // global accent. Without this the setting reached the bottom
                    // navigation bar and nothing else, so the top bar read as if it
                    // were not part of the app's appearance at all.
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                        titleContentColor = MaterialTheme.colorScheme.primary,
                        navigationIconContentColor = MaterialTheme.colorScheme.primary,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
            )
        },
        bottomBar = {
            if (fullPlayerItem == null && !showCardStudio && !(isLandscape && selectedDestination == 0)) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                    tonalElevation = 0.dp,
                ) {
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
            val routeMotionMillis = if (libraryState.motionPercent <= 0) 0 else 420
            AnimatedVisibility(
                visible = fullPlayerItem != null,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(routeMotionMillis)) + scaleIn(
                    animationSpec = tween(routeMotionMillis),
                    initialScale = 0.78f,
                    transformOrigin = TransformOrigin(0f, 1f),
                ),
                exit = fadeOut(tween(routeMotionMillis / 2)) + scaleOut(
                    animationSpec = tween(routeMotionMillis),
                    targetScale = 0.78f,
                    transformOrigin = TransformOrigin(0f, 1f),
                ),
                label = "global vinyl to player",
            ) {
              (fullPlayerItem ?: retainedFullPlayerItem)?.let { playerItem ->
                if (showSubtitleDetail) {
                    SubtitleArtworkDetail(
                        item = playerItem,
                        subtitles = subtitleState,
                        playbackState = playbackState,
                        motionPercent = libraryState.motionPercent,
                        visualizerMode = LyricsVisualizerMode.fromOrdinal(
                            libraryState.lyricsVisualizerOrdinal,
                        ),
                        wordGlowMode = LyricsWordGlowMode.fromOrdinal(
                            libraryState.lyricsWordGlowModeOrdinal,
                        ),
                        backgroundOrdinal = libraryState.lyricsBackgroundOrdinal,
                        luminousTuning = libraryState.luminousTuning,
                        pendoloTuning = libraryState.pendoloTuning,
                        temperaTuning = libraryState.temperaTuning,
                        onVisualizerModeChange = {
                            onLyricsVisualizerModeChange(it.storedValue)
                        },
                        onBackgroundChange = onLyricsBackgroundChange,
                        onMotionPercentChange = onMotionPercentChange,
                        onWordGlowModeChange = {
                            onLyricsWordGlowModeChange(it.ordinal)
                        },
                        onLuminousTuningChange = onLuminousTuningChange,
                        onPendoloTuningChange = onPendoloTuningChange,
                        onTemperaTuningChange = onTemperaTuningChange,
                        onBack = { showSubtitleDetail = false },
                        onTogglePlayPause = onTogglePlayPause,
                        onSeekTo = onSeekTo,
                        onPrevious = onPrevious.takeIf { playbackState.hasPrevious },
                        onNext = onNext.takeIf { playbackState.hasNext },
                    )
                } else {
                      PlayerDetail(
                          item = playerItem,
                          onMetadataSaved = { id, saved ->
                              retainedNowPlayingItem = retainedNowPlayingItem?.let {
                                  if (it.id == id) it.copy(artworkUri = saved.artworkUri, documentUri = saved.documentUri) else it
                              }
                              onMetadataSaved(id, saved)
                          },
                        queueItems = playerQueue,
                        playbackState = playbackState,
                        subtitles = subtitleState,
                        onRepeatModeChange = onRepeatModeChange,
                        onSleepTimerChange = onSleepTimerChange,
                        onTogglePlayPause = onTogglePlayPause,
                        onPrevious = onPrevious.takeIf { playbackState.hasPrevious },
                        onNext = onNext.takeIf { playbackState.hasNext },
                        onSeekTo = onSeekTo,
                        onSeekBy = onSeekBy,
                        onQueueItemSelected = { selected ->
                            fullPlayerItemId = selected.id
                            onPlayItem(selected)
                        },
                        onOpenLyrics = { showSubtitleDetail = true },
                        onMakeCard = {
                            studioSeedItem = playerItem
                            studioSeedId = playerItem.id
                            studioStartAtCompose = true
                            showCardStudio = true
                        },
                        toolClusterStyle = ToolClusterStyle.fromOrdinal(
                            libraryState.toolClusterStyleOrdinal,
                        ),
                        motionPercent = libraryState.motionPercent,
                        cardCoverByDefault = libraryState.cardCoverByDefault,
                        cardRevision = cardRevision,
                        initialPage = playerSection,
                        onPageChange = { playerSection = it },
                    )
                }
              }
            }
            AnimatedVisibility(
                visible = fullPlayerItem == null,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(routeMotionMillis)),
                exit = fadeOut(tween(routeMotionMillis / 2)),
                label = "app destinations",
            ) {
              Box(Modifier.fillMaxSize()) {
              if (selectedDestination == 0) {
                LibraryContent(
                    state = libraryState,
                    playbackState = playbackState,
                    subtitleState = subtitleState,
                    layoutMode = layoutMode,
                    onLayoutModeChange = { onLibraryLayoutChange(it.ordinal) },
                    onChooseRoot = onChooseRoot,
                    onRefresh = onRefresh,
                    onOpenFolder = onOpenFolder,
                    onSetFolderCover = onSetFolderCover,
                    onOpenPlayer = {
                        showSubtitleDetail = false
                        fullPlayerItemId = it.id
                    },
                    onOpenLyrics = {
                        fullPlayerItemId = it.id
                        showSubtitleDetail = true
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
                    onPrevious = onPrevious,
                    onNext = onNext,
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
                    onToolClusterStyleChange = onToolClusterStyleChange,
                    onLyricsWordGlowModeChange = onLyricsWordGlowModeChange,
                    onOpenCardStudio = {
                        // Deliberately no seed: the room reached from Settings is about
                        // the collection, not about whatever happens to be playing.
                        studioSeedItem = null
                        studioSeedId = null
                        studioStartAtCompose = false
                        showCardStudio = true
                    },
                    onCardCoverByDefaultChange = onCardCoverByDefaultChange,
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

            AnimatedVisibility(
                visible = fullPlayerItem == null && retainedNowPlayingItem != null,
                modifier = Modifier
                    .fillMaxSize(),
                enter = fadeIn(tween(routeMotionMillis)),
                exit = fadeOut(tween(routeMotionMillis / 2)),
                label = "global vinyl player",
            ) {
                retainedNowPlayingItem?.let { playingItem ->
                    GlobalVinylPlayer(
                        item = playingItem,
                        isPlaying = playbackState.isPlaying,
                        motionPercent = libraryState.motionPercent,
                        xPerMille = libraryState.globalVinylXPerMille,
                        yPerMille = libraryState.globalVinylYPerMille,
                        onPositionSettled = onGlobalVinylPositionChange,
                        onClick = {
                            showSubtitleDetail = false
                            fullPlayerItemId = playingItem.id
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                    )
                }
            }

            // Above the player rather than beside it: the studio is opened from the
            // player's own tool cluster, and the captured seed is what keeps it on
            // screen while the player underneath animates away.
            AnimatedVisibility(
                visible = showCardStudio,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(routeMotionMillis)),
                exit = fadeOut(tween(routeMotionMillis / 2)),
                label = "card studio",
            ) {
                // A null item is a real state here, not a missing one: this is the
                // collection room, and the owner may reach it with nothing playing.
                CardStudioScreen(
                    item = studioItem,
                    onClose = { showCardStudio = false },
                    modifier = Modifier.fillMaxSize(),
                    startAtCompose = studioStartAtCompose,
                    onCardCreated = { cardRevision++ },
                )
            }
        }
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    playbackState: PlaybackState,
    subtitleState: SubtitleDetailState,
    layoutMode: LibraryLayoutMode,
    onLayoutModeChange: (LibraryLayoutMode) -> Unit,
    onChooseRoot: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: (LibraryItem) -> Unit,
    onSetFolderCover: suspend (String, android.net.Uri?) -> Boolean,
    onOpenPlayer: (LibraryItem) -> Unit,
    onOpenLyrics: (LibraryItem) -> Unit,
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
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var coverFolderUri by rememberSaveable { mutableStateOf<String?>(null) }
    var coverFolderTitle by rememberSaveable { mutableStateOf("") }
    val editFolderCover: (LibraryItem) -> Unit = { item ->
        if (item.type == LibraryItemType.Folder && item.documentUri != null) {
            coverFolderTitle = item.title
            coverFolderUri = item.documentUri
        }
    }
    coverFolderUri?.let { folder ->
        FolderCoverDialog(
            folderUri = folder, title = coverFolderTitle,
            onSave = onSetFolderCover, onDismiss = { coverFolderUri = null },
        )
    }
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
            Box(Modifier.weight(1f).fillMaxWidth()) {
              when (layoutMode) {
                LibraryLayoutMode.FocusWall -> FocusCoverWall(
                    modifier = Modifier.fillMaxSize(),
                    items = state.items,
                    contentKey = state.folderKey,
                    focusedItemId = focusedItemId,
                    playbackState = playbackState,
                    subtitleState = subtitleState,
                    wordGlowMode = LyricsWordGlowMode.fromOrdinal(
                        state.lyricsWordGlowModeOrdinal,
                    ),
                    viewportState = viewportState,
                    style = FocusWallStyle.Bento,
                    onFocusedItemChange = onFocusedItemChange,
                    onRevealPlayingItem = revealPlayingItem,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onOpenLyrics = onOpenLyrics,
                    onFolderLongClick = editFolderCover,
                    layoutMode = layoutMode,
                    onLayoutModeChange = onLayoutModeChange,
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
                    toolClusterStyle = ToolClusterStyle.fromOrdinal(
                        state.toolClusterStyleOrdinal,
                    ),
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
                    subtitleState = subtitleState,
                    wordGlowMode = LyricsWordGlowMode.fromOrdinal(
                        state.lyricsWordGlowModeOrdinal,
                    ),
                    viewportState = foliaViewportState,
                    style = FocusWallStyle.Folia,
                    onFocusedItemChange = onFocusedItemChange,
                    onRevealPlayingItem = revealPlayingItem,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onOpenLyrics = onOpenLyrics,
                    onFolderLongClick = editFolderCover,
                    layoutMode = layoutMode,
                    onLayoutModeChange = onLayoutModeChange,
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
                    toolClusterStyle = ToolClusterStyle.fromOrdinal(
                        state.toolClusterStyleOrdinal,
                    ),
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
                    onFolderLongClick = editFolderCover,
                    onPlay = onPlayItem,
                    onTogglePlayPause = onTogglePlayPause,
                )
                LibraryLayoutMode.CompactList -> CompactLibraryList(
                    modifier = Modifier.fillMaxSize(),
                    items = state.items,
                    playbackState = playbackState,
                    onOpen = openItem,
                    onFolderLongClick = editFolderCover,
                    onPlay = onPlayItem,
                    onTogglePlayPause = onTogglePlayPause,
                )
                LibraryLayoutMode.Grid -> LibraryGrid(
                    modifier = Modifier.fillMaxSize(),
                    items = state.items,
                    playbackState = playbackState,
                    onOpen = openItem,
                    onFolderLongClick = editFolderCover,
                    onPlay = onPlayItem,
                    onTogglePlayPause = onTogglePlayPause,
                )
              }
              if (!layoutMode.isWall) {
                  LibraryLayoutSwitcher(
                      selected = layoutMode,
                      onSelect = onLayoutModeChange,
                      style = ToolClusterStyle.fromOrdinal(state.toolClusterStyleOrdinal),
                      motionPercent = state.motionPercent,
                      contentKey = state.folderKey,
                  )
              }
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
