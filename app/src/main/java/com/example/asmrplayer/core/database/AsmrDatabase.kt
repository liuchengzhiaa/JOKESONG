package com.example.asmrplayer.core.database

import com.example.asmrplayer.core.annotations.*
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FolderEntity::class,
        AudioEntity::class,
        ArtworkEntity::class,
        SubtitleEntity::class,
        PlaybackProgressEntity::class,
        PlaybackSessionEntity::class,
        PlaybackQueueEntryEntity::class,
        PlaybackBookmarkEntity::class,
        UserItemEntity::class, UserTagEntity::class, UserItemTagEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AsmrDatabase : RoomDatabase() {
    abstract fun annotationDao(): AnnotationDao

    abstract fun libraryDao(): LibraryDao

    abstract fun playbackDao(): PlaybackDao

    companion object {
        const val FileName: String = "asmr-player.db"

        @Volatile
        private var instance: AsmrDatabase? = null

        fun get(context: Context): AsmrDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AsmrDatabase::class.java,
                FileName,
            ).addMigrations(AnnotationMigration1To2).build().also { instance = it }
        }
    }
}
