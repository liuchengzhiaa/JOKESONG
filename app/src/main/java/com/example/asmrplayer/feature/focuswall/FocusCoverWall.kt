package com.example.asmrplayer.feature.focuswall

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import com.example.asmrplayer.R
import com.example.asmrplayer.core.artwork.CoverAccentLoader
import com.example.asmrplayer.core.artwork.CoverFacts
import com.example.asmrplayer.core.media.PlaybackState
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import com.example.asmrplayer.core.settings.ToolClusterStyle
import com.example.asmrplayer.core.settings.levelFraction
import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.feature.folderSummaryLine
import com.example.asmrplayer.feature.library.LibraryLayoutMode
import com.example.asmrplayer.feature.library.LibraryLayoutOptionsPanel
import com.example.asmrplayer.feature.library.icon
import com.example.asmrplayer.feature.player.formatPlaybackTime
import com.example.asmrplayer.feature.subtitles.CoverLyricsFrame
import com.example.asmrplayer.feature.subtitles.LyricsWordGlowMode
import com.example.asmrplayer.feature.subtitles.SubtitleDetailState
import com.example.asmrplayer.feature.subtitles.coverLyricsFrame
import com.example.asmrplayer.feature.subtitles.coverLyricsGlowTimeline
import com.example.asmrplayer.feature.subtitles.coverLyricsShadeAlpha
import com.example.asmrplayer.ui.FoliaSeekBar
import com.example.asmrplayer.ui.GlowingLyricText
import com.example.asmrplayer.ui.components.GlassOptionsPanel
import com.example.asmrplayer.ui.components.GlassTool
import com.example.asmrplayer.ui.components.GlassToolCluster
import com.example.asmrplayer.ui.components.GlassToolsPanel
import com.example.asmrplayer.ui.theme.artworkAccent
import com.example.asmrplayer.ui.theme.artworkBrush
import kotlin.math.abs
import kotlin.math.max
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

/**
 * Enough off-screen wall to cover a fast frame without composing almost three
 * whole viewports. These are presentation buffers only; visible cards are still
 * tested against their real scaled bounds below.
 */
private const val VerticalPrefetchViewportFraction = 0.50f
private const val HorizontalPrefetchViewportFraction = 0.25f

/** Non-current cards must not observe the Media3 position ticker. */
private val InactiveWallPlaybackState = PlaybackState()

private data class VisibleWallCard(
    val placement: FocusWallPlacement,
    val screenCenterX: Float,
    val screenCenterY: Float,
    val depthScale: Float,
)

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
    subtitleState: SubtitleDetailState,
    wordGlowMode: LyricsWordGlowMode,
    viewportState: FocusWallViewportState,
    onFocusedItemChange: (String?) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenLyrics: (LibraryItem) -> Unit,
    layoutMode: LibraryLayoutMode,
    onLayoutModeChange: (LibraryLayoutMode) -> Unit,
    onDensityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: FocusWallStyle = FocusWallStyle.Bento,
    /**
     * Folia only. The Bento wall's focused block is a hand-written template, so it
     * has no shape or size to choose; both callbacks are still passed by every wall
     * so the shared tools panel needs no special case.
     */
    onFocusShapeChange: (Int) -> Unit = {},
    onFocusSizeChange: (Int) -> Unit = {},
    onAmbientStrengthChange: (Int) -> Unit = {},
    lightsOut: Boolean = false,
    autoFocusPlaying: Boolean = false,
    posterTintPercent: Int = 0,
    motionPercent: Int = 100,
    onLightsOutChange: (Boolean) -> Unit = {},
    onAutoFocusPlayingChange: (Boolean) -> Unit = {},
    onPosterTintStrengthChange: (Int) -> Unit = {},
    onMotionPercentChange: (Int) -> Unit = {},
    onActivate: (LibraryItem) -> Unit = {},
    onPlay: (LibraryItem) -> Unit = {},
    /**
     * Brings the wall's focus to the item that is playing, following the queue back
     * to its folder when it is not on this wall. Null when that cannot be done, and
     * the entry is then shown disabled.
     */
    onRevealPlayingItem: (() -> Unit)? = null,
    /** Presentation of the wall's corner tool cluster, shared with the player's. */
    toolClusterStyle: ToolClusterStyle = ToolClusterStyle.Default,
) {
    // The state holder is stable while its value advances. Only the one focused,
    // current poster reads it inside its own subcomposition; 30 ordinary posters
    // no longer recompose every time Media3 publishes another position tick.
    val playbackStateProvider = rememberUpdatedState(playbackState)
    val currentMediaId = playbackState.mediaId
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            FocusWallCanvas(
                items = items,
                contentKey = contentKey,
                focusedItemId = focusedItemId,
                playbackStateProvider = playbackStateProvider,
                currentMediaId = currentMediaId,
                currentIsPlaying = playbackState.isPlaying,
                currentHasPrevious = playbackState.hasPrevious,
                currentHasNext = playbackState.hasNext,
                subtitleState = subtitleState,
                wordGlowMode = wordGlowMode,
                viewportState = viewportState,
                style = style,
                lightsOut = lightsOut,
                posterTintPercent = posterTintPercent,
                motionPercent = motionPercent,
                onFocusedItemChange = onFocusedItemChange,
                onSeekTo = onSeekTo,
                onPrevious = onPrevious,
                onNext = onNext,
                onOpenLyrics = onOpenLyrics,
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
                                if (item.id == currentMediaId) {
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
                        item.id == currentMediaId -> onTogglePlayPause()
                        else -> onPlay(item)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            WallToolsOverlay(
                contentKey = contentKey,
                layoutMode = layoutMode,
                onLayoutModeChange = onLayoutModeChange,
                style = style,
                viewportState = viewportState,
                onRevealPlayingItem = onRevealPlayingItem,
                onDensityChange = onDensityChange,
                onFocusShapeChange = onFocusShapeChange,
                onFocusSizeChange = onFocusSizeChange,
                onAmbientStrengthChange = onAmbientStrengthChange,
                lightsOut = lightsOut,
                autoFocusPlaying = autoFocusPlaying,
                posterTintPercent = posterTintPercent,
                motionPercent = motionPercent,
                onLightsOutChange = onLightsOutChange,
                onAutoFocusPlayingChange = onAutoFocusPlayingChange,
                onPosterTintStrengthChange = onPosterTintStrengthChange,
                onMotionPercentChange = onMotionPercentChange,
                toolClusterStyle = toolClusterStyle,
            )
        }
    }
}

/**
 * Ranges of the two presentation settings, shared by the crest on their row and the
 * slider on their page so the two can never disagree about where the value sits.
 */
private val PosterTintPercentRange = 0..60
private val MotionPercentRange = 0..100

