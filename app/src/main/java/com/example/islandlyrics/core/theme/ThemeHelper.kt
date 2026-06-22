package com.example.islandlyrics.core.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.islandlyrics.core.settings.AppPreferences

object ThemeHelper {

    private const val KEY_LANGUAGE = AppPreferences.Keys.LANGUAGE_CODE // "" (system), "en", "zh-CN"
    private const val KEY_UI_USE_MIUIX = AppPreferences.Keys.UI_USE_MIUIX

    private const val KEY_MIUIX_FOLLOW_SYSTEM = AppPreferences.Keys.THEME_FOLLOW_SYSTEM
    private const val KEY_MIUIX_DARK_MODE = AppPreferences.Keys.THEME_DARK_MODE
    private const val KEY_MIUIX_PURE_BLACK = AppPreferences.Keys.THEME_PURE_BLACK
    private const val KEY_MIUIX_DYNAMIC_COLOR = AppPreferences.Keys.THEME_DYNAMIC_COLOR
    private const val KEY_MIUIX_CUSTOM_COLOR = AppPreferences.Keys.THEME_CUSTOM_COLOR
    private const val KEY_MIUIX_CUSTOM_COLOR_GLOBAL_TINT = AppPreferences.Keys.THEME_CUSTOM_COLOR_GLOBAL_TINT

    private const val KEY_MATERIAL_FOLLOW_SYSTEM = AppPreferences.Keys.MATERIAL_THEME_FOLLOW_SYSTEM
    private const val KEY_MATERIAL_DARK_MODE = AppPreferences.Keys.MATERIAL_THEME_DARK_MODE
    private const val KEY_MATERIAL_PURE_BLACK = AppPreferences.Keys.MATERIAL_THEME_PURE_BLACK
    private const val KEY_MATERIAL_DYNAMIC_COLOR = AppPreferences.Keys.MATERIAL_THEME_DYNAMIC_COLOR
    private const val KEY_MATERIAL_THEME_COLOR_SOURCE = AppPreferences.Keys.MATERIAL_THEME_COLOR_SOURCE
    private const val KEY_MATERIAL_CUSTOM_COLOR = AppPreferences.Keys.MATERIAL_THEME_CUSTOM_COLOR
    private const val KEY_MATERIAL_CUSTOM_COLOR_GLOBAL_TINT = AppPreferences.Keys.MATERIAL_THEME_CUSTOM_COLOR_GLOBAL_TINT

    const val MATERIAL_THEME_COLOR_SOURCE_DEFAULT = "default"
    const val MATERIAL_THEME_COLOR_SOURCE_CUSTOM = "custom"

    fun applyTheme(context: Context) {
        val prefs = prefs(context)

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
            prefs(context).getBoolean(KEY_MIUIX_PURE_BLACK, false)
        } else {
            getMaterialPureBlack(context)
        }
    }

    // Setters
    fun setLanguage(context: Context, langCode: String) {
        prefs(context)
            .edit().putString(KEY_LANGUAGE, langCode).apply()
        if (langCode.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langCode))
        }
    }

    fun setFollowSystem(context: Context, follow: Boolean) {
        val prefs = prefs(context)
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
        val prefs = prefs(context)
        prefs.edit().putBoolean(activeDarkModeKey(context), enable).apply()
        if (!getFollowSystem(context)) {
            AppCompatDelegate.setDefaultNightMode(if (enable) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    fun setPureBlack(context: Context, enable: Boolean) {
        prefs(context)
            .edit().putBoolean(activePureBlackKey(context), enable).apply()
        // Activity needs recreation to pick this up in onCreate
    }

    fun setDynamicColor(context: Context, enable: Boolean) {
        prefs(context)
            .edit().putBoolean(activeDynamicColorKey(context), enable).apply()
        // Requires App restart or Activity recreation logic
    }

    fun getFollowSystem(context: Context): Boolean {
        val prefs = prefs(context)
        return if (isMiuixEnabled(context)) {
            prefs.getBoolean(KEY_MIUIX_FOLLOW_SYSTEM, true)
        } else {
            readBooleanWithFallback(prefs, KEY_MATERIAL_FOLLOW_SYSTEM, KEY_MIUIX_FOLLOW_SYSTEM, true)
        }
    }

    fun getDarkMode(context: Context): Boolean {
        val prefs = prefs(context)
        return if (isMiuixEnabled(context)) {
            prefs.getBoolean(KEY_MIUIX_DARK_MODE, false)
        } else {
            readBooleanWithFallback(prefs, KEY_MATERIAL_DARK_MODE, KEY_MIUIX_DARK_MODE, false)
        }
    }

    fun getMaterialPureBlack(context: Context): Boolean {
        val prefs = prefs(context)
        return readBooleanWithFallback(prefs, KEY_MATERIAL_PURE_BLACK, KEY_MIUIX_PURE_BLACK, false)
    }

    fun getMaterialDynamicColor(context: Context): Boolean {
        val prefs = prefs(context)
        return readBooleanWithFallback(prefs, KEY_MATERIAL_DYNAMIC_COLOR, KEY_MIUIX_DYNAMIC_COLOR, false)
    }

    fun getMaterialCustomColor(context: Context): Int {
        val prefs = prefs(context)
        return readIntWithFallback(prefs, KEY_MATERIAL_CUSTOM_COLOR, KEY_MIUIX_CUSTOM_COLOR, 0xFF3482FF.toInt())
    }

    fun getMaterialThemeColorSource(context: Context): String {
        val prefs = prefs(context)
        val stored = prefs.getString(KEY_MATERIAL_THEME_COLOR_SOURCE, MATERIAL_THEME_COLOR_SOURCE_DEFAULT)
        return stored.takeIf { it == MATERIAL_THEME_COLOR_SOURCE_DEFAULT || it == MATERIAL_THEME_COLOR_SOURCE_CUSTOM }
            ?: MATERIAL_THEME_COLOR_SOURCE_DEFAULT
    }

    fun isMaterialCustomColorGlobalTintEnabled(context: Context): Boolean {
        return getMaterialThemeColorSource(context) == MATERIAL_THEME_COLOR_SOURCE_CUSTOM
    }

    fun setMaterialCustomColor(context: Context, argb: Int) {
        prefs(context)
            .edit()
            .putInt(KEY_MATERIAL_CUSTOM_COLOR, argb)
            .apply()
    }

    fun setMaterialCustomColorGlobalTint(context: Context, enabled: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_MATERIAL_CUSTOM_COLOR_GLOBAL_TINT, enabled)
            .apply()
    }

    fun setMaterialThemeColorSource(context: Context, source: String) {
        val normalized = when (source) {
            MATERIAL_THEME_COLOR_SOURCE_CUSTOM -> MATERIAL_THEME_COLOR_SOURCE_CUSTOM
            else -> MATERIAL_THEME_COLOR_SOURCE_DEFAULT
        }
        prefs(context)
            .edit()
            .putString(KEY_MATERIAL_THEME_COLOR_SOURCE, normalized)
            .putBoolean(KEY_MATERIAL_CUSTOM_COLOR_GLOBAL_TINT, normalized == MATERIAL_THEME_COLOR_SOURCE_CUSTOM)
            .apply()
    }

    private fun isMiuixEnabled(context: Context): Boolean {
        return prefs(context)
            .getBoolean(KEY_UI_USE_MIUIX, true)
    }

    private fun prefs(context: Context) =
        AppPreferences.of(context)

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
