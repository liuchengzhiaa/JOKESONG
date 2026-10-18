package com.example.asmrplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.core.settings.ToolClusterStyle
import com.example.asmrplayer.ui.CrestLevelIcon

/**
 * One row of a [GlassToolCluster] panel: a glyph, a label, and at most one trailing
 * affordance.
 *
 * The trailing slot is a `when`, not three independent slots, because the three
 * meanings are mutually exclusive on purpose: a row either reports a level, reports
 * a written value, or confirms that it is the chosen option -- never two of them.
 */
data class GlassTool(
    val icon: ImageVector,
    val labelRes: Int,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val valueRes: Int? = null,
    val valueText: String? = null,
    /**
     * A label that has already been resolved, for the rows whose wording carries an
     * argument. A resource id alone cannot express "后退 10 秒" without either the
     * number going into the string itself -- where it would then have to be kept in
     * step with a constant by hand -- or every panel growing a formatting parameter
     * for the one row that needs it.
     */
    val labelText: String? = null,
    val levelFraction: Float? = null,
    val selected: Boolean = false,
)

/**
 * The frosted-glass collect button and the panel that expands out of it, anchored to
 * the bottom-end corner of whatever it is placed in.
 *
 * Extracted from the focus wall so the same affordance can appear on more than one
 * surface while looking and behaving identically there. Everything that makes the
 * gesture feel like one thing is preserved verbatim: the glyph turns 45 degrees as
 * the panel grows from the corner the button sits in, so the turn reads as the lid
 * opening rather than as two unrelated animations.
 *
 * [scrim] is a parameter rather than a constant because a scrim is a claim on every
 * touch inside this box. That is right where the panel is the only thing to interact
 * with, and wrong anywhere the box also carries a gesture: on the full player the
 * artwork underneath owns a left swipe, and an invisible scrim would swallow it.
 * Pass `false` there and the button becomes the only way to dismiss.
 *
 * @param panelMotionMillis already resolved from the global motion setting by the
 *   caller, which keeps this component free of any one surface's motion policy.
 * @param triggerIcon what the button shows. Defaulted to the wall's glyph so the wall
 *   keeps rendering exactly what it always did; a surface that has its own idea of
 *   what "more" looks like passes its own.
 * @param triggerRotationDegrees how far the glyph turns when the panel opens. The
 *   45 degrees is the *wall's*: that glyph closes into a cross, so the turn reads as a
 *   lid. It is a parameter rather than a constant because the same turn on a
 *   horizontal ellipsis just leaves a slanted one -- a glyph that reads as damaged
 *   rather than as open -- and a surface should be able to say so.
 */
@Composable
fun GlassToolCluster(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    panelMotionMillis: Int,
    contentDescription: String,
    style: ToolClusterStyle,
    modifier: Modifier = Modifier,
    scrim: Boolean = true,
    triggerIcon: ImageVector = Icons.Default.Widgets,
    triggerRotationDegrees: Float = 45f,
    panel: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (expanded && scrim) {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = interactionSource, indication = null) {
                        onExpandedChange(false)
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
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(panelMotionMillis)) + scaleIn(
                    animationSpec = tween(panelMotionMillis),
                    initialScale = 0.82f,
                    transformOrigin = TransformOrigin(1f, 1f),
                ),
                exit = fadeOut(tween(panelMotionMillis)) + scaleOut(
                    animationSpec = tween(panelMotionMillis),
                    targetScale = 0.88f,
                    transformOrigin = TransformOrigin(1f, 1f),
                ),
            ) {
                panel()
            }
            GlassToolButton(
                expanded = expanded,
                onClick = { onExpandedChange(!expanded) },
                panelMotionMillis = panelMotionMillis,
                contentDescription = contentDescription,
                style = style,
                icon = triggerIcon,
                rotationDegrees = triggerRotationDegrees,
            )
        }
    }
}

@Composable
private fun GlassToolButton(
    expanded: Boolean,
    onClick: () -> Unit,
    panelMotionMillis: Int,
    contentDescription: String,
    style: ToolClusterStyle,
    icon: ImageVector,
    rotationDegrees: Float,
) {
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) rotationDegrees else 0f,
        animationSpec = tween(panelMotionMillis),
        label = "glass tool rotation",
    )
    val chrome = buttonChrome(style)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .then(
                chrome.borderColor?.let { borderColor ->
                    Modifier.border(width = chrome.borderWidth, color = borderColor, shape = CircleShape)
                } ?: Modifier,
            ),
        shape = CircleShape,
        color = chrome.color,
        contentColor = chrome.contentColor,
        tonalElevation = 0.dp,
        shadowElevation = chrome.shadowElevation,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    chrome.gradient?.let { colors ->
                        Modifier.background(Brush.linearGradient(colors))
                    } ?: Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(19.dp)
                    .rotate(iconRotation),
            )
        }
    }
}

/** A plain panel of rows, for a surface whose tools are all one level deep. */
@Composable
fun GlassToolsPanel(
    tools: List<GlassTool>,
    style: ToolClusterStyle,
    modifier: Modifier = Modifier,
) {
    GlassPanelSurface(style = style, modifier = modifier) { contentColor ->
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            tools.forEach { tool -> GlassToolRow(tool, contentColor) }
        }
    }
}

