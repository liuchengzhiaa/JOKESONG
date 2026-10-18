package com.example.asmrplayer.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioOutputTest {
    private fun candidate(kind: AudioOutputKind, name: String? = null) =
        AudioOutputCandidate(kind = kind, name = name)

    @Test
    fun `nothing connected names nothing`() {
        assertNull(pickActiveOutput(emptyList()))
    }

    @Test
    fun `a wired output outranks a paired one`() {
        val picked = pickActiveOutput(
            listOf(
                candidate(AudioOutputKind.Bluetooth, "WH-1000XM4"),
                candidate(AudioOutputKind.Wired, "Wired headset"),
            ),
        )

        // Plugging in is the more recent, more deliberate act, so it wins even while
        // a paired headset is still in range.
        assertEquals(AudioOutputKind.Wired, picked?.kind)
        assertEquals("Wired headset", picked?.name)
    }

    @Test
    fun `bluetooth outranks the built-in speaker`() {
        val picked = pickActiveOutput(
            listOf(
                candidate(AudioOutputKind.BuiltIn, "Speaker"),
                candidate(AudioOutputKind.Bluetooth, "WH-1000XM4"),
            ),
        )

        assertEquals(AudioOutputKind.Bluetooth, picked?.kind)
    }

    @Test
    fun `a lone built-in speaker is still worth naming`() {
        val picked = pickActiveOutput(listOf(candidate(AudioOutputKind.BuiltIn, "Speaker")))

        assertEquals(AudioOutputKind.BuiltIn, picked?.kind)
        assertEquals("Speaker", picked?.name)
    }

    @Test
    fun `an unclassified output is better than no mark at all`() {
        val picked = pickActiveOutput(
            listOf(
                candidate(AudioOutputKind.Other, "Something"),
                candidate(AudioOutputKind.BuiltIn, null),
            ),
        )

        assertEquals(AudioOutputKind.BuiltIn, picked?.kind)
    }

    @Test
    fun `the order the platform reported them in does not decide`() {
        val forward = pickActiveOutput(
            listOf(candidate(AudioOutputKind.BuiltIn, "Speaker"), candidate(AudioOutputKind.Wired, "Jack")),
        )
        val reversed = pickActiveOutput(
            listOf(candidate(AudioOutputKind.Wired, "Jack"), candidate(AudioOutputKind.BuiltIn, "Speaker")),
        )

        assertEquals(forward, reversed)
    }

    @Test
    fun `two outputs of the same class keep the platform's order`() {
        val picked = pickActiveOutput(
            listOf(
                candidate(AudioOutputKind.Wired, "First"),
                candidate(AudioOutputKind.Wired, "Second"),
            ),
        )

        assertEquals("First", picked?.name)
    }
}
