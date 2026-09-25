package com.example.asmrplayer.feature.focuswall

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusWallInteractionTest {
    @Test
    fun `tapping another card moves focus to it`() {
        assertEquals(
            FocusWallIntent.Focus("track-2"),
            resolveCardTap(focusedItemId = "track-1", tappedItemId = "track-2"),
        )
    }

    @Test
    fun `tapping the focused card activates it`() {
        assertEquals(
            FocusWallIntent.Activate("track-2"),
            resolveCardTap(focusedItemId = "track-2", tappedItemId = "track-2"),
        )
    }

    @Test
    fun `the first tap with nothing focused focuses that card`() {
        assertEquals(
            FocusWallIntent.Focus("track-1"),
            resolveCardTap(focusedItemId = null, tappedItemId = "track-1"),
        )
    }

    @Test
    fun `a quick double tap focuses then activates`() {
        // There is no double-tap gesture bound to the cards. "Double tap to open"
        // is just this rule applied twice, which is what lets the first tap be
        // delivered immediately instead of waiting out the double-tap timeout.
        val afterFirstTap = resolveCardTap(focusedItemId = "track-1", tappedItemId = "track-2")
        val focusedItemId = (afterFirstTap as FocusWallIntent.Focus).itemId
        val afterSecondTap = resolveCardTap(focusedItemId = focusedItemId, tappedItemId = "track-2")

        assertEquals(FocusWallIntent.Activate("track-2"), afterSecondTap)
    }

    @Test
    fun `revealing the playing item is offered when it is on the wall`() {
        assertEquals(
            true,
            canRevealPlayingItem(
                playingItemId = "track-2",
                listedItemIds = setOf("track-1", "track-2"),
                // Even with no origin recorded, the item is right here.
                playbackOriginKnown = false,
            ),
        )
    }

    @Test
    fun `an item in another folder is reachable through the recorded origin`() {
        assertEquals(
            true,
            canRevealPlayingItem(
                playingItemId = "track-9",
                listedItemIds = setOf("track-1", "track-2"),
                playbackOriginKnown = true,
            ),
        )
    }

    @Test
    fun `an item in another folder is unreachable once the origin is lost`() {
        // A queue that outlived the process: playback continues, but which folder
        // it was started from is gone, so there is nowhere to follow it back to.
        assertEquals(
            false,
            canRevealPlayingItem(
                playingItemId = "track-9",
                listedItemIds = setOf("track-1", "track-2"),
                playbackOriginKnown = false,
            ),
        )
    }

    @Test
    fun `nothing playing offers nothing to reveal`() {
        assertEquals(
            false,
            canRevealPlayingItem(
                playingItemId = null,
                listedItemIds = setOf("track-1"),
                // An origin from an earlier session does not make this available.
                playbackOriginKnown = true,
            ),
        )
    }
}
