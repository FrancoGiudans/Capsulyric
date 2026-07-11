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

package com.example.islandlyrics.lyrics.state

internal class TrackChangeDetector {
    private var lastTrackId: String? = null

    fun didTrackChange(
        title: String,
        artist: String,
        packageName: String
    ): Boolean {
        val currentTrackId = trackId(title, artist, packageName)
        if (lastTrackId == currentTrackId) return false
        lastTrackId = currentTrackId
        return true
    }

    fun describe(
        title: String,
        artist: String,
        packageName: String
    ): String = trackId(title, artist, packageName)

    private fun trackId(
        title: String,
        artist: String,
        packageName: String
    ): String = "$title-$artist-$packageName"
}
