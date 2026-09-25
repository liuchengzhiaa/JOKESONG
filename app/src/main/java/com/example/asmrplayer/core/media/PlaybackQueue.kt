package com.example.asmrplayer.core.media

import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType

/**
 * A queue built from one folder listing: the visible audio children in display
 * order, starting at the entry the user selected.
 */
data class PlaybackQueuePlan(
    val items: List<LibraryItem>,
    val startIndex: Int,
) {
    val startItem: LibraryItem? get() = items.getOrNull(startIndex)
}

/**
 * Turns the current folder into the deterministic playback queue.
 *
 * The folder listing is already in natural display order, so reusing it keeps
 * the queue stable and predictable. When the selected audio is not part of the
 * listing it still plays alone rather than silently starting a different track.
 *
 * Returns null when there is nothing playable at all.
 */
fun buildFolderQueue(
    folderItems: List<LibraryItem>,
    selected: LibraryItem?,
): PlaybackQueuePlan? {
    if (selected == null || selected.type != LibraryItemType.Audio) return null
    if (selected.documentUri == null) return null
    val audio = folderItems.filter { it.type == LibraryItemType.Audio && it.documentUri != null }
    val index = audio.indexOfFirst { it.id == selected.id }
    return if (index >= 0) {
        PlaybackQueuePlan(items = audio, startIndex = index)
    } else {
        PlaybackQueuePlan(items = listOf(selected), startIndex = 0)
    }
}
