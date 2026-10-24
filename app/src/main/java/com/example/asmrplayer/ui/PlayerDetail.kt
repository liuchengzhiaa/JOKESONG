package com.example.asmrplayer.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Slider
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.unit.sp
import com.example.asmrplayer.core.artwork.CoverAccentLoader
import com.example.asmrplayer.core.artwork.CoverFacts
import com.example.asmrplayer.core.storage.AppPreferences
import com.example.asmrplayer.core.storage.AppSettings
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.asmrplayer.R
import com.example.asmrplayer.core.audio.AudioOutput
import com.example.asmrplayer.core.audio.AudioOutputKind
import com.example.asmrplayer.core.audio.androidAudioOutputs
import com.example.asmrplayer.core.audio.pickActiveOutput
import com.example.asmrplayer.core.card.CardLayerGeometry
import com.example.asmrplayer.core.card.CardRepository
import com.example.asmrplayer.core.card.CardSummary
import com.example.asmrplayer.core.media.PlaybackState
import com.example.asmrplayer.core.media.RepeatMode
import com.example.asmrplayer.core.media.SleepTimerChoices
import com.example.asmrplayer.core.media.sleepRemainingLabel
import com.example.asmrplayer.core.metadata.LocalAudioMetadata
import com.example.asmrplayer.core.metadata.LocalAudioMetadataReader
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.settings.ToolClusterStyle
import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.feature.focuswall.wallMotionDuration
import com.example.asmrplayer.feature.player.PlayerTimeline
import com.example.asmrplayer.feature.player.formatPlaybackTime
import com.example.asmrplayer.feature.player.parseDurationLabel
import com.example.asmrplayer.feature.subtitles.LyricsView
import com.example.asmrplayer.feature.subtitles.SubtitleDetailState
import com.example.asmrplayer.feature.subtitles.lyricsView
import com.example.asmrplayer.ui.card.CardSurface
import com.example.asmrplayer.ui.components.GlassOptionsPanel
import com.example.asmrplayer.ui.components.GlassTool
import com.example.asmrplayer.ui.components.GlassToolCluster
import com.example.asmrplayer.ui.components.GlassToolsPanel
import com.example.asmrplayer.ui.theme.artworkBrush
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SkipSeconds = 10

/**
 * The immersive player's own palette. It is deliberately not the theme's: the player
 * is near-black regardless of light or dark, and the subtitle surface that opens over
 * the same artwork reads the same colours, so these two are shared rather than
 * copied. The lyric block that sits under the cover reads them too, which is why all
 * three are `internal` rather than private to this file.
 */
internal val PlayerBackground = Color(0xFF090A0D)
internal val PlayerSecondaryText = Color(0xFFAAAAB2)
internal val PlayerPrimaryText = Color(0xFFF4F4F6)

private enum class PlayerPanel { Queue, Bookmarks, Chapters }

/**
 * Which page the collect button is showing. The root page is the list of things this
 * song can do; the timer is a page of its own because choosing a duration is a choice
 * between eight values, and a sub-list is how the wall already presents those.
 */
private enum class PlayerToolsPage { Root, SleepTimer }

/** The corner radius of the cover, and of the frame it used to be drawn in. */
private val ArtworkShape = RoundedCornerShape(8.dp)

/**
 * Whether the cover slot shows the song's card or its artwork.
 *
 * [choice] is what the owner did on this visit: `true` after he asked for the card,
 * `false` after he asked to go back, and null while he has not touched it -- at which
 * point [defaultToCard] decides. A card is never shown without something having asked
 * for it, and that something is either his tap or his setting.
 *
 * Pure and top-level so the rule can be tested without a composable: this is the whole
 * of the behaviour the toggle exists to provide, and getting the null case wrong is how
 * a song would start opening on a card nobody selected.
 */
internal fun showsCardCover(
    hasCard: Boolean,
    choice: Boolean?,
    defaultToCard: Boolean,
): Boolean = hasCard && (choice ?: defaultToCard)

