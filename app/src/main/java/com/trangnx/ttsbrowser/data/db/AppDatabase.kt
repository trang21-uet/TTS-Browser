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
    entities = [HistoryEntity::class, BookmarkEntity::class, SiteRuleEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun siteRuleDao(): SiteRuleDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tts_browser.db").build()
    }
}
