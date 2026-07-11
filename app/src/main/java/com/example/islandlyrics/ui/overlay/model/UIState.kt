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

package com.example.islandlyrics.ui.overlay.model

import android.graphics.Bitmap
import com.example.islandlyrics.ui.overlay.config.OverlayRenderDefaults
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher

/**
 * Unified UI State for all Lyric Renderers (Capsule and Super Island)
 */
data class UIState(
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val displayLyric: String = "",
    val fullLyric: String = "",
    val preferMetadataLayout: Boolean = false,
    val isTimingGapPlaceholder: Boolean = false,
    val timelineCapability: LyricRepository.TimelineCapability = LyricRepository.TimelineCapability.NONE,
    val isStatic: Boolean = false,
    val progressMax: Int = 0,
    val progressCurrent: Int = 0,
    val albumColor: Int = OverlayRenderDefaults.COLOR_PRIMARY,
    val useSyllableScrolling: Boolean = false,
    val syllableLines: List<OnlineLyricFetcher.LyricLine>? = null,
    val currentLineIndex: Int = -1,
    val lyricPresentation: LyricPresentation = LyricPresentation(),
    val mediaPackage: String = "",
    val albumArt: Bitmap? = null
)
