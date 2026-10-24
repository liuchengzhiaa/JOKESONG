package com.example.asmrplayer.feature.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.asmrplayer.core.artwork.FolderCoverRepository
import com.example.asmrplayer.core.artwork.FolderCoverBatchState
import com.example.asmrplayer.core.artwork.FolderCoverBatchStatus
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import com.example.asmrplayer.core.settings.ToolClusterStyle
import com.example.asmrplayer.core.settings.UiAccentPalette
import com.example.asmrplayer.core.settings.UiStylePreset
import com.example.asmrplayer.core.storage.AppPreferences
import com.example.asmrplayer.core.storage.AppSettings
import com.example.asmrplayer.core.storage.FolderSnapshot
import com.example.asmrplayer.core.storage.FolderSnapshotCache
import com.example.asmrplayer.core.storage.RootPreferences
import com.example.asmrplayer.core.storage.SafDirectoryScanner
import com.example.asmrplayer.core.subtitles.TimedTextCache
import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.core.subtitles.TimedTextMatch
import com.example.asmrplayer.core.subtitles.TimedTextReader
import com.example.asmrplayer.core.subtitles.cacheKey
import com.example.asmrplayer.core.subtitles.parseTimedText
import com.example.asmrplayer.feature.focuswall.FoliaFocusShape
import com.example.asmrplayer.feature.focuswall.FoliaFocusSize
import com.example.asmrplayer.feature.subtitles.LuminousTuning
import com.example.asmrplayer.feature.subtitles.LyricsVisualizerBackground
import com.example.asmrplayer.feature.subtitles.LyricsVisualizerMode
import com.example.asmrplayer.feature.subtitles.LyricsWordGlowMode
import com.example.asmrplayer.feature.subtitles.PendoloTuning
import com.example.asmrplayer.feature.subtitles.SubtitleDetailState
import com.example.asmrplayer.feature.subtitles.TemperaTuning
import com.example.asmrplayer.feature.subtitles.clamped
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val hasRoot: Boolean = false,
    val folderKey: String = "",
    val folderTitle: String = "",
    val items: List<LibraryItem> = emptyList(),
    val supportedItemCount: Int = 0,
    val isScanning: Boolean = false,
    val isTruncated: Boolean = false,
    val canNavigateUp: Boolean = false,
    val hasError: Boolean = false,
    /** True when this listing came from the in-memory snapshot, not a scan. */
    val isRestored: Boolean = false,
    /**
     * True once a play started in this session, which is what makes the folder a
     * running queue belongs to knowable. See [LibraryViewModel.revealPlayingItem].
     */
    val hasPlaybackOrigin: Boolean = false,
    val focusedItemId: String? = null,
    val canvasZoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val libraryLayoutOrdinal: Int = AppSettings.DefaultLibraryLayoutOrdinal,
    val wallDensityPercent: Int = 100,
    /** Shape of the Folia wall's focused cover; the Bento wall ignores it. */
    val wallFocusShapeOrdinal: Int = FoliaFocusShape.Square.ordinal,
    /** How large the Folia wall's focused cover grows. */
    val wallFocusSizeOrdinal: Int = FoliaFocusSize.Medium.ordinal,
    /** Strength of the focused-cover ambient wash on the Folia wall. */
    val wallAmbientPercent: Int = 100,
    /** Dim every Folia poster except the focused and currently playing items. */
    val wallLightsOut: Boolean = false,
    /** Follow Media3 item changes back to their poster. */
    val wallAutoFocusPlaying: Boolean = false,
    /** Strength of the cover-colour wash over ordinary Folia posters. */
    val wallPosterTintPercent: Int = 0,
    /** Shared transition intensity for current and future global UI motion. */
    val motionPercent: Int = 100,
    val uiStylePresetOrdinal: Int = UiStylePreset.Balanced.ordinal,
    val uiAccentPaletteOrdinal: Int = UiAccentPalette.Ocean.ordinal,
    val uiScalePercent: Int = 100,
    /** Presentation of every collapsible tool cluster; one choice drives all of them. */
    val toolClusterStyleOrdinal: Int = ToolClusterStyle.Default.ordinal,
    /** Whether the full player opens on a song's card rather than on its cover. */
    val cardCoverByDefault: Boolean = false,
    /** Draggable global record position, normalized inside the current safe viewport. */
    val globalVinylXPerMille: Int = 0,
    val globalVinylYPerMille: Int = 1_000,
    /** Persisted full-screen lyric presentation. */
    val lyricsVisualizerOrdinal: Int = LyricsVisualizerMode.Default.storedValue,
    /** Persisted lyric backdrop, independent of the visualizer mode above. */
    val lyricsBackgroundOrdinal: Int? = null,
    /** Persisted source policy for exact or presentation-estimated word glow. */
    val lyricsWordGlowModeOrdinal: Int = LyricsWordGlowMode.Default.ordinal,
    /** The word cloud's own parameters; only the 流光 mode reads them. */
    val luminousTuning: LuminousTuning = LuminousTuning(),
    /** The escapement wheel's own parameters; only the 时计 mode reads them. */
    val pendoloTuning: PendoloTuning = PendoloTuning(),
    /** The 凝彩 stage shape and its user-managed SAF image pool. */
    val temperaTuning: TemperaTuning = TemperaTuning(),
    /**
     * Which track in this listing has lyrics beside it, keyed by item id. Empty when
     * none of them do, which is the ordinary case and not a failure.
     */
    val timedText: Map<String, TimedTextMatch> = emptyMap(),
)

