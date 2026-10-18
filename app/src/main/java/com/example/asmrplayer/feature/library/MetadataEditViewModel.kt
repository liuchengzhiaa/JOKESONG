package com.example.asmrplayer.feature.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.asmrplayer.core.metadata.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MetadataEditState(val uri: String = "", val loading: Boolean = true, val busy: Boolean = false,
    val snapshot: TagEditSnapshot? = null, val fields: Map<String, String> = emptyMap(),
    val coverLoading: Boolean = false,
    val cover: CoverEdit = CoverEdit.Keep, val previewUri: String? = null, val phase: EditPhase = EditPhase.Preparing,
    val error: String? = null, val pending: Boolean = false, val saved: TagSaveResult? = null)

class MetadataEditViewModel(app: Application) : AndroidViewModel(app) {
    private val editor = AudioTagEditor(app)
    private val mutable = MutableStateFlow(MetadataEditState())
    val state = mutable.asStateFlow()
    private var session = ""
    private var savedDelivered = false
    private var loadJob: Job? = null
    fun takeSavedEvent(): TagSaveResult? = mutable.value.saved?.takeUnless { savedDelivered }?.also { savedDelivered = true }
    fun open(uri: String, sessionId: String = session) {
        if (session == sessionId && mutable.value.uri == uri && (mutable.value.snapshot != null || mutable.value.loading)) return
        if (mutable.value.busy) return
        loadJob?.cancel()
        session = sessionId
        savedDelivered = false
        mutable.value = MetadataEditState(uri = uri)
        loadJob = viewModelScope.launch {
            try {
                if (editor.hasPending()) { mutable.value = mutable.value.copy(loading = false, pending = true, error = "RECOVERY_REQUIRED"); return@launch }
                val snapshot = editor.load(uri)
                if (session != sessionId || mutable.value.uri != uri) return@launch
                mutable.value = mutable.value.copy(loading = false, snapshot = snapshot,
                    fields = EditableTagKeys.associateWith(snapshot::text), error = null, saved = null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (session == sessionId && mutable.value.uri == uri)
                    mutable.value = mutable.value.copy(loading = false, error = e.message ?: "ERROR")
            }
        }
    }
    fun field(key: String, value: String) {
        if (!mutable.value.busy && value.length <= if (key == "COMMENT") 8192 else 2048)
            mutable.value = mutable.value.copy(fields = mutable.value.fields + (key to value), saved = null)
    }
    fun cover(uri: Uri) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, coverLoading = true, error = null)
        viewModelScope.launch {
            try { mutable.value = mutable.value.copy(cover = CoverEdit.Replace(editor.loadCover(uri)), previewUri = uri.toString(), saved = null) }
            catch (e: Exception) { mutable.value = mutable.value.copy(error = e.message ?: "ERROR") }
            finally { mutable.value = mutable.value.copy(busy = false, coverLoading = false) }
        }
    }
    fun removeCover() { if (!mutable.value.busy) mutable.value = mutable.value.copy(cover = CoverEdit.RemoveFront, previewUri = null, saved = null) }
    fun save() {
        val s = mutable.value; val snapshot = s.snapshot ?: return
        if (s.busy || s.pending) return
        mutable.value = s.copy(busy = true, error = null, saved = null)
        savedDelivered = false
        viewModelScope.launch {
            try {
                val saved = editor.save(s.uri, snapshot, s.fields, s.cover) { phase -> mutable.value = mutable.value.copy(phase = phase) }
                mutable.value = mutable.value.copy(saved = saved, pending = saved.cleanupPending)
                // The same dialog cannot accidentally re-save the stale pre-edit snapshot.
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(error = e.message ?: "ERROR", pending = runCatching { editor.hasPending() }.getOrDefault(true))
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun recover() {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                editor.recover()
                val uri = mutable.value.uri
                mutable.value = MetadataEditState(uri = "", loading = false)
                open(uri)
            } catch (e: Exception) { mutable.value = mutable.value.copy(error = "RECOVERY_CONFLICT", pending = true) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
