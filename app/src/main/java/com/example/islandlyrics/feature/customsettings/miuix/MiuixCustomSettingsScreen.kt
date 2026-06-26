package com.example.islandlyrics.feature.customsettings.miuix

import com.example.islandlyrics.ui.miuix.theme.rememberIslandLyricsMiuixThemeController
import com.example.islandlyrics.ui.miuix.effects.miuixPageScroll
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurTopAppBar
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackIcon
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
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandTextLimitConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.platform.XmsfBypassMode
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.runtime.service.LyricService
import com.example.islandlyrics.feature.main.HomeLyricPreviewDisplay
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.feature.customsettings.CustomSettingsAction
import com.example.islandlyrics.feature.customsettings.CustomSettingsViewModel
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.util.Locale
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference as SuperArrow
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as SuperDropdown
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private const val MIUIX_THEME_COLOR_SOURCE_PREF_KEY = "miuix_theme_color_source"

@Composable
@Suppress("UNUSED_PARAMETER")
fun MiuixCustomSettingsScreen(
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onShowLogs: () -> Unit,
    updateVersionText: String,
    updateBuildText: String,
    viewModel: CustomSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var floatingLyricsLabEnabled by remember(uiState.floatingLyricsLabEnabled) {
        mutableStateOf(uiState.floatingLyricsLabEnabled)
    }

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
    var followSystem by remember(uiState.followSystem) { mutableStateOf(uiState.followSystem) }
    var darkMode by remember(uiState.darkMode) { mutableStateOf(uiState.darkMode) }
    var iconStyle by remember(uiState.iconStyle) { mutableStateOf(uiState.iconStyle) }
    var customThemeColor by remember(uiState.customThemeColor) { mutableStateOf(Color(uiState.customThemeColor)) }

    var actionStyle by remember(uiState.actionStyle) { mutableStateOf(uiState.actionStyle) }
    var superIslandMediaButtonLayout by remember(uiState.superIslandMediaButtonLayout) {
        mutableStateOf(uiState.superIslandMediaButtonLayout)
    }
    var superIslandNotificationStyle by remember(uiState.superIslandNotificationStyle) {
        mutableStateOf(uiState.superIslandNotificationStyle)
    }
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
    var notificationClickStyle by remember(uiState.notificationClickStyle) {
        mutableStateOf(uiState.notificationClickStyle)
    }
    var dismissDelay by remember(uiState.dismissDelayMs) { mutableLongStateOf(uiState.dismissDelayMs) }

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
    var monetEnabled by remember(uiState.monetEnabled) { mutableStateOf(uiState.monetEnabled) }
    var customThemeGlobalTintEnabled by remember(uiState.customThemeGlobalTintEnabled) {
        mutableStateOf(uiState.customThemeGlobalTintEnabled)
    }
    var miuixThemeColorEditing by remember { mutableStateOf(false) }
    var miuixThemeColorSnapshot by remember { mutableStateOf(customThemeColor) }
    var miuixThemeColorSource by remember {
        mutableStateOf(
            prefs.getString(
                MIUIX_THEME_COLOR_SOURCE_PREF_KEY,
                if (customThemeGlobalTintEnabled) {
                    ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_CUSTOM
                } else {
                    ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT
                }
            ) ?: ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT
        )
    }
    var cardBlurEnabled by remember(uiState.cardBlurEnabled) { mutableStateOf(uiState.cardBlurEnabled) }
    var blockXmsfMode by remember { mutableStateOf(XmsfBypassMode.read(prefs)) }
    var blockXmsfCustomDurationMs by remember { mutableIntStateOf(XmsfBypassMode.readCustomDurationMs(prefs)) }
    var showHomeLyricPreviewDialog by remember { mutableStateOf(false) }

    val isLiveUpdateSupported = remember { RomUtils.isLiveUpdateSupported() }
    val isHyperOs = remember { RomUtils.isHyperOs() }
    val effectiveCapsuleRenderMode = if (!isLiveUpdateSupported && capsuleRenderMode == CapsuleRenderMode.LIVE_UPDATE) {
        CapsuleRenderMode.XIAOMI_SUPER_ISLAND
    } else {
        capsuleRenderMode
    }
    val homeLyricPreviewKeepOneText = stringResource(R.string.settings_home_lyric_preview_keep_one)
    val superIslandEnabled = effectiveCapsuleRenderMode == CapsuleRenderMode.XIAOMI_SUPER_ISLAND
    val islandStyleCapsuleEnabled = superIslandEnabled
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

    fun setHomeLyricPreviewDisplayMode(mode: String, checked: Boolean) {
        val nextModes = HomeLyricPreviewDisplay.toggledModes(homeLyricPreviewDisplayModes, mode, checked)
        if (nextModes == null) {
            Toast.makeText(
                context,
                homeLyricPreviewKeepOneText,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        homeLyricPreviewDisplayModes = nextModes
        viewModel.dispatch(CustomSettingsAction.SetHomeLyricPreviewDisplayModes(nextModes))
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

    val previewController = rememberIslandLyricsMiuixThemeController(
        dynamicColor = monetEnabled,
        followSystem = followSystem,
        forceDark = darkMode,
        customThemeColorArgb = customThemeColor.toArgb(),
        customThemeColorSource = miuixThemeColorSource,
        customThemeGlobalTintEnabled = customThemeGlobalTintEnabled
    )

    MiuixTheme(controller = previewController) {
        MiuixBlurScaffold(
            topBar = {
                MiuixBlurTopAppBar(
                    title = stringResource(R.string.page_title_personalization),
                    largeTitle = stringResource(R.string.page_title_personalization),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        androidx.compose.material3.IconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            MiuixBackIcon(contentDescription = "Back")
                        }
                    },
                    bottomContent = {
                        Column {
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
                        }
                    }
                )
            },
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .miuixPageScroll(scrollBehavior),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 12.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp
                    )
                ) {
                when (page) {
                        0 -> { // Capsule
                            item {
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
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                            item {
                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                    SuperSwitch(
                                        title = stringResource(R.string.settings_disable_scrolling),
                                        summary = stringResource(R.string.settings_disable_scrolling_desc),
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

                                    SuperDropdown(
                                        title = stringResource(R.string.settings_lyric_text_display_mode),
                                        summary = stringResource(R.string.settings_lyric_text_display_mode_desc),
                                        items = lyricTextModeLabels,
                                        selectedIndex = currentLyricTextModeIndex,
                                        onSelectedIndexChange = { index ->
                                            lyricTextDisplayMode = lyricTextModes[index]
                                            viewModel.dispatch(CustomSettingsAction.SetLyricTextDisplayMode(lyricTextDisplayMode))
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
                                                viewModel.dispatch(CustomSettingsAction.SetOneUiCapsuleColorMode(newMode))
                                            }
                                        )
                                    }
                                    if (isLiveUpdateSupported &&
                                        effectiveCapsuleRenderMode == CapsuleRenderMode.LIVE_UPDATE &&
                                        liveUpdateTextLimitsLabEnabled
                                    ) {
                                        MiuixSuperIslandTextLimitSlider(
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
                                    if (isHyperOs) {
                                        if (isLiveUpdateSupported) {
                                            val capsuleModeItems = buildList {
                                                add(CapsuleRenderMode.LIVE_UPDATE to stringResource(R.string.capsule_mode_live_update))
                                                add(CapsuleRenderMode.XIAOMI_SUPER_ISLAND to stringResource(R.string.capsule_mode_super_island))
                                            }
                                            val capsuleModes = capsuleModeItems.map { it.first }
                                            val capsuleModeLabels = capsuleModeItems.map { it.second }
                                            val currentCapsuleModeIndex =
                                                capsuleModes.indexOf(effectiveCapsuleRenderMode).takeIf { it >= 0 } ?: 0

                                            SuperDropdown(
                                                title = stringResource(R.string.settings_capsule_mode),
                                                items = capsuleModeLabels,
                                                selectedIndex = currentCapsuleModeIndex,
                                                onSelectedIndexChange = { index ->
                                                    setCapsuleRenderMode(capsuleModes[index])
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

                                            SuperDropdown(
                                                title = stringResource(R.string.settings_super_island_lyric_mode),
                                                entry = DropdownEntry(
                                                    items = lyricModeItems.map { (modeId, nameId, descId) ->
                                                        DropdownItem(
                                                            text = stringResource(nameId),
                                                            summary = stringResource(descId),
                                                            selected = superIslandLyricMode == modeId,
                                                            onClick = {
                                                                superIslandLyricMode = modeId
                                                                viewModel.dispatch(CustomSettingsAction.SetSuperIslandLyricMode(modeId))
                                                                if (modeId == "standard") {
                                                                    applySuperIslandScrollForce(force = false, restoreLegacyState = true)
                                                                }
                                                            }
                                                        )
                                                    }
                                                )
                                            )

                                            if (superIslandLyricMode == "full") {
                                                SuperSwitch(
                                                    title = stringResource(R.string.settings_super_island_full_lyric_show_left_cover),
                                                    summary = stringResource(R.string.settings_super_island_full_lyric_show_left_cover_desc),
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
                                                MiuixSuperIslandTextLimitSlider(
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
                                                    MiuixSuperIslandTextLimitSlider(
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

                                            SuperSwitch(
                                                title = stringResource(R.string.settings_super_island_colorize),
                                                summary = stringResource(R.string.settings_super_island_colorize_desc),
                                                checked = superIslandTextColorEnabled,
                                                onCheckedChange = {
                                                    if (!it && superIslandColorEditing) {
                                                        superIslandCustomColor = superIslandColorSnapshot
                                                        superIslandColorEditing = false
                                                    }
                                                    superIslandTextColorEnabled = it
                                                    progressColorEnabled = it
                                                    viewModel.dispatch(CustomSettingsAction.SetSuperIslandTextColorEnabled(it))
                                                    viewModel.dispatch(CustomSettingsAction.SetProgressColorEnabled(it))
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

                                                SuperDropdown(
                                                    title = stringResource(R.string.settings_super_island_color_source),
                                                    summary = stringResource(R.string.settings_super_island_color_source_desc),
                                                    items = colorSourceLabels,
                                                    selectedIndex = currentColorSourceIndex,
                                                    onSelectedIndexChange = { index ->
                                                        if (superIslandColorEditing) {
                                                            superIslandCustomColor = superIslandColorSnapshot
                                                            superIslandColorEditing = false
                                                        }
                                                        val newSource = colorSources[index]
                                                        superIslandColorSource = newSource
                                                        viewModel.dispatch(CustomSettingsAction.SetSuperIslandColorSource(newSource))
                                                    }
                                                )

                                                if (superIslandColorSource == SuperIslandColorSource.CUSTOM) {
                                                    MiuixEditableColorSection(
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
                                            }

                                            SuperSwitch(
                                                title = stringResource(R.string.settings_super_island_share),
                                                summary = stringResource(R.string.settings_super_island_share_desc),
                                                checked = superIslandShareEnabled,
                                                onCheckedChange = {
                                                    superIslandShareEnabled = it
                                                    viewModel.dispatch(CustomSettingsAction.SetSuperIslandShareEnabled(it))
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
                                                        viewModel.dispatch(CustomSettingsAction.SetSuperIslandShareFormat(newFormat))
                                                    }
                                                )
                                            }

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
                                            val bypassOptions = listOf(
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
                                            )

                                            SuperDropdown(
                                                title = stringResource(R.string.settings_block_xmsf_mode),
                                                summary = currentBlockXmsfModeSummary,
                                                entry = DropdownEntry(
                                                    items = bypassOptions.map { (mode, label, summary) ->
                                                        DropdownItem(
                                                            text = label,
                                                            summary = summary,
                                                            selected = blockXmsfMode == mode,
                                                            onClick = {
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
                                                )
                                            )
                                            if (blockXmsfMode == XmsfBypassMode.CUSTOM) {
                                                MiuixXmsfBypassDurationSlider(
                                                    title = stringResource(R.string.settings_block_xmsf_custom_duration),
                                                    summary = stringResource(R.string.settings_block_xmsf_custom_duration_desc),
                                                    warning = stringResource(R.string.settings_block_xmsf_custom_duration_warning),
                                                    value = blockXmsfCustomDurationMs,
                                                    onValueChange = { newDurationMs ->
                                                        blockXmsfCustomDurationMs = newDurationMs
                                                        viewModel.dispatch(CustomSettingsAction.SetXmsfCustomDurationMs(newDurationMs))
                                                    }
                                                )
                                            }

                                        }
                                    }
                                    if (isLiveUpdateSupported && !superIslandEnabled) {
                                        val iconStyles = buildList {
                                            add("disabled")
                                            add("advanced")
                                            add("album_art")
                                        }
                                        val iconStyleNames = buildList {
                                            add(stringResource(R.string.icon_style_classic))
                                            add(stringResource(R.string.icon_style_advanced))
                                            add(stringResource(R.string.icon_style_album_art))
                                        }
                                        val currentIconIndex = iconStyles.indexOf(iconStyle).takeIf { it >= 0 } ?: 0
                                        
                                        SuperDropdown(
                                            title = stringResource(R.string.settings_icon_style),
                                            items = iconStyleNames,
                                            selectedIndex = currentIconIndex,
                                            onSelectedIndexChange = { index ->
                                                val newStyle = iconStyles[index]
                                                iconStyle = newStyle
                                                viewModel.dispatch(CustomSettingsAction.SetDynamicIconStyle(newStyle))
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
                                    superIslandNotificationStyle = superIslandNotificationStyle,
                                    superIslandLyricMode = superIslandLyricMode,
                                    superIslandFullLyricShowLeftCover = superIslandFullLyricShowLeftCover
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
                                                viewModel.dispatch(CustomSettingsAction.SetProgressColorEnabled(it))
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
                                            viewModel.dispatch(CustomSettingsAction.SetNotificationActionsStyle(newStyle))
                                        }
                                    )

                                    if (actionStyle == "media_controls" && superIslandEnabled) {
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
                                                viewModel.dispatch(CustomSettingsAction.SetSuperIslandNotificationStyle(newStyle))
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
                                                    viewModel.dispatch(CustomSettingsAction.SetSuperIslandMediaButtonLayout(newLayout))
                                                }
                                            )
                                        }
                                    }

                                    val clickStyleItems = listOf(
                                        Triple("default",
                                            stringResource(R.string.settings_click_action_default),
                                            stringResource(R.string.settings_click_action_default_desc)),
                                        Triple("media_controls",
                                            stringResource(R.string.settings_click_action_media),
                                            stringResource(R.string.settings_click_action_media_desc)),
                                        Triple("open_playing_app",
                                            stringResource(R.string.settings_click_action_open_playing_app),
                                            stringResource(R.string.settings_click_action_open_playing_app_desc))
                                    )

                                    SuperDropdown(
                                        title = stringResource(R.string.settings_click_action_title),
                                        entry = DropdownEntry(
                                            items = clickStyleItems.map { (styleId, name, desc) ->
                                                DropdownItem(
                                                    text = name,
                                                    summary = desc,
                                                    selected = notificationClickStyle == styleId,
                                                    onClick = {
                                                        notificationClickStyle = styleId
                                                        viewModel.dispatch(CustomSettingsAction.SetNotificationClickStyle(styleId))
                                                    }
                                                )
                                            }
                                        )
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
                                            viewModel.dispatch(CustomSettingsAction.SetDismissDelay(newDelay))
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
                                            viewModel.dispatch(CustomSettingsAction.SetMiuixEnabled(newStyle))
                                            val restartIntent = Intent(context, MainActivity::class.java)
                                            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                            context.startActivity(restartIntent)
                                            (context as? Activity)?.finish()
                                        }
                                    )
                                    SuperArrow(
                                        title = stringResource(R.string.settings_home_lyric_preview_title),
                                        summary = stringResource(
                                            R.string.settings_home_lyric_preview_summary_fmt,
                                            homeLyricPreviewDisplayModes.labelForHomeLyricPreview()
                                        ),
                                        onClick = { showHomeLyricPreviewDialog = true }
                                    )
                                    SuperSwitch(
                                        title = stringResource(R.string.settings_theme_follow_system),
                                        checked = followSystem,
                                        onCheckedChange = {
                                            followSystem = it
                                            viewModel.dispatch(CustomSettingsAction.SetFollowSystem(it))
                                        }
                                    )
                                    SuperSwitch(
                                        title = stringResource(R.string.settings_theme_dark_mode),
                                        checked = darkMode,
                                        enabled = !followSystem,
                                        onCheckedChange = {
                                            darkMode = it
                                            viewModel.dispatch(CustomSettingsAction.SetDarkMode(it))
                                        }
                                    )
                                    SuperSwitch(
                                        title = stringResource(R.string.settings_predictive_back),
                                        summary = stringResource(R.string.settings_predictive_back_desc),
                                        checked = predictiveBackEnabled,
                                        onCheckedChange = {
                                            predictiveBackEnabled = it
                                            viewModel.dispatch(CustomSettingsAction.SetPredictiveBackEnabled(it))
                                        }
                                    )
                                    val predictiveBackModes = PredictiveBackAnimationMode.options
                                    val predictiveBackModeLabels = predictiveBackModes.map { stringResource(it.labelRes) }
                                    val currentPredictiveBackModeIndex =
                                        predictiveBackModes.indexOf(predictiveBackAnimationMode).takeIf { it >= 0 } ?: 0

                                    SuperDropdown(
                                        title = stringResource(R.string.settings_predictive_back_animation_mode),
                                        summary = stringResource(R.string.settings_predictive_back_animation_mode_desc),
                                        items = predictiveBackModeLabels,
                                        selectedIndex = currentPredictiveBackModeIndex,
                                        onSelectedIndexChange = { index ->
                                            val mode = predictiveBackModes[index]
                                            predictiveBackAnimationMode = mode
                                            viewModel.dispatch(CustomSettingsAction.SetPredictiveBackAnimationMode(mode))
                                        }
                                    )
                                    if (predictiveBackAnimationMode == PredictiveBackAnimationMode.Consistent) {
                                        val predictiveBackStyles = PredictiveBackAnimationStyle.options
                                        val predictiveBackStyleLabels = predictiveBackStyles.map { stringResource(it.labelRes) }
                                        val currentPredictiveBackStyleIndex =
                                            predictiveBackStyles.indexOf(predictiveBackAnimationStyle).takeIf { it >= 0 } ?: 0

                                        SuperDropdown(
                                            title = stringResource(R.string.settings_predictive_back_animation),
                                            summary = stringResource(R.string.settings_predictive_back_animation_desc),
                                            items = predictiveBackStyleLabels,
                                            selectedIndex = currentPredictiveBackStyleIndex,
                                            onSelectedIndexChange = { index ->
                                                val style = predictiveBackStyles[index]
                                                predictiveBackAnimationStyle = style
                                                viewModel.dispatch(CustomSettingsAction.SetPredictiveBackAnimationStyle(style))
                                            }
                                        )
                                    }
                                    SuperSwitch(
                                        title = stringResource(R.string.settings_theme_dynamic_color),
                                        summary = stringResource(R.string.settings_theme_dynamic_color_desc),
                                        checked = monetEnabled,
                                        onCheckedChange = { enabled ->
                                            monetEnabled = enabled
                                            viewModel.dispatch(CustomSettingsAction.SetDynamicColor(enabled))
                                        }
                                    )
                                    if (!monetEnabled) {
                                        val themeColorSources = listOf(
                                            ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT,
                                            ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_CUSTOM
                                        )
                                        val themeColorSourceLabels = listOf(
                                            stringResource(R.string.settings_theme_color_source_default),
                                            stringResource(R.string.settings_theme_color_source_custom)
                                        )
                                        val currentThemeColorSourceIndex =
                                            themeColorSources.indexOf(miuixThemeColorSource).takeIf { it >= 0 } ?: 0

                                        SuperDropdown(
                                            title = stringResource(R.string.settings_theme_color_source),
                                            items = themeColorSourceLabels,
                                            selectedIndex = currentThemeColorSourceIndex,
                                            onSelectedIndexChange = { index ->
                                                if (miuixThemeColorEditing) {
                                                    customThemeColor = miuixThemeColorSnapshot
                                                    miuixThemeColorEditing = false
                                                }
                                                miuixThemeColorSource = themeColorSources[index]
                                                viewModel.dispatch(CustomSettingsAction.SetMiuixThemeColorSource(miuixThemeColorSource))
                                            }
                                        )

                                        if (miuixThemeColorSource == ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_CUSTOM) {
                                            MiuixEditableColorSection(
                                                title = stringResource(R.string.settings_theme_custom_color),
                                                color = customThemeColor,
                                                isEditing = miuixThemeColorEditing,
                                                defaultActionText = stringResource(R.string.settings_theme_color_source_default),
                                                onStartEditing = {
                                                    miuixThemeColorSnapshot = customThemeColor
                                                    miuixThemeColorEditing = true
                                                },
                                                onColorChanged = { color ->
                                                    customThemeColor = color
                                                },
                                                onApply = {
                                                    viewModel.dispatch(CustomSettingsAction.SetMiuixThemeCustomColor(customThemeColor.toArgb()))
                                                    miuixThemeColorEditing = false
                                                },
                                                onCancel = {
                                                    customThemeColor = miuixThemeColorSnapshot
                                                    miuixThemeColorEditing = false
                                                },
                                                onUseDefault = {
                                                    customThemeColor = miuixThemeColorSnapshot
                                                    miuixThemeColorSource = ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT
                                                    viewModel.dispatch(CustomSettingsAction.SetMiuixThemeColorSource(ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT))
                                                    miuixThemeColorEditing = false
                                                }
                                            )
                                            SuperSwitch(
                                                title = stringResource(R.string.settings_theme_custom_color_global_tint),
                                                summary = stringResource(R.string.settings_theme_custom_color_global_tint_desc),
                                                checked = customThemeGlobalTintEnabled,
                                                onCheckedChange = { enabled ->
                                                    customThemeGlobalTintEnabled = enabled
                                                    viewModel.dispatch(CustomSettingsAction.SetMiuixThemeGlobalTintEnabled(enabled))
                                                }
                                            )
                                        }
                                    }
                                    SuperSwitch(
                                        title = stringResource(R.string.settings_card_blur),
                                        summary = stringResource(R.string.settings_card_blur_desc),
                                        checked = cardBlurEnabled,
                                        onCheckedChange = {
                                            cardBlurEnabled = it
                                            viewModel.dispatch(CustomSettingsAction.SetCardBlurEnabled(it))
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
        if (showHomeLyricPreviewDialog) {
            MiuixBlurDialog(
                show = true,
                title = stringResource(R.string.settings_home_lyric_preview_title),
                onDismissRequest = { showHomeLyricPreviewDialog = false }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CheckboxPreference(
                        title = stringResource(R.string.lyric_text_display_mode_lyric),
                        checked = HomeLyricPreviewDisplay.LYRIC in homeLyricPreviewDisplayModes,
                        onCheckedChange = {
                            setHomeLyricPreviewDisplayMode(HomeLyricPreviewDisplay.LYRIC, it)
                        }
                    )
                    CheckboxPreference(
                        title = stringResource(R.string.lyric_text_display_mode_translation),
                        checked = HomeLyricPreviewDisplay.TRANSLATION in homeLyricPreviewDisplayModes,
                        onCheckedChange = {
                            setHomeLyricPreviewDisplayMode(HomeLyricPreviewDisplay.TRANSLATION, it)
                        }
                    )
                    CheckboxPreference(
                        title = stringResource(R.string.lyric_text_display_mode_romanization),
                        checked = HomeLyricPreviewDisplay.ROMANIZATION in homeLyricPreviewDisplayModes,
                        onCheckedChange = {
                            setHomeLyricPreviewDisplayMode(HomeLyricPreviewDisplay.ROMANIZATION, it)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        text = stringResource(R.string.backup_dialog_confirm),
                        onClick = { showHomeLyricPreviewDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}

@Composable
private fun MiuixSuperIslandTextLimitSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val clampedValue = value.coerceIn(valueRange)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = "$title: ${formatSuperIslandTextLimit(clampedValue)}",
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 15.dp.value.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Slider(
            value = clampedValue,
            onValueChange = { raw ->
                val stepped = (kotlin.math.round(raw * 2f) / 2f).coerceIn(valueRange)
                onValueChange(stepped)
            },
            valueRange = valueRange,
            steps = ((valueRange.endInclusive - valueRange.start) / 0.5f).toInt() - 1,
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            showKeyPoints = true
        )
    }
}

@Composable
private fun MiuixXmsfBypassDurationSlider(
    title: String,
    summary: String,
    warning: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val clampedValue = value.coerceIn(
        XmsfBypassMode.MIN_CUSTOM_DURATION_MS,
        XmsfBypassMode.MAX_CUSTOM_DURATION_MS
    )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = "$title: ${stringResource(R.string.settings_block_xmsf_duration_value, clampedValue)}",
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 15.dp.value.sp
        )
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.dp.value.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Slider(
            value = clampedValue.toFloat(),
            onValueChange = { raw ->
                onValueChange(snapXmsfBypassDuration(raw.roundToInt()))
            },
            valueRange = XmsfBypassMode.MIN_CUSTOM_DURATION_MS.toFloat()..XmsfBypassMode.MAX_CUSTOM_DURATION_MS.toFloat(),
            steps = ((XmsfBypassMode.MAX_CUSTOM_DURATION_MS - XmsfBypassMode.MIN_CUSTOM_DURATION_MS) /
                XmsfBypassMode.CUSTOM_DURATION_STEP_MS) - 1,
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            showKeyPoints = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = warning,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.dp.value.sp
        )
    }
}

private fun formatSuperIslandTextLimit(value: Float): String {
    return if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        "%.1f".format(Locale.US, value)
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
private fun Set<String>.labelForHomeLyricPreview(): String {
    val labels = buildList {
        if (HomeLyricPreviewDisplay.LYRIC in this@labelForHomeLyricPreview) {
            add(stringResource(R.string.lyric_text_display_mode_lyric))
        }
        if (HomeLyricPreviewDisplay.TRANSLATION in this@labelForHomeLyricPreview) {
            add(stringResource(R.string.lyric_text_display_mode_translation))
        }
        if (HomeLyricPreviewDisplay.ROMANIZATION in this@labelForHomeLyricPreview) {
            add(stringResource(R.string.lyric_text_display_mode_romanization))
        }
    }
    return labels.ifEmpty {
        listOf(stringResource(R.string.lyric_text_display_mode_lyric))
    }.joinToString(" / ")
}


