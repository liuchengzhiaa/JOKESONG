package com.example.asmrplayer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.asmrplayer.R
import com.example.asmrplayer.core.artwork.AudioArtworkProvider
import com.example.asmrplayer.core.artwork.audioArtworkUri
import com.example.asmrplayer.core.metadata.*
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.feature.library.MetadataEditViewModel
import kotlinx.coroutines.launch

@Composable
internal fun MetadataEditorDialog(item: LibraryItem, onDismiss: () -> Unit,
    onBeforeSave: () -> Unit, onSaved: (TagSaveResult) -> Unit) {
    val model: MetadataEditViewModel = viewModel(key = "metadata-editor")
    val state by model.state.collectAsStateWithLifecycle()
    val session = rememberSaveable { java.util.UUID.randomUUID().toString() }
    LaunchedEffect(item.documentUri, session) { model.open(item.documentUri.orEmpty(), session) }
    LaunchedEffect(state.saved) {
        model.takeSavedEvent()?.let { saved ->
            // A tag-only edit must not remove an existing sidecar/folder-art fallback.
            // Unwrap the private endpoint rather than nesting it as its own fallback.
            val previous = item.artworkUri?.toUri()
            val fallback = if (previous?.authority == AudioArtworkProvider.Authority)
                previous.getQueryParameter("fallback") else item.artworkUri
            val version = saved.artworkUri.toUri().getQueryParameter("version").orEmpty()
            onSaved(saved.copy(artworkUri = audioArtworkUri(saved.documentUri, fallback, version)))
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.data?.let(model::cover)
    }
    BackHandler(state.busy) { }
    Dialog(onDismissRequest = { if (!state.busy) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnBackPress = !state.busy, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.metadata_edit), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss, enabled = !state.busy) { Text(stringResource(R.string.metadata_close)) }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(if (state.coverLoading) R.string.metadata_loading_cover else phaseLabel(state.phase)), style = MaterialTheme.typography.bodySmall)
                }
                state.error?.let { Text(stringResource(errorLabel(it)), color = MaterialTheme.colorScheme.error) }
                if (state.pending) Button(onClick = model::recover, enabled = !state.busy) { Text(stringResource(R.string.metadata_recover)) }
                if (state.saved != null) Text(stringResource(if (state.pending) R.string.metadata_saved_cleanup else R.string.metadata_saved))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.snapshot?.fileName ?: item.title, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.metadata_notice), style = MaterialTheme.typography.bodySmall)
                    if (state.cover != CoverEdit.RemoveFront) AsyncImage(model = state.previewUri ?: item.artworkUri,
                        contentDescription = stringResource(R.string.metadata_cover), contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(170.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(enabled = state.snapshot != null && !state.busy && state.saved == null, onClick = {
                            picker.launch(coverImagePickerIntent())
                        }) { Text(stringResource(R.string.metadata_choose_cover)) }
                        TextButton(onClick = model::removeCover, enabled = state.snapshot != null && !state.busy && state.saved == null) {
                            Text(stringResource(R.string.metadata_remove_cover))
                        }
                    }
                    EditableTagKeys.forEach { key ->
                        OutlinedTextField(value = state.fields[key].orEmpty(), onValueChange = { model.field(key, it) },
                            label = { Text(stringResource(tagLabel(key))) }, singleLine = key != "COMMENT",
                            enabled = state.snapshot != null && !state.busy && !state.pending && state.saved == null,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
                Button(onClick = { onBeforeSave(); model.save() }, modifier = Modifier.fillMaxWidth(),
                    enabled = state.snapshot != null && !state.loading && !state.busy && !state.pending && state.saved == null) {
                    Text(stringResource(R.string.metadata_save_file))
                }
            }
        }
    }
}

private fun tagLabel(key: String): Int = when (key) {
    "TITLE" -> R.string.metadata_title; "ARTIST" -> R.string.metadata_artist; "ALBUM" -> R.string.metadata_album
    "ALBUMARTIST" -> R.string.metadata_album_artist; "TRACKNUMBER" -> R.string.metadata_track; "DISCNUMBER" -> R.string.metadata_disc
    "DATE" -> R.string.metadata_date; "GENRE" -> R.string.metadata_genre; else -> R.string.metadata_comment
}
private fun phaseLabel(phase: EditPhase) = when (phase) {
    EditPhase.Preparing -> R.string.metadata_preparing
    EditPhase.Validated -> R.string.metadata_validating
    EditPhase.Committed -> R.string.metadata_cleanup
    else -> R.string.metadata_committing
}
private fun errorLabel(code: String) = when (code) {
    "RECOVERY_REQUIRED", "RECOVERY_CONFLICT" -> R.string.metadata_recovery_error
    "INSUFFICIENT_SPACE" -> R.string.metadata_space_error
    "UNSUPPORTED_FORMAT", "UNSUPPORTED_PROVIDER", "READ_ONLY_PROVIDER", "NON_SEEKABLE_FILE", "UNKNOWN_FILE_SIZE" -> R.string.metadata_unsupported
    "WRITE_GRANT_REQUIRED" -> R.string.metadata_grant_error
    "SOURCE_CHANGED" -> R.string.metadata_changed_error
    "ARTWORK_TOO_LARGE", "TAGS_TOO_LARGE" -> R.string.metadata_size_error
    else -> R.string.metadata_failed
}

/** On restart the original may temporarily have its backup name, so recovery cannot rely on opening that song. */
@Composable
internal fun MetadataRecoveryPrompt(onRecovered: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val editor = remember(context) { AudioTagEditor(context) }
    val scope = rememberCoroutineScope()
    var visible by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = runCatching { editor.hasPending() }.getOrDefault(true) }
    if (visible) AlertDialog(onDismissRequest = { if (!busy) visible = false },
        title = { Text(stringResource(R.string.metadata_recovery_title)) },
        text = { Column { Text(stringResource(if (failed) R.string.metadata_recovery_error else R.string.metadata_recovery_notice)); if (busy) LinearProgressIndicator() } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try { editor.recover(); visible = false; onRecovered() } catch (_: Exception) { failed = true }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.metadata_recover)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { visible = false }) { Text(stringResource(R.string.metadata_later)) } })
}
