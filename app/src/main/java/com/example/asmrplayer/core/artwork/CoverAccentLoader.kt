package com.example.asmrplayer.core.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.core.net.toUri
import java.io.IOException
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DecodeTargetEdge = 96
private const val MaximumSampledPixels = 4_096

/**
 * What reading one cover tells the wall about it: the colour the focused card is outlined
 * with, and the artwork's own size.
 *
 * The size is free here -- the decoder has already read the image's bounds to choose a
 * sample size -- and it is the only thing that can supply it. A file listing carries a
 * name and a MIME type and no dimensions, so 完整封面 can only be cut to a cover's shape
 * after the cover itself has been opened.
 */
internal data class CoverFacts(
    /** The card's accent, or null when the artwork holds no colour worth sampling. */
    val accentArgb: Int?,
    /**
     * Population-weighted colour families from the same bounded decode. The first
     * entry is [accentArgb]; later entries are deliberately distinct enough to
     * light different parts of the wall instead of producing four copies of one
     * nearly-identical shade.
     */
    val paletteArgbs: List<Int>,
    val width: Int,
    val height: Int,
    val rawPaletteArgbs: List<Int> = paletteArgbs,
    val playerToneArgb: Int? = rawPaletteArgbs.firstOrNull(),
    val playerField: PlayerColourField? = null,
) {
    /** The artwork's own proportions, which is what a card has to take the shape of. */
    val aspect: Float get() = width.toFloat() / height
}

/**
 * Loads only a thumbnail-sized version of a presentation-relevant cover, then
 * caches its size and representative colours. At present the focused hero uses
 * those facts for shape, border and Folia ambient light; ordinary cards never pay
 * this decoding cost.
 */
internal object CoverAccentLoader {
    private val cache = LruCache<String, CoverFacts>(96)

    suspend fun resolve(context: Context, artworkUri: String?): CoverFacts? {
        artworkUri ?: return null
        synchronized(cache) { cache.get(artworkUri) }?.let { return it }

        val result = withContext(Dispatchers.IO) {
            decodeCoverFacts(context, artworkUri.toUri())
        }
        if (result != null) synchronized(cache) { cache.put(artworkUri, result) }
        return result
    }

