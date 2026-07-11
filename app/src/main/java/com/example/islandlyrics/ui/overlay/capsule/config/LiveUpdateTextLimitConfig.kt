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
