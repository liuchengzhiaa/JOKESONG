package com.example.asmrplayer.ui.lyrics

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.asmrplayer.R
import com.example.asmrplayer.feature.subtitles.LuminousBreathingFloatRange
import com.example.asmrplayer.feature.subtitles.LuminousTuning
import com.example.asmrplayer.feature.subtitles.LuminousWordSpacingRange
import com.example.asmrplayer.feature.subtitles.LyricsVisualizerBackground
import com.example.asmrplayer.feature.subtitles.LyricsVisualizerMode
import com.example.asmrplayer.feature.subtitles.LyricsWordGlowMode
import com.example.asmrplayer.feature.subtitles.PendoloActiveScaleRange
import com.example.asmrplayer.feature.subtitles.PendoloArcAngleRange
import com.example.asmrplayer.feature.subtitles.PendoloArcRadiusRange
import com.example.asmrplayer.feature.subtitles.PendoloGearDecor
import com.example.asmrplayer.feature.subtitles.PendoloTickSnappinessRange
import com.example.asmrplayer.feature.subtitles.PendoloTuning
import com.example.asmrplayer.feature.subtitles.PendoloWheelCenterXRange
import com.example.asmrplayer.feature.subtitles.PendoloWheelCenterYRange
import com.example.asmrplayer.feature.subtitles.TemperaArtworkSource
import com.example.asmrplayer.feature.subtitles.TemperaColorMode
import com.example.asmrplayer.feature.subtitles.TemperaImageDepth
import com.example.asmrplayer.feature.subtitles.TemperaStageAspect
import com.example.asmrplayer.feature.subtitles.TemperaTuning
import kotlin.math.roundToInt

/** One Folia-shaped settings surface for the whole immersive lyric experience. */
@Composable
internal fun LyricsSettingsPanel(
    visualizerMode: LyricsVisualizerMode,
    background: LyricsVisualizerBackground,
    backgroundFollowsMode: Boolean,
    motionPercent: Int,
    wordGlowMode: LyricsWordGlowMode,
    luminous: LuminousTuning,
    pendolo: PendoloTuning,
    tempera: TemperaTuning,
    onVisualizerModeChange: (LyricsVisualizerMode) -> Unit,
    onBackgroundChange: (LyricsVisualizerBackground?) -> Unit,
    onMotionPercentChange: (Int) -> Unit,
    onWordGlowModeChange: (LyricsWordGlowMode) -> Unit,
    onLuminousPreview: (LuminousTuning) -> Unit,
    onPendoloPreview: (PendoloTuning) -> Unit,
    onTemperaPreview: (TemperaTuning) -> Unit,
    onLuminousChange: (LuminousTuning) -> Unit,
    onPendoloChange: (PendoloTuning) -> Unit,
    onTemperaChange: (TemperaTuning) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(LyricsSettingsTab.Animation) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.46f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 420.dp).fillMaxHeight(),
                color = PanelSurface,
                contentColor = PanelPrimaryText,
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                tonalElevation = 0.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    PanelHeader(onDismiss)
                    SettingsTabs(selected = tab, onSelect = { tab = it })
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (tab) {
                            LyricsSettingsTab.General -> GeneralSettings(
                                motionPercent,
                                onMotionPercentChange,
                            )
                            LyricsSettingsTab.Background -> BackgroundSettings(
                                background,
                                backgroundFollowsMode,
                                onBackgroundChange,
                            )
                            LyricsSettingsTab.Animation -> AnimationSettings(
                                mode = visualizerMode,
                                luminous = luminous,
                                pendolo = pendolo,
                                tempera = tempera,
                                onModeChange = onVisualizerModeChange,
                                onLuminousPreview = onLuminousPreview,
                                onPendoloPreview = onPendoloPreview,
                                onTemperaPreview = onTemperaPreview,
                                onLuminousChange = onLuminousChange,
                                onPendoloChange = onPendoloChange,
                                onTemperaChange = onTemperaChange,
                            )
                            LyricsSettingsTab.Subtitles -> SubtitleSettings(
                                wordGlowMode,
                                onWordGlowModeChange,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.lyrics_settings_title), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.lyrics_settings_description), color = PanelSecondaryText, fontSize = 11.sp)
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, stringResource(R.string.close))
        }
    }
}