/** Shared Media3 controls around pageable intrinsic cover and ordinary lyric surfaces. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerDetail(
    item: LibraryItem,
    queueItems: List<LibraryItem>,
    playbackState: PlaybackState,
    subtitles: SubtitleDetailState,
    onTogglePlayPause: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onQueueItemSelected: (LibraryItem) -> Unit,
    onOpenLyrics: () -> Unit,
    onMakeCard: () -> Unit,
    onRepeatModeChange: (RepeatMode) -> Unit,
    onSleepTimerChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    toolClusterStyle: ToolClusterStyle = ToolClusterStyle.Default,
    motionPercent: Int = 100,
    annotationRoot: String? = null,
    onAnnotate: () -> Unit = {},
    cardCoverByDefault: Boolean = false,
    cardRevision: Int = 0,
    initialPage: Int = 0,
    onPageChange: (Int) -> Unit = {},
    onMetadataSaved: (String, com.example.asmrplayer.core.metadata.TagSaveResult) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val preferences = remember(context) { AppPreferences(context.applicationContext) }
    val sizing by preferences.settings.collectAsState(AppSettings())
    val scope = rememberCoroutineScope()
    var showSizing by rememberSaveable { mutableStateOf(false) }
    var editedArtwork by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var metadataRevision by remember(item.id) { mutableIntStateOf(0) }
    var showMetadataEditor by rememberSaveable { mutableStateOf(false) }
    var editTargetId by rememberSaveable { mutableStateOf("") }
    var editTargetUri by rememberSaveable { mutableStateOf<String?>(null) }
    var editTargetTitle by rememberSaveable { mutableStateOf("") }
    var editTargetArtwork by rememberSaveable { mutableStateOf<String?>(null) }
    val artworkItem = item.copy(artworkUri = editedArtwork ?: item.artworkUri)
    var coverFacts by remember(artworkItem.artworkUri) { mutableStateOf<CoverFacts?>(null) }
    var coverFactsLoading by remember(artworkItem.artworkUri) { mutableStateOf(artworkItem.artworkUri != null) }
    var decodedArtworkAspect by remember(artworkItem.artworkUri) { mutableFloatStateOf(0f) }
    val artworkAspect = playerArtworkAspect(decodedArtworkAspect, coverFacts?.aspect)
    LaunchedEffect(artworkItem.artworkUri) {
        try { coverFacts = CoverAccentLoader.resolve(context, artworkItem.artworkUri) }
        finally { coverFactsLoading = false }
    }
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(0, 1), pageCount = { 3 })
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage == 2) {
            pager.scrollToPage(1)
            onPageChange(1)
            onOpenLyrics()
        } else onPageChange(pager.settledPage)
    }
    BackHandler(enabled = pager.currentPage == 1) { scope.launch { pager.animateScrollToPage(0) } }
    if (showSizing) ModalBottomSheet(onDismissRequest = { showSizing = false }, containerColor = PlayerBackground, contentColor = PlayerPrimaryText) {
        PlayerSizingSettings(sizing) { cover, title, lyrics ->
            scope.launch { preferences.setPlayerSizing(cover, title, lyrics) }
        }
    }
    val reader = remember(context) { LocalAudioMetadataReader(context.applicationContext) }
    var metadata by remember(item.id) {
        mutableStateOf(LocalAudioMetadata(durationMs = parseDurationLabel(item.durationLabel)))
    }
    var bookmarks by remember(item.id) { mutableStateOf(emptyList<Long>()) }
    var panel by remember(item.id) { mutableStateOf<PlayerPanel?>(null) }
    var toolsExpanded by remember(item.id) { mutableStateOf(false) }
    var toolsPage by remember(item.id) { mutableStateOf(PlayerToolsPage.Root) }

    // Whether this song has been carded, and which card. Read off the main thread
    // because it lists a directory, and re-read per item so moving through the queue
    // shows each song's own cover rather than the last one's. `cardRevision` is bumped
    // when the studio makes a card, which is the one way this answer can change while
    // the player is on screen.
    val cardRepository = remember(context) { CardRepository(context) }
    var card by remember(item.id) { mutableStateOf<CardSummary?>(null) }
    LaunchedEffect(item.id, cardRevision) {
        card = withContext(Dispatchers.IO) { cardRepository.findForMedia(item.id) }
    }

    // Null until the owner touches the toggle, so the setting keeps deciding until he
    // does. Keyed on the item: moving to the next song is a fresh question, which is
    // what makes the card something he asks for each time rather than something that
    // follows him down the queue.
    var coverChoice by rememberSaveable(item.id) { mutableStateOf<Boolean?>(null) }
    val showingCard = showsCardCover(
        hasCard = card != null,
        choice = coverChoice,
        defaultToCard = cardCoverByDefault,
    )

    if (showMetadataEditor) MetadataEditorDialog(item.copy(id = editTargetId, documentUri = editTargetUri,
        title = editTargetTitle, artworkUri = editTargetArtwork), onDismiss = { showMetadataEditor = false },
        onBeforeSave = { if (playbackState.isCurrent(editTargetId) && playbackState.isPlaying) onTogglePlayPause() },
        onSaved = { saved ->
            if (item.id == editTargetId) { editedArtwork = saved.artworkUri; metadataRevision++; coverChoice = false }
            onMetadataSaved(editTargetId, saved)
        })

    LaunchedEffect(item.id, item.documentUri, metadataRevision) {
        metadata = reader.read(item.documentUri).let { loaded ->
            loaded.copy(durationMs = loaded.durationMs ?: parseDurationLabel(item.durationLabel))
        }
    }

    val isCurrent = playbackState.isCurrent(item.id)
    val durationMs = if (isCurrent && playbackState.hasDuration) {
        playbackState.durationMs
    } else {
        metadata.durationMs ?: 0L
    }
    val timeline = PlayerTimeline(
        durationMs = durationMs,
        positionMs = if (isCurrent) playbackState.positionMs else 0L,
        bookmarksMs = bookmarks,
    )
    val isPlaying = isCurrent && playbackState.isPlaying

    // The lyric stream is the one the wall and the scene already share. A state that
    // belongs to the track that was playing a moment ago is not this track's lyrics,
    // so the document is only taken when it says it describes this item -- the queue
    // can advance while the page stays open.
    val view = if (subtitles.describes(item.id)) subtitles.lyricsView() else null
    val document = (view as? LyricsView.Timed)?.document
    // Position is only this track's position when the session is on this track.
    val lyricPositionMs = playbackState.positionMs.takeIf { isCurrent }
    val lyricPair = lyricPairAt(document, lyricPositionMs)

    val sleepCountdown = rememberSleepCountdown(playbackState.sleepExpiresAtMs)

    // Anchored to the bottom-end corner of the page, which is where the last slot of
    // the tool row is: the button is the fifth item of that row rather than a floating
    // thing beside it, and the row's own padding is chosen to line the two up. No
    // scrim is passed, because a scrim across the whole page would swallow the left
    // swipe that opens the lyric scene.
    //
    // The panel says what pressing each row does, not what the song has. The card row
    // is the one that has to: with no card it offers to make one, with a card it
    // offers the card, and while the card is showing it offers the way back.
    val toolCluster: @Composable BoxScope.() -> Unit = {
        GlassToolCluster(
            expanded = toolsExpanded,
            onExpandedChange = {
                toolsExpanded = it
                if (!it) toolsPage = PlayerToolsPage.Root
            },
            panelMotionMillis = wallMotionDuration(190, motionPercent),
            contentDescription = stringResource(R.string.player_tools),
            style = toolClusterStyle,
            scrim = false,
            triggerIcon = Icons.Default.MoreHoriz,
            // The wall's glyph closes into a cross at 45 degrees; a horizontal ellipsis
            // only becomes a slanted one, which reads as a damaged icon rather than an
            // open panel. The panel growing out of the corner is the feedback here.
            triggerRotationDegrees = 0f,
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            PlayerToolsPanel(
                page = toolsPage,
                style = toolClusterStyle,
                playingCard = showingCard,
                hasCard = card != null,
                armedMinutes = playbackState.sleepTimerMinutes,
                sleepCountdown = sleepCountdown,
                onPageChange = { toolsPage = it },
                onCard = {
                    toolsExpanded = false
                    when {
                        // Nothing to switch to yet, so the row is the way to make one --
                        // straight to the form, not by way of the room, which is about
                        // cards this song does not have.
                        card == null -> onMakeCard()
                        showingCard -> coverChoice = false
                        else -> coverChoice = true
                    }
                },
                onLyrics = {
                    toolsExpanded = false
                    onOpenLyrics()
                },
                onSizing = { toolsExpanded = false; showSizing = true },
                onEditMetadata = {
                    toolsExpanded = false
                    editTargetId = item.id; editTargetUri = item.documentUri
                    editTargetTitle = item.title; editTargetArtwork = artworkItem.artworkUri
                    showMetadataEditor = true
                },
                onBookmarks = {
                    toolsExpanded = false
                    panel = PlayerPanel.Bookmarks
                },
                onChapters = {
                    toolsExpanded = false
                    panel = PlayerPanel.Chapters
                },
                onSleepTimer = {
                    onSleepTimerChange(it)
                    toolsPage = PlayerToolsPage.Root
                },
            )
        }
    }

    panel?.let { selectedPanel ->
        ModalBottomSheet(
            onDismissRequest = { panel = null },
            containerColor = Color(0xFF17181D),
            contentColor = PlayerPrimaryText,
        ) {
            PlayerPanelContent(
                panel = selectedPanel,
                currentMediaId = playbackState.mediaId,
                queueItems = queueItems,
                timeline = timeline,
                onAddBookmark = { bookmarks = timeline.addBookmark().bookmarksMs },
                onRemoveBookmark = { bookmarks = timeline.removeBookmark(it).bookmarksMs },
                onSeekTo = onSeekTo,
                onQueueItemSelected = {
                    panel = null
                    onQueueItemSelected(it)
                },
            )
        }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val header: @Composable () -> Unit = {
        Column {
            PlayerHeader(title = metadata.title ?: item.title, artist = metadata.artist, titlePercent = sizing.playerTitlePercent)
            Box(Modifier.padding(start = 22.dp)) {
                com.example.asmrplayer.ui.annotations.AudioScoreBadge(annotationRoot, item.documentUri, onAnnotate)
            }
        }
    }

    val progress: @Composable () -> Unit = {
        PlayerProgress(
            playbackState = playbackState,
            timeline = timeline,
            isCurrent = isCurrent,
            onSeekTo = onSeekTo,
        )
    }

    val transport: @Composable (Boolean) -> Unit = { compact ->
        PlayerTransport(
            isPlaying = isPlaying,
            isCurrent = isCurrent,
            hasQueue = playbackState.hasQueue,
            onPlayPause = onTogglePlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onSeekBy = onSeekBy,
            canSeek = isCurrent && timeline.durationMs > 0,
            modifier = if (compact) Modifier.padding(vertical = 4.dp) else Modifier,
        )
    }

    // Four icons and the collect button in the fifth slot. The end padding is what
    // makes it a five-slot row: with four 42 dp slots evenly spread across the width
    // that is left, the trailing gap equals the gap between two of them, so the button
    // lands exactly where a fifth slot would have been.
    val footer: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 58.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTool(
                icon = repeatIcon(playbackState.repeatMode),
                description = stringResource(
                    when (playbackState.repeatMode) {
                        RepeatMode.Off -> R.string.repeat_off
                        RepeatMode.All -> R.string.repeat_all
                        RepeatMode.One -> R.string.repeat_one
                    },
                ),
                active = playbackState.repeatMode != RepeatMode.Off,
                onClick = { onRepeatModeChange(playbackState.repeatMode.next()) },
            )
            BottomTool(
                icon = Icons.Default.Timer,
                description = stringResource(R.string.sleep_timer),
                active = playbackState.sleepExpiresAtMs != null,
                onClick = {
                    toolsPage = PlayerToolsPage.SleepTimer
                    toolsExpanded = true
                },
            )
            BottomTool(
                icon = Icons.Default.GraphicEq,
                description = stringResource(R.string.player_lyrics),
                onClick = onOpenLyrics,
            )
            BottomTool(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                description = stringResource(R.string.queue),
                onClick = { panel = PlayerPanel.Queue },
            )
        }
    }

    Box(modifier = modifier.fillMaxSize().background(PlayerBackground)) {
        PlayerBackdrop(artworkItem, coverFacts, coverFactsLoading, motionPercent)
        // The inset box, and the reason the collect button lives in this one rather
        // than around everything: it has to be pinned to the same edge the tool row
        // sits against, or the two would drift apart by the gesture bar's height.
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                            if (page == 0) BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                val artworkSize = minOf(maxWidth * 0.86f, maxHeight * 0.82f, 380.dp)
                                PlayerArtwork(
                                    item = artworkItem,
                                    onDecodedAspect = { if (kotlin.math.abs(decodedArtworkAspect - it) > 0.002f) decodedArtworkAspect = it },
                                    card = card,
                                    showCard = showingCard,
                                    modifier = Modifier.artworkSized(
                                        showingCard = showingCard,
                                        squareSize = artworkSize,
                                        artworkAspect = artworkAspect,
                                        coverPercent = sizing.playerCoverPercent,
                                        maxWidth = maxWidth,
                                        maxHeight = maxHeight,
                                    ),
                                )
                            } else PlayerPlainLyrics(subtitles, item.id, lyricPositionMs, sizing.playerLyricsPercent, onSeekTo.takeIf { isCurrent })
                        }
                        // Landscape has no reference to copy, so the collect button
                        // stays where it has always been: the cover's corner.
                        toolCluster()
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                    ) {
                        header()
                        PlayerCoverLyrics(lyricPair, sizing.playerLyricsPercent)
                        Spacer(Modifier.height(10.dp))
                        progress()
                        transport(true)
                        footer()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        beyondViewportPageCount = 0,
                    ) { page ->
                        when (page) {
                            0 -> Column(Modifier.fillMaxSize()) {
                                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    PlayerArtwork(
                                        item = artworkItem, card = card, showCard = showingCard,
                                        onDecodedAspect = { if (kotlin.math.abs(decodedArtworkAspect - it) > 0.002f) decodedArtworkAspect = it },
                                        modifier = Modifier.artworkSized(
                                            showingCard = showingCard,
                                            squareSize = maxWidth * 0.85f,
                                            artworkAspect = artworkAspect,
                                            coverPercent = sizing.playerCoverPercent,
                                            maxWidth = maxWidth, maxHeight = maxHeight,
                                        ),
                                    )
                                }
                                PlayerCoverLyrics(lyricPair, sizing.playerLyricsPercent)
                            }
                            1 -> PlayerPlainLyrics(
                                subtitles = subtitles, itemId = item.id,
                                positionMs = lyricPositionMs, textPercent = sizing.playerLyricsPercent,
                                onSeekTo = onSeekTo.takeIf { isCurrent },
                            )
                            else -> PlayerPlainLyrics(subtitles, item.id, lyricPositionMs, sizing.playerLyricsPercent, null)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    progress()
                    transport(false)
                    footer()
                }
            }
            // Portrait only: there the button is the fifth slot of the tool row, and
            // the inset box's bottom-end corner is exactly that slot. See the layout
            // note on [footer].
            if (!landscape) toolCluster()
        }
    }
}

/** The glyph for a repeat mode: the two states differ, the "off" one is dimmed. */
private fun repeatIcon(mode: RepeatMode): ImageVector = when (mode) {
    RepeatMode.Off, RepeatMode.All -> Icons.Default.Repeat
    RepeatMode.One -> Icons.Default.RepeatOne
}

