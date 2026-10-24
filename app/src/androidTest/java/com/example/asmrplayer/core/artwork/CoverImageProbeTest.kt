package com.example.asmrplayer.core.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Generated in-memory images only; no owner files, media permissions or SAF trees. */
@RunWith(AndroidJUnit4::class)
class CoverImageProbeTest {
    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        return try { ByteArrayOutputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        } } finally { bitmap.recycle() }
    }
    @Test fun cappedNonMarkStreamReportsBoundsWithoutAllocatingReturnedBitmap() {
        val bytes = png(); val original = bytes.clone()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        CoverImageHeaderStream(ByteArrayInputStream(bytes)).use { input ->
            assertNull(BitmapFactory.decodeStream(input, null, bounds))
        }
        assertEquals(64, bounds.outWidth); assertEquals(32, bounds.outHeight)
        assertEquals("image/png", bounds.outMimeType); assertArrayEquals(original, bytes)
    }
    @Test fun insufficientHeaderBudgetCannotProduceUsableDimensions() {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        CoverImageHeaderStream(ByteArrayInputStream(png()), 1).use { input ->
            assertNull(BitmapFactory.decodeStream(input, null, bounds))
        }
        assertTrue(bounds.outWidth <= 0 || bounds.outHeight <= 0)
    }
}
