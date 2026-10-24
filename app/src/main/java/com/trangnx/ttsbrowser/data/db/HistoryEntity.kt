package com.trangnx.ttsbrowser.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One visited page in the browser history. */
@Entity(tableName = "history", indices = [Index("visitedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long,
)
