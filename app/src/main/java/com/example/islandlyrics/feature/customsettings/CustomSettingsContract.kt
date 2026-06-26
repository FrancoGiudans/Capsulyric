package com.example.islandlyrics.feature.customsettings

import com.example.islandlyrics.core.platform.XmsfBypassMode
import com.example.islandlyrics.ui.overlay.config.CapsuleRenderMode
import com.example.islandlyrics.ui.navigation.PredictiveBackAnimationMode
import com.example.islandlyrics.ui.navigation.PredictiveBackAnimationStyle

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
    val superIslandAdvancedStyleLabEnabled: Boolean = false,
    val superIslandTextLimitsLabEnabled: Boolean = false,
    val superIslandRelaxedTextLimitsLabEnabled: Boolean = false,
    val liveUpdateTextLimitsLabEnabled: Boolean = false,
    val liveUpdateTextChars: Float = 5f,
    val notificationClickStyle: String = "default",
    val dismissDelayMs: Long = 0L,
    val progressColorEnabled: Boolean = false,
    val disableScrolling: Boolean = false,
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
    val homeLyricPreviewDisplayModes: Set<String> = setOf("lyric"),
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
    data class SetSuperIslandMediaButtonLayout(val value: String) : CustomSettingsAction
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
    data class SetHomeLyricPreviewDisplayModes(val value: Set<String>) : CustomSettingsAction
}
