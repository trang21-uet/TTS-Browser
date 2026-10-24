package com.trangnx.ttsbrowser.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Content CSS selector picked manually for a site, reused for every chapter on that [host]. */
@Entity(tableName = "site_rule")
data class SiteRuleEntity(
    @PrimaryKey val host: String,
    val contentSelector: String,
    val updatedAt: Long,
)
