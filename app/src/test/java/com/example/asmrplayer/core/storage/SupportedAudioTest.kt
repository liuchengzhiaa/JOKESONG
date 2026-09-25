package com.example.asmrplayer.core.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedAudioTest {
    @Test
    fun `accepts every guaranteed extension case insensitively`() {
        listOf("mp3", "FLAC", "wav", "M4A", "aac", "OGG", "opus").forEach { extension ->
            assertTrue(SupportedAudio.isSupported("track.$extension", null))
        }
    }

    @Test
    fun `accepts audio and ogg mime types when extension is missing`() {
        assertTrue(SupportedAudio.isSupported("track", "audio/mpeg"))
        assertTrue(SupportedAudio.isSupported("track", "application/ogg"))
    }

    @Test
    fun `rejects unrelated files`() {
        assertFalse(SupportedAudio.isSupported("cover.jpg", "image/jpeg"))
        assertFalse(SupportedAudio.isSupported("notes.txt", "text/plain"))
    }
}
