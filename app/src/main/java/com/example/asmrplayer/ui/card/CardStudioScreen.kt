package com.example.asmrplayer.ui.card

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.asmrplayer.R
import com.example.asmrplayer.core.card.CardBuilder
import com.example.asmrplayer.core.card.CardLayerGeometry
import com.example.asmrplayer.core.card.CardRepository
import com.example.asmrplayer.core.card.CardStylePreset
import com.example.asmrplayer.core.card.CardSummary
import com.example.asmrplayer.core.card.CardTextFields
import com.example.asmrplayer.core.model.LibraryItem
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The card collection: what has been made, and how to make another.
 *
 * A collection room rather than a file list: cards are shelved by the series printed on
 * them, pinned ones get a case of their own at the top, and the whole thing can be
 * searched, reordered and cleared out in batches. The shelves are the card's own
 * `collection` line rather than a folder the owner has to maintain, so the room is
 * arranged by something that is already true of every card.
 *
 * The viewer is the workbench and this is the shelf. Materials and depths are switched
 * where they can be seen against the card itself, not here -- duplicating those controls
 * onto a grid of thumbnails would be a second set of them to keep in step.
 *
 * The studio is entered two ways and they are not the same errand. From Settings it is a
 * place: the room is the point, and a card is one of the things that happen in it. From
 * a song that has no card it is a single act -- make this song's card -- so it opens on
 * the form, and [startAtCompose] is what tells the two apart.
 */
