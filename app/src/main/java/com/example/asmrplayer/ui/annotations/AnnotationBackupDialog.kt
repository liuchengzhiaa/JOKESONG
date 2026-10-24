package com.example.asmrplayer.ui.annotations

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.asmrplayer.R
import android.net.Uri

@Composable
internal fun AnnotationBackupDialog(root: String, onDismiss: () -> Unit) {
    val model: AnnotationBackupViewModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    var importing by rememberSaveable { mutableStateOf(false) }
    var importUri by rememberSaveable { mutableStateOf<String?>(null) }
    // Passwords are deliberately never saved in Bundle, DataStore, DB or backup headers.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { model.saveExport(it) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importUri = it?.toString() }
    LaunchedEffect(root) { model.attachRoot(root) }
    AlertDialog(
        onDismissRequest = { if (!state.busy) { model.reset(); onDismiss() } },
        title = { Text(stringResource(R.string.annotation_backup_title)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.annotation_backup_notice), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !importing, enabled = !state.busy, onClick = { importing = false; model.reset() }, label = { Text(stringResource(R.string.annotation_backup_export)) })
                    FilterChip(selected = importing, enabled = !state.busy, onClick = { importing = true; model.reset() }, label = { Text(stringResource(R.string.annotation_backup_import)) })
                }
                if (state.exportReady) {
                    Button(onClick = { exportPicker.launch("JOKESONG-tags-${java.time.LocalDate.now()}.jst") }, enabled = !state.busy) {
                        Text(stringResource(R.string.annotation_backup_choose_save))
                    }
                } else if (state.preview != null) {
                    val preview = state.preview!!
                    Text(stringResource(R.string.annotation_backup_preview, preview.items, preview.matched, preview.missing, preview.tags))
                    Text(stringResource(R.string.annotation_backup_merge_notice))
                    Text(stringResource(R.string.annotation_backup_target, runCatching { android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(root)) }.getOrDefault(root)))
                    Button(enabled = !state.busy && (preview.matched > 0 || preview.items == 0), onClick = model::confirmImport) {
                        Text(stringResource(R.string.annotation_backup_confirm))
                    }
                } else {
                    if (importing) {
                        TextButton(enabled = !state.busy, onClick = { importPicker.launch(arrayOf("*/*")) }) {
                            Text(stringResource(if (importUri == null) R.string.annotation_backup_choose_file else R.string.annotation_backup_file_selected))
                        }
                        Text(stringResource(R.string.annotation_backup_structure))
                    }
                    OutlinedTextField(value = password, onValueChange = { password = it.take(256) }, enabled = !state.busy,
                        label = { Text(stringResource(R.string.annotation_backup_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    if (!importing) OutlinedTextField(value = confirmation, onValueChange = { confirmation = it.take(256) }, enabled = !state.busy,
                        label = { Text(stringResource(R.string.annotation_backup_confirm_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Button(enabled = !state.busy && if (importing) importUri != null && password.isNotEmpty()
                        else password.length >= 8 && password.isNotBlank() && password == confirmation,
                        onClick = {
                            val secret = password.toCharArray(); password = ""; confirmation = ""
                            if (importing) model.prepareImport(root, Uri.parse(importUri!!), secret)
                            else model.prepareExport(root, secret)
                        }) { Text(stringResource(if (importing) R.string.annotation_backup_preview_action else R.string.annotation_backup_prepare)) }
                }
                state.message?.let { Text(stringResource(it)) }
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.annotation_backup_progress, state.progress))
                    TextButton(onClick = model::cancel) { Text(stringResource(R.string.annotation_backup_stop)) }
                }
            }
        },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { model.reset(); onDismiss() }) { Text(stringResource(R.string.metadata_close)) } },
    )
}
