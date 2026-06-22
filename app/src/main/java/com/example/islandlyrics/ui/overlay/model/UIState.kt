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
    val mediaPackage: String = "",
    val albumArt: Bitmap? = null
)
