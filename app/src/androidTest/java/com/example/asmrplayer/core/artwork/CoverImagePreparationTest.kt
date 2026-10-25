package com.example.asmrplayer.core.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Generated private fixtures only. No media permissions, user trees or network. */
@RunWith(AndroidJUnit4::class)
class CoverImagePreparationTest {
    private fun fixture(block: (File) -> Unit) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val folder = File.createTempFile("cover-codec-test-", ".dir", cache)
        check(folder.delete() && folder.mkdir())
        try { block(folder) } finally { folder.listFiles().orEmpty().forEach { it.delete() }; folder.delete() }
    }
    private fun png(file: File, width: Int, height: Int, noise: Boolean = false, alpha: Boolean = false) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.setHasAlpha(alpha)
            if (noise) {
                val random = Random(7)
                val row = IntArray(width)
                for (y in 0 until height) {
                    for (x in row.indices) row[x] = 0xFF000000.toInt() or random.nextInt(0x1000000)
                    bitmap.setPixels(row, 0, width, 0, y, width, 1)
                }
            } else {
                bitmap.eraseColor(if (alpha) 0x80775588.toInt() else 0xFF775588.toInt())
                if (alpha) Canvas(bitmap).drawRect(0f, 0f, width / 3f, height.toFloat(),
                    Paint().apply { color = 0; xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC) })
            }
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }
    private fun hash(file: File): List<Byte> {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val b = ByteArray(32768); while (true) {
            val n = input.read(b); if (n < 0) break; digest.update(b, 0, n)
        } }
        return digest.digest().toList()
    }
    @Test fun compliantImageKeepsOriginalBytesAndIsNotDeletedByResultClose() = fixture { dir ->
        val source = File(dir, "small.png"); png(source, 64, 32)
        val before = hash(source)
        prepareCoverImage(source).use { result -> assertEquals(source, result.file); assertEquals("image/png", result.mime) }
        assertTrue(source.exists()); assertEquals(before, hash(source)); assertEquals(1, dir.listFiles()!!.size)
    }
    @Test fun noisyPngOverEightMiBIsSampledCompressedValidatedAndSourcePreserved() = fixture { dir ->
        val source = File(dir, "noise.png"); png(source, 2304, 1536, noise = true)
        assertTrue(source.length() > MaximumFolderCoverBytes)
        val before = hash(source)
        var prepared: File? = null
        prepareCoverImage(source).use { result ->
            prepared = result.file
            assertNotEquals(source, result.file); assertEquals("image/jpeg", result.mime)
            assertTrue(result.file.length() in 1..MaximumFolderCoverBytes)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(result.file.path, bounds)
            assertEquals(2048, bounds.outWidth); assertTrue(bounds.outHeight in 1365..1366)
            assertEquals("image/jpeg", validateFolderCoverImage(result.file))
            assertEquals(before, hash(source))
        }
        assertFalse(prepared!!.exists()); assertEquals(1, dir.listFiles()!!.size)
    }
    @Test fun transparentWidePngPreservesAspectAndTransparencyAfterConversion() = fixture { dir ->
        val source = File(dir, "alpha.png"); png(source, 3000, 1000, alpha = true)
        val before = hash(source)
        prepareCoverImage(source).use { result ->
            assertEquals("image/webp", result.mime)
            val bitmap = BitmapFactory.decodeFile(result.file.path, BitmapFactory.Options().apply { inSampleSize = 16 })!!
            try {
                assertEquals(3f, bitmap.width.toFloat() / bitmap.height, 0.08f)
                assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, bitmap.height / 2)))
                assertTrue(kotlin.math.abs(android.graphics.Color.alpha(bitmap.getPixel(bitmap.width - 1, bitmap.height / 2)) - 128) <= 2)
            } finally { bitmap.recycle() }
        }
        assertEquals(before, hash(source)); assertEquals(1, dir.listFiles()!!.size)
    }
    @Test fun invalidAndTruncatedSourcesFailWithoutLeavingEncodedCopies() = fixture { dir ->
        val source = File(dir, "bad.png"); source.writeBytes(byteArrayOf(1,2,3))
        try { prepareCoverImage(source).close(); fail() } catch (_: Exception) { }
        assertEquals(1, dir.listFiles()!!.size)
        png(source, 3000, 1000)
        RandomAccessFile(source, "rw").use { it.setLength(it.length() / 2) }
        val before = hash(source)
        try { prepareCoverImage(source).close(); fail() } catch (_: Exception) { }
        assertEquals(before, hash(source)); assertEquals(1, dir.listFiles()!!.size)
    }
    @Test fun sourceSafetyLimitIsCheckedBeforeDecoding() = fixture { dir ->
        val source = File(dir, "oversized.png")
        RandomAccessFile(source, "rw").use { it.setLength(MaximumCoverSourceBytes + 1) }
        try { prepareCoverImage(source).close(); fail() } catch (_: IllegalStateException) { }
        assertEquals(MaximumCoverSourceBytes + 1, source.length()); assertEquals(1, dir.listFiles()!!.size)
    }
    @Test fun cancellationAfterEncodingRecyclesAndRemovesOnlyGeneratedTemporaryCopy() = fixture { dir ->
        val source = File(dir, "cancel.png"); png(source, 3000, 1000)
        val before = hash(source); var checks = 0
        try { prepareCoverImage(source) { if (++checks == 4) throw CancellationException() }.close(); fail() }
        catch (_: CancellationException) { }
        assertEquals(before, hash(source)); assertEquals(1, dir.listFiles()!!.size)
    }
}
