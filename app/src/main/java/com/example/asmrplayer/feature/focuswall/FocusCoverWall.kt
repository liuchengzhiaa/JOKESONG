package com.example.asmrplayer.feature.focuswall

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.asmrplayer.R
import com.example.asmrplayer.core.artwork.CoverAccentLoader
import com.example.asmrplayer.core.media.PlaybackState
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import com.example.asmrplayer.feature.folderSummaryLine
import com.example.asmrplayer.feature.player.formatPlaybackTime
import com.example.asmrplayer.ui.theme.artworkBrush
import com.example.asmrplayer.ui.theme.artworkAccent
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How quickly a released fling decays. Release is deliberately inertial and does
 * not snap to a card: snapping would move the wall after the user had stopped
 * moving it, which is the same "it moved on its own" feeling that made focus
 * follow the viewport unworkable.
 */
private const val FlingFrictionMultiplier = 1.35f

enum class FocusWallStyle {
    Bento,
    Folia,
}

@Composable
fun FocusCoverWall(
    items: List<LibraryItem>,
    contentKey: String,
    focusedItemId: String?,
    playbackState: PlaybackState,
    viewportState: FocusWallViewportState,
    onFocusedItemChange: (String?) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onDensityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: FocusWallStyle = FocusWallStyle.Bento,
    onActivate: (LibraryItem) -> Unit = {},
    onPlay: (LibraryItem) -> Unit = {},
    /**
     * Brings the wall's focus to the item that is playing, following the queue back
     * to its folder when it is not on this wall. Null when that cannot be done, and
     * the entry is then shown disabled.
     */
    onRevealPlayingItem: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val density = viewportState.density
            AssistChip(
                onClick = {
                    val next = density.next()
                    viewportState.densityOrdinal = next.ordinal
                    onDensityChange(next.ordinal)
                },
                label = {
                    Text(
                        stringResource(
                            R.string.wall_density_value,
                            stringResource(density.labelResource()),
                        ),
                    )
                },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.zoom_percent, (viewportState.zoom * 100).roundToInt()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Text(
            text = stringResource(R.string.focus_wall_hint),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            FocusWallCanvas(
                items = items,
                contentKey = contentKey,
                focusedItemId = focusedItemId,
                playbackState = playbackState,
                viewportState = viewportState,
                style = style,
                onFocusedItemChange = onFocusedItemChange,
                onSeekTo = onSeekTo,
                onCardClick = { item ->
                    when (resolveCardTap(focusedItemId, item.id)) {
                        // A tap on any other card moves focus to it. Nothing else
                        // happens yet -- the pile of actions below belongs to the
                        // second tap, once the card already holds focus.
                        is FocusWallIntent.Focus -> onFocusedItemChange(item.id)
                        is FocusWallIntent.Activate -> when (item.type) {
                            LibraryItemType.Folder -> onActivate(item)
                            // Tapping the focused card of the item that is already
                            // current opens the full player instead of restarting it.
                            LibraryItemType.Audio -> {
                                if (playbackState.isCurrent(item.id)) {
                                    onActivate(item)
                                } else {
                                    onPlay(item)
                                }
                            }
                        }
                    }
                },
                onPlayPause = { item ->
                    when {
                        item.type == LibraryItemType.Folder -> onPlay(item)
                        playbackState.isCurrent(item.id) -> onTogglePlayPause()
                        else -> onPlay(item)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            WallToolsOverlay(
                contentKey = contentKey,
                onRevealPlayingItem = onRevealPlayingItem,
            )
        }
    }
}

/** One entry of the wall's tools panel. Adding a tool is adding a row here. */
private data class WallTool(
    val icon: ImageVector,
    val labelRes: Int,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/**
 * The wall's floating tools button and the small panel that opens beside it.
 *
 * It floats over the wall rather than inside it, so it stays the same size and in
 * the same place while the wall is zoomed or panned. The panel is anchored to the
 * button instead of being a modal sheet: it is a handful of actions on a surface
 * that is already full-screen and gesture-driven, and a sheet would take the wall
 * away to show four rows.
 *
 * While the panel is open a transparent scrim covers the wall, so a tap that means
 * "close this" cannot also land on a card behind it.
 *
 * The button is always there, even while every tool it holds is unavailable: it is
 * the wall's one growing actions surface, and one that appears and disappears would
 * be harder to find than a row that is greyed out until it applies.
 */
@Composable
private fun WallToolsOverlay(
    contentKey: String,
    onRevealPlayingItem: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Keyed on the content, so changing folder or wall style closes the panel.
    var expanded by remember(contentKey) { mutableStateOf(false) }

    // The overlay fills the wall, so the scrim below covers all of it -- the button
    // and the panel are placed inside, at the bottom end.
    Box(modifier = modifier.fillMaxSize()) {
        if (expanded) {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = interactionSource, indication = null) {
                        expanded = false
                    },
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedVisibility(visible = expanded) {
                WallToolsPanel(
                    tools = listOf(
                        WallTool(
                            icon = Icons.Default.MyLocation,
                            labelRes = R.string.focus_playing_item,
                            enabled = onRevealPlayingItem != null,
                            onClick = {
                                expanded = false
                                onRevealPlayingItem?.invoke()
                            },
                        ),
                    ),
                )
            }
            SmallFloatingActionButton(
                onClick = { expanded = !expanded },
                containerColor = Color(0xFF17181D),
                contentColor = Color.White,
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = stringResource(R.string.wall_tools),
                )
            }
        }
    }
}

@Composable
private fun WallToolsPanel(tools: List<WallTool>) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF17181D),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            tools.forEach { tool ->
                Row(
                    modifier = Modifier
                        .clickable(enabled = tool.enabled, onClick = tool.onClick)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (tool.enabled) Color.White else Color.White.copy(alpha = 0.38f),
                    )
                    Text(
                        text = stringResource(tool.labelRes),
                        color = if (tool.enabled) Color.White else Color.White.copy(alpha = 0.38f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusWallCanvas(
    items: List<LibraryItem>,
    contentKey: String,
    focusedItemId: String?,
    playbackState: PlaybackState,
    viewportState: FocusWallViewportState,
    style: FocusWallStyle,
    onFocusedItemChange: (String?) -> Unit,
    onCardClick: (LibraryItem) -> Unit,
    onPlayPause: (LibraryItem) -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val orientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        FocusWallOrientation.Landscape
    } else {
        FocusWallOrientation.Portrait
    }
    val localDensity = LocalDensity.current
    val scope = rememberCoroutineScope()
    var activePanAnimation by remember(contentKey) { mutableStateOf<Job?>(null) }

    fun animatePanTo(target: Offset, durationMillis: Int) {
        activePanAnimation?.cancel()
        activePanAnimation = scope.launch {
            Animatable(viewportState.pan, Offset.VectorConverter).animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = durationMillis),
            ) {
                viewportState.pan = value
            }
        }
    }

    /**
     * Lets a released drag coast to a stop.
     *
     * There is deliberately no snap and no focus change here. Snapping would move
     * the wall after the user had already stopped moving it, and re-focusing the
     * card nearest the centre -- what this used to do -- reflowed the whole block
     * and re-centred the canvas on every release, which made the wall impossible
     * to scroll. Focus is now only ever moved by a double tap.
     */
    fun settlePan(velocity: Offset, bounds: FocusWallWorldBounds?) {
        activePanAnimation?.cancel()
        if (velocity.getDistance() < 1f) return
        var coasting: Job? = null
        coasting = scope.launch {
            Animatable(viewportState.pan, Offset.VectorConverter).animateDecay(
                initialVelocity = velocity,
                animationSpec = exponentialDecay<Offset>(
                    frictionMultiplier = FlingFrictionMultiplier,
                ),
            ) {
                val bounded = clampWallPan(value.x, value.y, viewportState.zoom, bounds)
                viewportState.pan = Offset(bounded.x, bounded.y)
                // Stop coasting the moment the wall's edge is reached: otherwise
                // the decay would keep producing values the clamp holds still,
                // keeping the viewport pinned there until it ran itself out.
                if (bounded.x != value.x || bounded.y != value.y) coasting?.cancel()
            }
        }
        activePanAnimation = coasting
    }

    BoxWithConstraints(modifier = modifier.clip(RoundedCornerShape(0.dp))) {
        val viewportWidth = with(localDensity) { maxWidth.toPx() }
        val viewportHeight = with(localDensity) { maxHeight.toPx() }
        val wallGapPx = with(localDensity) { 2.dp.toPx() }
        val layoutItems = remember(items) {
            items.map { FocusWallLayoutItem(it.id, it.paletteSeed) }
        }
        val placements = remember(
            layoutItems,
            viewportWidth,
            viewportHeight,
            orientation,
            viewportState.densityOrdinal,
            focusedItemId,
            style,
            wallGapPx,
        ) {
            when (style) {
                FocusWallStyle.Bento -> FocusWallLayoutEngine.layout(
                    items = layoutItems,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    orientation = orientation,
                    density = viewportState.density,
                    focusedItemId = focusedItemId,
                    gapPx = wallGapPx,
                )
                FocusWallStyle.Folia -> FoliaWallLayoutEngine.layout(
                    items = layoutItems,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    orientation = orientation,
                    density = viewportState.density,
                    focusedItemId = focusedItemId,
                    gapPx = wallGapPx,
                )
            }
        }
        val placementById = remember(placements) { placements.associateBy(FocusWallPlacement::itemId) }
        val itemById = remember(items) { items.associateBy(LibraryItem::id) }

        LaunchedEffect(focusedItemId, placements) {
            val focused = focusedItemId?.let(placementById::get) ?: return@LaunchedEffect
            if (viewportState.restored) {
                // Returning to a visited folder keeps the viewport it was left
                // with instead of animating over it.
                viewportState.restored = false
                return@LaunchedEffect
            }
            animatePanTo(centeredPan(focused, viewportState.zoom), durationMillis = 280)
        }

        SubcomposeLayout(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(placements, orientation, contentKey) {
                    val worldBounds = wallWorldBounds(placements)
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        activePanAnimation?.cancel()
                        val velocityTracker = VelocityTracker().apply {
                            addPosition(firstDown.uptimeMillis, firstDown.position)
                        }
                        var pastTouchSlop = false
                        var accumulatedPan = Offset.Zero

                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) {
                                // The focused card's seek bar owns this gesture;
                                // the canvas must not pan underneath it.
                                continue
                            }
                            val panChange = event.calculatePan()
                            val zoomChange = event.calculateZoom()
                            val centroid = event.calculateCentroid()
                            if (centroid.isSpecified) {
                                event.changes.firstOrNull()?.let { change ->
                                    velocityTracker.addPosition(change.uptimeMillis, centroid)
                                }
                            }

                            accumulatedPan += panChange
                            if (!pastTouchSlop) {
                                pastTouchSlop = accumulatedPan.getDistance() > viewConfiguration.touchSlop ||
                                    abs(zoomChange - 1f) > 0.012f
                            }

                            if (pastTouchSlop) {
                                val oldZoom = viewportState.zoom
                                val newZoom = (oldZoom * zoomChange)
                                    .coerceIn(MinimumCanvasZoom, MaximumCanvasZoom)
                                val ratio = newZoom / oldZoom
                                val centroidFromCenter = if (centroid.isSpecified) {
                                    centroid - Offset(size.width / 2f, size.height / 2f)
                                } else {
                                    Offset.Zero
                                }
                                viewportState.zoom = newZoom
                                val panned = viewportState.pan * ratio +
                                    centroidFromCenter * (1f - ratio) + panChange
                                val bounded = clampWallPan(panned.x, panned.y, newZoom, worldBounds)
                                viewportState.pan = Offset(bounded.x, bounded.y)
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (pastTouchSlop) {
                            val velocity = velocityTracker.calculateVelocity()
                            settlePan(Offset(velocity.x, velocity.y), worldBounds)
                        }
                    }
                },
        ) { constraints ->
            if (placements.isEmpty()) {
                return@SubcomposeLayout layout(constraints.maxWidth, constraints.maxHeight) {}
            }

            val zoom = viewportState.zoom
            val panOffset = viewportState.pan
            val verticalBuffer = constraints.maxHeight * 0.75f
            val minWorldY = (-constraints.maxHeight / 2f - panOffset.y - verticalBuffer) / zoom
            val maxWorldY = (constraints.maxHeight / 2f - panOffset.y + verticalBuffer) / zoom
            val horizontalBuffer = constraints.maxWidth * 0.35f
            val minWorldX = (-constraints.maxWidth / 2f - panOffset.x - horizontalBuffer) / zoom
            val maxWorldX = (constraints.maxWidth / 2f - panOffset.x + horizontalBuffer) / zoom
            val nearViewport = placementsNearWorldRect(
                placements = placements,
                minCenterX = minWorldX,
                maxCenterX = maxWorldX,
                minCenterY = minWorldY,
                maxCenterY = maxWorldY,
            )
            val visible = nearViewport.filter { placement ->
                val screenCenterX = constraints.maxWidth / 2f + placement.centerX * zoom + panOffset.x
                val screenCenterY = constraints.maxHeight / 2f + placement.centerY * zoom + panOffset.y
                val depthScale = focusWallDepthScale(
                    offsetFromCenterX = screenCenterX - constraints.maxWidth / 2f,
                    offsetFromCenterY = screenCenterY - constraints.maxHeight / 2f,
                    viewportWidth = constraints.maxWidth.toFloat(),
                    viewportHeight = constraints.maxHeight.toFloat(),
                )
                val halfWidth = placement.width * zoom / 2f
                val halfHeight = placement.height * zoom / 2f
                screenCenterX + halfWidth >= -horizontalBuffer &&
                    screenCenterX - halfWidth <= constraints.maxWidth + horizontalBuffer &&
                    screenCenterY + halfHeight >= -verticalBuffer &&
                    screenCenterY - halfHeight <= constraints.maxHeight + verticalBuffer
            }.sortedBy { placement ->
                val screenCenterX = constraints.maxWidth / 2f + placement.centerX * zoom + panOffset.x
                val screenCenterY = constraints.maxHeight / 2f + placement.centerY * zoom + panOffset.y
                val depth = focusWallDepthScale(
                    offsetFromCenterX = screenCenterX - constraints.maxWidth / 2f,
                    offsetFromCenterY = screenCenterY - constraints.maxHeight / 2f,
                    viewportWidth = constraints.maxWidth.toFloat(),
                    viewportHeight = constraints.maxHeight.toFloat(),
                )
                depth + if (placement.itemId == focusedItemId) 10f else 0f
            }

            val measured = visible.mapNotNull { placement ->
                val item = itemById[placement.itemId] ?: return@mapNotNull null
                val isFocused = placement.itemId == focusedItemId
                val isCurrent = playbackState.isCurrent(placement.itemId)
                val screenCenterX = constraints.maxWidth / 2f + placement.centerX * zoom + panOffset.x
                val screenCenterY = constraints.maxHeight / 2f + placement.centerY * zoom + panOffset.y
                val depthScale = focusWallDepthScale(
                    offsetFromCenterX = screenCenterX - constraints.maxWidth / 2f,
                    offsetFromCenterY = screenCenterY - constraints.maxHeight / 2f,
                    viewportWidth = constraints.maxWidth.toFloat(),
                    viewportHeight = constraints.maxHeight.toFloat(),
                )
                val visualAlpha = when {
                    isFocused -> 1f
                    focusedItemId != null -> (0.48f + depthScale * 0.34f).coerceAtMost(0.78f)
                    else -> 0.72f + depthScale * 0.28f
                }
                val placeable = subcompose(placement.itemId) {
                    val animatedCenterX by animateFloatAsState(
                        targetValue = placement.centerX,
                        animationSpec = tween(durationMillis = 320),
                        label = "wallCardX",
                    )
                    val animatedCenterY by animateFloatAsState(
                        targetValue = placement.centerY,
                        animationSpec = tween(durationMillis = 320),
                        label = "wallCardY",
                    )
                    val animatedWidth by animateFloatAsState(
                        targetValue = placement.width,
                        animationSpec = tween(durationMillis = 320),
                        label = "wallCardWidth",
                    )
                    val animatedHeight by animateFloatAsState(
                        targetValue = placement.height,
                        animationSpec = tween(durationMillis = 320),
                        label = "wallCardHeight",
                    )
                    FocusCoverCard(
                        item = item,
                        isFocused = isFocused,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && playbackState.isPlaying,
                        playbackState = playbackState,
                        onClick = { onCardClick(item) },
                        onPlayPause = { onPlayPause(item) },
                        onSeekTo = onSeekTo,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom * animatedWidth / placement.width
                                scaleY = zoom * animatedHeight / placement.height
                                translationX = (animatedCenterX - placement.centerX) * zoom
                                translationY = (animatedCenterY - placement.centerY) * zoom
                                alpha = visualAlpha
                                shadowElevation = if (isFocused) 18.dp.toPx() else 3.dp.toPx()
                            },
                    )
                }.first().measure(
                    Constraints.fixed(
                        width = placement.width.roundToInt().coerceAtLeast(1),
                        height = placement.height.roundToInt().coerceAtLeast(1),
                    ),
                )
                placement to placeable
            }

            layout(constraints.maxWidth, constraints.maxHeight) {
                measured.forEach { (placement, placeable) ->
                    val centerX = constraints.maxWidth / 2f + placement.centerX * zoom + panOffset.x
                    val centerY = constraints.maxHeight / 2f + placement.centerY * zoom + panOffset.y
                    placeable.placeRelative(
                        x = (centerX - placeable.width / 2f).roundToInt(),
                        y = (centerY - placeable.height / 2f).roundToInt(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusCoverCard(
    item: LibraryItem,
    isFocused: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    playbackState: PlaybackState,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val sampledAccentArgb by produceState<Int?>(
        null,
        item.artworkUri,
        isFocused,
    ) {
        value = if (isFocused) {
            CoverAccentLoader.resolve(context, item.artworkUri)
        } else {
            null
        }
    }
    val coverAccent = sampledAccentArgb?.let { Color(it) } ?: artworkAccent(item.paletteSeed)
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) coverAccent.copy(alpha = 0.76f) else Color.Transparent,
        label = "focusBorder",
    )
    val borderWidth by animateFloatAsState(if (isFocused) 2.2f else 0f, label = "focusBorderWidth")
    val shape = RoundedCornerShape(if (isFocused) 12.dp else 8.dp)

    Surface(
        modifier = modifier
            .border(borderWidth.dp, borderColor, shape)
            .clip(shape)
            // Plain clickable, deliberately: binding onDoubleClick as well would
            // make Compose hold every single tap back for the double-tap timeout
            // before delivering it, which is felt as lag on the wall's main
            // gesture. "Double tap to open" still works -- see resolveCardTap.
            .clickable(onClick = onClick),
        shape = shape,
        tonalElevation = if (isFocused) 8.dp else 1.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(artworkBrush(item.paletteSeed)),
        ) {
            val compactCard = !isFocused && (maxWidth < 116.dp || maxHeight < 112.dp)
            if (item.artworkUri != null) {
                AsyncImage(
                    model = item.artworkUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x08000000),
                            0.46f to Color(0x24000000),
                            1f to Color(0xEB05070B),
                        ),
                    ),
            )

            Icon(
                imageVector = if (item.type == LibraryItemType.Folder) {
                    Icons.Default.Folder
                } else {
                    Icons.Default.LibraryMusic
                },
                contentDescription = stringResource(
                    if (item.type == LibraryItemType.Folder) R.string.folder_type else R.string.audio_type,
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (compactCard) 6.dp else 12.dp)
                    .size(if (isFocused) 34.dp else if (compactCard) 22.dp else 30.dp)
                    .background(Color(0xB814171D), CircleShape)
                    .padding(if (compactCard) 5.dp else 7.dp),
                tint = Color.White,
            )

            if (isCurrent) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(if (compactCard) 6.dp else 12.dp)
                        .background(Color(0xD014171D), CircleShape)
                        .padding(
                            horizontal = if (compactCard) 6.dp else 10.dp,
                            vertical = if (compactCard) 4.dp else 6.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(if (compactCard) 14.dp else 17.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    if (!compactCard) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            stringResource(R.string.now_playing),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(if (isFocused) 18.dp else if (compactCard) 8.dp else 14.dp),
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = if (isFocused) 24.sp else if (compactCard) 12.sp else 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (compactCard) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compactCard) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.detailLine(),
                        color = Color(0xFFDCE1EA),
                        style = if (isFocused) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Only audio carries an action row here. A folder owns no playable
                // queue of its own -- the folder listing excludes child folders --
                // and tapping the focused cover already resolves to the same
                // activation the card used to spell out, so a focused folder shows
                // nothing but its own title and counts.
                if (isFocused && item.type == LibraryItemType.Audio) {
                    if (isCurrent) {
                        Spacer(Modifier.height(8.dp))
                        CompactSeekBar(playbackState = playbackState, onSeekTo = onSeekTo)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledIconButton(onClick = onPlayPause) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.pause else R.string.play,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact seekable timeline for the focused card. It reports the shared playback
 * position and seeks the real player; it never models progress locally.
 */
@Composable
private fun CompactSeekBar(
    playbackState: PlaybackState,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubFraction by remember(playbackState.mediaId) { mutableStateOf<Float?>(null) }
    val shownFraction = scrubFraction ?: playbackState.progress
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatPlaybackTime(playbackState.positionForFraction(shownFraction)),
            color = Color(0xFFDCE1EA),
            style = MaterialTheme.typography.labelSmall,
        )
        Slider(
            value = shownFraction,
            onValueChange = { scrubFraction = it },
            onValueChangeFinished = {
                scrubFraction?.let { fraction ->
                    onSeekTo(playbackState.positionForFraction(fraction))
                }
                scrubFraction = null
            },
            enabled = playbackState.hasDuration,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color(0x66FFFFFF),
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text(
            text = if (playbackState.hasDuration) {
                formatPlaybackTime(playbackState.durationMs)
            } else {
                stringResource(R.string.unknown_duration)
            },
            color = Color(0xFFDCE1EA),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun LibraryItem.detailLine(): String = when (type) {
    LibraryItemType.Folder -> folderSummaryLine(childFolderCount, audioCount)
    LibraryItemType.Audio -> durationLabel ?: subtitle.ifBlank { stringResource(R.string.audio_type) }
}

private fun centeredPan(placement: FocusWallPlacement, zoom: Float): Offset = Offset(
    x = -placement.centerX * zoom,
    y = -placement.centerY * zoom,
)

private fun FocusWallDensity.labelResource(): Int = when (this) {
    FocusWallDensity.Small -> R.string.density_small
    FocusWallDensity.Medium -> R.string.density_medium
    FocusWallDensity.Large -> R.string.density_large
}