@Composable
private fun SettingsTabs(selected: LyricsSettingsTab, onSelect: (LyricsSettingsTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LyricsSettingsTab.entries.forEach { tab ->
            val active = tab == selected
            Text(
                text = stringResource(tab.label),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) PanelAccent.copy(alpha = 0.18f) else PanelCard)
                    .border(1.dp, if (active) PanelAccent.copy(alpha = 0.72f) else Color.Transparent, CircleShape)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                color = if (active) PanelPrimaryText else PanelSecondaryText,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun GeneralSettings(motionPercent: Int, onMotionPercentChange: (Int) -> Unit) {
    SectionHeading(R.string.lyrics_settings_general, R.string.lyrics_settings_general_help)
    TuningSliderRow(
        label = stringResource(R.string.wall_motion_strength),
        valueText = stringResource(R.string.percent_value, motionPercent),
        value = motionPercent,
        range = 0..100,
        onPreview = {},
        onCommit = onMotionPercentChange,
    )
}

@Composable
private fun BackgroundSettings(
    background: LyricsVisualizerBackground,
    followsMode: Boolean,
    onBackgroundChange: (LyricsVisualizerBackground?) -> Unit,
) {
    SectionHeading(R.string.lyrics_settings_background, R.string.lyrics_settings_background_help)
    ChoiceCard(
        label = stringResource(R.string.lyrics_background_choose),
        options = listOf(
            Choice(stringResource(R.string.lyrics_background_follow_mode), followsMode) { onBackgroundChange(null) },
        ) + LyricsVisualizerBackground.entries.map { candidate ->
            Choice(stringResource(candidate.labelResource()), !followsMode && candidate == background) {
                onBackgroundChange(candidate)
            }
        },
    )
}

@Composable
private fun AnimationSettings(
    mode: LyricsVisualizerMode,
    luminous: LuminousTuning,
    pendolo: PendoloTuning,
    tempera: TemperaTuning,
    onModeChange: (LyricsVisualizerMode) -> Unit,
    onLuminousPreview: (LuminousTuning) -> Unit,
    onPendoloPreview: (PendoloTuning) -> Unit,
    onTemperaPreview: (TemperaTuning) -> Unit,
    onLuminousChange: (LuminousTuning) -> Unit,
    onPendoloChange: (PendoloTuning) -> Unit,
    onTemperaChange: (TemperaTuning) -> Unit,
) {
    SectionHeading(R.string.lyrics_settings_animation, R.string.lyrics_settings_animation_help)
    ChoiceCard(
        label = stringResource(R.string.lyrics_visualizer_choose),
        options = LyricsVisualizerMode.entries.map { candidate ->
            Choice(stringResource(candidate.labelResource()), candidate == mode) { onModeChange(candidate) }
        },
    )
    when (mode) {
        LyricsVisualizerMode.Luminous -> LuminousRows(luminous, onLuminousPreview, onLuminousChange)
        LyricsVisualizerMode.Pendolo -> PendoloRows(pendolo, onPendoloPreview, onPendoloChange)
        LyricsVisualizerMode.Claddagh -> TuningCard {
            Text(
                stringResource(R.string.lyrics_claddagh_no_parameters),
                color = PanelSecondaryText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        LyricsVisualizerMode.Tempera -> TemperaRows(tempera, onTemperaPreview, onTemperaChange)
    }
}

@Composable
private fun SubtitleSettings(
    wordGlowMode: LyricsWordGlowMode,
    onWordGlowModeChange: (LyricsWordGlowMode) -> Unit,
) {
    SectionHeading(R.string.lyrics_settings_subtitles, R.string.lyrics_settings_subtitles_help)
    ChoiceCard(
        label = stringResource(R.string.lyrics_word_glow),
        options = LyricsWordGlowMode.entries.map { candidate ->
            Choice(stringResource(candidate.labelResource()), candidate == wordGlowMode) {
                onWordGlowModeChange(candidate)
            }
        },
    )
}

@Composable
private fun TemperaRows(
    tuning: TemperaTuning,
    onPreview: (TemperaTuning) -> Unit,
    onChange: (TemperaTuning) -> Unit,
) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (uris.isNotEmpty()) {
            onChange(
                tuning.copy(
                    artworkSource = TemperaArtworkSource.ImageLibrary,
                    imageUris = (tuning.imageUris + uris.map { it.toString() }).distinct(),
                ),
            )
        }
    }
    ChoiceCard(
        label = stringResource(R.string.lyrics_tempera_artwork_source),
        options = TemperaArtworkSource.entries.map { source ->
            Choice(stringResource(source.labelResource()), source == tuning.artworkSource) {
                onChange(tuning.copy(artworkSource = source))
            }
        },
    )
    ChoiceCard(
        label = stringResource(R.string.lyrics_tempera_stage_aspect),
        options = TemperaStageAspect.entries.map { aspect ->
            Choice(stringResource(aspect.labelResource()), aspect == tuning.stageAspect) {
                onChange(tuning.copy(stageAspect = aspect))
            }
        },
    )
    TuningCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.lyrics_tempera_image_library), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.lyrics_tempera_image_count, tuning.imageUris.size),
                    color = PanelSecondaryText,
                    fontSize = 11.sp,
                )
            }
            if (tuning.imageUris.isNotEmpty()) {
                IconButton(onClick = { onChange(tuning.copy(imageUris = emptyList())) }) {
                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.clear))
                }
            }
        }
        if (tuning.imageUris.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tuning.imageUris.take(8).forEach { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
        TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
            Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(stringResource(R.string.lyrics_tempera_choose_images))
        }
    }
    TuningSliderRow(
        label = stringResource(R.string.lyrics_tempera_image_frequency),
        valueText = stringResource(R.string.percent_value, tuning.imageFrequencyPercent),
        value = tuning.imageFrequencyPercent,
        range = 0..100,
        onPreview = { onPreview(tuning.copy(imageFrequencyPercent = it)) },
        onCommit = { onChange(tuning.copy(imageFrequencyPercent = it)) },
    )
    ChoiceCard(
        label = stringResource(R.string.lyrics_tempera_image_depth),
        options = TemperaImageDepth.entries.map { depth ->
            Choice(stringResource(depth.labelResource()), depth == tuning.imageDepth) {
                onChange(tuning.copy(imageDepth = depth))
            }
        },
    )
    TuningSliderRow(
        label = stringResource(R.string.lyrics_tempera_camera_intensity),
        valueText = stringResource(R.string.percent_value, tuning.cameraIntensityPercent),
        value = tuning.cameraIntensityPercent,
        range = 0..200,
        onPreview = { onPreview(tuning.copy(cameraIntensityPercent = it)) },
        onCommit = { onChange(tuning.copy(cameraIntensityPercent = it)) },
    )
    TuningSliderRow(
        label = stringResource(R.string.lyrics_tempera_glyph_motion),
        valueText = stringResource(R.string.percent_value, tuning.glyphMotionPercent),
        value = tuning.glyphMotionPercent,
        range = 0..200,
        onPreview = { onPreview(tuning.copy(glyphMotionPercent = it)) },
        onCommit = { onChange(tuning.copy(glyphMotionPercent = it)) },
    )
    TuningSliderRow(
        label = stringResource(R.string.lyrics_tempera_settle_timing),
        valueText = stringResource(R.string.percent_value, tuning.glyphSettleStretchPercent),
        value = tuning.glyphSettleStretchPercent,
        range = 0..100,
        onPreview = { onPreview(tuning.copy(glyphSettleStretchPercent = it)) },
        onCommit = { onChange(tuning.copy(glyphSettleStretchPercent = it)) },
    )
    ChoiceCard(
        label = stringResource(R.string.lyrics_tempera_color_mode),
        options = TemperaColorMode.entries.map { mode ->
            Choice(stringResource(mode.labelResource()), mode == tuning.colorMode) {
                onChange(tuning.copy(colorMode = mode))
            }
        },
    )
    ToggleRow(stringResource(R.string.lyrics_tempera_whole_line), tuning.wholeLineLyrics) {
        onChange(tuning.copy(wholeLineLyrics = it))
    }
    ToggleRow(stringResource(R.string.lyrics_tempera_show_blocks), tuning.showBlocks) {
        onChange(tuning.copy(showBlocks = it))
    }
    ToggleRow(stringResource(R.string.lyrics_tempera_show_decor), tuning.showDecor) {
        onChange(tuning.copy(showDecor = it))
    }
    ToggleRow(stringResource(R.string.lyrics_tempera_corner_marks), tuning.showCornerMarks) {
        onChange(tuning.copy(showCornerMarks = it))
    }
    ToggleRow(stringResource(R.string.lyrics_tempera_text_inversion), tuning.textInversion) {
        onChange(tuning.copy(textInversion = it))
    }
    ToggleRow(stringResource(R.string.lyrics_tempera_transitions), tuning.enableTransitions) {
        onChange(tuning.copy(enableTransitions = it))
    }
}

