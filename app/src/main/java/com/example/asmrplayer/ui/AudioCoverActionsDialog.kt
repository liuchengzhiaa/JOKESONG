package com.example.asmrplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.R
import kotlinx.coroutines.launch

@Composable
internal fun AudioCoverActionsDialog(
    title: String,
    onEditAudio: () -> Unit,
    onUseForFolder: suspend () -> Boolean,
    onDismiss: () -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = onEditAudio) { Text(stringResource(R.string.audio_cover_edit)) }
                TextButton(enabled = !busy, onClick = {
                    busy = true; failed = false
                    scope.launch {
                        try { if (onUseForFolder()) onDismiss() else failed = true }
                        finally { busy = false }
                    }
                }) { Text(stringResource(R.string.audio_as_folder_cover)) }
                Text(stringResource(R.string.audio_as_folder_cover_notice))
                if (busy) LinearProgressIndicator()
                if (failed) Text(stringResource(R.string.audio_as_folder_cover_failed))
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.metadata_close)) } },
    )
}
