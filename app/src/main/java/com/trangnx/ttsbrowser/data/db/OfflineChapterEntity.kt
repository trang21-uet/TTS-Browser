package com.trangnx.ttsbrowser.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A chapter of a saved story downloaded ahead, so it opens without loading the page. [url] is the chapter's
 * own URL, [requestedUrl] the link it was reached by (they differ after a redirect); both find it.
 * [text] is the paragraphs joined with `\n` (paragraphs never contain a newline).
 */
@Entity(
    tableName = "offline_chapter",
    foreignKeys = [ForeignKey(BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId"), Index("requestedUrl")],
)
data class OfflineChapterEntity(
    @PrimaryKey val url: String,
    val requestedUrl: String,
    val bookId: Long,
    val title: String,
    val text: String,
    val startsWithTitle: Boolean,
    val prevUrl: String?,
    val nextUrl: String?,
    val pageTitle: String,
    val source: String,
    val savedAt: Long,
)

/** How many chapters of a story are downloaded. */
data class OfflineChapterCount(val bookId: Long, val count: Int)
