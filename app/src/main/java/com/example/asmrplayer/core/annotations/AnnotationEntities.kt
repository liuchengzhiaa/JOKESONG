package com.example.asmrplayer.core.annotations

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import com.example.asmrplayer.core.subtitles.TimedTextCandidate
import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextMatch

/** Small user-owned snapshots, not a second full-library scan. No media bytes. */
@Entity(tableName = "user_items", primaryKeys = ["rootUri", "documentUri"],
    indices = [Index(value = ["rootUri", "score", "title", "documentUri"])])
data class UserItemEntity(
    val rootUri: String,
    val documentUri: String,
    val kind: String,
    val title: String,
    val subtitle: String,
    val artworkUri: String?,
    val paletteSeed: Int,
    val parentUri: String?,
    val score: Int? = null,
    val lyricUri: String? = null,
    val lyricName: String? = null,
    val lyricFormat: String? = null,
    val lyricSize: Long = 0,
    val lyricModified: Long = 0,
    val lyricLanguage: String? = null,
) : java.io.Serializable {
    fun item() = LibraryItem(documentUri, title, subtitle, LibraryItemType.valueOf(kind),
        paletteSeed = paletteSeed, documentUri = documentUri, artworkUri = artworkUri)
    fun timedText(): TimedTextMatch? {
        val uri = lyricUri ?: return null
        val format = TimedTextFormat.entries.firstOrNull { it.name == lyricFormat } ?: return null
        return TimedTextMatch(title, TimedTextCandidate(lyricName.orEmpty(), uri, format,
            lyricSize, lyricModified), lyricLanguage)
    }
    companion object {
        fun capture(root: String, item: LibraryItem, parent: String?, match: TimedTextMatch?) = UserItemEntity(
            root, requireNotNull(item.documentUri), item.type.name, item.title, item.subtitle,
            item.artworkUri, item.paletteSeed, parent,
            lyricUri = match?.candidate?.documentUri, lyricName = match?.candidate?.displayName,
            lyricFormat = match?.candidate?.format?.name, lyricSize = match?.candidate?.sizeBytes ?: 0,
            lyricModified = match?.candidate?.modifiedAtMs ?: 0, lyricLanguage = match?.languageTag,
        )
    }
}

@Entity(tableName = "user_tags", indices = [Index(value = ["rootUri", "nameKey"], unique = true)])
data class UserTagEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rootUri: String, val name: String, val nameKey: String)

@Entity(tableName = "user_item_tags", primaryKeys = ["rootUri", "documentUri", "tagId"],
    indices = [Index(value = ["tagId"])], foreignKeys = [
        ForeignKey(entity = UserItemEntity::class, parentColumns = ["rootUri", "documentUri"],
            childColumns = ["rootUri", "documentUri"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = UserTagEntity::class, parentColumns = ["id"],
            childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ])
data class UserItemTagEntity(val rootUri: String, val documentUri: String, val tagId: Long)

data class TagSummary(val id: Long, val name: String, val itemCount: Int)
