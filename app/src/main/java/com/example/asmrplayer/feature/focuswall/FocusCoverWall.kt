package com.example.asmrplayer.feature.focuswall

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.asmrplayer.R
import com.example.asmrplayer.core.media.PlaybackState
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import com.example.asmrplayer.feature.folderSummaryLine
import com.example.asmrplayer.feature.player.formatPlaybackTime
import com.example.asmrplayer.ui.theme.artworkBrush
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val FlingThresholdPxPerSecond = 650f

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
    onActivate: (LibraryItem) -> Unit = {},
    onPlay: (LibraryItem) -> Unit = {},
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

        FocusWallCanvas(
            items = items,
            contentKey = contentKey,
            focusedItemId = focusedItemId,
            playbackState = playbackState,
            viewportState = viewportState,
            onFocusedItemChange = onFocusedItemChange,
            onSeekTo = onSeekTo,
            onCardClick = { item ->
                when (resolveCardTap(focusedItemId, item.id)) {
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
    }
}

@Composable
private fun FocusWallCanvas(
    items: List<LibraryItem>,
    contentKey: String,
    focusedItemId: String?,
    playbackState: PlaybackState,
    viewportState: FocusWallViewportState,
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
    val currentFocusedItemId by rememberUpdatedState(focusedItemId)

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

    BoxWithConstraints(modifier = modifier.clip(RoundedCornerShape(0.dp))) {
        val viewportWidth = with(localDensity) { maxWidth.toPx() }
        val viewportHeight = with(localDensity) { maxHeight.toPx() }
        val layoutItems = remember(items) {
            items.map { FocusWallLayoutItem(it.id, it.paletteSeed) }
        }
        val placements = remember(
            layoutItems,
            viewportWidth,
            viewportHeight,
            orientation,
            viewportState.densityOrdinal,
        ) {
            FocusWallLayoutEngine.layout(
                items = layoutItems,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                orientation = orientation,
                density = viewportState.density,
            )
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
                                viewportState.pan = viewportState.pan * ratio +
                                    centroidFromCenter * (1f - ratio) + panChange
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (pastTouchSlop && placements.isNotEmpty()) {
                            val velocity = velocityTracker.calculateVelocity()
                            val nearest = nearestPlacementToCenter(
                                placements = placements,
                                pan = viewportState.pan,
                                zoom = viewportState.zoom,
                            )
                            val target = if (
                                Offset(velocity.x, velocity.y).getDistance() >= FlingThresholdPxPerSecond
                            ) {
                                directionalNeighbor(
                                    placements = placements,
                                    current = nearest,
                                    contentVelocity = Offset(velocity.x, velocity.y),
                                    orientation = orientation,
                                ) ?: nearest
                            } else {
                                nearest
                            }

                            if (target != null) {
                                if (target.itemId != currentFocusedItemId) {
                                    onFocusedItemChange(target.itemId)
                                } else {
                                    animatePanTo(
                                        target = centeredPan(target, viewportState.zoom),
                                        durationMillis = 230,
                                    )
                                }
                            }
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
                val focusBoost = if (placement.itemId == focusedItemId) 1.18f else 1f
                val visualScale = depthScale * focusBoost
                val halfWidth = placement.width * zoom * visualScale / 2f
                val halfHeight = placement.height * zoom * visualScale / 2f
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
                val visualScale = depthScale * if (isFocused) 1.18f else 1f
                val visualAlpha = when {
                    isFocused -> 1f
                    focusedItemId != null -> (0.48f + depthScale * 0.34f).coerceAtMost(0.78f)
                    else -> 0.58f + depthScale * 0.42f
                }
                val placeable = subcompose(placement.itemId) {
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
                                scaleX = zoom * visualScale
                                scaleY = zoom * visualScale
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
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "focusBorder",
    )
    val borderWidth by animateFloatAsState(if (isFocused) 2.4f else 0f, label = "focusBorderWidth")
    val shape = RoundedCornerShape(if (isFocused) 12.dp else 8.dp)

    Surface(
        modifier = modifier
            .border(borderWidth.dp, borderColor, shape)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        tonalElevation = if (isFocused) 8.dp else 1.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(artworkBrush(item.paletteSeed)),
        ) {
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
                    .padding(12.dp)
                    .size(if (isFocused) 34.dp else 30.dp)
                    .background(Color(0xB814171D), CircleShape)
                    .padding(7.dp),
                tint = Color.White,
            )

            if (isCurrent) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color(0xD014171D), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        stringResource(R.string.now_playing),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(if (isFocused) 18.dp else 14.dp),
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = if (isFocused) 24.sp else 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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

private fun nearestPlacementToCenter(
    placements: List<FocusWallPlacement>,
    pan: Offset,
    zoom: Float,
): FocusWallPlacement? = placements.minByOrNull { placement ->
    val x = placement.centerX * zoom + pan.x
    val y = placement.centerY * zoom + pan.y
    x * x + y * y
}

private fun directionalNeighbor(
    placements: List<FocusWallPlacement>,
    current: FocusWallPlacement?,
    contentVelocity: Offset,
    orientation: FocusWallOrientation,
): FocusWallPlacement? {
    current ?: return null

    val direction = Offset(-contentVelocity.x, -contentVelocity.y)
    val directionLength = direction.getDistance()
    if (directionLength == 0f) return current
    return placements.asSequence()
        .filter { it.itemId != current.itemId }
        .mapNotNull { candidate ->
            val delta = Offset(
                candidate.centerX - current.centerX,
                candidate.centerY - current.centerY,
            )
            val distance = delta.getDistance()
            if (distance == 0f) return@mapNotNull null
            val portraitVerticalBias = if (orientation == FocusWallOrientation.Portrait) 0.82f else 1f
            val weightedDelta = Offset(delta.x * portraitVerticalBias, delta.y)
            val weightedDistance = weightedDelta.getDistance()
            val cosine = (weightedDelta.x * direction.x + weightedDelta.y * direction.y) /
                (weightedDistance * directionLength)
            if (cosine < 0.35f) null else candidate to (distance / (cosine * cosine))
        }
        .minByOrNull { it.second }
        ?.first
}

private fun FocusWallDensity.labelResource(): Int = when (this) {
    FocusWallDensity.Small -> R.string.density_small
    FocusWallDensity.Medium -> R.string.density_medium
    FocusWallDensity.Large -> R.string.density_large
}
