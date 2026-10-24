package com.trangnx.ttsbrowser.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A bookmarked page; [url] is unique. */
@Entity(tableName = "bookmark", indices = [Index(value = ["url"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long,
)
