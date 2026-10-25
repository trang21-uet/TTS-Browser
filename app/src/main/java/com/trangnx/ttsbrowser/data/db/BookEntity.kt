package com.trangnx.ttsbrowser.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A story saved to the library; one per (site, title). */
@Entity(tableName = "book", indices = [Index(value = ["host", "title"], unique = true)])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val host: String,
    val createdAt: Long,
    val updatedAt: Long,
)
