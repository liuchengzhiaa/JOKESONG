package com.example.asmrplayer.feature.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFollowingTest {

    @Test
    fun `the current line is anchored a lead-in below the top`() {
        // The line being sung lands four rows down with the next three already
        // visible, instead of pressed against the top edge.
        assertEquals(0, LyricsFollowing.anchorIndex(centreIndex = 0, lineCount = 20))
        assertEquals(2, LyricsFollowing.anchorIndex(centreIndex = 5, lineCount = 20))
        assertEquals(7, LyricsFollowing.anchorIndex(centreIndex = 10, lineCount = 20))
    }

    @Test
    fun `the anchor never points before the start of the list`() {
        // A three-line file opened at its second line would otherwise ask to scroll to
        // a negative index, which is an exception from the list rather than a no-op.
        assertEquals(0, LyricsFollowing.anchorIndex(centreIndex = -1, lineCount = 20))
        assertEquals(0, LyricsFollowing.anchorIndex(centreIndex = 1, lineCount = 3))
        assertEquals(0, LyricsFollowing.anchorIndex(centreIndex = 3, lineCount = 2))
    }

    @Test
    fun `the anchor never points past the end of the list`() {
        // The last line still gets its lead-in from the lines before it,...
        assertEquals(6, LyricsFollowing.anchorIndex(centreIndex = 9, lineCount = 10))
        // ...and an index past the end clamps to the last one the list actually has.
        assertEquals(9, LyricsFollowing.anchorIndex(centreIndex = 40, lineCount = 10))
    }

    @Test
    fun `an empty list anchors at zero so a caller can scroll unconditionally`() {
        assertEquals(0, LyricsFollowing.anchorIndex(centreIndex = 0, lineCount = 0))
        assertEquals(0, LyricsFollowing.anchorIndex(centreIndex = 5, lineCount = 0))
        assertEquals(0, LyricsFollowing.anchorIndex(centreIndex = 0, lineCount = 1))
    }

    @Test
    fun `following moves the list only once playback has reached a line`() {
        assertTrue(LyricsFollowing.shouldFollow(centreIndex = 0, isFollowing = true))
        assertTrue(LyricsFollowing.shouldFollow(centreIndex = 4, isFollowing = true))
        // Before the first line starts there is nothing to scroll to, and that is the
        // state every track begins in.
        assertFalse(LyricsFollowing.shouldFollow(centreIndex = -1, isFollowing = true))
    }

    @Test
    fun `a reader who scrolled away is not moved again`() {
        assertFalse(LyricsFollowing.shouldFollow(centreIndex = 4, isFollowing = false))
        assertFalse(LyricsFollowing.shouldFollow(centreIndex = -1, isFollowing = false))
    }
}
