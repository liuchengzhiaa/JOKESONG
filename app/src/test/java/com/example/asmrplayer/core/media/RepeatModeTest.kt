package com.example.asmrplayer.core.media

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatModeTest {
    @Test
    fun `every player mode maps onto its own repeat mode`() {
        assertEquals(RepeatMode.Off, RepeatMode.ofPlayerMode(Player.REPEAT_MODE_OFF))
        assertEquals(RepeatMode.All, RepeatMode.ofPlayerMode(Player.REPEAT_MODE_ALL))
        assertEquals(RepeatMode.One, RepeatMode.ofPlayerMode(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `the two translations are inverses`() {
        RepeatMode.entries.forEach { mode ->
            assertEquals(mode, RepeatMode.ofPlayerMode(mode.toPlayerMode()))
        }
    }

    @Test
    fun `a mode the app does not know reads as no repeat`() {
        // A value Media3 might add later. Degrading to Off keeps an unasked-for mode
        // off the owner's screen rather than inventing a repeat he did not choose.
        assertEquals(RepeatMode.Off, RepeatMode.ofPlayerMode(99))
        assertEquals(RepeatMode.Off, RepeatMode.ofPlayerMode(-1))
    }

    @Test
    fun `tapping the control walks off, all, one and back`() {
        assertEquals(RepeatMode.All, RepeatMode.Off.next())
        assertEquals(RepeatMode.One, RepeatMode.All.next())
        assertEquals(RepeatMode.Off, RepeatMode.One.next())
    }

    @Test
    fun `three taps return to where the walk started`() {
        RepeatMode.entries.forEach { mode ->
            assertEquals(mode, mode.next().next().next())
        }
    }

    @Test
    fun `the default mode is off`() {
        assertEquals(RepeatMode.Off, PlaybackState().repeatMode)
    }
}
