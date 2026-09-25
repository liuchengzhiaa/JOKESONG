package com.example.asmrplayer.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.asmrplayer.R

/** One countable half of a folder's summary line. */
enum class FolderSummaryPart {
    Subfolders,
    Audio,
}

/**
 * The parts a folder's summary line should mention, in display order.
 *
 * A folder advertises only the counts it actually has: "0 个子文件夹 · 2 条音频"
 * spends half the line on a number that tells the reader nothing, and folders in a
 * real library are usually lopsided.
 *
 * An entirely empty folder is the deliberate exception. With no non-zero count to
 * carry the line it keeps both parts, so it still reads "0 个子文件夹 · 0 条音频"
 * rather than going blank -- a blank summary on a card with no artwork either reads
 * as a failure to load rather than as an empty folder.
 */
fun folderSummaryParts(childFolderCount: Int, audioCount: Int): List<FolderSummaryPart> =
    if (childFolderCount <= 0 && audioCount <= 0) {
        listOf(FolderSummaryPart.Subfolders, FolderSummaryPart.Audio)
    } else {
        buildList {
            if (childFolderCount > 0) add(FolderSummaryPart.Subfolders)
            if (audioCount > 0) add(FolderSummaryPart.Audio)
        }
    }

/**
 * Renders [folderSummaryParts] for the folder's secondary line. Shared by the
 * library layouts and the focus wall so the two can never disagree.
 */
@Composable
fun folderSummaryLine(childFolderCount: Int, audioCount: Int): String {
    val parts = folderSummaryParts(childFolderCount, audioCount)
    val subfolders = if (FolderSummaryPart.Subfolders in parts) {
        stringResource(R.string.folder_subfolder_count, childFolderCount)
    } else {
        null
    }
    val audio = if (FolderSummaryPart.Audio in parts) {
        stringResource(R.string.folder_audio_count, audioCount)
    } else {
        null
    }
    return listOfNotNull(subfolders, audio).joinToString(" · ")
}
