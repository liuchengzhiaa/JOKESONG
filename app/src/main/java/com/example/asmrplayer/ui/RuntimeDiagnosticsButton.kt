package com.example.asmrplayer.ui

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.asmrplayer.BuildConfig
import com.example.asmrplayer.R
import com.example.asmrplayer.feature.library.LibraryUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owner-initiated, local-only diagnostics. No media names, paths, exception messages or uploads. */
@Composable
internal fun RuntimeDiagnosticsButton(state: LibraryUiState) {
    val context = LocalContext.current
    val clipboardLabel = stringResource(R.string.runtime_diagnostics)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Button(
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            busy = true
            scope.launch {
                try {
                    val counts = "items=${state.items.size}; supported=${state.supportedItemCount}; layout=${state.libraryLayoutOrdinal}; zoom=${state.canvasZoom}; density=${state.wallDensityPercent}"
                    val report = withContext(Dispatchers.IO) { collectRuntimeDiagnostics(context, counts) }
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                        ?: throw IllegalStateException("Clipboard unavailable")
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(clipboardLabel, report),
                    )
                    Toast.makeText(context, R.string.runtime_diagnostics_copied, Toast.LENGTH_SHORT).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: RuntimeException) {
                    Toast.makeText(context, R.string.runtime_diagnostics_failed, Toast.LENGTH_SHORT).show()
                } finally { busy = false }
            }
        },
    ) { Text(stringResource(R.string.runtime_diagnostics)) }
}

private fun collectRuntimeDiagnostics(context: Context, counts: String): String = buildString {
    appendLine("JOKESONG ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("sdk=${Build.VERSION.SDK_INT}; heapMaxBytes=${Runtime.getRuntime().maxMemory()}")
    appendLine(counts)
    if (Build.VERSION.SDK_INT >= 30) {
        val exits = try {
            context.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(context.packageName, 0, 3).orEmpty()
        } catch (_: RuntimeException) { emptyList() }
        appendLine("exitRecords=${exits.size}; reasonCodes: crash=${ApplicationExitInfo.REASON_CRASH}, native=${ApplicationExitInfo.REASON_CRASH_NATIVE}, anr=${ApplicationExitInfo.REASON_ANR}, lowMemory=${ApplicationExitInfo.REASON_LOW_MEMORY}")
        exits.forEach { exit ->
            // Only numeric system facts. Do not include description, processName or traceInputStream.
            appendLine("exitMs=${exit.timestamp}; reason=${exit.reason}; status=${exit.status}; pssKb=${exit.pss}; rssKb=${exit.rss}")
        }
    } else appendLine("exitRecords=unavailable (requires API30)")
}