/**
 * The wording of one duration on the timer page, or null when it is not a duration
 * this app offers.
 *
 * Written out rather than built from the number, because [GlassTool] resolves its
 * label from a resource and a resource cannot take an argument here -- and "45 分钟"
 * is a phrase, not a number with a unit glued on. The null return is what keeps this
 * honest: the two lists are separate, so a choice added to [SleepTimerChoices] without
 * a phrase to go with it should be visibly missing rather than silently labelled
 * something else.
 */
internal fun sleepChoiceLabel(minutes: Int): Int? = when (minutes) {
    0 -> R.string.sleep_timer_off
    5 -> R.string.sleep_timer_5
    10 -> R.string.sleep_timer_10
    15 -> R.string.sleep_timer_15
    30 -> R.string.sleep_timer_30
    45 -> R.string.sleep_timer_45
    60 -> R.string.sleep_timer_60
    90 -> R.string.sleep_timer_90
    else -> null
}

/**
 * The countdown on the timer, ticking once a second while it is armed.
 *
 * The service owns the deadline and this owns the reading of it, which is the right
 * split: a deadline is a fact that has to outlive the screen, and a countdown is a
 * thing that is only ever looked at. Nothing here pauses anything -- when the reading
 * runs out the label becomes null, and the pause has already happened on the other
 * side of the session.
 */
