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

import android.content.Context
import android.content.SharedPreferences
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.ui.overlay.config.OneUiCapsuleColorMode

internal class LyricCapsulePreferencesCache(
    context: Context,
    private val onActionsChanged: () -> Unit,
    private val onDynamicIconStyleChanged: () -> Unit,
    private val onPreferencesChanged: () -> Unit = {}
) {
    var actionStyle = "disabled"
        private set
    var showProgressBar = true
        private set
    var useAlbumColor = false
        private set
    var useDynamicIcon = false
        private set
    var iconStyle = "classic"
        private set
    var clickStyle = "default"
        private set
    var oneUiCapsuleColorMode = OneUiCapsuleColorMode.BLACK
        private set
    var liveUpdateTextLimitsEnabled = false
        private set
    var liveUpdateTextWeight = LiveUpdateTextLimitConfig.defaultWeight()
        private set

    private val prefs by lazy { AppPreferences.of(context) }

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            AppPreferences.Keys.NOTIFICATION_ACTIONS_STYLE -> {
                actionStyle = AppPreferences.notificationActionsStyle(p)
                onActionsChanged()
            }
            AppPreferences.Keys.LIVE_UPDATE_SHOW_PROGRESS_BAR -> {
                showProgressBar = AppPreferences.isLiveUpdateShowProgressBar(p)
                onPreferencesChanged()
            }
            AppPreferences.Keys.PROGRESS_BAR_COLOR_ENABLED -> {
                useAlbumColor = AppPreferences.isProgressBarColorEnabled(p)
                onPreferencesChanged()
            }
            AppPreferences.Keys.DYNAMIC_ICON_STYLE -> {
                iconStyle = AppPreferences.dynamicIconStyle(p)
                useDynamicIcon = iconStyle != "disabled"
                onDynamicIconStyleChanged()
            }
            AppPreferences.Keys.NOTIFICATION_CLICK_STYLE -> {
                clickStyle = AppPreferences.notificationClickStyle(p)
                onActionsChanged()
            }
            "oneui_capsule_color_enabled",
            OneUiCapsuleColorMode.PREF_KEY -> {
                oneUiCapsuleColorMode = OneUiCapsuleColorMode.read(p)
                onPreferencesChanged()
            }
            LabFeatureManager.KEY_LIVE_UPDATE_TEXT_LIMITS_ENABLED,
            LiveUpdateTextLimitConfig.KEY_CHARS -> {
                liveUpdateTextLimitsEnabled = LabFeatureManager.isLiveUpdateTextLimitsEnabled(p)
                liveUpdateTextWeight = LiveUpdateTextLimitConfig.weightForChars(
                    LiveUpdateTextLimitConfig.chars(p)
                )
                onPreferencesChanged()
            }
        }
    }

    fun start() {
        load()
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun stop() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun load() {
        actionStyle = AppPreferences.notificationActionsStyle(prefs)
        showProgressBar = AppPreferences.isLiveUpdateShowProgressBar(prefs)
        useAlbumColor = AppPreferences.isProgressBarColorEnabled(prefs)
        iconStyle = AppPreferences.dynamicIconStyle(prefs)
        useDynamicIcon = iconStyle != "disabled"
        clickStyle = AppPreferences.notificationClickStyle(prefs)
        oneUiCapsuleColorMode = OneUiCapsuleColorMode.read(prefs)
        liveUpdateTextLimitsEnabled = LabFeatureManager.isLiveUpdateTextLimitsEnabled(prefs)
        liveUpdateTextWeight = LiveUpdateTextLimitConfig.weightForChars(
            LiveUpdateTextLimitConfig.chars(prefs)
        )
    }
}
