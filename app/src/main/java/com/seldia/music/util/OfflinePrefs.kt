package com.seldia.music.util

import android.content.Context

/** Preferencias simples para el modo híbrido online ⇄ offline. */
object OfflinePrefs {

    private const val PREFS = "seldia_offline_prefs"
    private const val KEY_AUTO_DOWNLOAD = "auto_download_audio"

    var autoDownloadEnabled: Boolean
        get() = prefs?.getBoolean(KEY_AUTO_DOWNLOAD, false) ?: false
        set(value) {
            prefs?.edit()?.putBoolean(KEY_AUTO_DOWNLOAD, value)?.apply()
        }

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }
}
