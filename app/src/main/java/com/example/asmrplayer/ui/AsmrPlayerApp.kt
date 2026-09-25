package com.example.asmrplayer.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.R
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import com.example.asmrplayer.feature.focuswall.FocusCoverWall
import com.example.asmrplayer.feature.library.LibraryUiState

private data class Destination(
    val label: Int,
    val icon: @Composable () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsmrPlayerApp(
    libraryState: LibraryUiState,
    onChooseRoot: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: (LibraryItem) -> Unit,
    onNavigateUp: () -> Unit,
) {
    var selectedDestination by rememberSaveable { mutableIntStateOf(0) }
    var playingItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var fullPlayerItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val fullPlayerItem = fullPlayerItemId?.let { id ->
        libraryState.items.firstOrNull { it.id == id }
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
    BackHandler(enabled = fullPlayerItem != null) {
        fullPlayerItemId = null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = fullPlayerItem?.title
                            ?: libraryState.folderTitle.ifBlank { stringResource(R.string.folders) },
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    when {
                        fullPlayerItem != null -> {
                            IconButton(onClick = { fullPlayerItemId = null }) {
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
                PlayerDetail(
                    item = fullPlayerItem,
                    isPlaying = playingItemId == fullPlayerItem.id,
                    onPlayPause = {
                        playingItemId = if (playingItemId == fullPlayerItem.id) {
                            null
                        } else {
                            fullPlayerItem.id
                        }
                    },
                )
            } else if (selectedDestination == 0) {
                LibraryContent(
                    state = libraryState,
                    playingItemId = playingItemId,
                    onPlayingItemChange = { playingItemId = it },
                    onChooseRoot = onChooseRoot,
                    onRefresh = onRefresh,
                    onOpenFolder = onOpenFolder,
                    onOpenPlayer = { fullPlayerItemId = it.id },
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
    playingItemId: String?,
    onPlayingItemChange: (String?) -> Unit,
    onChooseRoot: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: (LibraryItem) -> Unit,
    onOpenPlayer: (LibraryItem) -> Unit,
) {
    var focusedItemId by rememberSaveable(state.folderKey) { mutableStateOf<String?>(null) }
    BackHandler(enabled = focusedItemId != null) {
        focusedItemId = null
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
            if (state.isTruncated || state.hasError) {
                Text(
                    text = if (state.isTruncated) {
                        stringResource(R.string.library_items_truncated, state.supportedItemCount)
                    } else {
                        stringResource(R.string.library_scan_failed_short)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FocusCoverWall(
                modifier = Modifier.fillMaxSize(),
                contentKey = state.folderKey,
                items = state.items,
                focusedItemId = focusedItemId,
                playingItemId = playingItemId,
                onFocusedItemChange = { focusedItemId = it },
                onPlayingItemChange = onPlayingItemChange,
                onActivate = { item ->
                    if (item.type == LibraryItemType.Folder) {
                        onOpenFolder(item)
                    } else {
                        onOpenPlayer(item)
                    }
                },
            )
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
