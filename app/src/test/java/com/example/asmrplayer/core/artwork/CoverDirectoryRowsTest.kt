package com.example.asmrplayer.core.artwork

import org.junit.Assert.*
import org.junit.Test

class CoverDirectoryRowsTest {
    @Test fun `natural ordering and kinds survive sorting`() {
        val rows = CoverDirectoryRows()
        rows.add(CoverDocument("u10", "10.mp3"), CoverDirectoryRow.Audio)
        rows.add(CoverDocument("u2", "2.mp3"), CoverDirectoryRow.Audio)
        rows.add(CoverDocument("u1", "1.mp3"), CoverDirectoryRow.Folder)
        assertEquals(listOf("1.mp3", "2.mp3", "10.mp3"), rows.sorted().map { it.document.name })
        assertEquals(CoverDirectoryRow.Folder, rows.sorted().first().kind)
    }
    @Test fun `one folder holds ten thousand ordered metadata entries without bitmaps`() {
        val rows = CoverDirectoryRows()
        (10_000 downTo 1).forEach { rows.add(CoverDocument("u$it", "$it"), CoverDirectoryRow.Folder) }
        val sorted = rows.sorted()
        assertEquals(10_000, sorted.size); assertEquals("1", sorted.first().document.name)
        assertEquals("10000", sorted.last().document.name)
    }
    @Test fun `entry and byte limits fail explicitly instead of returning a partial index`() {
        val rows = CoverDirectoryRows(maxEntries = 1)
        rows.add(CoverDocument("a", "a"), CoverDirectoryRow.Folder)
        try { rows.add(CoverDocument("b", "b"), CoverDirectoryRow.Folder); fail() } catch (_: IllegalStateException) { }
        try { CoverDirectoryRows(maxBytes = 10).add(CoverDocument("a", "a"), CoverDirectoryRow.Audio); fail() }
        catch (_: IllegalStateException) { }
    }
}
