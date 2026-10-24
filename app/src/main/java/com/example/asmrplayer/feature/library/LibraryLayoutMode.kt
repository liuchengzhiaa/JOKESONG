package com.example.asmrplayer.feature.library

import androidx.annotation.StringRes
import com.example.asmrplayer.R

/**
 * The library presentations. Switching between them is a pure presentation
 * change: it never rescans the folder and never touches navigation state.
 */
enum class LibraryLayoutMode(@StringRes val labelRes: Int, val storedValue: Int) {
    DetailedList(R.string.layout_detailed_list, 0),
    CompactList(R.string.layout_compact_list, 1),
    Grid(R.string.layout_grid, 2),
    FoliaWall(R.string.folia_wall, 4);

    val isWall: Boolean get() = this == FoliaWall

    fun next(): LibraryLayoutMode = entries[(ordinal + 1) % entries.size]

    companion object {
        val Default: LibraryLayoutMode = FoliaWall

        fun fromOrdinal(ordinal: Int): LibraryLayoutMode =
            // Stored 3 was the removed Bento wall; both old wall values now open Folia.
            entries.firstOrNull { it.storedValue == ordinal }
                ?: if (ordinal < 0) DetailedList else FoliaWall
    }
}
