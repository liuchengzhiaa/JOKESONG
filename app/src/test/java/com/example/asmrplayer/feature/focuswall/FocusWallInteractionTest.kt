package com.example.asmrplayer.feature.focuswall

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusWallInteractionTest {
    @Test
    fun `first tap focuses without activating`() {
        assertEquals(
            FocusWallIntent.Focus("track-2"),
            resolveCardTap(focusedItemId = "track-1", tappedItemId = "track-2"),
        )
    }

    @Test
    fun `second tap activates focused item`() {
        assertEquals(
            FocusWallIntent.Activate("track-2"),
            resolveCardTap(focusedItemId = "track-2", tappedItemId = "track-2"),
        )
    }
}
