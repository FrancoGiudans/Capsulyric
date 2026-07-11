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

package com.example.islandlyrics.ui.overlay.display
import android.graphics.Bitmap
import androidx.palette.graphics.Palette

internal class AlbumArtColorExtractor(
    private val defaultColor: Int
) {
    var currentColor: Int = defaultColor
        private set

    private var lastExtractedArtHash = 0

    fun reset() {
        currentColor = defaultColor
        lastExtractedArtHash = 0
    }

    fun extract(
        bitmap: Bitmap?,
        currentBitmapHashProvider: () -> Int?
    ) {
        if (bitmap == null) {
            reset()
            return
        }

        val artHash = bitmap.hashCode()
        if (artHash == lastExtractedArtHash) return

        Palette.from(bitmap).generate { palette ->
            if (artHash != currentBitmapHashProvider()) return@generate
            if (palette != null) {
                currentColor = palette.getVibrantColor(
                    palette.getMutedColor(
                        palette.getDominantColor(defaultColor)
                    )
                )
                lastExtractedArtHash = artHash
            }
        }
    }
}
