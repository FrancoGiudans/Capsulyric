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
