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

package com.example.islandlyrics.ui.overlay.config
import android.content.SharedPreferences
import android.graphics.Color

object OneUiCapsuleColorMode {
    const val PREF_KEY = "oneui_capsule_color_mode"
    private const val LEGACY_PREF_KEY = "oneui_capsule_color_enabled"

    const val BLACK = "black"
    const val TRANSPARENT = "transparent"
    const val TRANSLUCENT_BLACK = "translucent_black"
    const val ALBUM = "album"

    val values = listOf(BLACK, TRANSPARENT, TRANSLUCENT_BLACK, ALBUM)

    fun read(prefs: SharedPreferences): String {
        val stored = prefs.getString(PREF_KEY, null)
        if (stored != null) {
            return stored.takeIf { it in values } ?: BLACK
        }
        return if (prefs.getBoolean(LEGACY_PREF_KEY, false)) ALBUM else BLACK
    }

    fun write(prefs: SharedPreferences, mode: String) {
        val normalized = mode.takeIf { it in values } ?: BLACK
        prefs.edit()
            .putString(PREF_KEY, normalized)
            .putBoolean(LEGACY_PREF_KEY, normalized == ALBUM)
            .apply()
    }

    fun resolveColor(mode: String, albumColor: Int): Int {
        return when (mode) {
            TRANSPARENT -> Color.TRANSPARENT
            // One UI appears to flatten translucent pure black to opaque black.
            // Keep a very dark tint so translucency remains visible in the capsule.
            TRANSLUCENT_BLACK -> Color.argb(160, 24, 24, 24)
            ALBUM -> albumColor
            else -> Color.BLACK
        }
    }
}