@Composable
private fun rememberSleepCountdown(expiresAtMs: Long?): String? {
    if (expiresAtMs == null) return null
    return produceState(
        initialValue = sleepRemainingLabel(expiresAtMs, System.currentTimeMillis()),
        key1 = expiresAtMs,
    ) {
        while (value != null) {
            delay(1_000L)
            value = sleepRemainingLabel(expiresAtMs, System.currentTimeMillis())
        }
    }.value
}

/** The title, the artist, and the mark saying where the sound is going. */
@Composable
private fun PlayerHeader(title: String, artist: String?, titlePercent: Int = 100) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 18.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = PlayerPrimaryText,
                fontSize = (20f * titlePercent / 100).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!artist.isNullOrBlank()) Text(
                artist,
                color = PlayerSecondaryText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AudioOutputMark()
    }
}

/**
 * Where the sound is going, as a mark rather than a control.
 *
 * There is no output picker in this app and this does not pretend to be one: it is
 * read-only, drawn in the secondary colour with no ripple, and it exists because
 * knowing at a glance whether the audio is on the speaker or in the headphones is
 * worth something on a page used in the dark. Read off the main thread, because
 * enumerating output devices is a call into the audio service.
 */
@Composable
private fun AudioOutputMark() {
    val context = LocalContext.current
    var output by remember(context) { mutableStateOf<AudioOutput?>(null) }
    LaunchedEffect(context) {
        output = withContext(Dispatchers.IO) { pickActiveOutput(androidAudioOutputs(context)) }
    }
    val description = when {
        output?.name != null -> stringResource(R.string.audio_output_to, output!!.name!!)
        output?.kind == AudioOutputKind.BuiltIn -> stringResource(R.string.audio_output_builtin)
        else -> stringResource(R.string.audio_output)
    }
    Icon(
        imageVector = Icons.Default.Podcasts,
        contentDescription = description,
        tint = PlayerSecondaryText,
        modifier = Modifier.size(22.dp),
    )
}

