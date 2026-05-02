package com.example.islandlyrics.feature.customsettings.miuix

import android.app.Activity
import com.example.islandlyrics.ui.common.NotificationPreview
import com.example.islandlyrics.ui.common.CapsulePreview
import com.example.islandlyrics.ui.common.OneUiCapsuleColorMode
import com.example.islandlyrics.R
import com.example.islandlyrics.core.platform.XmsfBypassMode
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.service.LyricService
import com.example.islandlyrics.feature.main.MainActivity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as SuperDropdown
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import com.example.islandlyrics.ui.miuix.*

@Composable
fun MiuixCustomSettingsScreen(
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onShowLogs: () -> Unit,
    updateVersionText: String,
    updateBuildText: String
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var floatingLyricsLabEnabled by remember { mutableStateOf(LabFeatureManager.isFloatingLyricsEnabled(prefs)) }

    val tabs = buildList {
        add(stringResource(R.string.tab_capsule))
        add(stringResource(R.string.tab_notification))
        add(stringResource(R.string.tab_app_ui))
        if (floatingLyricsLabEnabled) {
            add(stringResource(R.string.settings_floating_lyrics))
        }
    }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val floatingLyricsPageIndex = if (floatingLyricsLabEnabled) tabs.lastIndex else -1

    // State
    var followSystem by remember { mutableStateOf(prefs.getBoolean("theme_follow_system", true)) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("theme_dark_mode", false)) }
    var iconStyle by remember { mutableStateOf(prefs.getString("dynamic_icon_style", "disabled") ?: "disabled") }

    var actionStyle by remember { mutableStateOf(prefs.getString("notification_actions_style", "disabled") ?: "disabled") }
    var superIslandMediaButtonLayout by remember { mutableStateOf(prefs.getString("super_island_media_button_layout", "two_button") ?: "two_button") }
    var superIslandNotificationStyle by remember { mutableStateOf(LabFeatureManager.sanitizeSuperIslandNotificationStyle(context)) }
    var superIslandAdvancedStyleLabEnabled by remember { mutableStateOf(LabFeatureManager.isSuperIslandAdvancedStyleEnabled(prefs)) }
    var notificationClickStyle by remember { mutableStateOf(prefs.getString("notification_click_style", "default") ?: "default") }
    var dismissDelay by remember { mutableLongStateOf(prefs.getLong("notification_dismiss_delay", 0L)) }

    var progressColorEnabled by remember { mutableStateOf(prefs.getBoolean("progress_bar_color_enabled", false)) }
    var disableScrolling by remember { mutableStateOf(prefs.getBoolean("disable_lyric_scrolling", false)) }
    var oneuiCapsuleColorMode by remember { mutableStateOf(OneUiCapsuleColorMode.read(prefs)) }

    var superIslandEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_enabled", false)) }
    var superIslandLyricMode by remember { mutableStateOf(prefs.getString("super_island_lyric_mode", "standard") ?: "standard") }
    var superIslandFullLyricShowLeftCover by remember { mutableStateOf(prefs.getBoolean("super_island_full_lyric_show_left_cover", true)) }
    var superIslandTextColorEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_text_color_enabled", false)) }

    var superIslandShareEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_share_enabled", true)) }
    var superIslandShareFormat by remember { mutableStateOf(prefs.getString("super_island_share_format", "format_1") ?: "format_1") }
    var miuixEnabled by remember { mutableStateOf(prefs.getBoolean("ui_use_miuix", false)) }
    var predictiveBackEnabled by remember { mutableStateOf(prefs.getBoolean("predictive_back_enabled", false)) }
    var monetEnabled by remember { mutableStateOf(prefs.getBoolean("theme_dynamic_color", true)) }
    var cardBlurEnabled by remember { mutableStateOf(prefs.getBoolean("card_blur_enabled", false)) }
    var blockXmsfMode by remember { mutableStateOf(XmsfBypassMode.read(prefs)) }

    val isLiveUpdateSupported = remember { RomUtils.isLiveUpdateSupported() }
    val isHyperOs = remember { RomUtils.isHyperOs() }
    fun setSuperIslandMode(enabled: Boolean) {
        if (superIslandEnabled == enabled) return

        superIslandEnabled = enabled
        prefs.edit().putBoolean("super_island_enabled", enabled).apply()

        if (enabled && actionStyle == "miplay") {
            actionStyle = "disabled"
            prefs.edit().putString("notification_actions_style", "disabled").apply()
        }

        val action = if (enabled) {
            "ACTION_ENABLE_SUPER_ISLAND"
        } else {
            "ACTION_DISABLE_SUPER_ISLAND"
        }
        val intent = Intent(context, LyricService::class.java).setAction(action)
        context.startService(intent)
    }

    LaunchedEffect(Unit) {
        LabFeatureManager.ensureInitialized(prefs)
        superIslandAdvancedStyleLabEnabled = LabFeatureManager.isSuperIslandAdvancedStyleEnabled(prefs)
        floatingLyricsLabEnabled = LabFeatureManager.isFloatingLyricsEnabled(prefs)
        superIslandNotificationStyle = LabFeatureManager.sanitizeSuperIslandNotificationStyle(context)
    }
    LaunchedEffect(superIslandLyricMode) {
        if (superIslandLyricMode == "full" && !disableScrolling) {
            disableScrolling = true
            prefs.edit().putBoolean("disable_lyric_scrolling", true).apply()
        }
    }
    LaunchedEffect(isLiveUpdateSupported) {
        if (!isLiveUpdateSupported) {
            if (iconStyle != "disabled") {
                iconStyle = "disabled"
                prefs.edit().putString("dynamic_icon_style", "disabled").apply()
            }
            if (actionStyle == "miplay") {
                actionStyle = "disabled"
                prefs.edit().putString("notification_actions_style", "disabled").apply()
            }
        }
    }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurSmallTopAppBar(
                title = stringResource(R.string.page_title_personalization),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
    ) { padding ->
        // Tab Row + Pager
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Spacer(modifier = Modifier.height(12.dp))
            TabRowWithContour(
                tabs = tabs,
                selectedTabIndex = pagerState.currentPage,
                onTabSelected = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                colors = TabRowDefaults.tabRowColors(
                    backgroundColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    contentColor = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    selectedBackgroundColor = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                    selectedContentColor = MiuixTheme.colorScheme.onBackground
                ),
                maxWidth = 96.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                ) {
                    when (page) {
                        0 -> { // Capsule
                            item {
                                val previewIconStyle = if (superIslandEnabled) "advanced" else iconStyle
                                CapsulePreview(
                                    dynamicIconEnabled = if (superIslandEnabled) true else iconStyle != "disabled",
                                    iconStyle = previewIconStyle,
                                    oneuiCapsuleColorMode = oneuiCapsuleColorMode
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                            item {
                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                    SuperSwitch(
                                        title = stringResource(R.string.settings_disable_scrolling),
                                        summary = stringResource(R.string.settings_disable_scrolling_desc),
                                        checked = disableScrolling || superIslandLyricMode == "full",
                                        enabled = superIslandLyricMode != "full",
                                        onCheckedChange = {
                                            disableScrolling = it
                                            prefs.edit().putBoolean("disable_lyric_scrolling", it).apply()
                                        }
                                    )
                                    if (RomUtils.getRomType() == "OneUI") {
                                        val oneUiColorModes = OneUiCapsuleColorMode.values
                                        val oneUiColorModeLabels = listOf(
                                            stringResource(R.string.oneui_capsule_color_mode_black),
                                            stringResource(R.string.oneui_capsule_color_mode_transparent),
                                            stringResource(R.string.oneui_capsule_color_mode_translucent_black),
                                            stringResource(R.string.oneui_capsule_color_mode_album)
                                        )
                                        val currentModeIndex = oneUiColorModes.indexOf(oneuiCapsuleColorMode).takeIf { it >= 0 } ?: 0

                                        SuperDropdown(
                                            title = stringResource(R.string.settings_oneui_capsule_color),
                                            items = oneUiColorModeLabels,
                                            selectedIndex = currentModeIndex,
                                            onSelectedIndexChange = { index ->
                                                val newMode = oneUiColorModes[index]
                                                oneuiCapsuleColorMode = newMode
                                                OneUiCapsuleColorMode.write(prefs, newMode)
                                            }
                                        )
                                    }
                                    if (isHyperOs) {
                                        if (isLiveUpdateSupported) {
                                            val capsuleModes = listOf(false, true)
                                            val capsuleModeLabels = listOf(
                                                stringResource(R.string.capsule_mode_live_update),
                                                stringResource(R.string.capsule_mode_super_island)
                                            )
                                            val currentCapsuleModeIndex = capsuleModes.indexOf(superIslandEnabled).takeIf { it >= 0 } ?: 0

                                            SuperDropdown(
                                                title = stringResource(R.string.settings_capsule_mode),
                                                items = capsuleModeLabels,
                                                selectedIndex = currentCapsuleModeIndex,
                                                onSelectedIndexChange = { index ->
                                                    setSuperIslandMode(capsuleModes[index])
                                                }
                                            )
                                        }
                                        if (superIslandEnabled || !isLiveUpdateSupported) {
                                            val lyricModes = listOf("standard", "full")
                                            val lyricModeLabels = listOf(
                                                stringResource(R.string.super_island_lyric_mode_standard),
                                                stringResource(R.string.super_island_lyric_mode_full)
                                            )
                                            val currentLyricModeIndex = lyricModes.indexOf(superIslandLyricMode).takeIf { it >= 0 } ?: 0

                                            SuperDropdown(
                                                title = stringResource(R.string.settings_super_island_lyric_mode),
                                                items = lyricModeLabels,
                                                selectedIndex = currentLyricModeIndex,
                                                onSelectedIndexChange = { index ->
                                                    val newMode = lyricModes[index]
                                                    superIslandLyricMode = newMode
                                                    prefs.edit()
                                                        .putString("super_island_lyric_mode", newMode)
                                                        .apply()
                                                    if (newMode == "full" && !disableScrolling) {
                                                        disableScrolling = true
                                                        prefs.edit().putBoolean("disable_lyric_scrolling", true).apply()
                                                    }
                                                }
                                            )

                                            if (superIslandLyricMode == "full") {
                                                SuperSwitch(
                                                    title = stringResource(R.string.settings_super_island_full_lyric_show_left_cover),
                                                    summary = stringResource(R.string.settings_super_island_full_lyric_show_left_cover_desc),
                                                    checked = superIslandFullLyricShowLeftCover,
                                                    onCheckedChange = {
                                                        superIslandFullLyricShowLeftCover = it
                                                        prefs.edit().putBoolean("super_island_full_lyric_show_left_cover", it).apply()
                                                    }
                                                )
                                            }

                                            SuperSwitch(
                                                title = stringResource(R.string.settings_super_island_colorize),
                                                summary = stringResource(R.string.settings_super_island_colorize_desc),
                                                checked = superIslandTextColorEnabled,
                                                onCheckedChange = {
                                                    superIslandTextColorEnabled = it
                                                    progressColorEnabled = it
                                                    prefs.edit().putBoolean("super_island_text_color_enabled", it).apply()
                                                    prefs.edit().putBoolean("progress_bar_color_enabled", it).apply()
                                                }
                                            )

                                            SuperSwitch(
                                                title = stringResource(R.string.settings_super_island_share),
                                                summary = stringResource(R.string.settings_super_island_share_desc),
                                                checked = superIslandShareEnabled,
                                                onCheckedChange = {
                                                    superIslandShareEnabled = it
                                                    prefs.edit().putBoolean("super_island_share_enabled", it).apply()
                                                }
                                            )

                                            if (superIslandShareEnabled) {
                                                val shareFormats = listOf("format_1", "format_2", "format_3")
                                                val shareFormatNames = listOf(
                                                    stringResource(R.string.share_format_1),
                                                    stringResource(R.string.share_format_2),
                                                    stringResource(R.string.share_format_3)
                                                )
                                                val currentFormatIndex = shareFormats.indexOf(superIslandShareFormat).takeIf { it >= 0 } ?: 0

                                                SuperDropdown(
                                                    title = stringResource(R.string.settings_super_island_share_format),
                                                    items = shareFormatNames,
                                                    selectedIndex = currentFormatIndex,
                                                    onSelectedIndexChange = { index ->
                                                        val newFormat = shareFormats[index]
                                                        superIslandShareFormat = newFormat
                                                        prefs.edit().putString("super_island_share_format", newFormat).apply()
                                                    }
                                                )
                                            }

                                            val bypassModeLabels = listOf(
                                                stringResource(R.string.settings_block_xmsf_mode_disabled),
                                                stringResource(R.string.settings_block_xmsf_mode_standard),
                                                stringResource(R.string.settings_block_xmsf_mode_aggressive)
                                            )
                                            val bypassModes = listOf(
                                                XmsfBypassMode.DISABLED,
                                                XmsfBypassMode.STANDARD,
                                                XmsfBypassMode.AGGRESSIVE
                                            )
                                            val currentBypassIndex = bypassModes.indexOf(blockXmsfMode).takeIf { it >= 0 } ?: 0

                                            SuperDropdown(
                                                title = stringResource(R.string.settings_block_xmsf_mode),
                                                items = bypassModeLabels,
                                                selectedIndex = currentBypassIndex,
                                                onSelectedIndexChange = { index ->
                                                    val newMode = bypassModes[index]
                                                    if (newMode == XmsfBypassMode.DISABLED) {
                                                        blockXmsfMode = newMode
                                                        XmsfBypassMode.write(prefs, newMode)
                                                    } else {
                                                        scope.launch {
                                                            try {
                                                                com.example.islandlyrics.integration.shizuku.requireShizukuPermissionGranted {
                                                                    blockXmsfMode = newMode
                                                                    XmsfBypassMode.write(prefs, newMode)
                                                                }
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Shizuku permission required", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            )

                                        }
                                    }
                                    if (isLiveUpdateSupported && !superIslandEnabled) {
                                        val iconStyles = listOf("disabled", "advanced")
                                        val iconStyleNames = listOf(
                                            stringResource(R.string.icon_style_classic),
                                            stringResource(R.string.icon_style_advanced)
                                        )
                                        val currentIconIndex = iconStyles.indexOf(iconStyle).takeIf { it >= 0 } ?: 0
                                        
                                        SuperDropdown(
                                            title = stringResource(R.string.settings_icon_style),
                                            items = iconStyleNames,
                                            selectedIndex = currentIconIndex,
                                            onSelectedIndexChange = { index ->
                                                val newStyle = iconStyles[index]
                                                iconStyle = newStyle
                                                prefs.edit().putString("dynamic_icon_style", newStyle).apply()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        1 -> { // Notification
                            item {
                                NotificationPreview(
                                    progressColorEnabled = progressColorEnabled,
                                    actionStyle = actionStyle,
                                    superIslandEnabled = superIslandEnabled,
                                    superIslandTextColorEnabled = superIslandTextColorEnabled,
                                    superIslandMediaButtonLayout = superIslandMediaButtonLayout,
                                    superIslandNotificationStyle = superIslandNotificationStyle
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                            item {
                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                    // ⚡ Logic: Hide Colorize Progress Bar if Playback Notification (media_controls) is selected
                                    if (actionStyle == "disabled" || (actionStyle == "media_controls" && superIslandNotificationStyle == "advanced_beta")) {
                                        SuperSwitch(
                                            title = stringResource(R.string.settings_progress_color),
                                            summary = stringResource(R.string.settings_progress_color_desc),
                                            checked = progressColorEnabled,
                                            onCheckedChange = {
                                                progressColorEnabled = it
                                                prefs.edit().putBoolean("progress_bar_color_enabled", it).apply()
                                            }
                                        )
                                    }

                                    val actionStyles = mutableListOf("disabled", "media_controls")
                                    val actionStyleNames = mutableListOf(
                                        stringResource(R.string.settings_action_style_off),
                                        stringResource(R.string.settings_action_style_media)
                                    )
                                    if (isLiveUpdateSupported && !superIslandEnabled) {
                                        actionStyles.add("miplay")
                                        actionStyleNames.add(stringResource(R.string.settings_action_style_miplay))
                                    }
                                    val currentActionIndex = actionStyles.indexOf(actionStyle).takeIf { it >= 0 } ?: 0

                                    SuperDropdown(
                                        title = stringResource(R.string.settings_notification_actions),
                                        items = actionStyleNames,
                                        selectedIndex = currentActionIndex,
                                        onSelectedIndexChange = { index ->
                                            val newStyle = actionStyles[index]
                                            actionStyle = newStyle
                                            prefs.edit().putString("notification_actions_style", newStyle).apply()
                                        }
                                    )

                                    if (actionStyle == "media_controls" && (superIslandEnabled || !isLiveUpdateSupported)) {
                                        val notificationStyles = buildList {
                                            add(LabFeatureManager.SUPER_ISLAND_STYLE_STANDARD)
                                            if (superIslandAdvancedStyleLabEnabled) {
                                                add(LabFeatureManager.SUPER_ISLAND_STYLE_ADVANCED)
                                            }
                                        }
                                        val notificationStyleNames = notificationStyles.map { style ->
                                            when (style) {
                                                LabFeatureManager.SUPER_ISLAND_STYLE_ADVANCED -> stringResource(R.string.super_island_notification_style_advanced_beta)
                                                else -> stringResource(R.string.super_island_notification_style_standard)
                                            }
                                        }
                                        val currentNotificationStyleIndex = notificationStyles.indexOf(superIslandNotificationStyle).takeIf { it >= 0 } ?: 0

                                        SuperDropdown(
                                            title = stringResource(R.string.settings_super_island_notification_style),
                                            items = notificationStyleNames,
                                            selectedIndex = currentNotificationStyleIndex,
                                            onSelectedIndexChange = { index ->
                                                val newStyle = notificationStyles[index]
                                                superIslandNotificationStyle = newStyle
                                                prefs.edit().putString("super_island_notification_style", newStyle).apply()
                                            }
                                        )

                                        if (superIslandNotificationStyle != "advanced_beta") {
                                            val buttonLayouts = listOf("two_button", "three_button")
                                            val buttonLayoutNames = listOf(
                                                stringResource(R.string.super_island_media_button_layout_two),
                                                stringResource(R.string.super_island_media_button_layout_three)
                                            )
                                            val currentButtonLayoutIndex = buttonLayouts.indexOf(superIslandMediaButtonLayout).takeIf { it >= 0 } ?: 0

                                            SuperDropdown(
                                                title = stringResource(R.string.settings_super_island_media_button_layout),
                                                items = buttonLayoutNames,
                                                selectedIndex = currentButtonLayoutIndex,
                                                onSelectedIndexChange = { index ->
                                                    val newLayout = buttonLayouts[index]
                                                    superIslandMediaButtonLayout = newLayout
                                                    prefs.edit().putString("super_island_media_button_layout", newLayout).apply()
                                                }
                                            )
                                        }
                                    }

                                    val clickStyles = listOf("default", "media_controls")
                                    val clickStyleNames = listOf(
                                        stringResource(R.string.settings_click_action_default),
                                        stringResource(R.string.settings_click_action_media)
                                    )
                                    val currentClickIndex = clickStyles.indexOf(notificationClickStyle).takeIf { it >= 0 } ?: 0

                                    SuperDropdown(
                                        title = stringResource(R.string.settings_click_action_title),
                                        items = clickStyleNames,
                                        selectedIndex = currentClickIndex,
                                        onSelectedIndexChange = { index ->
                                            val newStyle = clickStyles[index]
                                            notificationClickStyle = newStyle
                                            prefs.edit().putString("notification_click_style", newStyle).apply()
                                        }
                                    )

                                    val delayOptions = listOf(0L, 1000L, 3000L, 5000L)
                                    val delayNames = listOf(
                                        stringResource(R.string.dismiss_delay_immediate),
                                        stringResource(R.string.dismiss_delay_1s),
                                        stringResource(R.string.dismiss_delay_3s),
                                        stringResource(R.string.dismiss_delay_5s)
                                    )
                                    val currentDelayIndex = delayOptions.indexOf(dismissDelay).takeIf { it >= 0 } ?: 0

                                    SuperDropdown(
                                        title = stringResource(R.string.settings_dismiss_delay_title),
                                        items = delayNames,
                                        selectedIndex = currentDelayIndex,
                                        onSelectedIndexChange = { index ->
                                            val newDelay = delayOptions[index]
                                            dismissDelay = newDelay
                                            prefs.edit().putLong("notification_dismiss_delay", newDelay).apply()
                                        }
                                    )
                                }
                            }
                        }
                        2 -> { // App UI
                            item {
                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                    val uiStyles = listOf(false, true)
                                    val uiStyleNames = listOf(
                                        stringResource(R.string.ui_style_material),
                                        stringResource(R.string.ui_style_miuix)
                                    )
                                    val currentUiIndex = uiStyles.indexOf(miuixEnabled).takeIf { it >= 0 } ?: 0
                                    
                                    SuperDropdown(
                                        title = stringResource(R.string.settings_app_ui_style),
                                        items = uiStyleNames,
                                        selectedIndex = currentUiIndex,
                                        onSelectedIndexChange = { index ->
                                            val newStyle = uiStyles[index]
                                            miuixEnabled = newStyle
                                            prefs.edit().putBoolean("ui_use_miuix", newStyle).apply()
                                            val restartIntent = Intent(context, MainActivity::class.java)
                                            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                            context.startActivity(restartIntent)
                                            (context as? Activity)?.finish()
                                        }
                                    )
                                    SuperSwitch(
                                        title = stringResource(R.string.settings_theme_follow_system),
                                        checked = followSystem,
                                        onCheckedChange = {
                                            followSystem = it
                                            ThemeHelper.setFollowSystem(context, it)
                                        }
                                    )
                                    SuperSwitch(
                                        title = stringResource(R.string.settings_theme_dark_mode),
                                        checked = darkMode,
                                        enabled = !followSystem,
                                        onCheckedChange = {
                                            darkMode = it
                                            ThemeHelper.setDarkMode(context, it)
                                        }
                                    )
                                    SuperSwitch(
                                        title = stringResource(R.string.settings_predictive_back),
                                        summary = stringResource(R.string.settings_predictive_back_desc),
                                        checked = predictiveBackEnabled,
                                        onCheckedChange = {
                                            predictiveBackEnabled = it
                                            prefs.edit().putBoolean("predictive_back_enabled", it).apply()
                                        }
                                    )
                                    SuperSwitch(
                                        title = stringResource(R.string.settings_theme_dynamic_color),
                                        summary = stringResource(R.string.settings_theme_dynamic_color_desc),
                                        checked = monetEnabled,
                                        onCheckedChange = { enabled ->
                                            monetEnabled = enabled
                                            ThemeHelper.setDynamicColor(context, enabled)
                                            // Restart to apply new ThemeController mode
                                            val restartIntent = Intent(context, MainActivity::class.java)
                                            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                            context.startActivity(restartIntent)
                                            (context as? Activity)?.finish()
                                        }
                                    )
                                    SuperSwitch(
                                        title = stringResource(R.string.settings_card_blur),
                                        summary = stringResource(R.string.settings_card_blur_desc),
                                        checked = cardBlurEnabled,
                                        onCheckedChange = {
                                            cardBlurEnabled = it
                                            prefs.edit().putBoolean("card_blur_enabled", it).apply()
                                        }
                                    )
                                }
                            }
                        }
                        floatingLyricsPageIndex -> { // Desktop Lyrics
                            item {
                                MiuixFloatingLyricsSettingsSubScreen(prefs, scope)
                            }
                        }
                    }
                }
            }
        }
    }
}
