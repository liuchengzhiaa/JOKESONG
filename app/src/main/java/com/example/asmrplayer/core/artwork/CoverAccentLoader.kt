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
 * Loads only a thumbnail-sized version of the focused cover, then caches its
 * representative colour. Non-focused cards never pay this decoding cost.
 */
internal object CoverAccentLoader {
    private val cache = LruCache<String, Int>(96)

    suspend fun resolve(context: Context, artworkUri: String?): Int? {
        artworkUri ?: return null
        synchronized(cache) { cache.get(artworkUri) }?.let { return it }

        val result = withContext(Dispatchers.IO) {
            decodeBoundedBitmap(context, artworkUri.toUri())?.usePixels(::dominantCoverArgb)
        }
        if (result != null) synchronized(cache) { cache.put(artworkUri, result) }
        return result
    }

    private fun decodeBoundedBitmap(context: Context, uri: Uri): Bitmap? {
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
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun Bitmap.usePixels(block: (IntArray) -> Int?): Int? = try {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        block(pixels)
    } finally {
        recycle()
    }
}

/**
 * Finds a colour family rather than averaging the entire image into grey. RGB
 * values are quantised into a small histogram and saturation slightly increases
 * a bucket's score. Very transparent, nearly black and nearly white pixels are
 * ignored because they usually describe padding or typography rather than art.
 */
internal fun dominantCoverArgb(pixels: IntArray): Int? {
    if (pixels.isEmpty()) return null
    data class Bucket(var count: Int = 0, var red: Long = 0, var green: Long = 0, var blue: Long = 0)

    val buckets = HashMap<Int, Bucket>(128)
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
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.count++
            bucket.red += red
            bucket.green += green
            bucket.blue += blue
        }
        index += step
    }

    val winner = buckets.values.maxByOrNull { bucket ->
        val red = (bucket.red / bucket.count).toInt()
        val green = (bucket.green / bucket.count).toInt()
        val blue = (bucket.blue / bucket.count).toInt()
        val saturation = maxOf(red, green, blue) - minOf(red, green, blue)
        bucket.count * (96 + saturation)
    } ?: return null

    var red = (winner.red / winner.count).toInt()
    var green = (winner.green / winner.count).toInt()
    var blue = (winner.blue / winner.count).toInt()
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
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}

private fun mixChannel(source: Int, target: Int, fraction: Float): Int =
    (source + (target - source) * fraction).toInt().coerceIn(0, 255)