    private fun decodeCoverFacts(context: Context, uri: Uri): CoverFacts? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                null
            } else {
                var sampleSize = 1
                while (
                    bounds.outWidth / sampleSize > DecodeTargetEdge * 2 ||
                    bounds.outHeight / sampleSize > DecodeTargetEdge * 2
                ) {
                    sampleSize *= 2
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = resolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: return null
                // The size comes from the bounds, not from the bitmap: the bitmap is a
                // downsampled copy, and a card cut to its proportions would be cut to
                // the proportions of whatever sample size happened to be chosen.
                val sampleWidth = bitmap.width
                val sampleHeight = bitmap.height
                val (rawPalette, playerTone, playerField) = bitmap.usePixels { pixels ->
                    Triple(representativeCoverArgbs(pixels, MaximumPaletteColors, balance = false),
                        playerCoverArgb(pixels), playerColourField(pixels, sampleWidth, sampleHeight))
                }
                val palette = rawPalette.map(::balanceCoverColor)
                CoverFacts(
                    accentArgb = palette.firstOrNull(),
                    paletteArgbs = palette,
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                    rawPaletteArgbs = rawPalette,
                    playerToneArgb = playerTone ?: rawPalette.firstOrNull(),
                    playerField = playerField,
                )
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun <T> Bitmap.usePixels(block: (IntArray) -> T): T = try {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        block(pixels)
    } finally {
        recycle()
    }
}

private const val MaximumPaletteColors = 4
private const val MinimumPaletteDistanceSquared = 42 * 42

private data class CoverColorBucket(
    var count: Int = 0,
    var red: Long = 0,
    var green: Long = 0,
    var blue: Long = 0,
) {
    fun averageArgb(): Int {
        val averageRed = (red / count).toInt()
        val averageGreen = (green / count).toInt()
        val averageBlue = (blue / count).toInt()
        return opaqueArgb(averageRed, averageGreen, averageBlue)
    }

    fun score(): Long {
        val averageRed = (red / count).toInt()
        val averageGreen = (green / count).toInt()
        val averageBlue = (blue / count).toInt()
        val saturation = maxOf(averageRed, averageGreen, averageBlue) -
            minOf(averageRed, averageGreen, averageBlue)
        return count.toLong() * (96 + saturation)
    }
}

/**
 * Finds a colour family rather than averaging the entire image into grey. RGB
 * values are quantised into a small histogram and saturation slightly increases
 * a bucket's score. Very transparent, nearly black and nearly white pixels are
 * ignored because they usually describe padding or typography rather than art.
 */
internal fun dominantCoverArgb(pixels: IntArray): Int? {
    return representativeCoverArgbs(pixels, 1).firstOrNull()
}

/**
 * Builds a small, deterministic palette from a bounded pixel sample.
 *
 * Population still matters most, so a tiny saturated logo cannot replace the
 * colour that visually owns the cover. Once the strongest family is selected,
 * nearby buckets are skipped so the remaining entries can drive visibly
 * different ambient lights. Transparent, near-black and near-white pixels are
 * ignored for the same reason as [dominantCoverArgb].
 */
internal fun representativeCoverArgbs(pixels: IntArray, maxColors: Int = MaximumPaletteColors, balance: Boolean = true): List<Int> {
    if (pixels.isEmpty() || maxColors <= 0) return emptyList()

    val buckets = HashMap<Int, CoverColorBucket>(128)
    val step = max(1, pixels.size / MaximumSampledPixels)
    var index = 0
    while (index < pixels.size) {
        val pixel = pixels[index]
        val alpha = pixel ushr 24 and 0xFF
        val red = pixel ushr 16 and 0xFF
        val green = pixel ushr 8 and 0xFF
        val blue = pixel and 0xFF
        val luminance = (red * 299 + green * 587 + blue * 114) / 1_000
        if (alpha >= 160 && luminance in 22..242) {
            val key = (red shr 4 shl 8) or (green shr 4 shl 4) or (blue shr 4)
            val bucket = buckets.getOrPut(key) { CoverColorBucket() }
            bucket.count++
            bucket.red += red
            bucket.green += green
            bucket.blue += blue
        }
        index += step
    }

    val candidates = buckets.values
        .sortedWith(
            compareByDescending<CoverColorBucket>(CoverColorBucket::score)
                .thenByDescending(CoverColorBucket::count)
                .thenBy(CoverColorBucket::averageArgb),
        )
        .map(CoverColorBucket::averageArgb)

    val selected = ArrayList<Int>(maxColors.coerceAtMost(candidates.size))
    candidates.forEach { candidate ->
        if (selected.size >= maxColors) return@forEach
        if (selected.all { selectedColor -> colorDistanceSquared(candidate, selectedColor) >= MinimumPaletteDistanceSquared }) {
            selected += candidate
        }
    }
    return if (balance) selected.map(::balanceCoverColor) else selected
}

/** Keeps very dark/bright swatches useful as UI light without erasing their hue. */
private fun balanceCoverColor(color: Int): Int {
    var red = color ushr 16 and 0xFF
    var green = color ushr 8 and 0xFF
    var blue = color and 0xFF
    val luminance = (red * 299 + green * 587 + blue * 114) / 1_000
    if (luminance < 80) {
        red = mixChannel(red, 255, 0.28f)
        green = mixChannel(green, 255, 0.28f)
        blue = mixChannel(blue, 255, 0.28f)
    } else if (luminance > 218) {
        red = mixChannel(red, 0, 0.16f)
        green = mixChannel(green, 0, 0.16f)
        blue = mixChannel(blue, 0, 0.16f)
    }
    return opaqueArgb(red, green, blue)
}

private fun colorDistanceSquared(first: Int, second: Int): Int {
    val red = (first ushr 16 and 0xFF) - (second ushr 16 and 0xFF)
    val green = (first ushr 8 and 0xFF) - (second ushr 8 and 0xFF)
    val blue = (first and 0xFF) - (second and 0xFF)
    return red * red + green * green + blue * blue
}

private fun opaqueArgb(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

private fun mixChannel(source: Int, target: Int, fraction: Float): Int =
    (source + (target - source) * fraction).toInt().coerceIn(0, 255)

/** Coarser colour families avoid a broad pale highlight dominating a tonal background. */
internal fun playerCoverArgb(pixels: IntArray): Int? {
    val buckets = HashMap<Int, CoverColorBucket>()
    val step = max(1, pixels.size / MaximumSampledPixels)
    for (i in pixels.indices step step) {
        val pixel = pixels[i]
        val r = pixel ushr 16 and 255
        val g = pixel ushr 8 and 255
        val b = pixel and 255
        val high = maxOf(r, g, b)
        val low = minOf(r, g, b)
        // Ignore transparency, deep shadows, paper/skin highlights and neutral typography.
        if ((pixel ushr 24) < 160 || high !in 45..210 || high - low < high * 0.20f) continue
        val key = (r shr 5 shl 6) or (g shr 5 shl 3) or (b shr 5)
        buckets.getOrPut(key) { CoverColorBucket() }.apply {
            count++; red += r; green += g; blue += b
        }
    }
    return buckets.values.sortedWith(compareByDescending<CoverColorBucket> { it.score() }
        .thenBy { it.averageArgb() }).firstOrNull()?.averageArgb()
}
