package com.example.asmrplayer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import coil3.compose.AsyncImage
import com.example.asmrplayer.R
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.ui.theme.artworkAccent
import com.example.asmrplayer.ui.theme.artworkBrush
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class GlobalVinylPlacement(
    val xPerMille: Int,
    val yPerMille: Int,
)

internal fun globalVinylCoordinate(perMille: Int, availablePx: Float): Float =
    availablePx.coerceAtLeast(0f) * perMille.coerceIn(0, 1_000) / 1_000f

internal fun settleGlobalVinylPlacement(
    xPx: Float,
    yPx: Float,
    availableWidthPx: Float,
    availableHeightPx: Float,
): GlobalVinylPlacement {
    val safeWidth = availableWidthPx.coerceAtLeast(0f)
    val safeHeight = availableHeightPx.coerceAtLeast(0f)
    val snappedX = if (xPx <= safeWidth / 2f) 0 else 1_000
    val normalizedY = if (safeHeight == 0f) {
        0
    } else {
        (yPx.coerceIn(0f, safeHeight) / safeHeight * 1_000f).roundToInt()
    }
    return GlobalVinylPlacement(snappedX, normalizedY.coerceIn(0, 1_000))
}

/**
 * The single global now-playing affordance.
 *
 * It intentionally owns no playback state and no route state. The app shell gives it
 * the current presentation item and decides where a click navigates. That keeps this
 * small visual reusable if the navigation implementation changes later.
 */
@Composable
fun GlobalVinylPlayer(
    item: LibraryItem,
    isPlaying: Boolean,
    motionPercent: Int,
    xPerMille: Int,
    yPerMille: Int,
    onPositionSettled: (Int, Int) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val angle = remember(item.id) { Animatable(0f) }
    LaunchedEffect(item.id, isPlaying, motionPercent) {
        if (!isPlaying || motionPercent <= 0) return@LaunchedEffect
        val turnMillis = (26_000f / (motionPercent.coerceIn(20, 100) / 100f)).toInt()
        while (isActive) {
            angle.animateTo(
                targetValue = angle.value + 360f,
                animationSpec = tween(turnMillis, easing = LinearEasing),
            )
            angle.snapTo(angle.value % 360f)
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(if (motionPercent <= 0) 0 else 120),
        label = "global vinyl press",
    )
    val accent = artworkAccent(item.paletteSeed)
    val description = stringResource(R.string.open_player)

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val recordSizePx = with(density) { 68.dp.toPx() }
        val availableWidth = (constraints.maxWidth - recordSizePx).coerceAtLeast(0f)
        val availableHeight = (constraints.maxHeight - recordSizePx).coerceAtLeast(0f)
        var position by remember(availableWidth, availableHeight) {
            mutableStateOf(
                Offset(
                    globalVinylCoordinate(xPerMille, availableWidth),
                    globalVinylCoordinate(yPerMille, availableHeight),
                ),
            )
        }
        var dragging by remember { mutableStateOf(false) }
        var settleJob by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(xPerMille, yPerMille, availableWidth, availableHeight) {
            if (!dragging && settleJob?.isActive != true) {
                position = Offset(
                    globalVinylCoordinate(xPerMille, availableWidth),
                    globalVinylCoordinate(yPerMille, availableHeight),
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .size(68.dp)
                .pointerInput(availableWidth, availableHeight, motionPercent) {
                    detectDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            dragging = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            position = Offset(
                                x = (position.x + dragAmount.x).coerceIn(0f, availableWidth),
                                y = (position.y + dragAmount.y).coerceIn(0f, availableHeight),
                            )
                        },
                        onDragCancel = {
                            dragging = false
                            position = Offset(
                                globalVinylCoordinate(xPerMille, availableWidth),
                                globalVinylCoordinate(yPerMille, availableHeight),
                            )
                        },
                        onDragEnd = {
                            val settled = settleGlobalVinylPlacement(
                                xPx = position.x,
                                yPx = position.y,
                                availableWidthPx = availableWidth,
                                availableHeightPx = availableHeight,
                            )
                            val targetX = globalVinylCoordinate(
                                settled.xPerMille,
                                availableWidth,
                            )
                            settleJob = scope.launch {
                                if (motionPercent <= 0) {
                                    position = position.copy(x = targetX)
                                } else {
                                    animate(
                                        initialValue = position.x,
                                        targetValue = targetX,
                                        animationSpec = tween(180),
                                    ) { value, _ -> position = position.copy(x = value) }
                                }
                                dragging = false
                                onPositionSettled(settled.xPerMille, settled.yPerMille)
                            }
                        },
                    )
                }
                .scale(pressScale)
                .shadow(14.dp, CircleShape)
                .clip(CircleShape)
                .semantics { contentDescription = description }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Reading the animation in the layer block updates only this render
                    // layer instead of recomposing the record on every frame.
                    .graphicsLayer { rotationZ = angle.value }
                    .clip(CircleShape),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(brush = artworkBrush(item.paletteSeed))
                }
                if (item.artworkUri != null) {
                    AsyncImage(
                        model = item.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Canvas(Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    listOf(0.42f, 0.58f, 0.74f, 0.88f).forEachIndexed { index, fraction ->
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.12f + index * 0.025f),
                            radius = radius * fraction,
                            center = center,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                    drawCircle(Color.Black.copy(alpha = 0.58f), radius * 0.15f, center)
                    drawCircle(accent.copy(alpha = 0.94f), radius * 0.07f, center)
                    drawCircle(
                        Color.White.copy(alpha = 0.48f),
                        radius * 0.025f,
                        Offset(center.x, center.y),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.42f),
                        radius = radius - 0.75.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
        }
    }
}
