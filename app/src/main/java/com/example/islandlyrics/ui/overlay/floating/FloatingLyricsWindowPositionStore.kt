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
import androidx.core.content.edit
import com.example.islandlyrics.core.settings.AppPreferences

internal data class FloatingLyricsWindowPosition(
    val x: Int,
    val y: Int,
    val fromPrefs: Boolean
)

internal class FloatingLyricsWindowPositionStore(
    private val context: Context
) {
    fun load(overlayWidth: Int): FloatingLyricsWindowPosition {
        val prefs = prefs()
        val storedPosition = if (prefs.contains(KEY_POS_X) && prefs.contains(KEY_POS_Y)) {
            FloatingLyricsWindowPosition(
                x = prefs.getInt(KEY_POS_X, 0),
                y = prefs.getInt(KEY_POS_Y, dpToPx(DEFAULT_Y_DP)),
                fromPrefs = true
            )
        } else {
            defaultPosition()
        }

        val clamped = clamp(storedPosition.x, storedPosition.y, overlayWidth)
        return storedPosition.copy(x = clamped.x, y = clamped.y)
    }

    fun save(x: Int, y: Int) {
        val prefs = prefs()
        if (prefs.contains(KEY_POS_X) && prefs.contains(KEY_POS_Y) &&
            prefs.getInt(KEY_POS_X, 0) == x && prefs.getInt(KEY_POS_Y, 0) == y) {
            return
        }
        prefs.edit {
            putInt(KEY_POS_X, x)
            putInt(KEY_POS_Y, y)
        }
    }

    fun clamp(x: Int, y: Int, overlayWidth: Int): FloatingLyricsWindowPosition {
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val minVisible = dpToPx(MIN_VISIBLE_DP)
        val overlayLeftWhenCentered = (screenWidth - overlayWidth) / 2
        val minX = -overlayWidth + minVisible - overlayLeftWhenCentered
        val maxX = screenWidth - minVisible - overlayLeftWhenCentered
        val maxY = (screenHeight - minVisible).coerceAtLeast(0)

        return FloatingLyricsWindowPosition(
            x = x.coerceIn(minX, maxX),
            y = y.coerceIn(0, maxY),
            fromPrefs = false
        )
    }

    private fun defaultPosition(): FloatingLyricsWindowPosition =
        FloatingLyricsWindowPosition(0, dpToPx(DEFAULT_Y_DP), fromPrefs = false)

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    private fun prefs() = AppPreferences.of(context)

    companion object {
        const val KEY_POS_X = AppPreferences.Keys.FLOATING_POS_X
        const val KEY_POS_Y = AppPreferences.Keys.FLOATING_POS_Y
        private const val DEFAULT_Y_DP = 140
        private const val MIN_VISIBLE_DP = 48
    }
}
