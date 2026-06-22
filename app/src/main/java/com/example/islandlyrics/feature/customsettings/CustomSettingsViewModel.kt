package com.example.islandlyrics.feature.customsettings

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.core.platform.XmsfBypassMode
import com.example.islandlyrics.ui.overlay.config.CapsuleRenderMode
import com.example.islandlyrics.ui.overlay.config.LyricTextDisplayMode
import com.example.islandlyrics.ui.overlay.config.OneUiCapsuleColorMode
import com.example.islandlyrics.ui.navigation.PREF_PREDICTIVE_BACK_ENABLED
import com.example.islandlyrics.ui.navigation.PredictiveBackAnimationMode
import com.example.islandlyrics.ui.navigation.PredictiveBackAnimationStyle
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandColorSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CustomSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferences.of(application)
    private val _uiState = MutableStateFlow(readState())
    val uiState: StateFlow<CustomSettingsUiState> = _uiState.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refresh()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun dispatch(action: CustomSettingsAction) {
        val app = getApplication<Application>()
        when (action) {
            is CustomSettingsAction.SetFollowSystem ->
                ThemeHelper.setFollowSystem(app, action.value)
            is CustomSettingsAction.SetDarkMode ->
                ThemeHelper.setDarkMode(app, action.value)
            is CustomSettingsAction.SetPureBlack ->
                ThemeHelper.setPureBlack(app, action.value)
            is CustomSettingsAction.SetDynamicColor ->
                ThemeHelper.setDynamicColor(app, action.value)
            is CustomSettingsAction.SetMaterialThemeColorSource ->
                ThemeHelper.setMaterialThemeColorSource(app, action.value)
            is CustomSettingsAction.SetMaterialCustomColor ->
                ThemeHelper.setMaterialCustomColor(app, action.value)
            is CustomSettingsAction.SetDynamicIconStyle ->
                prefs.edit { putString(AppPreferences.Keys.DYNAMIC_ICON_STYLE, action.value) }
            is CustomSettingsAction.SetNotificationActionsStyle ->
                prefs.edit { putString(AppPreferences.Keys.NOTIFICATION_ACTIONS_STYLE, action.value) }
            is CustomSettingsAction.SetNotificationClickStyle ->
                prefs.edit { putString(AppPreferences.Keys.NOTIFICATION_CLICK_STYLE, action.value) }
            is CustomSettingsAction.SetDismissDelay ->
                prefs.edit { putLong("notification_dismiss_delay", action.value) }
            is CustomSettingsAction.SetProgressColorEnabled ->
                prefs.edit { putBoolean(AppPreferences.Keys.PROGRESS_BAR_COLOR_ENABLED, action.value) }
            is CustomSettingsAction.SetDisableScrolling ->
                prefs.edit { putBoolean(AppPreferences.Keys.DISABLE_LYRIC_SCROLLING, action.value) }
            is CustomSettingsAction.ApplySuperIslandScrollForce ->
                applySuperIslandScrollForce(
                    force = action.force,
                    restoreLegacyState = action.restoreLegacyState,
                    currentDisableScrolling = action.currentDisableScrolling
                )
            is CustomSettingsAction.SetLyricTextDisplayMode ->
                LyricTextDisplayMode.write(prefs, action.value)
            is CustomSettingsAction.SetOneUiCapsuleColorMode ->
                OneUiCapsuleColorMode.write(prefs, action.value)
            is CustomSettingsAction.SetCapsuleRenderMode ->
                CapsuleRenderMode.write(prefs, action.value)
            is CustomSettingsAction.SetSuperIslandLyricMode ->
                prefs.edit { putString(AppPreferences.Keys.SUPER_ISLAND_LYRIC_MODE, action.value) }
            is CustomSettingsAction.SetSuperIslandFullLyricShowLeftCover ->
                prefs.edit { putBoolean(AppPreferences.Keys.SUPER_ISLAND_FULL_LYRIC_SHOW_LEFT_COVER, action.value) }
            is CustomSettingsAction.SetSuperIslandTextLimit ->
                prefs.edit { putFloat(action.key, action.value) }
            is CustomSettingsAction.SetSuperIslandTextColorEnabled ->
                prefs.edit { putBoolean(AppPreferences.Keys.SUPER_ISLAND_TEXT_COLOR_ENABLED, action.value) }
            is CustomSettingsAction.SetSuperIslandColorSource ->
                SuperIslandColorSource.write(prefs, action.value)
            is CustomSettingsAction.SetSuperIslandCustomColor ->
                SuperIslandColorSource.writeCustomColor(prefs, action.value)
            is CustomSettingsAction.SetSuperIslandShareEnabled ->
                prefs.edit { putBoolean(AppPreferences.Keys.SUPER_ISLAND_SHARE_ENABLED, action.value) }
            is CustomSettingsAction.SetSuperIslandShareFormat ->
                prefs.edit { putString(AppPreferences.Keys.SUPER_ISLAND_SHARE_FORMAT, action.value) }
            is CustomSettingsAction.SetSuperIslandNotificationStyle ->
                prefs.edit { putString(AppPreferences.Keys.SUPER_ISLAND_NOTIFICATION_STYLE, action.value) }
            is CustomSettingsAction.SetSuperIslandMediaButtonLayout ->
                prefs.edit { putString(AppPreferences.Keys.SUPER_ISLAND_MEDIA_BUTTON_LAYOUT, action.value) }
            is CustomSettingsAction.SetXmsfBypassMode ->
                XmsfBypassMode.write(prefs, action.value)
            is CustomSettingsAction.SetXmsfCustomDurationMs ->
                XmsfBypassMode.writeCustomDurationMs(prefs, action.value)
            is CustomSettingsAction.SetMiuixEnabled ->
                prefs.edit { putBoolean(AppPreferences.Keys.UI_USE_MIUIX, action.value) }
            is CustomSettingsAction.SetMiuixThemeColorSource ->
                prefs.edit { putString(AppPreferences.Keys.MIUIX_THEME_COLOR_SOURCE, action.value) }
            is CustomSettingsAction.SetMiuixThemeCustomColor ->
                prefs.edit { putInt(AppPreferences.Keys.THEME_CUSTOM_COLOR, action.value) }
            is CustomSettingsAction.SetMiuixThemeGlobalTintEnabled ->
                prefs.edit { putBoolean(AppPreferences.Keys.THEME_CUSTOM_COLOR_GLOBAL_TINT, action.value) }
            is CustomSettingsAction.SetCardBlurEnabled ->
                prefs.edit { putBoolean(AppPreferences.Keys.CARD_BLUR_ENABLED, action.value) }
            is CustomSettingsAction.SetPredictiveBackEnabled ->
                prefs.edit { putBoolean(PREF_PREDICTIVE_BACK_ENABLED, action.value) }
            is CustomSettingsAction.SetPredictiveBackAnimationMode ->
                PredictiveBackAnimationMode.write(prefs, action.value)
            is CustomSettingsAction.SetPredictiveBackAnimationStyle ->
                PredictiveBackAnimationStyle.write(prefs, action.value)
        }
        refresh()
    }

    fun refresh() {
        _uiState.value = readState()
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onCleared()
    }

    private fun readState(): CustomSettingsUiState {
        val app = getApplication<Application>()
        LabFeatureManager.ensureInitialized(prefs)
        return CustomSettingsUiState(
            floatingLyricsLabEnabled = LabFeatureManager.isFloatingLyricsEnabled(prefs),
            followSystem = ThemeHelper.getFollowSystem(app),
            darkMode = ThemeHelper.getDarkMode(app),
            pureBlack = ThemeHelper.getMaterialPureBlack(app),
            dynamicColor = ThemeHelper.getMaterialDynamicColor(app),
            materialThemeColorSource = ThemeHelper.getMaterialThemeColorSource(app),
            customThemeColor = ThemeHelper.getMaterialCustomColor(app),
            iconStyle = AppPreferences.dynamicIconStyle(prefs),
            actionStyle = AppPreferences.notificationActionsStyle(prefs),
            superIslandMediaButtonLayout = AppPreferences.superIslandMediaButtonLayout(prefs),
            superIslandNotificationStyle = LabFeatureManager.sanitizeSuperIslandNotificationStyle(app),
            superIslandAdvancedStyleLabEnabled = LabFeatureManager.isSuperIslandAdvancedStyleEnabled(prefs),
            superIslandTextLimitsLabEnabled = LabFeatureManager.isSuperIslandTextLimitsEnabled(prefs),
            superIslandRelaxedTextLimitsLabEnabled = LabFeatureManager.isSuperIslandRelaxedTextLimitsEnabled(prefs),
            notificationClickStyle = AppPreferences.notificationClickStyle(prefs),
            dismissDelayMs = prefs.getLong("notification_dismiss_delay", 0L),
            progressColorEnabled = AppPreferences.isProgressBarColorEnabled(prefs),
            disableScrolling = AppPreferences.isLyricScrollingDisabled(prefs),
            lyricTextDisplayMode = LyricTextDisplayMode.read(prefs),
            oneuiCapsuleColorMode = OneUiCapsuleColorMode.read(prefs),
            capsuleRenderMode = CapsuleRenderMode.read(prefs),
            superIslandLyricMode = AppPreferences.superIslandLyricMode(prefs),
            superIslandFullLyricShowLeftCover = AppPreferences.isSuperIslandFullLyricLeftCoverEnabled(prefs),
            superIslandTextColorEnabled = AppPreferences.isSuperIslandTextColorEnabled(prefs),
            superIslandColorSource = SuperIslandColorSource.read(prefs),
            superIslandCustomColor = SuperIslandColorSource.readCustomColor(prefs),
            superIslandShareEnabled = AppPreferences.isSuperIslandShareEnabled(prefs),
            superIslandShareFormat = AppPreferences.superIslandShareFormat(prefs),
            miuixEnabled = prefs.getBoolean(AppPreferences.Keys.UI_USE_MIUIX, true),
            predictiveBackEnabled = prefs.getBoolean(PREF_PREDICTIVE_BACK_ENABLED, true),
            predictiveBackAnimationMode = PredictiveBackAnimationMode.read(prefs),
            predictiveBackAnimationStyle = PredictiveBackAnimationStyle.read(prefs),
            monetEnabled = prefs.getBoolean(AppPreferences.Keys.THEME_DYNAMIC_COLOR, true),
            customThemeGlobalTintEnabled = prefs.getBoolean(AppPreferences.Keys.THEME_CUSTOM_COLOR_GLOBAL_TINT, false),
            cardBlurEnabled = prefs.getBoolean(AppPreferences.Keys.CARD_BLUR_ENABLED, false)
        )
    }

    private fun applySuperIslandScrollForce(
        force: Boolean,
        restoreLegacyState: Boolean,
        currentDisableScrolling: Boolean
    ) {
        val forcedKey = "super_island_lyric_mode_forced_disable_scrolling"
        val backupKey = "disable_lyric_scrolling_before_super_island_lyric_mode"
        val legacyForcedKey = "full_super_island_forced_disable_scrolling"
        val legacyBackupKey = "disable_lyric_scrolling_before_full_super_island"
        val wasForced = prefs.getBoolean(forcedKey, false) || prefs.getBoolean(legacyForcedKey, false)
        prefs.edit {
            if (force) {
                if (!wasForced) {
                    putBoolean(backupKey, currentDisableScrolling)
                    putBoolean(forcedKey, true)
                }
                putBoolean(AppPreferences.Keys.DISABLE_LYRIC_SCROLLING, true)
            } else if (wasForced) {
                val restoredValue = if (prefs.contains(backupKey)) {
                    prefs.getBoolean(backupKey, false)
                } else {
                    prefs.getBoolean(legacyBackupKey, false)
                }
                putBoolean(AppPreferences.Keys.DISABLE_LYRIC_SCROLLING, restoredValue)
                remove(backupKey)
                remove(forcedKey)
                remove(legacyBackupKey)
                remove(legacyForcedKey)
            } else if (restoreLegacyState && currentDisableScrolling) {
                putBoolean(AppPreferences.Keys.DISABLE_LYRIC_SCROLLING, false)
            }
        }
    }
}
