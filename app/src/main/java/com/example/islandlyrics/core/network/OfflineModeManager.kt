package com.example.islandlyrics.core.network

import android.content.Context
import com.example.islandlyrics.core.settings.AppPreferences

object OfflineModeManager {
    const val KEY_FULLY_OFFLINE_MODE = AppPreferences.Keys.FULLY_OFFLINE_MODE

    fun isEnabled(context: Context): Boolean {
        return AppPreferences.isOfflineModeEnabled(AppPreferences.of(context.applicationContext))
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        AppPreferences.of(context.applicationContext)
            .edit()
            .putBoolean(KEY_FULLY_OFFLINE_MODE, enabled)
            .apply()
    }
}
