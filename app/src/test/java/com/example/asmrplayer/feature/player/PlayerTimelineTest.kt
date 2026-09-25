package com.example.asmrplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTimelineTest {
    @Test
    fun `parses long form duration labels`() {
        assertEquals(2_538_000L, parseDurationLabel("42:18"))
        assertEquals(3_723_000L, parseDurationLabel("1:02:03"))
        assertNull(parseDurationLabel("1:72"))
        assertNull(parseDurationLabel("unknown"))
    }

    @Test
    fun `seek and skip clamp to track bounds`() {
        val timeline = PlayerTimeline(durationMs = 100_000L)
            .seekToProgress(0.5f)

        assertEquals(50_000L, timeline.positionMs)
        assertEquals(0L, timeline.skipBy(-80_000L).positionMs)
        assertEquals(100_000L, timeline.skipBy(80_000L).positionMs)
    }

    @Test
    fun `bookmarks are ordered and deduplicated near the same instant`() {
        val timeline = PlayerTimeline(durationMs = 100_000L, positionMs = 30_000L)
            .addBookmark()
            .copy(positionMs = 30_500L)
            .addBookmark()
            .copy(positionMs = 10_000L)
            .addBookmark()

        assertEquals(listOf(10_000L, 30_000L), timeline.bookmarksMs)
    }

    @Test
    fun `formats short and long playback times`() {
        assertEquals("0:09", formatPlaybackTime(9_500L))
        assertEquals("42:18", formatPlaybackTime(2_538_000L))
        assertEquals("1:02:03", formatPlaybackTime(3_723_000L))
        assertTrue(PlayerTimeline(10_000L, 5_000L).progress in 0.49f..0.51f)
    }
}
