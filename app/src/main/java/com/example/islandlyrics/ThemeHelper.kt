package com.example.islandlyrics

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object ThemeHelper {

    private const val PREFS_NAME = "IslandLyricsPrefs"
    private const val KEY_LANGUAGE = "language_code" // "" (system), "en", "zh-CN"
    private const val KEY_FOLLOW_SYSTEM = "theme_follow_system"
    private const val KEY_DARK_MODE = "theme_dark_mode"
    private const val KEY_PURE_BLACK = "theme_pure_black"
    private const val KEY_DYNAMIC_COLOR = "theme_dynamic_color"

    fun applyTheme(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Language
        val lang = prefs.getString(KEY_LANGUAGE, "")
        if (lang.isNullOrEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        }

        // 2. Dark Mode
        val followSystem = prefs.getBoolean(KEY_FOLLOW_SYSTEM, true)
        if (followSystem) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        } else {
            val isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)
            AppCompatDelegate.setDefaultNightMode(if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }

        // Dynamic Colors handled in Application/Activity onCreate
    }

    fun isDynamicColorEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DYNAMIC_COLOR, true)
    }

    fun isPureBlackEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PURE_BLACK, false)
    }

    // Setters
    fun setLanguage(context: Context, langCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, langCode).apply()
        if (langCode.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langCode))
        }
    }

    fun setFollowSystem(context: Context, follow: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FOLLOW_SYSTEM, follow).apply()
        // Re-apply immediately
        if (follow) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        } else {
            val isDarkMode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_DARK_MODE, false)
            AppCompatDelegate.setDefaultNightMode(if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    fun setDarkMode(context: Context, enable: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK_MODE, enable).apply()
        if (!context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_FOLLOW_SYSTEM, true)) {
            AppCompatDelegate.setDefaultNightMode(if (enable) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    fun setPureBlack(context: Context, enable: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PURE_BLACK, enable).apply()
        // Activity needs recreation to pick this up in onCreate
    }

    fun setDynamicColor(context: Context, enable: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DYNAMIC_COLOR, enable).apply()
        // Requires App restart or Activity recreation logic
    }
}