/** Which page of the tools panel is showing. */
private sealed interface WallPanelPage {
    data object Root : WallPanelPage
    data object Layout : WallPanelPage
    data object Density : WallPanelPage
    data object Zoom : WallPanelPage
    data object AmbientStrength : WallPanelPage
    data object Presentation : WallPanelPage
    data object PosterTint : WallPanelPage
    data object Motion : WallPanelPage
    data object FocusShape : WallPanelPage
    data object FocusSize : WallPanelPage
}

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
    layoutMode: LibraryLayoutMode,
    onLayoutModeChange: (LibraryLayoutMode) -> Unit,
    style: FocusWallStyle,
    viewportState: FocusWallViewportState,
    onRevealPlayingItem: (() -> Unit)?,
    onDensityChange: (Int) -> Unit,
    onFocusShapeChange: (Int) -> Unit,
    onFocusSizeChange: (Int) -> Unit,
    onAmbientStrengthChange: (Int) -> Unit,
    lightsOut: Boolean,
    autoFocusPlaying: Boolean,
    posterTintPercent: Int,
    motionPercent: Int,
    onLightsOutChange: (Boolean) -> Unit,
    onAutoFocusPlayingChange: (Boolean) -> Unit,
    onPosterTintStrengthChange: (Int) -> Unit,
    onMotionPercentChange: (Int) -> Unit,
    toolClusterStyle: ToolClusterStyle,
    modifier: Modifier = Modifier,
) {
    // Keyed on the content, so changing folder or wall style closes the panel and
    // returns it to its first page.
    var expanded by remember(contentKey) { mutableStateOf(false) }
    var page by remember(contentKey) { mutableStateOf<WallPanelPage>(WallPanelPage.Root) }
    BackHandler(enabled = expanded) {
        if (page != WallPanelPage.Root) page = WallPanelPage.Root else expanded = false
    }

    // The canvas zoom is stored as a fraction but read as a percentage, like the two
    // settings either side of it in the root panel, so every crest level is drawn
    // from the same kind of number.
    val zoomPercent = (viewportState.zoom * 100).roundToInt()
    val minimumZoomPercent = (MinimumCanvasZoom * 100).roundToInt()
    val maximumZoomPercent = (MaximumCanvasZoom * 100).roundToInt()

    val panelMotionMillis = wallMotionDuration(190, motionPercent)

    // Collapsing also returns the panel to its first page, so reopening never lands on
    // the page the last visit left behind.
    GlassToolCluster(
        expanded = expanded,
        onExpandedChange = { open ->
            expanded = open
            if (!open) page = WallPanelPage.Root
        },
        panelMotionMillis = panelMotionMillis,
        contentDescription = stringResource(R.string.wall_tools),
        style = toolClusterStyle,
        modifier = modifier,
    ) {
        when (page) {
            WallPanelPage.Root -> GlassToolsPanel(
                style = toolClusterStyle,
                tools = listOf(
                    GlassTool(
                        icon = layoutMode.icon(),
                        labelRes = R.string.layout,
                        valueRes = layoutMode.labelRes,
                        onClick = { page = WallPanelPage.Layout },
                    ),
                    GlassTool(
                        icon = Icons.Default.MyLocation,
                        labelRes = R.string.focus_playing_item,
                        enabled = onRevealPlayingItem != null,
                        onClick = {
                            expanded = false
                            onRevealPlayingItem?.invoke()
                        },
                    ),
                    GlassTool(
                        icon = Icons.Default.Widgets,
                        labelRes = R.string.wall_density,
                        enabled = true,
                        onClick = { page = WallPanelPage.Density },
                        levelFraction = levelFraction(
                            viewportState.densityPercent,
                            MinimumWallDensityPercent..MaximumWallDensityPercent,
                        ),
                    ),
                    GlassTool(
                        icon = Icons.Default.FitScreen,
                        labelRes = R.string.wall_zoom,
                        enabled = true,
                        onClick = { page = WallPanelPage.Zoom },
                        levelFraction = levelFraction(
                            zoomPercent,
                            minimumZoomPercent..maximumZoomPercent,
                        ),
                    ),
                ) + if (style == FocusWallStyle.Folia) {
                    listOf(
                        GlassTool(
                            icon = Icons.Default.GraphicEq,
                            labelRes = R.string.wall_ambient_strength,
                            enabled = true,
                            onClick = { page = WallPanelPage.AmbientStrength },
                            levelFraction = levelFraction(
                                viewportState.ambientPercent,
                                0..MaximumAmbientPercent,
                            ),
                        ),
                        GlassTool(
                            icon = Icons.Default.Tune,
                            labelRes = R.string.wall_presentation,
                            enabled = true,
                            onClick = { page = WallPanelPage.Presentation },
                        ),
                        GlassTool(
                            icon = Icons.Default.CropSquare,
                            labelRes = R.string.wall_focus_shape,
                            enabled = true,
                            onClick = { page = WallPanelPage.FocusShape },
                            valueRes = viewportState.focusShape.labelResource(),
                        ),
                        GlassTool(
                            icon = Icons.Default.AspectRatio,
                            labelRes = R.string.wall_focus_size,
                            // The silhouettes are drawn at one size: there is
                            // no second size for a heart or a circle to grow
                            // into, so the row shows the setting greyed out
                            // rather than opening a page that cannot change
                            // anything.
                            enabled = !viewportState.focusShape.isSilhouette,
                            onClick = { page = WallPanelPage.FocusSize },
                            valueRes = viewportState.focusSize.labelResource(),
                        ),
                    )
                } else {
                    emptyList()
                },
            )
            WallPanelPage.Layout -> LibraryLayoutOptionsPanel(
                selected = layoutMode,
                onSelect = {
                    expanded = false
                    page = WallPanelPage.Root
                    if (it != layoutMode) onLayoutModeChange(it)
                },
                onBack = { page = WallPanelPage.Root },
                style = toolClusterStyle,
            )
            WallPanelPage.Density -> WallPercentPanel(
                titleRes = R.string.wall_density,
                valuePercent = viewportState.densityPercent,
                valueRange = MinimumWallDensityPercent..MaximumWallDensityPercent,
                onValuePreview = {},
                onValueCommit = { percent ->
                    viewportState.densityPercent = percent
                    onDensityChange(percent)
                },
                onBack = { page = WallPanelPage.Root },
                onReset = {
                    viewportState.densityPercent = DefaultWallPercent
                    onDensityChange(DefaultWallPercent)
                },
            )
            WallPanelPage.Zoom -> WallZoomPanel(
                zoom = viewportState.zoom,
                onZoomChange = { requestedZoom ->
                    val oldZoom = viewportState.zoom
                    val newZoom = requestedZoom.coerceIn(
                        MinimumCanvasZoom,
                        MaximumCanvasZoom,
                    )
                    // A menu-driven zoom uses the viewport centre as its
                    // anchor, matching a centred pinch and keeping the same
                    // world point under the centre of the screen.
                    viewportState.pan *= newZoom / oldZoom
                    viewportState.zoom = newZoom
                },
                onBack = { page = WallPanelPage.Root },
            )
            WallPanelPage.AmbientStrength -> WallPercentPanel(
                titleRes = R.string.wall_ambient_strength,
                valuePercent = viewportState.ambientPercent,
                valueRange = 0..MaximumAmbientPercent,
                onValuePreview = { viewportState.ambientPercent = it },
                onValueCommit = { onAmbientStrengthChange(it) },
                onBack = { page = WallPanelPage.Root },
                onReset = {
                    viewportState.ambientPercent = DefaultWallPercent
                    onAmbientStrengthChange(DefaultWallPercent)
                },
            )
            WallPanelPage.Presentation -> GlassOptionsPanel(
                style = toolClusterStyle,
                titleRes = R.string.wall_presentation,
                onBack = { page = WallPanelPage.Root },
                options = listOf(
                    GlassTool(
                        icon = Icons.Default.LightMode,
                        labelRes = R.string.wall_lights_out,
                        enabled = true,
                        valueRes = if (lightsOut) R.string.setting_on else R.string.setting_off,
                        onClick = { onLightsOutChange(!lightsOut) },
                    ),
                    GlassTool(
                        icon = Icons.Default.CenterFocusStrong,
                        labelRes = R.string.wall_auto_focus,
                        enabled = true,
                        valueRes = if (autoFocusPlaying) {
                            R.string.setting_on
                        } else {
                            R.string.setting_off
                        },
                        onClick = { onAutoFocusPlayingChange(!autoFocusPlaying) },
                    ),
                    GlassTool(
                        icon = Icons.Default.ColorLens,
                        labelRes = R.string.wall_poster_tint,
                        enabled = true,
                        levelFraction = levelFraction(
                            posterTintPercent,
                            PosterTintPercentRange,
                        ),
                        onClick = { page = WallPanelPage.PosterTint },
                    ),
                    GlassTool(
                        icon = Icons.Default.MotionPhotosOff,
                        labelRes = R.string.wall_reduced_motion,
                        enabled = true,
                        levelFraction = levelFraction(motionPercent, MotionPercentRange),
                        onClick = { page = WallPanelPage.Motion },
                    ),
                ),
            )
            WallPanelPage.PosterTint -> WallPercentPanel(
                titleRes = R.string.wall_poster_tint,
                valuePercent = posterTintPercent,
                // No reset row: this one's default is zero, and the slider's
                // left end already is it.
                valueRange = PosterTintPercentRange,
                onValuePreview = {},
                onValueCommit = onPosterTintStrengthChange,
                onBack = { page = WallPanelPage.Presentation },
            )
            WallPanelPage.Motion -> WallPercentPanel(
                titleRes = R.string.wall_motion_strength,
                valuePercent = motionPercent,
                valueRange = MotionPercentRange,
                onValuePreview = {},
                onValueCommit = onMotionPercentChange,
                onBack = { page = WallPanelPage.Presentation },
                onReset = { onMotionPercentChange(DefaultWallPercent) },
            )
            WallPanelPage.FocusShape -> GlassOptionsPanel(
                style = toolClusterStyle,
                titleRes = R.string.wall_focus_shape,
                onBack = { page = WallPanelPage.Root },
                options = FoliaFocusShape.entries.map { shape ->
                    GlassTool(
                        icon = shape.icon(),
                        labelRes = shape.labelResource(),
                        enabled = true,
                        selected = shape == viewportState.focusShape,
                        onClick = {
                            viewportState.focusShapeOrdinal = shape.ordinal
                            onFocusShapeChange(shape.ordinal)
                            page = WallPanelPage.Root
                        },
                    )
                },
            )
            WallPanelPage.FocusSize -> GlassOptionsPanel(
                style = toolClusterStyle,
                titleRes = R.string.wall_focus_size,
                onBack = { page = WallPanelPage.Root },
                options = FoliaFocusSize.entries.map { size ->
                    GlassTool(
                        icon = Icons.Default.AspectRatio,
                        labelRes = size.labelResource(),
                        enabled = true,
                        selected = size == viewportState.focusSize,
                        onClick = {
                            viewportState.focusSizeOrdinal = size.ordinal
                            onFocusSizeChange(size.ordinal)
                            page = WallPanelPage.Root
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun WallZoomPanel(
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onBack: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .padding(vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.wall_zoom),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.wall_zoom),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.zoom_percent, (zoom * 100).roundToInt()),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Slider(
                value = zoom,
                onValueChange = { value ->
                    onZoomChange(value.coerceIn(MinimumCanvasZoom, MaximumCanvasZoom))
                },
                valueRange = MinimumCanvasZoom..MaximumCanvasZoom,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier
                    .clickable { onZoomChange(1f) }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FitScreen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.wall_reset_percent),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * Continuous percentage control. Density intentionally previews only the number
 * and commits the expensive reflow on release; light can opt into live preview.
 */
@Composable
private fun WallPercentPanel(
    titleRes: Int,
    valuePercent: Int,
    valueRange: IntRange,
    onValuePreview: (Int) -> Unit,
    onValueCommit: (Int) -> Unit,
    onBack: () -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var pendingPercent by remember(valuePercent) { mutableIntStateOf(valuePercent) }
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .padding(vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(titleRes),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.percent_value, pendingPercent),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Slider(
                value = pendingPercent.toFloat(),
                onValueChange = { value ->
                    pendingPercent = value.roundToInt().coerceIn(valueRange)
                    onValuePreview(pendingPercent)
                },
                onValueChangeFinished = { onValueCommit(pendingPercent) },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (onReset != null) {
                Row(
                    modifier = Modifier
                        .clickable(onClick = onReset)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.wall_reset_percent),
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
    playbackStateProvider: State<PlaybackState>,
    currentMediaId: String?,
    currentIsPlaying: Boolean,
    currentHasPrevious: Boolean,
    currentHasNext: Boolean,
    subtitleState: SubtitleDetailState,
    wordGlowMode: LyricsWordGlowMode,
    viewportState: FocusWallViewportState,
    style: FocusWallStyle,
    lightsOut: Boolean,
    posterTintPercent: Int,
    motionPercent: Int,
    onFocusedItemChange: (String?) -> Unit,
    onCardClick: (LibraryItem) -> Unit,
    onPlayPause: (LibraryItem) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenLyrics: (LibraryItem) -> Unit,
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

    LaunchedEffect(motionPercent) {
        if (motionPercent <= 0) activePanAnimation?.cancel()
    }

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
        if (motionPercent <= 0 || velocity.getDistance() < 1f) return
        var coasting: Job? = null
        coasting = scope.launch {
            Animatable(viewportState.pan, Offset.VectorConverter).animateDecay(
                initialVelocity = velocity * (motionPercent.coerceIn(0, 100) / 100f),
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
        // Read here, in a composition scope, so a pinch re-decodes artwork at the new
        // bucket. The cards are placed by a layout pass, not by a recomposition, so a
        // zoom read inside the canvas would never reach them.
        val artworkScale = artworkDecodeScale(viewportState.zoom)
        val layoutItems = remember(items) {
            items.map { FocusWallLayoutItem(it.id, it.paletteSeed) }
        }
        // The focused cover is read once, here, rather than inside its card: its
        // proportions decide the hero's shape while the block is being laid out, and the
        // accent that comes back with them is what the card is outlined in. Nothing else
        // on the wall pays the decode.
        val context = LocalContext.current.applicationContext
        val focusedArtworkUri = items.firstOrNull { it.id == focusedItemId }?.artworkUri
        val focusedCoverFacts by produceState<CoverFacts?>(null, focusedArtworkUri) {
            value = CoverAccentLoader.resolve(context, focusedArtworkUri)
        }
        // Focus is the wall's visual subject even when another audio keeps playing.
        // Its cover therefore owns the ambient palette. Clearing focus returns the
        // Folia wall to its neutral background instead of retaining a stale colour.
        val focusedItem = items.firstOrNull { it.id == focusedItemId }
        val ambientPalette = remember(
            focusedItemId,
            focusedCoverFacts,
            focusedItem?.paletteSeed,
        ) {
            if (focusedItemId == null) {
                emptyList()
            } else {
                val extracted = focusedCoverFacts?.paletteArgbs.orEmpty().map(::Color)
                if (extracted.isNotEmpty()) {
                    extracted
                } else {
                    focusedItem?.let { item -> fallbackAmbientPalette(artworkAccent(item.paletteSeed)) }
                        .orEmpty()
                }
            }
        }
        val posterTintColor = ambientPalette.firstOrNull()
            ?: MaterialTheme.colorScheme.primary
        val placements = remember(
            layoutItems,
            viewportWidth,
            viewportHeight,
            orientation,
            viewportState.densityPercent,
            viewportState.focusShapeOrdinal,
            viewportState.focusSizeOrdinal,
            focusedItemId,
            focusedCoverFacts,
            style,
            wallGapPx,
        ) {
            when (style) {
                FocusWallStyle.Bento -> FocusWallLayoutEngine.layout(
                    items = layoutItems,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    orientation = orientation,
                    density = FocusWallDensity.Medium,
                    densityScale = viewportState.densityScale,
                    focusedItemId = focusedItemId,
                    gapPx = wallGapPx,
                )
                FocusWallStyle.Folia -> FoliaWallLayoutEngine.layout(
                    items = layoutItems,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    orientation = orientation,
                    density = FocusWallDensity.Medium,
                    densityScale = viewportState.densityScale,
                    focusedItemId = focusedItemId,
                    gapPx = wallGapPx,
                    focusShape = viewportState.focusShape,
                    focusSize = viewportState.focusSize,
                    focusArtworkAspect = focusedCoverFacts?.aspect,
                )
            }
        }
        val placementById = remember(placements) { placements.associateBy(FocusWallPlacement::itemId) }
        val itemById = remember(items) { items.associateBy(LibraryItem::id) }
        val maximumPlacementHalfSize = remember(placements) {
            placements.fold(Size.Zero) { largest, placement ->
                Size(
                    width = max(largest.width, placement.width / 2f),
                    height = max(largest.height, placement.height / 2f),
                )
            }
        }

        LaunchedEffect(focusedItemId, placements, motionPercent) {
            val focused = focusedItemId?.let(placementById::get) ?: return@LaunchedEffect
            if (viewportState.restored) {
                // Returning to a visited folder keeps the viewport it was left
                // with instead of animating over it.
                viewportState.restored = false
                return@LaunchedEffect
            }
            animatePanTo(
                centeredPan(focused, viewportState.zoom),
                durationMillis = wallMotionDuration(280, motionPercent),
            )
        }

        CoverAmbientLayer(
            // The original Bento wall remains visually stable. The cover-driven
            // stage lighting belongs to the Folia presentation mode and can be
            // evaluated independently before it is offered elsewhere.
            palette = if (style == FocusWallStyle.Folia) ambientPalette else emptyList(),
            mode = AmbientLayerMode.Backdrop,
            strengthMultiplier = viewportState.ambientMultiplier,
            motionPercent = motionPercent,
            modifier = Modifier.fillMaxSize(),
        )

        SubcomposeLayout(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(placements, orientation, contentKey, motionPercent) {
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
            val verticalBuffer = constraints.maxHeight * VerticalPrefetchViewportFraction
            val minWorldY = (-constraints.maxHeight / 2f - panOffset.y - verticalBuffer) / zoom
            val maxWorldY = (constraints.maxHeight / 2f - panOffset.y + verticalBuffer) / zoom
            val horizontalBuffer = constraints.maxWidth * HorizontalPrefetchViewportFraction
            val minWorldX = (-constraints.maxWidth / 2f - panOffset.x - horizontalBuffer) / zoom
            val maxWorldX = (constraints.maxWidth / 2f - panOffset.x + horizontalBuffer) / zoom
            val nearViewport = placementsNearWorldRect(
                placements = placements,
                // The index is centre-based. Extend the query by the largest card
                // half-size so a large poster whose edge intersects the buffered
                // viewport can never be discarded before the exact test below.
                minCenterX = minWorldX - maximumPlacementHalfSize.width,
                maxCenterX = maxWorldX + maximumPlacementHalfSize.width,
                minCenterY = minWorldY - maximumPlacementHalfSize.height,
                maxCenterY = maxWorldY + maximumPlacementHalfSize.height,
            )
            // Calculate screen geometry once per card and reuse it for culling,
            // z-order, alpha and placement. The old path repeated the same two
            // coordinate transforms and hypot calculation up to three times per
            // card on every drag frame.
            val visible = ArrayList<VisibleWallCard>(nearViewport.size)
            nearViewport.forEach { placement ->
                val screenCenterX = constraints.maxWidth / 2f + placement.centerX * zoom + panOffset.x
                val screenCenterY = constraints.maxHeight / 2f + placement.centerY * zoom + panOffset.y
                val halfWidth = placement.width * zoom / 2f
                val halfHeight = placement.height * zoom / 2f
                val intersectsBufferedViewport =
                    screenCenterX + halfWidth >= -horizontalBuffer &&
                    screenCenterX - halfWidth <= constraints.maxWidth + horizontalBuffer &&
                    screenCenterY + halfHeight >= -verticalBuffer &&
                    screenCenterY - halfHeight <= constraints.maxHeight + verticalBuffer
                if (intersectsBufferedViewport) {
                    visible += VisibleWallCard(
                        placement = placement,
                        screenCenterX = screenCenterX,
                        screenCenterY = screenCenterY,
                        depthScale = focusWallDepthScale(
                            offsetFromCenterX = screenCenterX - constraints.maxWidth / 2f,
                            offsetFromCenterY = screenCenterY - constraints.maxHeight / 2f,
                            viewportWidth = constraints.maxWidth.toFloat(),
                            viewportHeight = constraints.maxHeight.toFloat(),
                        ),
                    )
                }
            }
            // Preserve the established draw order while sorting the already
            // calculated scalar instead of recalculating geometry in the comparator.
            visible.sortBy { card ->
                card.depthScale + if (card.placement.itemId == focusedItemId) 10f else 0f
            }

            val measured = visible.mapNotNull { visibleCard ->
                val placement = visibleCard.placement
                val item = itemById[placement.itemId] ?: return@mapNotNull null
                val isFocused = placement.itemId == focusedItemId
                val isCurrent = placement.itemId == currentMediaId
                val normalVisualAlpha = when {
                    isFocused -> 1f
                    focusedItemId != null ->
                        (0.48f + visibleCard.depthScale * 0.34f).coerceAtMost(0.78f)
                    else -> 0.72f + visibleCard.depthScale * 0.28f
                }
                val lightsOutVisualAlpha = focusWallPosterAlpha(
                    normalAlpha = normalVisualAlpha,
                    lightsOut = style == FocusWallStyle.Folia && lightsOut,
                    isFocused = isFocused,
                    isCurrent = isCurrent,
                )
                val placeable = subcompose(placement.itemId) {
                    val cardAnimationMillis = wallMotionDuration(320, motionPercent)
                    val animatedCenterX by animateFloatAsState(
                        targetValue = placement.centerX,
                        animationSpec = tween(durationMillis = cardAnimationMillis),
                        label = "wallCardX",
                    )
                    val animatedCenterY by animateFloatAsState(
                        targetValue = placement.centerY,
                        animationSpec = tween(durationMillis = cardAnimationMillis),
                        label = "wallCardY",
                    )
                    val animatedWidth by animateFloatAsState(
                        targetValue = placement.width,
                        animationSpec = tween(durationMillis = cardAnimationMillis),
                        label = "wallCardWidth",
                    )
                    val animatedHeight by animateFloatAsState(
                        targetValue = placement.height,
                        animationSpec = tween(durationMillis = cardAnimationMillis),
                        label = "wallCardHeight",
                    )
                    // Only the discrete lights-out state animates. Distance alpha
                    // follows the drag directly instead of restarting a 260 ms
                    // tween for every visible card on every pointer frame.
                    val animatedLightsOutFraction by animateFloatAsState(
                        targetValue = if (lightsOutVisualAlpha < normalVisualAlpha) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = wallMotionDuration(260, motionPercent),
                        ),
                        label = "wallCardLightsOutFraction",
                    )
                    val visualAlpha = normalVisualAlpha +
                        (lightsOutVisualAlpha - normalVisualAlpha) * animatedLightsOutFraction
                    val activePlaybackState = if (isFocused && isCurrent) {
                        playbackStateProvider.value
                    } else {
                        InactiveWallPlaybackState
                    }
                    FocusCoverCard(
                        item = item,
                        style = style,
                        isFocused = isFocused,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && currentIsPlaying,
                        playbackState = activePlaybackState,
                        subtitleState = subtitleState,
                        wordGlowMode = wordGlowMode,
                        decodeSize = artworkDecodeSize(
                            widthPx = placement.width,
                            heightPx = placement.height,
                            zoom = artworkScale,
                        ),
                        silhouette = placement.shape,
                        fitArtwork = placement.fitArtwork,
                        coverAccentArgb = if (isFocused) focusedCoverFacts?.accentArgb else null,
                        lyricsShadeAlpha = if (isFocused) {
                            coverLyricsShadeAlpha(
                                paletteArgbs = focusedCoverFacts?.paletteArgbs.orEmpty(),
                                fallbackArgb = artworkAccent(item.paletteSeed).toArgb(),
                            )
                        } else {
                            0f
                        },
                        posterTint = if (
                            style == FocusWallStyle.Folia && !isFocused && !isCurrent
                        ) {
                            posterTintColor.copy(
                                alpha = posterTintPercent.coerceIn(0, 60) / 100f,
                            )
                        } else {
                            Color.Transparent
                        },
                        motionPercent = motionPercent,
                        onClick = { onCardClick(item) },
                        onPlayPause = { onPlayPause(item) },
                        onSeekTo = onSeekTo,
                        onPrevious = onPrevious.takeIf {
                            isCurrent && currentHasPrevious
                        },
                        onNext = onNext.takeIf {
                            isCurrent && currentHasNext
                        },
                        onOpenLyrics = { onOpenLyrics(item) },
                        onClearFocus = { onFocusedItemChange(null) },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom * animatedWidth / placement.width
                                scaleY = zoom * animatedHeight / placement.height
                                translationX = (animatedCenterX - placement.centerX) * zoom
                                translationY = (animatedCenterY - placement.centerY) * zoom
                                alpha = visualAlpha
                                // Flush ordinary posters match the packed Folia
                                // reference and avoid one GPU shadow layer per card.
                                shadowElevation = if (isFocused) 12.dp.toPx() else 0f
                            },
                    )
                }.first().measure(
                    Constraints.fixed(
                        width = placement.width.roundToInt().coerceAtLeast(1),
                        height = placement.height.roundToInt().coerceAtLeast(1),
                    ),
                )
                visibleCard to placeable
            }

            layout(constraints.maxWidth, constraints.maxHeight) {
                measured.forEach { (visibleCard, placeable) ->
                    placeable.placeRelative(
                        x = (visibleCard.screenCenterX - placeable.width / 2f).roundToInt(),
                        y = (visibleCard.screenCenterY - placeable.height / 2f).roundToInt(),
                    )
                }
            }
        }

        // A tightly packed wall hides almost all of its backdrop. This low-alpha
        // screen-space wash lets the same light reach the posters themselves,
        // which is what makes the whole wall visibly change with the cover. It has
        // no pointer input, so the card and seek gestures below remain the owners.
        CoverAmbientLayer(
            palette = if (style == FocusWallStyle.Folia) ambientPalette else emptyList(),
            mode = AmbientLayerMode.PosterWash,
            strengthMultiplier = viewportState.ambientMultiplier,
            motionPercent = motionPercent,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Cover-derived light behind or gently over the poster canvas.
 *
 * This deliberately draws three cheap gradients instead of blurring a full-size
 * bitmap or running a frame-rate shader. Only colour changes animate, so a paused
 * long-form track costs nothing after its 900 ms transition settles. Keeping it in
 * its own composable also prevents those animation frames from re-running the wall's
 * SubcomposeLayout.
 */
private enum class AmbientLayerMode { Backdrop, PosterWash }

@Composable
private fun CoverAmbientLayer(
    palette: List<Color>,
    mode: AmbientLayerMode,
    strengthMultiplier: Float,
    motionPercent: Int,
    modifier: Modifier = Modifier,
) {
    val primaryTarget = palette.getOrNull(0) ?: Color.Transparent
    val secondaryTarget = palette.getOrNull(1)
        ?: palette.getOrNull(0)?.let { lerp(it, Color.White, 0.18f) }
        ?: Color.Transparent
    val tertiaryTarget = palette.getOrNull(2)
        ?: palette.getOrNull(0)?.let { lerp(it, Color.Black, 0.24f) }
        ?: Color.Transparent
    val primary by animateColorAsState(
        targetValue = primaryTarget,
        animationSpec = tween(durationMillis = wallMotionDuration(900, motionPercent)),
        label = "wallAmbientPrimary",
    )
    val secondary by animateColorAsState(
        targetValue = secondaryTarget,
        animationSpec = tween(durationMillis = wallMotionDuration(900, motionPercent)),
        label = "wallAmbientSecondary",
    )
    val tertiary by animateColorAsState(
        targetValue = tertiaryTarget,
        animationSpec = tween(durationMillis = wallMotionDuration(900, motionPercent)),
        label = "wallAmbientTertiary",
    )
    val animatedStrength by animateFloatAsState(
        targetValue = strengthMultiplier,
        animationSpec = tween(durationMillis = wallMotionDuration(260, motionPercent)),
        label = "wallAmbientStrength${mode.name}",
    )
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryStrength = when (mode) {
        AmbientLayerMode.Backdrop -> if (isDarkSurface) 0.32f else 0.20f
        AmbientLayerMode.PosterWash -> if (isDarkSurface) 0.13f else 0.085f
    }
    val secondaryStrength = when (mode) {
        AmbientLayerMode.Backdrop -> if (isDarkSurface) 0.28f else 0.17f
        AmbientLayerMode.PosterWash -> if (isDarkSurface) 0.105f else 0.065f
    }
    val tertiaryStrength = when (mode) {
        AmbientLayerMode.Backdrop -> if (isDarkSurface) 0.20f else 0.12f
        AmbientLayerMode.PosterWash -> if (isDarkSurface) 0.08f else 0.045f
    }
    val vignetteStrength = if (isDarkSurface) 0.18f else 0.055f
    val effectivePrimaryStrength = primaryStrength * animatedStrength
    val effectiveSecondaryStrength = secondaryStrength * animatedStrength
    val effectiveTertiaryStrength = tertiaryStrength * animatedStrength
    val effectiveVignetteStrength = vignetteStrength * animatedStrength

    Box(modifier = modifier.drawWithCache {
        val largestEdge = max(size.width, size.height)
        val primaryBrush = Brush.radialGradient(
                colors = listOf(
                    primary.multipliedAlpha(effectivePrimaryStrength),
                    primary.multipliedAlpha(effectivePrimaryStrength * 0.38f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.14f, size.height * 0.16f),
                radius = largestEdge * 0.72f,
        )
        val secondaryBrush = Brush.radialGradient(
                colors = listOf(
                    secondary.multipliedAlpha(effectiveSecondaryStrength),
                    secondary.multipliedAlpha(effectiveSecondaryStrength * 0.32f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.88f, size.height * 0.78f),
                radius = largestEdge * 0.67f,
        )
        val tertiaryBrush = Brush.radialGradient(
                colors = listOf(
                    tertiary.multipliedAlpha(effectiveTertiaryStrength),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.62f, size.height * 0.30f),
                radius = largestEdge * 0.48f,
        )
        val vignetteBrush = if (mode == AmbientLayerMode.Backdrop) {
            // A shallow edge falloff keeps the colours reading as light in a
            // space rather than three translucent circles. The poster wash omits
            // it so card artwork is coloured, not globally darkened twice.
            Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = effectiveVignetteStrength),
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = largestEdge * 0.72f,
            )
        } else {
            null
        }
        onDrawBehind {
            drawRect(primaryBrush)
            drawRect(secondaryBrush)
            drawRect(tertiaryBrush)
            vignetteBrush?.let { brush -> drawRect(brush) }
        }
    })
}

private fun fallbackAmbientPalette(accent: Color): List<Color> = listOf(
    accent,
    lerp(accent, Color.White, 0.18f),
    lerp(accent, Color.Black, 0.24f),
)

private fun Color.multipliedAlpha(factor: Float): Color =
    copy(alpha = alpha * factor.coerceIn(0f, 1f))

@Composable
private fun FocusCoverCard(
    item: LibraryItem,
    style: FocusWallStyle,
    isFocused: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    playbackState: PlaybackState,
    subtitleState: SubtitleDetailState,
    wordGlowMode: LyricsWordGlowMode,
    decodeSize: IntSize,
    /**
     * The silhouette this card is drawn as, or null for a plain rectangle. A shaped card
     * is still placed and animated by its rectangle; this only changes what it paints and
     * where it can be touched.
     */
    silhouette: FocusWallShape?,
    /** Letterbox the artwork instead of cropping it, so none of it is hidden. */
    fitArtwork: Boolean,
    /**
     * The accent sampled from this card's own cover, or null when it is not the focused
     * card or its artwork held no colour worth sampling. The wall reads the focused cover
     * once for the whole block, so the card is handed the colour rather than loading it.
     */
    coverAccentArgb: Int?,
    /** Contrast shade derived from the same bounded palette decode as the border. */
    lyricsShadeAlpha: Float,
    /** Cover-derived wash for an ordinary Folia poster; current/focused cards get transparent. */
    posterTint: Color,
    motionPercent: Int,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onOpenLyrics: () -> Unit,
    onClearFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val platformContext = LocalPlatformContext.current
    val foliaPoster = style == FocusWallStyle.Folia
    val foliaFocused = foliaPoster && isFocused
    // The card is composed at its zoom-1 layout size and then magnified by the canvas,
    // so it asks for artwork at the size it will actually be painted at. Layout
    // constraints would otherwise pin the request to the smaller layout size.
    val artworkRequest = remember(item.artworkUri, decodeSize) {
        item.artworkUri?.let { uri ->
            ImageRequest.Builder(platformContext)
                .data(uri)
                .size(decodeSize.width, decodeSize.height)
                // Inexact keeps Coil from upscaling a small source to the request: it
                // returns what the artwork actually has and lets the card scale it.
                .precision(Precision.INEXACT)
                .build()
        }
    }
    val coverAccent = coverAccentArgb?.let { Color(it) } ?: artworkAccent(item.paletteSeed)
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) coverAccent.copy(alpha = 0.76f) else Color.Transparent,
        animationSpec = tween(durationMillis = wallMotionDuration(260, motionPercent)),
        label = "focusBorder",
    )
    val borderWidth by animateFloatAsState(
        targetValue = when {
            foliaFocused -> 1.1f
            isFocused -> 2.2f
            else -> 0f
        },
        animationSpec = tween(durationMillis = wallMotionDuration(260, motionPercent)),
        label = "focusBorderWidth",
    )
    val animatedPosterTint by animateColorAsState(
        targetValue = posterTint,
        animationSpec = tween(durationMillis = wallMotionDuration(260, motionPercent)),
        label = "wallPosterTint",
    )
    // A silhouette is clipped to its own cells, so the artwork inside it is cut to the
    // shape and a tap in the corners of its rectangle never reaches the card. Every other
    // card keeps the rounded rectangle the wall has always drawn.
    val shape: Shape = silhouette?.let(::FocusWallCellShape)
        ?: RoundedCornerShape(
            when {
                foliaPoster -> 2.dp
                isFocused -> 12.dp
                else -> 8.dp
            },
        )

    Box(
        modifier = modifier
            .border(borderWidth.dp, borderColor, shape)
            .clip(shape)
            // Plain clickable, deliberately: binding onDoubleClick as well would
            // make Compose hold every single tap back for the double-tap timeout
            // before delivering it, which is felt as lag on the wall's main
            // gesture. "Double tap to open" still works -- see resolveCardTap.
            .clickable(onClick = onClick),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(artworkBrush(item.paletteSeed)),
        ) {
            val compactCard = !isFocused && (maxWidth < 116.dp || maxHeight < 112.dp)
            if (artworkRequest != null) {
                AsyncImage(
                    model = artworkRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    // 完整封面 exists to show the whole cover, so it letterboxes the
                    // artwork in the card the other presets crop to fill.
                    contentScale = if (fitArtwork) ContentScale.Fit else ContentScale.Crop,
                )
            }

            // One cached draw node replaces the old tint, vertical shade and
            // focused-side shade Boxes. Their visual order and colours are the
            // same, but ordinary cards no longer add two extra layout nodes and
            // rebuild gradient brushes while the wall moves.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val verticalShade = if (foliaFocused) {
                            Brush.verticalGradient(
                                0f to Color(0x42000000),
                                0.38f to Color(0x30000000),
                                0.72f to Color(0x62000000),
                                1f to Color(0xD605070B),
                            )
                        } else if (foliaPoster) {
                            Brush.verticalGradient(
                                0f to Color(0x18000000),
                                0.48f to Color(0x26000000),
                                1f to Color(0xDA05070B),
                            )
                        } else {
                            Brush.verticalGradient(
                                0f to Color(0x08000000),
                                0.46f to Color(0x24000000),
                                1f to Color(0xEB05070B),
                            )
                        }
                        val focusedSideShade = if (foliaFocused) {
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = lyricsShadeAlpha * 0.72f),
                                0.54f to Color.Black.copy(alpha = lyricsShadeAlpha * 0.28f),
                                1f to Color.Transparent,
                            )
                        } else {
                            null
                        }
                        onDrawBehind {
                            if (animatedPosterTint.alpha > 0f) drawRect(animatedPosterTint)
                            drawRect(verticalShade)
                            focusedSideShade?.let { shade -> drawRect(shade) }
                        }
                    },
            )

            val lyricDocument = subtitleState.document?.takeIf {
                isFocused &&
                    isCurrent &&
                    item.type == LibraryItemType.Audio &&
                    subtitleState.describes(item.id) &&
                    it.isTimed
            }
            val lyricFrame = lyricDocument?.coverLyricsFrame(playbackState.positionMs)
            val hasCoverLyrics = lyricFrame != null
            if (foliaFocused) {
                FoliaFocusedPosterChrome(
                    item = item,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    playbackState = playbackState,
                    lyricFrame = lyricFrame,
                    lyricDocument = lyricDocument,
                    wordGlowMode = wordGlowMode,
                    accent = coverAccent,
                    shadeAlpha = lyricsShadeAlpha,
                    motionPercent = motionPercent,
                    onPlayPause = onPlayPause,
                    onSeekTo = onSeekTo,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onOpenLyrics = onOpenLyrics.takeIf { lyricFrame != null },
                    onClearFocus = onClearFocus,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (lyricFrame != null) {
                val lyricTopPadding = (maxHeight * 0.18f).coerceIn(28.dp, 48.dp)
                val lyricBottomPadding = (maxHeight * 0.36f).coerceIn(58.dp, 92.dp)
                val usableLyricHeight = maxHeight - lyricTopPadding - lyricBottomPadding
                CoverLyricsOverlay(
                    frame = lyricFrame,
                    document = lyricDocument,
                    positionMs = playbackState.positionMs,
                    isPlaying = isPlaying,
                    wordGlowMode = wordGlowMode,
                    accent = coverAccent,
                    shadeAlpha = lyricsShadeAlpha,
                    motionPercent = motionPercent,
                    showContext = usableLyricHeight >= 124.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                        // Top icons own the first band; title, seek and transport
                        // own the deeper bottom band. Lyrics occupy what is truly
                        // left instead of being painted under those controls.
                        .padding(
                            start = 12.dp,
                            top = lyricTopPadding,
                            end = 12.dp,
                            bottom = lyricBottomPadding,
                        ),
                )
            }

            if (foliaPoster && !isFocused) {
                FoliaOrdinaryPosterChrome(
                    item = item,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (!foliaPoster) {
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
            }

            if (isCurrent && !foliaPoster) {
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

            if (!foliaPoster) Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(if (isFocused) 18.dp else if (compactCard) 8.dp else 14.dp),
            ) {
                // The live lyric scene is the focused cover's central subject. Its
                // now-playing badge still identifies the media, so repeating title
                // and metadata below it would take the only space the lyrics have.
                if (!hasCoverLyrics) {
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
                }

                // Only audio carries an action row here. A folder owns no playable
                // queue of its own -- the folder listing excludes child folders --
                // and tapping the focused cover already resolves to the same
                // activation the card used to spell out, so a focused folder shows
                // nothing but its own title and counts.
                //
                // The row that carries the play button carries the way out of focus
                // too, at its far end: the button pair then reads as one row of
                // actions, and neither sits on top of the title above it.
                if (isFocused && item.type == LibraryItemType.Audio) {
                    if (isCurrent) {
                        Spacer(Modifier.height(8.dp))
                        CompactSeekBar(playbackState = playbackState, onSeekTo = onSeekTo)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        FilledIconButton(onClick = onPlayPause) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.pause else R.string.play,
                                ),
                            )
                        }
                        ClearFocusButton(onClick = onClearFocus)
                    }
                } else if (isFocused) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        ClearFocusButton(onClick = onClearFocus)
                    }
                }
            }
        }
    }
}

/**
 * Ordinary Folia posters use the same edge, shade and type language as the hero,
 * but do not carry playback chrome. The tiny square remains only as the product's
 * folder/audio discriminator; everything else is cover, title and metadata.
 */
@Composable
private fun FoliaOrdinaryPosterChrome(
    item: LibraryItem,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val inset = maxWidth * FoliaPosterChromeSpec.OrdinaryInset
        val titleSize = (maxWidth.value * FoliaPosterChromeSpec.OrdinaryTitleFont)
            .coerceIn(9f, 18f).sp
        val metadataSize = (maxWidth.value * FoliaPosterChromeSpec.OrdinaryMetadataFont)
            .coerceIn(7f, 10f).sp
        val badgeSize = (maxWidth * FoliaPosterChromeSpec.OrdinaryTypeBadge)
            .coerceIn(7.dp, 18.dp)

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = inset, top = inset)
                .size(badgeSize)
                .background(Color(0x7810141B), RoundedCornerShape(1.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (item.type == LibraryItemType.Folder) {
                    Icons.Default.Folder
                } else {
                    Icons.Default.LibraryMusic
                },
                contentDescription = stringResource(
                    if (item.type == LibraryItemType.Folder) R.string.folder_type else R.string.audio_type,
                ),
                modifier = Modifier.size(badgeSize * 0.56f),
                tint = Color.White.copy(alpha = 0.76f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = inset,
                    end = inset,
                    bottom = maxHeight * FoliaPosterChromeSpec.OrdinaryBottom,
                )
                .fillMaxWidth(),
        ) {
            Text(
                text = item.title,
                color = Color.White.copy(alpha = 0.90f),
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.94f), Offset(0f, 1f), 6f),
                ),
            )
            Text(
                text = item.detailLine(),
                color = Color.White.copy(alpha = 0.58f),
                fontSize = metadataSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.90f), Offset(0f, 1f), 4f),
                ),
            )
        }
    }
}

