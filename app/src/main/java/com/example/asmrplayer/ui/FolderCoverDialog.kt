package com.example.asmrplayer.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.R
import kotlinx.coroutines.launch

@Composable
internal fun FolderCoverDialog(
    folderUri: String,
    title: String,
    onSave: suspend (String, Uri?) -> Boolean,
    onDismiss: () -> Unit,
) {
    var busy by remember(folderUri) { mutableStateOf(false) }
    var failed by remember(folderUri) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun save(image: Uri?) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try {
                if (onSave(folderUri, image)) onDismiss() else failed = true
            } finally {
                busy = false
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let(::save)
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.folder_cover_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title)
                Text(stringResource(R.string.folder_cover_notice))
                if (busy) LinearProgressIndicator()
                if (failed) Text(stringResource(R.string.folder_cover_failed))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { picker.launch(coverImagePickerIntent()) }) {
                Text(stringResource(R.string.folder_cover_set))
            }
        },
        dismissButton = {
            Row {
                TextButton(enabled = !busy, onClick = { save(null) }) {
                    Text(stringResource(R.string.folder_cover_reset))
                }
                TextButton(enabled = !busy, onClick = onDismiss) {
                    Text(stringResource(R.string.metadata_close))
                }
            }
        },
    )
}
