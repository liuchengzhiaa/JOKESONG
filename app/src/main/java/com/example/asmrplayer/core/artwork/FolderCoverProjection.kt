package com.example.asmrplayer.core.artwork

import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType

/** Pure publication boundary: never mutate the raw snapshot or individual track artwork. */
internal fun projectFolderCovers(
    items: List<LibraryItem>,
    artworkFor: (folderUri: String, previousArtwork: String?) -> String,
): List<LibraryItem> = items.map { item ->
    val uri = item.documentUri
    if (item.type == LibraryItemType.Folder && uri != null) {
        item.copy(artworkUri = artworkFor(uri, item.artworkUri))
    } else item
}
