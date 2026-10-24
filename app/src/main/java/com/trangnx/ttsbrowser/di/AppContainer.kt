package com.trangnx.ttsbrowser.di

import android.content.Context

/**
 * Manual dependency container. Repositories, TTS engines and other singletons
 * are added here as lazy properties in later phases.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
}
