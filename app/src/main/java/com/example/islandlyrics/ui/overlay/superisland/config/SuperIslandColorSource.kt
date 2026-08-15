/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.ui.overlay.superisland.config

import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.example.islandlyrics.core.settings.AppPreferences

object SuperIslandColorSource {
    const val PREF_KEY = "super_island_color_source"
    const val CUSTOM_COLOR_PREF_KEY = "super_island_custom_color"
    const val SMART_CONTRAST_PREF_KEY = "super_island_smart_min_contrast"
    const val SMART_WHITE_RATIO_PREF_KEY = "super_island_smart_white_ratio"

    const val OFF = "off"
    const val ALBUM_ART = "album_art"
    const val ALBUM_ART_SMART = "album_art_smart"
    const val CUSTOM = "custom"

    const val DEFAULT_CUSTOM_COLOR = 0xFF3482FF.toInt()
    const val DEFAULT_NEUTRAL_COLOR = 0xFF757575.toInt()
    const val SMART_CONTRAST_MIN = 3.0f
    const val SMART_CONTRAST_MAX = 6.0f
    const val SMART_CONTRAST_DEFAULT = 4.5f
    const val SMART_WHITE_RATIO_MIN = 0.0f
    const val SMART_WHITE_RATIO_MAX = 0.5f
    const val SMART_WHITE_RATIO_DEFAULT = 0.20f

    private const val LEGACY_READABLE_WEAK = "album_art_readable_weak"
    private const val LEGACY_READABLE_STRONG = "album_art_readable_strong"

    val values = listOf(OFF, ALBUM_ART, ALBUM_ART_SMART, CUSTOM)

    fun read(prefs: SharedPreferences): String {
        val raw = prefs.getString(PREF_KEY, null)
        val source = when (raw) {
            LEGACY_READABLE_WEAK, LEGACY_READABLE_STRONG -> ALBUM_ART_SMART
            OFF, ALBUM_ART, ALBUM_ART_SMART, CUSTOM -> raw
            else -> null
        }
        return when (source) {
            OFF -> OFF
            null -> if (isLegacyEnabled(prefs)) ALBUM_ART else OFF
            else -> if (isLegacyEnabled(prefs)) source else OFF
        }
    }

    fun write(prefs: SharedPreferences, source: String) {
        val migratedSource = when (source) {
            LEGACY_READABLE_WEAK, LEGACY_READABLE_STRONG -> ALBUM_ART_SMART
            else -> source
        }
        val normalizedSource = migratedSource.takeIf { it in values } ?: OFF
        prefs.edit()
            .putString(PREF_KEY, normalizedSource)
            .putBoolean(AppPreferences.Keys.SUPER_ISLAND_TEXT_COLOR_ENABLED, normalizedSource != OFF)
            .apply()
    }

    fun readCustomColor(prefs: SharedPreferences): Int {
        return prefs.getInt(CUSTOM_COLOR_PREF_KEY, DEFAULT_CUSTOM_COLOR)
    }

    fun writeCustomColor(prefs: SharedPreferences, color: Int) {
        prefs.edit().putInt(CUSTOM_COLOR_PREF_KEY, color).apply()
    }

    fun readSmartMinContrast(prefs: SharedPreferences): Float =
        prefs.getFloat(SMART_CONTRAST_PREF_KEY, SMART_CONTRAST_DEFAULT)
            .coerceIn(SMART_CONTRAST_MIN, SMART_CONTRAST_MAX)

    fun writeSmartMinContrast(prefs: SharedPreferences, value: Float) {
        prefs.edit()
            .putFloat(SMART_CONTRAST_PREF_KEY, value.coerceIn(SMART_CONTRAST_MIN, SMART_CONTRAST_MAX))
            .apply()
    }

    fun readSmartWhiteRatio(prefs: SharedPreferences): Float =
        prefs.getFloat(SMART_WHITE_RATIO_PREF_KEY, SMART_WHITE_RATIO_DEFAULT)
            .coerceIn(SMART_WHITE_RATIO_MIN, SMART_WHITE_RATIO_MAX)

    fun writeSmartWhiteRatio(prefs: SharedPreferences, value: Float) {
        prefs.edit()
            .putFloat(SMART_WHITE_RATIO_PREF_KEY, value.coerceIn(SMART_WHITE_RATIO_MIN, SMART_WHITE_RATIO_MAX))
            .apply()
    }

    fun isColorized(source: String): Boolean = source != OFF

    fun resolveColor(
        source: String,
        albumColor: Int,
        customColor: Int,
        smartMinimumContrast: Float = SMART_CONTRAST_DEFAULT,
        smartWhiteRatio: Float = SMART_WHITE_RATIO_DEFAULT
    ): Int {
        return when (source) {
            ALBUM_ART -> albumColor
            ALBUM_ART_SMART -> ensureReadable(
                color = albumColor,
                minimumContrast = smartMinimumContrast.toDouble(),
                minimumWhiteRatio = smartWhiteRatio
            )
            CUSTOM -> customColor
            else -> DEFAULT_NEUTRAL_COLOR
        }
    }

    private fun isLegacyEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(AppPreferences.Keys.SUPER_ISLAND_TEXT_COLOR_ENABLED, false)

    private fun ensureReadable(
        color: Int,
        minimumContrast: Double,
        minimumWhiteRatio: Float
    ): Int {
        val baseColor = ColorUtils.blendARGB(color, Color.WHITE, minimumWhiteRatio)
        if (ColorUtils.calculateContrast(baseColor, Color.BLACK) >= minimumContrast) return baseColor

        var low = minimumWhiteRatio
        var high = 1f
        repeat(20) {
            val ratio = (low + high) / 2f
            val candidate = ColorUtils.blendARGB(color, Color.WHITE, ratio)
            if (ColorUtils.calculateContrast(candidate, Color.BLACK) >= minimumContrast) {
                high = ratio
            } else {
                low = ratio
            }
        }
        return ColorUtils.blendARGB(color, Color.WHITE, high)
    }

}