/**
 * Folder browsing with memory-backed back navigation.
 *
 * A visited folder is kept in a bounded [FolderSnapshotCache], so pressing Back
 * restores the parent instantly from memory instead of scanning the provider
 * again. Only an explicit refresh invalidates the current folder's entry.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val contentResolver = application.contentResolver
    private val rootPreferences = RootPreferences(application)
    private val appPreferences = AppPreferences(application)
    private val folderCovers = FolderCoverRepository(application)
    private val scanner = SafDirectoryScanner(contentResolver)
    private val folderSnapshots = FolderSnapshotCache()
    private val timedTextReader = TimedTextReader(contentResolver)
    private val timedTextCache = TimedTextCache()
    private val folderStack = ArrayDeque<Uri>()
    private var treeUri: Uri? = null
    private var scanJob: Job? = null
    private var coverBatchJob: Job? = null
    private val mutableCoverBatch = MutableStateFlow(FolderCoverBatchState())
    val coverBatch = mutableCoverBatch.asStateFlow()
    private var wallZoomPersistJob: Job? = null
    private var subtitleJob: Job? = null

    /**
     * The folder chain the running queue was built from. A queue is always built
     * from one folder listing, so remembering the chain at the moment playback
     * starts is enough to follow any of its tracks back -- no scan, and no
     * document-URI-to-path map.
     */
    private var playbackOrigin: List<Uri> = emptyList()
    private var annotatedSubtitle: Pair<String, TimedTextMatch?>? = null
    private var pendingFocusItemId: String? = null
    private var appSettings = AppSettings()
    private var receivedInitialAppSettings = false

    private val mutableUiState = MutableStateFlow(LibraryUiState(isScanning = true))
    val uiState = mutableUiState.asStateFlow()

    private val mutableSubtitleState = MutableStateFlow(SubtitleDetailState())

    /**
     * The lyrics of whichever track's subtitle detail is open, published separately
     * from [uiState] because the two have different lifetimes: browsing replaces the
     * whole library state on every folder change, and the lyrics of the track that is
     * playing must survive that.
     */
    val subtitleState = mutableSubtitleState.asStateFlow()

    init {
        viewModelScope.launch {
            appPreferences.settings.collectLatest { settings ->
                appSettings = settings
                val applyWallDefaults = !receivedInitialAppSettings
                receivedInitialAppSettings = true
                mutableUiState.update { it.withAppSettings(settings, applyWallDefaults) }
            }
        }
        viewModelScope.launch {
            rootPreferences.rootUri.collectLatest { persistedRoot ->
                if (treeUri != persistedRoot) coverBatchJob?.cancel()
                scanJob?.cancel()
                folderStack.clear()
                folderSnapshots.clear()
                playbackOrigin = emptyList()
                annotatedSubtitle = null
                pendingFocusItemId = null
                treeUri = persistedRoot
                if (persistedRoot == null) {
                    mutableUiState.value = LibraryUiState().withAppSettings(
                        appSettings,
                        applyWallDefaults = true,
                    )
                } else {
                    val rootDocumentUri = scanner.rootDocumentUri(persistedRoot)
                    folderStack.addLast(rootDocumentUri)
                    mutableUiState.value = LibraryUiState(
                        hasRoot = true,
                        isScanning = true,
                    ).withAppSettings(appSettings, applyWallDefaults = true)
                    loadFolder(rootDocumentUri)
                }
            }
        }
    }

    fun selectRoot(uri: Uri) {
        viewModelScope.launch {
            val previousRoot = treeUri
            val persisted = persistPermission(uri)
            if (persisted) {
                if (previousRoot != uri) coverBatchJob?.cancelAndJoin()
                rootPreferences.setRootUri(uri)
                if (previousRoot != null && previousRoot != uri) {
                    releasePermission(previousRoot)
                }
            } else {
                mutableUiState.update { it.copy(isScanning = false, hasError = true) }
            }
        }
    }

    fun openFolder(item: LibraryItem) {
        if (item.type != LibraryItemType.Folder) return
        val uri = item.documentUri?.let(Uri::parse) ?: return
        folderStack.addLast(uri)
        loadFolder(uri)
    }

    fun openAnnotatedFolder(entry: com.example.asmrplayer.core.annotations.UserItemEntity) {
        val root = treeUri ?: return
        if (entry.rootUri != root.toString() || entry.kind != "Folder") return
        val rootDocument = scanner.rootDocumentUri(root)
        folderStack.clear()
        folderStack.addLast(rootDocument)
        val target = Uri.parse(entry.documentUri)
        if (target != rootDocument) folderStack.addLast(target)
        loadFolder(target)
    }

    fun noteAnnotatedPlayback(entry: com.example.asmrplayer.core.annotations.UserItemEntity) {
        val root = treeUri ?: return
        if (entry.rootUri != root.toString() || entry.kind != "Audio") return
        annotatedSubtitle = entry.documentUri to entry.timedText()
        val rootDocument = scanner.rootDocumentUri(root)
        playbackOrigin = listOfNotNull(rootDocument, entry.parentUri?.let(Uri::parse)).distinct()
        mutableUiState.update { it.copy(hasPlaybackOrigin = true) }
    }

    fun navigateUp(): Boolean {
        if (folderStack.size <= 1) return false
        folderStack.removeLast()
        folderStack.lastOrNull()?.let(::loadFolder)
        return true
    }

    /** Patch only the edited item's URI/artwork; retain folder focus and viewport. */
    fun refreshAfterMetadataEdit(id: String, saved: com.example.asmrplayer.core.metadata.TagSaveResult) {
        fun project(items: List<LibraryItem>) = items.map { item ->
            if (item.id == id) item.copy(documentUri = saved.documentUri, artworkUri = saved.artworkUri) else item
        }
        folderSnapshots.updateAll { it.copy(items = project(it.items)) }
        mutableUiState.update { it.copy(items = project(it.items)) } // Preserve focus/viewport, no re-scan spinner.
    }

    /** The picker captures the folder identity; navigation during selection never retargets it. */
    suspend fun setFolderCover(folderUri: String, image: Uri?): Boolean {
        return try {
            folderCovers.set(folderUri, image)
            folderStack.peekLast()?.let(::loadFolder) // Raw snapshot + new projection; no rescan.
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setAudioAsFolderCover(folderUri: String, audioUri: String): Boolean = try {
        val saved = folderCovers.setFromAudio(folderUri, audioUri)
        if (saved) folderStack.peekLast()?.let(::loadFolder)
        saved
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { false }

    /** Explicit, cancellable whole-root operation. Navigation/rotation do not restart it. */
    fun startFolderCoverBatch() {
        if (coverBatchJob?.isActive == true) return
        val root = treeUri ?: return
        mutableCoverBatch.value = FolderCoverBatchState(status = FolderCoverBatchStatus.Running)
        coverBatchJob = viewModelScope.launch {
            var latest = com.example.asmrplayer.core.artwork.FolderCoverBatchCounts()
            var lastPublished = 0L
            try {
                val result = folderCovers.processTree(root) { counts ->
                    latest = counts
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastPublished >= 200) {
                        mutableCoverBatch.value = FolderCoverBatchState(FolderCoverBatchStatus.Running, counts)
                        lastPublished = now
                    }
                }
                mutableCoverBatch.value = FolderCoverBatchState(FolderCoverBatchStatus.Completed, result)
            } catch (cancelled: CancellationException) {
                mutableCoverBatch.value = FolderCoverBatchState(FolderCoverBatchStatus.Cancelled, latest)
            } catch (_: Exception) {
                mutableCoverBatch.value = FolderCoverBatchState(FolderCoverBatchStatus.Failed, latest)
            } finally {
                if (treeUri == root) folderStack.peekLast()?.let(::loadFolder)
            }
        }
    }
    fun cancelFolderCoverBatch() { coverBatchJob?.cancel() }

    /** Explicit refresh: drop the snapshot and scan the folder again. */
    fun refresh() {
        folderStack.peekLast()?.let { loadFolder(it, forceRefresh = true) }
    }

    /** Focus is discrete, so it is also published as UI state. */
    fun onFocusedItemChange(itemId: String?) {
        val folderKey = currentFolderKey() ?: return
        folderSnapshots.update(folderKey) { it.copy(focusedItemId = itemId) }
        mutableUiState.update { state ->
            if (state.folderKey == folderKey) state.copy(focusedItemId = itemId) else state
        }
    }

    /**
     * Records which folder the queue now starting was built from, so focus can be
     * brought back to any of its tracks later. Called by the play path, which is
     * the only place that knows both the listing and the track.
     */
    fun notePlaybackOrigin() {
        annotatedSubtitle = null
        if (folderStack.isEmpty()) return
        playbackOrigin = folderStack.toList()
        mutableUiState.update { it.copy(hasPlaybackOrigin = true) }
    }

    /**
     * Moves the wall's focus onto [itemId], following the running queue back to the
     * folder it started from when the item is not in the current listing.
     *
     * Returns false when the item cannot be reached at all, which is exactly the
     * case [canRevealPlayingItem] reports as disabled.
     */
    fun revealPlayingItem(itemId: String): Boolean {
        if (mutableUiState.value.items.any { it.id == itemId }) {
            onFocusedItemChange(itemId)
            return true
        }
        if (playbackOrigin.isEmpty()) return false

        // The listing arrives asynchronously, and either path publishes a whole new
        // state that would drop a focus set now, so it waits for the folder.
        pendingFocusItemId = itemId
        folderStack.clear()
        folderStack.addAll(playbackOrigin)
        loadFolder(playbackOrigin.last())
        return true
    }

    /**
     * Applies a focus that was requested before its folder was loaded. Called after
     * every path that publishes a listing.
     */
    private fun applyPendingFocus() {
        val target = pendingFocusItemId ?: return
        pendingFocusItemId = null
        onFocusedItemChange(target)
    }

    /**
     * Loads the lyrics of [itemId] for the subtitle detail surface.
     *
     * The matched file is read once and the parsed document cached, so closing and
     * reopening the surface -- or the queue moving on and coming back -- does not go
     * to the document provider again. Reopening on a track with no match publishes the
     * unmatched state immediately rather than leaving the previous track's lines up.
     */
    fun openSubtitleDetail(itemId: String) {
        subtitleJob?.cancel()
        val match = mutableUiState.value.timedText[itemId]
            ?: annotatedSubtitle?.takeIf { it.first == itemId }?.second
        if (match == null) {
            mutableSubtitleState.value = SubtitleDetailState.unmatched(itemId)
            return
        }

        val key = match.candidate.cacheKey()
        timedTextCache.get(key)?.let { cached ->
            mutableSubtitleState.value = match.toDetailState(itemId, cached)
            return
        }

        mutableSubtitleState.value = match.toDetailState(itemId, document = null, isLoading = true)
        subtitleJob = viewModelScope.launch {
            val source = timedTextReader.read(match.candidate.documentUri.toUri())
            val document = source?.let { parseTimedText(match.candidate.format, it) }
            if (document != null) timedTextCache.put(key, document)
            mutableSubtitleState.value = match.toDetailState(itemId, document)
        }
    }

    fun closeSubtitleDetail() {
        subtitleJob?.cancel()
        mutableSubtitleState.value = SubtitleDetailState()
    }

    /**
     * Viewport updates arrive on every drag frame. They are written straight to
     * the snapshot and deliberately do not publish UI state, so panning never
     * triggers a recomposition of the library.
     */
    fun onViewportChange(zoom: Float, panX: Float, panY: Float) {
        val folderKey = currentFolderKey() ?: return
        folderSnapshots.update(folderKey) {
            it.copy(canvasZoom = zoom, panX = panX, panY = panY)
        }
        wallZoomPersistJob?.cancel()
        wallZoomPersistJob = viewModelScope.launch {
            delay(350)
            appPreferences.setWallZoom((zoom * 100).toInt())
        }
    }

    fun onLibraryLayoutChange(ordinal: Int) {
        val safeOrdinal = LibraryLayoutMode.fromOrdinal(ordinal).ordinal
        mutableUiState.update { it.copy(libraryLayoutOrdinal = safeOrdinal) }
        viewModelScope.launch { appPreferences.setLibraryLayout(safeOrdinal) }
    }

    fun onWallDensityChange(percent: Int) {
        val folderKey = currentFolderKey() ?: return
        val safePercent = percent.coerceIn(70, 130)
        folderSnapshots.update(folderKey) { it.copy(wallDensityPercent = safePercent) }
        mutableUiState.update { it.copy(wallDensityPercent = safePercent) }
        viewModelScope.launch { appPreferences.setWallDensity(safePercent) }
    }

    fun onWallFocusShapeChange(ordinal: Int) {
        val folderKey = currentFolderKey() ?: return
        folderSnapshots.update(folderKey) { it.copy(wallFocusShapeOrdinal = ordinal) }
        mutableUiState.update { it.copy(wallFocusShapeOrdinal = ordinal) }
        viewModelScope.launch { appPreferences.setWallFocusShape(ordinal) }
    }

    fun onWallFocusSizeChange(ordinal: Int) {
        val folderKey = currentFolderKey() ?: return
        folderSnapshots.update(folderKey) { it.copy(wallFocusSizeOrdinal = ordinal) }
        mutableUiState.update { it.copy(wallFocusSizeOrdinal = ordinal) }
        viewModelScope.launch { appPreferences.setWallFocusSize(ordinal) }
    }

    fun onWallAmbientStrengthChange(percent: Int) {
        val folderKey = currentFolderKey() ?: return
        val safePercent = percent.coerceIn(0, 150)
        folderSnapshots.update(folderKey) { it.copy(wallAmbientPercent = safePercent) }
        mutableUiState.update { it.copy(wallAmbientPercent = safePercent) }
        viewModelScope.launch { appPreferences.setWallAmbient(safePercent) }
    }

    fun onWallLightsOutChange(enabled: Boolean) {
        mutableUiState.update { it.copy(wallLightsOut = enabled) }
        viewModelScope.launch { appPreferences.setLightsOut(enabled) }
    }

    fun onWallAutoFocusPlayingChange(enabled: Boolean) {
        mutableUiState.update { it.copy(wallAutoFocusPlaying = enabled) }
        viewModelScope.launch { appPreferences.setAutoFocusPlaying(enabled) }
    }

    fun onWallPosterTintStrengthChange(percent: Int) {
        val safePercent = percent.coerceIn(0, 60)
        mutableUiState.update { it.copy(wallPosterTintPercent = safePercent) }
        viewModelScope.launch { appPreferences.setPosterTint(safePercent) }
    }

    fun onMotionPercentChange(percent: Int) {
        val safePercent = percent.coerceIn(0, 100)
        mutableUiState.update {
            it.copy(
                motionPercent = safePercent,
                uiStylePresetOrdinal = UiStylePreset.Custom.ordinal,
            )
        }
        viewModelScope.launch { appPreferences.setMotionPercent(safePercent) }
    }

    fun onUiAccentPaletteChange(ordinal: Int) {
        val safeOrdinal = UiAccentPalette.fromOrdinal(ordinal).ordinal
        mutableUiState.update {
            it.copy(
                uiAccentPaletteOrdinal = safeOrdinal,
                uiStylePresetOrdinal = UiStylePreset.Custom.ordinal,
            )
        }
        viewModelScope.launch { appPreferences.setUiAccentPalette(safeOrdinal) }
    }

    fun onUiScalePercentChange(percent: Int) {
        val safePercent = percent.coerceIn(85, 120)
        mutableUiState.update {
            it.copy(
                uiScalePercent = safePercent,
                uiStylePresetOrdinal = UiStylePreset.Custom.ordinal,
            )
        }
        viewModelScope.launch { appPreferences.setUiScalePercent(safePercent) }
    }

    fun onToolClusterStyleChange(ordinal: Int) {
        val safeOrdinal = ToolClusterStyle.fromOrdinal(ordinal).ordinal
        mutableUiState.update { it.copy(toolClusterStyleOrdinal = safeOrdinal) }
        viewModelScope.launch { appPreferences.setToolClusterStyle(safeOrdinal) }
    }

    fun onCardCoverByDefaultChange(enabled: Boolean) {
        mutableUiState.update { it.copy(cardCoverByDefault = enabled) }
        viewModelScope.launch { appPreferences.setCardCoverByDefault(enabled) }
    }

    fun onGlobalVinylPositionChange(xPerMille: Int, yPerMille: Int) {
        val safeX = xPerMille.coerceIn(0, 1_000)
        val safeY = yPerMille.coerceIn(0, 1_000)
        mutableUiState.update {
            it.copy(
                globalVinylXPerMille = safeX,
                globalVinylYPerMille = safeY,
            )
        }
        viewModelScope.launch { appPreferences.setGlobalVinylPosition(safeX, safeY) }
    }

    fun onLyricsVisualizerModeChange(ordinal: Int) {
        val safeOrdinal = LyricsVisualizerMode.fromOrdinal(ordinal).storedValue
        mutableUiState.update { it.copy(lyricsVisualizerOrdinal = safeOrdinal) }
        viewModelScope.launch { appPreferences.setLyricsVisualizer(safeOrdinal) }
    }

    /** Null restores "follow the mode" rather than pinning the current background. */
    fun onLyricsBackgroundChange(ordinal: Int?) {
        val safeOrdinal = ordinal
            ?.let { LyricsVisualizerBackground.fromOrdinal(it).ordinal }
        mutableUiState.update { it.copy(lyricsBackgroundOrdinal = safeOrdinal) }
        viewModelScope.launch { appPreferences.setLyricsBackground(safeOrdinal) }
    }

    fun onLyricsWordGlowModeChange(ordinal: Int) {
        val safeOrdinal = LyricsWordGlowMode.fromOrdinal(ordinal).ordinal
        mutableUiState.update { it.copy(lyricsWordGlowModeOrdinal = safeOrdinal) }
        viewModelScope.launch { appPreferences.setLyricsWordGlowMode(safeOrdinal) }
    }

    /**
     * A mode's parameters arrive together, already at the value the gesture ended on.
     *
     * The sheet previews a drag locally and only calls this on release, so every call
     * here is a commit and every commit is persisted; a drag frame never reaches
     * storage.
     */
    fun onLuminousTuningChange(tuning: LuminousTuning) {
        val safe = tuning.clamped()
        mutableUiState.update { it.copy(luminousTuning = safe) }
        viewModelScope.launch { appPreferences.setLuminousTuning(safe) }
    }

    fun onPendoloTuningChange(tuning: PendoloTuning) {
        val safe = tuning.clamped()
        mutableUiState.update { it.copy(pendoloTuning = safe) }
        viewModelScope.launch { appPreferences.setPendoloTuning(safe) }
    }

    fun onTemperaTuningChange(tuning: TemperaTuning) {
        val safe = tuning.clamped()
        mutableUiState.update { it.copy(temperaTuning = safe) }
        viewModelScope.launch { appPreferences.setTemperaTuning(safe) }
    }

    fun onUiStylePresetChange(ordinal: Int) {
        val preset = UiStylePreset.fromOrdinal(ordinal).takeUnless { it == UiStylePreset.Custom }
            ?: UiStylePreset.Balanced
        mutableUiState.update {
            it.copy(
                uiStylePresetOrdinal = preset.ordinal,
                uiAccentPaletteOrdinal = preset.accent.ordinal,
                motionPercent = preset.motionPercent,
                uiScalePercent = preset.uiScalePercent,
            )
        }
        viewModelScope.launch { appPreferences.applyUiStylePreset(preset.ordinal) }
    }

    fun resetAppearanceDefaults() {
        appSettings = AppSettings()
        folderSnapshots.updateAll { snapshot ->
            snapshot.copy(
                canvasZoom = appSettings.wallZoomPercent / 100f,
                wallDensityPercent = appSettings.wallDensityPercent,
                wallFocusShapeOrdinal = appSettings.wallFocusShapeOrdinal,
                wallFocusSizeOrdinal = appSettings.wallFocusSizeOrdinal,
                wallAmbientPercent = appSettings.wallAmbientPercent,
            )
        }
        mutableUiState.update { it.withAppSettings(appSettings, applyWallDefaults = true) }
        viewModelScope.launch { appPreferences.resetDefaults() }
    }

    private fun currentFolderKey(): String? = folderStack.peekLast()?.toString()

    private fun loadFolder(folderUri: Uri, forceRefresh: Boolean = false) {
        val activeTreeUri = treeUri ?: return
        val folderKey = folderUri.toString()
        scanJob?.cancel()

        if (forceRefresh) folderSnapshots.invalidate(folderKey)

        val restored = if (forceRefresh) null else folderSnapshots.get(folderKey)
        if (restored != null) {
            // Jumping to a track is not back navigation: the restored viewport would
            // otherwise swallow the one animation that brings the camera to it.
            scanJob = viewModelScope.launch {
                try {
                    val presented = folderCovers.project(restored.items)
                    mutableUiState.value = restored.toUiState(
                        canNavigateUp = folderStack.size > 1,
                        restored = pendingFocusItemId == null,
                        hasPlaybackOrigin = playbackOrigin.isNotEmpty(),
                    ).copy(items = presented).withAppSettings(appSettings, applyWallDefaults = false)
                    applyPendingFocus()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    mutableUiState.update { it.copy(isScanning = false, hasError = true) }
                }
            }
            return
        }

        val isFolderChange = mutableUiState.value.folderKey != folderKey
        mutableUiState.value = if (isFolderChange) {
            // A different folder must never inherit the previous viewport.
            LibraryUiState(
                hasRoot = true,
                folderKey = folderKey,
                isScanning = true,
                canNavigateUp = folderStack.size > 1,
                hasPlaybackOrigin = playbackOrigin.isNotEmpty(),
            ).withAppSettings(appSettings, applyWallDefaults = true)
        } else {
            mutableUiState.value.copy(isScanning = true, hasError = false, isRestored = false)
        }

        scanJob = viewModelScope.launch {
            try {
                val listing = scanner.scan(activeTreeUri, folderUri)
                val presented = folderCovers.project(listing.items)
                folderSnapshots.put(
                    FolderSnapshot(
                        folderKey = folderKey,
                        folderTitle = listing.folderName,
                        items = listing.items,
                        supportedItemCount = listing.supportedItemCount,
                        isTruncated = listing.isTruncated,
                        timedText = listing.timedText,
                    ),
                )
                mutableUiState.value = LibraryUiState(
                    hasRoot = true,
                    folderKey = folderKey,
                    folderTitle = listing.folderName,
                    items = presented,
                    supportedItemCount = listing.supportedItemCount,
                    isTruncated = listing.isTruncated,
                    isScanning = false,
                    canNavigateUp = folderStack.size > 1,
                    hasPlaybackOrigin = playbackOrigin.isNotEmpty(),
                    timedText = listing.timedText,
                ).withAppSettings(appSettings, applyWallDefaults = true)
                applyPendingFocus()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.update {
                    it.copy(
                        isScanning = false,
                        canNavigateUp = folderStack.size > 1,
                        hasError = true,
                    )
                }
            }
        }
    }

    private fun persistPermission(uri: Uri): Boolean {
        val readFlag = Intent.FLAG_GRANT_READ_URI_PERMISSION
        return try {
            contentResolver.takePersistableUriPermission(
                uri,
                readFlag or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        } catch (_: SecurityException) {
            try {
                contentResolver.takePersistableUriPermission(uri, readFlag)
                true
            } catch (_: SecurityException) {
                false
            }
        }
    }

    private fun releasePermission(uri: Uri) {
        val permission = contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        var flags = 0
        if (permission.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (permission.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (flags == 0) return
        try {
            contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // The old provider may already have revoked the grant; the new root remains selected.
        }
    }
}

private fun TimedTextMatch.toDetailState(
    itemId: String,
    document: TimedTextDocument?,
    isLoading: Boolean = false,
): SubtitleDetailState = SubtitleDetailState(
    itemId = itemId,
    isLoading = isLoading,
    sourceName = candidate.displayName,
    languageTag = languageTag,
    document = document,
)

private fun FolderSnapshot.toUiState(
    canNavigateUp: Boolean,
    restored: Boolean,
    hasPlaybackOrigin: Boolean,
): LibraryUiState = LibraryUiState(
    hasRoot = true,
    folderKey = folderKey,
    folderTitle = folderTitle,
    items = items,
    supportedItemCount = supportedItemCount,
    isTruncated = isTruncated,
    isScanning = false,
    canNavigateUp = canNavigateUp,
    isRestored = restored,
    hasPlaybackOrigin = hasPlaybackOrigin,
    focusedItemId = focusedItemId,
    canvasZoom = canvasZoom,
    panX = panX,
    panY = panY,
    wallDensityPercent = wallDensityPercent,
    wallFocusShapeOrdinal = wallFocusShapeOrdinal,
    wallFocusSizeOrdinal = wallFocusSizeOrdinal,
    wallAmbientPercent = wallAmbientPercent,
    timedText = timedText,
)

private fun LibraryUiState.withAppSettings(
    settings: AppSettings,
    applyWallDefaults: Boolean,
): LibraryUiState = copy(
    libraryLayoutOrdinal = settings.libraryLayoutOrdinal,
    canvasZoom = if (applyWallDefaults) settings.wallZoomPercent / 100f else canvasZoom,
    wallDensityPercent = if (applyWallDefaults) settings.wallDensityPercent else wallDensityPercent,
    wallFocusShapeOrdinal = if (applyWallDefaults) {
        settings.wallFocusShapeOrdinal
    } else {
        wallFocusShapeOrdinal
    },
    wallFocusSizeOrdinal = if (applyWallDefaults) {
        settings.wallFocusSizeOrdinal
    } else {
        wallFocusSizeOrdinal
    },
    wallAmbientPercent = if (applyWallDefaults) settings.wallAmbientPercent else wallAmbientPercent,
    wallLightsOut = settings.wallLightsOut,
    wallAutoFocusPlaying = settings.wallAutoFocusPlaying,
    wallPosterTintPercent = settings.wallPosterTintPercent,
    motionPercent = settings.motionPercent,
    uiStylePresetOrdinal = settings.uiStylePresetOrdinal,
    uiAccentPaletteOrdinal = settings.uiAccentPaletteOrdinal,
    uiScalePercent = settings.uiScalePercent,
    toolClusterStyleOrdinal = settings.toolClusterStyleOrdinal,
    cardCoverByDefault = settings.cardCoverByDefault,
    globalVinylXPerMille = settings.globalVinylXPerMille,
    globalVinylYPerMille = settings.globalVinylYPerMille,
    lyricsVisualizerOrdinal = LyricsVisualizerMode
        .fromOrdinal(settings.lyricsVisualizerOrdinal)
        .storedValue,
    lyricsBackgroundOrdinal = settings.lyricsBackgroundOrdinal
        ?.let { LyricsVisualizerBackground.fromOrdinal(it).ordinal },
    lyricsWordGlowModeOrdinal = LyricsWordGlowMode
        .fromOrdinal(settings.lyricsWordGlowModeOrdinal)
        .ordinal,
    luminousTuning = settings.luminousTuning,
    pendoloTuning = settings.pendoloTuning,
    temperaTuning = settings.temperaTuning,
)
