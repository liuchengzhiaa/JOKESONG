package com.example.asmrplayer.feature.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import com.example.asmrplayer.core.storage.FolderSnapshot
import com.example.asmrplayer.core.storage.FolderSnapshotCache
import com.example.asmrplayer.core.storage.RootPreferences
import com.example.asmrplayer.core.storage.SafDirectoryScanner
import com.example.asmrplayer.feature.focuswall.FocusWallDensity
import com.example.asmrplayer.feature.focuswall.FoliaFocusShape
import com.example.asmrplayer.feature.focuswall.FoliaFocusSize
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val wallDensityOrdinal: Int = FocusWallDensity.Medium.ordinal,
    /** Shape of the Folia wall's focused cover; the Bento wall ignores it. */
    val wallFocusShapeOrdinal: Int = FoliaFocusShape.Square.ordinal,
    /** How large the Folia wall's focused cover grows. */
    val wallFocusSizeOrdinal: Int = FoliaFocusSize.Medium.ordinal,
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
    private val scanner = SafDirectoryScanner(contentResolver)
    private val folderSnapshots = FolderSnapshotCache()
    private val folderStack = ArrayDeque<Uri>()
    private var treeUri: Uri? = null
    private var scanJob: Job? = null

    /**
     * The folder chain the running queue was built from. A queue is always built
     * from one folder listing, so remembering the chain at the moment playback
     * starts is enough to follow any of its tracks back -- no scan, and no
     * document-URI-to-path map.
     */
    private var playbackOrigin: List<Uri> = emptyList()
    private var pendingFocusItemId: String? = null

    private val mutableUiState = MutableStateFlow(LibraryUiState(isScanning = true))
    val uiState = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            rootPreferences.rootUri.collectLatest { persistedRoot ->
                scanJob?.cancel()
                folderStack.clear()
                folderSnapshots.clear()
                playbackOrigin = emptyList()
                pendingFocusItemId = null
                treeUri = persistedRoot
                if (persistedRoot == null) {
                    mutableUiState.value = LibraryUiState()
                } else {
                    val rootDocumentUri = scanner.rootDocumentUri(persistedRoot)
                    folderStack.addLast(rootDocumentUri)
                    mutableUiState.value = LibraryUiState(hasRoot = true, isScanning = true)
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

    fun navigateUp(): Boolean {
        if (folderStack.size <= 1) return false
        folderStack.removeLast()
        folderStack.lastOrNull()?.let(::loadFolder)
        return true
    }

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
     * Viewport updates arrive on every drag frame. They are written straight to
     * the snapshot and deliberately do not publish UI state, so panning never
     * triggers a recomposition of the library.
     */
    fun onViewportChange(zoom: Float, panX: Float, panY: Float) {
        val folderKey = currentFolderKey() ?: return
        folderSnapshots.update(folderKey) {
            it.copy(canvasZoom = zoom, panX = panX, panY = panY)
        }
    }

    fun onWallDensityChange(ordinal: Int) {
        val folderKey = currentFolderKey() ?: return
        folderSnapshots.update(folderKey) { it.copy(wallDensityOrdinal = ordinal) }
    }

    fun onWallFocusShapeChange(ordinal: Int) {
        val folderKey = currentFolderKey() ?: return
        folderSnapshots.update(folderKey) { it.copy(wallFocusShapeOrdinal = ordinal) }
    }

    fun onWallFocusSizeChange(ordinal: Int) {
        val folderKey = currentFolderKey() ?: return
        folderSnapshots.update(folderKey) { it.copy(wallFocusSizeOrdinal = ordinal) }
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
            mutableUiState.value = restored.toUiState(
                canNavigateUp = folderStack.size > 1,
                restored = pendingFocusItemId == null,
                hasPlaybackOrigin = playbackOrigin.isNotEmpty(),
            )
            applyPendingFocus()
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
            )
        } else {
            mutableUiState.value.copy(isScanning = true, hasError = false, isRestored = false)
        }

        scanJob = viewModelScope.launch {
            try {
                val listing = scanner.scan(activeTreeUri, folderUri)
                folderSnapshots.put(
                    FolderSnapshot(
                        folderKey = folderKey,
                        folderTitle = listing.folderName,
                        items = listing.items,
                        supportedItemCount = listing.supportedItemCount,
                        isTruncated = listing.isTruncated,
                    ),
                )
                mutableUiState.value = LibraryUiState(
                    hasRoot = true,
                    folderKey = folderKey,
                    folderTitle = listing.folderName,
                    items = listing.items,
                    supportedItemCount = listing.supportedItemCount,
                    isTruncated = listing.isTruncated,
                    isScanning = false,
                    canNavigateUp = folderStack.size > 1,
                    hasPlaybackOrigin = playbackOrigin.isNotEmpty(),
                )
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
    wallDensityOrdinal = wallDensityOrdinal,
    wallFocusShapeOrdinal = wallFocusShapeOrdinal,
    wallFocusSizeOrdinal = wallFocusSizeOrdinal,
)
