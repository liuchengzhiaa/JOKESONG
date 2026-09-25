package com.example.asmrplayer.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCompletionTest {
    @Test
    fun `unknown duration never completes`() {
        assertFalse(shouldMarkPlaybackCompleted(positionMs = 10_000L, durationMs = 0L))
    }

    @Test
    fun `ninety five percent completes long form audio`() {
        // Use a duration whose last 5% exceeds 30 seconds so this test isolates
        // the percentage rule instead of also satisfying the near-end rule.
        assertFalse(shouldMarkPlaybackCompleted(positionMs = 949_999L, durationMs = 1_000_000L))
        assertTrue(shouldMarkPlaybackCompleted(positionMs = 950_000L, durationMs = 1_000_000L))
    }

    @Test
    fun `last thirty seconds completes long form audio`() {
        assertFalse(shouldMarkPlaybackCompleted(positionMs = 569_999L, durationMs = 600_000L))
        assertTrue(shouldMarkPlaybackCompleted(positionMs = 570_000L, durationMs = 600_000L))
    }

    @Test
    fun `short audio does not complete immediately`() {
        assertFalse(shouldMarkPlaybackCompleted(positionMs = 1L, durationMs = 20_000L))
        assertTrue(shouldMarkPlaybackCompleted(positionMs = 19_000L, durationMs = 20_000L))
    }
}
