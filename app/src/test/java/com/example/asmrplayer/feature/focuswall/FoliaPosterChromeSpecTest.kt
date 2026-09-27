package com.example.asmrplayer.feature.focuswall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoliaPosterChromeSpecTest {
    @Test
    fun `poster bands keep the measured Folia order`() {
        assertTrue(FoliaPosterChromeSpec.StatusTop < FoliaPosterChromeSpec.HeaderTop)
        assertTrue(FoliaPosterChromeSpec.HeaderTop < FoliaPosterChromeSpec.PreviousLyricCenter)
        assertTrue(FoliaPosterChromeSpec.PreviousLyricCenter < FoliaPosterChromeSpec.CurrentLyricCenter)
        assertTrue(FoliaPosterChromeSpec.CurrentLyricCenter < FoliaPosterChromeSpec.FollowingLyricCenter)
        assertTrue(FoliaPosterChromeSpec.FollowingLyricCenter < FoliaPosterChromeSpec.ControlsTop)
        assertTrue(FoliaPosterChromeSpec.ControlsTop < FoliaPosterChromeSpec.ProgressTop)
        assertTrue(FoliaPosterChromeSpec.ProgressTop < 1f)
    }

    @Test
    fun `current lyric owns the typographic hierarchy`() {
        assertTrue(FoliaPosterChromeSpec.CurrentLyricFont > FoliaPosterChromeSpec.PreviousLyricFont)
        assertTrue(FoliaPosterChromeSpec.PreviousLyricFont > FoliaPosterChromeSpec.HeaderTitleFont)
        assertEquals(
            FoliaPosterChromeSpec.PreviousLyricFont,
            FoliaPosterChromeSpec.FollowingLyricFont,
            0.0001f,
        )
        assertTrue(FoliaPosterChromeSpec.FollowingLyricFont > FoliaPosterChromeSpec.HeaderMetadataFont)
    }

    @Test
    fun `current lyric is vertically centred between context lines`() {
        assertEquals(0.5f, FoliaPosterChromeSpec.CurrentLyricCenter, 0.0001f)
        val before = FoliaPosterChromeSpec.CurrentLyricCenter -
            FoliaPosterChromeSpec.PreviousLyricCenter
        val after = FoliaPosterChromeSpec.FollowingLyricCenter -
            FoliaPosterChromeSpec.CurrentLyricCenter
        assertEquals(before, after, 0.0001f)
    }

    @Test
    fun `reference insets stay asymmetric`() {
        assertEquals(0.043f, FoliaPosterChromeSpec.HorizontalInset, 0.001f)
        assertEquals(0.085f, FoliaPosterChromeSpec.ContextInset, 0.001f)
        assertTrue(FoliaPosterChromeSpec.ContextInset > FoliaPosterChromeSpec.HorizontalInset)
    }
}
