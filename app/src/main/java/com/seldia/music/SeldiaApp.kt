package com.seldia.music

import android.app.Application
import com.seldia.music.di.AppContainer

class SeldiaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.seldia.music.util.OfflinePrefs.init(this)
        com.seldia.music.util.ThemePrefs.init(this)
        container = AppContainer(this)
    }
}