/**
 * Focused-poster chrome measured from the owner's Folia reference.
 *
 * The reference is a poster composition, not a Material card: a tiny status label
 * and title live in the upper-left, lyrics use an asymmetric left-aligned hierarchy,
 * and the transport/progress chrome hugs the bottom edge. Every position and visual
 * size is derived from the poster itself so square, portrait and landscape heroes
 * keep the same composition.
 */
@Composable
private fun FoliaFocusedPosterChrome(
    item: LibraryItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    playbackState: PlaybackState,
    lyricFrame: CoverLyricsFrame?,
    lyricDocument: TimedTextDocument?,
    wordGlowMode: LyricsWordGlowMode,
    accent: Color,
    shadeAlpha: Float,
    motionPercent: Int,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onOpenLyrics: (() -> Unit)?,
    onClearFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val inset = maxWidth * FoliaPosterChromeSpec.HorizontalInset
        val statusFont = (maxWidth.value * FoliaPosterChromeSpec.HeaderMetadataFont)
            .coerceIn(7f, 10f).sp
        val headerTitleFont = (maxWidth.value * FoliaPosterChromeSpec.HeaderTitleFont)
            .coerceIn(10f, 17f).sp
        val metadataFont = (maxWidth.value * FoliaPosterChromeSpec.HeaderMetadataFont)
            .coerceIn(7f, 10f).sp
        val controlTouchSize = (maxWidth * FoliaPosterChromeSpec.ControlTouchSize)
            .coerceIn(16.dp, 30.dp)
        val controlVisualSize = (maxWidth * FoliaPosterChromeSpec.ControlVisualSize)
            .coerceIn(9.dp, 17.dp)

        Text(
            text = stringResource(
                when {
                    isCurrent -> R.string.now_playing
                    item.type == LibraryItemType.Folder -> R.string.folder_type
                    else -> R.string.audio_type
                },
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = inset,
                    top = maxHeight * FoliaPosterChromeSpec.StatusTop,
                )
                .background(Color(0x7810141B), RoundedCornerShape(1.dp))
                .padding(horizontal = 3.dp, vertical = 1.dp),
            color = accentForPoster(accent).copy(alpha = 0.88f),
            fontSize = statusFont,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = inset,
                    top = maxHeight * FoliaPosterChromeSpec.HeaderTop,
                )
                .width(maxWidth * FoliaPosterChromeSpec.HeaderTitleWidth),
        ) {
            Text(
                text = item.title,
                color = Color.White.copy(alpha = 0.94f),
                fontSize = headerTitleFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.9f), Offset(0f, 1f), 5f),
                ),
            )
            Text(
                text = item.detailLine(),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = metadataFont,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.86f), Offset(0f, 1f), 4f),
                ),
            )
        }

        if (lyricFrame != null) {
            FoliaCoverLyricsOverlay(
                frame = lyricFrame,
                document = lyricDocument,
                positionMs = playbackState.positionMs,
                isPlaying = isPlaying,
                wordGlowMode = wordGlowMode,
                accent = accent,
                shadeAlpha = shadeAlpha,
                motionPercent = motionPercent,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (item.type == LibraryItemType.Audio) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = inset,
                        top = maxHeight * FoliaPosterChromeSpec.ControlsTop,
                    ),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FoliaPosterControl(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    label = stringResource(if (isPlaying) R.string.pause else R.string.play),
                    onClick = onPlayPause,
                    touchSize = controlTouchSize,
                    iconSize = controlVisualSize,
                )
                if (isCurrent) {
                    FoliaPosterControl(
                        icon = Icons.Default.SkipPrevious,
                        label = stringResource(R.string.previous_track),
                        onClick = onPrevious,
                        touchSize = controlTouchSize,
                        iconSize = controlVisualSize,
                    )
                    FoliaPosterControl(
                        icon = Icons.Default.SkipNext,
                        label = stringResource(R.string.next_track),
                        onClick = onNext,
                        touchSize = controlTouchSize,
                        iconSize = controlVisualSize,
                    )
                }
                if (onOpenLyrics != null) {
                    FoliaPosterControl(
                        icon = Icons.Default.Subtitles,
                        label = stringResource(R.string.subtitle_detail),
                        onClick = onOpenLyrics,
                        touchSize = controlTouchSize,
                        iconSize = controlVisualSize,
                        selected = true,
                    )
                }
                FoliaPosterControl(
                    icon = Icons.Default.Close,
                    label = stringResource(R.string.clear_focus),
                    onClick = onClearFocus,
                    touchSize = controlTouchSize,
                    iconSize = controlVisualSize,
                )
            }
        } else {
            FoliaPosterControl(
                icon = Icons.Default.Close,
                label = stringResource(R.string.clear_focus),
                onClick = onClearFocus,
                touchSize = controlTouchSize,
                iconSize = controlVisualSize,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = inset,
                        top = maxHeight * FoliaPosterChromeSpec.ControlsTop,
                    ),
            )
        }

        if (isCurrent) {
            Text(
                text = "${formatPlaybackTime(playbackState.positionMs)} / " +
                    if (playbackState.hasDuration) {
                        formatPlaybackTime(playbackState.durationMs)
                    } else {
                        stringResource(R.string.unknown_duration)
                    },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = maxWidth * 0.47f,
                        top = maxHeight * (FoliaPosterChromeSpec.ControlsTop + 0.012f),
                    ),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = metadataFont,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.9f), Offset(0f, 1f), 4f),
                ),
            )
            FoliaSeekBar(
                playbackState = playbackState,
                onSeekTo = onSeekTo,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = inset,
                        top = maxHeight * FoliaPosterChromeSpec.ProgressTop,
                        end = inset,
                    )
                    .fillMaxWidth()
                    .height((maxHeight * 0.034f).coerceIn(8.dp, 16.dp)),
            )
        }
    }
}

