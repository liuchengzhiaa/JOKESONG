package com.example.asmrplayer.ui.lyrics.visualizers.pendolo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.feature.subtitles.PendoloGearDecor
import com.example.asmrplayer.feature.subtitles.PendoloTuning
import com.example.asmrplayer.feature.subtitles.PendoloWheel
import com.example.asmrplayer.feature.subtitles.motionFraction
import com.example.asmrplayer.ui.lyrics.LyricCoverLayer
import com.example.asmrplayer.ui.lyrics.lyricsSceneIsRunning
import com.example.asmrplayer.ui.lyrics.rememberLyricsSceneElapsedSeconds
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The clockwork behind the lyric arc: the escapement wheel the lines ride on, drawn
 * once per frame rather than composed out of a hundred little boxes.
 *
 * Two clocks drive it, and they are deliberately different. The **main gear** turns
 * with the lyric rail -- it is handed the same angle the rail is rotated by, so a
 * gear tooth and a line can never disagree about where the wheel is. Everything else
 * runs on the **scene clock**: the balance wheel oscillates at a fixed rate and the
 * seconds wheel steps once a second. That split is why the drawn gears keep ticking
 * while the lines hold still within a line, which is what the reference does and what
 * makes the dial read as a movement rather than as a picture of one.
 *
 * Audio is not analysed anywhere in this app, so the balance wheel oscillates at the
 * amplitude the reference itself uses when it is handed no audio. It is a constant,
 * not an omission: the gear is visibly alive without pretending to hear anything.
 */
@Composable
internal fun PendoloClockwork(
    item: LibraryItem,
    wheel: PendoloWheel,
    tuning: PendoloTuning,
    accent: Color,
    isPlaying: Boolean,
    motionPercent: Int,
    /**
     * Radians the escapement wheel has ratcheted through, shared with the rail.
     *
     * A lambda rather than a value so the read happens while drawing: a tick in flight
     * redraws the clockwork instead of recomposing it every frame.
     */
    gearRotation: () -> Float,
    modifier: Modifier = Modifier,
) {
    val elapsed = rememberLyricsSceneElapsedSeconds(
        running = lyricsSceneIsRunning(isPlaying, motionPercent),
    )
    val motion = motionFraction(motionPercent)
    val decor = when (tuning.gearDecor) {
        PendoloGearDecor.None -> 0f
        PendoloGearDecor.Subtle -> SubtleDecorOpacity
        PendoloGearDecor.Full -> 1f
    }

    Box(modifier.fillMaxSize()) {
        if (tuning.centreGradient) {
            // A dark pool at the hub. Without it the dial has no glass to sit under
            // and the gears read as a flat diagram rather than as depth.
            Canvas(Modifier.fillMaxSize()) { drawHubPool(wheel) }
        }
        if (tuning.coverOnDial) {
            DialCover(item = item, wheel = wheel)
        }
        Canvas(Modifier.fillMaxSize()) {
            if (decor <= 0f) return@Canvas
            val rotation = gearRotation()
            val seconds = elapsed.value

            drawGuideRings(wheel, decor)
            drawDialTicks(wheel, rotation, decor)
            drawEscapementGear(wheel, rotation, accent, decor)
            drawSpokedWheel(wheel, rotation, accent, decor)
            drawBevelRings(wheel, accent, decor)
            drawGuilloche(wheel, decor)
            drawBalanceWheel(wheel, accent, seconds, motion, decor)
            drawHairspring(wheel, accent, seconds, motion, decor)
            drawSecondsWheel(wheel, accent, seconds, decor)
            drawPlanetGears(wheel, rotation, accent, decor)
            drawJewels(wheel, accent, decor)
        }
    }
}

/**
 * The artwork, seen through the dial.
 *
 * Drawn as a composable rather than into the canvas so it goes through the same
 * loader -- and the same palette fallback -- as every other cover in the app.
 */
@Composable
private fun DialCover(item: LibraryItem, wheel: PendoloWheel) {
    val diameter = wheel.baseRadius * DialCoverFraction * 2f
    if (diameter <= 0f) return
    val density = LocalDensity.current
    val sizeDp = with(density) { diameter.toDp() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (wheel.centerX - diameter / 2f).roundToInt(),
                    y = (wheel.centerY - diameter / 2f).roundToInt(),
                )
            }
            .size(sizeDp)
            .clip(CircleShape)
            .alpha(DialCoverOpacity),
    ) {
        LyricCoverLayer(item = item, scale = 1f, alpha = 1f, blurRadius = 0.dp)
    }
}

