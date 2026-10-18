package com.example.asmrplayer.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.example.asmrplayer.R
import com.example.asmrplayer.core.overlay.DesktopLyricsSettings
import com.example.asmrplayer.core.overlay.LyricColorHsv
import com.example.asmrplayer.core.storage.AppPreferences
import com.example.asmrplayer.core.storage.AppSettings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun DesktopLyricsPreferences() {
    val context = LocalContext.current
    val prefs = remember(context) { AppPreferences(context.applicationContext) }
    val state by prefs.settings.collectAsStateWithLifecycle(AppSettings())
    val s = state.desktopLyrics
    val scope = rememberCoroutineScope()
    var permission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var permissionFailure by remember { mutableStateOf(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permission = Settings.canDrawOverlays(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun save(value: DesktopLyricsSettings) { scope.launch { prefs.setDesktopLyrics(value) } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permission = Settings.canDrawOverlays(context)
        scope.launch { prefs.setDesktopLyricsEnabled(permission) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.desktop_description), style = MaterialTheme.typography.bodySmall)
        DesktopSwitch(R.string.desktop_enabled, s.enabled && permission) { enabled ->
            permission = Settings.canDrawOverlays(context)
            if (!enabled || permission) save(s.copy(enabled = enabled)) else {
                try {
                    permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri()))
                } catch (_: android.content.ActivityNotFoundException) { permissionFailure = true }
            }
        }
        if (!permission) Text(stringResource(R.string.desktop_permission), style = MaterialTheme.typography.bodySmall)
        if (permissionFailure) Text(stringResource(R.string.desktop_permission_manual), color = MaterialTheme.colorScheme.error)
        // Preview complements the live overlay, which now remains attached inside this Activity.
        Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = s.backgroundPercent / 100f),
            RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(s.lineCount) { index ->
                Text(stringResource(if (index == s.lineCount / 2) R.string.desktop_preview_active else R.string.desktop_preview_context),
                    color = Color(if (index == s.lineCount / 2) s.highlightArgb else s.textArgb).copy(alpha = if (index == s.lineCount / 2) 1f else .55f),
                    fontSize = s.fontSizeSp.sp,
                    fontWeight = if (s.bold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !s.wordByWord, onClick = { save(s.copy(wordByWord = false)) }, label = { Text(stringResource(R.string.desktop_line_mode)) })
            FilterChip(selected = s.wordByWord, onClick = { save(s.copy(wordByWord = true)) }, label = { Text(stringResource(R.string.desktop_word_mode)) })
        }
        if (s.wordByWord) Text(stringResource(R.string.desktop_word_explanation), style = MaterialTheme.typography.bodySmall)
        DesktopSlider(R.string.desktop_font_size, s.fontSizeSp, 12f..36f) { save(s.copy(fontSizeSp = it)) }
        DesktopSlider(R.string.desktop_line_count, s.lineCount, 1f..4f, steps = 2) { save(s.copy(lineCount = it)) }
        DesktopSlider(R.string.desktop_background, s.backgroundPercent, 0f..80f) { save(s.copy(backgroundPercent = it)) }
        FilterChip(selected = s.backgroundPercent == 0, onClick = { save(s.copy(backgroundPercent = 0)) },
            label = { Text(stringResource(R.string.desktop_transparent)) })
        Text(stringResource(R.string.desktop_transparent_hint), style = MaterialTheme.typography.bodySmall)
        DesktopColorPalette(R.string.desktop_text_color, s.textArgb, DesktopLyricsSettings().textArgb) { save(s.copy(textArgb = it)) }
        DesktopColorPalette(R.string.desktop_highlight_color, s.highlightArgb, DesktopLyricsSettings().highlightArgb) { save(s.copy(highlightArgb = it)) }
        DesktopSwitch(R.string.desktop_bold, s.bold) { save(s.copy(bold = it)) }
        DesktopSwitch(R.string.desktop_locked, s.locked) { save(s.copy(locked = it)) }
        Text(stringResource(R.string.desktop_lock_explanation), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { save(s.copy(xPerMille = 500, yPerMille = 200)) }) { Text(stringResource(R.string.desktop_reset_position)) }
    }
}

@Composable
private fun DesktopSwitch(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DesktopSlider(label: Int, value: Int, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onCommit: (Int) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text(stringResource(label, draft.roundToInt()))
        Slider(value = draft, onValueChange = { draft = it }, valueRange = range, steps = steps,
            onValueChangeFinished = { onCommit(draft.roundToInt()) })
    }
}

@Composable
private fun DesktopColorPalette(label: Int, argb: Int, defaultArgb: Int, onCommit: (Int) -> Unit) {
    var hsv by remember { mutableStateOf(LyricColorHsv.fromArgb(argb)) }
    LaunchedEffect(argb) { if (hsv.toArgb() != argb) hsv = LyricColorHsv.fromArgb(argb) }
    val color = Color(hsv.toArgb())
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(24.dp).background(color, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                Text(stringResource(label), modifier = Modifier.weight(1f))
                TextButton(onClick = { hsv = LyricColorHsv.fromArgb(defaultArgb); onCommit(defaultArgb) }) {
                    Text(stringResource(R.string.desktop_reset_color))
                }
            }
            DesktopColorSlider(R.string.desktop_hue, hsv.hue, 0f..360f,
                Brush.horizontalGradient((0..6).map { Color(LyricColorHsv(it * 60f, 1f, 1f).toArgb()) }),
                color, stringResource(R.string.desktop_hue_value, hsv.hue.roundToInt()),
                { hsv = hsv.copy(hue = it) }, { onCommit(hsv.toArgb()) })
            DesktopColorSlider(R.string.desktop_saturation, hsv.saturation, 0f..1f,
                Brush.horizontalGradient(listOf(Color(hsv.copy(saturation = 0f).toArgb()), Color(hsv.copy(saturation = 1f).toArgb()))),
                color, stringResource(R.string.desktop_color_percent, (hsv.saturation * 100).roundToInt()),
                { hsv = hsv.copy(saturation = it) }, { onCommit(hsv.toArgb()) })
            DesktopColorSlider(R.string.desktop_brightness, hsv.value, 0f..1f,
                Brush.horizontalGradient(listOf(Color.Black, Color(hsv.copy(value = 1f).toArgb()))),
                color, stringResource(R.string.desktop_color_percent, (hsv.value * 100).roundToInt()),
                { hsv = hsv.copy(value = it) }, { onCommit(hsv.toArgb()) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0xFFFFFFFF, 0xFF9CD9FF, 0xFFFFD477, 0xFFFFA8CB, 0xFFA4E6BC, 0xFFFF7777).forEachIndexed { index, value ->
                    val description = stringResource(R.string.desktop_color_preset, index + 1)
                    IconButton(onClick = { hsv = LyricColorHsv.fromArgb(value.toInt()); onCommit(value.toInt()) },
                        modifier = Modifier.size(40.dp).semantics { contentDescription = description }) {
                        Box(Modifier.size(22.dp).background(Color(value.toInt()), CircleShape)
                            .border(if (hsv.toArgb() == value.toInt()) 2.dp else 1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopColorSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>,
    gradient: Brush, color: Color, valueLabel: String, onChange: (Float) -> Unit, onCommit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(label), modifier = Modifier.width(38.dp), style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onCommit, valueRange = range,
            modifier = Modifier.weight(1f),
            thumb = { Box(Modifier.size(20.dp).background(color, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)) },
            track = { Box(Modifier.fillMaxWidth().height(8.dp).background(gradient, RoundedCornerShape(4.dp))) })
        Text(valueLabel, modifier = Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall)
    }
}