@Composable
private fun LuminousRows(
    tuning: LuminousTuning,
    onPreview: (LuminousTuning) -> Unit,
    onChange: (LuminousTuning) -> Unit,
) {
    TuningSliderRow(
        stringResource(R.string.lyrics_tuning_breathing_float),
        stringResource(R.string.percent_value, tuning.breathingFloatPercent),
        tuning.breathingFloatPercent,
        LuminousBreathingFloatRange,
        { onPreview(tuning.copy(breathingFloatPercent = it)) },
        { onChange(tuning.copy(breathingFloatPercent = it)) },
    )
    TuningSliderRow(
        stringResource(R.string.lyrics_tuning_word_spacing),
        stringResource(R.string.percent_value, tuning.wordSpacingPercent),
        tuning.wordSpacingPercent,
        LuminousWordSpacingRange,
        { onPreview(tuning.copy(wordSpacingPercent = it)) },
        { onChange(tuning.copy(wordSpacingPercent = it)) },
    )
    ToggleRow(stringResource(R.string.lyrics_tuning_word_rotation), tuning.wordRotation) {
        onChange(tuning.copy(wordRotation = it))
    }
    ResetRow { onChange(LuminousTuning()) }
}

@Composable
private fun PendoloRows(
    tuning: PendoloTuning,
    onPreview: (PendoloTuning) -> Unit,
    onChange: (PendoloTuning) -> Unit,
) {
    TuningSliderRow(
        stringResource(R.string.lyrics_tuning_wheel_center_x),
        tuning.wheelCenterXPerMille.toString(),
        tuning.wheelCenterXPerMille,
        PendoloWheelCenterXRange,
        { onPreview(tuning.copy(wheelCenterXPerMille = it)) },
        { onChange(tuning.copy(wheelCenterXPerMille = it)) },
    )
    TuningSliderRow(
        stringResource(R.string.lyrics_tuning_wheel_center_y),
        tuning.wheelCenterYPerMille.toString(),
        tuning.wheelCenterYPerMille,
        PendoloWheelCenterYRange,
        { onPreview(tuning.copy(wheelCenterYPerMille = it)) },
        { onChange(tuning.copy(wheelCenterYPerMille = it)) },
    )
    TuningSliderRow(
        stringResource(R.string.lyrics_tuning_arc_radius),
        stringResource(R.string.percent_value, tuning.arcRadiusPercent),
        tuning.arcRadiusPercent,
        PendoloArcRadiusRange,
        { onPreview(tuning.copy(arcRadiusPercent = it)) },
        { onChange(tuning.copy(arcRadiusPercent = it)) },
    )
    TuningSliderRow(
        stringResource(R.string.lyrics_tuning_arc_angle),
        stringResource(R.string.degrees_value, tuning.arcAngleDegrees),
        tuning.arcAngleDegrees,
        PendoloArcAngleRange,
        { onPreview(tuning.copy(arcAngleDegrees = it)) },
        { onChange(tuning.copy(arcAngleDegrees = it)) },
    )
    TuningSliderRow(
        stringResource(R.string.lyrics_tuning_tick_snappiness),
        formatTenths(tuning.tickSnappinessPerTen),
        tuning.tickSnappinessPerTen,
        PendoloTickSnappinessRange,
        { onPreview(tuning.copy(tickSnappinessPerTen = it)) },
        { onChange(tuning.copy(tickSnappinessPerTen = it)) },
    )
    TuningSliderRow(
        stringResource(R.string.lyrics_tuning_active_scale),
        stringResource(R.string.percent_value, tuning.activeScalePercent),
        tuning.activeScalePercent,
        PendoloActiveScaleRange,
        { onPreview(tuning.copy(activeScalePercent = it)) },
        { onChange(tuning.copy(activeScalePercent = it)) },
    )
    ChoiceCard(
        stringResource(R.string.lyrics_tuning_gear_decor),
        PendoloGearDecor.entries.map { decor ->
            Choice(stringResource(decor.labelResource()), decor == tuning.gearDecor) {
                onChange(tuning.copy(gearDecor = decor))
            }
        },
    )
    ToggleRow(stringResource(R.string.lyrics_tuning_centre_gradient), tuning.centreGradient) {
        onChange(tuning.copy(centreGradient = it))
    }
    ToggleRow(stringResource(R.string.lyrics_tuning_cover_on_dial), tuning.coverOnDial) {
        onChange(tuning.copy(coverOnDial = it))
    }
    ToggleRow(stringResource(R.string.lyrics_tuning_line_glow), tuning.lineGlow) {
        onChange(tuning.copy(lineGlow = it))
    }
    ResetRow { onChange(PendoloTuning()) }
}