/** The seek bar and the two times, with the "not this song" and error states under it. */
@Composable
private fun PlayerProgress(
    playbackState: PlaybackState,
    timeline: PlayerTimeline,
    isCurrent: Boolean,
    onSeekTo: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
    ) {
        FoliaSeekBar(
            playbackState = playbackState,
            onSeekTo = onSeekTo,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatPlaybackTime(if (isCurrent) playbackState.positionMs else timeline.positionMs),
                color = PlayerSecondaryText,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                if (timeline.durationMs > 0L) {
                    formatPlaybackTime(timeline.durationMs)
                } else {
                    stringResource(R.string.unknown_duration)
                },
                color = PlayerSecondaryText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (!isCurrent) {
            Text(
                stringResource(R.string.player_not_current_item),
                color = PlayerSecondaryText,
                style = MaterialTheme.typography.labelSmall,
            )
        } else if (playbackState.errorMessage != null) {
            Text(
                stringResource(R.string.player_error, playbackState.errorMessage),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Previous, skip backward, play/pause, skip forward and next. */
@Composable
private fun PlayerTransport(
    isPlaying: Boolean,
    isCurrent: Boolean,
    hasQueue: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onSeekBy: (Long) -> Unit,
    canSeek: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
            Icon(
                Icons.Default.SkipPrevious,
                stringResource(R.string.previous_track),
                tint = PlayerPrimaryText,
                modifier = Modifier.size(34.dp),
            )
        }
        IconButton(onClick = { onSeekBy(-SkipSeconds * 1000L) }, enabled = canSeek) {
            Icon(Icons.Default.Replay10, stringResource(R.string.skip_back_seconds, SkipSeconds), tint = PlayerPrimaryText.copy(alpha = if (canSeek) 1f else 0.38f))
        }
        IconButton(
            onClick = onPlayPause,
            enabled = isCurrent || hasQueue,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (isPlaying) R.string.pause else R.string.play),
                Modifier.size(35.dp),
                tint = PlayerPrimaryText,
            )
        }
        IconButton(onClick = { onSeekBy(SkipSeconds * 1000L) }, enabled = canSeek) {
            Icon(Icons.Default.Forward10, stringResource(R.string.skip_forward_seconds, SkipSeconds), tint = PlayerPrimaryText.copy(alpha = if (canSeek) 1f else 0.38f))
        }
        IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
            Icon(
                Icons.Default.SkipNext,
                stringResource(R.string.next_track),
                tint = PlayerPrimaryText,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

/** One slot of the tool row. Sized to match the collect button so the row reads as five. */
@Composable
private fun BottomTool(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) PlayerPrimaryText else PlayerSecondaryText,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * The collect button's panel: the list of things this song can do, and the timer page
 * behind one of them.
 *
 * Nothing here reads the card or the lyric state for itself -- the caller owns all of
 * it and passes down what each row needs, because the row's meaning depends on state
 * the panel has no business holding.
 */
@Composable
private fun PlayerToolsPanel(
    page: PlayerToolsPage,
    style: ToolClusterStyle,
    playingCard: Boolean,
    hasCard: Boolean,
    armedMinutes: Int?,
    sleepCountdown: String?,
    onPageChange: (PlayerToolsPage) -> Unit,
    onCard: () -> Unit,
    onLyrics: () -> Unit,
    onSizing: () -> Unit,
    onEditMetadata: () -> Unit,
    onBookmarks: () -> Unit,
    onChapters: () -> Unit,
    onSleepTimer: (Int) -> Unit,
) {
    if (page == PlayerToolsPage.SleepTimer) {
        // Every row here is a duration with its own wording, so the check is the only
        // trailing affordance a row can carry: one row is chosen, and the row that is
        // chosen is the one the countdown belongs to. The countdown itself is on the
        // row that opens this page, where it can be read without opening anything.
        GlassOptionsPanel(
            titleRes = R.string.sleep_timer,
            onBack = { onPageChange(PlayerToolsPage.Root) },
            style = style,
            options = SleepTimerChoices.map { minutes ->
                GlassTool(
                    icon = Icons.Default.Timer,
                    labelRes = sleepChoiceLabel(minutes) ?: R.string.sleep_timer_off,
                    selected = minutes == (armedMinutes ?: 0),
                    onClick = { onSleepTimer(minutes) },
                )
            },
        )
        return
    }

    GlassToolsPanel(
        style = style,
        tools = listOf(
            GlassTool(
                icon = Icons.Default.Style,
                labelRes = if (playingCard) R.string.card_cover_original else R.string.card_studio,
                onClick = onCard,
            ),
            GlassTool(
                icon = Icons.Default.Subtitles,
                labelRes = R.string.player_lyrics,
                onClick = onLyrics,
            ),
            GlassTool(icon = Icons.Default.Tune, labelRes = R.string.player_sizing, onClick = onSizing),
            GlassTool(icon = Icons.Default.Tune, labelRes = R.string.metadata_edit, onClick = onEditMetadata),
            // The row that opens the timer is also the row that says how long is left,
            // because the countdown is the whole of what there is to know about an
            // armed timer and it should not need a tap to read.
            GlassTool(
                icon = Icons.Default.Timer,
                labelRes = R.string.sleep_timer,
                valueText = sleepCountdown,
                onClick = { onPageChange(PlayerToolsPage.SleepTimer) },
            ),
            GlassTool(
                icon = Icons.Default.Bookmark,
                labelRes = R.string.bookmarks,
                onClick = onBookmarks,
            ),
            GlassTool(
                icon = Icons.AutoMirrored.Filled.ViewList,
                labelRes = R.string.chapters,
                onClick = onChapters,
            ),
        ),
    )
}

/** Card uses its own geometry; ordinary artwork fits its intrinsic proportions. */
private fun Modifier.artworkSized(
    showingCard: Boolean,
    squareSize: Dp,
    artworkAspect: Float = 1f,
    coverPercent: Int = 100,
    maxWidth: Dp,
    maxHeight: Dp,
): Modifier = if (showingCard) {
    val aspect = CardLayerGeometry.CanvasAspect
    height(minOf(maxHeight * 0.86f, (maxWidth * 0.86f) / aspect, 520.dp))
        .aspectRatio(aspect)
} else {
    val dimensions = fitPlayerArtwork(
        maxWidth.value, maxHeight.value, squareSize.value, artworkAspect, coverPercent,
    )
    size(dimensions.first.dp, dimensions.second.dp)
}

/** Clean artwork (Fit) or the user's card; paging gestures belong to the parent pager. */
@Composable
private fun PlayerArtwork(
    item: LibraryItem,
    card: CardSummary?,
    showCard: Boolean,
    onDecodedAspect: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeDescription = stringResource(R.string.player_swipe_pages)

    if (showCard && card != null) {
        CardSurface(
            cardId = card.id,
            bare = true,
            modifier = modifier,
        )
        return
    }

    Box(
        modifier = modifier
            .clip(ArtworkShape)
            .background(artworkBrush(item.paletteSeed))
            .semantics { contentDescription = swipeDescription },
    ) {
        if (item.artworkUri != null) {
            AsyncImage(
                model = item.artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    val image = state.result.image
                    if (image.width > 0 && image.height > 0) {
                        onDecodedAspect(image.width.toFloat() / image.height)
                    }
                },
            )
        } else {
            Box(Modifier.fillMaxSize().background(artworkBrush(item.paletteSeed)))
        }
    }
}

@Composable
private fun PlayerPanelContent(
    panel: PlayerPanel,
    currentMediaId: String?,
    queueItems: List<LibraryItem>,
    timeline: PlayerTimeline,
    onAddBookmark: () -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onQueueItemSelected: (LibraryItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 540.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 30.dp),
    ) {
        // The heading carries the position for the queue, because the panel is where
        // "which of these is playing" is a question: the page's header used to say it
        // and no longer has room, and a list of twelve titles with no marker in it
        // would be the one place the answer is worth having.
        val heading = when (panel) {
            PlayerPanel.Queue -> {
                val index = queueItems.indexOfFirst { it.id == currentMediaId }
                if (index >= 0) {
                    stringResource(R.string.queue) + " · " +
                        stringResource(R.string.queue_position, index + 1, queueItems.size)
                } else {
                    stringResource(R.string.queue)
                }
            }
            PlayerPanel.Bookmarks -> stringResource(R.string.bookmarks)
            PlayerPanel.Chapters -> stringResource(R.string.chapters)
        }
        Text(
            heading,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        when (panel) {
            PlayerPanel.Queue -> queueItems.forEachIndexed { index, queueItem ->
                val isCurrent = queueItem.id == currentMediaId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onQueueItemSelected(queueItem) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text((index + 1).toString(), Modifier.width(34.dp), color = PlayerSecondaryText)
                    Icon(
                        Icons.Default.LibraryMusic,
                        null,
                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else PlayerSecondaryText,
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            queueItem.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isCurrent) FontWeight.Bold else null,
                        )
                        Text(queueItem.subtitle.uppercase(Locale.ROOT), color = PlayerSecondaryText)
                    }
                }
                HorizontalDivider(color = Color(0xFF303137))
            }
            PlayerPanel.Bookmarks -> {
                Button(
                    onClick = onAddBookmark,
                    enabled = timeline.durationMs > 0L,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.BookmarkAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_bookmark_at, formatPlaybackTime(timeline.positionMs)))
                }
                Spacer(Modifier.height(10.dp))
                if (timeline.bookmarksMs.isEmpty()) {
                    PanelMessage(stringResource(R.string.no_bookmarks))
                } else {
                    timeline.bookmarksMs.forEach { bookmark ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeekTo(bookmark) }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(formatPlaybackTime(bookmark), Modifier.weight(1f))
                            IconButton(onClick = { onRemoveBookmark(bookmark) }) {
                                Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete_bookmark))
                            }
                        }
                    }
                }
            }
            PlayerPanel.Chapters -> PanelMessage(stringResource(R.string.no_embedded_chapters))
        }
    }
}

@Composable
private fun PanelMessage(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 26.dp),
        color = PlayerSecondaryText,
        textAlign = TextAlign.Center,
    )
}

// The subtitle surface lives in ui/SubtitleDetail.kt. It keeps the immersive
// player's palette, so the two constants above are shared rather than duplicated.
