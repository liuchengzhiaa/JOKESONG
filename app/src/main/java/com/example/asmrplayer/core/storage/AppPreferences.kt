package com.example.asmrplayer.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.asmrplayer.core.settings.ToolClusterStyle
import com.example.asmrplayer.core.settings.UiAccentPalette
import com.example.asmrplayer.core.settings.UiStylePreset
import com.example.asmrplayer.feature.subtitles.LuminousTuning
import com.example.asmrplayer.feature.subtitles.PendoloGearDecor
import com.example.asmrplayer.feature.subtitles.PendoloTuning
import com.example.asmrplayer.feature.subtitles.TemperaArtworkSource
import com.example.asmrplayer.feature.subtitles.TemperaColorMode
import com.example.asmrplayer.feature.subtitles.TemperaImageDepth
import com.example.asmrplayer.feature.subtitles.TemperaStageAspect
import com.example.asmrplayer.feature.subtitles.TemperaTuning
import com.example.asmrplayer.feature.subtitles.clamped
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.appPreferences by preferencesDataStore(name = "wall_presentation_preferences")

internal fun resolveLyricsWordGlowModeOrdinal(
    storedOrdinal: Int?,
    legacyEnabled: Boolean?,
): Int = storedOrdinal ?: when (legacyEnabled) {
    false -> 0 // LyricsWordGlowMode.Off
    else -> AppSettings.DefaultLyricsWordGlowModeOrdinal
}

