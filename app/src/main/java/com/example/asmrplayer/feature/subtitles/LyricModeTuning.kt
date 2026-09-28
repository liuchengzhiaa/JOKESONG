package com.example.asmrplayer.feature.subtitles

/**
 * The parameters a lyric mode exposes.
 *
 * Two rules shape everything here.
 *
 * **The global motion setting is not one of them.** `motionPercent` stays the master
 * switch: it decides whether the scene runs at all and scales how far anything
 * travels. A mode's own parameters are absolute positions, so a person can tune the
 * wheel to the shape they like and then still turn motion down without losing that
 * shape. Folding motion into these ranges would make the two settings fight.
 *
 * **Every continuous parameter is stored as an integer with a declared range.** That
 * is what lets the settings list reuse `levelFraction` from `core/settings` -- the
 * position of a value on its own control is then a pure function of the value and
 * the range, so a slider cannot disagree with the number beside it, and a value that
 * came out of storage out of range can be clamped back without inventing a meaning
 * for it.
 */

/** Which parts of the clockwork are drawn behind the lyric rail. */
enum class PendoloGearDecor {
    /** No clockwork at all: the rail floats on the backdrop by itself. */
    None,

    /** The full assembly at reduced opacity, so it reads as a watermark. */
    Subtle,

    /** The full assembly at full opacity. */
    Full,
    ;

    companion object {
        val Default: PendoloGearDecor = Subtle

        fun fromOrdinal(ordinal: Int): PendoloGearDecor = entries.getOrNull(ordinal) ?: Default
    }
}

/**
 * The escapement wheel's geometry and furniture.
 *
 * Every default here is the reference's own default, so a first run looks like the
 * mode was designed rather than like a zeroed-out settings page.
 */
data class PendoloTuning(
    /** Horizontal position of the wheel's hub, in thousandths of the stage width. */
    val wheelCenterXPerMille: Int = DefaultWheelCenterXPerMille,
    /** Vertical position of the wheel's hub, in thousandths of the stage height. */
    val wheelCenterYPerMille: Int = DefaultWheelCenterYPerMille,
    /** Arc radius, in percent of the stage's shorter side. */
    val arcRadiusPercent: Int = DefaultArcRadiusPercent,
    /** How much of the circle the visible lines span, in degrees. */
    val arcAngleDegrees: Int = DefaultArcAngleDegrees,
    /** Escapement spring strength, in tenths -- how hard a tick snaps into place. */
    val tickSnappinessPerTen: Int = DefaultTickSnappinessPerTen,
    /** How much bigger the line being sung is than its neighbours, in percent. */
    val activeScalePercent: Int = DefaultActiveScalePercent,
    val gearDecor: PendoloGearDecor = PendoloGearDecor.Default,
    /** A dark radial pool behind the hub, which is what makes the dial read as glass. */
    val centreGradient: Boolean = true,
    /** Draw the cover inside the dial. */
    val coverOnDial: Boolean = false,
    /** A soft outer glow on the line being sung. */
    val lineGlow: Boolean = false,
) {
    val wheelCenterXFraction: Float get() = wheelCenterXPerMille / 1000f
    val wheelCenterYFraction: Float get() = wheelCenterYPerMille / 1000f
    val arcRadiusFraction: Float get() = arcRadiusPercent / 100f
    val tickSnappiness: Float get() = tickSnappinessPerTen / 10f
    val activeScale: Float get() = activeScalePercent / 100f
}

/** The word cloud's layout and drift. */
data class LuminousTuning(
    /** Whether each word is tilted onto its own angle. */
    val wordRotation: Boolean = true,
    /** Breathing float amplitude, in percent of the reference's own amplitude. */
    val breathingFloatPercent: Int = DefaultBreathingFloatPercent,
    /** Space between words, in percent of the reference's own spacing. */
    val wordSpacingPercent: Int = DefaultWordSpacingPercent,
)

/** Which artwork pool the compiled 凝彩 shots draw from. */
enum class TemperaArtworkSource {
    /** Reuse the current audio's cover for every shot. */
    Cover,

    /** Select deterministically from the user-managed SAF image library. */
    ImageLibrary,
    ;

    companion object {
        val Default = Cover
        fun fromOrdinal(ordinal: Int): TemperaArtworkSource = entries.getOrNull(ordinal) ?: Default
    }
}

/**
 * The authored canvas inside the phone screen.
 *
 * FullScreen is useful while developing a mode, while Square and LandscapeFourThree
 * keep a desktop-authored PV composition intact on a portrait phone. In the latter
 * two the canvas is fitted inside the viewport; on portrait its long side is exactly
 * the phone width, as requested by the product owner.
 */
enum class TemperaStageAspect(val widthOverHeight: Float?) {
    FullScreen(null),
    Square(1f),
    LandscapeFourThree(4f / 3f),
    ;

    companion object {
        val Default = LandscapeFourThree
        fun fromOrdinal(ordinal: Int): TemperaStageAspect = entries.getOrNull(ordinal) ?: Default
    }
}