@Composable
fun CardStudioScreen(
    item: LibraryItem?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    startAtCompose: Boolean = false,
    onCardCreated: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CardRepository(context) }
    val builder = remember { CardBuilder(context) }

    var page by rememberSaveable {
        mutableStateOf(if (startAtCompose) StudioPage.Compose else StudioPage.Warehouse)
    }

    // Still true while the form is the bottom of the stack, which is what makes back from
    // it leave the studio rather than open a room the owner never asked for. Cleared the
    // moment the room is used, because from then on the room is behind him and belongs on
    // the way back out.
    var composeIsRoot by rememberSaveable { mutableStateOf(startAtCompose) }
    var cards by remember { mutableStateOf<List<CardSummary>>(emptyList()) }
    var openCardId by rememberSaveable { mutableStateOf<String?>(null) }

    // Held here rather than inside the grid so that opening a card and coming back
    // returns to the same shelf, in the same order, with the same search still applied.
    var query by rememberSaveable { mutableStateOf("") }
    var sortOrdinal by rememberSaveable { mutableIntStateOf(CardSort.Newest.ordinal) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable(stateSaver = SelectedSaver) {
        mutableStateOf(emptySet<String>())
    }

    var fields by rememberSaveable(stateSaver = CardFieldsSaver) {
        mutableStateOf(item?.let { CardTextFields.fromItem(it) } ?: CardTextFields())
    }
    var presetOrdinal by rememberSaveable { mutableIntStateOf(CardStylePreset.Default.ordinal) }
    var coverUri by rememberSaveable { mutableStateOf(item?.artworkUri) }

    var generating by remember { mutableStateOf(false) }
    var generateFailed by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<CardSummary?>(null) }

    suspend fun refresh() {
        cards = withContext(Dispatchers.IO) { repository.listCards() }
    }

    LaunchedEffect(Unit) { refresh() }

    // A selection can outlive the cards it names -- a batch delete, or a card removed
    // elsewhere -- so the count shown is always the intersection, never the raw set.
    val liveSelection = remember(selected, cards) {
        selected intersect cards.mapTo(mutableSetOf(), CardSummary::id)
    }

    val preset = CardStylePreset.fromOrdinal(presetOrdinal)
    val sort = CardSort.fromOrdinal(sortOrdinal)

    // The one step back from the form, shared by the header arrow and the system gesture
    // so the two cannot disagree about whether there is a room behind it.
    val leaveCompose: () -> Unit = {
        if (composeIsRoot) onClose() else page = StudioPage.Warehouse
    }

    BackHandler(enabled = true) {
        when {
            selecting -> {
                selecting = false
                selected = emptySet()
            }
            page == StudioPage.Warehouse -> onClose()
            page == StudioPage.Compose -> leaveCompose()
            else -> {
                openCardId = null
                page = StudioPage.Warehouse
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The warehouse and the form are ordinary app pages and stay inside the
            // safe area. The viewer is an immersive surface: its WebGL canvas paints
            // edge-to-edge and its native controls apply their own safe-area padding.
            .then(
                if (page == StudioPage.Viewer) {
                    Modifier
                } else {
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                },
            ),
    ) {
        // The viewer brings its own header, so the studio's would be a second one
        // stacked on top of it.
        if (page != StudioPage.Viewer) {
            StudioHeader(
                title = stringResource(
                    if (page == StudioPage.Warehouse) {
                        R.string.card_studio_title
                    } else {
                        R.string.card_compose_title
                    },
                ),
                onBack = {
                    if (page == StudioPage.Warehouse) onClose() else leaveCompose()
                },
            )
        }

        Box(Modifier.weight(1f)) {
            when (page) {
                StudioPage.Warehouse -> Warehouse(
                    cards = cards,
                    query = query,
                    onQueryChange = { query = it },
                    sort = sort,
                    onSortChange = { sortOrdinal = it.ordinal },
                    selecting = selecting,
                    onSelectingChange = {
                        selecting = it
                        if (!it) selected = emptySet()
                    },
                    selected = liveSelection,
                    onSelectedChange = { selected = it },
                    onOpen = { summary ->
                        openCardId = summary.id
                        page = StudioPage.Viewer
                    },
                    onLongPress = { pendingAction = it },
                    onPinSelected = { ids ->
                        // One control for the whole batch, so it says one thing: pin them
                        // all, or unpin them all, never a mixed result the label cannot
                        // describe.
                        val pinned = !cards.filter { it.id in ids }.all(CardSummary::pinned)
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                ids.forEach { repository.setPinned(it, pinned) }
                            }
                            selected = emptySet()
                            refresh()
                        }
                    },
                    onDeleteSelected = { ids ->
                        scope.launch {
                            withContext(Dispatchers.IO) { repository.deleteCards(ids) }
                            selected = emptySet()
                            selecting = false
                            refresh()
                        }
                    },
                    onNew = {
                        generateFailed = false
                        composeIsRoot = false
                        page = StudioPage.Compose
                    },
                )

                StudioPage.Compose -> CardComposeForm(
                    fields = fields,
                    onFieldsChange = { fields = it },
                    preset = preset,
                    onPresetChange = { presetOrdinal = it.ordinal },
                    coverUri = coverUri,
                    onCoverChange = { coverUri = it },
                    generating = generating,
                    failed = generateFailed,
                    onGenerate = {
                        generating = true
                        generateFailed = false
                        scope.launch {
                            val created = builder.create(
                                artworkUri = coverUri?.takeIf(String::isNotEmpty)?.let(Uri::parse),
                                fields = fields.trimmed(),
                                preset = preset,
                                // The song the card was struck from, so this song's own
                                // page can find it again. Null when the cover was picked
                                // out of the gallery rather than taken from the library.
                                mediaId = item?.id,
                            )
                            generating = false
                            if (created == null) {
                                generateFailed = true
                            } else {
                                refresh()
                                openCardId = created
                                page = StudioPage.Viewer
                                // The song behind this form now has a card, and the
                                // player that sent us here is still holding the answer
                                // it read before there was one.
                                onCardCreated(created)
                            }
                        }
                    },
                )

                StudioPage.Viewer -> {
                    val cardId = openCardId
                    if (cardId == null) {
                        LaunchedEffect(Unit) { page = StudioPage.Warehouse }
                    } else {
                        CardViewerScreen(
                            cardId = cardId,
                            onBack = {
                                openCardId = null
                                page = StudioPage.Warehouse
                            },
                            onSaved = { scope.launch { refresh() } },
                        )
                    }
                }
            }
        }
    }

    // What a long press offers for one card. Pinning is here rather than on the tile
    // because a pin badge is a state, not a control: pressing the thing that says
    // "this one matters" to find out it also deletes would be a surprise.
    pendingAction?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.card_delete_confirm, target.title)) },
            text = { Text(stringResource(R.string.card_delete_note)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            pendingAction = null
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    repository.setPinned(target.id, !target.pinned)
                                }
                                refresh()
                            }
                        },
                    ) {
                        Text(
                            stringResource(
                                if (target.pinned) R.string.card_unpin else R.string.card_pin,
                            ),
                        )
                    }
                    TextButton(
                        onClick = {
                            pendingAction = null
                            scope.launch {
                                withContext(Dispatchers.IO) { repository.deleteCard(target.id) }
                                refresh()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.card_delete_action))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.card_cancel))
                }
            },
        )
    }
}

@Composable
private fun StudioHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.card_back),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The room itself.
 *
 * One grid holds everything, with the shelf headings as full-width rows inside it, so
 * the whole collection scrolls as one surface rather than as a set of nested lists to
 * keep in sync.
 */