/** Process-durable defaults and global presentation choices. */
data class AppSettings(
    val desktopLyrics: com.example.asmrplayer.core.overlay.DesktopLyricsSettings = com.example.asmrplayer.core.overlay.DesktopLyricsSettings(),
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
    /**
     * Presentation of every collapsible tool cluster. Kept as a raw ordinal because
     * storage owns no UI type, and read back through the enum's own `fromOrdinal` so a
     * value this build does not know shows the default rather than nothing.
     */
    val toolClusterStyleOrdinal: Int = ToolClusterStyle.Default.ordinal,
    /**
     * Whether the full player opens on a song's card instead of its cover.
     *
     * Off by default, and it is a default rather than a rule: a song whose card is
     * showing can always be switched back to its artwork from the player's tools, and
     * with this off the switch is the only way the card ever appears. Both directions
     * are deliberate -- an install that predates cards must not start showing them, and
     * a card is a thing the owner asks for rather than a thing that happens to him.
     */
    val cardCoverByDefault: Boolean = false,
    val playerCoverPercent: Int = 100,
    val playerTitlePercent: Int = 100,
    val playerLyricsPercent: Int = 100,
    /** Normalized position of the draggable global record within its safe viewport. */
    val globalVinylXPerMille: Int = 0,
    val globalVinylYPerMille: Int = 1_000,
    /** Raw ordinal; the subtitles feature owns the visualizer registry type. */
    val lyricsVisualizerOrdinal: Int = DefaultLyricsVisualizerStoredValue,
    /**
     * Raw ordinal of the chosen lyric background, or null to follow whichever
     * background the selected visualizer mode was designed around. Null is the
     * default because it is what keeps an install that predates the setting
     * looking exactly as it did.
     */
    val lyricsBackgroundOrdinal: Int? = null,
    /** Raw ordinal; the subtitles feature owns the glow-timing policy type. */
    val lyricsWordGlowModeOrdinal: Int = DefaultLyricsWordGlowModeOrdinal,
    /**
     * The two lyric modes that expose parameters of their own.
     *
     * These are the feature's own value types rather than raw ordinals, unlike the
     * registry choices above. An ordinal is fragile because the feature decides what
     * the enum contains, so storage must not know the list. A tuning is the opposite
     * case: it is the stored shape itself -- a flat, immutable record of primitives
     * with no Compose or UI dependency -- so importing it keeps one schema instead of
     * a parallel copy that would have to be kept in step by hand. The one ordinal
     * inside (`PendoloGearDecor`) still resolves through the feature's own
     * `fromOrdinal`, so a stored value this build does not know degrades to the mode's
     * default rather than to a broken mode.
     */
    val luminousTuning: LuminousTuning = LuminousTuning(),
    val pendoloTuning: PendoloTuning = PendoloTuning(),
    val temperaTuning: TemperaTuning = TemperaTuning(),
) {
    companion object {
        // Stable Folia wall storage value; legacy 3 is migrated at the UI boundary.
        const val DefaultLibraryLayoutOrdinal = 4
        // LyricsWordGlowMode.Smart.ordinal, kept raw so storage owns no UI type.
        const val DefaultLyricsWordGlowModeOrdinal = 1
        // LyricsVisualizerMode.Luminous.storedValue. Kept raw so storage owns no UI type.
        const val DefaultLyricsVisualizerStoredValue = 3
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
            // A key that was never written falls back to the mode's own default rather
            // than to a copy of it kept here, so an install that predates the parameter
            // looks exactly like a fresh one and the two defaults cannot drift.
            val luminousDefaults = LuminousTuning()
            val pendoloDefaults = PendoloTuning()
            val temperaDefaults = TemperaTuning()
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
                toolClusterStyleOrdinal = ToolClusterStyle.fromOrdinal(
                    preferences[ToolClusterStyleKey] ?: ToolClusterStyle.Default.ordinal,
                ).ordinal,
                cardCoverByDefault = preferences[CardCoverByDefaultKey] ?: false,
                playerCoverPercent = (preferences[PlayerCoverKey] ?: 100).coerceIn(70, 115),
                desktopLyrics = com.example.asmrplayer.core.overlay.DesktopLyricsSettings(
                    enabled = preferences[DesktopEnabledKey] ?: false,
                    fontSizeSp = preferences[DesktopFontKey] ?: 20,
                    lineCount = preferences[DesktopLinesKey] ?: 2,
                    wordByWord = preferences[DesktopWordsKey] ?: false,
                    textArgb = preferences[DesktopTextKey] ?: 0xFFFFFFFF.toInt(),
                    highlightArgb = preferences[DesktopHighlightKey] ?: 0xFF9CD9FF.toInt(),
                    backgroundPercent = preferences[DesktopBackgroundKey] ?: 25,
                    locked = preferences[DesktopLockedKey] ?: false,
                    bold = preferences[DesktopBoldKey] ?: true,
                    xPerMille = preferences[DesktopXKey] ?: 500,
                    yPerMille = preferences[DesktopYKey] ?: 200,
                ).clamped(),
                playerTitlePercent = (preferences[PlayerTitleKey] ?: 100).coerceIn(75, 140),
                playerLyricsPercent = (preferences[PlayerLyricsKey] ?: 100).coerceIn(75, 150),
                globalVinylXPerMille = (preferences[GlobalVinylXKey] ?: 0)
                    .coerceIn(0, 1_000),
                globalVinylYPerMille = (preferences[GlobalVinylYKey] ?: 1_000)
                    .coerceIn(0, 1_000),
                lyricsVisualizerOrdinal = preferences[LyricsVisualizerKey]
                    ?: AppSettings.DefaultLyricsVisualizerStoredValue,
                lyricsBackgroundOrdinal = preferences[LyricsBackgroundKey],
                lyricsWordGlowModeOrdinal = resolveLyricsWordGlowModeOrdinal(
                    storedOrdinal = preferences[LyricsWordGlowModeKey],
                    legacyEnabled = preferences[LegacyLyricsWordGlowKey],
                ),
                // `clamped()` on the way out, so a stored number that this build would
                // not produce -- written by an older one, or edited by hand -- degrades
                // to the nearest legal parameter instead of failing the read.
                luminousTuning = LuminousTuning(
                    wordRotation = preferences[LuminousWordRotationKey]
                        ?: luminousDefaults.wordRotation,
                    breathingFloatPercent = preferences[LuminousBreathingFloatKey]
                        ?: luminousDefaults.breathingFloatPercent,
                    wordSpacingPercent = preferences[LuminousWordSpacingKey]
                        ?: luminousDefaults.wordSpacingPercent,
                ).clamped(),
                pendoloTuning = PendoloTuning(
                    wheelCenterXPerMille = preferences[PendoloWheelCenterXKey]
                        ?: pendoloDefaults.wheelCenterXPerMille,
                    wheelCenterYPerMille = preferences[PendoloWheelCenterYKey]
                        ?: pendoloDefaults.wheelCenterYPerMille,
                    arcRadiusPercent = preferences[PendoloArcRadiusKey]
                        ?: pendoloDefaults.arcRadiusPercent,
                    arcAngleDegrees = preferences[PendoloArcAngleKey]
                        ?: pendoloDefaults.arcAngleDegrees,
                    tickSnappinessPerTen = preferences[PendoloTickSnappinessKey]
                        ?: pendoloDefaults.tickSnappinessPerTen,
                    activeScalePercent = preferences[PendoloActiveScaleKey]
                        ?: pendoloDefaults.activeScalePercent,
                    gearDecor = preferences[PendoloGearDecorKey]
                        ?.let(PendoloGearDecor::fromOrdinal)
                        ?: pendoloDefaults.gearDecor,
                    centreGradient = preferences[PendoloCentreGradientKey]
                        ?: pendoloDefaults.centreGradient,
                    coverOnDial = preferences[PendoloCoverOnDialKey]
                        ?: pendoloDefaults.coverOnDial,
                    lineGlow = preferences[PendoloLineGlowKey] ?: pendoloDefaults.lineGlow,
                ).clamped(),
                temperaTuning = TemperaTuning(
                    artworkSource = preferences[TemperaArtworkSourceKey]
                        ?.let(TemperaArtworkSource::fromOrdinal)
                        ?: temperaDefaults.artworkSource,
                    stageAspect = preferences[TemperaStageAspectKey]
                        ?.let(TemperaStageAspect::fromOrdinal)
                        ?: temperaDefaults.stageAspect,
                    imageUris = preferences[TemperaImageUrisKey]?.toList().orEmpty(),
                    cameraIntensityPercent = preferences[TemperaCameraIntensityKey]
                        ?: temperaDefaults.cameraIntensityPercent,
                    glyphMotionPercent = preferences[TemperaGlyphMotionKey]
                        ?: temperaDefaults.glyphMotionPercent,
                    glyphSettleStretchPercent = preferences[TemperaGlyphSettleStretchKey]
                        ?: temperaDefaults.glyphSettleStretchPercent,
                    wholeLineLyrics = preferences[TemperaWholeLineKey]
                        ?: temperaDefaults.wholeLineLyrics,
                    colorMode = preferences[TemperaColorModeKey]
                        ?.let(TemperaColorMode::fromOrdinal) ?: temperaDefaults.colorMode,
                    showBlocks = preferences[TemperaShowBlocksKey] ?: temperaDefaults.showBlocks,
                    showDecor = preferences[TemperaShowDecorKey] ?: temperaDefaults.showDecor,
                    showCornerMarks = preferences[TemperaShowCornerMarksKey]
                        ?: temperaDefaults.showCornerMarks,
                    textInversion = preferences[TemperaTextInversionKey]
                        ?: temperaDefaults.textInversion,
                    enableTransitions = preferences[TemperaEnableTransitionsKey]
                        ?: temperaDefaults.enableTransitions,
                    imageFrequencyPercent = preferences[TemperaImageFrequencyKey]
                        ?: temperaDefaults.imageFrequencyPercent,
                    imageDepth = preferences[TemperaImageDepthKey]
                        ?.let(TemperaImageDepth::fromOrdinal) ?: temperaDefaults.imageDepth,
                ).clamped(),
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

    suspend fun setToolClusterStyle(ordinal: Int) = edit {
        it[ToolClusterStyleKey] = ToolClusterStyle.fromOrdinal(ordinal).ordinal
    }

    suspend fun setCardCoverByDefault(enabled: Boolean) = edit {
        it[CardCoverByDefaultKey] = enabled
    }

    suspend fun setGlobalVinylPosition(xPerMille: Int, yPerMille: Int) = edit {
        it[GlobalVinylXKey] = xPerMille.coerceIn(0, 1_000)
        it[GlobalVinylYKey] = yPerMille.coerceIn(0, 1_000)
    }

    suspend fun setLyricsVisualizer(ordinal: Int) = edit {
        it[LyricsVisualizerKey] = ordinal.coerceAtLeast(0)
    }

    suspend fun setLyricsWordGlowMode(ordinal: Int) = edit {
        it[LyricsWordGlowModeKey] = ordinal.coerceAtLeast(0)
    }

    /** Passing null clears the choice, so the background follows the mode again. */
    suspend fun setLyricsBackground(ordinal: Int?) = edit { preferences ->
        if (ordinal == null) {
            preferences.remove(LyricsBackgroundKey)
        } else {
            preferences[LyricsBackgroundKey] = ordinal.coerceAtLeast(0)
        }
    }

    /**
     * Writes a mode's whole parameter set in one edit.
     *
     * The sheet commits on release rather than on every drag frame, so this is called
     * once per gesture: one edit for all of the mode's keys keeps a half-written set
     * out of storage and costs one round trip instead of one per slider.
     */
    suspend fun setLuminousTuning(tuning: LuminousTuning) = edit { preferences ->
        val safe = tuning.clamped()
        preferences[LuminousWordRotationKey] = safe.wordRotation
        preferences[LuminousBreathingFloatKey] = safe.breathingFloatPercent
        preferences[LuminousWordSpacingKey] = safe.wordSpacingPercent
    }

    suspend fun setPendoloTuning(tuning: PendoloTuning) = edit { preferences ->
        val safe = tuning.clamped()
        preferences[PendoloWheelCenterXKey] = safe.wheelCenterXPerMille
        preferences[PendoloWheelCenterYKey] = safe.wheelCenterYPerMille
        preferences[PendoloArcRadiusKey] = safe.arcRadiusPercent
        preferences[PendoloArcAngleKey] = safe.arcAngleDegrees
        preferences[PendoloTickSnappinessKey] = safe.tickSnappinessPerTen
        preferences[PendoloActiveScaleKey] = safe.activeScalePercent
        preferences[PendoloGearDecorKey] = safe.gearDecor.ordinal
        preferences[PendoloCentreGradientKey] = safe.centreGradient
        preferences[PendoloCoverOnDialKey] = safe.coverOnDial
        preferences[PendoloLineGlowKey] = safe.lineGlow
    }

    suspend fun setTemperaTuning(tuning: TemperaTuning) = edit { preferences ->
        val safe = tuning.clamped()
        preferences[TemperaArtworkSourceKey] = safe.artworkSource.ordinal
        preferences[TemperaStageAspectKey] = safe.stageAspect.ordinal
        preferences[TemperaCameraIntensityKey] = safe.cameraIntensityPercent
        preferences[TemperaGlyphMotionKey] = safe.glyphMotionPercent
        preferences[TemperaGlyphSettleStretchKey] = safe.glyphSettleStretchPercent
        preferences[TemperaWholeLineKey] = safe.wholeLineLyrics
        preferences[TemperaColorModeKey] = safe.colorMode.ordinal
        preferences[TemperaShowBlocksKey] = safe.showBlocks
        preferences[TemperaShowDecorKey] = safe.showDecor
        preferences[TemperaShowCornerMarksKey] = safe.showCornerMarks
        preferences[TemperaTextInversionKey] = safe.textInversion
        preferences[TemperaEnableTransitionsKey] = safe.enableTransitions
        preferences[TemperaImageFrequencyKey] = safe.imageFrequencyPercent
        preferences[TemperaImageDepthKey] = safe.imageDepth.ordinal
        if (safe.imageUris.isEmpty()) {
            preferences.remove(TemperaImageUrisKey)
        } else {
            preferences[TemperaImageUrisKey] = safe.imageUris.toSet()
        }
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

    suspend fun setPlayerSizing(cover: Int, title: Int, lyrics: Int) = edit {
        it[PlayerCoverKey] = cover.coerceIn(70, 115)
        it[PlayerTitleKey] = title.coerceIn(75, 140)
        it[PlayerLyricsKey] = lyrics.coerceIn(75, 150)
    }

    suspend fun resetDefaults() = edit { it.clear() }

    suspend fun setDesktopLyrics(value: com.example.asmrplayer.core.overlay.DesktopLyricsSettings) = edit {
        val v = value.clamped()
        it[DesktopEnabledKey] = v.enabled
        it[DesktopFontKey] = v.fontSizeSp
        it[DesktopLinesKey] = v.lineCount
        it[DesktopWordsKey] = v.wordByWord
        it[DesktopTextKey] = v.textArgb
        it[DesktopHighlightKey] = v.highlightArgb
        it[DesktopBackgroundKey] = v.backgroundPercent
        it[DesktopLockedKey] = v.locked
        it[DesktopBoldKey] = v.bold
        it[DesktopXKey] = v.xPerMille
        it[DesktopYKey] = v.yPerMille
    }

    suspend fun setDesktopLyricsPosition(x: Int, y: Int) = edit {
        it[DesktopXKey] = x.coerceIn(0, 1000)
        it[DesktopYKey] = y.coerceIn(0, 1000)
    }

    suspend fun setDesktopLyricsEnabled(enabled: Boolean) = edit { it[DesktopEnabledKey] = enabled }

    private suspend inline fun edit(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.appPreferences.edit { block(it) }
    }

    private companion object {
        val DesktopEnabledKey = booleanPreferencesKey("desktop_lyrics_enabled")
        val DesktopFontKey = intPreferencesKey("desktop_lyrics_font_sp")
        val DesktopLinesKey = intPreferencesKey("desktop_lyrics_lines")
        val DesktopWordsKey = booleanPreferencesKey("desktop_lyrics_words")
        val DesktopTextKey = intPreferencesKey("desktop_lyrics_text_argb")
        val DesktopHighlightKey = intPreferencesKey("desktop_lyrics_highlight_argb")
        val DesktopBackgroundKey = intPreferencesKey("desktop_lyrics_background_percent")
        val DesktopLockedKey = booleanPreferencesKey("desktop_lyrics_locked")
        val DesktopBoldKey = booleanPreferencesKey("desktop_lyrics_bold")
        val DesktopXKey = intPreferencesKey("desktop_lyrics_x")
        val DesktopYKey = intPreferencesKey("desktop_lyrics_y")
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
        val ToolClusterStyleKey = intPreferencesKey("ui_tool_cluster_style")
        val PlayerCoverKey = intPreferencesKey("player_cover_percent")
        val PlayerTitleKey = intPreferencesKey("player_title_percent")
        val PlayerLyricsKey = intPreferencesKey("player_lyrics_percent")
        val CardCoverByDefaultKey = booleanPreferencesKey("card_cover_by_default")
        val GlobalVinylXKey = intPreferencesKey("global_vinyl_x_per_mille")
        val GlobalVinylYKey = intPreferencesKey("global_vinyl_y_per_mille")
        val LyricsVisualizerKey = intPreferencesKey("lyrics_visualizer_mode")
        val LyricsBackgroundKey = intPreferencesKey("lyrics_visualizer_background")
        val LyricsWordGlowModeKey = intPreferencesKey("lyrics_word_glow_mode")
        val LuminousWordRotationKey = booleanPreferencesKey("lyrics_luminous_word_rotation")
        val LuminousBreathingFloatKey = intPreferencesKey("lyrics_luminous_breathing_percent")
        val LuminousWordSpacingKey = intPreferencesKey("lyrics_luminous_word_spacing_percent")
        val PendoloWheelCenterXKey = intPreferencesKey("lyrics_pendolo_wheel_center_x")
        val PendoloWheelCenterYKey = intPreferencesKey("lyrics_pendolo_wheel_center_y")
        val PendoloArcRadiusKey = intPreferencesKey("lyrics_pendolo_arc_radius")
        val PendoloArcAngleKey = intPreferencesKey("lyrics_pendolo_arc_angle")
        val PendoloTickSnappinessKey = intPreferencesKey("lyrics_pendolo_tick_snappiness")
        val PendoloActiveScaleKey = intPreferencesKey("lyrics_pendolo_active_scale")
        val PendoloGearDecorKey = intPreferencesKey("lyrics_pendolo_gear_decor")
        val PendoloCentreGradientKey = booleanPreferencesKey("lyrics_pendolo_centre_gradient")
        val PendoloCoverOnDialKey = booleanPreferencesKey("lyrics_pendolo_cover_on_dial")
        val PendoloLineGlowKey = booleanPreferencesKey("lyrics_pendolo_line_glow")
        val TemperaArtworkSourceKey = intPreferencesKey("lyrics_tempera_artwork_source")
        val TemperaStageAspectKey = intPreferencesKey("lyrics_tempera_stage_aspect")
        val TemperaImageUrisKey = stringSetPreferencesKey("lyrics_tempera_image_uris")
        val TemperaCameraIntensityKey = intPreferencesKey("lyrics_tempera_camera_intensity_percent")
        val TemperaGlyphMotionKey = intPreferencesKey("lyrics_tempera_glyph_motion_percent")
        val TemperaGlyphSettleStretchKey = intPreferencesKey("lyrics_tempera_glyph_settle_percent")
        val TemperaWholeLineKey = booleanPreferencesKey("lyrics_tempera_whole_line")
        val TemperaColorModeKey = intPreferencesKey("lyrics_tempera_color_mode")
        val TemperaShowBlocksKey = booleanPreferencesKey("lyrics_tempera_show_blocks")
        val TemperaShowDecorKey = booleanPreferencesKey("lyrics_tempera_show_decor")
        val TemperaShowCornerMarksKey = booleanPreferencesKey("lyrics_tempera_show_corner_marks")
        val TemperaTextInversionKey = booleanPreferencesKey("lyrics_tempera_text_inversion")
        val TemperaEnableTransitionsKey = booleanPreferencesKey("lyrics_tempera_enable_transitions")
        val TemperaImageFrequencyKey = intPreferencesKey("lyrics_tempera_image_frequency_percent")
        val TemperaImageDepthKey = intPreferencesKey("lyrics_tempera_image_depth")
        val LegacyLyricsWordGlowKey = booleanPreferencesKey("lyrics_word_glow")
        val LegacyPosterTintStrengthKey = intPreferencesKey("wall_poster_tint_strength")
        val LegacyReducedMotionKey = booleanPreferencesKey("wall_reduced_motion")
    }
}
