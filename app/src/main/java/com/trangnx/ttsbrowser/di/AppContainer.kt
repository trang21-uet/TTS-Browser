package com.trangnx.ttsbrowser.di

import android.content.Context
import com.trangnx.ttsbrowser.data.db.AppDatabase
import com.trangnx.ttsbrowser.data.repo.BrowsingRepository

/**
 * Manual dependency container. Repositories, TTS engines and other singletons
 * are added here as lazy properties.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val browsingRepository: BrowsingRepository by lazy {
        BrowsingRepository(database.historyDao(), database.bookmarkDao())
    }
}
