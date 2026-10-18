package com.example.asmrplayer.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLayoutModeTest {
    @Test
    fun `only wall modes supply their own corner tools`() {
        assertEquals(
            listOf(LibraryLayoutMode.FocusWall, LibraryLayoutMode.FoliaWall),
            LibraryLayoutMode.entries.filter { it.isWall },
        )
        assertEquals(
            listOf(LibraryLayoutMode.DetailedList, LibraryLayoutMode.CompactList, LibraryLayoutMode.Grid),
            LibraryLayoutMode.entries.filterNot { it.isWall },
        )
    }

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
    fun `folia wall is immediately to the right of the original focus wall`() {
        assertEquals(
            LibraryLayoutMode.FoliaWall,
            LibraryLayoutMode.entries[LibraryLayoutMode.FocusWall.ordinal + 1],
        )
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
        assertEquals(LibraryLayoutMode.FoliaWall, LibraryLayoutMode.fromOrdinal(99))
    }
}
