package com.example.asmrplayer.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaybackDao {
    @Upsert
    abstract suspend fun upsertProgress(progress: PlaybackProgressEntity)

    @Query("SELECT * FROM playback_progress WHERE audioDocumentUri = :audioUri LIMIT 1")
    abstract suspend fun progress(audioUri: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE audioDocumentUri = :audioUri LIMIT 1")
    abstract fun observeProgress(audioUri: String): Flow<PlaybackProgressEntity?>

    @Upsert
    abstract suspend fun upsertSession(session: PlaybackSessionEntity)

    @Query("SELECT * FROM playback_session WHERE singletonId = 1 LIMIT 1")
    abstract suspend fun session(): PlaybackSessionEntity?

    @Query("SELECT * FROM playback_session WHERE singletonId = 1 LIMIT 1")
    abstract fun observeSession(): Flow<PlaybackSessionEntity?>

    @Query("SELECT * FROM playback_queue ORDER BY queueIndex")
    abstract suspend fun queue(): List<PlaybackQueueEntryEntity>

    @Query("SELECT * FROM playback_queue ORDER BY queueIndex")
    abstract fun observeQueue(): Flow<List<PlaybackQueueEntryEntity>>

    @Query("DELETE FROM playback_queue")
    protected abstract suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertQueue(entries: List<PlaybackQueueEntryEntity>)

    /** Queue replacement is atomic, so process death cannot leave half a queue. */
    @Transaction
    open suspend fun replaceQueue(entries: List<PlaybackQueueEntryEntity>) {
        clearQueue()
        insertQueue(
            entries.mapIndexed { index, entry ->
                entry.copy(entryId = 0L, queueIndex = index)
            },
        )
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addBookmark(bookmark: PlaybackBookmarkEntity): Long

    @Delete
    abstract suspend fun deleteBookmark(bookmark: PlaybackBookmarkEntity)

    @Query("SELECT * FROM playback_bookmarks WHERE audioDocumentUri = :audioUri ORDER BY positionMs")
    abstract fun observeBookmarks(audioUri: String): Flow<List<PlaybackBookmarkEntity>>
}
