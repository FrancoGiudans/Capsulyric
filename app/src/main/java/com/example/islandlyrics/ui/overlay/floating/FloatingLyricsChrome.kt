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

import android.content.SharedPreferences

internal data class FloatingLyricsChrome(
    val minimalHorizontalPaddingDp: Int,
    val minimalVerticalPaddingDp: Int,
    val minimalBackgroundRadiusDp: Int,
    val minimalBackgroundColor: Int,
    val expandedHorizontalPaddingDp: Int,
    val expandedVerticalPaddingDp: Int,
    val expandedRadiusDp: Int,
    val expandedBackgroundColor: Int,
    val expandedFallbackBackgroundColor: Int,
    val expandedBlurRadiusDp: Int,
    val albumArtSizeDp: Int,
    val albumArtRadiusDp: Int,
    val expandedAlbumArtSizeDp: Int,
    val expandedAlbumArtRadiusDp: Int,
    val innerPanelRadiusDp: Int,
    val iconButtonSizeDp: Int,
    val iconButtonPaddingDp: Int
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun from(prefs: SharedPreferences): FloatingLyricsChrome {
            return FloatingLyricsChrome(
                minimalHorizontalPaddingDp = 12,
                minimalVerticalPaddingDp = 6,
                minimalBackgroundRadiusDp = 14,
                minimalBackgroundColor = 0x99000000.toInt(),
                expandedHorizontalPaddingDp = 14,
                expandedVerticalPaddingDp = 10,
                expandedRadiusDp = 22,
                expandedBackgroundColor = 0x991A1A1C.toInt(),
                expandedFallbackBackgroundColor = 0xE61A1A1C.toInt(),
                expandedBlurRadiusDp = 24,
                albumArtSizeDp = 32,
                albumArtRadiusDp = 8,
                expandedAlbumArtSizeDp = 52,
                expandedAlbumArtRadiusDp = 10,
                innerPanelRadiusDp = 16,
                iconButtonSizeDp = 40,
                iconButtonPaddingDp = 8
            )
        }
    }
}
