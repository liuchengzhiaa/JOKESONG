package com.example.asmrplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalNameComparatorTest {
    @Test
    fun `sorts numeric filename runs by numeric value`() {
        val names = listOf("Track 10", "Track 2", "Track 01", "Track 1")

        assertEquals(
            listOf("Track 1", "Track 01", "Track 2", "Track 10"),
            names.sortedWith(NaturalNameComparator),
        )
    }

    @Test
    fun `sort is deterministic when case differs`() {
        val names = listOf("beta", "Alpha", "alpha")

        assertEquals(
            listOf("Alpha", "alpha", "beta"),
            names.sortedWith(NaturalNameComparator),
        )
    }
}
