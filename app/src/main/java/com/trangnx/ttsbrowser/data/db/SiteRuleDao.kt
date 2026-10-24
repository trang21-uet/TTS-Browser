package com.trangnx.ttsbrowser.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SiteRuleDao {
    @Query("SELECT * FROM site_rule WHERE host = :host")
    suspend fun get(host: String): SiteRuleEntity?

    @Upsert
    suspend fun upsert(rule: SiteRuleEntity)

    @Query("DELETE FROM site_rule WHERE host = :host")
    suspend fun delete(host: String)
}
