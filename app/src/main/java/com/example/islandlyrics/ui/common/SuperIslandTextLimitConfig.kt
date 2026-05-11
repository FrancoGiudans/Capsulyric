package com.example.islandlyrics.ui.common

import android.content.SharedPreferences

object SuperIslandTextLimitConfig {
    const val KEY_RIGHT_CHARS = "super_island_text_limit_right_chars"
    const val KEY_LEFT_WITH_COVER_CHARS = "super_island_text_limit_left_with_cover_chars"
    const val KEY_LEFT_NO_COVER_CHARS = "super_island_text_limit_left_no_cover_chars"

    const val RIGHT_MIN_CHARS = 6f
    const val RIGHT_MAX_CHARS = 8f
    const val RIGHT_DEFAULT_CHARS = 7f

    const val LEFT_WITH_COVER_MIN_CHARS = 4f
    const val LEFT_WITH_COVER_MAX_CHARS = 6f
    const val LEFT_WITH_COVER_DEFAULT_CHARS = 6f

    const val LEFT_NO_COVER_MIN_CHARS = RIGHT_MIN_CHARS
    const val LEFT_NO_COVER_MAX_CHARS = RIGHT_MAX_CHARS
    const val LEFT_NO_COVER_DEFAULT_CHARS = RIGHT_MAX_CHARS

    fun rightChars(prefs: SharedPreferences): Float =
        prefs.getFloat(KEY_RIGHT_CHARS, RIGHT_DEFAULT_CHARS)
            .coerceIn(RIGHT_MIN_CHARS, RIGHT_MAX_CHARS)

    fun leftChars(prefs: SharedPreferences, showLeftCover: Boolean): Float {
        return if (showLeftCover) {
            prefs.getFloat(KEY_LEFT_WITH_COVER_CHARS, LEFT_WITH_COVER_DEFAULT_CHARS)
                .coerceIn(LEFT_WITH_COVER_MIN_CHARS, LEFT_WITH_COVER_MAX_CHARS)
        } else {
            prefs.getFloat(KEY_LEFT_NO_COVER_CHARS, LEFT_NO_COVER_DEFAULT_CHARS)
                .coerceIn(LEFT_NO_COVER_MIN_CHARS, LEFT_NO_COVER_MAX_CHARS)
        }
    }

    fun weightForChars(chars: Float): Int = (chars * 2f).toInt()
}
