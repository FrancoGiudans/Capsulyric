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

import android.graphics.Color
import android.content.SharedPreferences
import androidx.core.graphics.ColorUtils
import com.example.islandlyrics.core.settings.AppPreferences

object SuperIslandColorSource {
    const val PREF_KEY = "super_island_color_source"
    const val CUSTOM_COLOR_PREF_KEY = "super_island_custom_color"

    const val OFF = "off"
    const val ALBUM_ART = "album_art"
    const val ALBUM_ART_READABLE_WEAK = "album_art_readable_weak"
    const val ALBUM_ART_READABLE_STRONG = "album_art_readable_strong"
    const val CUSTOM = "custom"

    const val DEFAULT_CUSTOM_COLOR = 0xFF3482FF.toInt()
    const val DEFAULT_NEUTRAL_COLOR = 0xFF757575.toInt()

    val values = listOf(
        OFF,
        ALBUM_ART,
        ALBUM_ART_READABLE_WEAK,
        ALBUM_ART_READABLE_STRONG,
        CUSTOM
    )

    fun read(prefs: SharedPreferences): String {
        val source = prefs.getString(PREF_KEY, null)
        return when (source) {
            OFF, ALBUM_ART_READABLE_WEAK, ALBUM_ART_READABLE_STRONG -> source
            ALBUM_ART, CUSTOM -> if (isLegacyEnabled(prefs)) source else OFF
            null -> if (isLegacyEnabled(prefs)) ALBUM_ART else OFF
            else -> if (isLegacyEnabled(prefs)) ALBUM_ART else OFF
        }
    }

    fun write(prefs: SharedPreferences, source: String) {
        val normalizedSource = source.takeIf { it in values } ?: OFF
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

    fun isColorized(source: String): Boolean = source != OFF

    fun resolveColor(source: String, albumColor: Int, customColor: Int): Int {
        return when (source) {
            ALBUM_ART -> albumColor
            ALBUM_ART_READABLE_WEAK -> ensureReadable(
                color = albumColor,
                minimumContrast = WEAK_MINIMUM_CONTRAST,
                minimumWhiteRatio = WEAK_WHITE_RATIO
            )
            ALBUM_ART_READABLE_STRONG -> ensureReadable(
                color = albumColor,
                minimumContrast = STRONG_MINIMUM_CONTRAST,
                minimumWhiteRatio = STRONG_WHITE_RATIO
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

    private const val WEAK_MINIMUM_CONTRAST = 3.0
    private const val STRONG_MINIMUM_CONTRAST = 4.5
    private const val WEAK_WHITE_RATIO = 0.20f
    private const val STRONG_WHITE_RATIO = 0.70f
}
