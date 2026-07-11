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
import com.example.islandlyrics.lyrics.state.LyricRepository

object LyricTextDisplayMode {
    const val PREF_KEY = "lyric_text_display_mode"

    const val LYRIC = "lyric"
    const val TRANSLATION = "translation"
    const val ROMANIZATION = "romanization"

    val values = listOf(LYRIC, TRANSLATION, ROMANIZATION)

    fun read(prefs: SharedPreferences): String {
        val stored = prefs.getString(PREF_KEY, LYRIC) ?: LYRIC
        return if (stored in values) stored else LYRIC
    }

    fun write(prefs: SharedPreferences, mode: String) {
        prefs.edit().putString(PREF_KEY, if (mode in values) mode else LYRIC).apply()
    }

    fun resolve(
        mode: String,
        lyricInfo: LyricRepository.LyricInfo?,
        fallbackText: String
    ): String {
        val preferred = when (mode) {
            TRANSLATION -> lyricInfo?.translation
            ROMANIZATION -> lyricInfo?.roma
            else -> null
        }?.takeIf { it.isNotBlank() }
        return preferred ?: fallbackText
    }
}
