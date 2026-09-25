package com.example.asmrplayer.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateTest {
    @Test
    fun `progress is the position over the duration`() {
        val state = PlaybackState(mediaId = "track-1", positionMs = 30_000L, durationMs = 120_000L)

        assertEquals(0.25f, state.progress, 0.0001f)
    }

    @Test
    fun `progress is zero and not seekable without a duration`() {
        val state = PlaybackState(mediaId = "track-1", positionMs = 30_000L, durationMs = 0L)

        assertEquals(0f, state.progress, 0.0001f)
        assertFalse(state.hasDuration)
        assertEquals(0L, state.positionForFraction(0.5f))
    }

    @Test
    fun `progress never exceeds one when the position overshoots`() {
        val state = PlaybackState(positionMs = 200_000L, durationMs = 120_000L)

        assertEquals(1f, state.progress, 0.0001f)
    }

    @Test
    fun `a slider fraction maps onto a seekable position`() {
        val state = PlaybackState(durationMs = 120_000L)

        assertEquals(60_000L, state.positionForFraction(0.5f))
        assertEquals(0L, state.positionForFraction(-1f))
        assertEquals(120_000L, state.positionForFraction(2f))
    }

    @Test
    fun `only the current media item is current`() {
        val state = PlaybackState(mediaId = "track-2")

        assertTrue(state.isCurrent("track-2"))
        assertFalse(state.isCurrent("track-1"))
        assertFalse(state.isCurrent(null))
    }

    @Test
    fun `toggle pauses while play when ready`() {
        val state = PlaybackState(playWhenReady = true, hasQueue = true)

        assertEquals(PlaybackCommand.Pause, toggleCommandFor(state))
    }

    @Test
    fun `toggle plays when paused with a queue`() {
        val state = PlaybackState(playWhenReady = false, hasQueue = true)

        assertEquals(PlaybackCommand.Play, toggleCommandFor(state))
    }

    @Test
    fun `toggle is inert without a queue`() {
        val state = PlaybackState(playWhenReady = false, hasQueue = false)

        assertEquals(PlaybackCommand.None, toggleCommandFor(state))
    }

    @Test
    fun `a disconnected state has no queue and no position`() {
        val state = PlaybackState()

        assertFalse(state.isConnected)
        assertFalse(state.hasQueue)
        assertEquals(0L, state.positionMs)
        assertEquals(0f, state.progress, 0.0001f)
    }
}
