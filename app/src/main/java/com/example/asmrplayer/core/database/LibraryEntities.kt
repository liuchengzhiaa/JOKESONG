package com.example.asmrplayer.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Indexed folder presentation. Real files remain owned by the SAF provider. */
@Entity(
    tableName = "library_folders",
    indices = [
        Index(value = ["parentDocumentUri", "sortKey", "displayName"]),
        Index(value = ["lastSeenScanId"]),
    ],
)
data class FolderEntity(
    @PrimaryKey val documentUri: String,
    val parentDocumentUri: String?,
    val displayName: String,
    val relativePath: String,
    val sortKey: String,
    val artworkKey: String?,
    val childFolderCount: Int,
    val audioCount: Int,
    val totalDurationMs: Long,
    val modifiedAtMs: Long,
    val addedAtMs: Long,
    val lastSeenScanId: Long,
    val isMissing: Boolean = false,
)

/** Cached metadata for one playable document; aliases never enter this table. */
@Entity(
    tableName = "audio_items",
    indices = [
        Index(value = ["parentFolderUri", "sortKey", "displayName"]),
        Index(value = ["lastSeenScanId"]),
        Index(value = ["fingerprint"]),
    ],
)
data class AudioEntity(
    @PrimaryKey val documentUri: String,
    val parentFolderUri: String,
    val displayName: String,
    val sortKey: String,
    val mimeType: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val circle: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val comment: String?,
    /** JSON is reserved for user-defined tag pairs, not privacy aliases. */
    val customTagsJson: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val modifiedAtMs: Long,
    val addedAtMs: Long,
    val fingerprint: String,
    val artworkKey: String?,
    val lastSeenScanId: Long,
    val isMissing: Boolean = false,
)

/** Artwork metadata and cache identity only; bitmap bytes are never stored in Room. */
@Entity(
    tableName = "artwork_entries",
    indices = [Index(value = ["ownerDocumentUri"])],
)
data class ArtworkEntity(
    @PrimaryKey val artworkKey: String,
    val ownerDocumentUri: String,
    /** embedded, sibling_image, descendant_fallback, or manual_folder (private presentation). */
    val sourceKind: String,
    val sourceUri: String?,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val cacheKey: String,
    val modifiedAtMs: Long,
)

/** A matched or unmatched timed-text document discovered below the selected root. */
@Entity(
    tableName = "subtitle_entries",
    indices = [
        Index(value = ["audioDocumentUri"]),
        Index(value = ["parentFolderUri"]),
        Index(value = ["lastSeenScanId"]),
    ],
)
data class SubtitleEntity(
    @PrimaryKey val documentUri: String,
    val parentFolderUri: String,
    val audioDocumentUri: String?,
    val displayName: String,
    /** lrc, srt, or vtt. */
    val format: String,
    val languageTag: String?,
    val sizeBytes: Long,
    val modifiedAtMs: Long,
    val lastSeenScanId: Long,
    val isMissing: Boolean = false,
)
