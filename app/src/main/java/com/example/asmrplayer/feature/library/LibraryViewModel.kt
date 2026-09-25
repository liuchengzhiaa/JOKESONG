package com.example.asmrplayer.feature.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import com.example.asmrplayer.core.storage.RootPreferences
import com.example.asmrplayer.core.storage.SafDirectoryScanner
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
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val contentResolver = application.contentResolver
    private val rootPreferences = RootPreferences(application)
    private val scanner = SafDirectoryScanner(contentResolver)
    private val folderStack = ArrayDeque<Uri>()
    private var treeUri: Uri? = null
    private var scanJob: Job? = null

    private val mutableUiState = MutableStateFlow(LibraryUiState(isScanning = true))
    val uiState = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            rootPreferences.rootUri.collectLatest { persistedRoot ->
                scanJob?.cancel()
                folderStack.clear()
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
        loadFolder(folderStack.peekLast())
        return true
    }

    fun refresh() {
        folderStack.peekLast()?.let(::loadFolder)
    }

    private fun loadFolder(folderUri: Uri) {
        val activeTreeUri = treeUri ?: return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            mutableUiState.update {
                val isFolderChange = it.folderKey != folderUri.toString()
                it.copy(
                    hasRoot = true,
                    folderKey = folderUri.toString(),
                    items = if (isFolderChange) emptyList() else it.items,
                    supportedItemCount = if (isFolderChange) 0 else it.supportedItemCount,
                    isTruncated = if (isFolderChange) false else it.isTruncated,
                    isScanning = true,
                    hasError = false,
                    canNavigateUp = folderStack.size > 1,
                )
            }
            try {
                val listing = scanner.scan(activeTreeUri, folderUri)
                mutableUiState.update {
                    it.copy(
                        folderTitle = listing.folderName,
                        items = listing.items,
                        supportedItemCount = listing.supportedItemCount,
                        isScanning = false,
                        isTruncated = listing.isTruncated,
                        canNavigateUp = folderStack.size > 1,
                        hasError = false,
                    )
                }
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
