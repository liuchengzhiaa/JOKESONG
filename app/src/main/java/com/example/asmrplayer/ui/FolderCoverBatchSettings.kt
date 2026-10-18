package com.example.asmrplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.R
import com.example.asmrplayer.core.artwork.FolderCoverBatchState
import com.example.asmrplayer.core.artwork.FolderCoverBatchStatus

@Composable
internal fun FolderCoverBatchSettings(state: FolderCoverBatchState, hasRoot: Boolean, onStart: () -> Unit, onCancel: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val running = state.status == FolderCoverBatchStatus.Running
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = hasRoot && !running, onClick = { confirm = true }) {
            Text(stringResource(R.string.folder_cover_batch_title))
        }
        Text(stringResource(R.string.folder_cover_batch_notice))
        if (state.status != FolderCoverBatchStatus.Idle) {
            Text(stringResource(when (state.status) {
                FolderCoverBatchStatus.Running -> R.string.folder_cover_batch_running
                FolderCoverBatchStatus.Completed -> R.string.folder_cover_batch_completed
                FolderCoverBatchStatus.Cancelled -> R.string.folder_cover_batch_cancelled
                else -> R.string.folder_cover_batch_failed
            }))
            val c = state.counts
            Text(stringResource(R.string.folder_cover_batch_counts, c.visited, c.created, c.existing, c.missing, c.failed))
        }
        if (running) {
            LinearProgressIndicator()
            TextButton(onClick = onCancel) { Text(stringResource(R.string.folder_cover_batch_cancel)) }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.folder_cover_batch_title)) },
        text = { Text(stringResource(R.string.folder_cover_batch_confirm)) },
        confirmButton = { TextButton(onClick = { confirm = false; onStart() }) { Text(stringResource(R.string.folder_cover_batch_start)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.folder_cover_batch_cancel)) } },
    )
}
