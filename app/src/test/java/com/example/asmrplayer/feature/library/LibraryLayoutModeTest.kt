package com.example.asmrplayer.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLayoutModeTest {
    @Test
    fun `next cycles through every layout and returns to the first`() {
        var mode = LibraryLayoutMode.DetailedList

        val visited = buildList {
            repeat(LibraryLayoutMode.entries.size) {
                add(mode)
                mode = mode.next()
            }
        }

        assertEquals(LibraryLayoutMode.entries.toList(), visited)
        assertEquals(LibraryLayoutMode.DetailedList, mode)
    }

    @Test
    fun `the default layout is the focus wall`() {
        assertEquals(LibraryLayoutMode.FocusWall, LibraryLayoutMode.Default)
    }

    @Test
    fun `an ordinal round trips`() {
        LibraryLayoutMode.entries.forEach { mode ->
            assertEquals(mode, LibraryLayoutMode.fromOrdinal(mode.ordinal))
        }
    }

    @Test
    fun `an out of range ordinal falls back to a valid layout`() {
        assertEquals(LibraryLayoutMode.DetailedList, LibraryLayoutMode.fromOrdinal(-3))
        assertEquals(LibraryLayoutMode.FocusWall, LibraryLayoutMode.fromOrdinal(99))
    }
}
