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
                expandedBackgroundColor = 0xE61A1A1C.toInt(),
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
