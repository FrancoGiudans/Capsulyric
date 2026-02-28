package com.example.islandlyrics

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

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

    // Pager State
    val pagerState = rememberPagerState(pageCount = { 3 })
    val tabs = listOf(
        stringResource(R.string.tab_capsule),
        stringResource(R.string.tab_notification),
        stringResource(R.string.tab_app_ui)
    )

    // State
    var followSystem by remember { mutableStateOf(prefs.getBoolean("theme_follow_system", true)) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("theme_dark_mode", false)) }
    var dynamicIconEnabled by remember { mutableStateOf(prefs.getBoolean("dynamic_icon_enabled", false)) }
    var iconStyle by remember { mutableStateOf(prefs.getString("dynamic_icon_style", "classic") ?: "classic") }

    var actionStyle by remember { mutableStateOf(prefs.getString("notification_actions_style", "disabled") ?: "disabled") }
    var notificationClickStyle by remember { mutableStateOf(prefs.getString("notification_click_style", "default") ?: "default") }
    var dismissDelay by remember { mutableLongStateOf(prefs.getLong("notification_dismiss_delay", 0L)) }

    var progressColorEnabled by remember { mutableStateOf(prefs.getBoolean("progress_bar_color_enabled", false)) }
    var disableScrolling by remember { mutableStateOf(prefs.getBoolean("disable_lyric_scrolling", false)) }
    var oneuiCapsuleColorEnabled by remember { mutableStateOf(prefs.getBoolean("oneui_capsule_color_enabled", false)) }

    var superIslandEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_enabled", false)) }
    var superIslandTextColorEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_text_color_enabled", false)) }

    var superIslandShareEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_share_enabled", true)) }
    var superIslandShareFormat by remember { mutableStateOf(prefs.getString("super_island_share_format", "format_1") ?: "format_1") }
    var miuixEnabled by remember { mutableStateOf(prefs.getBoolean("ui_use_miuix", false)) }
    var predictiveBackEnabled by remember { mutableStateOf(prefs.getBoolean("predictive_back_enabled", false)) }

    val isHyperOsSupported = remember { RomUtils.isHyperOsVersionAtLeast(3, 0, 300) }
    val isHyperOs = remember { RomUtils.getRomType() == "HyperOS" }
    LaunchedEffect(isHyperOsSupported) {
        if (!isHyperOsSupported) {
            if (dynamicIconEnabled) {
                dynamicIconEnabled = false
                prefs.edit().putBoolean("dynamic_icon_enabled", false).apply()
            }
            if (actionStyle == "miplay") {
                actionStyle = "disabled"
                prefs.edit().putString("notification_actions_style", "disabled").apply()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
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
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        // Tab Row + Pager
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Spacer(modifier = Modifier.height(12.dp))
            // HyperOS-style pill tab row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MiuixTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .then(
                                if (!selected) Modifier.border(
                                    width = 1.dp,
                                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                ) else Modifier
                            )
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MiuixTheme.colorScheme.onBackground
                                    else MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }

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
                                CapsulePreview(
                                    dynamicIconEnabled = dynamicIconEnabled,
                                    iconStyle = iconStyle,
                                    oneuiCapsuleColorEnabled = oneuiCapsuleColorEnabled
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                            item {
                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                    SuperSwitch(
                                        title = stringResource(R.string.settings_disable_scrolling),
                                        summary = stringResource(R.string.settings_disable_scrolling_desc),
                                        checked = disableScrolling,
                                        onCheckedChange = {
                                            disableScrolling = it
                                            prefs.edit().putBoolean("disable_lyric_scrolling", it).apply()
                                        }
                                    )
                                    if (RomUtils.getRomType() == "OneUI") {
                                        SuperSwitch(
                                            title = stringResource(R.string.settings_oneui_capsule_color),
                                            summary = stringResource(R.string.settings_oneui_capsule_color_desc),
                                            checked = oneuiCapsuleColorEnabled,
                                            onCheckedChange = {
                                                oneuiCapsuleColorEnabled = it
                                                prefs.edit().putBoolean("oneui_capsule_color_enabled", it).apply()
                                            }
                                        )
                                    }
                                    if (isHyperOs) {
                                        SuperSwitch(
                                            title = stringResource(R.string.settings_super_island),
                                            summary = stringResource(R.string.settings_super_island_desc),
                                            checked = superIslandEnabled,
                                            onCheckedChange = { enabled ->
                                                superIslandEnabled = enabled
                                                prefs.edit().putBoolean("super_island_enabled", enabled).apply()

                                                // ⚡ Logic: If MiPlay is selected when enabling Super Island, switch to Off
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
                                        )
                                        if (superIslandEnabled) {
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
                                        }
                                    }
                                    if (isHyperOsSupported && !superIslandEnabled) {
                                        SuperSwitch(
                                            title = stringResource(R.string.settings_dynamic_icon),
                                            summary = stringResource(R.string.settings_dynamic_icon_desc),
                                            checked = dynamicIconEnabled,
                                            onCheckedChange = {
                                                dynamicIconEnabled = it
                                                prefs.edit().putBoolean("dynamic_icon_enabled", it).apply()
                                            }
                                        )
                                        if (dynamicIconEnabled) {
                                            val iconStyles = listOf("classic", "advanced")
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
                        }
                        1 -> { // Notification
                            item {
                                NotificationPreview(
                                    progressColorEnabled = progressColorEnabled,
                                    actionStyle = actionStyle,
                                    superIslandEnabled = superIslandEnabled,
                                    superIslandTextColorEnabled = superIslandTextColorEnabled
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                            item {
                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                    // ⚡ Logic: Hide Colorize Progress Bar if Playback Notification (media_controls) is selected
                                    if (actionStyle == "disabled") {
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
                                    if (isHyperOsSupported && !superIslandEnabled) {
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
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
