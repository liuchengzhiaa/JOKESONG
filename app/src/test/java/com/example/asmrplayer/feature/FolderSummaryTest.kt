package com.example.asmrplayer.feature

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderSummaryTest {
    @Test
    fun `a folder with only subfolders mentions subfolders alone`() {
        assertEquals(
            listOf(FolderSummaryPart.Subfolders),
            folderSummaryParts(childFolderCount = 3, audioCount = 0),
        )
    }

    @Test
    fun `a folder with only audio mentions audio alone`() {
        assertEquals(
            listOf(FolderSummaryPart.Audio),
            folderSummaryParts(childFolderCount = 0, audioCount = 2),
        )
    }

    @Test
    fun `a folder with both mentions both, subfolders first`() {
        assertEquals(
            listOf(FolderSummaryPart.Subfolders, FolderSummaryPart.Audio),
            folderSummaryParts(childFolderCount = 1, audioCount = 12),
        )
    }

    @Test
    fun `an empty folder keeps the full line rather than going blank`() {
        assertEquals(
            listOf(FolderSummaryPart.Subfolders, FolderSummaryPart.Audio),
            folderSummaryParts(childFolderCount = 0, audioCount = 0),
        )
    }

    @Test
    fun `the empty case also covers a negative count`() {
        assertEquals(
            listOf(FolderSummaryPart.Subfolders, FolderSummaryPart.Audio),
            folderSummaryParts(childFolderCount = -1, audioCount = 0),
        )
    }

    @Test
    fun `a missing half is dropped as soon as the other half is present`() {
        assertEquals(
            listOf(FolderSummaryPart.Subfolders),
            folderSummaryParts(childFolderCount = 1, audioCount = 0),
        )
        assertEquals(
            listOf(FolderSummaryPart.Audio),
            folderSummaryParts(childFolderCount = 0, audioCount = 1),
        )
    }
}
