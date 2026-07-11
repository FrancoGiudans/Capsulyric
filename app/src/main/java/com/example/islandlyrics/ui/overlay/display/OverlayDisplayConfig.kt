/*
 *
 *  * Copyright (c) 2026 Franco Giudance
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

package com.example.islandlyrics.ui.overlay.display
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandTextLimitConfig
import com.example.islandlyrics.ui.overlay.config.LyricTextDisplayMode
import com.example.islandlyrics.ui.overlay.config.CapsuleRenderMode
import com.example.islandlyrics.ui.overlay.capsule.config.LiveUpdateTextLimitConfig
import android.content.SharedPreferences
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.LabFeatureManager

internal data class OverlayDisplayConfig(
    val disableScrolling: Boolean,
    val lyricTextDisplayMode: String,
    val capsuleRenderMode: CapsuleRenderMode,
    val superIslandLyricMode: String,
    val superIslandRightTextWeight: Int,
    val liveUpdateTextLimitsEnabled: Boolean,
    val liveUpdateTextWeight: Int
) {
    fun maxDisplayWeight(baseMaxDisplayWeight: Int): Int {
        return when {
            capsuleRenderMode == CapsuleRenderMode.LIVE_UPDATE ->
                if (liveUpdateTextLimitsEnabled) liveUpdateTextWeight else LiveUpdateTextLimitConfig.defaultWeight()
            RomUtils.isHyperOs() &&
                capsuleRenderMode == CapsuleRenderMode.XIAOMI_SUPER_ISLAND &&
                superIslandLyricMode == "standard" -> superIslandRightTextWeight
            else -> baseMaxDisplayWeight
        }
    }

    companion object {
        const val KEY_SUPER_ISLAND_RELAXED_TEXT_LIMITS =
            "lab_super_island_relaxed_text_limits_enabled"

        fun from(prefs: SharedPreferences): OverlayDisplayConfig {
            val relaxedLimitsEnabled = prefs.getBoolean(KEY_SUPER_ISLAND_RELAXED_TEXT_LIMITS, false)
            return OverlayDisplayConfig(
                disableScrolling = AppPreferences.isLyricScrollingDisabled(prefs),
                lyricTextDisplayMode = LyricTextDisplayMode.read(prefs),
                capsuleRenderMode = CapsuleRenderMode.effective(prefs),
                superIslandLyricMode = AppPreferences.superIslandLyricMode(prefs),
                superIslandRightTextWeight = SuperIslandTextLimitConfig.weightForChars(
                    SuperIslandTextLimitConfig.rightChars(
                        prefs = prefs,
                        relaxed = relaxedLimitsEnabled
                    )
                ),
                liveUpdateTextLimitsEnabled = LabFeatureManager.isLiveUpdateTextLimitsEnabled(prefs),
                liveUpdateTextWeight = LiveUpdateTextLimitConfig.weightForChars(
                    LiveUpdateTextLimitConfig.chars(prefs)
                )
            )
        }
    }
}
