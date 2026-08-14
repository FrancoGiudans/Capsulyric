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

package com.example.islandlyrics.feature.customsettings

import com.example.islandlyrics.core.platform.XmsfBypassMode
import com.example.islandlyrics.ui.overlay.config.CapsuleRenderMode
import com.example.islandlyrics.ui.navigation.PredictiveBackAnimationMode
import com.example.islandlyrics.ui.navigation.PredictiveBackAnimationStyle

/** 个性化设置页拆分后各页面可用的页签集合。 */
enum class CustomSettingsTab {
    CAPSULE,
    NOTIFICATION,
    APP_UI,
    DESKTOP_LYRICS
}

data class CustomSettingsUiState(
    val floatingLyricsLabEnabled: Boolean = false,
    val followSystem: Boolean = true,
    val darkMode: Boolean = false,
    val pureBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    val materialThemeColorSource: String = "default",
    val customThemeColor: Int = 0xFF3482FF.toInt(),
    val iconStyle: String = "disabled",
    val actionStyle: String = "disabled",
    val superIslandMediaButtonLayout: String = "two_button",
    val superIslandNotificationStyle: String = "standard",
    val superIslandDualLineMode: String = "translation",
    val superIslandShowProgressBar: Boolean = true,
    val superIslandSecondaryTextModes: List<String> = listOf("next_lyric"),
    val superIslandTemplate2PicSource: String = "album_art",
    val superIslandTemplate2CustomPicUri: String? = null,
    val superIslandAdvancedStyleLabEnabled: Boolean = false,
    val superIslandTextLimitsLabEnabled: Boolean = false,
    val superIslandRelaxedTextLimitsLabEnabled: Boolean = false,
    val liveUpdateTextLimitsLabEnabled: Boolean = false,
    val liveUpdateTextChars: Float = 5f,
    val notificationClickStyle: String = "default",
    val dismissDelayMs: Long = 0L,
    val progressColorEnabled: Boolean = false,
    val disableScrolling: Boolean = false,
    val lockScreenHideNotification: Boolean = false,
    val lyricTextDisplayMode: String = "lyric",
    val oneuiCapsuleColorMode: String = "black",
    val capsuleRenderMode: CapsuleRenderMode = CapsuleRenderMode.XIAOMI_SUPER_ISLAND,
    val superIslandLyricMode: String = "standard",
    val superIslandFullLyricShowLeftCover: Boolean = true,
    val superIslandTextColorEnabled: Boolean = false,
    val superIslandColorSource: String = "album_art",
    val superIslandCustomColor: Int = 0xFF3482FF.toInt(),
    val superIslandShareEnabled: Boolean = true,
    val superIslandShareFormat: String = "format_1",
    val miuixEnabled: Boolean = true,
    val predictiveBackEnabled: Boolean = true,
    val predictiveBackAnimationMode: PredictiveBackAnimationMode = PredictiveBackAnimationMode.default,
    val predictiveBackAnimationStyle: PredictiveBackAnimationStyle = PredictiveBackAnimationStyle.default,
    val homeLyricPreviewDisplayModes: List<String> = listOf("next_lyric"),
    val monetEnabled: Boolean = true,
    val customThemeGlobalTintEnabled: Boolean = false,
    val cardBlurEnabled: Boolean = false
)

sealed interface CustomSettingsAction {
    data class SetFollowSystem(val value: Boolean) : CustomSettingsAction
    data class SetDarkMode(val value: Boolean) : CustomSettingsAction
    data class SetPureBlack(val value: Boolean) : CustomSettingsAction
    data class SetDynamicColor(val value: Boolean) : CustomSettingsAction
    data class SetMaterialThemeColorSource(val value: String) : CustomSettingsAction
    data class SetMaterialCustomColor(val value: Int) : CustomSettingsAction
    data class SetDynamicIconStyle(val value: String) : CustomSettingsAction
    data class SetNotificationActionsStyle(val value: String) : CustomSettingsAction
    data class SetNotificationClickStyle(val value: String) : CustomSettingsAction
    data class SetDismissDelay(val value: Long) : CustomSettingsAction
    data class SetProgressColorEnabled(val value: Boolean) : CustomSettingsAction
    data class SetDisableScrolling(val value: Boolean) : CustomSettingsAction
    data class SetLockScreenHideNotification(val value: Boolean) : CustomSettingsAction
    data class ApplySuperIslandScrollForce(
        val force: Boolean,
        val restoreLegacyState: Boolean,
        val currentDisableScrolling: Boolean
    ) : CustomSettingsAction
    data class SetLyricTextDisplayMode(val value: String) : CustomSettingsAction
    data class SetOneUiCapsuleColorMode(val value: String) : CustomSettingsAction
    data class SetCapsuleRenderMode(val value: CapsuleRenderMode) : CustomSettingsAction
    data class SetSuperIslandLyricMode(val value: String) : CustomSettingsAction
    data class SetSuperIslandFullLyricShowLeftCover(val value: Boolean) : CustomSettingsAction
    data class SetSuperIslandTextLimit(val key: String, val value: Float) : CustomSettingsAction
    data class SetLiveUpdateTextLimit(val value: Float) : CustomSettingsAction
    data class SetSuperIslandTextColorEnabled(val value: Boolean) : CustomSettingsAction
    data class SetSuperIslandColorSource(val value: String) : CustomSettingsAction
    data class SetSuperIslandCustomColor(val value: Int) : CustomSettingsAction
    data class SetSuperIslandShareEnabled(val value: Boolean) : CustomSettingsAction
    data class SetSuperIslandShareFormat(val value: String) : CustomSettingsAction
    data class SetSuperIslandNotificationStyle(val value: String) : CustomSettingsAction
    data class SetSuperIslandDualLineMode(val value: String) : CustomSettingsAction
    data class SetSuperIslandMediaButtonLayout(val value: String) : CustomSettingsAction
    data class SetSuperIslandShowProgressBar(val value: Boolean) : CustomSettingsAction
    data class SetSuperIslandSecondaryTextModes(val value: List<String>) : CustomSettingsAction
    data class SetSuperIslandTemplate2PicSource(val value: String) : CustomSettingsAction
    data class SetSuperIslandTemplate2CustomPicUri(val value: String?) : CustomSettingsAction
    data class SetXmsfBypassMode(val value: XmsfBypassMode) : CustomSettingsAction
    data class SetXmsfCustomDurationMs(val value: Int) : CustomSettingsAction
    data class SetMiuixEnabled(val value: Boolean) : CustomSettingsAction
    data class SetMiuixThemeColorSource(val value: String) : CustomSettingsAction
    data class SetMiuixThemeCustomColor(val value: Int) : CustomSettingsAction
    data class SetMiuixThemeGlobalTintEnabled(val value: Boolean) : CustomSettingsAction
    data class SetCardBlurEnabled(val value: Boolean) : CustomSettingsAction
    data class SetPredictiveBackEnabled(val value: Boolean) : CustomSettingsAction
    data class SetPredictiveBackAnimationMode(val value: PredictiveBackAnimationMode) : CustomSettingsAction
    data class SetPredictiveBackAnimationStyle(val value: PredictiveBackAnimationStyle) : CustomSettingsAction
    data class SetHomeLyricPreviewDisplayModes(val value: List<String>) : CustomSettingsAction
}