private fun DrawScope.drawHubPool(wheel: PendoloWheel) {
    if (wheel.baseRadius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = HubPoolAlpha), Color.Transparent),
            center = Offset(wheel.centerX, wheel.centerY),
            radius = wheel.baseRadius * HubPoolRadiusFraction,
        ),
        radius = wheel.baseRadius * HubPoolRadiusFraction,
        center = Offset(wheel.centerX, wheel.centerY),
    )
}

private fun DrawScope.drawGuideRings(wheel: PendoloWheel, decor: Float) {
    GuideRingFractions.forEach { fraction ->
        drawCircle(
            color = Color.White.copy(alpha = GuideRingAlpha * decor),
            radius = wheel.baseRadius * fraction,
            center = wheel.center,
            style = Stroke(width = hairline(wheel)),
        )
    }
}

/**
 * The 60 ticks of the dial, one per second, turning a fifth as fast as the wheel --
 * a train of gears, not a rigidly attached hand.
 */
private fun DrawScope.drawDialTicks(wheel: PendoloWheel, rotationRadians: Float, decor: Float) {
    val radius = wheel.baseRadius * TickRadiusFraction
    if (radius <= 0f) return
    rotate(degrees = Math.toDegrees((rotationRadians * TickDriftFraction).toDouble()).toFloat()) {
        repeat(TickCount) { index ->
            val major = index % TickMajorEvery == 0
            val length = wheel.scaled(if (major) TickMajorLengthPx else TickMinorLengthPx)
            val angle = index * FullTurn / TickCount
            val direction = Offset(cos(angle), sin(angle))
            drawLine(
                color = Color.White.copy(alpha = (if (major) TickMajorAlpha else TickMinorAlpha) * decor),
                start = wheel.center + direction * radius,
                end = wheel.center + direction * (radius - length),
                strokeWidth = if (major) hairline(wheel) * 1.4f else hairline(wheel),
            )
        }
    }
}

/**
 * The escapement wheel itself: a toothed rim whose rotation is the lyric rail's own,
 * so the teeth and the lines are the same object seen twice.
 */
private fun DrawScope.drawEscapementGear(
    wheel: PendoloWheel,
    rotationRadians: Float,
    accent: Color,
    decor: Float,
) {
    val outer = wheel.baseRadius + wheel.scaled(GearToothDepthPx)
    if (outer <= 0f) return
    val path = Path()
    val steps = GearTeeth * 2
    repeat(steps) { index ->
        val radius = if (index % 2 == 0) outer else wheel.baseRadius
        val angle = index * FullTurn / steps
        val point = wheel.center + Offset(cos(angle), sin(angle)) * radius
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color = accent.copy(alpha = GearRimAlpha * decor))
    drawPath(
        path = path,
        color = Color.White.copy(alpha = GearEdgeAlpha * decor),
        style = Stroke(width = hairline(wheel)),
    )
}

private fun DrawScope.drawSpokedWheel(
    wheel: PendoloWheel,
    rotationRadians: Float,
    accent: Color,
    decor: Float,
) {
    val inner = wheel.baseRadius * SpokeInnerFraction
    val outer = wheel.baseRadius * SpokeOuterFraction
    if (outer <= inner) return
    rotate(degrees = Math.toDegrees(rotationRadians.toDouble()).toFloat()) {
        repeat(SpokeCount) { index ->
            val angle = index * FullTurn / SpokeCount
            val direction = Offset(cos(angle), sin(angle))
            drawLine(
                color = Color.White.copy(alpha = SpokeAlpha * decor),
                start = wheel.center + direction * inner,
                end = wheel.center + direction * outer,
                strokeWidth = wheel.scaled(SpokeWidthPx),
            )
        }
    }
    drawCircle(
        color = Color.White.copy(alpha = SpokeAlpha * decor),
        radius = outer,
        center = wheel.center,
        style = Stroke(width = hairline(wheel) * 1.6f),
    )
    drawCircle(
        color = accent.copy(alpha = HubCapAlpha * decor),
        radius = inner,
        center = wheel.center,
    )
}

