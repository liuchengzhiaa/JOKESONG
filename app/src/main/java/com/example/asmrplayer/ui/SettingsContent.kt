package com.example.asmrplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.R
import com.example.asmrplayer.core.settings.UiAccentPalette
import com.example.asmrplayer.core.settings.UiStylePreset
import com.example.asmrplayer.feature.library.LibraryUiState
import com.example.asmrplayer.feature.subtitles.LyricsWordGlowMode
import com.example.asmrplayer.ui.theme.accent
import com.example.asmrplayer.ui.theme.accentContentColor
import kotlin.math.roundToInt

/**
 * The settings surface.
 *
 * It is a short list of groups rather than one long page of controls, because
 * appearance is one group among several and a settings screen whose first screen
 * is entirely about colour reads as if that were all there is to set. Groups open
 * on tap and one is open at a time; nothing is open on arrival, so the first thing
 * the page answers is what can be set here.
 */
@Composable
fun SettingsScreen(
    state: LibraryUiState,
    onChooseRoot: () -> Unit,
    onRefresh: () -> Unit,
    onUiStylePresetChange: (Int) -> Unit,
    onUiAccentPaletteChange: (Int) -> Unit,
    onMotionPercentChange: (Int) -> Unit,
    onUiScalePercentChange: (Int) -> Unit,
    onLyricsWordGlowModeChange: (Int) -> Unit,
    onResetAppearanceDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Each group owns the state it renders. Opening one closes the others so the
    // settings root remains a short index rather than three nested screens at once.
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    var libraryOpen by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            SettingsGroupRow(
                titleRes = R.string.settings_appearance,
                summaryRes = R.string.settings_appearance_summary,
                icon = Icons.Default.Palette,
                expanded = appearanceOpen,
                onToggle = {
                    appearanceOpen = !appearanceOpen
                    lyricsOpen = false
                    libraryOpen = false
                },
            )
        }
        if (appearanceOpen) {
            item {
                AppearanceSettingsSection(
                    state = state,
                    onUiStylePresetChange = onUiStylePresetChange,
                    onUiAccentPaletteChange = onUiAccentPaletteChange,
                    onMotionPercentChange = onMotionPercentChange,
                    onUiScalePercentChange = onUiScalePercentChange,
                    onResetAppearanceDefaults = onResetAppearanceDefaults,
                )
            }
        }
        item {
            SettingsGroupRow(
                titleRes = R.string.settings_lyrics,
                summaryRes = R.string.settings_lyrics_summary,
                icon = Icons.Default.Subtitles,
                expanded = lyricsOpen,
                onToggle = {
                    lyricsOpen = !lyricsOpen
                    appearanceOpen = false
                    libraryOpen = false
                },
            )
        }
        if (lyricsOpen) {
            item {
                WordGlowModeSettings(
                    selectedMode = LyricsWordGlowMode.fromOrdinal(
                        state.lyricsWordGlowModeOrdinal,
                    ),
                    onModeChange = { onLyricsWordGlowModeChange(it.ordinal) },
                )
            }
        }
        item {
            SettingsGroupRow(
                titleRes = R.string.settings_library,
                summaryRes = R.string.settings_library_summary,
                icon = Icons.Default.FolderOpen,
                expanded = libraryOpen,
                onToggle = {
                    libraryOpen = !libraryOpen
                    appearanceOpen = false
                    lyricsOpen = false
                },
            )
        }
        if (libraryOpen) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsActionRow(
                        icon = Icons.Default.FolderOpen,
                        labelRes = R.string.choose_library_root,
                        onClick = onChooseRoot,
                    )
                    SettingsActionRow(
                        icon = Icons.Default.Refresh,
                        labelRes = R.string.refresh_current_folder,
                        enabled = state.hasRoot && !state.isScanning,
                        onClick = onRefresh,
                    )
                }
            }
        }
    }
}

@Composable
private fun WordGlowModeSettings(
    selectedMode: LyricsWordGlowMode,
    onModeChange: (LyricsWordGlowMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Subtitles,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.lyrics_word_glow),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.lyrics_word_glow_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        LyricsWordGlowMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeChange(mode) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onModeChange(mode) },
                    )
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            stringResource(mode.wordGlowLabelResource()),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(mode.wordGlowDescriptionResource()),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun LyricsWordGlowMode.wordGlowLabelResource(): Int = when (this) {
    LyricsWordGlowMode.Off -> R.string.lyrics_word_glow_off
    LyricsWordGlowMode.Smart -> R.string.lyrics_word_glow_smart
    LyricsWordGlowMode.ExactOnly -> R.string.lyrics_word_glow_exact
}

private fun LyricsWordGlowMode.wordGlowDescriptionResource(): Int = when (this) {
    LyricsWordGlowMode.Off -> R.string.lyrics_word_glow_off_description
    LyricsWordGlowMode.Smart -> R.string.lyrics_word_glow_smart_description
    LyricsWordGlowMode.ExactOnly -> R.string.lyrics_word_glow_exact_description
}