private fun accentForPoster(accent: Color): Color = if (accent.luminance() < 0.46f) {
    lerp(accent, Color.White, 0.52f)
} else {
    accent
}

@Composable
private fun FoliaCoverLyricsOverlay(
    frame: CoverLyricsFrame,
    document: TimedTextDocument?,
    positionMs: Long,
    isPlaying: Boolean,
    wordGlowMode: LyricsWordGlowMode,
    accent: Color,
    shadeAlpha: Float,
    motionPercent: Int,
    modifier: Modifier = Modifier,
) {
    val duration = wallMotionDuration(280, motionPercent)
    val readableAccent = accentForPoster(accent)
    AnimatedContent(
        targetState = frame,
        contentKey = { it.currentIndex },
        transitionSpec = {
            fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
        },
        contentAlignment = Alignment.Center,
        label = "foliaCoverLyrics",
        modifier = modifier,
    ) { shown ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val timeline = remember(document, shown.currentIndex, wordGlowMode) {
                document?.coverLyricsGlowTimeline(shown, wordGlowMode)
            }
            // A cover-wide field, not a pill around the words. The active line gets
            // the deepest part while the artwork is still readable above it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.28f to Color.Transparent,
                            0.42f to Color.Black.copy(alpha = shadeAlpha * 0.24f),
                            0.52f to Color.Black.copy(alpha = shadeAlpha * 0.62f),
                            0.72f to Color.Black.copy(alpha = shadeAlpha * 0.38f),
                            0.86f to Color.Transparent,
                        ),
                    ),
            )
            val previousSize = (maxWidth.value * FoliaPosterChromeSpec.PreviousLyricFont)
                .coerceIn(9f, 20f).sp
            val currentSize = (maxWidth.value * FoliaPosterChromeSpec.CurrentLyricFont)
                .coerceIn(12f, 30f).sp
            val followingSize = (maxWidth.value * FoliaPosterChromeSpec.FollowingLyricFont)
                .coerceIn(9f, 20f).sp

            if (shown.previous != null) {
                Text(
                    text = shown.previous,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(
                            y = maxHeight *
                                (FoliaPosterChromeSpec.PreviousLyricCenter - 0.5f),
                        )
                        .padding(start = maxWidth * FoliaPosterChromeSpec.ContextInset)
                        .width(maxWidth * FoliaPosterChromeSpec.PreviousLyricWidth)
                        .blur(0.7.dp),
                    color = readableAccent.copy(alpha = 0.58f),
                    fontSize = previousSize,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = Shadow(Color.Black.copy(alpha = 0.92f), Offset(0f, 2f), 8f),
                    ),
                )
            }
            GlowingLyricText(
                text = shown.current,
                timeline = timeline,
                positionMs = positionMs,
                isPlaying = isPlaying,
                accent = readableAccent,
                motionPercent = motionPercent,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(
                        y = maxHeight *
                            (FoliaPosterChromeSpec.CurrentLyricCenter - 0.5f),
                    )
                    .padding(start = maxWidth * FoliaPosterChromeSpec.HorizontalInset)
                    .width(maxWidth * FoliaPosterChromeSpec.CurrentLyricWidth),
                restingColor = readableAccent.copy(alpha = 0.94f),
                fontSize = currentSize,
                lineHeight = currentSize * 1.12f,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.94f), Offset(0f, 2f), 9f),
                ),
            )
            if (shown.next != null) {
                Text(
                    text = shown.next,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(
                            y = maxHeight *
                                (FoliaPosterChromeSpec.FollowingLyricCenter - 0.5f),
                        )
                        .padding(start = maxWidth * FoliaPosterChromeSpec.HorizontalInset)
                        .width(maxWidth * FoliaPosterChromeSpec.FollowingLyricWidth),
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = followingSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        shadow = Shadow(Color.Black.copy(alpha = 0.9f), Offset(0f, 1f), 5f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun FoliaPosterControl(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)?,
    touchSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val enabled = onClick != null
    Box(
        modifier = modifier
            .size(touchSize)
            .clip(RoundedCornerShape(if (selected) 4.dp else touchSize / 2))
            .background(
                when {
                    selected -> Color.White.copy(alpha = 0.18f)
                    else -> Color.Black.copy(alpha = 0.12f)
                },
            )
            .clickable(enabled = enabled) { onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(iconSize),
            tint = Color.White.copy(alpha = if (enabled) 0.92f else 0.30f),
        )
    }
}

/**
 * Three-line lyric scene for the expanded playing poster.
 *
 * It is deliberately centred rather than reusing the full reader's scrolling
 * anchor: a cover is a finite visual composition, not a document. Context lines
 * blur and recede, the active line takes the focus palette, and line changes use
 * the global motion percentage. A palette-derived black field protects text on
 * bright artwork without permanently blurring the cover bitmap.
 */
@Composable
private fun CoverLyricsOverlay(
    frame: CoverLyricsFrame,
    document: TimedTextDocument?,
    positionMs: Long,
    isPlaying: Boolean,
    wordGlowMode: LyricsWordGlowMode,
    accent: Color,
    shadeAlpha: Float,
    motionPercent: Int,
    showContext: Boolean,
    modifier: Modifier = Modifier,
) {
    val duration = wallMotionDuration(280, motionPercent)
    val readableAccent = if (accent.luminance() < 0.46f) {
        lerp(accent, Color.White, 0.52f)
    } else {
        accent
    }
    AnimatedContent(
        targetState = frame,
        contentKey = { it.currentIndex },
        transitionSpec = {
            (fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = 0.96f)) togetherWith
                (fadeOut(tween(duration)) + scaleOut(tween(duration), targetScale = 1.025f))
        },
        contentAlignment = Alignment.Center,
        label = "coverLyrics",
        modifier = modifier,
    ) { shown ->
        val timeline = remember(document, shown.currentIndex, wordGlowMode) {
            document?.coverLyricsGlowTimeline(shown, wordGlowMode)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = shadeAlpha),
                            Color.Black.copy(alpha = shadeAlpha * 0.78f),
                            Color.Transparent,
                        ),
                        radius = 520f,
                    ),
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showContext && shown.previous != null) {
                CoverLyricContextLine(shown.previous, blurRadius = 1.8f, alpha = 0.52f)
            }
            GlowingLyricText(
                text = shown.current,
                timeline = timeline,
                positionMs = positionMs,
                isPlaying = isPlaying,
                accent = readableAccent,
                motionPercent = motionPercent,
                restingColor = readableAccent,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.92f), Offset(0f, 2f), 8f),
                ),
            )
            if (showContext && shown.next != null) {
                CoverLyricContextLine(shown.next, blurRadius = 1.2f, alpha = 0.68f)
            }
        }
    }
}

