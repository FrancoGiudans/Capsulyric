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

package com.example.islandlyrics.ui.overlay.floating
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.theme.ensureColorContrast

internal enum class FloatingLyricsTextWeight(val weight: Int, @param:StringRes val label: Int) {
    REGULAR(400, R.string.settings_floating_weight_regular),
    MEDIUM(500, R.string.settings_floating_weight_medium),
    SEMIBOLD(600, R.string.settings_floating_weight_semibold),
    BOLD(700, R.string.settings_floating_weight_bold);

    companion object {
        fun read(prefs: SharedPreferences): FloatingLyricsTextWeight {
            val weight = prefs.getInt(AppPreferences.Keys.FLOATING_TEXT_WEIGHT, REGULAR.weight)
            return entries.firstOrNull { it.weight == weight } ?: REGULAR
        }
    }
}

internal data class FloatingLyricsStyle(
    val textSizeSp: Float,
    val textWeight: FloatingLyricsTextWeight,
    val baseTextColor: Int,
    val followAlbumColor: Boolean,
    val showAlbumArt: Boolean,
    val enableTextStroke: Boolean,
    val enableTextBackground: Boolean
) {
    fun textColor(albumColor: Int, background: Int): Int {
        if (!followAlbumColor) return baseTextColor
        val opaqueBackground = ColorUtils.setAlphaComponent(background, 255)
        val contrastColor = if (ColorUtils.calculateContrast(Color.WHITE, opaqueBackground) >
            ColorUtils.calculateContrast(Color.BLACK, opaqueBackground)
        ) {
            Color.WHITE
        } else {
            Color.BLACK
        }
        return ensureColorContrast(albumColor, opaqueBackground, contrastColor, minimumContrast = 4.5)
    }
}

internal class FloatingLyricsStyleStore(
    private val context: Context
) {
    var style: FloatingLyricsStyle = load()
        private set

    fun reload(): FloatingLyricsStyle {
        style = load()
        return style
    }

    fun adjustTextSize(delta: Float): FloatingLyricsStyle {
        val prefs = prefs()
        val size = (prefs.getFloat(KEY_TEXT_SIZE, SIZE_DEFAULT) + delta).coerceIn(10f, 32f)
        prefs.edit { putFloat(KEY_TEXT_SIZE, size) }
        style = style.copy(textSizeSp = size)
        return style
    }

    fun adjustTextWeight(delta: Int) {
        val prefs = prefs()
        val current = FloatingLyricsTextWeight.read(prefs)
        val weights = FloatingLyricsTextWeight.entries
        val next = weights[(current.ordinal + delta).coerceIn(weights.indices)]
        if (next == current) return
        prefs.edit { putInt(AppPreferences.Keys.FLOATING_TEXT_WEIGHT, next.weight) }
        style = style.copy(textWeight = next)
    }

    private fun load(): FloatingLyricsStyle {
        val prefs = prefs()
        return FloatingLyricsStyle(
            textSizeSp = prefs.getFloat(KEY_TEXT_SIZE, SIZE_DEFAULT),
            textWeight = FloatingLyricsTextWeight.read(prefs),
            baseTextColor = prefs.getInt(KEY_TEXT_COLOR, Color.WHITE),
            followAlbumColor = prefs.getBoolean(KEY_FOLLOW_ALBUM_COLOR, true),
            showAlbumArt = prefs.getBoolean(KEY_SHOW_ALBUM_ART, true),
            enableTextStroke = prefs.getBoolean(KEY_TEXT_STROKE, true),
            enableTextBackground = prefs.getBoolean(KEY_TEXT_BACKGROUND, false)
        )
    }

    private fun prefs() = AppPreferences.of(context)

    companion object {
        const val KEY_TEXT_SIZE = AppPreferences.Keys.FLOATING_TEXT_SIZE_SP
        const val KEY_TEXT_COLOR = AppPreferences.Keys.FLOATING_TEXT_COLOR
        const val KEY_FOLLOW_ALBUM_COLOR = AppPreferences.Keys.FLOATING_FOLLOW_ALBUM_COLOR
        const val KEY_SHOW_ALBUM_ART = AppPreferences.Keys.FLOATING_SHOW_ALBUM_ART
        const val KEY_TEXT_STROKE = AppPreferences.Keys.FLOATING_TEXT_STROKE
        const val KEY_TEXT_BACKGROUND = AppPreferences.Keys.FLOATING_TEXT_BACKGROUND
        const val SIZE_DEFAULT = 15f
    }
}
