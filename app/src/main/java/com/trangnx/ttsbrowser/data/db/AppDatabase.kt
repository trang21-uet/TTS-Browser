package com.trangnx.ttsbrowser.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * App-wide Room database. Later phases add tables (book, chapter, progress, site_rule)
 * by bumping [version] with an AutoMigration; schemas are exported to `app/schemas`.
 */
@Database(
    entities = [HistoryEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tts_browser.db").build()
    }
}