enum class TemperaColorMode {
    Duo,
    Mono,
    CoverGradient,
    ;

    companion object {
        val Default = Duo
        fun fromOrdinal(ordinal: Int): TemperaColorMode = entries.getOrNull(ordinal) ?: Default
    }
}

enum class TemperaImageDepth {
    BehindType,
    InFrontOfType,
    ;

    companion object {
        val Default = BehindType
        fun fromOrdinal(ordinal: Int): TemperaImageDepth = entries.getOrNull(ordinal) ?: Default
    }
}

/** Persisted parameters owned only by 凝彩. */
data class TemperaTuning(
    val artworkSource: TemperaArtworkSource = TemperaArtworkSource.Default,
    val stageAspect: TemperaStageAspect = TemperaStageAspect.Default,
    /** Persisted SAF document URIs. The renderer never enumerates storage itself. */
    val imageUris: List<String> = emptyList(),
    /** Folia-compatible scene controls, stored as percentages for touch-friendly sliders. */
    val cameraIntensityPercent: Int = 100,
    val glyphMotionPercent: Int = 100,
    val glyphSettleStretchPercent: Int = 50,
    val wholeLineLyrics: Boolean = false,
    val colorMode: TemperaColorMode = TemperaColorMode.Default,
    val showBlocks: Boolean = true,
    val showDecor: Boolean = true,
    val showCornerMarks: Boolean = true,
    val textInversion: Boolean = true,
    val enableTransitions: Boolean = true,
    val imageFrequencyPercent: Int = 60,
    val imageDepth: TemperaImageDepth = TemperaImageDepth.Default,
)

/**
 * Whether this mode exposes parameters of its own.
 *
 * Exhaustive rather than a `setOf(...)` lookup, so a new mode has to answer the
 * question at compile time: the scene header offers a settings button only where this
 * is true, and a mode that grows a tuning cannot silently go without one.
 */
val LyricsVisualizerMode.hasTunings: Boolean
    get() = when (this) {
        LyricsVisualizerMode.Luminous,
        LyricsVisualizerMode.Pendolo,
        LyricsVisualizerMode.Tempera,
        -> true
        LyricsVisualizerMode.Claddagh -> false
    }

/** Storage ranges, and therefore slider ranges. */
val PendoloWheelCenterXRange = -200..400
val PendoloWheelCenterYRange = 100..900
val PendoloArcRadiusRange = 25..70
val PendoloArcAngleRange = 40..160
val PendoloTickSnappinessRange = 5..60
val PendoloActiveScaleRange = 100..160
val LuminousBreathingFloatRange = 0..200
val LuminousWordSpacingRange = 0..200

private const val DefaultWheelCenterXPerMille = 0
private const val DefaultWheelCenterYPerMille = 500
private const val DefaultArcRadiusPercent = 42
private const val DefaultArcAngleDegrees = 100
private const val DefaultTickSnappinessPerTen = 20
private const val DefaultActiveScalePercent = 125
private const val DefaultBreathingFloatPercent = 100
private const val DefaultWordSpacingPercent = 70

/**
 * Brings a tuning back inside its ranges.
 *
 * Applied on the way out of storage and again on the way into a mode, so a stored
 * value that predates a range change degrades to the nearest legal one instead of
 * producing a wheel with a negative radius.
 */
fun PendoloTuning.clamped(): PendoloTuning = copy(
    wheelCenterXPerMille = wheelCenterXPerMille.coerceIn(PendoloWheelCenterXRange),
    wheelCenterYPerMille = wheelCenterYPerMille.coerceIn(PendoloWheelCenterYRange),
    arcRadiusPercent = arcRadiusPercent.coerceIn(PendoloArcRadiusRange),
    arcAngleDegrees = arcAngleDegrees.coerceIn(PendoloArcAngleRange),
    tickSnappinessPerTen = tickSnappinessPerTen.coerceIn(PendoloTickSnappinessRange),
    activeScalePercent = activeScalePercent.coerceIn(PendoloActiveScaleRange),
)

fun LuminousTuning.clamped(): LuminousTuning = copy(
    breathingFloatPercent = breathingFloatPercent.coerceIn(LuminousBreathingFloatRange),
    wordSpacingPercent = wordSpacingPercent.coerceIn(LuminousWordSpacingRange),
)

fun TemperaTuning.clamped(): TemperaTuning = copy(
    imageUris = imageUris
        .asSequence()
        .filter(String::isNotBlank)
        .distinct()
        .take(MaxTemperaLibraryImages)
        .sorted()
        .toList(),
    cameraIntensityPercent = cameraIntensityPercent.coerceIn(0, 200),
    glyphMotionPercent = glyphMotionPercent.coerceIn(0, 200),
    glyphSettleStretchPercent = glyphSettleStretchPercent.coerceIn(0, 100),
    imageFrequencyPercent = imageFrequencyPercent.coerceIn(0, 100),
)

const val MaxTemperaLibraryImages = 32
