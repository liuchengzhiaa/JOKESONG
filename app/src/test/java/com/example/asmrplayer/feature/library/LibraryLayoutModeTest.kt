package com.example.asmrplayer.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLayoutModeTest {
    @Test
    fun `only wall modes supply their own corner tools`() {
        assertEquals(
            listOf(LibraryLayoutMode.FoliaWall),
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
    fun `the default layout is the Folia wall`() {
        assertEquals(LibraryLayoutMode.FoliaWall, LibraryLayoutMode.Default)
    }

    @Test
    fun `both legacy wall choices migrate to Folia without changing list and grid values`() {
        assertEquals(LibraryLayoutMode.FoliaWall, LibraryLayoutMode.fromOrdinal(3))
        assertEquals(LibraryLayoutMode.FoliaWall, LibraryLayoutMode.fromOrdinal(4))
        assertEquals(listOf(0, 1, 2, 4), LibraryLayoutMode.entries.map { it.storedValue })
        assertEquals(4, com.example.asmrplayer.core.storage.AppSettings.DefaultLibraryLayoutOrdinal)
    }

    @Test
    fun `an ordinal round trips`() {
        LibraryLayoutMode.entries.forEach { mode ->
            assertEquals(mode, LibraryLayoutMode.fromOrdinal(mode.storedValue))
        }
    }

    @Test
    fun `an out of range ordinal falls back to a valid layout`() {
        assertEquals(LibraryLayoutMode.DetailedList, LibraryLayoutMode.fromOrdinal(-3))
        assertEquals(LibraryLayoutMode.FoliaWall, LibraryLayoutMode.fromOrdinal(99))
    }
}
