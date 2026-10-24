package com.example.asmrplayer.ui.annotations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.asmrplayer.R
import com.example.asmrplayer.core.annotations.*
import com.example.asmrplayer.core.database.AsmrDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun AnnotationEditor(target: UserItemEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dao = remember(context) { AsmrDatabase.get(context).annotationDao() }
    val scope = rememberCoroutineScope()
    var loaded by rememberSaveable(target.rootUri, target.documentUri) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var selected by rememberSaveable(target.rootUri, target.documentUri) { mutableStateOf(listOf<Long>()) }
    var newNames by rememberSaveable(target.rootUri, target.documentUri) { mutableStateOf(listOf<String>()) }
    var score by rememberSaveable(target.rootUri, target.documentUri) { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var newName by rememberSaveable { mutableStateOf("") }
    var invalidName by remember { mutableStateOf(false) }
    var readFailed by remember { mutableStateOf(false) }
    val choicesFlow = remember(dao, target.rootUri, search) {
        dao.tags(target.rootUri, AnnotationPolicy.containsPattern(search), AnnotationPolicy.PageSize + 1, 0)
            .catch { if (it is CancellationException) throw it; readFailed = true; emit(emptyList()) }
    }
    val choices by choicesFlow.collectAsStateWithLifecycle(emptyList())
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(target.rootUri, target.documentUri, retry) {
        if (!loaded) try {
            selected = dao.assigned(target.rootUri, target.documentUri).map { it.id }
            score = dao.item(target.rootUri, target.documentUri)?.score?.toString().orEmpty()
            loaded = true; failed = false
        } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
    }
    val validScore = runCatching { AnnotationPolicy.score(score) }.isSuccess
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.annotation_edit_title, target.title), maxLines = 2) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!loaded) {
                    if (failed) TextButton(onClick = { retry++ }) { Text(stringResource(R.string.annotation_retry)) }
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (target.kind == "Audio") OutlinedTextField(
                    value = score, onValueChange = { if (it.length <= 3) score = it }, enabled = loaded && !busy,
                    label = { Text(stringResource(R.string.annotation_score_hint)) },
                    supportingText = { Text(stringResource(R.string.annotation_score_help)) },
                    isError = !validScore, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(value = search, onValueChange = { search = it.take(100) },
                    label = { Text(stringResource(R.string.annotation_find_tag)) }, singleLine = true,
                    enabled = loaded && !busy)
                Text(stringResource(R.string.annotation_selected_count, selected.size + newNames.size),
                    style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                    items(choices.take(AnnotationPolicy.PageSize), key = { it.id }) { tag ->
                        val checked = tag.id in selected
                        fun toggle() {
                            if (checked) selected = selected - tag.id
                            else if (selected.size + newNames.size < AnnotationPolicy.MaxTagsPerItem) selected = selected + tag.id
                        }
                        Row(Modifier.fillMaxWidth().clickable(enabled = loaded && !busy) { toggle() }) {
                            Checkbox(checked, onCheckedChange = { toggle() }, enabled = loaded && !busy)
                            Text(tag.name, Modifier.weight(1f).padding(vertical = 12.dp))
                        }
                    }
                    items(newNames, key = { "new:$it" }) { name ->
                        TextButton(enabled = !busy, onClick = { newNames = newNames - name }) {
                            Text(stringResource(R.string.annotation_new_tag_pending, name))
                        }
                    }
                }
                if (choices.size > AnnotationPolicy.PageSize) Text(stringResource(R.string.annotation_refine_search))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it.take(160); invalidName = false },
                        label = { Text(stringResource(R.string.annotation_new_tag)) }, singleLine = true,
                        modifier = Modifier.weight(1f), enabled = loaded && !busy, isError = invalidName)
                    TextButton(enabled = loaded && !busy, onClick = {
                        val name = runCatching { AnnotationPolicy.tagName(newName) }.getOrNull()
                        if (name == null || selected.size + newNames.size >= AnnotationPolicy.MaxTagsPerItem) invalidName = true
                        else {
                            if (newNames.none { AnnotationPolicy.tagKey(it) == AnnotationPolicy.tagKey(name) }) newNames = newNames + name
                            newName = ""
                        }
                    }) { Text(stringResource(R.string.annotation_add)) }
                }
                if (invalidName) Text(stringResource(R.string.annotation_tag_invalid), color = MaterialTheme.colorScheme.error)
                if (failed || readFailed) Text(stringResource(R.string.annotation_failed), color = MaterialTheme.colorScheme.error)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = loaded && !busy && validScore && !readFailed, onClick = {
                busy = true; failed = false
                scope.launch {
                    try {
                        // Do not silently discard a name typed without pressing Add.
                        val names = newNames + if (newName.isNotBlank()) listOf(AnnotationPolicy.tagName(newName)) else emptyList()
                        dao.save(target, selected.toSet(), names, if (target.kind == "Audio") AnnotationPolicy.score(score) else null)
                        onDismiss()
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { failed = true }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.annotation_save)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.annotation_cancel)) } },
    )
}
