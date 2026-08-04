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
 * 模板2（文本组件2 + 识别图形组件1）中识别图形组件的图片来源。
 */
internal enum class SuperIslandTemplate2PicSource(val preferenceValue: String) {
    ALBUM_ART("album_art"),
    PLAYING_APP("playing_app"),
    APP_ICON("app_icon"),
    CUSTOM("custom");

    companion object {
        const val PREF_KEY = AppPreferences.Keys.SUPER_ISLAND_TEMPLATE2_PIC_SOURCE
        const val CUSTOM_PIC_PREF_KEY = AppPreferences.Keys.SUPER_ISLAND_TEMPLATE2_CUSTOM_PIC_URI

        fun from(value: String?): SuperIslandTemplate2PicSource =
            entries.firstOrNull { it.preferenceValue == value } ?: ALBUM_ART

        fun read(prefs: SharedPreferences): SuperIslandTemplate2PicSource =
            from(prefs.getString(PREF_KEY, null))

        fun write(prefs: SharedPreferences, source: SuperIslandTemplate2PicSource) {
            prefs.edit { putString(PREF_KEY, source.preferenceValue) }
        }

        fun readCustomPicUri(prefs: SharedPreferences): String? =
            prefs.getString(CUSTOM_PIC_PREF_KEY, null)

        fun writeCustomPicUri(prefs: SharedPreferences, uri: String?) {
            if (uri == null) {
                prefs.edit { remove(CUSTOM_PIC_PREF_KEY) }
            } else {
                prefs.edit { putString(CUSTOM_PIC_PREF_KEY, uri) }
            }
        }
    }
}
