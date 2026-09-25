package com.example.asmrplayer.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Long-form progress survives completion and is never discarded automatically. */
@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey val audioDocumentUri: String,
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val lastPlayedAtMs: Long,
    val playCount: Int,
)

/** Singleton global restore point. A reboot restores this paused. */
@Entity(tableName = "playback_session")
data class PlaybackSessionEntity(
    @PrimaryKey val singletonId: Int = SingletonId,
    val currentAudioDocumentUri: String?,
    val currentQueueIndex: Int,
    val currentPositionMs: Long,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    /** Historical state only; restoration must never automatically produce audio. */
    val wasPlaying: Boolean,
    val updatedAtMs: Long,
) {
    companion object {
        const val SingletonId: Int = 1
    }
}

/** Persisted effective order, including a fixed shuffle order when enabled. */
@Entity(
    tableName = "playback_queue",
    indices = [
        Index(value = ["queueIndex"], unique = true),
        Index(value = ["audioDocumentUri"]),
    ],
)
data class PlaybackQueueEntryEntity(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0L,
    val queueIndex: Int,
    val audioDocumentUri: String,
    val titleSnapshot: String,
    val artworkUriSnapshot: String?,
    /** Original unshuffled position, used to reconstruct deterministic shuffle state. */
    val sourceIndex: Int,
)

@Entity(
    tableName = "playback_bookmarks",
    indices = [Index(value = ["audioDocumentUri", "positionMs"], unique = true)],
)
data class PlaybackBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val bookmarkId: Long = 0L,
    val audioDocumentUri: String,
    val positionMs: Long,
    val label: String?,
    val createdAtMs: Long,
)