@Composable
private fun SectionHeading(title: Int, help: Int) {
    Text(stringResource(title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Text(stringResource(help), color = PanelSecondaryText, fontSize = 11.sp, lineHeight = 16.sp)
}

@Composable
private fun TuningSliderRow(
    label: String,
    valueText: String,
    value: Int,
    range: IntRange,
    onPreview: (Int) -> Unit,
    onCommit: (Int) -> Unit,
) {
    var pending by remember(label, range.first, range.last) { mutableIntStateOf(value) }
    var dragging by remember(label, range.first, range.last) { mutableStateOf(false) }
    LaunchedEffect(value, dragging) { if (!dragging) pending = value }
    TuningCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(valueText, color = PanelAccent, fontSize = 13.sp)
        }
        Slider(
            value = pending.toFloat(),
            onValueChange = {
                dragging = true
                pending = it.roundToInt().coerceIn(range)
                onPreview(pending)
            },
            onValueChangeFinished = {
                if (dragging) onCommit(pending)
                dragging = false
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = PanelAccent, activeTrackColor = PanelAccent),
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    TuningCard(Modifier.clickable { onCheckedChange(!checked) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (checked) Icon(Icons.Default.Check, null, tint = PanelAccent)
        }
    }
}

@Composable
private fun ChoiceCard(label: String, options: List<Choice>) {
    TuningCard {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        options.forEach { option ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = option.onSelect).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    option.label,
                    modifier = Modifier.weight(1f),
                    color = if (option.selected) PanelPrimaryText else PanelSecondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (option.selected) Icon(Icons.Default.Check, null, tint = PanelAccent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ResetRow(onReset: () -> Unit) {
    TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.lyrics_tuning_reset))
    }
}

@Composable
private fun TuningCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().background(PanelCard, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) { content() }
}

private data class Choice(val label: String, val selected: Boolean, val onSelect: () -> Unit)

private enum class LyricsSettingsTab(val label: Int) {
    General(R.string.lyrics_settings_general),
    Background(R.string.lyrics_settings_background),
    Animation(R.string.lyrics_settings_animation),
    Subtitles(R.string.lyrics_settings_subtitles),
}

private fun PendoloGearDecor.labelResource(): Int = when (this) {
    PendoloGearDecor.None -> R.string.lyrics_tuning_gear_none
    PendoloGearDecor.Subtle -> R.string.lyrics_tuning_gear_subtle
    PendoloGearDecor.Full -> R.string.lyrics_tuning_gear_full
}

private fun LyricsWordGlowMode.labelResource(): Int = when (this) {
    LyricsWordGlowMode.Off -> R.string.lyrics_word_glow_off
    LyricsWordGlowMode.Smart -> R.string.lyrics_word_glow_smart
    LyricsWordGlowMode.ExactOnly -> R.string.lyrics_word_glow_exact
}

private fun TemperaArtworkSource.labelResource(): Int = when (this) {
    TemperaArtworkSource.Cover -> R.string.lyrics_tempera_source_cover
    TemperaArtworkSource.ImageLibrary -> R.string.lyrics_tempera_source_library
}

private fun TemperaStageAspect.labelResource(): Int = when (this) {
    TemperaStageAspect.FullScreen -> R.string.lyrics_tempera_aspect_full
    TemperaStageAspect.Square -> R.string.lyrics_tempera_aspect_square
    TemperaStageAspect.LandscapeFourThree -> R.string.lyrics_tempera_aspect_four_three
}

private fun TemperaColorMode.labelResource(): Int = when (this) {
    TemperaColorMode.Duo -> R.string.lyrics_tempera_color_duo
    TemperaColorMode.Mono -> R.string.lyrics_tempera_color_mono
    TemperaColorMode.CoverGradient -> R.string.lyrics_tempera_color_gradient
}

private fun TemperaImageDepth.labelResource(): Int = when (this) {
    TemperaImageDepth.BehindType -> R.string.lyrics_tempera_image_behind
    TemperaImageDepth.InFrontOfType -> R.string.lyrics_tempera_image_front
}

private fun formatTenths(tenths: Int): String =
    if (tenths % 10 == 0) (tenths / 10).toString() else (tenths / 10f).toString()

private val PanelSurface = Color(0xFF11151C)
private val PanelCard = Color.White.copy(alpha = 0.055f)
private val PanelPrimaryText = Color(0xFFF4F6FA)
private val PanelSecondaryText = PanelPrimaryText.copy(alpha = 0.58f)
private val PanelAccent = Color(0xFF86D6FF)
