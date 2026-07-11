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

package com.example.islandlyrics.ui.overlay.superisland.render
import com.example.islandlyrics.ui.overlay.model.UIState
internal object SuperIslandTextResolver {
    fun isPlaceholder(text: String): Boolean {
        return text.isBlank() || text.trim() == "♪"
    }

    fun primaryText(state: UIState): String {
        val candidates = if (state.isTimingGapPlaceholder && !state.preferMetadataLayout) {
            sequenceOf(state.fullLyric, state.displayLyric)
        } else {
            sequenceOf(state.fullLyric, state.displayLyric, state.title)
        }
        return candidates
            .firstOrNull { !isPlaceholder(it) }
            ?: "♪"
    }

    fun compactText(state: UIState): String {
        val candidates = if (state.isTimingGapPlaceholder && !state.preferMetadataLayout) {
            sequenceOf(state.displayLyric)
        } else {
            sequenceOf(state.displayLyric, state.title)
        }
        return candidates
            .firstOrNull { !isPlaceholder(it) }
            ?: "♪"
    }

    fun shareContent(state: UIState, format: String): String {
        val primary = primaryText(state)
        val artist = if (state.artist.isNotBlank()) state.artist else "未知歌手"
        val song = state.title.ifEmpty { "未知歌曲" }
        return when (format) {
            "format_2" -> "$primary -$artist\uff0c$song"
            "format_3" -> "$primary\n$artist\uff0c$song"
            else -> "$primary\n$song by $artist"
        }
    }
}
