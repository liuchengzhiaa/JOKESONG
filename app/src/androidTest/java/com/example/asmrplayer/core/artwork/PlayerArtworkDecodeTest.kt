package com.example.asmrplayer.core.artwork

import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Generated single-colour PNG fixtures only, not any media from the owner's library. */
@RunWith(AndroidJUnit4::class)
class PlayerArtworkDecodeTest {
    @Test fun displayDecoderAndPalettePreserveWideTallAndSquareSourceDimensions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((width, height) in listOf(300 to 500, 800 to 450, 400 to 400)) {
            val file = File.createTempFile("generated-player-cover-", ".png", context.cacheDir)
            try {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(0xFF775588.toInt())
                    file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { bitmap.recycle() }
                val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context)
                    .data(file.toUri()).size(200, 200).scale(Scale.FIT).precision(Precision.INEXACT).build())
                assertTrue(result is SuccessResult)
                val image = (result as SuccessResult).image
                assertEquals(width.toFloat() / height, image.width.toFloat() / image.height, 0.01f)
                val facts = CoverAccentLoader.resolve(context, file.toUri().toString())!!
                assertEquals(width, facts.width)
                assertEquals(height, facts.height)
                assertEquals(9, facts.playerField!!.colours.size)
            } finally { file.delete() }
        }
    }
}
