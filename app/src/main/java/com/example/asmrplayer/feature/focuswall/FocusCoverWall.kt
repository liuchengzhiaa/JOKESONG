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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewComfy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val MinimumCanvasZoom = 0.72f
private const val MaximumCanvasZoom = 1.35f
private const val FlingThresholdPxPerSecond = 650f

@Composable
fun FocusCoverWall(
    items: List<LibraryItem>,
    contentKey: String,
    focusedItemId: String?,
    playingItemId: String?,
    onFocusedItemChange: (String?) -> Unit,
    onPlayingItemChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    onActivate: (LibraryItem) -> Unit = {},
    onPlay: (LibraryItem) -> Unit = {},
) {
    var densityOrdinal by rememberSaveable(contentKey) {
        mutableIntStateOf(FocusWallDensity.Medium.ordinal)
    }
    var canvasZoom by rememberSaveable(contentKey) { mutableFloatStateOf(1f) }
    val wallDensity = FocusWallDensity.entries[densityOrdinal]

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { onFocusedItemChange(null) },
                leadingIcon = { Icon(Icons.Default.ViewComfy, null, Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.focus_wall)) },
            )
            AssistChip(
                onClick = { densityOrdinal = wallDensity.next().ordinal },
                label = {
                    Text(
                        stringResource(
                            R.string.wall_density_value,
                            stringResource(wallDensity.labelResource()),
                        ),
                    )
                },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.zoom_percent, (canvasZoom * 100).roundToInt()),
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
            playingItemId = playingItemId,
            wallDensity = wallDensity,
            canvasZoom = canvasZoom,
            onCanvasZoomChange = { canvasZoom = it.coerceIn(MinimumCanvasZoom, MaximumCanvasZoom) },
            onFocusedItemChange = onFocusedItemChange,
            onCardClick = { item ->
                when (resolveCardTap(focusedItemId, item.id)) {
                    is FocusWallIntent.Focus -> onFocusedItemChange(item.id)
                    is FocusWallIntent.Activate -> when (item.type) {
                        LibraryItemType.Folder -> onActivate(item)
                        LibraryItemType.Audio -> {
                            if (playingItemId == item.id) {
                                onActivate(item)
                            } else {
                                onPlayingItemChange(item.id)
                                onPlay(item)
                            }
                        }
                    }
                }
            },
            onPlayPause = { item ->
                if (item.type == LibraryItemType.Folder) {
                    onPlay(item)
                } else if (playingItemId == item.id) {
                    onPlayingItemChange(null)
                } else {
                    onPlayingItemChange(item.id)
                    onPlay(item)
                }
            },
            onEnterFolder = onActivate,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun FocusWallCanvas(
    items: List<LibraryItem>,
    contentKey: String,
    focusedItemId: String?,
    playingItemId: String?,
    wallDensity: FocusWallDensity,
    canvasZoom: Float,
    onCanvasZoomChange: (Float) -> Unit,
    onFocusedItemChange: (String?) -> Unit,
    onCardClick: (LibraryItem) -> Unit,
    onPlayPause: (LibraryItem) -> Unit,
    onEnterFolder: (LibraryItem) -> Unit,
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
    var panOffset by remember(contentKey) { mutableStateOf(Offset.Zero) }
    var activePanAnimation by remember(contentKey) { mutableStateOf<Job?>(null) }
    val currentZoom by rememberUpdatedState(canvasZoom)
    val currentFocusedItemId by rememberUpdatedState(focusedItemId)

    fun animatePanTo(target: Offset, durationMillis: Int) {
        activePanAnimation?.cancel()
        activePanAnimation = scope.launch {
            Animatable(panOffset, Offset.VectorConverter).animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = durationMillis),
            ) {
                panOffset = value
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
            wallDensity,
        ) {
            FocusWallLayoutEngine.layout(
                items = layoutItems,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                orientation = orientation,
                density = wallDensity,
            )
        }
        val placementById = remember(placements) { placements.associateBy(FocusWallPlacement::itemId) }
        val itemById = remember(items) { items.associateBy(LibraryItem::id) }

        LaunchedEffect(focusedItemId, placements) {
            val focused = focusedItemId?.let(placementById::get) ?: return@LaunchedEffect
            animatePanTo(centeredPan(focused, currentZoom), durationMillis = 280)
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
                                val oldZoom = currentZoom
                                val newZoom = (oldZoom * zoomChange)
                                    .coerceIn(MinimumCanvasZoom, MaximumCanvasZoom)
                                val ratio = newZoom / oldZoom
                                val centroidFromCenter = if (centroid.isSpecified) {
                                    centroid - Offset(size.width / 2f, size.height / 2f)
                                } else {
                                    Offset.Zero
                                }
                                panOffset = panOffset * ratio +
                                    centroidFromCenter * (1f - ratio) + panChange
                                onCanvasZoomChange(newZoom)
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (pastTouchSlop && placements.isNotEmpty()) {
                            val velocity = velocityTracker.calculateVelocity()
                            val nearest = nearestPlacementToCenter(
                                placements = placements,
                                pan = panOffset,
                                zoom = currentZoom,
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
                                        target = centeredPan(target, currentZoom),
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

            val zoom = canvasZoom
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
                val isPlaying = placement.itemId == playingItemId
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
                        isPlaying = isPlaying,
                        onClick = { onCardClick(item) },
                        onPlayPause = { onPlayPause(item) },
                        onEnterFolder = { onEnterFolder(item) },
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
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onEnterFolder: () -> Unit,
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

            if (isPlaying) {
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

                if (isFocused) {
                    if (isPlaying && item.type == LibraryItemType.Audio) {
                        Spacer(Modifier.height(9.dp))
                        LinearProgressIndicator(
                            progress = { 0.02f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color(0x55FFFFFF),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilledIconButton(onClick = onPlayPause) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.pause else R.string.play,
                                ),
                            )
                        }
                        if (item.type == LibraryItemType.Audio) {
                            IconButton(onClick = {}) {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = stringResource(R.string.add_to_queue),
                                    tint = Color.White,
                                )
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                            FilledTonalButton(onClick = onEnterFolder) {
                                Text(stringResource(R.string.enter_folder))
                                Spacer(Modifier.width(5.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryItem.detailLine(): String = when (type) {
    LibraryItemType.Folder -> stringResource(
        R.string.folder_contents,
        childFolderCount,
        audioCount,
    )
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

private fun artworkBrush(seed: Int): Brush {
    val palettes = listOf(
        listOf(Color(0xFF315C72), Color(0xFF101927)),
        listOf(Color(0xFF74586A), Color(0xFF211420)),
        listOf(Color(0xFF476458), Color(0xFF101B17)),
        listOf(Color(0xFF73584B), Color(0xFF251711)),
        listOf(Color(0xFF4E557C), Color(0xFF121526)),
    )
    val colors = palettes[(seed - 1).mod(palettes.size)]
    return Brush.linearGradient(colors)
}
