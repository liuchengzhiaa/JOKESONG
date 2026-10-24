package com.example.asmrplayer.core.annotations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val AnnotationMigration1To2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_items` (`rootUri` TEXT NOT NULL, `documentUri` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `subtitle` TEXT NOT NULL, `artworkUri` TEXT, `paletteSeed` INTEGER NOT NULL, `parentUri` TEXT, `score` INTEGER, `lyricUri` TEXT, `lyricName` TEXT, `lyricFormat` TEXT, `lyricSize` INTEGER NOT NULL, `lyricModified` INTEGER NOT NULL, `lyricLanguage` TEXT, PRIMARY KEY(`rootUri`, `documentUri`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_items_rootUri_score_title_documentUri` ON `user_items` (`rootUri`, `score`, `title`, `documentUri`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rootUri` TEXT NOT NULL, `name` TEXT NOT NULL, `nameKey` TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_tags_rootUri_nameKey` ON `user_tags` (`rootUri`, `nameKey`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_item_tags` (`rootUri` TEXT NOT NULL, `documentUri` TEXT NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`rootUri`, `documentUri`, `tagId`), FOREIGN KEY(`rootUri`, `documentUri`) REFERENCES `user_items` (`rootUri`, `documentUri`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tagId`) REFERENCES `user_tags` (`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_item_tags_tagId` ON `user_item_tags` (`tagId`)")
    }
}
