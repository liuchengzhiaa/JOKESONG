package com.example.asmrplayer.core.metadata

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Only disposable generated PCM in app cache. Never opens the owner's library. */
@RunWith(AndroidJUnit4::class)
class NativeTagRoundTripTest {
    @Test fun unicodeTagsAndArtworkPreserveGeneratedPcmAndOriginalFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = File.createTempFile("metadata-original-", ".wav", context.cacheDir)
        val staged = File.createTempFile("metadata-stage-", ".wav", context.cacheDir)
        try {
            val pcm = ByteBuffer.allocate(16000).order(ByteOrder.LITTLE_ENDIAN)
            repeat(8000) { pcm.putShort((kotlin.math.sin(it * 2 * Math.PI * 440 / 8000) * 4000).toInt().toShort()) }
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).put("RIFF".toByteArray()).putInt(16036)
                .put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000)
                .putShort(2).putShort(16).put("data".toByteArray()).putInt(16000).array()
            original.writeBytes(header + pcm.array()); original.copyTo(staged, overwrite = true)
            val originalHash = streamDigest(original.inputStream())
            val audio = FileInputStream(original).use { payloadDigest(AudioFile(it.channel), "wav") }
            val beforeProbe = probeAudio(ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY))
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF4477AA.toInt()) }
            val art = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); it.toByteArray() }; bitmap.recycle()
            ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                assertTrue(TagLib.savePropertyMap(fd.dup().detachFd(), hashMapOf("TITLE" to arrayOf("测试タイトル"), "ARTIST" to arrayOf("声优"))))
                val properties = TagLib.getMetadata(fd.dup().detachFd(), false)!!.propertyMap
                assertEquals("测试タイトル", properties["TITLE"]!!.single())
                assertEquals("声优", properties["ARTIST"]!!.single())
                assertTrue(TagLib.savePictures(fd.dup().detachFd(), arrayOf(Picture(art, "Front Cover", "Front Cover", "image/jpeg"))))
                assertArrayEquals(art, TagLib.getPictures(fd.dup().detachFd()).single().data)
                val afterCover = TagLib.getMetadata(fd.dup().detachFd(), false)!!.propertyMap
                assertEquals(properties.mapValues { it.value.toList() }, afterCover.mapValues { it.value.toList() })
            }
            assertEquals(originalHash, streamDigest(original.inputStream()))
            assertEquals(audio, FileInputStream(staged).use { payloadDigest(AudioFile(it.channel), "wav") })
            assertEquals(beforeProbe, probeAudio(ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY)))
        } finally { original.delete(); staged.delete() }
    }
}
