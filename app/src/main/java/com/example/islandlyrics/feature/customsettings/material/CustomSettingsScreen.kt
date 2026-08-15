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

package com.example.islandlyrics.feature.customsettings.material


import com.example.islandlyrics.ui.material.blur.MaterialBlurDropdownMenu

import com.example.islandlyrics.ui.material.blur.MaterialBlurAlertDialog

import com.example.islandlyrics.ui.material.blur.MaterialBlurScaffold
import com.example.islandlyrics.ui.theme.material.MaterialBlurTopAppBar
import android.app.Activity
import com.example.islandlyrics.ui.preview.NotificationPreview
import com.example.islandlyrics.ui.preview.CapsulePreview
import com.example.islandlyrics.ui.overlay.config.CapsuleRenderMode
import com.example.islandlyrics.ui.overlay.config.LyricTextDisplayMode
import com.example.islandlyrics.ui.overlay.config.OneUiCapsuleColorMode
import com.example.islandlyrics.ui.overlay.capsule.config.LiveUpdateTextLimitConfig
import com.example.islandlyrics.ui.navigation.PredictiveBackAnimationMode
import com.example.islandlyrics.ui.navigation.PredictiveBackAnimationStyle
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandColorSource
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandDualLineMode
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandSecondaryTextMode
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandTemplate2PicSource
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandTextLimitConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.platform.XmsfBypassMode
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.runtime.service.LyricService
import com.example.islandlyrics.ui.theme.material.AppTheme
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.feature.settings.material.LanguageSelectionDialog
import com.example.islandlyrics.feature.settings.material.SettingsSwitchItem
import com.example.islandlyrics.feature.settings.material.SettingsTextItem
import com.example.islandlyrics.feature.settings.material.SettingsCard
import com.example.islandlyrics.feature.settings.material.SettingsCardDivider
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.ui.overlay.model.SecondaryTextMode
import com.example.islandlyrics.feature.customsettings.CustomSettingsAction
import com.example.islandlyrics.feature.customsettings.CustomSettingsTab
import com.example.islandlyrics.feature.customsettings.CustomSettingsViewModel
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.provider.Settings
import android.util.TypedValue
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun CustomSettingsScreen(
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit = {},
    onShowLogs: () -> Unit = {},
    updateVersionText: String = "",
    updateBuildText: String = "",
    title: String = stringResource(R.string.page_title_personalization),
    tabs: Set<CustomSettingsTab> = CustomSettingsTab.entries.toSet(),
    viewModel: CustomSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val orderedTabs = buildList {
        if (CustomSettingsTab.CAPSULE in tabs) add(CustomSettingsTab.CAPSULE)
        if (CustomSettingsTab.NOTIFICATION in tabs) add(CustomSettingsTab.NOTIFICATION)
        if (CustomSettingsTab.APP_UI in tabs) add(CustomSettingsTab.APP_UI)
        if (CustomSettingsTab.DESKTOP_LYRICS in tabs) {
            add(CustomSettingsTab.DESKTOP_LYRICS)
        }
    }
    val tabLabels = orderedTabs.map { tab ->
        when (tab) {
            CustomSettingsTab.CAPSULE -> stringResource(R.string.tab_capsule)
            CustomSettingsTab.NOTIFICATION -> stringResource(R.string.tab_notification)
            CustomSettingsTab.APP_UI -> stringResource(R.string.tab_app_ui)
            CustomSettingsTab.DESKTOP_LYRICS -> stringResource(R.string.settings_floating_lyrics)
        }
    }
    val pagerState = rememberPagerState(pageCount = { orderedTabs.size })

    // --- State Duplication ---

    // Theme State
    var followSystem by remember(uiState.followSystem) { mutableStateOf(uiState.followSystem) }
    var darkMode by remember(uiState.darkMode) { mutableStateOf(uiState.darkMode) }
    var pureBlack by remember(uiState.pureBlack) { mutableStateOf(uiState.pureBlack) }
    var dynamicColor by remember(uiState.dynamicColor) { mutableStateOf(uiState.dynamicColor) }
    var materialThemeColorSource by remember(uiState.materialThemeColorSource) {
        mutableStateOf(uiState.materialThemeColorSource)
    }
    var customThemeColor by remember(uiState.customThemeColor) { mutableStateOf(Color(uiState.customThemeColor)) }
    var materialThemeColorEditing by remember { mutableStateOf(false) }
    var materialThemeColorSnapshot by remember { mutableStateOf(customThemeColor) }
    var iconStyle by remember(uiState.iconStyle) { mutableStateOf(uiState.iconStyle) }
    
    // Dialog State
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showIconStyleDropdown by remember { mutableStateOf(false) }
    var showOneUiCapsuleColorDropdown by remember { mutableStateOf(false) }
    var showCapsuleModeDropdown by remember { mutableStateOf(false) }
    var showLyricTextDisplayModeDropdown by remember { mutableStateOf(false) }
    var showSuperIslandLyricModeDropdown by remember { mutableStateOf(false) }
    var showSuperIslandColorSourceDropdown by remember { mutableStateOf(false) }
    var showThemeColorSourceDropdown by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // Notification Action Style State
    var actionStyle by remember(uiState.actionStyle) { mutableStateOf(uiState.actionStyle) }
    var superIslandMediaButtonLayout by remember(uiState.superIslandMediaButtonLayout) {
        mutableStateOf(uiState.superIslandMediaButtonLayout)
    }
    var superIslandNotificationStyle by remember(uiState.superIslandNotificationStyle) {
        mutableStateOf(uiState.superIslandNotificationStyle)
    }
    var superIslandDualLineMode by remember(uiState.superIslandDualLineMode) {
        mutableStateOf(uiState.superIslandDualLineMode)
    }
    var superIslandShowProgressBar by remember(uiState.superIslandShowProgressBar) {
        mutableStateOf(uiState.superIslandShowProgressBar)
    }
    var secondaryTextModes by remember(uiState.superIslandSecondaryTextModes) {
        mutableStateOf(uiState.superIslandSecondaryTextModes)
    }
    var template2PicSource by remember(uiState.superIslandTemplate2PicSource) {
        mutableStateOf(uiState.superIslandTemplate2PicSource)
    }
    var template2CustomPicUri by remember(uiState.superIslandTemplate2CustomPicUri) {
        mutableStateOf(uiState.superIslandTemplate2CustomPicUri)
    }
    var showSecondaryTextModeDialog by remember { mutableStateOf(false) }
    var showTemplate2PicSourceDropdown by remember { mutableStateOf(false) }
    var superIslandAdvancedStyleLabEnabled by remember(uiState.superIslandAdvancedStyleLabEnabled) {
        mutableStateOf(uiState.superIslandAdvancedStyleLabEnabled)
    }
    var superIslandTextLimitsLabEnabled by remember(uiState.superIslandTextLimitsLabEnabled) {
        mutableStateOf(uiState.superIslandTextLimitsLabEnabled)
    }
    var superIslandRelaxedTextLimitsLabEnabled by remember(uiState.superIslandRelaxedTextLimitsLabEnabled) {
        mutableStateOf(uiState.superIslandRelaxedTextLimitsLabEnabled)
    }
    var liveUpdateTextLimitsLabEnabled by remember(uiState.liveUpdateTextLimitsLabEnabled) {
        mutableStateOf(uiState.liveUpdateTextLimitsLabEnabled)
    }
    var showActionStyleDropdown by remember { mutableStateOf(false) }
    var showSuperIslandMediaButtonLayoutDropdown by remember { mutableStateOf(false) }
    var showSuperIslandNotificationStyleDropdown by remember { mutableStateOf(false) }
    var showSuperIslandDualLineModeDropdown by remember { mutableStateOf(false) }

    // Share Format Dropdown State
    var showShareFormatDropdown by remember { mutableStateOf(false) }

    // Notification Click Action State
    var notificationClickStyle by remember(uiState.notificationClickStyle) {
        mutableStateOf(uiState.notificationClickStyle)
    }
    var showNotificationClickDropdown by remember { mutableStateOf(false) }

    // Dismiss Delay State
    var dismissDelay by remember(uiState.dismissDelayMs) { mutableLongStateOf(uiState.dismissDelayMs) }
    var showDismissDelayDropdown by remember { mutableStateOf(false) }

    // Other Setup
    var progressColorEnabled by remember(uiState.progressColorEnabled) { mutableStateOf(uiState.progressColorEnabled) }
    var disableScrolling by remember(uiState.disableScrolling) { mutableStateOf(uiState.disableScrolling) }
    var lyricTextDisplayMode by remember(uiState.lyricTextDisplayMode) { mutableStateOf(uiState.lyricTextDisplayMode) }
    var oneuiCapsuleColorMode by remember(uiState.oneuiCapsuleColorMode) { mutableStateOf(uiState.oneuiCapsuleColorMode) }

    var capsuleRenderMode by remember(uiState.capsuleRenderMode) { mutableStateOf(uiState.capsuleRenderMode) }
    var superIslandLyricMode by remember(uiState.superIslandLyricMode) { mutableStateOf(uiState.superIslandLyricMode) }
    var superIslandFullLyricShowLeftCover by remember(uiState.superIslandFullLyricShowLeftCover) {
        mutableStateOf(uiState.superIslandFullLyricShowLeftCover)
    }
    var superIslandTextColorEnabled by remember(uiState.superIslandTextColorEnabled) {
        mutableStateOf(uiState.superIslandTextColorEnabled)
    }
    var superIslandColorSource by remember(uiState.superIslandColorSource) {
        mutableStateOf(uiState.superIslandColorSource)
    }
    var superIslandCustomColor by remember(uiState.superIslandCustomColor) {
        mutableStateOf(Color(uiState.superIslandCustomColor))
    }
    var superIslandColorEditing by remember { mutableStateOf(false) }
    var superIslandColorSnapshot by remember { mutableStateOf(superIslandCustomColor) }
    var superIslandRightTextChars by remember {
        mutableFloatStateOf(SuperIslandTextLimitConfig.rightChars(prefs, superIslandRelaxedTextLimitsLabEnabled))
    }
    var superIslandLeftWithCoverTextChars by remember {
        mutableFloatStateOf(SuperIslandTextLimitConfig.leftChars(prefs, showLeftCover = true, superIslandRelaxedTextLimitsLabEnabled))
    }
    var superIslandLeftNoCoverTextChars by remember {
        mutableFloatStateOf(SuperIslandTextLimitConfig.leftChars(prefs, showLeftCover = false, superIslandRelaxedTextLimitsLabEnabled))
    }
    var liveUpdateTextChars by remember(uiState.liveUpdateTextChars) {
        mutableFloatStateOf(uiState.liveUpdateTextChars)
    }

    var superIslandShareEnabled by remember(uiState.superIslandShareEnabled) {
        mutableStateOf(uiState.superIslandShareEnabled)
    }
    var superIslandShareFormat by remember(uiState.superIslandShareFormat) {
        mutableStateOf(uiState.superIslandShareFormat)
    }
    var miuixEnabled by remember(uiState.miuixEnabled) { mutableStateOf(uiState.miuixEnabled) }
    var predictiveBackEnabled by remember(uiState.predictiveBackEnabled) {
        mutableStateOf(uiState.predictiveBackEnabled)
    }
    var predictiveBackAnimationMode by remember(uiState.predictiveBackAnimationMode) {
        mutableStateOf(uiState.predictiveBackAnimationMode)
    }
    var predictiveBackAnimationStyle by remember(uiState.predictiveBackAnimationStyle) {
        mutableStateOf(uiState.predictiveBackAnimationStyle)
    }
    var homeLyricPreviewDisplayModes by remember(uiState.homeLyricPreviewDisplayModes) {
        mutableStateOf(uiState.homeLyricPreviewDisplayModes)
    }


    // Dialog State for UI Style
    var showUiStyleDropdown by remember { mutableStateOf(false) }
    var showHomeLyricPreviewDialog by remember { mutableStateOf(false) }
    var showPredictiveBackAnimationModeDropdown by remember { mutableStateOf(false) }
    var showPredictiveBackAnimationDropdown by remember { mutableStateOf(false) }

    val template2PicPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // 无法持久化授权时，本次会话内仍可使用
        }
        template2CustomPicUri = uri.toString()
        viewModel.dispatch(CustomSettingsAction.SetSuperIslandTemplate2CustomPicUri(uri.toString()))
    }

    // Check for HyperOS 3.0.300+
    val isLiveUpdateSupported = remember { RomUtils.isLiveUpdateSupported() }
    val isHyperOs = remember { RomUtils.isHyperOs() }
    val effectiveCapsuleRenderMode = if (!isLiveUpdateSupported && capsuleRenderMode == CapsuleRenderMode.LIVE_UPDATE) {
        CapsuleRenderMode.XIAOMI_SUPER_ISLAND
    } else {
        capsuleRenderMode
    }
    val superIslandEnabled = effectiveCapsuleRenderMode == CapsuleRenderMode.XIAOMI_SUPER_ISLAND
    val islandStyleCapsuleEnabled = superIslandEnabled
    /**
     * 新通知逻辑：播放按键布局 + 显示进度条 推导生效的通知按键样式。
     */
    val effectiveActionStyle = if (superIslandEnabled) {
        when {
            superIslandMediaButtonLayout == "no_button" &&
                !superIslandShowProgressBar &&
                superIslandNotificationStyle == LabFeatureManager.SUPER_ISLAND_STYLE_STANDARD -> "template2"
            superIslandMediaButtonLayout == "no_button" -> "disabled"
            else -> "media_controls"
        }
    } else {
        actionStyle
    }
    val forceDisableScrollingForSuperIslandLyricMode =
        islandStyleCapsuleEnabled && superIslandLyricMode == "full"

    fun applySuperIslandScrollForce(force: Boolean, restoreLegacyState: Boolean = false) {
        val currentDisableScrolling = disableScrolling
        val forcedKey = "super_island_lyric_mode_forced_disable_scrolling"
        val legacyForcedKey = "full_super_island_forced_disable_scrolling"
        val wasForced = prefs.getBoolean(forcedKey, false) || prefs.getBoolean(legacyForcedKey, false)
        disableScrolling = when {
            force -> true
            wasForced -> {
                val backupKey = "disable_lyric_scrolling_before_super_island_lyric_mode"
                val legacyBackupKey = "disable_lyric_scrolling_before_full_super_island"
                if (prefs.contains(backupKey)) {
                    prefs.getBoolean(backupKey, false)
                } else {
                    prefs.getBoolean(legacyBackupKey, false)
                }
            }
            restoreLegacyState && currentDisableScrolling -> false
            else -> currentDisableScrolling
        }
        viewModel.dispatch(
            CustomSettingsAction.ApplySuperIslandScrollForce(
                force = force,
                restoreLegacyState = restoreLegacyState,
                currentDisableScrolling = currentDisableScrolling
            )
        )
    }

    fun setCapsuleRenderMode(mode: CapsuleRenderMode) {
        if (capsuleRenderMode == mode) return

        capsuleRenderMode = mode
        viewModel.dispatch(CustomSettingsAction.SetCapsuleRenderMode(mode))

        if (mode != CapsuleRenderMode.LIVE_UPDATE && actionStyle == "miplay") {
            actionStyle = "disabled"
            viewModel.dispatch(CustomSettingsAction.SetNotificationActionsStyle("disabled"))
        }

        val action = "ACTION_SET_CAPSULE_RENDER_MODE"
        val intent = Intent(context, LyricService::class.java).setAction(action)
        context.startService(intent)
    }

    LaunchedEffect(Unit) {
        LabFeatureManager.ensureInitialized(prefs)
        superIslandAdvancedStyleLabEnabled = LabFeatureManager.isSuperIslandAdvancedStyleEnabled(prefs)
        superIslandTextLimitsLabEnabled = LabFeatureManager.isSuperIslandTextLimitsEnabled(prefs)
        superIslandRelaxedTextLimitsLabEnabled = LabFeatureManager.isSuperIslandRelaxedTextLimitsEnabled(prefs)
        liveUpdateTextLimitsLabEnabled = LabFeatureManager.isLiveUpdateTextLimitsEnabled(prefs)
        liveUpdateTextChars = LiveUpdateTextLimitConfig.chars(prefs)
        superIslandNotificationStyle = LabFeatureManager.sanitizeSuperIslandNotificationStyle(context)
    }
    LaunchedEffect(forceDisableScrollingForSuperIslandLyricMode) {
        applySuperIslandScrollForce(forceDisableScrollingForSuperIslandLyricMode)
    }

    // Force disable unsupported features
    LaunchedEffect(isLiveUpdateSupported) {
        if (!isLiveUpdateSupported) {
            if (iconStyle != "disabled") {
                iconStyle = "disabled"
                viewModel.dispatch(CustomSettingsAction.SetDynamicIconStyle("disabled"))
            }
            if (actionStyle == "miplay") {
                actionStyle = "disabled"
                viewModel.dispatch(CustomSettingsAction.SetNotificationActionsStyle("disabled"))
            }
        } else if (!isHyperOs && iconStyle == "advanced") {
            // Advanced style is HyperOS-only; reset to classic on other ROMs
            iconStyle = "disabled"
            viewModel.dispatch(CustomSettingsAction.SetDynamicIconStyle("disabled"))
        }
    }

    // Determine actual dark mode for AppTheme
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDarkTheme = if (followSystem) isSystemDark else darkMode
    
     // Pure Black Background Logic
    LaunchedEffect(pureBlack, useDarkTheme) {
        val activity = context as? Activity
        if (activity != null) {
             if (pureBlack && useDarkTheme) {
                 activity.window.decorView.setBackgroundColor(AndroidColor.BLACK)
                 activity.window.setBackgroundDrawableResource(android.R.color.black)
             } else {
                 val typedValue = TypedValue()
                 activity.theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)
                 if (typedValue.resourceId != 0) {
                     activity.window.setBackgroundDrawableResource(typedValue.resourceId)
                 } else {
                     activity.window.decorView.setBackgroundColor(typedValue.data)
                 }
            }
        }
    }

    AppTheme(
        darkTheme = useDarkTheme,
        dynamicColor = dynamicColor,
        pureBlack = pureBlack && useDarkTheme,
        customThemeColorArgb = customThemeColor.toArgb(),
        customThemeGlobalTintEnabled = materialThemeColorSource == ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_CUSTOM
    ) {
        MaterialBlurScaffold(
            topBar = {
                Column {
                    MaterialBlurTopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                            }
                        },
                    )
                    // Tab switcher matching LogViewer FilterChip corner radius
                    if (tabLabels.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tabLabels.forEachIndexed { index, tabTitle ->
                                val selected = pagerState.currentPage == index
                                if (selected) {
                                    Button(
                                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.small,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(tabTitle, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.small,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(tabTitle, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            containerColor = materialPageContainerColor()
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(paddingValues).fillMaxSize()
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (orderedTabs[page]) {
                        CustomSettingsTab.CAPSULE -> { // Capsule (Moved from 1)
                            val previewIconStyle = if (superIslandEnabled) "advanced" else iconStyle
                            CapsulePreview(
                                dynamicIconEnabled = if (superIslandEnabled) true else iconStyle != "disabled",
                                iconStyle = previewIconStyle,
                                oneuiCapsuleColorMode = oneuiCapsuleColorMode,
                                superIslandEnabled = superIslandEnabled,
                                superIslandLyricMode = superIslandLyricMode,
                                superIslandFullLyricShowLeftCover = superIslandFullLyricShowLeftCover,
                                superIslandColorSource = superIslandColorSource,
                                superIslandCustomColor = superIslandCustomColor,
                                superIslandSmartMinContrast = uiState.superIslandSmartMinContrast,
                                superIslandSmartWhiteRatio = uiState.superIslandSmartWhiteRatio
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsCard {
                            SettingsSwitchItem(
                                title = stringResource(R.string.settings_disable_scrolling),
                                subtitle = stringResource(R.string.settings_disable_scrolling_desc),
                                checked = disableScrolling || forceDisableScrollingForSuperIslandLyricMode,
                                enabled = !forceDisableScrollingForSuperIslandLyricMode,
                                onCheckedChange = {
                                    disableScrolling = it
                                    viewModel.dispatch(CustomSettingsAction.SetDisableScrolling(it))
                                }
                            )

                            val lyricTextModes = LyricTextDisplayMode.values
                            val lyricTextModeLabels = listOf(
                                stringResource(R.string.lyric_text_display_mode_lyric),
                                stringResource(R.string.lyric_text_display_mode_translation),
                                stringResource(R.string.lyric_text_display_mode_romanization)
                            )
                            val currentLyricTextModeIndex = lyricTextModes.indexOf(lyricTextDisplayMode).takeIf { it >= 0 } ?: 0

                            SettingsCardDivider()
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    SettingsTextItem(
                                        title = stringResource(R.string.settings_lyric_text_display_mode),
                                        subtitle = stringResource(R.string.settings_lyric_text_display_mode_desc),
                                        value = lyricTextModeLabels[currentLyricTextModeIndex],
                                        onClick = { showLyricTextDisplayModeDropdown = true }
                                    )
                                    Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                        MaterialBlurDropdownMenu(
                                            expanded = showLyricTextDisplayModeDropdown,
                                            onDismissRequest = { showLyricTextDisplayModeDropdown = false }
                                        ) {
                                            lyricTextModeLabels.forEachIndexed { index, label ->
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        lyricTextDisplayMode = lyricTextModes[index]
                                                        viewModel.dispatch(CustomSettingsAction.SetLyricTextDisplayMode(lyricTextDisplayMode))
                                                        showLyricTextDisplayModeDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            } // end SettingsCard (basic capsule settings)

                            if (RomUtils.getRomType() == "OneUI") {
                                val oneUiColorModes = OneUiCapsuleColorMode.values
                                val oneUiColorModeLabels = listOf(
                                    stringResource(R.string.oneui_capsule_color_mode_black),
                                    stringResource(R.string.oneui_capsule_color_mode_transparent),
                                    stringResource(R.string.oneui_capsule_color_mode_translucent_black),
                                    stringResource(R.string.oneui_capsule_color_mode_album)
                                )
                                val oneUiColorModeDisplay = oneUiColorModeLabels[
                                    oneUiColorModes.indexOf(oneuiCapsuleColorMode).takeIf { it >= 0 } ?: 0
                                ]
                                Spacer(modifier = Modifier.height(8.dp))
                                SettingsCard {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    SettingsTextItem(
                                        title = stringResource(R.string.settings_oneui_capsule_color),
                                        value = oneUiColorModeDisplay,
                                        onClick = { showOneUiCapsuleColorDropdown = true }
                                    )
                                    Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                        MaterialBlurDropdownMenu(
                                            expanded = showOneUiCapsuleColorDropdown,
                                            onDismissRequest = { showOneUiCapsuleColorDropdown = false }
                                        ) {
                                            oneUiColorModes.zip(oneUiColorModeLabels).forEach { (mode, label) ->
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        oneuiCapsuleColorMode = mode
                                                        viewModel.dispatch(CustomSettingsAction.SetOneUiCapsuleColorMode(mode))
                                                        showOneUiCapsuleColorDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                } // end SettingsCard (OneUI)
                            }

                            if (isHyperOs) {
                                Spacer(modifier = Modifier.height(8.dp))
                                SettingsCard {
                                if (isLiveUpdateSupported) {
                                    val capsuleModeItems = buildList {
                                        add(CapsuleRenderMode.LIVE_UPDATE to stringResource(R.string.capsule_mode_live_update))
                                        add(CapsuleRenderMode.XIAOMI_SUPER_ISLAND to stringResource(R.string.capsule_mode_super_island))
                                    }
                                    val capsuleModes = capsuleModeItems.map { it.first }
                                    val capsuleModeLabels = capsuleModeItems.map { it.second }
                                    val currentCapsuleModeIndex =
                                        capsuleModes.indexOf(effectiveCapsuleRenderMode).takeIf { it >= 0 } ?: 0

                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_capsule_mode),
                                            value = capsuleModeLabels[currentCapsuleModeIndex],
                                            onClick = { showCapsuleModeDropdown = true }
                                        )
                                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                            MaterialBlurDropdownMenu(
                                                expanded = showCapsuleModeDropdown,
                                                onDismissRequest = { showCapsuleModeDropdown = false }
                                            ) {
                                                capsuleModeLabels.forEachIndexed { index, label ->
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            setCapsuleRenderMode(capsuleModes[index])
                                                            showCapsuleModeDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (isLiveUpdateSupported && !superIslandEnabled) {
                                    val styleDisplayName = when (iconStyle) {
                                        "advanced" -> stringResource(R.string.icon_style_advanced)
                                        "album_art" -> stringResource(R.string.icon_style_album_art)
                                        else -> stringResource(R.string.icon_style_classic)
                                    }
                                    SettingsCardDivider()
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_icon_style),
                                            value = styleDisplayName,
                                            onClick = { showIconStyleDropdown = true }
                                        )
                                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                            MaterialBlurDropdownMenu(
                                                expanded = showIconStyleDropdown,
                                                onDismissRequest = { showIconStyleDropdown = false }
                                            ) {
                                                val styles = buildList {
                                                    add("disabled" to R.string.icon_style_classic)
                                                    add("advanced" to R.string.icon_style_advanced)
                                                    add("album_art" to R.string.icon_style_album_art)
                                                }
                                                styles.forEach { (styleId, nameId) ->
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(nameId)) },
                                                        onClick = {
                                                            iconStyle = styleId
                                                            viewModel.dispatch(CustomSettingsAction.SetDynamicIconStyle(styleId))
                                                            showIconStyleDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (isLiveUpdateSupported &&
                                    effectiveCapsuleRenderMode == CapsuleRenderMode.LIVE_UPDATE &&
                                    liveUpdateTextLimitsLabEnabled
                                ) {
                                    SettingsCardDivider()
                                    MaterialSuperIslandTextLimitSlider(
                                        title = stringResource(R.string.settings_live_update_text_limit),
                                        value = liveUpdateTextChars.coerceIn(
                                            LiveUpdateTextLimitConfig.MIN_CHARS..
                                                LiveUpdateTextLimitConfig.MAX_CHARS
                                        ),
                                        valueRange = LiveUpdateTextLimitConfig.MIN_CHARS..
                                            LiveUpdateTextLimitConfig.MAX_CHARS,
                                        onValueChange = { value ->
                                            liveUpdateTextChars = value
                                            viewModel.dispatch(CustomSettingsAction.SetLiveUpdateTextLimit(value))
                                        }
                                    )
                                }

                                if (islandStyleCapsuleEnabled) {
                                    val lyricModeItems = buildList {
                                        add(Triple("standard", R.string.super_island_lyric_mode_standard, R.string.super_island_lyric_mode_standard_desc))
                                        add(Triple("full", R.string.super_island_lyric_mode_full, R.string.super_island_lyric_mode_full_desc))
                                    }
                                    val currentLyricModeItem = lyricModeItems.firstOrNull { it.first == superIslandLyricMode }
                                        ?: lyricModeItems.first()
                                    if (currentLyricModeItem.first != superIslandLyricMode) {
                                        superIslandLyricMode = currentLyricModeItem.first
                                        viewModel.dispatch(CustomSettingsAction.SetSuperIslandLyricMode(currentLyricModeItem.first))
                                    }
                                    val lyricModeDisplay = stringResource(currentLyricModeItem.second)
                                    val lyricModeSubtitle = stringResource(currentLyricModeItem.third)

                                    fun setSuperIslandLyricMode(newMode: String) {
                                        superIslandLyricMode = newMode
                                        viewModel.dispatch(CustomSettingsAction.SetSuperIslandLyricMode(newMode))
                                        if (newMode == "standard") {
                                            applySuperIslandScrollForce(force = false, restoreLegacyState = true)
                                        }
                                    }

                                    SettingsCardDivider()
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_super_island_lyric_mode),
                                            subtitle = lyricModeSubtitle,
                                            value = lyricModeDisplay,
                                            onClick = { showSuperIslandLyricModeDropdown = true }
                                        )
                                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                            MaterialBlurDropdownMenu(
                                                expanded = showSuperIslandLyricModeDropdown,
                                                onDismissRequest = { showSuperIslandLyricModeDropdown = false }
                                            ) {
                                                lyricModeItems.forEach { (modeId, nameId, descId) ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Column {
                                                                Text(stringResource(nameId))
                                                                Text(
                                                                    text = stringResource(descId),
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            setSuperIslandLyricMode(modeId)
                                                            showSuperIslandLyricModeDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (superIslandLyricMode == "full") {
                                        SettingsCardDivider()
                                        SettingsSwitchItem(
                                            title = stringResource(R.string.settings_super_island_full_lyric_show_left_cover),
                                            subtitle = stringResource(R.string.settings_super_island_full_lyric_show_left_cover_desc),
                                            checked = superIslandFullLyricShowLeftCover,
                                            onCheckedChange = {
                                                superIslandFullLyricShowLeftCover = it
                                                viewModel.dispatch(CustomSettingsAction.SetSuperIslandFullLyricShowLeftCover(it))
                                            }
                                        )
                                    }

                                    if (superIslandTextLimitsLabEnabled) {
                                        val rightRange = SuperIslandTextLimitConfig.RIGHT_MIN_CHARS..
                                            SuperIslandTextLimitConfig.rightMaxChars(superIslandRelaxedTextLimitsLabEnabled)
                                        MaterialSuperIslandTextLimitSlider(
                                            title = stringResource(R.string.settings_super_island_right_text_limit),
                                            value = superIslandRightTextChars.coerceIn(rightRange),
                                            valueRange = rightRange,
                                            onValueChange = { value ->
                                                superIslandRightTextChars = value
                                                viewModel.dispatch(CustomSettingsAction.SetSuperIslandTextLimit(SuperIslandTextLimitConfig.KEY_RIGHT_CHARS, value))
                                            }
                                        )

                                        if (superIslandLyricMode == "full") {
                                            val leftValue = when {
                                                superIslandFullLyricShowLeftCover -> superIslandLeftWithCoverTextChars
                                                else -> superIslandLeftNoCoverTextChars
                                            }
                                            val leftRange = when {
                                                superIslandFullLyricShowLeftCover -> SuperIslandTextLimitConfig.LEFT_WITH_COVER_MIN_CHARS..
                                                    SuperIslandTextLimitConfig.leftWithCoverMaxChars(superIslandRelaxedTextLimitsLabEnabled)
                                                else -> SuperIslandTextLimitConfig.LEFT_NO_COVER_MIN_CHARS..
                                                    SuperIslandTextLimitConfig.leftNoCoverMaxChars(superIslandRelaxedTextLimitsLabEnabled)
                                            }
                                            val leftKey = when {
                                                superIslandFullLyricShowLeftCover -> SuperIslandTextLimitConfig.KEY_LEFT_WITH_COVER_CHARS
                                                else -> SuperIslandTextLimitConfig.KEY_LEFT_NO_COVER_CHARS
                                            }
                                            MaterialSuperIslandTextLimitSlider(
                                                title = stringResource(R.string.settings_super_island_left_text_limit),
                                                value = leftValue.coerceIn(leftRange),
                                                valueRange = leftRange,
                                                onValueChange = { value ->
                                                    if (superIslandFullLyricShowLeftCover) {
                                                        superIslandLeftWithCoverTextChars = value
                                                    } else {
                                                        superIslandLeftNoCoverTextChars = value
                                                    }
                                                    viewModel.dispatch(CustomSettingsAction.SetSuperIslandTextLimit(leftKey, value))
                                                }
                                            )
                                        }
                                    }

                                    SettingsCardDivider()
                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_super_island_colorize),
                                        subtitle = stringResource(R.string.settings_super_island_colorize_desc),
                                        checked = superIslandTextColorEnabled,
                                        onCheckedChange = { enabled ->
                                            if (!enabled && superIslandColorEditing) {
                                                superIslandCustomColor = superIslandColorSnapshot
                                                superIslandColorEditing = false
                                            }
                                            val newSource = if (enabled && superIslandColorSource == SuperIslandColorSource.OFF) {
                                                SuperIslandColorSource.ALBUM_ART_SMART
                                            } else {
                                                superIslandColorSource
                                            }
                                            superIslandColorSource = newSource
                                            superIslandTextColorEnabled = enabled
                                            viewModel.dispatch(CustomSettingsAction.SetSuperIslandColorSource(newSource))
                                        }
                                    )

                                    if (superIslandTextColorEnabled) {
                                        val colorSourceOptions = listOf(
                                            Triple(
                                                SuperIslandColorSource.ALBUM_ART,
                                                R.string.settings_super_island_color_source_album_art,
                                                R.string.settings_super_island_color_source_album_art_desc
                                            ),
                                            Triple(
                                                SuperIslandColorSource.ALBUM_ART_SMART,
                                                R.string.settings_super_island_color_source_album_art_smart,
                                                R.string.settings_super_island_color_source_album_art_smart_desc
                                            ),
                                            Triple(
                                                SuperIslandColorSource.CUSTOM,
                                                R.string.settings_super_island_color_source_custom,
                                                R.string.settings_super_island_color_source_custom_desc
                                            )
                                        )
                                        val colorSources = colorSourceOptions.map { it.first }
                                        val colorSourceLabels = colorSourceOptions.map { stringResource(it.second) }
                                        val currentColorSourceIndex =
                                            colorSources.indexOf(superIslandColorSource).takeIf { it >= 0 } ?: 0

                                        SettingsCardDivider()
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            SettingsTextItem(
                                                title = stringResource(R.string.settings_super_island_color_source),
                                                subtitle = stringResource(R.string.settings_super_island_color_source_desc),
                                                value = colorSourceLabels[currentColorSourceIndex],
                                                onClick = { showSuperIslandColorSourceDropdown = true }
                                            )
                                            Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                                MaterialBlurDropdownMenu(
                                                    expanded = showSuperIslandColorSourceDropdown,
                                                    onDismissRequest = { showSuperIslandColorSourceDropdown = false }
                                                ) {
                                                    colorSourceOptions.forEach { (mode, nameId, descId) ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Column {
                                                                    Text(stringResource(nameId))
                                                                    Text(
                                                                        text = stringResource(descId),
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            },
                                                            onClick = {
                                                                if (superIslandColorEditing) {
                                                                    superIslandCustomColor = superIslandColorSnapshot
                                                                    superIslandColorEditing = false
                                                                }
                                                                superIslandColorSource = mode
                                                                viewModel.dispatch(CustomSettingsAction.SetSuperIslandColorSource(mode))
                                                                showSuperIslandColorSourceDropdown = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (superIslandColorSource == SuperIslandColorSource.CUSTOM) {
                                        SettingsCardDivider()
                                        MaterialEditableColorSection(
                                            title = stringResource(R.string.settings_super_island_custom_color),
                                            color = superIslandCustomColor,
                                            isEditing = superIslandColorEditing,
                                            defaultActionText = stringResource(R.string.settings_color_default),
                                            onStartEditing = {
                                                superIslandColorSnapshot = superIslandCustomColor
                                                superIslandColorEditing = true
                                            },
                                            onColorChanged = { color ->
                                                superIslandCustomColor = color
                                            },
                                            onApply = {
                                                viewModel.dispatch(CustomSettingsAction.SetSuperIslandCustomColor(superIslandCustomColor.toArgb()))
                                                superIslandColorEditing = false
                                            },
                                            onCancel = {
                                                superIslandCustomColor = superIslandColorSnapshot
                                                superIslandColorEditing = false
                                            },
                                            onUseDefault = {
                                                val defaultColor = Color(SuperIslandColorSource.DEFAULT_CUSTOM_COLOR)
                                                superIslandCustomColor = defaultColor
                                                superIslandColorSnapshot = defaultColor
                                                viewModel.dispatch(CustomSettingsAction.SetSuperIslandCustomColor(defaultColor.toArgb()))
                                                superIslandColorEditing = false
                                            }
                                        )
                                    }

                                    SettingsCardDivider()
                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_super_island_share),
                                        subtitle = stringResource(R.string.settings_super_island_share_desc),
                                        checked = superIslandShareEnabled,
                                        onCheckedChange = {
                                            superIslandShareEnabled = it
                                            viewModel.dispatch(CustomSettingsAction.SetSuperIslandShareEnabled(it))
                                        }
                                    )

                                        if (superIslandShareEnabled) {
                                            val formatDisplayName = when (superIslandShareFormat) {
                                                "format_2" -> stringResource(R.string.share_format_2)
                                                "format_3" -> stringResource(R.string.share_format_3)
                                                else -> stringResource(R.string.share_format_1)
                                            }
                                            SettingsCardDivider()
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                SettingsTextItem(
                                                    title = stringResource(R.string.settings_super_island_share_format),
                                                    value = formatDisplayName,
                                                    onClick = { showShareFormatDropdown = true }
                                                )
                                                Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                                    MaterialBlurDropdownMenu(
                                                        expanded = showShareFormatDropdown,
                                                        onDismissRequest = { showShareFormatDropdown = false }
                                                    ) {
                                                        val formats = listOf(
                                                            "format_1" to R.string.share_format_1,
                                                            "format_2" to R.string.share_format_2,
                                                            "format_3" to R.string.share_format_3
                                                        )
                                                        formats.forEach { (formatId, nameId) ->
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(nameId)) },
                                                                onClick = {
                                                                    superIslandShareFormat = formatId
                                                                    viewModel.dispatch(CustomSettingsAction.SetSuperIslandShareFormat(formatId))
                                                                    showShareFormatDropdown = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        var blockXmsfMode by remember { mutableStateOf(XmsfBypassMode.read(prefs)) }
                                        var blockXmsfCustomDurationMs by remember {
                                            mutableIntStateOf(XmsfBypassMode.readCustomDurationMs(prefs))
                                        }
                                        var showBlockXmsfModeDropdown by remember { mutableStateOf(false) }
                                        SettingsCardDivider()
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            SettingsTextItem(
                                                title = stringResource(R.string.settings_block_xmsf_mode),
                                                subtitle = stringResource(R.string.settings_block_xmsf_mode_desc),
                                                value = when (blockXmsfMode) {
                                                    XmsfBypassMode.STANDARD -> stringResource(R.string.settings_block_xmsf_mode_standard)
                                                    XmsfBypassMode.CUSTOM -> stringResource(R.string.settings_block_xmsf_mode_custom)
                                                    XmsfBypassMode.AGGRESSIVE -> stringResource(R.string.settings_block_xmsf_mode_aggressive)
                                                    XmsfBypassMode.DISABLED -> stringResource(R.string.settings_block_xmsf_mode_disabled)
                                                },
                                                onClick = { showBlockXmsfModeDropdown = true }
                                            )
                                            Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                                MaterialBlurDropdownMenu(
                                                    expanded = showBlockXmsfModeDropdown,
                                                    onDismissRequest = { showBlockXmsfModeDropdown = false }
                                                ) {
                                                    listOf(
                                                        Triple(
                                                            XmsfBypassMode.DISABLED,
                                                            stringResource(R.string.settings_block_xmsf_mode_disabled),
                                                            stringResource(R.string.settings_block_xmsf_mode_disabled_desc)
                                                        ),
                                                        Triple(
                                                            XmsfBypassMode.STANDARD,
                                                            stringResource(R.string.settings_block_xmsf_mode_standard),
                                                            stringResource(R.string.settings_block_xmsf_mode_standard_desc)
                                                        ),
                                                        Triple(
                                                            XmsfBypassMode.CUSTOM,
                                                            stringResource(R.string.settings_block_xmsf_mode_custom),
                                                            stringResource(R.string.settings_block_xmsf_mode_custom_desc)
                                                        ),
                                                        Triple(
                                                            XmsfBypassMode.AGGRESSIVE,
                                                            stringResource(R.string.settings_block_xmsf_mode_aggressive),
                                                            stringResource(R.string.settings_block_xmsf_mode_aggressive_desc)
                                                        )
                                                    ).forEach { (mode, label, summary) ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                    Text(text = label)
                                                                    Text(
                                                                        text = summary,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            },
                                                            onClick = {
                                                                showBlockXmsfModeDropdown = false
                                                                if (mode == XmsfBypassMode.DISABLED) {
                                                                    blockXmsfMode = mode
                                                                    viewModel.dispatch(CustomSettingsAction.SetXmsfBypassMode(mode))
                                                                } else {
                                                                    scope.launch {
                                                                        try {
                                                                            com.example.islandlyrics.integration.shizuku.requireShizukuPermissionGranted {
                                                                                blockXmsfMode = mode
                                                                                viewModel.dispatch(CustomSettingsAction.SetXmsfBypassMode(mode))
                                                                            }
                                                                        } catch (_: Exception) {
                                                                            Toast.makeText(context, "Shizuku permission required", Toast.LENGTH_LONG).show()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (blockXmsfMode == XmsfBypassMode.CUSTOM) {
                                            MaterialXmsfBypassDurationSlider(
                                                title = stringResource(R.string.settings_block_xmsf_custom_duration),
                                                summary = stringResource(R.string.settings_block_xmsf_custom_duration_desc),
                                                value = blockXmsfCustomDurationMs,
                                                onValueChange = { newDurationMs ->
                                                    blockXmsfCustomDurationMs = newDurationMs
                                                    viewModel.dispatch(CustomSettingsAction.SetXmsfCustomDurationMs(newDurationMs))
                                                }
                                            )
                                            Text(
                                                text = stringResource(R.string.settings_block_xmsf_custom_duration_warning),
                                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                }
                                } // end SettingsCard (HyperOS)
                            }
                        }
                        CustomSettingsTab.NOTIFICATION -> { // Notification
                             NotificationPreview(
                                 progressColorEnabled = progressColorEnabled,
                                 actionStyle = effectiveActionStyle,
                                 superIslandEnabled = superIslandEnabled,
                                 superIslandColorSource = superIslandColorSource,
                                 superIslandCustomColor = superIslandCustomColor,
                                 superIslandSmartMinContrast = uiState.superIslandSmartMinContrast,
                                 superIslandSmartWhiteRatio = uiState.superIslandSmartWhiteRatio,
                                 superIslandMediaButtonLayout = superIslandMediaButtonLayout,
                                 superIslandNotificationStyle = superIslandNotificationStyle,
                                 superIslandLyricMode = superIslandLyricMode,
                                 superIslandFullLyricShowLeftCover = superIslandFullLyricShowLeftCover,
                                 showProgressBar = superIslandShowProgressBar,
                                 template2PicSource = template2PicSource
                             )
                             Spacer(modifier = Modifier.height(16.dp))

                            SettingsCard {
                            // 旧版「通知按键」仅保留给非超级岛（实时更新胶囊）使用
                            if (!superIslandEnabled) {
                                val actionStyleDisplay = when (actionStyle) {
                                    "media_controls" -> stringResource(R.string.settings_action_style_media)
                                    "miplay" -> stringResource(R.string.settings_action_style_miplay)
                                    else -> stringResource(R.string.settings_action_style_off)
                                }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    SettingsTextItem(
                                        title = stringResource(R.string.settings_notification_actions),
                                        value = actionStyleDisplay,
                                        onClick = { showActionStyleDropdown = true }
                                    )
                                    Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                        MaterialBlurDropdownMenu(
                                            expanded = showActionStyleDropdown,
                                            onDismissRequest = { showActionStyleDropdown = false }
                                        ) {
                                            val allStyles = listOf(
                                                "disabled" to R.string.settings_action_style_off,
                                                "media_controls" to R.string.settings_action_style_media,
                                                "miplay" to R.string.settings_action_style_miplay
                                            )
                                            val styles = allStyles.filter { (styleId, _) ->
                                                if (styleId == "miplay") isLiveUpdateSupported && !superIslandEnabled else true
                                            }
                                            styles.forEach { (styleId, nameId) ->
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(nameId)) },
                                                    onClick = {
                                                        actionStyle = styleId
                                                        viewModel.dispatch(CustomSettingsAction.SetNotificationActionsStyle(styleId))
                                                        showActionStyleDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                // 进度条颜色（实时更新胶囊保留原逻辑）
                                if (actionStyle == "disabled" || (actionStyle == "media_controls" &&
                                        (superIslandNotificationStyle == LabFeatureManager.SUPER_ISLAND_STYLE_ADVANCED ||
                                                superIslandNotificationStyle == LabFeatureManager.SUPER_ISLAND_STYLE_ADVANCED_LYRICS_DUAL))
                                ) {
                                    SettingsCardDivider()
                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_progress_color),
                                        subtitle = stringResource(R.string.settings_progress_color_desc),
                                        checked = progressColorEnabled,
                                        onCheckedChange = {
                                            progressColorEnabled = it
                                            viewModel.dispatch(CustomSettingsAction.SetProgressColorEnabled(it))
                                        }
                                    )
                                }
                            }

                            if (superIslandEnabled) {
                                // 1. 通知样式（通知页最上面）
                                val notificationStyleDisplayName = when (superIslandNotificationStyle) {
                                    LabFeatureManager.SUPER_ISLAND_STYLE_ADVANCED -> stringResource(R.string.super_island_notification_style_advanced_beta)
                                    LabFeatureManager.SUPER_ISLAND_STYLE_ADVANCED_LYRICS_DUAL -> stringResource(R.string.super_island_notification_style_advanced_lyrics_dual)
                                    else -> stringResource(R.string.super_island_notification_style_standard)
                                }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    SettingsTextItem(
                                        title = stringResource(R.string.settings_super_island_notification_style),
                                        value = notificationStyleDisplayName,
                                        onClick = { showSuperIslandNotificationStyleDropdown = true }
                                    )
                                    Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                        MaterialBlurDropdownMenu(
                                            expanded = showSuperIslandNotificationStyleDropdown,
                                            onDismissRequest = { showSuperIslandNotificationStyleDropdown = false }
                                        ) {
                                            val styleOptions = buildList {
                                                add(LabFeatureManager.SUPER_ISLAND_STYLE_STANDARD to R.string.super_island_notification_style_standard)
                                                if (superIslandAdvancedStyleLabEnabled) {
                                                    add(LabFeatureManager.SUPER_ISLAND_STYLE_ADVANCED to R.string.super_island_notification_style_advanced_beta)
                                                    add(LabFeatureManager.SUPER_ISLAND_STYLE_ADVANCED_LYRICS_DUAL to R.string.super_island_notification_style_advanced_lyrics_dual)
                                                }
                                            }
                                            styleOptions.forEach { (styleId, nameId) ->
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(nameId)) },
                                                    onClick = {
                                                        superIslandNotificationStyle = styleId
                                                        viewModel.dispatch(CustomSettingsAction.SetSuperIslandNotificationStyle(styleId))
                                                        showSuperIslandNotificationStyleDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // 第二行内容（高级双行歌词样式专属）
                                if (superIslandNotificationStyle == LabFeatureManager.SUPER_ISLAND_STYLE_ADVANCED_LYRICS_DUAL) {
                                    val dualLineModeDisplayName = when (superIslandDualLineMode) {
                                        SuperIslandDualLineMode.NEXT_LYRIC -> stringResource(R.string.super_island_dual_line_mode_next_lyric)
                                        SuperIslandDualLineMode.ROMANIZATION -> stringResource(R.string.super_island_dual_line_mode_romanization)
                                        else -> stringResource(R.string.super_island_dual_line_mode_translation)
                                    }
                                    SettingsCardDivider()
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_super_island_dual_line_mode),
                                            value = dualLineModeDisplayName,
                                            onClick = { showSuperIslandDualLineModeDropdown = true }
                                        )
                                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                            MaterialBlurDropdownMenu(
                                                expanded = showSuperIslandDualLineModeDropdown,
                                                onDismissRequest = { showSuperIslandDualLineModeDropdown = false }
                                            ) {
                                                val dualLineModeOptions = listOf(
                                                    SuperIslandDualLineMode.NEXT_LYRIC to R.string.super_island_dual_line_mode_next_lyric,
                                                    SuperIslandDualLineMode.TRANSLATION to R.string.super_island_dual_line_mode_translation,
                                                    SuperIslandDualLineMode.ROMANIZATION to R.string.super_island_dual_line_mode_romanization
                                                )
                                                dualLineModeOptions.forEach { (modeId, nameId) ->
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(nameId)) },
                                                        onClick = {
                                                            superIslandDualLineMode = modeId
                                                            viewModel.dispatch(CustomSettingsAction.SetSuperIslandDualLineMode(modeId))
                                                            showSuperIslandDualLineModeDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. 播放按键布局（无按键置顶）
                                val layoutDisplayName = when (superIslandMediaButtonLayout) {
                                    "three_button" -> stringResource(R.string.super_island_media_button_layout_three)
                                    "no_button" -> stringResource(R.string.super_island_media_button_layout_none)
                                    else -> stringResource(R.string.super_island_media_button_layout_two)
                                }
                                SettingsCardDivider()
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    SettingsTextItem(
                                        title = stringResource(R.string.settings_super_island_media_button_layout),
                                        value = layoutDisplayName,
                                        onClick = { showSuperIslandMediaButtonLayoutDropdown = true }
                                    )
                                    Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                        MaterialBlurDropdownMenu(
                                            expanded = showSuperIslandMediaButtonLayoutDropdown,
                                            onDismissRequest = { showSuperIslandMediaButtonLayoutDropdown = false }
                                        ) {
                                            val layoutOptions = listOf(
                                                Triple(
                                                    "no_button",
                                                    R.string.super_island_media_button_layout_none,
                                                    R.string.super_island_media_button_layout_none_desc
                                                ),
                                                Triple(
                                                    "two_button",
                                                    R.string.super_island_media_button_layout_two,
                                                    R.string.super_island_media_button_layout_two_desc
                                                ),
                                                Triple(
                                                    "three_button",
                                                    R.string.super_island_media_button_layout_three,
                                                    R.string.super_island_media_button_layout_three_desc
                                                )
                                            )
                                            layoutOptions.forEach { (layoutId, nameId, descId) ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                            Text(stringResource(nameId))
                                                            Text(
                                                                text = stringResource(descId),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        superIslandMediaButtonLayout = layoutId
                                                        viewModel.dispatch(CustomSettingsAction.SetSuperIslandMediaButtonLayout(layoutId))
                                                        showSuperIslandMediaButtonLayoutDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // 3. 显示进度条（仅标准样式 + 无按键时出现）
                                if (superIslandNotificationStyle == LabFeatureManager.SUPER_ISLAND_STYLE_STANDARD &&
                                    superIslandMediaButtonLayout == "no_button"
                                ) {
                                    SettingsCardDivider()
                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_super_island_show_progress_bar),
                                        subtitle = stringResource(R.string.settings_super_island_show_progress_bar_desc),
                                        checked = superIslandShowProgressBar,
                                        onCheckedChange = {
                                            superIslandShowProgressBar = it
                                            viewModel.dispatch(CustomSettingsAction.SetSuperIslandShowProgressBar(it))
                                        }
                                    )
                                }

                                // 4. 显示模式：仅模板2（标准样式 + 无按键 + 不显示进度条）时显示
                                if (effectiveActionStyle == "template2") {
                                    val secondaryModesLabel = secondaryTextModes.mapNotNull { mode ->
                                        when (mode) {
                                            SuperIslandSecondaryTextMode.NEXT_LYRIC.preferenceValue ->
                                                stringResource(R.string.super_island_secondary_text_next_lyric)
                                            SuperIslandSecondaryTextMode.ROMANIZATION.preferenceValue ->
                                                stringResource(R.string.super_island_secondary_text_romanization)
                                            else -> stringResource(R.string.super_island_secondary_text_translation)
                                        }
                                    }.joinToString(" / ")
                                    SettingsCardDivider()
                                    SettingsTextItem(
                                        title = stringResource(R.string.settings_super_island_secondary_text_mode),
                                        value = secondaryModesLabel.ifEmpty {
                                            stringResource(R.string.super_island_secondary_text_next_lyric)
                                        },
                                        onClick = { showSecondaryTextModeDialog = true }
                                    )
                                }

                                // 5. 进度条颜色（显示进度条开启且进度可见时）
                                if (superIslandShowProgressBar &&
                                    (effectiveActionStyle == "disabled" || (effectiveActionStyle == "media_controls" &&
                                            (superIslandNotificationStyle == LabFeatureManager.SUPER_ISLAND_STYLE_ADVANCED ||
                                                    superIslandNotificationStyle == LabFeatureManager.SUPER_ISLAND_STYLE_ADVANCED_LYRICS_DUAL)))
                                ) {
                                    SettingsCardDivider()
                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_progress_color),
                                        subtitle = stringResource(R.string.settings_progress_color_desc),
                                        checked = progressColorEnabled,
                                        onCheckedChange = {
                                            progressColorEnabled = it
                                            viewModel.dispatch(CustomSettingsAction.SetProgressColorEnabled(it))
                                        }
                                    )
                                }

                                // 6. 模板2识别图形组件来源（标准样式 + 无按键 + 隐藏进度条时生效）
                                if (effectiveActionStyle == "template2") {
                                    val template2PicDisplay = when (template2PicSource) {
                                        SuperIslandTemplate2PicSource.PLAYING_APP.preferenceValue ->
                                            stringResource(R.string.super_island_template2_pic_playing_app)
                                        SuperIslandTemplate2PicSource.APP_ICON.preferenceValue ->
                                            stringResource(R.string.super_island_template2_pic_app_icon)
                                        SuperIslandTemplate2PicSource.CUSTOM.preferenceValue ->
                                            stringResource(R.string.super_island_template2_pic_custom)
                                        else -> stringResource(R.string.super_island_template2_pic_album_art)
                                    }
                                    SettingsCardDivider()
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_super_island_template2_pic_source),
                                            value = template2PicDisplay,
                                            onClick = { showTemplate2PicSourceDropdown = true }
                                        )
                                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                            MaterialBlurDropdownMenu(
                                                expanded = showTemplate2PicSourceDropdown,
                                                onDismissRequest = { showTemplate2PicSourceDropdown = false }
                                            ) {
                                                val picOptions = listOf(
                                                    Triple(
                                                        SuperIslandTemplate2PicSource.ALBUM_ART.preferenceValue,
                                                        R.string.super_island_template2_pic_album_art,
                                                        R.string.super_island_template2_pic_album_art_desc
                                                    ),
                                                    Triple(
                                                        SuperIslandTemplate2PicSource.PLAYING_APP.preferenceValue,
                                                        R.string.super_island_template2_pic_playing_app,
                                                        R.string.super_island_template2_pic_playing_app_desc
                                                    ),
                                                    Triple(
                                                        SuperIslandTemplate2PicSource.APP_ICON.preferenceValue,
                                                        R.string.super_island_template2_pic_app_icon,
                                                        R.string.super_island_template2_pic_app_icon_desc
                                                    ),
                                                    Triple(
                                                        SuperIslandTemplate2PicSource.CUSTOM.preferenceValue,
                                                        R.string.super_island_template2_pic_custom,
                                                        R.string.super_island_template2_pic_custom_desc
                                                    )
                                                )
                                                picOptions.forEach { (sourceId, nameId, descId) ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                Text(stringResource(nameId))
                                                                Text(
                                                                    text = stringResource(descId),
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            template2PicSource = sourceId
                                                            viewModel.dispatch(CustomSettingsAction.SetSuperIslandTemplate2PicSource(sourceId))
                                                            showTemplate2PicSourceDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (template2PicSource == SuperIslandTemplate2PicSource.CUSTOM.preferenceValue) {
                                        SettingsCardDivider()
                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_super_island_template2_pic_custom_pick),
                                            value = template2CustomPicUri
                                                ?: stringResource(R.string.settings_super_island_template2_pic_custom_none),
                                            onClick = { template2PicPickerLauncher.launch(arrayOf("image/*")) }
                                        )
                                    }
                                }
                            }

                            SettingsCardDivider()
                            val clickStyleDisplay = when (notificationClickStyle) {
                                "media_controls" -> stringResource(R.string.settings_click_action_media)
                                "open_playing_app" -> stringResource(R.string.settings_click_action_open_playing_app)
                                else -> stringResource(R.string.settings_click_action_default)
                            }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                SettingsTextItem(
                                    title = stringResource(R.string.settings_click_action_title),
                                    value = clickStyleDisplay,
                                    onClick = { showNotificationClickDropdown = true }
                                )
                                Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                    MaterialBlurDropdownMenu(
                                        expanded = showNotificationClickDropdown,
                                        onDismissRequest = { showNotificationClickDropdown = false }
                                    ) {
                                        val styles = listOf(
                                            Triple("default", R.string.settings_click_action_default, R.string.settings_click_action_default_desc),
                                            Triple("media_controls", R.string.settings_click_action_media, R.string.settings_click_action_media_desc),
                                            Triple("open_playing_app", R.string.settings_click_action_open_playing_app, R.string.settings_click_action_open_playing_app_desc)
                                        )
                                        styles.forEach { (styleId, nameId, descId) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        Text(stringResource(nameId))
                                                        Text(
                                                            text = stringResource(descId),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    notificationClickStyle = styleId
                                                    viewModel.dispatch(CustomSettingsAction.SetNotificationClickStyle(styleId))
                                                    showNotificationClickDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            SettingsCardDivider()
                            val dismissDelayText = when (dismissDelay) {
                                0L -> stringResource(R.string.dismiss_delay_immediate)
                                1000L -> stringResource(R.string.dismiss_delay_1s)
                                3000L -> stringResource(R.string.dismiss_delay_3s)
                                5000L -> stringResource(R.string.dismiss_delay_5s)
                                else -> stringResource(R.string.dismiss_delay_immediate)
                            }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                SettingsTextItem(
                                    title = stringResource(R.string.settings_dismiss_delay_title),
                                    value = dismissDelayText,
                                    onClick = { showDismissDelayDropdown = true }
                                )
                                Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                    MaterialBlurDropdownMenu(
                                        expanded = showDismissDelayDropdown,
                                        onDismissRequest = { showDismissDelayDropdown = false }
                                    ) {
                                        val options = listOf(
                                            0L to R.string.dismiss_delay_immediate,
                                            1000L to R.string.dismiss_delay_1s,
                                            3000L to R.string.dismiss_delay_3s,
                                            5000L to R.string.dismiss_delay_5s
                                        )
                                        options.forEach { (delay, labelRes) ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(labelRes)) },
                                                onClick = {
                                                    dismissDelay = delay
                                                    viewModel.dispatch(CustomSettingsAction.SetDismissDelay(delay))
                                                    showDismissDelayDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            } // end SettingsCard (notification)
                        }
                        CustomSettingsTab.APP_UI -> { // App UI
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsCard {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    val uiStyleDisplay = when (miuixEnabled) {
                                        true -> stringResource(R.string.ui_style_miuix)
                                        else -> stringResource(R.string.ui_style_material)
                                    }
                                    SettingsTextItem(
                                        title = stringResource(R.string.settings_app_ui_style),
                                        value = uiStyleDisplay,
                                        onClick = { showUiStyleDropdown = true }
                                    )
                                    Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                        MaterialBlurDropdownMenu(
                                            expanded = showUiStyleDropdown,
                                            onDismissRequest = { showUiStyleDropdown = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.ui_style_material)) },
                                                onClick = {
                                                    showUiStyleDropdown = false
                                                    if (miuixEnabled) {
                                                        miuixEnabled = false
                                                        viewModel.dispatch(CustomSettingsAction.SetMiuixEnabled(false))
                                                        val restartIntent = Intent(context, MainActivity::class.java)
                                                        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                                        context.startActivity(restartIntent)
                                                        (context as? Activity)?.finish()
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.ui_style_miuix)) },
                                                onClick = {
                                                    showUiStyleDropdown = false
                                                    if (!miuixEnabled) {
                                                        miuixEnabled = true
                                                        viewModel.dispatch(CustomSettingsAction.SetMiuixEnabled(true))
                                                        val restartIntent = Intent(context, MainActivity::class.java)
                                                        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                                        context.startActivity(restartIntent)
                                                        (context as? Activity)?.finish()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                SettingsCardDivider()
                                SettingsTextItem(
                                    title = stringResource(R.string.settings_home_lyric_preview_title),
                                    value = homeLyricPreviewDisplayModes.labelForHomeLyricPreview(),
                                    onClick = { showHomeLyricPreviewDialog = true }
                                )
                                SettingsCardDivider()
                                SettingsSwitchItem(
                                    title = stringResource(R.string.settings_theme_follow_system),
                                    checked = followSystem,
                                    onCheckedChange = {
                                        followSystem = it
                                        viewModel.dispatch(CustomSettingsAction.SetFollowSystem(it))
                                    }
                                )
                                SettingsCardDivider()
                                SettingsSwitchItem(
                                    title = stringResource(R.string.settings_theme_dark_mode),
                                    checked = darkMode,
                                    enabled = !followSystem,
                                    onCheckedChange = {
                                        darkMode = it
                                        viewModel.dispatch(CustomSettingsAction.SetDarkMode(it))
                                    }
                                )
                                SettingsCardDivider()
                                SettingsSwitchItem(
                                    title = stringResource(R.string.settings_theme_pure_black),
                                    subtitle = stringResource(R.string.settings_theme_pure_black_desc),
                                    checked = pureBlack,
                                    enabled = useDarkTheme,
                                    onCheckedChange = {
                                        pureBlack = it
                                        viewModel.dispatch(CustomSettingsAction.SetPureBlack(it))
                                    }
                                )
                                SettingsCardDivider()
                                SettingsSwitchItem(
                                    title = stringResource(R.string.settings_theme_dynamic_color),
                                    subtitle = stringResource(R.string.settings_theme_dynamic_color_desc),
                                    checked = dynamicColor,
                                    onCheckedChange = {
                                        dynamicColor = it
                                        viewModel.dispatch(CustomSettingsAction.SetDynamicColor(it))
                                    }
                                )
                                if (!dynamicColor) {
                                    val themeColorSources = listOf(
                                        ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT,
                                        ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_CUSTOM
                                    )
                                    val themeColorSourceLabels = listOf(
                                        stringResource(R.string.settings_theme_color_source_default),
                                        stringResource(R.string.settings_theme_color_source_custom)
                                    )
                                    val currentThemeColorSourceIndex =
                                        themeColorSources.indexOf(materialThemeColorSource).takeIf { it >= 0 } ?: 0

                                    SettingsCardDivider()
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_theme_color_source),
                                            value = themeColorSourceLabels[currentThemeColorSourceIndex],
                                            onClick = { showThemeColorSourceDropdown = true }
                                        )
                                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                            MaterialBlurDropdownMenu(
                                                expanded = showThemeColorSourceDropdown,
                                                onDismissRequest = { showThemeColorSourceDropdown = false }
                                            ) {
                                                themeColorSourceLabels.forEachIndexed { index, label ->
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            if (materialThemeColorEditing) {
                                                                customThemeColor = materialThemeColorSnapshot
                                                                materialThemeColorEditing = false
                                                            }
                                                            materialThemeColorSource = themeColorSources[index]
                                                            viewModel.dispatch(CustomSettingsAction.SetMaterialThemeColorSource(materialThemeColorSource))
                                                            showThemeColorSourceDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (materialThemeColorSource == ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_CUSTOM) {
                                        SettingsCardDivider()
                                        MaterialEditableColorSection(
                                            title = stringResource(R.string.settings_theme_custom_color),
                                            color = customThemeColor,
                                            isEditing = materialThemeColorEditing,
                                            defaultActionText = stringResource(R.string.settings_theme_color_source_default),
                                            onStartEditing = {
                                                materialThemeColorSnapshot = customThemeColor
                                                materialThemeColorEditing = true
                                            },
                                            onColorChanged = { color ->
                                                customThemeColor = color
                                            },
                                            onApply = {
                                                viewModel.dispatch(CustomSettingsAction.SetMaterialCustomColor(customThemeColor.toArgb()))
                                                materialThemeColorEditing = false
                                            },
                                            onCancel = {
                                                customThemeColor = materialThemeColorSnapshot
                                                materialThemeColorEditing = false
                                            },
                                            onUseDefault = {
                                                customThemeColor = materialThemeColorSnapshot
                                                materialThemeColorSource = ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT
                                                viewModel.dispatch(CustomSettingsAction.SetMaterialThemeColorSource(ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT))
                                                materialThemeColorEditing = false
                                            }
                                        )
                                    }
                                }
                                SettingsCardDivider()
                                SettingsSwitchItem(
                                    title = stringResource(R.string.settings_predictive_back),
                                    subtitle = stringResource(R.string.settings_predictive_back_desc),
                                    checked = predictiveBackEnabled,
                                    onCheckedChange = {
                                        predictiveBackEnabled = it
                                        viewModel.dispatch(CustomSettingsAction.SetPredictiveBackEnabled(it))
                                    }
                                )
                                SettingsCardDivider()
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    val predictiveBackModes = PredictiveBackAnimationMode.options
                                    val predictiveBackModeLabels = predictiveBackModes.map { stringResource(it.labelRes) }
                                    val currentPredictiveBackModeIndex =
                                        predictiveBackModes.indexOf(predictiveBackAnimationMode).takeIf { it >= 0 } ?: 0

                                    SettingsTextItem(
                                        title = stringResource(R.string.settings_predictive_back_animation_mode),
                                        subtitle = stringResource(R.string.settings_predictive_back_animation_mode_desc),
                                        value = predictiveBackModeLabels[currentPredictiveBackModeIndex],
                                        onClick = { showPredictiveBackAnimationModeDropdown = true }
                                    )
                                    Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                        MaterialBlurDropdownMenu(
                                            expanded = showPredictiveBackAnimationModeDropdown,
                                            onDismissRequest = { showPredictiveBackAnimationModeDropdown = false }
                                        ) {
                                            predictiveBackModes.forEachIndexed { index, mode ->
                                                DropdownMenuItem(
                                                    text = { Text(predictiveBackModeLabels[index]) },
                                                    onClick = {
                                                        predictiveBackAnimationMode = mode
                                                        viewModel.dispatch(CustomSettingsAction.SetPredictiveBackAnimationMode(mode))
                                                        if (mode != PredictiveBackAnimationMode.Consistent) {
                                                            showPredictiveBackAnimationDropdown = false
                                                        }
                                                        showPredictiveBackAnimationModeDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                if (predictiveBackAnimationMode == PredictiveBackAnimationMode.Consistent) {
                                    SettingsCardDivider()
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        val predictiveBackStyles = PredictiveBackAnimationStyle.options
                                        val predictiveBackStyleLabels = predictiveBackStyles.map { stringResource(it.labelRes) }
                                        val currentPredictiveBackStyleIndex =
                                            predictiveBackStyles.indexOf(predictiveBackAnimationStyle).takeIf { it >= 0 } ?: 0

                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_predictive_back_animation),
                                            subtitle = stringResource(R.string.settings_predictive_back_animation_desc),
                                            value = predictiveBackStyleLabels[currentPredictiveBackStyleIndex],
                                            onClick = { showPredictiveBackAnimationDropdown = true }
                                        )
                                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                            MaterialBlurDropdownMenu(
                                                expanded = showPredictiveBackAnimationDropdown,
                                                onDismissRequest = { showPredictiveBackAnimationDropdown = false }
                                            ) {
                                                predictiveBackStyles.forEachIndexed { index, style ->
                                                    DropdownMenuItem(
                                                            text = { Text(predictiveBackStyleLabels[index]) },
                                                            onClick = {
                                                                predictiveBackAnimationStyle = style
                                                                viewModel.dispatch(CustomSettingsAction.SetPredictiveBackAnimationStyle(style))
                                                                showPredictiveBackAnimationDropdown = false
                                                            }
                                                        )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        CustomSettingsTab.DESKTOP_LYRICS -> {
                            FloatingLyricsSettingsSubScreen(prefs)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            
            // --- Dialogs (Shared) ---
            if (showLanguageDialog) {
                LanguageSelectionDialog(
                    onDismiss = { showLanguageDialog = false }
                )
            }

            if (showPrivacyDialog) {
                MaterialBlurAlertDialog(
                    onDismissRequest = { showPrivacyDialog = false },
                    title = { Text(stringResource(R.string.dialog_privacy_title)) },
                    text = { Text(stringResource(R.string.dialog_privacy_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showPrivacyDialog = false
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }) {
                            Text(stringResource(R.string.dialog_btn_understand))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPrivacyDialog = false }) {
                            Text(stringResource(R.string.dialog_btn_cancel))
                        }
                    }
                )
            }

            if (showHomeLyricPreviewDialog) {
                MaterialHomeLyricSecondaryTextModeDialog(
                    selectedModes = homeLyricPreviewDisplayModes,
                    onDismiss = { showHomeLyricPreviewDialog = false },
                    onCommit = { modes ->
                        homeLyricPreviewDisplayModes = modes
                        viewModel.dispatch(
                            CustomSettingsAction.SetHomeLyricPreviewDisplayModes(modes)
                        )
                    }
                )
            }

            if (showSecondaryTextModeDialog) {
                MaterialSecondaryTextModeDialog(
                    selectedModes = secondaryTextModes,
                    onDismiss = { showSecondaryTextModeDialog = false },
                    onCommit = { modes ->
                        secondaryTextModes = modes
                        viewModel.dispatch(CustomSettingsAction.SetSuperIslandSecondaryTextModes(modes))
                    }
                )
            }

        }
    }
}

@Composable
private fun MaterialSecondaryTextModeDialog(
    selectedModes: List<String>,
    onDismiss: () -> Unit,
    onCommit: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val keepOneText = stringResource(R.string.settings_home_lyric_preview_keep_one)
    var modes by remember(selectedModes) { mutableStateOf(selectedModes) }
    var draggingMode by remember { mutableStateOf<String?>(null) }

    fun toggle(mode: SuperIslandSecondaryTextMode, checked: Boolean) {
        val prefValue = mode.preferenceValue
        if (checked) {
            if (prefValue !in modes) {
                modes = modes + prefValue
            }
        } else {
            if (modes.size <= 1) {
                Toast.makeText(context, keepOneText, Toast.LENGTH_SHORT).show()
                return
            }
            modes = modes - prefValue
        }
    }

    MaterialBlurAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_super_island_secondary_text_mode)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SuperIslandSecondaryTextMode.entries.forEach { mode ->
                    MaterialHomeLyricPreviewOption(
                        title = stringResource(mode.materialLabelRes()),
                        checked = mode.preferenceValue in modes,
                        onCheckedChange = { toggle(mode, it) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_super_island_secondary_text_priority),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                val rowHeight = 48.dp
                val orderedModes = modes.mapNotNull { SuperIslandSecondaryTextMode.from(it) }
                Box(modifier = Modifier.fillMaxWidth().height(rowHeight * orderedModes.size)) {
                    orderedModes.forEachIndexed { index, mode ->
                        key(mode.preferenceValue) {
                            MaterialSecondaryModeDragRow(
                                label = stringResource(mode.materialLabelRes()),
                                index = index,
                                rowHeight = rowHeight,
                                itemCount = orderedModes.size,
                                isDragging = draggingMode == mode.preferenceValue,
                                onDragStart = { draggingMode = mode.preferenceValue },
                                onDragMove = { from, to ->
                                    modes = modes.moveItem(from, to)
                                },
                                onDragCancel = { draggingMode = null },
                                onDragEnd = { draggingMode = null }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalModes = modes
                        .mapNotNull { SuperIslandSecondaryTextMode.from(it) }
                        .ifEmpty { listOf(SuperIslandSecondaryTextMode.NEXT_LYRIC) }
                        .map { it.preferenceValue }
                    onCommit(finalModes)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.backup_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_btn_cancel))
            }
        }
    )
}

@Composable
private fun MaterialHomeLyricSecondaryTextModeDialog(
    selectedModes: List<String>,
    onDismiss: () -> Unit,
    onCommit: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val keepOneText = stringResource(R.string.settings_home_lyric_preview_keep_one)
    var modes by remember(selectedModes) { mutableStateOf(selectedModes) }
    var draggingMode by remember { mutableStateOf<String?>(null) }

    fun toggle(mode: SecondaryTextMode, checked: Boolean) {
        val prefValue = mode.preferenceValue
        if (checked) {
            if (prefValue !in modes) {
                modes = modes + prefValue
            }
        } else {
            if (modes.size <= 1) {
                Toast.makeText(context, keepOneText, Toast.LENGTH_SHORT).show()
                return
            }
            modes = modes - prefValue
        }
    }

    MaterialBlurAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_home_lyric_preview_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SecondaryTextMode.entries.forEach { mode ->
                    MaterialHomeLyricPreviewOption(
                        title = stringResource(mode.materialLabelRes()),
                        checked = mode.preferenceValue in modes,
                        onCheckedChange = { toggle(mode, it) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_super_island_secondary_text_priority),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                val rowHeight = 48.dp
                val orderedModes = modes.mapNotNull { SecondaryTextMode.from(it) }
                Box(modifier = Modifier.fillMaxWidth().height(rowHeight * orderedModes.size)) {
                    orderedModes.forEachIndexed { index, mode ->
                        key(mode.preferenceValue) {
                            MaterialSecondaryModeDragRow(
                                label = stringResource(mode.materialLabelRes()),
                                index = index,
                                rowHeight = rowHeight,
                                itemCount = orderedModes.size,
                                isDragging = draggingMode == mode.preferenceValue,
                                onDragStart = { draggingMode = mode.preferenceValue },
                                onDragMove = { from, to ->
                                    modes = modes.moveItem(from, to)
                                },
                                onDragCancel = { draggingMode = null },
                                onDragEnd = { draggingMode = null }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalModes = modes
                        .mapNotNull { SecondaryTextMode.from(it) }
                        .ifEmpty { listOf(SecondaryTextMode.NEXT_LYRIC) }
                        .map { it.preferenceValue }
                    onCommit(finalModes)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.backup_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_btn_cancel))
            }
        }
    )
}

@Composable
private fun MaterialSecondaryModeDragRow(
    label: String,
    index: Int,
    rowHeight: Dp,
    itemCount: Int,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragMove: (Int, Int) -> Unit,
    onDragCancel: () -> Unit,
    onDragEnd: () -> Unit
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentIndex by rememberUpdatedState(index)
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    val animatedY by animateDpAsState(
        targetValue = rowHeight * index,
        animationSpec = spring(stiffness = 650f, dampingRatio = 0.85f),
        label = "secondaryModeReorderY"
    )
    val baseY = if (isDragging) rowHeight * index else animatedY
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .offset {
                IntOffset(
                    x = 0,
                    y = baseY.roundToPx() + if (isDragging) dragOffset.roundToInt() else 0
                )
            }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                alpha = if (isDragging) 0.92f else 1f
                scaleX = if (isDragging) 1.01f else 1f
                scaleY = if (isDragging) 1.01f else 1f
            }
            .then(
                if (isDragging) {
                    Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp)
                } else {
                    Modifier.padding(start = 48.dp)
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            Icons.Default.DragHandle,
            contentDescription = stringResource(R.string.action_drag_sort),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(40.dp)
                .padding(8.dp)
                .pointerInput(itemCount) {
                    detectDragGestures(
                        onDragStart = {
                            dragOffset = 0f
                            onDragStart()
                        },
                        onDragEnd = {
                            dragOffset = 0f
                            onDragEnd()
                        },
                        onDragCancel = {
                            dragOffset = 0f
                            onDragCancel()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.y
                            val from = currentIndex
                            val target = (from + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, itemCount - 1)
                            if (target != from) {
                                dragOffset -= (target - from) * rowHeightPx
                                onDragMove(from, target)
                            }
                        }
                    )
                }
        )
    }
}

private fun SuperIslandSecondaryTextMode.materialLabelRes(): Int = when (this) {
    SuperIslandSecondaryTextMode.NEXT_LYRIC -> R.string.super_island_secondary_text_next_lyric
    SuperIslandSecondaryTextMode.ROMANIZATION -> R.string.super_island_secondary_text_romanization
    SuperIslandSecondaryTextMode.TRANSLATION -> R.string.super_island_secondary_text_translation
}

@Composable
private fun SecondaryTextMode.materialLabelRes(): Int = when (this) {
    SecondaryTextMode.NEXT_LYRIC -> R.string.super_island_secondary_text_next_lyric
    SecondaryTextMode.ROMANIZATION -> R.string.super_island_secondary_text_romanization
    SecondaryTextMode.TRANSLATION -> R.string.super_island_secondary_text_translation
}

private fun <T> List<T>.moveItem(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply {
        val item = removeAt(from)
        add(to, item)
    }
}

@Composable
private fun MaterialHomeLyricPreviewOption(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MaterialSuperIslandTextLimitSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val clampedValue = value.coerceIn(valueRange)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        Text(
            text = "$title: ${formatSuperIslandTextLimit(clampedValue)}",
            style = MaterialTheme.typography.bodyLarge
        )
        Slider(
            value = clampedValue,
            onValueChange = { raw ->
                val stepped = (kotlin.math.round(raw * 2f) / 2f).coerceIn(valueRange)
                onValueChange(stepped)
            },
            valueRange = valueRange,
            steps = ((valueRange.endInclusive - valueRange.start) / 0.5f).toInt() - 1
        )
    }
}

@Composable
private fun MaterialXmsfBypassDurationSlider(
    title: String,
    summary: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val clampedValue = value.coerceIn(
        XmsfBypassMode.MIN_CUSTOM_DURATION_MS,
        XmsfBypassMode.MAX_CUSTOM_DURATION_MS
    )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        Text(
            text = "$title: ${stringResource(R.string.settings_block_xmsf_duration_value, clampedValue)}",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = clampedValue.toFloat(),
            onValueChange = { raw ->
                val stepped = snapXmsfBypassDuration(raw.roundToInt())
                onValueChange(stepped)
            },
            valueRange = XmsfBypassMode.MIN_CUSTOM_DURATION_MS.toFloat()..XmsfBypassMode.MAX_CUSTOM_DURATION_MS.toFloat(),
            steps = ((XmsfBypassMode.MAX_CUSTOM_DURATION_MS - XmsfBypassMode.MIN_CUSTOM_DURATION_MS) /
                XmsfBypassMode.CUSTOM_DURATION_STEP_MS) - 1
        )
    }
}

private fun formatSuperIslandTextLimit(value: Float): String {
    return if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        "%.1f".format(java.util.Locale.US, value)
    }
}

private fun snapXmsfBypassDuration(durationMs: Int): Int {
    val clamped = durationMs.coerceIn(
        XmsfBypassMode.MIN_CUSTOM_DURATION_MS,
        XmsfBypassMode.MAX_CUSTOM_DURATION_MS
    )
    val offset = clamped - XmsfBypassMode.MIN_CUSTOM_DURATION_MS
    val steppedOffset = ((offset.toFloat() / XmsfBypassMode.CUSTOM_DURATION_STEP_MS).roundToInt()) *
        XmsfBypassMode.CUSTOM_DURATION_STEP_MS
    return (XmsfBypassMode.MIN_CUSTOM_DURATION_MS + steppedOffset)
        .coerceIn(XmsfBypassMode.MIN_CUSTOM_DURATION_MS, XmsfBypassMode.MAX_CUSTOM_DURATION_MS)
}

@Composable
private fun List<String>.labelForHomeLyricPreview(): String {
    val labels = mapNotNull { mode ->
        SecondaryTextMode.from(mode)?.let { stringResource(it.materialLabelRes()) }
    }
    return labels.ifEmpty {
        listOf(stringResource(SecondaryTextMode.NEXT_LYRIC.materialLabelRes()))
    }.joinToString(" / ")
}

@Composable
fun CapsuleNotificationScreen(
    onBack: () -> Unit,
    viewModel: CustomSettingsViewModel = viewModel()
) {
    CustomSettingsScreen(
        onBack = onBack,
        title = stringResource(R.string.settings_capsule_notification_title),
        tabs = setOf(CustomSettingsTab.CAPSULE, CustomSettingsTab.NOTIFICATION),
        viewModel = viewModel
    )
}

@Composable
fun AppUiScreen(
    onBack: () -> Unit,
    viewModel: CustomSettingsViewModel = viewModel()
) {
    CustomSettingsScreen(
        onBack = onBack,
        title = stringResource(R.string.page_title_personalization),
        tabs = setOf(CustomSettingsTab.APP_UI),
        viewModel = viewModel
    )
}

@Composable
fun DesktopLyricsScreen(
    onBack: () -> Unit,
    viewModel: CustomSettingsViewModel = viewModel()
) {
    CustomSettingsScreen(
        onBack = onBack,
        title = stringResource(R.string.settings_floating_lyrics),
        tabs = setOf(CustomSettingsTab.DESKTOP_LYRICS),
        viewModel = viewModel
    )
}
