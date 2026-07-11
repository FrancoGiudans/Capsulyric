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

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.state.LyricRepository

/**
 * Structured lyric data for renderers that can lay out multiple lyric layers.
 *
 * Legacy renderers can keep using UIState.displayLyric/fullLyric, while richer
 * surfaces can use this model for original/romanization/translation lines,
 * neighboring lines, and word-level progress.
 */
data class LyricPresentation(
    val currentLine: DisplayLine? = null,
    val previousLine: DisplayLine? = null,
    val nextLine: DisplayLine? = null,
    val currentLineIndex: Int = -1,
    val timelineCapability: LyricRepository.TimelineCapability = LyricRepository.TimelineCapability.NONE,
    val wordProgress: WordProgress? = null
) {
    val canShowNeighborLine: Boolean
        get() = timelineCapability == LyricRepository.TimelineCapability.MULTI_LINE &&
                (previousLine != null || nextLine != null)

    val hasCounterpart: Boolean
        get() = currentLine?.romanization != null || currentLine?.translation != null

    data class DisplayLine(
        val text: String,
        val romanization: String? = null,
        val translation: String? = null,
        val startTime: Long = 0L,
        val endTime: Long = 0L,
        val syllables: List<OnlineLyricFetcher.SyllableInfo>? = null
    ) {
        val hasSyllables: Boolean
            get() = !syllables.isNullOrEmpty()
    }

    data class WordProgress(
        val sungText: String,
        val sungSyllableCount: Int,
        val totalSyllableCount: Int,
        val lineProgress: Float
    )
}