/** The turned, bevelled edge of the dial: two rings a hair apart. */
private fun DrawScope.drawBevelRings(wheel: PendoloWheel, accent: Color, decor: Float) {
    drawCircle(
        color = accent.copy(alpha = BevelAccentAlpha * decor),
        radius = wheel.baseRadius * BevelOuterFraction,
        center = wheel.center,
        style = Stroke(width = hairline(wheel) * 2f),
    )
    drawCircle(
        color = Color.White.copy(alpha = BevelLightAlpha * decor),
        radius = wheel.baseRadius * BevelInnerFraction,
        center = wheel.center,
        style = Stroke(width = hairline(wheel)),
    )
}

/** The engine-turned rays between the spokes and the rim. */
private fun DrawScope.drawGuilloche(wheel: PendoloWheel, decor: Float) {
    val inner = wheel.baseRadius * GuillocheInnerFraction
    val outer = wheel.baseRadius * GuillocheOuterFraction
    repeat(GuillocheRayCount) { index ->
        val angle = index * FullTurn / GuillocheRayCount
        val direction = Offset(cos(angle), sin(angle))
        drawLine(
            color = Color.White.copy(alpha = GuillocheAlpha * decor),
            start = wheel.center + direction * inner,
            end = wheel.center + direction * outer,
            strokeWidth = hairline(wheel),
        )
    }
}

/**
 * The balance wheel: the one part that oscillates rather than turns, and the reason
 * the dial looks alive between line changes rather than only on them.
 */
private fun DrawScope.drawBalanceWheel(
    wheel: PendoloWheel,
    accent: Color,
    seconds: Float,
    motion: Float,
    decor: Float,
) {
    val radius = wheel.baseRadius * BalanceRadiusFraction
    val centre = wheel.balanceCentre
    // A balance wheel does not go round: it swings. The amplitude is constant because
    // nothing here can hear the music.
    val swingDegrees = Math.toDegrees(
        (sin(seconds * BalanceRateRadPerSecond) * BalanceAmplitudeRadians * motion).toDouble(),
    ).toFloat()

    rotate(degrees = swingDegrees, pivot = centre) {
        drawCircle(
            color = Color.White.copy(alpha = BalanceRimAlpha * decor),
            radius = radius,
            center = centre,
            style = Stroke(width = hairline(wheel) * 3f),
        )
        repeat(BalanceSpokeCount) { index ->
            // Half a turn apart, so the two spokes read as one bar through the hub.
            val angle = index * FullTurn / (BalanceSpokeCount * 2)
            val direction = Offset(cos(angle), sin(angle))
            drawLine(
                color = accent.copy(alpha = BalanceSpokeAlpha * decor),
                start = centre - direction * radius,
                end = centre + direction * radius,
                strokeWidth = hairline(wheel) * 1.6f,
            )
        }
    }
}

