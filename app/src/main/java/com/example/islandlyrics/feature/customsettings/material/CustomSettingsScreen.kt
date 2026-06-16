package com.example.islandlyrics.feature.customsettings.material

import android.app.Activity
import com.example.islandlyrics.ui.common.NotificationPreview
import com.example.islandlyrics.ui.common.CapsulePreview
import com.example.islandlyrics.ui.common.CapsuleRenderMode
import com.example.islandlyrics.ui.common.LyricTextDisplayMode
import com.example.islandlyrics.ui.common.OneUiCapsuleColorMode
import com.example.islandlyrics.ui.common.SuperIslandColorSource
import com.example.islandlyrics.ui.common.SuperIslandTextLimitConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.platform.XmsfBypassMode
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.service.LyricService
import com.example.islandlyrics.ui.theme.material.AppTheme
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.feature.settings.material.LanguageSelectionDialog
import com.example.islandlyrics.feature.settings.material.SettingsSwitchItem
import com.example.islandlyrics.feature.settings.material.SettingsTextItem
import com.example.islandlyrics.feature.settings.material.SettingsCard
import com.example.islandlyrics.feature.settings.material.SettingsCardDivider
import com.example.islandlyrics.feature.main.MainActivity
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.provider.Settings
import android.util.TypedValue
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
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

    // Theme State
    var followSystem by remember { mutableStateOf(ThemeHelper.getFollowSystem(context)) }
    var darkMode by remember { mutableStateOf(ThemeHelper.getDarkMode(context)) }
    var pureBlack by remember { mutableStateOf(ThemeHelper.getMaterialPureBlack(context)) }
    var dynamicColor by remember { mutableStateOf(ThemeHelper.getMaterialDynamicColor(context)) }
    var materialThemeColorSource by remember { mutableStateOf(ThemeHelper.getMaterialThemeColorSource(context)) }
    var customThemeColor by remember { mutableStateOf(Color(ThemeHelper.getMaterialCustomColor(context))) }
    var materialThemeColorEditing by remember { mutableStateOf(false) }
    var materialThemeColorSnapshot by remember { mutableStateOf(customThemeColor) }
    var iconStyle by remember { mutableStateOf(prefs.getString("dynamic_icon_style", "disabled") ?: "disabled") }
    
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
    var actionStyle by remember { mutableStateOf(prefs.getString("notification_actions_style", "disabled") ?: "disabled") }
    var superIslandMediaButtonLayout by remember { mutableStateOf(prefs.getString("super_island_media_button_layout", "two_button") ?: "two_button") }
    var superIslandNotificationStyle by remember { mutableStateOf(LabFeatureManager.sanitizeSuperIslandNotificationStyle(context)) }
    var superIslandAdvancedStyleLabEnabled by remember { mutableStateOf(LabFeatureManager.isSuperIslandAdvancedStyleEnabled(prefs)) }
    var superIslandTextLimitsLabEnabled by remember { mutableStateOf(LabFeatureManager.isSuperIslandTextLimitsEnabled(prefs)) }
    var superIslandRelaxedTextLimitsLabEnabled by remember {
        mutableStateOf(LabFeatureManager.isSuperIslandRelaxedTextLimitsEnabled(prefs))
    }
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
    var disableScrolling by remember { mutableStateOf(prefs.getBoolean("disable_lyric_scrolling", false)) }
    var lyricTextDisplayMode by remember { mutableStateOf(LyricTextDisplayMode.read(prefs)) }
    var oneuiCapsuleColorMode by remember { mutableStateOf(OneUiCapsuleColorMode.read(prefs)) }

    var capsuleRenderMode by remember { mutableStateOf(CapsuleRenderMode.read(prefs)) }
    var colorOsFluidCloudLabEnabled by remember { mutableStateOf(LabFeatureManager.isColorOsFluidCloudEnabled(prefs)) }
    var superIslandLyricMode by remember { mutableStateOf(prefs.getString("super_island_lyric_mode", "standard") ?: "standard") }
    var superIslandFullLyricShowLeftCover by remember { mutableStateOf(prefs.getBoolean("super_island_full_lyric_show_left_cover", true)) }
    var superIslandTextColorEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_text_color_enabled", false)) }
    var superIslandColorSource by remember { mutableStateOf(SuperIslandColorSource.read(prefs)) }
    var superIslandCustomColor by remember { mutableStateOf(Color(SuperIslandColorSource.readCustomColor(prefs))) }
    var superIslandRightTextChars by remember {
        mutableFloatStateOf(SuperIslandTextLimitConfig.rightChars(prefs, superIslandRelaxedTextLimitsLabEnabled))
    }
    var superIslandLeftWithCoverTextChars by remember {
        mutableFloatStateOf(SuperIslandTextLimitConfig.leftChars(prefs, showLeftCover = true, superIslandRelaxedTextLimitsLabEnabled))
    }
    var superIslandLeftNoCoverTextChars by remember {
        mutableFloatStateOf(SuperIslandTextLimitConfig.leftChars(prefs, showLeftCover = false, superIslandRelaxedTextLimitsLabEnabled))
    }

    var superIslandShareEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_share_enabled", true)) }
    var superIslandShareFormat by remember { mutableStateOf(prefs.getString("super_island_share_format", "format_1") ?: "format_1") }
    var miuixEnabled by remember { mutableStateOf(prefs.getBoolean("ui_use_miuix", true)) }
    var predictiveBackEnabled by remember { mutableStateOf(prefs.getBoolean("predictive_back_enabled", true)) }


    // Dialog State for UI Style
    var showUiStyleDropdown by remember { mutableStateOf(false) }

    // Check for HyperOS 3.0.300+
    val isLiveUpdateSupported = remember { RomUtils.isLiveUpdateSupported() }
    val isHyperOs = remember { RomUtils.isHyperOs() }
    val effectiveCapsuleRenderMode = if (!isLiveUpdateSupported && capsuleRenderMode == CapsuleRenderMode.LIVE_UPDATE) {
        CapsuleRenderMode.XIAOMI_SUPER_ISLAND
    } else {
        capsuleRenderMode
    }
    val superIslandEnabled = effectiveCapsuleRenderMode == CapsuleRenderMode.XIAOMI_SUPER_ISLAND
    val colorOsFluidCloudEnabled = effectiveCapsuleRenderMode == CapsuleRenderMode.COLOROS_FLUID_CLOUD
    val forceDisableScrollingForSuperIslandLyricMode =
        isHyperOs && superIslandEnabled && superIslandLyricMode == "full"

    fun applySuperIslandScrollForce(force: Boolean, restoreLegacyState: Boolean = false) {
        val forcedKey = "super_island_lyric_mode_forced_disable_scrolling"
        val backupKey = "disable_lyric_scrolling_before_super_island_lyric_mode"
        val legacyForcedKey = "full_super_island_forced_disable_scrolling"
        val legacyBackupKey = "disable_lyric_scrolling_before_full_super_island"
        val wasForced = prefs.getBoolean(forcedKey, false) || prefs.getBoolean(legacyForcedKey, false)
        prefs.edit {
            if (force) {
                if (!wasForced) {
                    putBoolean(backupKey, disableScrolling)
                    putBoolean(forcedKey, true)
                }
                disableScrolling = true
                putBoolean("disable_lyric_scrolling", true)
            } else if (wasForced) {
                val restoredValue = if (prefs.contains(backupKey)) {
                    prefs.getBoolean(backupKey, false)
                } else {
                    prefs.getBoolean(legacyBackupKey, false)
                }
                disableScrolling = restoredValue
                putBoolean("disable_lyric_scrolling", restoredValue)
                remove(backupKey)
                remove(forcedKey)
                remove(legacyBackupKey)
                remove(legacyForcedKey)
            } else if (restoreLegacyState && disableScrolling) {
                disableScrolling = false
                putBoolean("disable_lyric_scrolling", false)
            }
        }
    }

    fun setCapsuleRenderMode(mode: CapsuleRenderMode) {
        if (capsuleRenderMode == mode) return

        capsuleRenderMode = mode
        CapsuleRenderMode.write(prefs, mode)

        if (mode != CapsuleRenderMode.LIVE_UPDATE && actionStyle == "miplay") {
            actionStyle = "disabled"
            prefs.edit { putString("notification_actions_style", "disabled") }
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
        colorOsFluidCloudLabEnabled = LabFeatureManager.isColorOsFluidCloudEnabled(prefs)
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
                prefs.edit { putString("dynamic_icon_style", "disabled") }
            }
            if (actionStyle == "miplay") {
                actionStyle = "disabled"
                prefs.edit { putString("notification_actions_style", "disabled") }
            }
        } else if (!isHyperOs && iconStyle == "advanced") {
            // Advanced style is HyperOS-only; reset to classic on other ROMs
            iconStyle = "disabled"
            prefs.edit { putString("dynamic_icon_style", "disabled") }
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
                    // Tab switcher matching LogViewer FilterChip corner radius
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val selected = pagerState.currentPage == index
                            if (selected) {
                                Button(
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.small,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.small,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
                    when (page) {
                        0 -> { // Capsule (Moved from 1)
                            val previewIconStyle = if (superIslandEnabled) "advanced" else iconStyle
                            CapsulePreview(
                                dynamicIconEnabled = if (superIslandEnabled) true else iconStyle != "disabled",
                                iconStyle = previewIconStyle,
                                oneuiCapsuleColorMode = oneuiCapsuleColorMode,
                                superIslandEnabled = superIslandEnabled,
                                superIslandLyricMode = superIslandLyricMode,
                                superIslandFullLyricShowLeftCover = superIslandFullLyricShowLeftCover,
                                superIslandTextColorEnabled = superIslandTextColorEnabled,
                                superIslandColorSource = superIslandColorSource,
                                superIslandCustomColor = superIslandCustomColor
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
                                    prefs.edit { putBoolean("disable_lyric_scrolling", it) }
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
                                } // end SettingsCard (OneUI)
                            }

                            if (isHyperOs || colorOsFluidCloudLabEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                SettingsCard {
                                if (isLiveUpdateSupported || colorOsFluidCloudLabEnabled) {
                                    val capsuleModeItems = buildList {
                                        if (isLiveUpdateSupported) {
                                            add(CapsuleRenderMode.LIVE_UPDATE to stringResource(R.string.capsule_mode_live_update))
                                        }
                                        if (isHyperOs) {
                                            add(CapsuleRenderMode.XIAOMI_SUPER_ISLAND to stringResource(R.string.capsule_mode_super_island))
                                        }
                                        if (colorOsFluidCloudLabEnabled) {
                                            add(CapsuleRenderMode.COLOROS_FLUID_CLOUD to stringResource(R.string.capsule_mode_fluid_cloud))
                                        }
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
                                            DropdownMenu(
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

                                if (isLiveUpdateSupported && !superIslandEnabled && !colorOsFluidCloudEnabled) {
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
                                            DropdownMenu(
                                                expanded = showIconStyleDropdown,
                                                onDismissRequest = { showIconStyleDropdown = false }
                                            ) {
                                                val styles = buildList {
                                                    add("disabled" to R.string.icon_style_classic)
                                                    if (isHyperOs) add("advanced" to R.string.icon_style_advanced)
                                                    add("album_art" to R.string.icon_style_album_art)
                                                }
                                                styles.forEach { (styleId, nameId) ->
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(nameId)) },
                                                        onClick = {
                                                            iconStyle = styleId
                                                            prefs.edit { putString("dynamic_icon_style", styleId) }
                                                            showIconStyleDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (superIslandEnabled) {
                                    val lyricModeItems = buildList {
                                        add(Triple("standard", R.string.super_island_lyric_mode_standard, R.string.super_island_lyric_mode_standard_desc))
                                        add(Triple("full", R.string.super_island_lyric_mode_full, R.string.super_island_lyric_mode_full_desc))
                                    }
                                    val currentLyricModeItem = lyricModeItems.firstOrNull { it.first == superIslandLyricMode }
                                        ?: lyricModeItems.first()
                                    if (currentLyricModeItem.first != superIslandLyricMode) {
                                        superIslandLyricMode = currentLyricModeItem.first
                                        prefs.edit { putString("super_island_lyric_mode", currentLyricModeItem.first) }
                                    }
                                    val lyricModeDisplay = stringResource(currentLyricModeItem.second)
                                    val lyricModeSubtitle = stringResource(currentLyricModeItem.third)

                                    fun setSuperIslandLyricMode(newMode: String) {
                                        superIslandLyricMode = newMode
                                        prefs.edit { putString("super_island_lyric_mode", newMode) }
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
                                            DropdownMenu(
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
                                                prefs.edit { putBoolean("super_island_full_lyric_show_left_cover", it) }
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
                                                prefs.edit { putFloat(SuperIslandTextLimitConfig.KEY_RIGHT_CHARS, value) }
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
                                                    prefs.edit { putFloat(leftKey, value) }
                                                }
                                            )
                                        }
                                    }

                                    SettingsCardDivider()
                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_super_island_colorize),
                                        subtitle = stringResource(R.string.settings_super_island_colorize_desc),
                                        checked = superIslandTextColorEnabled,
                                        onCheckedChange = {
                                            superIslandTextColorEnabled = it
                                            progressColorEnabled = it
                                            prefs.edit { putBoolean("super_island_text_color_enabled", it) }
                                            prefs.edit { putBoolean("progress_bar_color_enabled", it) }
                                        }
                                    )

                                    if (superIslandTextColorEnabled) {
                                        val colorSources = SuperIslandColorSource.values
                                        val colorSourceLabels = listOf(
                                            stringResource(R.string.settings_super_island_color_source_album_art),
                                            stringResource(R.string.settings_super_island_color_source_custom)
                                        )
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
                                                DropdownMenu(
                                                    expanded = showSuperIslandColorSourceDropdown,
                                                    onDismissRequest = { showSuperIslandColorSourceDropdown = false }
                                                ) {
                                                    colorSourceLabels.forEachIndexed { index, label ->
                                                        DropdownMenuItem(
                                                            text = { Text(label) },
                                                            onClick = {
                                                                val newSource = colorSources[index]
                                                                superIslandColorSource = newSource
                                                                SuperIslandColorSource.write(prefs, newSource)
                                                                showSuperIslandColorSourceDropdown = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (superIslandColorSource == SuperIslandColorSource.CUSTOM) {
                                            SettingsCardDivider()
                                            MaterialInlineColorSection(
                                                title = stringResource(R.string.settings_super_island_custom_color),
                                                color = superIslandCustomColor,
                                                onColorChanged = { color ->
                                                    superIslandCustomColor = color
                                                    SuperIslandColorSource.writeCustomColor(prefs, color.toArgb())
                                                }
                                            )
                                        }
                                    }

                                    SettingsCardDivider()
                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_super_island_share),
                                        subtitle = stringResource(R.string.settings_super_island_share_desc),
                                        checked = superIslandShareEnabled,
                                        onCheckedChange = {
                                            superIslandShareEnabled = it
                                            prefs.edit { putBoolean("super_island_share_enabled", it) }
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
                                                                prefs.edit { putString("super_island_share_format", formatId) }
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
                                    val currentBlockXmsfDurationText = stringResource(
                                        R.string.settings_block_xmsf_duration_value,
                                        blockXmsfCustomDurationMs
                                    )
                                    val currentBlockXmsfModeSummary = when (blockXmsfMode) {
                                        XmsfBypassMode.DISABLED -> stringResource(R.string.settings_block_xmsf_mode_disabled_desc)
                                        XmsfBypassMode.STANDARD -> stringResource(R.string.settings_block_xmsf_mode_standard_desc)
                                        XmsfBypassMode.CUSTOM -> stringResource(
                                            R.string.settings_block_xmsf_mode_custom_current_desc,
                                            currentBlockXmsfDurationText
                                        )
                                        XmsfBypassMode.AGGRESSIVE -> stringResource(R.string.settings_block_xmsf_mode_aggressive_desc)
                                    }
                                    SettingsCardDivider()
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        SettingsTextItem(
                                            title = stringResource(R.string.settings_block_xmsf_mode),
                                            subtitle = currentBlockXmsfModeSummary,
                                            value = when (blockXmsfMode) {
                                                XmsfBypassMode.STANDARD -> stringResource(R.string.settings_block_xmsf_mode_standard)
                                                XmsfBypassMode.CUSTOM -> stringResource(R.string.settings_block_xmsf_mode_custom)
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
                                                                XmsfBypassMode.write(prefs, mode)
                                                            } else {
                                                                scope.launch {
                                                                    try {
                                                                        com.example.islandlyrics.integration.shizuku.requireShizukuPermissionGranted {
                                                                            blockXmsfMode = mode
                                                                            XmsfBypassMode.write(prefs, mode)
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
                                                XmsfBypassMode.writeCustomDurationMs(prefs, newDurationMs)
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
                        1 -> { // Notification
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

                            SettingsCard {
                            if (actionStyle == "disabled" || (actionStyle == "media_controls" && superIslandNotificationStyle == "advanced_beta")) {
                                 SettingsSwitchItem(
                                    title = stringResource(R.string.settings_progress_color),
                                    subtitle = stringResource(R.string.settings_progress_color_desc),
                                    checked = progressColorEnabled,
                                    onCheckedChange = {
                                        progressColorEnabled = it
                                        prefs.edit { putBoolean("progress_bar_color_enabled", it) }
                                    }
                                )
                                SettingsCardDivider()
                            }

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
                                            if (styleId == "miplay") isLiveUpdateSupported && !superIslandEnabled && !colorOsFluidCloudEnabled else true
                                        }
                                        styles.forEach { (styleId, nameId) ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(nameId)) },
                                                onClick = {
                                                    actionStyle = styleId
                                                    prefs.edit { putString("notification_actions_style", styleId) }
                                                    showActionStyleDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (actionStyle == "media_controls" && superIslandEnabled) {
                                val notificationStyleDisplayName = when (superIslandNotificationStyle) {
                                    "advanced_beta" -> stringResource(R.string.super_island_notification_style_advanced_beta)
                                    else -> stringResource(R.string.super_island_notification_style_standard)
                                }
                                SettingsCardDivider()
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
                                                        prefs.edit { putString("super_island_notification_style", styleId) }
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
                                    SettingsCardDivider()
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
                                                            prefs.edit { putString("super_island_media_button_layout", layoutId) }
                                                            showSuperIslandMediaButtonLayoutDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            SettingsCardDivider()
                            val clickStyleDisplay = when (notificationClickStyle) {
                                "media_controls" -> stringResource(R.string.settings_click_action_media)
                                "open_playing_app" -> stringResource(R.string.settings_click_action_open_playing_app)
                                else -> stringResource(R.string.settings_click_action_default)
                            }
                            val clickStyleSubtitle = when (notificationClickStyle) {
                                "media_controls" -> stringResource(R.string.settings_click_action_media_desc)
                                "open_playing_app" -> stringResource(R.string.settings_click_action_open_playing_app_desc)
                                else -> stringResource(R.string.settings_click_action_default_desc)
                            }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                SettingsTextItem(
                                    title = stringResource(R.string.settings_click_action_title),
                                    subtitle = clickStyleSubtitle,
                                    value = clickStyleDisplay,
                                    onClick = { showNotificationClickDropdown = true }
                                )
                                Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                                    DropdownMenu(
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
                                                    notificationClickStyle = styleId
                                                    prefs.edit { putString("notification_click_style", styleId) }
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
                                                    prefs.edit { putLong("notification_dismiss_delay", delay) }
                                                    showDismissDelayDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            } // end SettingsCard (notification)
                        }
                        2 -> { // App UI
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
                                                        prefs.edit { putBoolean("ui_use_miuix", false) }
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
                                                        prefs.edit { putBoolean("ui_use_miuix", true) }
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
                                SettingsSwitchItem(
                                    title = stringResource(R.string.settings_theme_follow_system),
                                    checked = followSystem,
                                    onCheckedChange = {
                                        followSystem = it
                                        ThemeHelper.setFollowSystem(context, it)
                                    }
                                )
                                SettingsCardDivider()
                                SettingsSwitchItem(
                                    title = stringResource(R.string.settings_theme_dark_mode),
                                    checked = darkMode,
                                    enabled = !followSystem,
                                    onCheckedChange = {
                                        darkMode = it
                                        ThemeHelper.setDarkMode(context, it)
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
                                        ThemeHelper.setPureBlack(context, it)
                                    }
                                )
                                SettingsCardDivider()
                                SettingsSwitchItem(
                                    title = stringResource(R.string.settings_theme_dynamic_color),
                                    subtitle = stringResource(R.string.settings_theme_dynamic_color_desc),
                                    checked = dynamicColor,
                                    onCheckedChange = {
                                        dynamicColor = it
                                        ThemeHelper.setDynamicColor(context, it)
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
                                            DropdownMenu(
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
                                                            ThemeHelper.setMaterialThemeColorSource(context, materialThemeColorSource)
                                                            showThemeColorSourceDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (materialThemeColorSource == ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_CUSTOM) {
                                        SettingsCardDivider()
                                        MaterialThemeColorSection(
                                            color = customThemeColor,
                                            isEditing = materialThemeColorEditing,
                                            onStartEditing = {
                                                materialThemeColorSnapshot = customThemeColor
                                                materialThemeColorEditing = true
                                            },
                                            onColorChanged = { color ->
                                                customThemeColor = color
                                            },
                                            onApply = {
                                                ThemeHelper.setMaterialCustomColor(context, customThemeColor.toArgb())
                                                materialThemeColorEditing = false
                                            },
                                            onCancel = {
                                                customThemeColor = materialThemeColorSnapshot
                                                materialThemeColorEditing = false
                                            },
                                            onUseDefault = {
                                                customThemeColor = materialThemeColorSnapshot
                                                materialThemeColorSource = ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT
                                                ThemeHelper.setMaterialThemeColorSource(
                                                    context,
                                                    ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT
                                                )
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
                                        prefs.edit { putBoolean("predictive_back_enabled", it) }
                                    }
                                )
                            }
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

@Composable
private fun MaterialThemeColorSection(
    color: Color,
    isEditing: Boolean,
    onStartEditing: () -> Unit,
    onColorChanged: (Color) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onUseDefault: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_theme_custom_color),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(color)
        )
        if (isEditing) {
            MaterialLiteralColorPalette(
                color = color,
                onColorChanged = onColorChanged
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.dialog_btn_cancel))
                }
                OutlinedButton(onClick = onUseDefault) {
                    Text(stringResource(R.string.settings_theme_color_source_default))
                }
                FilledTonalButton(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.apply))
                }
            }
        } else {
            OutlinedButton(
                onClick = onStartEditing,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.parser_edit))
            }
        }
        Text(
            text = stringResource(
                if (isEditing) {
                    R.string.settings_theme_color_editing_hint
                } else {
                    R.string.settings_theme_color_edit_hint
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MaterialInlineColorSection(
    title: String,
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        MaterialLiteralColorPalette(
            color = color,
            onColorChanged = onColorChanged
        )
    }
}

@Composable
private fun MaterialLiteralColorPalette(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var paletteState by remember(color) { mutableStateOf(MaterialPaletteState.fromColor(color)) }

    LaunchedEffect(color.toArgb()) {
        paletteState = MaterialPaletteState.fromColor(color)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(paletteState.color)
        )

        MaterialColorGrid(
            hue = paletteState.hue,
            saturation = paletteState.saturation,
            brightness = paletteState.brightness,
            onStateChange = { hue, saturation, brightness ->
                paletteState = paletteState.copy(hue = hue, saturation = saturation, brightness = brightness)
                onColorChanged(paletteState.color)
            }
        )

        MaterialAlphaSlider(
            hue = paletteState.hue,
            saturation = paletteState.saturation,
            brightness = paletteState.brightness,
            alpha = paletteState.alpha,
            onAlphaChange = { alpha ->
                paletteState = paletteState.copy(alpha = alpha)
                onColorChanged(paletteState.color)
            }
        )
    }
}

@Composable
private fun MaterialColorGrid(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onStateChange: (Float, Float, Float) -> Unit
) {
    val columns = remember {
        listOf(0f, 28f, 52f, 86f, 120f, 148f, 180f, 210f, 242f, 272f, 300f, 330f)
    }
    val rows = remember { listOf(0.96f, 0.92f, 0.99f, 0.90f, 0.72f, 0.50f, 0.24f) }
    val radius = 28.dp
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    onStateChange(
                        (offset.x / width).coerceIn(0f, 1f) * 360f,
                        ((offset.y / height).coerceIn(0f, 1f) * 0.95f).coerceIn(0.02f, 0.95f),
                        (1f - (offset.y / height).coerceIn(0f, 1f) * 0.85f).coerceIn(0.16f, 1f)
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    onStateChange(
                        (change.position.x / width).coerceIn(0f, 1f) * 360f,
                        ((change.position.y / height).coerceIn(0f, 1f) * 0.95f).coerceIn(0.02f, 0.95f),
                        (1f - (change.position.y / height).coerceIn(0f, 1f) * 0.85f).coerceIn(0.16f, 1f)
                    )
                }
            }
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellWidth = size.width / columns.size
            val cellHeight = size.height / rows.size
            rows.forEachIndexed { rowIndex, rowSaturation ->
                columns.forEachIndexed { columnIndex, cellHue ->
                    val cellValue = when (rowIndex) {
                        0 -> 0.97f
                        1 -> 0.98f
                        2 -> 1f
                        3 -> 0.90f
                        4 -> 0.72f
                        5 -> 0.48f
                        else -> 0.24f
                    }
                    val color = hsvColor(
                        hue = cellHue,
                        saturation = if (rowIndex < 2) rowSaturation * 0.22f else rowSaturation,
                        value = cellValue
                    )
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(columnIndex * cellWidth, rowIndex * cellHeight),
                        size = Size(cellWidth + 1f, cellHeight + 1f),
                        cornerRadius = CornerRadius(
                            x = if (columnIndex == 0 || columnIndex == columns.lastIndex) 20f else 0f,
                            y = if (rowIndex == 0 || rowIndex == rows.lastIndex) 20f else 0f
                        )
                    )
                }
            }
        }

        val indicatorRadiusPx = with(density) { 16.dp.roundToPx() }
        val indicatorOffset = remember(hue, saturation, brightness, widthPx, heightPx) {
            IntOffset(
                x = ((hue / 360f) * widthPx).roundToInt() - indicatorRadiusPx,
                y = (((saturation / 0.95f).coerceIn(0f, 1f)) * heightPx).roundToInt() - indicatorRadiusPx
            )
        }
        Box(
            modifier = Modifier
                .offset { indicatorOffset }
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.94f))
                .border(1.dp, Color.Black.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(hsvColor(hue, saturation, brightness))
            )
        }
    }
}

@Composable
private fun MaterialAlphaSlider(
    hue: Float,
    saturation: Float,
    brightness: Float,
    alpha: Float,
    onAlphaChange: (Float) -> Unit
) {
    val previewColor = hsvColor(hue, saturation, brightness, alpha = alpha)
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    onAlphaChange((offset.x / width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    onAlphaChange((change.position.x / width).coerceIn(0f, 1f))
                }
            }
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val checkSize = size.height / 4f
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = 0f
                var column = row % 2
                while (x < size.width) {
                    drawRect(
                        color = if (column % 2 == 0) Color(0xFFD7DCE5) else Color(0xFFBFC7D4),
                        topLeft = Offset(x, y),
                        size = Size(checkSize, checkSize)
                    )
                    x += checkSize
                    column++
                }
                y += checkSize
                row++
            }
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        hsvColor(hue, saturation, brightness, alpha = 0f),
                        hsvColor(hue, saturation, brightness, alpha = 1f)
                    )
                ),
                size = size,
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
            )
        }

        val thumbRadiusPx = with(density) { 16.dp.roundToPx() }
        val thumbTopPx = with(density) { (-10).dp.roundToPx() }
        val thumbOffset = remember(alpha, widthPx) {
            IntOffset(
                x = (alpha.coerceIn(0f, 1f) * widthPx).roundToInt() - thumbRadiusPx,
                y = thumbTopPx
            )
        }
        Box(
            modifier = Modifier
                .offset { thumbOffset }
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color.Black.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .border(
                        width = if (previewColor.luminance() > 0.85f) 1.dp else 0.dp,
                        color = Color.Black.copy(alpha = 0.08f),
                        shape = CircleShape
                    )
            )
        }
    }
}

private data class MaterialPaletteState(
    val hue: Float,
    val saturation: Float,
    val brightness: Float,
    val alpha: Float
) {
    val color: Color
        get() = hsvColor(hue, saturation, brightness, alpha)

    companion object {
        fun fromColor(color: Color): MaterialPaletteState {
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(color.copy(alpha = 1f).toArgb(), hsv)
            return MaterialPaletteState(
                hue = hsv[0],
                saturation = hsv[1].coerceIn(0.02f, 0.95f),
                brightness = hsv[2].coerceIn(0.16f, 1f),
                alpha = color.alpha.coerceIn(0f, 1f)
            )
        }
    }
}

private fun hsvColor(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Color {
    return Color(
        AndroidColor.HSVToColor(
            (alpha.coerceIn(0f, 1f) * 255f).roundToInt(),
            floatArrayOf(
                ((hue % 360f) + 360f) % 360f,
                saturation.coerceIn(0f, 1f),
                value.coerceIn(0f, 1f)
            )
        )
    )
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
