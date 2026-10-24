package com.trangnx.ttsbrowser.data.repo

import com.trangnx.ttsbrowser.data.db.SiteRuleDao
import com.trangnx.ttsbrowser.data.db.SiteRuleEntity

/** Per-site content selectors chosen with the element picker. */
class SiteRuleRepository(
    private val dao: SiteRuleDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun selectorFor(url: String): String? = hostOf(url)?.let { dao.get(it)?.contentSelector }

    /** Returns `false` when [url] has no http(s) host to attach the rule to. */
    suspend fun save(url: String, selector: String): Boolean {
        val host = hostOf(url) ?: return false
        dao.upsert(SiteRuleEntity(host = host, contentSelector = selector, updatedAt = clock()))
        return true
    }

    suspend fun clear(url: String) {
        hostOf(url)?.let { dao.delete(it) }
    }
}

/** Lower-cased host without a leading `www.`, so `www.site.com` and `site.com` share a rule. */
internal fun hostOf(url: String): String? =
    hostRegex.find(url.trim())?.groupValues?.get(1)
        ?.lowercase()
        ?.removePrefix("www.")
        ?.takeIf { it.isNotEmpty() }

// Regex rather than java.net.URI: page URLs may contain characters URI rejects.
private val hostRegex = Regex("^https?://(?:[^/?#@]*@)?(\\[[^\\]]+]|[^/?#:]+)", RegexOption.IGNORE_CASE)