/** The hairspring: a flat spiral that breathes with the balance wheel. */
private fun DrawScope.drawHairspring(
    wheel: PendoloWheel,
    accent: Color,
    seconds: Float,
    motion: Float,
    decor: Float,
) {
    val centre = wheel.balanceCentre
    val outer = wheel.baseRadius * HairspringRadiusFraction
    if (outer <= 0f) return
    val path = Path()
    val turns = HairspringTurns
    val steps = (turns * HairspringStepsPerTurn).toInt()
    val phase = sin(seconds * BalanceRateRadPerSecond) * motion * HairspringBreathRadians
    repeat(steps + 1) { index ->
        val progress = index.toFloat() / steps
        val angle = progress * turns * 2f * PI.toFloat() + phase
        val radius = outer * (HairspringInnerFraction + (1f - HairspringInnerFraction) * progress)
        val point = centre + Offset(cos(angle), sin(angle)) * radius
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(
        path = path,
        color = accent.copy(alpha = HairspringAlpha * decor),
        style = Stroke(width = hairline(wheel)),
    )
}

/**
 * The seconds wheel: one tooth per second of the scene's own running time.
 *
 * Each step is eased rather than jumped, because the reference drives it from a
 * spring and a wheel that teleports once a second looks broken. The easing is the
 * cheap part of that spring -- it settles without overshooting.
 */
private fun DrawScope.drawSecondsWheel(
    wheel: PendoloWheel,
    accent: Color,
    seconds: Float,
    decor: Float,
) {
    val radius = wheel.baseRadius * SecondsWheelRadiusFraction
    if (radius <= 0f) return
    val centre = wheel.secondsCentre
    val wholeSteps = floor(seconds)
    val settled = 1f - (1f - (seconds - wholeSteps)).pow(SecondsWheelSettleExponent)
    val steps = wholeSteps + settled
    val angle = steps * FullTurn / SecondsWheelTeeth

    rotate(degrees = Math.toDegrees(angle.toDouble()).toFloat(), pivot = centre) {
        drawGearOutline(
            centre = centre,
            outerRadius = radius,
            innerRadius = radius - wheel.scaled(SecondsToothDepthPx),
            teeth = SecondsWheelTeeth,
            fill = Color.White.copy(alpha = SecondsWheelAlpha * decor),
            edge = accent.copy(alpha = SecondsWheelEdgeAlpha * decor),
            strokeWidth = hairline(wheel),
        )
        drawCircle(
            color = accent.copy(alpha = HubCapAlpha * decor),
            radius = radius * SecondsWheelHubFraction,
            center = centre,
        )
    }
}

/** Three small gears around the rim, turning with the escapement. */
private fun DrawScope.drawPlanetGears(
    wheel: PendoloWheel,
    rotationRadians: Float,
    accent: Color,
    decor: Float,
) {
    val radius = wheel.baseRadius * PlanetGearRadiusFraction
    if (radius <= 0f) return
    repeat(PlanetGearCount) { index ->
        val angle = index * FullTurn / PlanetGearCount + rotationRadians * PlanetGearDriftFraction
        val centre = wheel.center + Offset(cos(angle), sin(angle)) * (wheel.baseRadius * PlanetOrbitFraction)
        // Meshed gears turn against each other, so these run the other way.
        val spin = -rotationRadians * PlanetGearSpinFraction
        rotate(degrees = Math.toDegrees(spin.toDouble()).toFloat(), pivot = centre) {
            drawGearOutline(
                centre = centre,
                outerRadius = radius,
                innerRadius = radius - wheel.scaled(PlanetToothDepthPx),
                teeth = PlanetGearTeeth,
                fill = Color.White.copy(alpha = PlanetGearAlpha * decor),
                edge = accent.copy(alpha = PlanetGearEdgeAlpha * decor),
                strokeWidth = hairline(wheel),
            )
        }
    }
}

/** The bearing jewels: the small bright points a real movement is read by. */
private fun DrawScope.drawJewels(wheel: PendoloWheel, accent: Color, decor: Float) {
    val radius = wheel.scaled(JewelRadiusPx)
    listOf(wheel.center, wheel.balanceCentre, wheel.secondsCentre).forEach { centre ->
        drawCircle(color = accent.copy(alpha = JewelAlpha * decor), radius = radius, center = centre)
    }
}

/** A toothed ring, used by both of the small wheels. */
private fun DrawScope.drawGearOutline(
    centre: Offset,
    outerRadius: Float,
    innerRadius: Float,
    teeth: Int,
    fill: Color,
    edge: Color,
    strokeWidth: Float,
) {
    if (outerRadius <= 0f) return
    val path = Path()
    val steps = teeth * 2
    repeat(steps) { index ->
        val radius = if (index % 2 == 0) outerRadius else innerRadius.coerceAtLeast(0f)
        val angle = index * FullTurn / steps
        val point = centre + Offset(cos(angle), sin(angle)) * radius
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color = fill)
    drawPath(path, color = edge, style = Stroke(width = strokeWidth))
}

/**
 * A length in the dial's own units.
 *
 * The reference's small dimensions are absolute pixels tuned against a wheel of a
 * particular size. Scaling them by the wheel's actual radius keeps the dial's
 * proportions whatever the stage is and whatever radius the listener has dialled in,
 * rather than giving a tablet a wheel with pinprick teeth.
 */
private fun PendoloWheel.scaled(referencePx: Float): Float =
    referencePx * (baseRadius / ReferenceDialRadiusPx)

private val PendoloWheel.center: Offset get() = Offset(centerX, centerY)
private val PendoloWheel.balanceCentre: Offset
    get() = Offset(centerX + baseRadius * BalanceCentreXFraction, centerY + baseRadius * BalanceCentreYFraction)
private val PendoloWheel.secondsCentre: Offset
    get() = Offset(centerX + baseRadius * SecondsCentreXFraction, centerY + baseRadius * SecondsCentreYFraction)

private fun DrawScope.hairline(wheel: PendoloWheel): Float =
    wheel.scaled(HairlinePx).coerceAtLeast(HairlineMinimumPx)

/** Everything the dial is drawn against, in ticks and pixels of one reference wheel. */
private const val ReferenceDialRadiusPx = 450f
private const val HairlinePx = 1.6f
private const val HairlineMinimumPx = 0.6f
private const val FullTurn = (2.0 * PI).toFloat()

private const val HubPoolAlpha = 0.72f
private const val HubPoolRadiusFraction = 1.65f
private const val DialCoverFraction = 0.88f
private const val DialCoverOpacity = 0.42f
private const val SubtleDecorOpacity = 0.6f

private val GuideRingFractions = listOf(0.30f, 0.60f, 0.85f, 1.15f, 1.40f)
private const val GuideRingAlpha = 0.09f

private const val TickCount = 60
private const val TickMajorEvery = 5
private const val TickRadiusFraction = 1.15f
private const val TickMajorLengthPx = 12f
private const val TickMinorLengthPx = 6f
private const val TickMajorAlpha = 0.34f
private const val TickMinorAlpha = 0.20f
private const val TickDriftFraction = 0.2f

private const val GearTeeth = 36
private const val GearToothDepthPx = 10f
private const val GearRimAlpha = 0.20f
private const val GearEdgeAlpha = 0.16f

private const val SpokeCount = 6
private const val SpokeInnerFraction = 0.20f
private const val SpokeOuterFraction = 0.85f
private const val SpokeWidthPx = 2.2f
private const val SpokeAlpha = 0.18f
private const val HubCapAlpha = 0.22f

private const val BevelOuterFraction = 0.92f
private const val BevelInnerFraction = 0.88f
private const val BevelAccentAlpha = 0.20f
private const val BevelLightAlpha = 0.10f

private const val GuillocheRayCount = 48
private const val GuillocheInnerFraction = 0.62f
private const val GuillocheOuterFraction = 0.83f
private const val GuillocheAlpha = 0.05f

private const val BalanceRadiusFraction = 0.20f
private const val BalanceCentreXFraction = 0.30f
private const val BalanceCentreYFraction = -0.34f
private const val BalanceSpokeCount = 2
private const val BalanceRimAlpha = 0.28f
private const val BalanceSpokeAlpha = 0.30f

/** How fast the balance wheel swings, in radians per second. */
private const val BalanceRateRadPerSecond = 4.6f

/**
 * The bass level the reference falls back to when it is handed no audio.
 *
 * It is a constant here rather than a reading, because nothing in this app analyses
 * audio: the dial is visibly alive without claiming to react to anything.
 */
private const val SilentBass = 0.18f

/** Peak swing of the balance wheel, in radians, at that level. */
private const val BalanceAmplitudeRadians = 0.15f + SilentBass * 0.70f

private const val HairspringTurns = 3.5f
private const val HairspringStepsPerTurn = 36f
private const val HairspringRadiusFraction = 0.17f
private const val HairspringInnerFraction = 0.18f
private const val HairspringBreathRadians = 0.5f
private const val HairspringAlpha = 0.34f

private const val SecondsWheelTeeth = 15
private const val SecondsWheelRadiusFraction = 0.24f
private const val SecondsCentreXFraction = 0.42f
private const val SecondsCentreYFraction = 0.40f
private const val SecondsToothDepthPx = 7f
private const val SecondsWheelAlpha = 0.10f
private const val SecondsWheelEdgeAlpha = 0.24f
private const val SecondsWheelHubFraction = 0.18f
private const val SecondsWheelSettleExponent = 3f

private const val PlanetGearCount = 3
private const val PlanetGearTeeth = 12
private const val PlanetGearRadiusFraction = 0.10f
private const val PlanetOrbitFraction = 0.72f
private const val PlanetToothDepthPx = 5f
private const val PlanetGearAlpha = 0.07f
private const val PlanetGearEdgeAlpha = 0.16f
private const val PlanetGearDriftFraction = 0.5f
private const val PlanetGearSpinFraction = 2.4f

private const val JewelRadiusPx = 4f
private const val JewelAlpha = 0.42f
