package com.example.islandlyrics.ui.overlay.floating

import android.content.SharedPreferences
import com.example.islandlyrics.core.settings.AppPreferences

internal data class FloatingLyricsDisplayConfig(
    val displayMode: FloatingLyricsDisplayMode,
    val neighborAlignment: FloatingLyricsNeighborAlignment,
    val wordHighlight: Boolean
) {
    companion object {
        const val KEY_DISPLAY_MODE = AppPreferences.Keys.FLOATING_DISPLAY_MODE
        const val KEY_NEIGHBOR_ALIGNMENT = AppPreferences.Keys.FLOATING_NEIGHBOR_ALIGNMENT
        const val KEY_WORD_HIGHLIGHT = AppPreferences.Keys.FLOATING_WORD_HIGHLIGHT

        fun from(prefs: SharedPreferences): FloatingLyricsDisplayConfig {
            return FloatingLyricsDisplayConfig(
                displayMode = FloatingLyricsDisplayMode.from(
                    prefs.getString(KEY_DISPLAY_MODE, FloatingLyricsDisplayMode.SINGLE_LINE.value)
                ),
                neighborAlignment = FloatingLyricsNeighborAlignment.from(
                    prefs.getString(KEY_NEIGHBOR_ALIGNMENT, FloatingLyricsNeighborAlignment.CENTER.value)
                ),
                wordHighlight = prefs.getBoolean(KEY_WORD_HIGHLIGHT, true)
            )
        }
    }
}

internal enum class FloatingLyricsDisplayMode(val value: String) {
    SINGLE_LINE("single_line"),
    ROMANIZATION("romanization"),
    TRANSLATION("translation"),
    NEIGHBOR_LINE("neighbor_line");

    companion object {
        fun from(value: String?): FloatingLyricsDisplayMode {
            return entries.firstOrNull { it.value == value } ?: SINGLE_LINE
        }
    }
}

internal enum class FloatingLyricsNeighborAlignment(val value: String) {
    CENTER("center"),
    SPLIT_START_END("split_start_end");

    companion object {
        fun from(value: String?): FloatingLyricsNeighborAlignment {
            return entries.firstOrNull { it.value == value } ?: CENTER
        }
    }
}
