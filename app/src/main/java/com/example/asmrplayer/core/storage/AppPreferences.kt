package com.example.asmrplayer.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.asmrplayer.core.settings.UiAccentPalette
import com.example.asmrplayer.core.settings.UiStylePreset
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.appPreferences by preferencesDataStore(name = "wall_presentation_preferences")

/** Process-durable defaults and global presentation choices. */
data class AppSettings(
    val libraryLayoutOrdinal: Int = DefaultLibraryLayoutOrdinal,
    val wallZoomPercent: Int = 100,
    val wallDensityPercent: Int = 100,
    val wallFocusShapeOrdinal: Int = 0,
    val wallFocusSizeOrdinal: Int = 1,
    val wallAmbientPercent: Int = 100,
    val wallLightsOut: Boolean = false,
    val wallAutoFocusPlaying: Boolean = false,
    val wallPosterTintPercent: Int = 0,
    val motionPercent: Int = 100,
    val uiStylePresetOrdinal: Int = UiStylePreset.Balanced.ordinal,
    val uiAccentPaletteOrdinal: Int = UiAccentPalette.Ocean.ordinal,
    val uiScalePercent: Int = 100,
) {
    companion object {
        // LibraryLayoutMode.FocusWall.ordinal, kept raw so storage owns no UI type.
        const val DefaultLibraryLayoutOrdinal = 3
    }
}

class AppPreferences(private val context: Context) {
    val settings: Flow<AppSettings> = context.appPreferences.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            val migratedTint = when (preferences[LegacyPosterTintStrengthKey] ?: 0) {
                1 -> 17
                2 -> 33
                3 -> 50
                else -> 0
            }
            AppSettings(
                libraryLayoutOrdinal = preferences[LibraryLayoutKey]
                    ?: AppSettings.DefaultLibraryLayoutOrdinal,
                wallZoomPercent = (preferences[WallZoomKey] ?: 100).coerceIn(72, 135),
                wallDensityPercent = (preferences[WallDensityKey] ?: 100).coerceIn(70, 130),
                wallFocusShapeOrdinal = preferences[WallFocusShapeKey] ?: 0,
                wallFocusSizeOrdinal = preferences[WallFocusSizeKey] ?: 1,
                wallAmbientPercent = (preferences[WallAmbientKey] ?: 100).coerceIn(0, 150),
                wallLightsOut = preferences[LightsOutKey] ?: false,
                wallAutoFocusPlaying = preferences[AutoFocusPlayingKey] ?: false,
                wallPosterTintPercent = (preferences[PosterTintPercentKey] ?: migratedTint)
                    .coerceIn(0, 60),
                motionPercent = (preferences[MotionPercentKey]
                    ?: if (preferences[LegacyReducedMotionKey] == true) 0 else 100)
                    .coerceIn(0, 100),
                uiStylePresetOrdinal = preferences[UiStylePresetKey]
                    ?: UiStylePreset.Balanced.ordinal,
                uiAccentPaletteOrdinal = preferences[UiAccentPaletteKey]
                    ?: UiAccentPalette.Ocean.ordinal,
                uiScalePercent = (preferences[UiScaleKey] ?: 100).coerceIn(85, 120),
            )
        }

    suspend fun setLibraryLayout(ordinal: Int) = edit { it[LibraryLayoutKey] = ordinal }
    suspend fun setWallZoom(percent: Int) = edit { it[WallZoomKey] = percent.coerceIn(72, 135) }
    suspend fun setWallDensity(percent: Int) = edit { it[WallDensityKey] = percent.coerceIn(70, 130) }
    suspend fun setWallFocusShape(ordinal: Int) = edit { it[WallFocusShapeKey] = ordinal }
    suspend fun setWallFocusSize(ordinal: Int) = edit { it[WallFocusSizeKey] = ordinal }
    suspend fun setWallAmbient(percent: Int) = edit { it[WallAmbientKey] = percent.coerceIn(0, 150) }
    suspend fun setLightsOut(enabled: Boolean) = edit { it[LightsOutKey] = enabled }
    suspend fun setAutoFocusPlaying(enabled: Boolean) = edit { it[AutoFocusPlayingKey] = enabled }
    suspend fun setPosterTint(percent: Int) = edit { it[PosterTintPercentKey] = percent.coerceIn(0, 60) }

    suspend fun setMotionPercent(percent: Int) = edit {
        it[MotionPercentKey] = percent.coerceIn(0, 100)
        it[UiStylePresetKey] = UiStylePreset.Custom.ordinal
    }

    suspend fun setUiAccentPalette(ordinal: Int) = edit {
        it[UiAccentPaletteKey] = UiAccentPalette.fromOrdinal(ordinal).ordinal
        it[UiStylePresetKey] = UiStylePreset.Custom.ordinal
    }

    suspend fun setUiScalePercent(percent: Int) = edit {
        it[UiScaleKey] = percent.coerceIn(85, 120)
        it[UiStylePresetKey] = UiStylePreset.Custom.ordinal
    }

    suspend fun applyUiStylePreset(ordinal: Int) {
        val preset = UiStylePreset.fromOrdinal(ordinal).takeUnless { it == UiStylePreset.Custom }
            ?: UiStylePreset.Balanced
        edit {
            it[UiStylePresetKey] = preset.ordinal
            it[UiAccentPaletteKey] = preset.accent.ordinal
            it[MotionPercentKey] = preset.motionPercent
            it[UiScaleKey] = preset.uiScalePercent
        }
    }

    suspend fun resetDefaults() = edit { it.clear() }

    private suspend inline fun edit(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.appPreferences.edit { block(it) }
    }

    private companion object {
        val LibraryLayoutKey = intPreferencesKey("library_layout")
        val WallZoomKey = intPreferencesKey("wall_zoom_percent")
        val WallDensityKey = intPreferencesKey("wall_density_percent")
        val WallFocusShapeKey = intPreferencesKey("wall_focus_shape")
        val WallFocusSizeKey = intPreferencesKey("wall_focus_size")
        val WallAmbientKey = intPreferencesKey("wall_ambient_percent")
        val LightsOutKey = booleanPreferencesKey("wall_lights_out")
        val AutoFocusPlayingKey = booleanPreferencesKey("wall_auto_focus_playing")
        val PosterTintPercentKey = intPreferencesKey("wall_poster_tint_percent")
        val MotionPercentKey = intPreferencesKey("ui_motion_percent")
        val UiStylePresetKey = intPreferencesKey("ui_style_preset")
        val UiAccentPaletteKey = intPreferencesKey("ui_accent_palette")
        val UiScaleKey = intPreferencesKey("ui_scale_percent")
        val LegacyPosterTintStrengthKey = intPreferencesKey("wall_poster_tint_strength")
        val LegacyReducedMotionKey = booleanPreferencesKey("wall_reduced_motion")
    }
}
