package com.example.asmrplayer.core.model

enum class LibraryItemType {
    Folder,
    Audio,
}

data class LibraryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: LibraryItemType,
    val durationLabel: String? = null,
    val paletteSeed: Int,
    val documentUri: String? = null,
    val artworkUri: String? = null,
)
