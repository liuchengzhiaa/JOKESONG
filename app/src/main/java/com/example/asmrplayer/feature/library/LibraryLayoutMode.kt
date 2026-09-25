package com.example.asmrplayer.feature.library

import androidx.annotation.StringRes
import com.example.asmrplayer.R

/**
 * The four library presentations. Switching between them is a pure presentation
 * change: it never rescans the folder and never touches navigation state.
 */
enum class LibraryLayoutMode(@StringRes val labelRes: Int) {
    DetailedList(R.string.layout_detailed_list),
    CompactList(R.string.layout_compact_list),
    Grid(R.string.layout_grid),
    FocusWall(R.string.focus_wall);

    fun next(): LibraryLayoutMode = entries[(ordinal + 1) % entries.size]

    companion object {
        val Default: LibraryLayoutMode = FocusWall

        fun fromOrdinal(ordinal: Int): LibraryLayoutMode =
            entries[ordinal.coerceIn(entries.indices)]
    }
}
