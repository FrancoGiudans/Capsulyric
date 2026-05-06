package com.example.islandlyrics.feature.customsettings.material

import android.app.Activity
import com.example.islandlyrics.ui.common.NotificationPreview
import com.example.islandlyrics.ui.common.CapsulePreview
import com.example.islandlyrics.ui.common.LyricTextDisplayMode
import com.example.islandlyrics.ui.common.OneUiCapsuleColorMode
import com.example.islandlyrics.R
import com.example.islandlyrics.core.platform.XmsfBypassMode
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.update.UpdateChecker
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.service.LyricService
import com.example.islandlyrics.ui.theme.material.AppTheme
import com.example.islandlyrics.feature.settings.material.LanguageSelectionDialog
import com.example.islandlyrics.feature.settings.material.SettingsSwitchItem
import com.example.islandlyrics.feature.settings.material.SettingsTextItem
import com.example.islandlyrics.feature.main.MainActivity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CustomSettingsScreen(
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onShowLogs: () -> Unit,
    updateVersionText: String,
    updateBuildText: String
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
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

    // --- State Duplication ---

    // Preferences State
    var autoUpdateEnabled by remember { mutableStateOf(UpdateChecker.isAutoUpdateEnabled(context)) }
    
    // Theme State
    var followSystem by remember { mutableStateOf(prefs.getBoolean("theme_follow_system", true)) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("theme_dark_mode", false)) }
    var pureBlack by remember { mutableStateOf(prefs.getBoolean("theme_pure_black", false)) }
    var dynamicColor by remember { mutableStateOf(prefs.getBoolean("theme_dynamic_color", true)) }
    var iconStyle by remember { mutableStateOf(prefs.getString("dynamic_icon_style", "disabled") ?: "disabled") }
    
    // Dialog State
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showIconStyleDropdown by remember { mutableStateOf(false) }
    var showOneUiCapsuleColorDropdown by remember { mutableStateOf(false) }
    var showCapsuleModeDropdown by remember { mutableStateOf(false) }
    var showLyricTextDisplayModeDropdown by remember { mutableStateOf(false) }
    var showSuperIslandLyricModeDropdown by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // Notification Action Style State
    var actionStyle by remember { mutableStateOf(prefs.getString("notification_actions_style", "disabled") ?: "disabled") }
    var superIslandMediaButtonLayout by remember { mutableStateOf(prefs.getString("super_island_media_button_layout", "two_button") ?: "two_button") }
    var superIslandNotificationStyle by remember { mutableStateOf(LabFeatureManager.sanitizeSuperIslandNotificationStyle(context)) }
    var superIslandAdvancedStyleLabEnabled by remember { mutableStateOf(LabFeatureManager.isSuperIslandAdvancedStyleEnabled(prefs)) }
    var showActionStyleDropdown by remember { mutableStateOf(false) }
    var showSuperIslandMediaButtonLayoutDropdown by remember { mutableStateOf(false) }
    var showSuperIslandNotificationStyleDropdown by remember { mutableStateOf(false) }

    // Share Format Dropdown State
    var showShareFormatDropdown by remember { mutableStateOf(false) }

    // Notification Click Action State
    var notificationClickStyle by remember { mutableStateOf(prefs.getString("notification_click_style", "default") ?: "default") }
    var showNotificationClickDropdown by remember { mutableStateOf(false) }

    // Dismiss Delay State
    var dismissDelay by remember { mutableLongStateOf(prefs.getLong("notification_dismiss_delay", 0L)) }
    var showDismissDelayDropdown by remember { mutableStateOf(false) }

    // Other Setup
    var progressColorEnabled by remember { mutableStateOf(prefs.getBoolean("progress_bar_color_enabled", false)) }
    var hideRecentsEnabled by remember { mutableStateOf(prefs.getBoolean("hide_recents_enabled", false)) }
    var recommendMediaAppEnabled by remember { mutableStateOf(prefs.getBoolean("recommend_media_app", true)) }
    var disableScrolling by remember { mutableStateOf(prefs.getBoolean("disable_lyric_scrolling", false)) }
    var lyricTextDisplayMode by remember { mutableStateOf(LyricTextDisplayMode.read(prefs)) }
    var oneuiCapsuleColorMode by remember { mutableStateOf(OneUiCapsuleColorMode.read(prefs)) }

    var superIslandEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_enabled", false)) }
    var superIslandLyricMode by remember { mutableStateOf(prefs.getString("super_island_lyric_mode", "standard") ?: "standard") }
    var superIslandFullLyricShowLeftCover by remember { mutableStateOf(prefs.getBoolean("super_island_full_lyric_show_left_cover", true)) }
    var superIslandTextColorEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_text_color_enabled", false)) }

    var superIslandShareEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_share_enabled", true)) }
    var superIslandShareFormat by remember { mutableStateOf(prefs.getString("super_island_share_format", "format_1") ?: "format_1") }
    var miuixEnabled by remember { mutableStateOf(prefs.getBoolean("ui_use_miuix", false)) }
    var predictiveBackEnabled by remember { mutableStateOf(prefs.getBoolean("predictive_back_enabled", false)) }

    // Dialog State for UI Style
    var showUiStyleDropdown by remember { mutableStateOf(false) }

    // Check for HyperOS 3.0.300+
    val isLiveUpdateSupported = remember { RomUtils.isLiveUpdateSupported() }
    val isHyperOs = remember { RomUtils.isHyperOs() }
    val forceDisableScrollingForFullSuperIsland =
        isHyperOs && (superIslandEnabled || !isLiveUpdateSupported) && superIslandLyricMode == "full"

    fun applyFullSuperIslandScrollForce(force: Boolean, restoreLegacyState: Boolean = false) {
        val forcedKey = "full_super_island_forced_disable_scrolling"
        val backupKey = "disable_lyric_scrolling_before_full_super_island"
        val wasForced = prefs.getBoolean(forcedKey, false)
        val editor = prefs.edit()

        if (force) {
            if (!wasForced) {
                editor.putBoolean(backupKey, disableScrolling)
                editor.putBoolean(forcedKey, true)
            }
            disableScrolling = true
            editor.putBoolean("disable_lyric_scrolling", true)
        } else if (wasForced) {
            val restoredValue = prefs.getBoolean(backupKey, false)
            disableScrolling = restoredValue
            editor.putBoolean("disable_lyric_scrolling", restoredValue)
            editor.remove(backupKey)
            editor.remove(forcedKey)
        } else if (restoreLegacyState && disableScrolling) {
            disableScrolling = false
            editor.putBoolean("disable_lyric_scrolling", false)
        }

        editor.apply()
    }

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
    LaunchedEffect(forceDisableScrollingForFullSuperIsland) {
        applyFullSuperIslandScrollForce(forceDisableScrollingForFullSuperIsland)
    }

    // Logic for permissions status
    fun checkNotificationPermission(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    fun checkPostNotificationPermission(): Boolean {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
         }
         return true
    }

    var notificationGranted by remember { mutableStateOf(checkNotificationPermission()) }
    var postNotificationGranted by remember { mutableStateOf(checkPostNotificationPermission()) }

    // Force disable unsupported features
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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationGranted = checkNotificationPermission()
                postNotificationGranted = checkPostNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                 activity.window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
                 activity.window.setBackgroundDrawableResource(android.R.color.black)
             } else {
                 val typedValue = android.util.TypedValue()
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
        pureBlack = pureBlack && useDarkTheme
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.page_title_personalization)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                            }
                        },
                        colors = neutralMaterialTopBarColors()
                    )
                    PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(title) }
                            )
                        }
                    }
                }
            }
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
                    when (page) {
                        0 -> { // Capsule (Moved from 1)
                            val previewIconStyle = if (superIslandEnabled) "advanced" else iconStyle
                            CapsulePreview(
                                dynamicIconEnabled = if (superIslandEnabled) true else iconStyle != "disabled",
                                iconStyle = previewIconStyle,
                                oneuiCapsuleColorMode = oneuiCapsuleColorMode,
                                superIslandEnabled = superIslandEnabled || (isHyperOs && !isLiveUpdateSupported),
                                superIslandLyricMode = superIslandLyricMode,
                                superIslandFullLyricShowLeftCover = superIslandFullLyricShowLeftCover
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsSwitchItem(
                                title = stringResource(R.string.settings_disable_scrolling),
                                subtitle = stringResource(R.string.settings_disable_scrolling_desc),
                                checked = disableScrolling || forceDisableScrollingForFullSuperIsland,
                                enabled = !forceDisableScrollingForFullSuperIsland,
                                onCheckedChange = {
                                    disableScrolling = it
                                    prefs.edit().putBoolean("disable_lyric_scrolling", it).apply()
                                }
                            )

                            val lyricTextModes = LyricTextDisplayMode.values
                            val lyricTextModeLabels = listOf(
                                stringResource(R.string.lyric_text_display_mode_lyric),
                                stringResource(R.string.lyric_text_display_mode_translation),
                                stringResource(R.string.lyric_text_display_mode_romanization)
                            )
                            val currentLyricTextModeIndex = lyricTextModes.indexOf(lyricTextDisplayMode).takeIf { it >= 0 } ?: 0

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    SettingsTextItem(
                                        title = stringResource(R.string.settings_lyric_text_display_mode),
                                        value = lyricTextModeLabels[currentLyricTextModeIndex],
                                        onClick = { showLyricTextDisplayModeDropdown = true }
                                    )
                                    Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                        DropdownMenu(
                                            expanded = showLyricTextDisplayModeDropdown,
                                            onDismissRequest = { showLyricTextDisplayModeDropdown = false }
                                        ) {
                                            lyricTextModeLabels.forEachIndexed { index, label ->
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        lyricTextDisplayMode = lyricTextModes[index]
                                                        LyricTextDisplayMode.write(prefs, lyricTextDisplayMode)
                                                        showLyricTextDisplayModeDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.settings_lyric_text_display_mode_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp)
                                )
                            }

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
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    SettingsTextItem(
                                        title = stringResource(R.string.settings_oneui_capsule_color),
                                        value = oneUiColorModeDisplay,
                                        onClick = { showOneUiCapsuleColorDropdown = true }
                                    )
                                    Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                        DropdownMenu(
                                            expanded = showOneUiCapsuleColorDropdown,
                                            onDismissRequest = { showOneUiCapsuleColorDropdown = false }
                                        ) {
                                            oneUiColorModes.zip(oneUiColorModeLabels).forEach { (mode, label) ->
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        oneuiCapsuleColorMode = mode
                                                        OneUiCapsuleColorMode.write(prefs, mode)
                                                        showOneUiCapsuleColorDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (isHyperOs) {
                                if (isLiveUpdateSupported) {
                                    val capsuleModes = listOf(false, true)
                                    val capsuleModeLabels = listOf(
                                        stringResource(R.string.capsule_mode_live_update),
                                        stringResource(R.string.capsule_mode_super_island)
                                    )
                                    val currentCapsuleModeIndex = capsuleModes.indexOf(superIslandEnabled).takeIf { it >= 0 } ?: 0

                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_capsule_mode),
                                            value = capsuleModeLabels[currentCapsuleModeIndex],
                                            onClick = { showCapsuleModeDropdown = true }
                                        )
                                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                            DropdownMenu(
                                                expanded = showCapsuleModeDropdown,
                                                onDismissRequest = { showCapsuleModeDropdown = false }
                                            ) {
                                                capsuleModeLabels.forEachIndexed { index, label ->
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            setSuperIslandMode(capsuleModes[index])
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
                                        else -> stringResource(R.string.icon_style_classic)
                                    }
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_icon_style),
                                            value = styleDisplayName,
                                            onClick = { showIconStyleDropdown = true }
                                        )
                                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                            DropdownMenu(
                                                expanded = showIconStyleDropdown,
                                                onDismissRequest = { showIconStyleDropdown = false }
                                            ) {
                                                val styles = listOf(
                                                    "disabled" to R.string.icon_style_classic,
                                                    "advanced" to R.string.icon_style_advanced
                                                )
                                                styles.forEach { (styleId, nameId) ->
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(nameId)) },
                                                        onClick = {
                                                            iconStyle = styleId
                                                            prefs.edit().putString("dynamic_icon_style", styleId).apply()
                                                            showIconStyleDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (superIslandEnabled || !isLiveUpdateSupported) {
                                    val lyricModes = listOf("standard", "full")
                                    val lyricModeLabels = listOf(
                                        stringResource(R.string.super_island_lyric_mode_standard),
                                        stringResource(R.string.super_island_lyric_mode_full)
                                    )
                                    val currentLyricModeIndex = lyricModes.indexOf(superIslandLyricMode).takeIf { it >= 0 } ?: 0

                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_super_island_lyric_mode),
                                            value = lyricModeLabels[currentLyricModeIndex],
                                            onClick = { showSuperIslandLyricModeDropdown = true }
                                        )
                                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                            DropdownMenu(
                                                expanded = showSuperIslandLyricModeDropdown,
                                                onDismissRequest = { showSuperIslandLyricModeDropdown = false }
                                            ) {
                                                lyricModeLabels.forEachIndexed { index, label ->
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            val newMode = lyricModes[index]
                                                            superIslandLyricMode = newMode
                                                            prefs.edit()
                                                                .putString("super_island_lyric_mode", newMode)
                                                                .apply()
                                                            if (newMode == "standard") {
                                                                applyFullSuperIslandScrollForce(force = false, restoreLegacyState = true)
                                                            }
                                                            showSuperIslandLyricModeDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (superIslandLyricMode == "full") {
                                        SettingsSwitchItem(
                                            title = stringResource(R.string.settings_super_island_full_lyric_show_left_cover),
                                            subtitle = stringResource(R.string.settings_super_island_full_lyric_show_left_cover_desc),
                                            checked = superIslandFullLyricShowLeftCover,
                                            onCheckedChange = {
                                                superIslandFullLyricShowLeftCover = it
                                                prefs.edit().putBoolean("super_island_full_lyric_show_left_cover", it).apply()
                                            }
                                        )
                                    }

                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_super_island_colorize),
                                        subtitle = stringResource(R.string.settings_super_island_colorize_desc),
                                        checked = superIslandTextColorEnabled,
                                        onCheckedChange = {
                                            superIslandTextColorEnabled = it
                                            progressColorEnabled = it
                                            prefs.edit().putBoolean("super_island_text_color_enabled", it).apply()
                                            prefs.edit().putBoolean("progress_bar_color_enabled", it).apply()
                                        }
                                    )

                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_super_island_share),
                                        subtitle = stringResource(R.string.settings_super_island_share_desc),
                                        checked = superIslandShareEnabled,
                                        onCheckedChange = {
                                            superIslandShareEnabled = it
                                            prefs.edit().putBoolean("super_island_share_enabled", it).apply()
                                        }
                                    )

                                    if (superIslandShareEnabled) {
                                        val formatDisplayName = when (superIslandShareFormat) {
                                            "format_2" -> stringResource(R.string.share_format_2)
                                            "format_3" -> stringResource(R.string.share_format_3)
                                            else -> stringResource(R.string.share_format_1)
                                        }
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            SettingsTextItem(
                                                title = stringResource(R.string.settings_super_island_share_format),
                                                value = formatDisplayName,
                                                onClick = { showShareFormatDropdown = true }
                                            )
                                            Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                                DropdownMenu(
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
                                                                prefs.edit().putString("super_island_share_format", formatId).apply()
                                                                showShareFormatDropdown = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    var blockXmsfMode by remember { mutableStateOf(XmsfBypassMode.read(prefs)) }
                                    var showBlockXmsfModeDropdown by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_block_xmsf_mode),
                                            value = when (blockXmsfMode) {
                                                XmsfBypassMode.STANDARD -> stringResource(R.string.settings_block_xmsf_mode_standard)
                                                XmsfBypassMode.AGGRESSIVE -> stringResource(R.string.settings_block_xmsf_mode_aggressive)
                                                XmsfBypassMode.DISABLED -> stringResource(R.string.settings_block_xmsf_mode_disabled)
                                            },
                                            onClick = { showBlockXmsfModeDropdown = true }
                                        )
                                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                            DropdownMenu(
                                                expanded = showBlockXmsfModeDropdown,
                                                onDismissRequest = { showBlockXmsfModeDropdown = false }
                                            ) {
                                                listOf(
                                                    XmsfBypassMode.DISABLED to R.string.settings_block_xmsf_mode_disabled,
                                                    XmsfBypassMode.STANDARD to R.string.settings_block_xmsf_mode_standard,
                                                    XmsfBypassMode.AGGRESSIVE to R.string.settings_block_xmsf_mode_aggressive
                                                ).forEach { (mode, labelRes) ->
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(labelRes)) },
                                                        onClick = {
                                                            showBlockXmsfModeDropdown = false
                                                            if (mode == XmsfBypassMode.DISABLED) {
                                                                blockXmsfMode = mode
                                                                XmsfBypassMode.write(prefs, mode)
                                                            } else {
                                                                scope.launch {
                                                                    try {
                                                                        com.example.islandlyrics.integration.shizuku.requireShizukuPermissionGranted {
                                                                            blockXmsfMode = mode
                                                                            XmsfBypassMode.write(prefs, mode)
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
                                        }
                                    }

                                }
                            }
                        }
                        1 -> { // Notification (Moved from 2)
                             // Preview
                             NotificationPreview(
                                 progressColorEnabled = progressColorEnabled,
                                 actionStyle = actionStyle,
                                 superIslandEnabled = superIslandEnabled,
                                 superIslandTextColorEnabled = superIslandTextColorEnabled,
                                 superIslandMediaButtonLayout = superIslandMediaButtonLayout,
                                 superIslandNotificationStyle = superIslandNotificationStyle,
                                 superIslandLyricMode = superIslandLyricMode,
                                 superIslandFullLyricShowLeftCover = superIslandFullLyricShowLeftCover
                             )
                             Spacer(modifier = Modifier.height(16.dp))


                            if (actionStyle == "disabled" || (actionStyle == "media_controls" && superIslandNotificationStyle == "advanced_beta")) {
                                 SettingsSwitchItem(
                                    title = stringResource(R.string.settings_progress_color),
                                    subtitle = stringResource(R.string.settings_progress_color_desc),
                                    checked = progressColorEnabled,
                                    onCheckedChange = {
                                        progressColorEnabled = it
                                        prefs.edit().putBoolean("progress_bar_color_enabled", it).apply()
                                    }
                                )
                            }
                            
                             // Notification Action Style
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
                                    DropdownMenu(
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
                                                    prefs.edit().putString("notification_actions_style", styleId).apply()
                                                    showActionStyleDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (actionStyle == "media_controls" && (superIslandEnabled || !isLiveUpdateSupported)) {
                                val notificationStyleDisplayName = when (superIslandNotificationStyle) {
                                    "advanced_beta" -> stringResource(R.string.super_island_notification_style_advanced_beta)
                                    else -> stringResource(R.string.super_island_notification_style_standard)
                                }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    SettingsTextItem(
                                        title = stringResource(R.string.settings_super_island_notification_style),
                                        value = notificationStyleDisplayName,
                                        onClick = { showSuperIslandNotificationStyleDropdown = true }
                                    )
                                    Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                        DropdownMenu(
                                            expanded = showSuperIslandNotificationStyleDropdown,
                                            onDismissRequest = { showSuperIslandNotificationStyleDropdown = false }
                                        ) {
                                        val styleOptions = buildList {
                                            add(LabFeatureManager.SUPER_ISLAND_STYLE_STANDARD to R.string.super_island_notification_style_standard)
                                            if (superIslandAdvancedStyleLabEnabled) {
                                                add(LabFeatureManager.SUPER_ISLAND_STYLE_ADVANCED to R.string.super_island_notification_style_advanced_beta)
                                            }
                                        }
                                            styleOptions.forEach { (styleId, nameId) ->
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(nameId)) },
                                                    onClick = {
                                                        superIslandNotificationStyle = styleId
                                                        prefs.edit().putString("super_island_notification_style", styleId).apply()
                                                        showSuperIslandNotificationStyleDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                if (superIslandNotificationStyle != "advanced_beta") {
                                    val layoutDisplayName = when (superIslandMediaButtonLayout) {
                                        "three_button" -> stringResource(R.string.super_island_media_button_layout_three)
                                        else -> stringResource(R.string.super_island_media_button_layout_two)
                                    }
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_super_island_media_button_layout),
                                            value = layoutDisplayName,
                                            onClick = { showSuperIslandMediaButtonLayoutDropdown = true }
                                        )
                                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                            DropdownMenu(
                                                expanded = showSuperIslandMediaButtonLayoutDropdown,
                                                onDismissRequest = { showSuperIslandMediaButtonLayoutDropdown = false }
                                            ) {
                                                val layoutOptions = listOf(
                                                    "two_button" to R.string.super_island_media_button_layout_two,
                                                    "three_button" to R.string.super_island_media_button_layout_three
                                                )
                                                layoutOptions.forEach { (layoutId, nameId) ->
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(nameId)) },
                                                        onClick = {
                                                            superIslandMediaButtonLayout = layoutId
                                                            prefs.edit().putString("super_island_media_button_layout", layoutId).apply()
                                                            showSuperIslandMediaButtonLayoutDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
            
                            // Notification Click Action
                            val clickStyleDisplay = when (notificationClickStyle) {
                                "media_controls" -> stringResource(R.string.settings_click_action_media)
                                else -> stringResource(R.string.settings_click_action_default)
                            }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                SettingsTextItem(
                                    title = stringResource(R.string.settings_click_action_title),
                                    value = clickStyleDisplay,
                                    onClick = { showNotificationClickDropdown = true }
                                )
                                Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                    DropdownMenu(
                                        expanded = showNotificationClickDropdown,
                                        onDismissRequest = { showNotificationClickDropdown = false }
                                    ) {
                                        val styles = listOf(
                                            "default" to R.string.settings_click_action_default,
                                            "media_controls" to R.string.settings_click_action_media
                                        )
                                        styles.forEach { (styleId, nameId) ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(nameId)) },
                                                onClick = {
                                                    notificationClickStyle = styleId
                                                    prefs.edit().putString("notification_click_style", styleId).apply()
                                                    showNotificationClickDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            
                             // Dismiss Delay
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
                                    DropdownMenu(
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
                                                    prefs.edit().putLong("notification_dismiss_delay", delay).apply()
                                                    showDismissDelayDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        2 -> { // App UI (Moved from 0)
                             val uiStyleDisplay = when (miuixEnabled) {
                                 true -> stringResource(R.string.ui_style_miuix)
                                 else -> stringResource(R.string.ui_style_material)
                             }
                             Box(modifier = Modifier.fillMaxWidth()) {
                                 SettingsTextItem(
                                     title = stringResource(R.string.settings_app_ui_style),
                                     value = uiStyleDisplay,
                                     onClick = { showUiStyleDropdown = true }
                                 )
                                 Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                     DropdownMenu(
                                         expanded = showUiStyleDropdown,
                                         onDismissRequest = { showUiStyleDropdown = false }
                                     ) {
                                         DropdownMenuItem(
                                             text = { Text(stringResource(R.string.ui_style_material)) },
                                             onClick = {
                                                 showUiStyleDropdown = false
                                                 if (miuixEnabled) {
                                                     miuixEnabled = false
                                                     prefs.edit().putBoolean("ui_use_miuix", false).apply()
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
                                                     prefs.edit().putBoolean("ui_use_miuix", true).apply()
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
                             SettingsSwitchItem(
                                title = stringResource(R.string.settings_theme_follow_system),
                                checked = followSystem,
                                onCheckedChange = {
                                    followSystem = it
                                    ThemeHelper.setFollowSystem(context, it)
                                }
                            )
                            SettingsSwitchItem(
                                title = stringResource(R.string.settings_theme_dark_mode),
                                checked = darkMode,
                                enabled = !followSystem,
                                onCheckedChange = {
                                    darkMode = it
                                    ThemeHelper.setDarkMode(context, it)
                                }
                            )
                            SettingsSwitchItem(
                                title = stringResource(R.string.settings_theme_pure_black),
                                subtitle = stringResource(R.string.settings_theme_pure_black_desc),
                                checked = pureBlack,
                                enabled = useDarkTheme,
                                onCheckedChange = {
                                    pureBlack = it
                                    ThemeHelper.setPureBlack(context, it)
                                }
                            )
                            SettingsSwitchItem(
                                 title = stringResource(R.string.settings_theme_dynamic_color),
                                 subtitle = stringResource(R.string.settings_theme_dynamic_color_desc),
                                 checked = dynamicColor,
                                 onCheckedChange = {
                                     dynamicColor = it
                                     ThemeHelper.setDynamicColor(context, it)
                                 }
                             )
                             SettingsSwitchItem(
                                 title = stringResource(R.string.settings_predictive_back),
                                 subtitle = stringResource(R.string.settings_predictive_back_desc),
                                 checked = predictiveBackEnabled,
                                 onCheckedChange = {
                                     predictiveBackEnabled = it
                                     prefs.edit().putBoolean("predictive_back_enabled", it).apply()
                                 }
                             )

                        }
                        floatingLyricsPageIndex -> {
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
                AlertDialog(
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

        }
    }
}
