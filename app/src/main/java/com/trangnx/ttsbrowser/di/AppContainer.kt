package com.trangnx.ttsbrowser.di

import android.content.Context
import com.trangnx.ttsbrowser.data.db.AppDatabase
import com.trangnx.ttsbrowser.data.repo.BrowsingRepository
import com.trangnx.ttsbrowser.data.repo.SiteRuleRepository
import com.trangnx.ttsbrowser.extraction.ElementPicker
import com.trangnx.ttsbrowser.extraction.ExtractionScripts
import com.trangnx.ttsbrowser.extraction.ReadabilityExtractor

/**
 * Manual dependency container. Repositories, TTS engines and other singletons
 * are added here as lazy properties.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    private val extractionScripts: ExtractionScripts by lazy { ExtractionScripts(appContext.assets) }

    val browsingRepository: BrowsingRepository by lazy {
        BrowsingRepository(database.historyDao(), database.bookmarkDao())
    }

    val siteRuleRepository: SiteRuleRepository by lazy { SiteRuleRepository(database.siteRuleDao()) }

    val readabilityExtractor: ReadabilityExtractor by lazy {
        ReadabilityExtractor(extractionScripts, siteRuleRepository)
    }

    val elementPicker: ElementPicker by lazy { ElementPicker(extractionScripts) }
}
