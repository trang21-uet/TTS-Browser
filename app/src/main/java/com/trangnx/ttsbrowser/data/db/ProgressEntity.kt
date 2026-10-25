package com.trangnx.ttsbrowser.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Where reading stopped: [chapterUrl] + [sentenceIndex]. [charOffset] (the sentence's start in the
 * chapter text) restores the place if the chapter is split into sentences differently later.
 */
@Entity(
    tableName = "progress",
    foreignKeys = [ForeignKey(BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chapterUrl")],
)
data class ProgressEntity(
    @PrimaryKey val bookId: Long,
    val chapterUrl: String,
    val chapterTitle: String,
    val sentenceIndex: Int,
    val charOffset: Int,
    val updatedAt: Long,
)
