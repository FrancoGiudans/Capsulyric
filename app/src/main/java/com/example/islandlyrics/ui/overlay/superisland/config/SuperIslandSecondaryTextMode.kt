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
 *  *
 *  *
 */

package com.example.islandlyrics.ui.overlay.superisland.config

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.islandlyrics.core.settings.AppPreferences

/**
 * 超级岛展开态次要文本内容（主要文本固定为当前句歌词）。
 * 支持多选并按列表顺序决定优先级。
 */
internal enum class SuperIslandSecondaryTextMode(val preferenceValue: String) {
    NEXT_LYRIC("next_lyric"),
    TRANSLATION("translation"),
    ROMANIZATION("romanization");

    companion object {
        const val PREF_KEY = AppPreferences.Keys.SUPER_ISLAND_SECONDARY_TEXT_MODES
        val DEFAULT = listOf(NEXT_LYRIC)

        fun from(value: String?): SuperIslandSecondaryTextMode? =
            entries.firstOrNull { it.preferenceValue == value }

        fun read(prefs: SharedPreferences): List<SuperIslandSecondaryTextMode> {
            val raw = prefs.getString(PREF_KEY, null)?.takeIf { it.isNotBlank() }
                ?: return DEFAULT
            val modes = LinkedHashSet<SuperIslandSecondaryTextMode>()
            raw.split(',').forEach { token ->
                from(token)?.let { modes.add(it) }
            }
            return modes.toList().ifEmpty { DEFAULT }
        }

        fun write(prefs: SharedPreferences, modes: List<SuperIslandSecondaryTextMode>) {
            prefs.edit { putString(PREF_KEY, modes.joinToString(",") { it.preferenceValue }) }
        }
    }
}
