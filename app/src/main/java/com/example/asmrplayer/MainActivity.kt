package com.example.asmrplayer

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.example.asmrplayer.core.media.rememberPlaybackConnection
import com.example.asmrplayer.feature.library.LibraryViewModel
import com.example.asmrplayer.ui.AsmrPlayerApp
import com.example.asmrplayer.ui.theme.AsmrPlayerTheme

class MainActivity : ComponentActivity() {
    private val libraryViewModel by viewModels<LibraryViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
            val playback = rememberPlaybackConnection()
            val playbackState by playback.state.collectAsStateWithLifecycle()
            val rootPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
                onResult = { uri -> uri?.let(libraryViewModel::selectRoot) },
            )
            AsmrPlayerTheme {
                AsmrPlayerApp(
                    libraryState = libraryState,
                    playbackState = playbackState,
                    onChooseRoot = { rootPicker.launch(null) },
                    onRefresh = libraryViewModel::refresh,
                    onOpenFolder = libraryViewModel::openFolder,
                    onNavigateUp = { libraryViewModel.navigateUp() },
                    onFocusedItemChange = libraryViewModel::onFocusedItemChange,
                    onViewportChange = libraryViewModel::onViewportChange,
                    onWallDensityChange = libraryViewModel::onWallDensityChange,
                    onPlayItem = { item -> playback.playFromFolder(libraryState.items, item) },
                    onTogglePlayPause = playback::togglePlayPause,
                    onSeekTo = playback::seekTo,
                    onSeekBy = playback::seekBy,
                    onNext = playback::skipToNext,
                    onPrevious = playback::skipToPrevious,
                )
            }
        }
    }
}
