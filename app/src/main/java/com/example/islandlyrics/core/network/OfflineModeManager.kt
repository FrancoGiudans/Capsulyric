package com.example.islandlyrics.core.network

import android.content.Context

object OfflineModeManager {
    private const val PREFS_NAME = "IslandLyricsPrefs"
    const val KEY_FULLY_OFFLINE_MODE = "fully_offline_mode"

    fun isEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FULLY_OFFLINE_MODE, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FULLY_OFFLINE_MODE, enabled)
            .apply()
    }
}
