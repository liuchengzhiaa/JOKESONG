package com.example.asmrplayer.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LibraryDao {
    @Query("SELECT * FROM artwork_entries WHERE sourceKind = 'manual_folder' AND ownerDocumentUri IN (:owners)")
    suspend fun folderCoverOverrides(owners: List<String>): List<ArtworkEntity>

    @Query("DELETE FROM artwork_entries WHERE artworkKey = :key AND sourceKind = 'manual_folder'")
    suspend fun deleteFolderCoverOverride(key: String)

    @Upsert
    suspend fun upsertFolders(folders: List<FolderEntity>)

    @Upsert
    suspend fun upsertAudio(audio: List<AudioEntity>)

    @Upsert
    suspend fun upsertArtwork(artwork: List<ArtworkEntity>)

    @Upsert
    suspend fun upsertSubtitles(subtitles: List<SubtitleEntity>)

    /** Page direct children; never materialize the full 40,000-item index. */
    @Query(
        """
        SELECT * FROM library_folders
        WHERE parentDocumentUri = :parentUri AND isMissing = 0
        ORDER BY sortKey, displayName
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun folderPage(parentUri: String, limit: Int, offset: Int): List<FolderEntity>

    @Query(
        """
        SELECT * FROM audio_items
        WHERE parentFolderUri = :parentUri AND isMissing = 0
        ORDER BY sortKey, displayName
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun audioPage(parentUri: String, limit: Int, offset: Int): List<AudioEntity>

    @Query("SELECT * FROM audio_items WHERE documentUri = :documentUri LIMIT 1")
    suspend fun audio(documentUri: String): AudioEntity?

    @Query("UPDATE library_folders SET isMissing = 1 WHERE lastSeenScanId != :scanId")
    suspend fun markFoldersMissingBefore(scanId: Long)

    @Query("UPDATE audio_items SET isMissing = 1 WHERE lastSeenScanId != :scanId")
    suspend fun markAudioMissingBefore(scanId: Long)

    @Query("UPDATE subtitle_entries SET isMissing = 1 WHERE lastSeenScanId != :scanId")
    suspend fun markSubtitlesMissingBefore(scanId: Long)
}
