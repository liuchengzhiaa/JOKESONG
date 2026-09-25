package com.example.asmrplayer.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServicePolicyTest {
    @Test
    fun `empty session may stop after task removal`() {
        assertTrue(shouldStopPlaybackServiceAfterTaskRemoved(mediaItemCount = 0))
    }

    @Test
    fun `paused queue remains available after task removal`() {
        assertFalse(shouldStopPlaybackServiceAfterTaskRemoved(mediaItemCount = 1))
        assertFalse(shouldStopPlaybackServiceAfterTaskRemoved(mediaItemCount = 15))
    }
}