/**
 * A page of options for one setting. It carries its own back row rather than a
 * breadcrumb: the panel is a small floating list, and one row that returns to the
 * page above is quicker to hit than a title with a target beside it.
 */
@Composable
fun GlassOptionsPanel(
    titleRes: Int,
    onBack: () -> Unit,
    options: List<GlassTool>,
    style: ToolClusterStyle,
    modifier: Modifier = Modifier,
) {
    GlassPanelSurface(style = style, modifier = modifier) { contentColor ->
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
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
                    tint = contentColor,
                )
                Text(
                    text = stringResource(titleRes),
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            options.forEach { option -> GlassToolRow(option, contentColor) }
        }
    }
}

@Composable
private fun GlassPanelSurface(
    style: ToolClusterStyle,
    modifier: Modifier = Modifier,
    content: @Composable (Color) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val chrome = panelChrome(style)
    Surface(
        modifier = modifier.then(
            chrome.borderColor?.let { borderColor ->
                Modifier.border(width = chrome.borderWidth, color = borderColor, shape = shape)
            } ?: Modifier,
        ),
        shape = shape,
        color = chrome.color,
        contentColor = chrome.contentColor,
        tonalElevation = 0.dp,
        shadowElevation = chrome.shadowElevation,
    ) {
        content(chrome.contentColor)
    }
}

@Composable
private fun GlassToolRow(tool: GlassTool, contentColor: Color) {
    val contentAlpha = if (tool.enabled) 1f else 0.38f
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
            tint = contentColor.copy(alpha = contentAlpha),
        )
        Text(
            text = tool.labelText ?: stringResource(tool.labelRes),
            color = contentColor.copy(alpha = contentAlpha),
            style = MaterialTheme.typography.bodyMedium,
        )
        // The chosen option is the only row that can carry a trailing affordance:
        // a setting row shows its current value instead, so it never needs both.
        when {
            tool.selected -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            tool.levelFraction != null -> CrestLevelIcon(
                fraction = tool.levelFraction,
                // Dimmed with the row it belongs to, so an unavailable setting does
                // not read as a live one.
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                track = contentColor.copy(alpha = 0.16f * contentAlpha),
            )
            tool.valueText != null -> Text(
                text = tool.valueText,
                color = contentColor.copy(alpha = 0.7f * contentAlpha),
                style = MaterialTheme.typography.bodyMedium,
            )
            tool.valueRes != null -> Text(
                text = stringResource(tool.valueRes),
                // Dimmed with the row it belongs to: an unavailable setting's value is
                // not something to read as actionable either.
                color = contentColor.copy(alpha = 0.7f * contentAlpha),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The resolved chrome of one style. Nulls mean "this style does not draw that". */
private data class GlassChrome(
    val color: Color,
    val contentColor: Color,
    val borderColor: Color?,
    val borderWidth: Dp,
    val shadowElevation: Dp,
    val gradient: List<Color>?,
)

/**
 * Frosted is first and is exactly what the wall already drew, so an install that
 * predates this setting renders identically.
 */
@Composable
private fun buttonChrome(style: ToolClusterStyle): GlassChrome = when (style) {
    ToolClusterStyle.Frosted -> GlassChrome(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.54f),
        contentColor = MaterialTheme.colorScheme.primary,
        borderColor = Color.White.copy(alpha = 0.30f),
        borderWidth = 1.dp,
        shadowElevation = 10.dp,
        gradient = listOf(
            Color.White.copy(alpha = 0.16f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            Color.Transparent,
        ),
    )
    ToolClusterStyle.Outline -> GlassChrome(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        borderWidth = 1.5.dp,
        shadowElevation = 0.dp,
        gradient = null,
    )
    ToolClusterStyle.Solid -> GlassChrome(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        borderColor = null,
        borderWidth = 0.dp,
        shadowElevation = 4.dp,
        gradient = null,
    )
    ToolClusterStyle.Minimal -> GlassChrome(
        // A fully transparent disc would put a 19 dp glyph straight onto whatever
        // artwork is behind it, so the faintest backing is kept for legibility.
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
        contentColor = MaterialTheme.colorScheme.primary,
        borderColor = null,
        borderWidth = 0.dp,
        shadowElevation = 0.dp,
        gradient = null,
    )
}

/**
 * Panels stay far more opaque than buttons in every style: a button is a small disc
 * over artwork, but a panel is a list of text that has to be read.
 */
@Composable
private fun panelChrome(style: ToolClusterStyle): GlassChrome = when (style) {
    ToolClusterStyle.Frosted -> GlassChrome(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = Color.White.copy(alpha = 0.18f),
        borderWidth = 1.dp,
        shadowElevation = 12.dp,
        gradient = null,
    )
    ToolClusterStyle.Outline -> GlassChrome(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        borderWidth = 1.5.dp,
        shadowElevation = 2.dp,
        gradient = null,
    )
    ToolClusterStyle.Solid -> GlassChrome(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        borderColor = null,
        borderWidth = 0.dp,
        shadowElevation = 6.dp,
        gradient = null,
    )
    ToolClusterStyle.Minimal -> GlassChrome(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = null,
        borderWidth = 0.dp,
        shadowElevation = 4.dp,
        gradient = null,
    )
}
