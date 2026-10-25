package com.example.asmrplayer.core.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.os.Build
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

internal class PreparedCoverImage(val file: File, val mime: String, private val generated: Boolean) : Closeable {
    override fun close() { if (generated) file.delete() }
}

// Only codec work is serialized. Never resolve a content URI or call the audio provider inside this gate.
private val preparationGate = ReentrantLock()
private val copyableCoverMimes = setOf("image/jpeg", "image/png", "image/webp", "image/bmp", "image/x-ms-bmp", "image/heif", "image/heic", "image/avif")

/** Reads a bounded PRIVATE staged source; never rewrites it or the owner's original file. */
internal fun prepareCoverImage(source: File, checkpoint: () -> Unit = {}): PreparedCoverImage {
    while (!preparationGate.tryLock(100, TimeUnit.MILLISECONDS)) checkpoint()
    try {
        checkpoint()
        check(source.length() in 1..MaximumCoverSourceBytes) { "COVER_SOURCE_TOO_LARGE" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        check(bounds.outWidth in 1..65536 && bounds.outHeight in 1..65536) { "COVER_INVALID_IMAGE" }
        if (canCopyCoverUnchanged(source.length(), bounds.outWidth, bounds.outHeight) && bounds.outMimeType in copyableCoverMimes) {
            return PreparedCoverImage(source, validateFolderCoverImage(source), false)
        }
        // All attempts have bounded pixels and encoded bytes. Never decode the full original.
        for (edge in listOf(2048, 1024, 512)) {
            checkpoint()
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                val size = sampledCoverSize(info.size.width, info.size.height, edge)
                decoder.setTargetSize(size.width, size.height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                decoder.setOnPartialImageListener { false }
            }
            try {
                check(bitmap.width <= edge && bitmap.height <= edge) { "COVER_SAMPLE_TOO_LARGE" }
                val alpha = bitmap.hasAlpha()
                val format = if (!alpha) Bitmap.CompressFormat.JPEG else if (Build.VERSION.SDK_INT >= 30)
                    Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                val mime = if (alpha) "image/webp" else "image/jpeg"
                val encoded = File.createTempFile("cover-prepared-", ".tmp", source.parentFile)
                var retained = false
                try {
                    for (quality in listOf(92, 84, 72, 60)) {
                        checkpoint()
                        var compressed = false
                        var overflow = false
                        CoverEncodedOutput(encoded.outputStream()).use { output ->
                            try { compressed = bitmap.compress(format, quality, output) }
                            catch (error: IOException) { if (!output.exceeded) throw error }
                            overflow = output.exceeded
                        }
                        check(compressed || overflow) { "COVER_ENCODE_FAILED" }
                        if (!overflow && encoded.length() in 1..MaximumFolderCoverBytes) {
                            check(validateFolderCoverImage(encoded) == mime) { "COVER_ENCODE_VERIFY_FAILED" }
                            checkpoint()
                            retained = true
                            return PreparedCoverImage(encoded, mime, true)
                        }
                    }
                } finally { if (!retained) encoded.delete() }
            } finally { bitmap.recycle() }
        }
        error("COVER_CANNOT_FIT")
    } finally { preparationGate.unlock() }
}

internal fun validateFolderCoverImage(file: File): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    check(bounds.outWidth in 1..65536 && bounds.outHeight in 1..65536) { "COVER_INVALID_IMAGE" }
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val scale = minOf(1f, 128f / maxOf(info.size.width, info.size.height))
        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
            (info.size.height * scale).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setOnPartialImageListener { false }
    }
    bitmap.recycle()
    return bounds.outMimeType ?: error("COVER_INVALID_IMAGE")
}
