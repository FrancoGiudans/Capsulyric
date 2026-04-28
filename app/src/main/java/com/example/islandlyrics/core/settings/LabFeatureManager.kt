package com.example.islandlyrics.core.settings

import android.content.Context
import android.content.SharedPreferences

object LabFeatureManager {
    private const val PREFS_NAME = "IslandLyricsPrefs"
    private const val KEY_SUPER_ISLAND_ADVANCED_STYLE_ENABLED = "lab_super_island_advanced_style_enabled"
    private const val KEY_SUPER_ISLAND_ADVANCED_STYLE_MIGRATED = "lab_super_island_advanced_style_migrated"
    private const val KEY_FLOATING_LYRICS_ENABLED = "lab_floating_lyrics_enabled"
    private const val KEY_FLOATING_LYRICS_MIGRATED = "lab_floating_lyrics_migrated"

    const val SUPER_ISLAND_STYLE_STANDARD = "standard"
    const val SUPER_ISLAND_STYLE_ADVANCED = "advanced_beta"

    fun ensureInitialized(context: Context) {
        ensureInitialized(context.prefs())
    }

    fun ensureInitialized(prefs: SharedPreferences) {
        val editor = prefs.edit()
        var changed = false

        if (!prefs.getBoolean(KEY_SUPER_ISLAND_ADVANCED_STYLE_MIGRATED, false)) {
            val advancedSelected =
                prefs.getString("super_island_notification_style", SUPER_ISLAND_STYLE_STANDARD) == SUPER_ISLAND_STYLE_ADVANCED
            editor.putBoolean(KEY_SUPER_ISLAND_ADVANCED_STYLE_ENABLED, advancedSelected)
            editor.putBoolean(KEY_SUPER_ISLAND_ADVANCED_STYLE_MIGRATED, true)
            changed = true
        }

        if (!prefs.getBoolean(KEY_FLOATING_LYRICS_MIGRATED, false)) {
            val floatingLyricsEnabled = prefs.getBoolean("floating_lyrics_enabled", false)
            editor.putBoolean(KEY_FLOATING_LYRICS_ENABLED, floatingLyricsEnabled)
            editor.putBoolean(KEY_FLOATING_LYRICS_MIGRATED, true)
            changed = true
        }

        if (changed) editor.apply()
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

    fun isFloatingLyricsEnabled(context: Context): Boolean {
        val prefs = context.prefs()
        ensureInitialized(prefs)
        return prefs.getBoolean(KEY_FLOATING_LYRICS_ENABLED, false)
    }

    fun isFloatingLyricsEnabled(prefs: SharedPreferences): Boolean {
        ensureInitialized(prefs)
        return prefs.getBoolean(KEY_FLOATING_LYRICS_ENABLED, false)
    }

    fun setFloatingLyricsEnabled(context: Context, enabled: Boolean) {
        val prefs = context.prefs()
        ensureInitialized(prefs)
        prefs.edit()
            .putBoolean(KEY_FLOATING_LYRICS_ENABLED, enabled)
            .putBoolean(KEY_FLOATING_LYRICS_MIGRATED, true)
            .apply()
    }

    private fun Context.prefs(): SharedPreferences =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
