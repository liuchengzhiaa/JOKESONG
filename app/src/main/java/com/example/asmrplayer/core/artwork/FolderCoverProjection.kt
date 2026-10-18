package com.example.asmrplayer.core.artwork

import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType

/** Overrides are presentation only; keep automatic artwork in the raw folder snapshot. */
internal fun projectFolderCovers(
    items: List<LibraryItem>,
    covers: Map<String, String>,
): List<LibraryItem> = items.map { item ->
    val cover = if (item.type == LibraryItemType.Folder) covers[item.documentUri] else null
    if (cover != null) item.copy(artworkUri = cover) else item
}
