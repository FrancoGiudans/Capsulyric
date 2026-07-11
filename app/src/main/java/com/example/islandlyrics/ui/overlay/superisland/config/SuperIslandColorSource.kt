/*
 *
 *  * Copyright (c) 2026 Franco Giudance
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

object SuperIslandColorSource {
    const val PREF_KEY = "super_island_color_source"
    const val CUSTOM_COLOR_PREF_KEY = "super_island_custom_color"

    const val ALBUM_ART = "album_art"
    const val CUSTOM = "custom"

    const val DEFAULT_CUSTOM_COLOR = 0xFF3482FF.toInt()

    val values = listOf(ALBUM_ART, CUSTOM)

    fun read(prefs: SharedPreferences): String {
        return prefs.getString(PREF_KEY, ALBUM_ART)
            ?.takeIf { it in values }
            ?: ALBUM_ART
    }

    fun write(prefs: SharedPreferences, source: String) {
        prefs.edit().putString(PREF_KEY, source.takeIf { it in values } ?: ALBUM_ART).apply()
    }

    fun readCustomColor(prefs: SharedPreferences): Int {
        return prefs.getInt(CUSTOM_COLOR_PREF_KEY, DEFAULT_CUSTOM_COLOR)
    }

    fun writeCustomColor(prefs: SharedPreferences, color: Int) {
        prefs.edit().putInt(CUSTOM_COLOR_PREF_KEY, color).apply()
    }

    fun resolveColor(source: String, albumColor: Int, customColor: Int): Int {
        return if (source == CUSTOM) customColor else albumColor
    }
}
