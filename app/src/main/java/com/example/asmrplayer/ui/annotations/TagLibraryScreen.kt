package com.example.asmrplayer.ui.annotations

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.asmrplayer.R
import com.example.asmrplayer.core.annotations.*
import com.example.asmrplayer.core.database.AsmrDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch

/** Paged, root-scoped tags and exact score results; never scans the media tree. */
@Composable
fun TagLibraryScreen(root: String?, onOpen: (UserItemEntity) -> Unit) {
    if (root == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.annotation_choose_root)) }
        return
    }
    val context = LocalContext.current
    val dao = remember(context) { AsmrDatabase.get(context).annotationDao() }
    var scoresMode by rememberSaveable(root) { mutableStateOf(false) }
    var tagId by rememberSaveable(root) { mutableStateOf<Long?>(null) }
    var tagName by rememberSaveable(root) { mutableStateOf("") }
    var query by rememberSaveable(root) { mutableStateOf("") }
    var scoreText by rememberSaveable(root) { mutableStateOf("90") }
    var appliedScore by rememberSaveable(root) { mutableIntStateOf(90) }
    var page by rememberSaveable(root) { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var editTarget by rememberSaveable(root) { mutableStateOf<UserItemEntity?>(null) }
    val showItems = scoresMode || tagId != null
    val tagFlow = remember(root, query, page, retry, showItems) {
        dao.tags(root, AnnotationPolicy.containsPattern(query), AnnotationPolicy.PageSize + 1, if (showItems) 0 else page * AnnotationPolicy.PageSize)
            .catch { if (it is CancellationException) throw it; failed = true; emit(emptyList()) }
    }
    val itemFlow = remember(root, tagId, scoresMode, appliedScore, page, retry) {
        if (!showItems) kotlinx.coroutines.flow.flowOf(emptyList<UserItemEntity>()) else dao.items(root, tagId, appliedScore.takeIf { scoresMode }, AnnotationPolicy.PageSize + 1, page * AnnotationPolicy.PageSize)
            .catch { if (it is CancellationException) throw it; failed = true; emit(emptyList()) }
    }
    // Keys prevent one filter's rows flashing beneath another filter's heading.
    val tags by key(root, query, page, retry) { tagFlow.collectAsStateWithLifecycle(emptyList()) }
    val entries by key(root, tagId, scoresMode, appliedScore, page, retry) { itemFlow.collectAsStateWithLifecycle(emptyList()) }
    editTarget?.let { target -> AnnotationEditor(target) { editTarget = null } }
    BackHandler(enabled = tagId != null) { tagId = null; page = 0 }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !scoresMode, onClick = { scoresMode = false; tagId = null; page = 0 }, label = { Text(stringResource(R.string.annotation_tags)) })
            FilterChip(selected = scoresMode, onClick = { scoresMode = true; tagId = null; page = 0 }, label = { Text(stringResource(R.string.annotation_scores)) })
        }
        if (scoresMode) Row(verticalAlignment = Alignment.CenterVertically) {
            val parsed = runCatching { AnnotationPolicy.score(scoreText) }.getOrNull()
            OutlinedTextField(value = scoreText, onValueChange = { scoreText = it.take(3) },
                label = { Text(stringResource(R.string.annotation_exact_score)) }, singleLine = true,
                isError = parsed == null, modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            TextButton(enabled = parsed != null, onClick = { appliedScore = parsed!!; page = 0 }) { Text(stringResource(R.string.annotation_filter)) }
        } else if (tagId == null) {
            OutlinedTextField(value = query, onValueChange = { query = it.take(100); page = 0 },
                label = { Text(stringResource(R.string.annotation_find_tag)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { tagId = null; page = 0 }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.annotation_back_tags)) }
            Text(tagName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (scoresMode) stringResource(R.string.annotation_score_results, appliedScore) else stringResource(R.string.annotation_library_help), style = MaterialTheme.typography.bodySmall)
        if (failed) TextButton(onClick = { failed = false; retry++ }) { Text(stringResource(R.string.annotation_failed_retry)) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (!showItems) {
                if (tags.isEmpty()) item { Text(stringResource(R.string.annotation_empty_tags)) }
                items(tags.take(AnnotationPolicy.PageSize), key = { it.id }) { tag ->
                    ListItem(headlineContent = { Text(tag.name) },
                        supportingContent = { Text(stringResource(R.string.annotation_tag_count, tag.itemCount)) },
                        leadingContent = { Icon(Icons.Default.Sell, null) },
                        modifier = Modifier.combinedClickable(onClick = { tagId = tag.id; tagName = tag.name; page = 0 }))
                }
            } else {
                if (entries.isEmpty()) item { Text(stringResource(R.string.annotation_empty_results)) }
                items(entries.take(AnnotationPolicy.PageSize), key = { it.documentUri }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(if (entry.kind == "Folder") stringResource(R.string.annotation_folder) else entry.score?.let { stringResource(R.string.annotation_score_value, it) } ?: stringResource(R.string.annotation_unrated)) },
                        leadingContent = {
                            if (entry.artworkUri != null) AsyncImage(entry.artworkUri, null, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                            else Icon(if (entry.kind == "Folder") Icons.Default.Folder else Icons.Default.LibraryMusic, null)
                        },
                        modifier = Modifier.combinedClickable(onClick = { onOpen(entry) }, onLongClick = { editTarget = entry }, onLongClickLabel = stringResource(R.string.annotation_edit)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = page > 0, onClick = { page-- }) { Text(stringResource(R.string.annotation_previous)) }
            Text(stringResource(R.string.annotation_page, page + 1))
            TextButton(enabled = (if (showItems) entries.size else tags.size) > AnnotationPolicy.PageSize,
                onClick = { page++ }) { Text(stringResource(R.string.annotation_next)) }
        }
    }
}

@Composable
fun AudioScoreBadge(root: String?, uri: String?, onEdit: () -> Unit) {
    if (root == null || uri == null || !AnnotationPolicy.sameTree(root, uri)) return
    val context = LocalContext.current
    val dao = remember(context) { AsmrDatabase.get(context).annotationDao() }
    val flow = remember(root, uri) { dao.score(root, uri).catch { if (it is CancellationException) throw it; emit(null) } }
    val score by key(root, uri) { flow.collectAsStateWithLifecycle(null) }
    TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
        Text(score?.let { stringResource(R.string.annotation_score_and_tags, it) } ?: stringResource(R.string.annotation_unrated_and_tags),
            style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .78f))
    }
}
