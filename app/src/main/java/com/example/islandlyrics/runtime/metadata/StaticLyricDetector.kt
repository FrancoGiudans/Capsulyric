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

package com.example.islandlyrics.runtime.metadata

import com.example.islandlyrics.lyrics.state.LyricRepository

object StaticLyricDetector {
    fun isStaticMetadataLyric(
        lyric: String,
        metadata: LyricRepository.MediaInfo?
    ): Boolean {
        if (metadata == null) return false
        val title = metadata.title
        val artist = metadata.artist
        return lyric.equals(title, ignoreCase = true) ||
            lyric.equals(artist, ignoreCase = true) ||
            lyric.equals("$title - $artist", ignoreCase = true) ||
            lyric.equals("$artist - $title", ignoreCase = true)
    }
}
