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

internal object SuperIslandDualLineMode {
    const val PREF_KEY = "super_island_dual_line_mode"

    const val NEXT_LYRIC = "next_lyric"
    const val TRANSLATION = "translation"
    const val ROMANIZATION = "romanization"

    val values = listOf(NEXT_LYRIC, TRANSLATION, ROMANIZATION)

    fun read(prefs: SharedPreferences): String {
        val stored = prefs.getString(PREF_KEY, TRANSLATION) ?: TRANSLATION
        return if (stored in values) stored else TRANSLATION
    }
}