@Composable
private fun CoverLyricContextLine(text: String, blurRadius: Float, alpha: Float) {
    Text(
        text = text,
        modifier = Modifier.blur(blurRadius.dp),
        color = Color.White.copy(alpha = alpha),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium.copy(
            shadow = Shadow(Color.Black.copy(alpha = 0.88f), Offset(0f, 1f), 6f),
        ),
    )
}

/**
 * The focused card's way out of focus, at the card's bottom end.
 *
 * Icon only: the card is a picture, so a label would have to sit on top of it, and the
 * close glyph already carries the meaning. Removing focus rather than the item is the
 * only thing it can do, so there is no destructive action to guard.
 */
@Composable
private fun ClearFocusButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(38.dp)
            .background(Color(0xB814171D), CircleShape),
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.clear_focus),
            modifier = Modifier.size(20.dp),
            tint = Color.White,
        )
    }
}

/**
 * A card drawn as the cells the wall gave it, rather than as a rectangle.
 *
 * The cells are merged into one path before it is handed over: stroking them separately
 * would trace the seams between the cells as well as the silhouette's own outline, and
 * a silhouette must show one edge, not a grid. The cells meet exactly edge to edge -- the
 * layout gives adjacent cells of one card a shared edge -- so the merge is a real union
 * rather than an approximation.
 *
 * The shape is scaled to whatever box it is asked about, which is the card's own measured
 * size, so the same shape survives the card being animated between two sizes.
 */
