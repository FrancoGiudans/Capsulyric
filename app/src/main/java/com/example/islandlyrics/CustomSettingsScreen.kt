package com.example.islandlyrics

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    
    // Pager State
    // Pager State
    val pagerState = rememberPagerState(pageCount = { 3 })
    val tabs = listOf(
        stringResource(R.string.tab_capsule),
        stringResource(R.string.tab_notification),
        stringResource(R.string.tab_app_ui)
    )

    // ... (State setup lines 44-152 omitted for brevity as they don't change, assuming contextual match) ...
    // Note: I cannot omit lines in replace_file_content unless I use multiple chunks or careful selection.
    // The user wants me to reorder the content. The State setup is needed for the content.
    // I will target the TopAppBar and HorizontalPager parts specifically.

    /*
       Wait, I can't easily skip the middle part if I want to update 'tabs' definition (line 42) AND 'HorizontalPager' (line 178).
       I should do this in multiple chunks using multi_replace_file_content if possible, or just replace the big affected blocks.
       
       Let's use multi_replace_file_content.
    */
    
    // Changing strategy to multi_replace_file_content in the thought block, but here I am in the tool call.
    // I will cancel this tool call logic and use multi_replace_file_content instead.
    
    // ERROR in thought process: I am in the tool argument generation.
    // I will generate a multi_replace_file_content call.
 

    // --- State Duplication from SettingsScreen ---

    // Preferences State
    var autoUpdateEnabled by remember { mutableStateOf(UpdateChecker.isAutoUpdateEnabled(context)) }
    
    // Theme State
    var followSystem by remember { mutableStateOf(prefs.getBoolean("theme_follow_system", true)) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("theme_dark_mode", false)) }
    var pureBlack by remember { mutableStateOf(prefs.getBoolean("theme_pure_black", false)) }
    var dynamicColor by remember { mutableStateOf(prefs.getBoolean("theme_dynamic_color", true)) }
    var dynamicIconEnabled by remember { mutableStateOf(prefs.getBoolean("dynamic_icon_enabled", false)) }
    var iconStyle by remember { mutableStateOf(prefs.getString("dynamic_icon_style", "classic") ?: "classic") }
    
    // Dialog State
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showIconStyleDropdown by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // Notification Action Style State
    var actionStyle by remember { mutableStateOf(prefs.getString("notification_actions_style", "disabled") ?: "disabled") }
    var showActionStyleDropdown by remember { mutableStateOf(false) }

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
    var oneuiCapsuleColorEnabled by remember { mutableStateOf(prefs.getBoolean("oneui_capsule_color_enabled", false)) }

    var superIslandEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_enabled", false)) }
    var superIslandTextColorEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_text_color_enabled", false)) }
    var superIslandEdgeColorEnabled by remember { mutableStateOf(prefs.getBoolean("super_island_edge_color_enabled", false)) }
    var miuixEnabled by remember { mutableStateOf(prefs.getBoolean("ui_use_miuix", false)) }

    // Dialog State for UI Style
    var showUiStyleDropdown by remember { mutableStateOf(false) }

    // Check for HyperOS 3.0.300+
    val isHyperOsSupported = remember { RomUtils.isHyperOsVersionAtLeast(3, 0, 300) }

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
                        }
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
                            CapsulePreview(
                                dynamicIconEnabled = dynamicIconEnabled,
                                iconStyle = iconStyle,
                                oneuiCapsuleColorEnabled = oneuiCapsuleColorEnabled
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsSwitchItem(
                                title = stringResource(R.string.settings_disable_scrolling),
                                subtitle = stringResource(R.string.settings_disable_scrolling_desc),
                                checked = disableScrolling,
                                onCheckedChange = {
                                    disableScrolling = it
                                    prefs.edit().putBoolean("disable_lyric_scrolling", it).apply()
                                }
                            )

                            if (RomUtils.getRomType() == "OneUI") {
                                SettingsSwitchItem(
                                    title = stringResource(R.string.settings_oneui_capsule_color),
                                    subtitle = stringResource(R.string.settings_oneui_capsule_color_desc),
                                    checked = oneuiCapsuleColorEnabled,
                                    onCheckedChange = {
                                        oneuiCapsuleColorEnabled = it
                                        prefs.edit().putBoolean("oneui_capsule_color_enabled", it).apply()
                                    }
                                )
                            }

                            if (isHyperOsSupported) {
                                SettingsSwitchItem(
                                    title = stringResource(R.string.settings_dynamic_icon),
                                    subtitle = stringResource(R.string.settings_dynamic_icon_desc),
                                    checked = dynamicIconEnabled,
                                    onCheckedChange = {
                                        dynamicIconEnabled = it
                                        prefs.edit().putBoolean("dynamic_icon_enabled", it).apply()
                                    }
                                )
                                
                                if (dynamicIconEnabled) {
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
                                                    "classic" to R.string.icon_style_classic,
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
                                
                                SettingsSwitchItem(
                                    title = stringResource(R.string.settings_super_island),
                                    subtitle = stringResource(R.string.settings_super_island_desc),
                                    checked = superIslandEnabled,
                                    onCheckedChange = { enabled ->
                                        superIslandEnabled = enabled
                                        prefs.edit().putBoolean("super_island_enabled", enabled).apply()
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
                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_super_island_text_color),
                                        subtitle = stringResource(R.string.settings_super_island_text_color_desc),
                                        checked = superIslandTextColorEnabled,
                                        onCheckedChange = {
                                            superIslandTextColorEnabled = it
                                            prefs.edit().putBoolean("super_island_text_color_enabled", it).apply()
                                        }
                                    )
                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_super_island_edge_color),
                                        subtitle = stringResource(R.string.settings_super_island_edge_color_desc),
                                        checked = superIslandEdgeColorEnabled,
                                        onCheckedChange = {
                                            superIslandEdgeColorEnabled = it
                                            prefs.edit().putBoolean("super_island_edge_color_enabled", it).apply()
                                        }
                                    )
                                }
                            }
                        }
                        1 -> { // Notification (Moved from 2)
                             // Preview
                             NotificationPreview(
                                 progressColorEnabled = progressColorEnabled,
                                 actionStyle = actionStyle
                             )
                             Spacer(modifier = Modifier.height(16.dp))


                             SettingsSwitchItem(
                                title = stringResource(R.string.settings_progress_color),
                                subtitle = stringResource(R.string.settings_progress_color_desc),
                                checked = progressColorEnabled,
                                onCheckedChange = {
                                    progressColorEnabled = it
                                    prefs.edit().putBoolean("progress_bar_color_enabled", it).apply()
                                }
                            )
                            
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
                                            if (styleId == "miplay") isHyperOsSupported else true
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
