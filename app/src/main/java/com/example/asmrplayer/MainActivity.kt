package com.example.asmrplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.asmrplayer.core.media.rememberPlaybackConnection
import com.example.asmrplayer.core.settings.UiAccentPalette
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
            val subtitleState by libraryViewModel.subtitleState.collectAsStateWithLifecycle()
            val playback = rememberPlaybackConnection()
            val playbackState by playback.state.collectAsStateWithLifecycle()
            val rootPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
                onResult = { uri -> uri?.let(libraryViewModel::selectRoot) },
            )
            var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }
            val notificationPermission = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { /* Playback is allowed even when notification access is denied. */ },
            )
            AsmrPlayerTheme(
                accentPalette = UiAccentPalette.fromOrdinal(
                    libraryState.uiAccentPaletteOrdinal,
                ),
                uiScalePercent = libraryState.uiScalePercent,
            ) {
                AsmrPlayerApp(
                    libraryState = libraryState,
                    playbackState = playbackState,
                    subtitleState = subtitleState,
                    onChooseRoot = { rootPicker.launch(null) },
                    onRefresh = libraryViewModel::refresh,
                    onMetadataSaved = { id, saved ->
                        playback.onFileMetadataSaved(id, saved)
                        libraryViewModel.refreshAfterMetadataEdit(id, saved)
                    },
                    onOpenFolder = libraryViewModel::openFolder,
                    onNavigateUp = { libraryViewModel.navigateUp() },
                    onFocusedItemChange = libraryViewModel::onFocusedItemChange,
                    onViewportChange = libraryViewModel::onViewportChange,
                    onLibraryLayoutChange = libraryViewModel::onLibraryLayoutChange,
                    onWallDensityChange = libraryViewModel::onWallDensityChange,
                    onWallFocusShapeChange = libraryViewModel::onWallFocusShapeChange,
                    onWallFocusSizeChange = libraryViewModel::onWallFocusSizeChange,
                    onWallAmbientStrengthChange = libraryViewModel::onWallAmbientStrengthChange,
                    onWallLightsOutChange = libraryViewModel::onWallLightsOutChange,
                    onWallAutoFocusPlayingChange = libraryViewModel::onWallAutoFocusPlayingChange,
                    onWallPosterTintStrengthChange =
                        libraryViewModel::onWallPosterTintStrengthChange,
                    onMotionPercentChange = libraryViewModel::onMotionPercentChange,
                    onUiStylePresetChange = libraryViewModel::onUiStylePresetChange,
                    onUiAccentPaletteChange = libraryViewModel::onUiAccentPaletteChange,
                    onUiScalePercentChange = libraryViewModel::onUiScalePercentChange,
                    onGlobalVinylPositionChange =
                        libraryViewModel::onGlobalVinylPositionChange,
                    onLyricsVisualizerModeChange =
                        libraryViewModel::onLyricsVisualizerModeChange,
                    onLyricsBackgroundChange =
                        libraryViewModel::onLyricsBackgroundChange,
                    onLyricsWordGlowModeChange =
                        libraryViewModel::onLyricsWordGlowModeChange,
                    onLuminousTuningChange = libraryViewModel::onLuminousTuningChange,
                    onPendoloTuningChange = libraryViewModel::onPendoloTuningChange,
                    onTemperaTuningChange = libraryViewModel::onTemperaTuningChange,
                    onToolClusterStyleChange = libraryViewModel::onToolClusterStyleChange,
                    onCardCoverByDefaultChange = libraryViewModel::onCardCoverByDefaultChange,
                    onResetAppearanceDefaults = libraryViewModel::resetAppearanceDefaults,
                    onOpenSubtitleDetail = libraryViewModel::openSubtitleDetail,
                    onCloseSubtitleDetail = libraryViewModel::closeSubtitleDetail,
                    onPlayItem = { item ->
                        // The queue is built from the listing on screen, so this is
                        // the one moment that knows which folder the queue belongs
                        // to -- and therefore where "focus the playing item" has to
                        // go looking for it later.
                        libraryViewModel.notePlaybackOrigin()
                        val needsNotificationPermission =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                        if (needsNotificationPermission && !notificationPermissionRequested) {
                            notificationPermissionRequested = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        playback.playFromFolder(libraryState.items, item, libraryState.timedText)
                    },
                    // The wall only offers this when it can be honoured, so the
                    // refusal is not something the UI has to react to.
                    onRevealPlayingItem = { libraryViewModel.revealPlayingItem(it) },
                    onTogglePlayPause = playback::togglePlayPause,
                    onSeekTo = playback::seekTo,
                    onSeekBy = playback::seekBy,
                    onNext = playback::skipToNext,
                    onPrevious = playback::skipToPrevious,
                    onRepeatModeChange = playback::setRepeatMode,
                    onSleepTimerChange = playback::setSleepTimer,
                )
            }
        }
    }
}
