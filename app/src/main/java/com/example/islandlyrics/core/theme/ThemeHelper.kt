package com.example.islandlyrics.core.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object ThemeHelper {

    private const val PREFS_NAME = "IslandLyricsPrefs"
    private const val KEY_LANGUAGE = "language_code" // "" (system), "en", "zh-CN"
    private const val KEY_UI_USE_MIUIX = "ui_use_miuix"

    private const val KEY_MIUIX_FOLLOW_SYSTEM = "theme_follow_system"
    private const val KEY_MIUIX_DARK_MODE = "theme_dark_mode"
    private const val KEY_MIUIX_PURE_BLACK = "theme_pure_black"
    private const val KEY_MIUIX_DYNAMIC_COLOR = "theme_dynamic_color"
    private const val KEY_MIUIX_CUSTOM_COLOR = "theme_custom_color"
    private const val KEY_MIUIX_CUSTOM_COLOR_GLOBAL_TINT = "theme_custom_color_global_tint"

    private const val KEY_MATERIAL_FOLLOW_SYSTEM = "material_theme_follow_system"
    private const val KEY_MATERIAL_DARK_MODE = "material_theme_dark_mode"
    private const val KEY_MATERIAL_PURE_BLACK = "material_theme_pure_black"
    private const val KEY_MATERIAL_DYNAMIC_COLOR = "material_theme_dynamic_color"
    private const val KEY_MATERIAL_CUSTOM_COLOR = "material_theme_custom_color"
    private const val KEY_MATERIAL_CUSTOM_COLOR_GLOBAL_TINT = "material_theme_custom_color_global_tint"

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
        val followSystem = getFollowSystem(context)
        if (followSystem) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        } else {
            val isDarkMode = getDarkMode(context)
            AppCompatDelegate.setDefaultNightMode(if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }

        // Dynamic Colors handled in Application/Activity onCreate
    }

    fun isDynamicColorEnabled(context: Context): Boolean {
        if (isMiuixEnabled(context)) return false
        return getMaterialDynamicColor(context)
    }

    fun isPureBlackEnabled(context: Context): Boolean {
        return if (isMiuixEnabled(context)) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getBoolean(KEY_MIUIX_PURE_BLACK, false)
        } else {
            getMaterialPureBlack(context)
        }
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(activeFollowSystemKey(context), follow).apply()
        // Re-apply immediately
        if (follow) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        } else {
            val isDarkMode = getDarkMode(context)
            AppCompatDelegate.setDefaultNightMode(if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    fun setDarkMode(context: Context, enable: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(activeDarkModeKey(context), enable).apply()
        if (!getFollowSystem(context)) {
            AppCompatDelegate.setDefaultNightMode(if (enable) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    fun setPureBlack(context: Context, enable: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(activePureBlackKey(context), enable).apply()
        // Activity needs recreation to pick this up in onCreate
    }

    fun setDynamicColor(context: Context, enable: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(activeDynamicColorKey(context), enable).apply()
        // Requires App restart or Activity recreation logic
    }

    fun getFollowSystem(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (isMiuixEnabled(context)) {
            prefs.getBoolean(KEY_MIUIX_FOLLOW_SYSTEM, true)
        } else {
            readBooleanWithFallback(prefs, KEY_MATERIAL_FOLLOW_SYSTEM, KEY_MIUIX_FOLLOW_SYSTEM, true)
        }
    }

    fun getDarkMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (isMiuixEnabled(context)) {
            prefs.getBoolean(KEY_MIUIX_DARK_MODE, false)
        } else {
            readBooleanWithFallback(prefs, KEY_MATERIAL_DARK_MODE, KEY_MIUIX_DARK_MODE, false)
        }
    }

    fun getMaterialPureBlack(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return readBooleanWithFallback(prefs, KEY_MATERIAL_PURE_BLACK, KEY_MIUIX_PURE_BLACK, false)
    }

    fun getMaterialDynamicColor(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return readBooleanWithFallback(prefs, KEY_MATERIAL_DYNAMIC_COLOR, KEY_MIUIX_DYNAMIC_COLOR, false)
    }

    fun getMaterialCustomColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return readIntWithFallback(prefs, KEY_MATERIAL_CUSTOM_COLOR, KEY_MIUIX_CUSTOM_COLOR, 0xFF3482FF.toInt())
    }

    fun isMaterialCustomColorGlobalTintEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return readBooleanWithFallback(
            prefs,
            KEY_MATERIAL_CUSTOM_COLOR_GLOBAL_TINT,
            KEY_MIUIX_CUSTOM_COLOR_GLOBAL_TINT,
            false
        )
    }

    fun setMaterialCustomColor(context: Context, argb: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MATERIAL_CUSTOM_COLOR, argb)
            .apply()
    }

    fun setMaterialCustomColorGlobalTint(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MATERIAL_CUSTOM_COLOR_GLOBAL_TINT, enabled)
            .apply()
    }

    private fun isMiuixEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_UI_USE_MIUIX, true)
    }

    private fun activeFollowSystemKey(context: Context): String {
        return if (isMiuixEnabled(context)) KEY_MIUIX_FOLLOW_SYSTEM else KEY_MATERIAL_FOLLOW_SYSTEM
    }

    private fun activeDarkModeKey(context: Context): String {
        return if (isMiuixEnabled(context)) KEY_MIUIX_DARK_MODE else KEY_MATERIAL_DARK_MODE
    }

    private fun activePureBlackKey(context: Context): String {
        return if (isMiuixEnabled(context)) KEY_MIUIX_PURE_BLACK else KEY_MATERIAL_PURE_BLACK
    }

    private fun activeDynamicColorKey(context: Context): String {
        return if (isMiuixEnabled(context)) KEY_MIUIX_DYNAMIC_COLOR else KEY_MATERIAL_DYNAMIC_COLOR
    }

    private fun readBooleanWithFallback(
        prefs: android.content.SharedPreferences,
        primaryKey: String,
        fallbackKey: String,
        defaultValue: Boolean
    ): Boolean {
        return if (prefs.contains(primaryKey)) prefs.getBoolean(primaryKey, defaultValue)
        else prefs.getBoolean(fallbackKey, defaultValue)
    }

    private fun readIntWithFallback(
        prefs: android.content.SharedPreferences,
        primaryKey: String,
        fallbackKey: String,
        defaultValue: Int
    ): Int {
        return if (prefs.contains(primaryKey)) prefs.getInt(primaryKey, defaultValue)
        else prefs.getInt(fallbackKey, defaultValue)
    }
}
