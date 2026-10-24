package com.trangnx.ttsbrowser

import android.app.Application
import com.trangnx.ttsbrowser.di.AppContainer

/** Application entry point; owns the process-wide [AppContainer]. */
class TtsBrowserApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