@Composable
private fun Warehouse(
    cards: List<CardSummary>,
    query: String,
    onQueryChange: (String) -> Unit,
    sort: CardSort,
    onSortChange: (CardSort) -> Unit,
    selecting: Boolean,
    onSelectingChange: (Boolean) -> Unit,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    onOpen: (CardSummary) -> Unit,
    onLongPress: (CardSummary) -> Unit,
    onPinSelected: (Set<String>) -> Unit,
    onDeleteSelected: (Set<String>) -> Unit,
    onNew: () -> Unit,
) {
    val groups = remember(cards, query, sort) { CardShelves.shelve(cards, query, sort) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (cards.isNotEmpty()) {
                WarehouseToolbar(
                    query = query,
                    onQueryChange = onQueryChange,
                    sort = sort,
                    onSortChange = onSortChange,
                    selecting = selecting,
                    onSelectingChange = onSelectingChange,
                    summary = stringResource(
                        R.string.card_summary,
                        cards.size,
                        CardShelves.seriesCount(cards),
                    ),
                )
            }

            when {
                cards.isEmpty() -> EmptyRoom()
                groups.isEmpty() -> Text(
                    text = stringResource(R.string.card_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        // Clear of the floating button, or of the bar that replaces it.
                        bottom = 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    groups.forEach { group ->
                        item(
                            key = "shelf-${group.heading.key()}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            ShelfLabel(heading = group.heading, count = group.cards.size)
                        }
                        items(group.cards, key = { it.id }) { summary ->
                            CardTile(
                                summary = summary,
                                selecting = selecting,
                                selected = summary.id in selected,
                                onClick = {
                                    if (selecting) {
                                        onSelectedChange(selected.toggle(summary.id))
                                    } else {
                                        onOpen(summary)
                                    }
                                },
                                onLongPress = {
                                    if (selecting) {
                                        onSelectedChange(selected.toggle(summary.id))
                                    } else {
                                        onLongPress(summary)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        if (selecting && selected.isNotEmpty()) {
            SelectionBar(
                count = selected.size,
                // Everything chosen is pinned or nothing is: one control, whose only
                // unambiguous meaning is to become the opposite of what is there now.
                allPinned = cards.filter { it.id in selected }.all(CardSummary::pinned),
                onPin = { onPinSelected(selected) },
                onDelete = { onDeleteSelected(selected) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (!selecting) {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.card_new)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            )
        }
    }
}

/** Search, order, and the way into selecting: the room's three controls, in one row. */
@Composable
private fun WarehouseToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: CardSort,
    onSortChange: (CardSort) -> Unit,
    selecting: Boolean,
    onSelectingChange: (Boolean) -> Unit,
    summary: String,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.card_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.card_search_clear),
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.card_sort))
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    CardSort.entries.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(stringResource(entry.labelRes)) },
                            onClick = {
                                sortMenuOpen = false
                                onSortChange(entry)
                            },
                            trailingIcon = {
                                if (entry == sort) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
            TextButton(onClick = { onSelectingChange(!selecting) }) {
                Text(
                    stringResource(
                        if (selecting) R.string.card_select_cancel else R.string.card_select,
                    ),
                )
            }
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun ShelfLabel(heading: ShelfHeading, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when (heading) {
                is ShelfHeading.Series -> heading.name
                ShelfHeading.Pinned -> stringResource(R.string.card_group_pinned)
                ShelfHeading.Ungrouped -> stringResource(R.string.card_group_ungrouped)
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyRoom() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.card_empty),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.card_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** What a batch of chosen cards can be made to do, floating over the shelves. */
@Composable
private fun SelectionBar(
    count: Int,
    allPinned: Boolean,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.card_selected_count, count),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = onPin) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = stringResource(
                        if (allPinned) R.string.card_unpin else R.string.card_pin,
                    ),
                    // Reads as a state rather than as a button: accent when the batch is
                    // already pinned, quiet when pressing it would pin them.
                    tint = if (allPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.card_delete_selected),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardTile(
    summary: CardSummary,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CardLayerGeometry.CanvasAspect)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            // A card that has never been saved shows the flat picture of its own face,
            // struck from the same three layers the viewer loads. It is not the same
            // picture the viewer's save would produce -- no foil, no tilt -- but it is
            // this card and not the cover it was made from.
            summary.thumbnail?.let { file ->
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (summary.pinned) {
                PinBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
            if (selecting) {
                MarkBadge(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }
        }
        Text(
            text = summary.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = remember(summary.createdAtMillis) {
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(summary.createdAtMillis))
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(22.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun MarkBadge(selected: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(22.dp),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Black.copy(alpha = 0.45f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            Color.White
        },
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

private enum class StudioPage { Warehouse, Compose, Viewer }

/** The order the shelves' contents are shown in. Pinned always come first. */
private enum class CardSort {
    Newest,
    Oldest,
    Title,
    ;

    val labelRes: Int
        get() = when (this) {
            Newest -> R.string.card_sort_newest
            Oldest -> R.string.card_sort_oldest
            Title -> R.string.card_sort_title
        }

    companion object {
        fun fromOrdinal(ordinal: Int): CardSort = entries[ordinal.coerceIn(entries.indices)]
    }
}

/** One shelf: a heading and what stands on it. */
private data class Shelf(val heading: ShelfHeading, val cards: List<CardSummary>)

/**
 * What is written on a shelf's edge.
 *
 * Pinned and ungrouped are not series names -- they are the two shelves that are not
 * about a series at all -- so they are their own cases rather than strings that would
 * have to be recognised again later, and they are the only ones that need translating.
 */
private sealed interface ShelfHeading {
    data class Series(val name: String) : ShelfHeading

    data object Pinned : ShelfHeading

    data object Ungrouped : ShelfHeading
}

/**
 * Arranging the room.
 *
 * Sorting and shelving are one step rather than two, because the shelves come out in a
 * fixed order and only what stands on them is sorted -- and because a card has to be
 * looked at once to know which shelf it belongs on. The pinned shelf is placed by
 * hand and the rest by name, so the collection does not reshuffle when a new card is
 * added to a series that already exists.
 */
private object CardShelves {

    /** Pinned first, then by series; ungrouped cards last rather than second. */
    fun shelve(cards: List<CardSummary>, query: String, sort: CardSort): List<Shelf> {
        val needle = query.trim()
        val matching = if (needle.isEmpty()) {
            cards
        } else {
            cards.filter { card ->
                card.title.contains(needle, ignoreCase = true) ||
                    card.series.contains(needle, ignoreCase = true)
            }
        }
        if (matching.isEmpty()) return emptyList()

        val (pinned, rest) = matching.partition(CardSummary::pinned)
        val (named, unnamed) = rest.partition { it.series.isNotBlank() }

        val shelves = ArrayList<Shelf>(1 + named.size + 1)
        if (pinned.isNotEmpty()) {
            shelves += Shelf(ShelfHeading.Pinned, pinned.sortedWith(sort.comparator))
        }
        // Series in name order, then the unnamed shelf: a card with no series is the
        // exception in the room, and an exception reads better at the end than in the
        // middle of the things it is an exception to.
        named.groupBy(CardSummary::series)
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .forEach { (series, group) ->
                shelves += Shelf(ShelfHeading.Series(series), group.sortedWith(sort.comparator))
            }
        if (unnamed.isNotEmpty()) {
            shelves += Shelf(ShelfHeading.Ungrouped, unnamed.sortedWith(sort.comparator))
        }
        return shelves
    }

    /**
     * How many distinct series the collection is filed under, ignoring search.
     *
     * Cards with no series are not counted: they are the one group that is not a series,
     * and counting them would make the summary claim a series the owner never wrote.
     */
    fun seriesCount(cards: List<CardSummary>): Int =
        cards.asSequence().map(CardSummary::series).filter(String::isNotBlank).distinct().count()

    private val CardSort.comparator: Comparator<CardSummary>
        get() = when (this) {
            CardSort.Newest -> compareByDescending(CardSummary::createdAtMillis)
            CardSort.Oldest -> compareBy(CardSummary::createdAtMillis)
            CardSort.Title -> compareBy(String.CASE_INSENSITIVE_ORDER, CardSummary::title)
        }
}

/** A stable identity for a shelf, so a heading is not re-keyed when a card changes shelf. */
private fun ShelfHeading.key(): String = when (this) {
    is ShelfHeading.Series -> "series:$name"
    ShelfHeading.Pinned -> "pinned"
    ShelfHeading.Ungrouped -> "ungrouped"
}

private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

/**
 * A selection survives rotation as a list.
 *
 * A `Set` is not one of the types a saved instance state is defined to carry, and a
 * selection that silently emptied itself when the phone turned would make the batch
 * controls look broken rather than look reset.
 */
private val SelectedSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

/**
 * Without a saver the form would be rebuilt from the song on every configuration change,
 * which is the same as losing everything typed into it.
 */
private val CardFieldsSaver = listSaver<CardTextFields, String>(
    save = {
        listOf(it.title, it.subtitle, it.collection, it.tagline, it.technique, it.edition, it.description)
    },
    restore = {
        CardTextFields(
            title = it[0],
            subtitle = it[1],
            collection = it[2],
            tagline = it[3],
            technique = it[4],
            edition = it[5],
            description = it[6],
        )
    },
)
