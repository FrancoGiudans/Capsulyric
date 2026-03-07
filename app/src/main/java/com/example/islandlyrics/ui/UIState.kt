package com.example.islandlyrics.ui

import com.example.islandlyrics.data.lyric.OnlineLyricFetcher

/**
 * Unified UI State for all Lyric Renderers (Capsule and Super Island)
 */
data class UIState(
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val displayLyric: String = "",
    val fullLyric: String = "",
    val isStatic: Boolean = false,
    val progressMax: Int = 0,
    val progressCurrent: Int = 0,
    val albumColor: Int = 0xFF202124.toInt(),
    val useSyllableScrolling: Boolean = false,
    val syllableLines: List<OnlineLyricFetcher.LyricLine>? = null,
    val currentLineIndex: Int = -1,
    val mediaPackage: String = ""
)
