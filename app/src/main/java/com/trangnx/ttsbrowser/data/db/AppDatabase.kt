package com.trangnx.ttsbrowser.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * App-wide Room database. New tables are added by bumping [version] with an AutoMigration;
 * schemas are exported to `app/schemas`.
 */
@Database(
    entities = [
        HistoryEntity::class, BookmarkEntity::class, SiteRuleEntity::class,
        BookEntity::class, ProgressEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun siteRuleDao(): SiteRuleDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tts_browser.db").build()
    }
}