/**
 * A group header. The chevron turns rather than swapping glyphs, so the row's
 * meaning and its position do not change while it opens.
 */
@Composable
private fun SettingsGroupRow(
    titleRes: Int,
    summaryRes: Int,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (expanded) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(summaryRes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                // Decorative: the row's own label already names it, and the open
                // state is visible in the panel underneath.
                contentDescription = null,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One immediate action inside an open group. */
@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.38f
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = stringResource(labelRes),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/**
 * Global appearance controls shared by navigation, chrome and wall tools. A plain
 * column: the settings list is the scroller, and a second one nested inside it
 * would take the gesture the page needs.
 */
@Composable
private fun AppearanceSettingsSection(
    state: LibraryUiState,
    onUiStylePresetChange: (Int) -> Unit,
    onUiAccentPaletteChange: (Int) -> Unit,
    onMotionPercentChange: (Int) -> Unit,
    onUiScalePercentChange: (Int) -> Unit,
    onResetAppearanceDefaults: () -> Unit,
) {
    val selectedPreset = UiStylePreset.fromOrdinal(state.uiStylePresetOrdinal)
    val selectedAccent = UiAccentPalette.fromOrdinal(state.uiAccentPaletteOrdinal)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = stringResource(R.string.appearance_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsSectionTitle(R.string.ui_style_preset)
        UiStylePreset.entries.filterNot { it == UiStylePreset.Custom }.forEach { preset ->
            val selected = preset == selectedPreset
            val containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUiStylePresetChange(preset.ordinal) },
                colors = CardDefaults.cardColors(containerColor = containerColor),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(preset.titleResource()),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.style_preset_summary,
                                preset.motionPercent,
                                preset.uiScalePercent,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.layout_selected),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        if (selectedPreset == UiStylePreset.Custom) {
            Text(
                text = stringResource(R.string.ui_style_custom_active),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        SettingsSectionTitle(R.string.ui_accent_color)
        OutlinedCard(shape = RoundedCornerShape(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiAccentPalette.entries.forEach { palette ->
                    AccentChoice(
                        palette = palette,
                        selected = palette == selectedAccent,
                        onClick = { onUiAccentPaletteChange(palette.ordinal) },
                    )
                }
            }
        }
        AppearancePercentControl(
            title = stringResource(R.string.ui_motion_strength),
            description = stringResource(R.string.ui_motion_description),
            valuePercent = state.motionPercent,
            valueRange = 0..100,
            onValueCommit = onMotionPercentChange,
        )
        AppearancePercentControl(
            title = stringResource(R.string.ui_scale),
            description = stringResource(R.string.ui_scale_description),
            valuePercent = state.uiScalePercent,
            valueRange = 85..120,
            onValueCommit = onUiScalePercentChange,
        )
        Button(
            onClick = onResetAppearanceDefaults,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null)
            Text(
                text = stringResource(R.string.reset_appearance_defaults),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun AccentChoice(
    palette: UiAccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // The swatch shows the variant this theme actually uses, so what is picked here
    // is what the top bar and navigation bar will be — not an approximation of it.
    val color = palette.accent(darkTheme = isSystemInDarkTheme())
    Box(
        modifier = Modifier
            .size(if (selected) 48.dp else 42.dp)
            .background(color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(palette.titleResource()),
                tint = accentContentColor(color),
            )
        }
    }
}

@Composable
private fun AppearancePercentControl(
    title: String,
    description: String,
    valuePercent: Int,
    valueRange: IntRange,
    onValueCommit: (Int) -> Unit,
) {
    var pendingPercent by remember(valuePercent) { mutableIntStateOf(valuePercent) }
    OutlinedCard(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.percent_value, pendingPercent),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = pendingPercent.toFloat(),
                onValueChange = {
                    pendingPercent = it.roundToInt().coerceIn(valueRange)
                },
                onValueChangeFinished = { onValueCommit(pendingPercent) },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            )
        }
    }
}

private fun UiStylePreset.titleResource(): Int = when (this) {
    UiStylePreset.Balanced -> R.string.style_balanced
    UiStylePreset.Calm -> R.string.style_calm
    UiStylePreset.Compact -> R.string.style_compact
    UiStylePreset.Vivid -> R.string.style_vivid
    UiStylePreset.Custom -> R.string.style_custom
}

private fun UiAccentPalette.titleResource(): Int = when (this) {
    UiAccentPalette.Ocean -> R.string.accent_ocean
    UiAccentPalette.Lavender -> R.string.accent_lavender
    UiAccentPalette.Amber -> R.string.accent_amber
    UiAccentPalette.Mint -> R.string.accent_mint
    UiAccentPalette.Rose -> R.string.accent_rose
}
