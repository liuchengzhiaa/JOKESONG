package com.example.asmrplayer.ui.annotations

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.asmrplayer.R
import com.example.asmrplayer.core.annotations.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class BackupUiState(val busy: Boolean = false, val progress: Int = 0,
    val message: Int? = null, val exportReady: Boolean = false, val preview: BackupPreview? = null)

internal class AnnotationBackupViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AnnotationBackupStore(application)
    private val mutableState = MutableStateFlow(BackupUiState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var export: File? = null
    private var stage: AnnotationImportStage? = null
    private var lastProgress = 0L
    private fun progress(value: Int) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastProgress >= 200) { lastProgress = now; mutableState.value = mutableState.value.copy(progress = value) }
    }
    private var boundRoot: String? = null
    fun attachRoot(root: String) {
        if (boundRoot == root) return
        boundRoot = root
        if (!mutableState.value.busy) reset()
        else {
            val previous = job
            viewModelScope.launch { previous?.cancelAndJoin(); if (job === previous) reset() }
        }
    }
    fun reset() {
        if (mutableState.value.busy) return
        export?.delete(); export = null; stage?.close(); stage = null
        mutableState.value = BackupUiState()
    }
    private fun work(action: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, message = null, progress = 0)
        job = viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { mutableState.value = BackupUiState(message = R.string.annotation_backup_cancelled); throw e }
            catch (_: UnsupportedOperationException) { mutableState.value = BackupUiState(message = R.string.annotation_backup_provider_unsupported) }
            catch (_: Exception) { mutableState.value = BackupUiState(message = R.string.annotation_backup_failed) }
            finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }
    fun prepareExport(root: String, password: CharArray) {
        if (mutableState.value.busy) { password.fill('\u0000'); return }
        reset()
        work {
            try {
                export = store.export(root, password, ::progress)
                mutableState.value = BackupUiState(exportReady = true, message = R.string.annotation_backup_ready)
            } finally { password.fill('\u0000') }
        }
    }
    fun saveExport(uri: Uri?) {
        if (uri == null) return // Keep encrypted staging so Cancel can pick a different destination.
        val file = export ?: run { mutableState.value = BackupUiState(message = R.string.annotation_backup_failed); return }
        work {
            store.copyExport(file, uri)
            file.delete(); export = null
            mutableState.value = BackupUiState(message = R.string.annotation_backup_exported)
        }
    }
    fun prepareImport(root: String, uri: Uri, password: CharArray) {
        if (mutableState.value.busy) { password.fill('\u0000'); return }
        reset()
        work {
            try {
                stage = store.prepare(root, uri, password, ::progress)
                mutableState.value = BackupUiState(preview = stage!!.preview())
            } finally { password.fill('\u0000') }
        }
    }
    fun confirmImport() {
        val prepared = stage ?: return
        work {
            try {
                store.commit(prepared, ::progress)
                mutableState.value = BackupUiState(message = R.string.annotation_backup_imported)
            } finally { prepared.close(); stage = null }
        }
    }
    fun cancel() { job?.cancel() }
    override fun onCleared() {
        // The active coroutine owns any unfinished file; its cancellation/finally handles it.
        val active = job
        if (active != null && !active.isCompleted) active.invokeOnCompletion { export?.delete(); stage?.close() }
        else { export?.delete(); stage?.close() }
    }
}
