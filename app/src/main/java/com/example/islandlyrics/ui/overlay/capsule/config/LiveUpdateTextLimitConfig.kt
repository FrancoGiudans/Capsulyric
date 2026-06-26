package com.example.islandlyrics.ui.overlay.capsule.config

import android.content.SharedPreferences
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandTextLimitConfig

object LiveUpdateTextLimitConfig {
    const val KEY_CHARS = "live_update_text_limit_chars"

    const val MIN_CHARS = 5f
    const val MAX_CHARS = SuperIslandTextLimitConfig.RIGHT_MAX_CHARS

    fun defaultChars(): Float {
        return when (RomUtils.getRomType()) {
            "AOSP", "OneUI" -> MIN_CHARS
            else -> MAX_CHARS
        }
    }

    fun chars(prefs: SharedPreferences): Float =
        prefs.getFloat(KEY_CHARS, defaultChars()).coerceIn(MIN_CHARS, MAX_CHARS)

    fun weightForChars(chars: Float): Int =
        SuperIslandTextLimitConfig.weightForChars(chars)

    fun defaultWeight(): Int =
        weightForChars(defaultChars())
}