private class FocusWallCellShape(private val cells: FocusWallShape) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (cells.rects.isEmpty()) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        val cellPath = Path()
        cells.rects.forEach { rect ->
            cellPath.addRect(
                Rect(
                    left = rect.left * size.width,
                    top = rect.top * size.height,
                    right = rect.right * size.width,
                    bottom = rect.bottom * size.height,
                ),
            )
        }
        return Outline.Generic(
            Path().apply { op(cellPath, cellPath, PathOperation.Union) },
        )
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

/** The size presets share the same three labels as the density options. */
private fun FoliaFocusSize.labelResource(): Int = when (this) {
    FoliaFocusSize.Small -> R.string.density_small
    FoliaFocusSize.Medium -> R.string.density_medium
    FoliaFocusSize.Large -> R.string.density_large
}

private fun FoliaFocusShape.labelResource(): Int = when (this) {
    FoliaFocusShape.Square -> R.string.focus_shape_square
    FoliaFocusShape.Portrait -> R.string.focus_shape_portrait
    FoliaFocusShape.Landscape -> R.string.focus_shape_landscape
    FoliaFocusShape.FullCover -> R.string.focus_shape_full
    FoliaFocusShape.Heart -> R.string.focus_shape_heart
    FoliaFocusShape.Circle -> R.string.focus_shape_circle
}

/** A glyph whose own proportions say which shape the option picks. */
private fun FoliaFocusShape.icon(): ImageVector = when (this) {
    FoliaFocusShape.Square -> Icons.Default.CropSquare
    FoliaFocusShape.Portrait -> Icons.Default.CropPortrait
    FoliaFocusShape.Landscape -> Icons.Default.CropLandscape
    FoliaFocusShape.FullCover -> Icons.Default.FitScreen
    FoliaFocusShape.Heart -> Icons.Default.Favorite
    FoliaFocusShape.Circle -> Icons.Default.Circle
}
