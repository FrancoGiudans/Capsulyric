package com.example.islandlyrics.core.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.islandlyrics.core.update.UpdateChecker

object LabFeatureManager {
    private const val PREFS_NAME = "IslandLyricsPrefs"
    private const val KEY_SUPER_ISLAND_ADVANCED_STYLE_ENABLED = "lab_super_island_advanced_style_enabled"
    private const val KEY_SUPER_ISLAND_ADVANCED_STYLE_MIGRATED = "lab_super_island_advanced_style_migrated"
    private const val KEY_SUPER_ISLAND_TEXT_LIMITS_ENABLED = "lab_super_island_text_limits_enabled"
    private const val KEY_FLOATING_LYRICS_ENABLED = "lab_floating_lyrics_enabled"
    private const val KEY_FLOATING_LYRICS_MIGRATED = "lab_floating_lyrics_migrated"
    private const val KEY_EXPERIMENT_UPDATES_ENABLED = "lab_experiment_updates_enabled"
    private const val KEY_EXPERIMENT_UPDATES_MIGRATED = "lab_experiment_updates_migrated"
    private const val KEY_FEED_SOURCE_PRIORITY = "lab_feed_source_priority"

    const val SUPER_ISLAND_STYLE_STANDARD = "standard"
    const val SUPER_ISLAND_STYLE_ADVANCED = "advanced_beta"
    const val FEED_SOURCE_GITHUB = "github"
    const val FEED_SOURCE_GITEE = "gitee"

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

        if (!prefs.getBoolean(KEY_EXPERIMENT_UPDATES_MIGRATED, false)) {
            val legacyPrereleaseEnabled = prefs.getBoolean("allow_prerelease_updates", false)
            val legacyChannel = prefs.getString("prerelease_channel", "Alpha")
            val migratedEnabled = when {
                prefs.contains("update_channel") ->
                    prefs.getString("update_channel", UpdateChecker.CHANNEL_STABLE) == UpdateChecker.CHANNEL_EXPERIMENT
                legacyPrereleaseEnabled && legacyChannel == "Canary" -> true
                else -> false
            }
            editor.putBoolean(KEY_EXPERIMENT_UPDATES_ENABLED, migratedEnabled)
            editor.putBoolean(KEY_EXPERIMENT_UPDATES_MIGRATED, true)
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

    fun isSuperIslandTextLimitsEnabled(context: Context): Boolean {
        val prefs = context.prefs()
        ensureInitialized(prefs)
        return prefs.getBoolean(KEY_SUPER_ISLAND_TEXT_LIMITS_ENABLED, false)
    }

    fun isSuperIslandTextLimitsEnabled(prefs: SharedPreferences): Boolean {
        ensureInitialized(prefs)
        return prefs.getBoolean(KEY_SUPER_ISLAND_TEXT_LIMITS_ENABLED, false)
    }

    fun setSuperIslandTextLimitsEnabled(context: Context, enabled: Boolean) {
        val prefs = context.prefs()
        ensureInitialized(prefs)
        prefs.edit()
            .putBoolean(KEY_SUPER_ISLAND_TEXT_LIMITS_ENABLED, enabled)
            .apply()
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

    fun isExperimentUpdatesEnabled(context: Context): Boolean {
        val prefs = context.prefs()
        ensureInitialized(prefs)
        return prefs.getBoolean(KEY_EXPERIMENT_UPDATES_ENABLED, false)
    }

    fun isExperimentUpdatesEnabled(prefs: SharedPreferences): Boolean {
        ensureInitialized(prefs)
        return prefs.getBoolean(KEY_EXPERIMENT_UPDATES_ENABLED, false)
    }

    fun setExperimentUpdatesEnabled(context: Context, enabled: Boolean) {
        val prefs = context.prefs()
        ensureInitialized(prefs)

        prefs.edit()
            .putBoolean(KEY_EXPERIMENT_UPDATES_ENABLED, enabled)
            .putBoolean(KEY_EXPERIMENT_UPDATES_MIGRATED, true)
            .apply()

        val currentChannel = UpdateChecker.getUpdateChannel(context)
        when {
            enabled -> UpdateChecker.setUpdateChannel(context, UpdateChecker.CHANNEL_EXPERIMENT)
            currentChannel == UpdateChecker.CHANNEL_EXPERIMENT ->
                UpdateChecker.setUpdateChannel(context, UpdateChecker.CHANNEL_PREVIEW)
        }
    }

    fun getFeedSourcePriority(context: Context): String {
        val prefs = context.prefs()
        ensureInitialized(prefs)
        return normalizeFeedSourcePriority(prefs.getString(KEY_FEED_SOURCE_PRIORITY, FEED_SOURCE_GITHUB))
    }

    fun setFeedSourcePriority(context: Context, priority: String) {
        val prefs = context.prefs()
        ensureInitialized(prefs)
        prefs.edit()
            .putString(KEY_FEED_SOURCE_PRIORITY, normalizeFeedSourcePriority(priority))
            .apply()
    }

    private fun normalizeFeedSourcePriority(priority: String?): String {
        return when (priority?.trim()?.lowercase()) {
            FEED_SOURCE_GITEE -> FEED_SOURCE_GITEE
            else -> FEED_SOURCE_GITHUB
        }
    }

    private fun Context.prefs(): SharedPreferences =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
