package com.example.islandlyrics.core.settings

import android.content.Context
import android.content.SharedPreferences

object LabFeatureManager {
    private const val PREFS_NAME = "IslandLyricsPrefs"
    private const val KEY_SUPER_ISLAND_ADVANCED_STYLE_ENABLED = "lab_super_island_advanced_style_enabled"
    private const val KEY_SUPER_ISLAND_ADVANCED_STYLE_MIGRATED = "lab_super_island_advanced_style_migrated"

    const val SUPER_ISLAND_STYLE_STANDARD = "standard"
    const val SUPER_ISLAND_STYLE_ADVANCED = "advanced_beta"

    fun ensureInitialized(context: Context) {
        ensureInitialized(context.prefs())
    }

    fun ensureInitialized(prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_SUPER_ISLAND_ADVANCED_STYLE_MIGRATED, false)) return

        val advancedSelected =
            prefs.getString("super_island_notification_style", SUPER_ISLAND_STYLE_STANDARD) == SUPER_ISLAND_STYLE_ADVANCED
        prefs.edit()
            .putBoolean(KEY_SUPER_ISLAND_ADVANCED_STYLE_ENABLED, advancedSelected)
            .putBoolean(KEY_SUPER_ISLAND_ADVANCED_STYLE_MIGRATED, true)
            .apply()
    }

    fun isSuperIslandAdvancedStyleEnabled(context: Context): Boolean {
        val prefs = context.prefs()
        ensureInitialized(prefs)
        return prefs.getBoolean(KEY_SUPER_ISLAND_ADVANCED_STYLE_ENABLED, false)
    }

    fun isSuperIslandAdvancedStyleEnabled(prefs: SharedPreferences): Boolean {
        ensureInitialized(prefs)
        return prefs.getBoolean(KEY_SUPER_ISLAND_ADVANCED_STYLE_ENABLED, false)
    }

    fun setSuperIslandAdvancedStyleEnabled(context: Context, enabled: Boolean): Boolean {
        val prefs = context.prefs()
        ensureInitialized(prefs)

        val currentStyle =
            prefs.getString("super_island_notification_style", SUPER_ISLAND_STYLE_STANDARD)
                ?: SUPER_ISLAND_STYLE_STANDARD
        val revertedToStandard = !enabled && currentStyle == SUPER_ISLAND_STYLE_ADVANCED

        prefs.edit()
            .putBoolean(KEY_SUPER_ISLAND_ADVANCED_STYLE_ENABLED, enabled)
            .putBoolean(KEY_SUPER_ISLAND_ADVANCED_STYLE_MIGRATED, true)
            .apply {
                if (revertedToStandard) {
                    putString("super_island_notification_style", SUPER_ISLAND_STYLE_STANDARD)
                }
            }
            .apply()

        return revertedToStandard
    }

    fun sanitizeSuperIslandNotificationStyle(context: Context): String {
        val prefs = context.prefs()
        ensureInitialized(prefs)

        val currentStyle =
            prefs.getString("super_island_notification_style", SUPER_ISLAND_STYLE_STANDARD)
                ?: SUPER_ISLAND_STYLE_STANDARD
        val advancedEnabled = prefs.getBoolean(KEY_SUPER_ISLAND_ADVANCED_STYLE_ENABLED, false)
        if (currentStyle == SUPER_ISLAND_STYLE_ADVANCED && !advancedEnabled) {
            prefs.edit().putString("super_island_notification_style", SUPER_ISLAND_STYLE_STANDARD).apply()
            return SUPER_ISLAND_STYLE_STANDARD
        }
        return currentStyle
    }

    private fun Context.prefs(): SharedPreferences =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
