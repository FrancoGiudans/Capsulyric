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
    val pagerState = rememberPagerState(pageCount = { 3 })
    val tabs = listOf("App Body", "Capsule", "Notification") 

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
    var showIconStyleDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // Notification Action Style State
    var actionStyle by remember { mutableStateOf(prefs.getString("notification_actions_style", "disabled") ?: "disabled") }
    var showActionStyleDialog by remember { mutableStateOf(false) }

    // Notification Click Action State
    var notificationClickStyle by remember { mutableStateOf(prefs.getString("notification_click_style", "default") ?: "default") }
    var showNotificationClickDialog by remember { mutableStateOf(false) }

    // Dismiss Delay State
    var dismissDelay by remember { mutableLongStateOf(prefs.getLong("notification_dismiss_delay", 0L)) }
    var showDismissDelayDialog by remember { mutableStateOf(false) }

    // Other Setup
    var progressColorEnabled by remember { mutableStateOf(prefs.getBoolean("progress_bar_color_enabled", false)) }
    var hideRecentsEnabled by remember { mutableStateOf(prefs.getBoolean("hide_recents_enabled", false)) }
    var recommendMediaAppEnabled by remember { mutableStateOf(prefs.getBoolean("recommend_media_app", true)) }

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
                        title = { Text("Custom Settings") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                            }
                        }
                    )
                    TabRow(selectedTabIndex = pagerState.currentPage) {
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
                        0 -> { // App Body
                             // Language
                            val currentLangCode = prefs.getString("language_code", "")
                            val currentLangText = when(currentLangCode) {
                                "en" -> "English"
                                "zh-CN" -> "简体中文"
                                else -> stringResource(R.string.settings_theme_follow_system)
                            }
                            SettingsTextItem(
                                title = stringResource(R.string.settings_language),
                                value = currentLangText,
                                onClick = { showLanguageDialog = true }
                            )
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            // Theme
                            SettingsSectionHeader(text = stringResource(R.string.settings_personalization_header)) // Using header for structure
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

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            // General
                            SettingsSectionHeader(text = stringResource(R.string.settings_general_header))

                            SettingsSwitchItem(
                                title = stringResource(R.string.settings_auto_update),
                                subtitle = stringResource(R.string.settings_auto_update_desc),
                                checked = autoUpdateEnabled,
                                onCheckedChange = {
                                    autoUpdateEnabled = it
                                    UpdateChecker.setAutoUpdateEnabled(context, it)
                                }
                            )
                            
                            // Prerelease Updates
                            var prereleaseEnabled by remember { mutableStateOf(UpdateChecker.isPrereleaseEnabled(context)) }
                            var showPrereleaseDialog by remember { mutableStateOf(false) }

                            SettingsSwitchItem(
                                title = stringResource(R.string.settings_prerelease_update),
                                subtitle = stringResource(R.string.settings_prerelease_update_desc),
                                checked = prereleaseEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        showPrereleaseDialog = true
                                    } else {
                                        prereleaseEnabled = false
                                        UpdateChecker.setPrereleaseEnabled(context, false)
                                    }
                                }
                            )
                            
                             if (showPrereleaseDialog) {
                                AlertDialog(
                                    onDismissRequest = { showPrereleaseDialog = false },
                                    title = { Text(stringResource(R.string.dialog_prerelease_warning_title)) },
                                    text = { Text(stringResource(R.string.dialog_prerelease_warning_message)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            prereleaseEnabled = true
                                            UpdateChecker.setPrereleaseEnabled(context, true)
                                            showPrereleaseDialog = false
                                        }) {
                                            Text(stringResource(android.R.string.ok))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showPrereleaseDialog = false }) {
                                            Text(stringResource(android.R.string.cancel))
                                        }
                                    }
                                )
                            }

                            SettingsActionItem(
                                title = stringResource(R.string.settings_general_battery),
                                icon = R.drawable.ic_music_note,
                                onClick = {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    intent.data = Uri.parse("package:${context.packageName}")
                                    context.startActivity(intent)
                                }
                            )
                            
                            SettingsSwitchItem(
                                title = stringResource(R.string.settings_hide_recents),
                                subtitle = stringResource(R.string.settings_hide_recents_desc),
                                checked = hideRecentsEnabled,
                                onCheckedChange = {
                                    hideRecentsEnabled = it
                                    prefs.edit().putBoolean("hide_recents_enabled", it).apply()
                                }
                            )

                            SettingsSwitchItem(
                                title = stringResource(R.string.settings_recommend_media_app),
                                subtitle = stringResource(R.string.settings_recommend_media_app_desc),
                                checked = recommendMediaAppEnabled,
                                onCheckedChange = {
                                    recommendMediaAppEnabled = it
                                    prefs.edit().putBoolean("recommend_media_app", it).apply()
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            
                            // Help & About
                            SettingsSectionHeader(text = stringResource(R.string.settings_help_about_header))

                             SettingsActionItem(
                                title = stringResource(R.string.faq_title),
                                icon = android.R.drawable.ic_menu_help,
                                onClick = {
                                    context.startActivity(Intent(context, FAQActivity::class.java))
                                }
                            )

                            SettingsActionItem(
                                title = stringResource(R.string.settings_feedback),
                                icon = android.R.drawable.ic_menu_send,
                                onClick = {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://francogiudans.github.io/CapsulyricFeed/"))
                                    context.startActivity(browserIntent)
                                }
                            )

                            SettingsActionItem(
                                title = stringResource(R.string.settings_about_github),
                                icon = R.drawable.ic_link,
                                onClick = {
                                     val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FrancoGiudans/Capsulyric"))
                                     context.startActivity(browserIntent)
                                }
                            )
                            
                             SettingsValueItem(
                                title = stringResource(R.string.about_version),
                                value = updateVersionText
                            )
                            
                            // Build Number (Dev Trigger)
                            var devStepCount by remember { mutableIntStateOf(0) }
                            val isDevMode = remember { prefs.getBoolean("dev_mode_enabled", false) }
                            var showLogs by remember { mutableStateOf(BuildConfig.DEBUG || isDevMode) }
            
                            SettingsValueItem(
                                title = stringResource(R.string.about_commit),
                                value = updateBuildText,
                                onClick = {
                                    devStepCount++
                                     if (devStepCount in 3..6) {
                                         Toast.makeText(context, "${7 - devStepCount} steps away from developer mode...", Toast.LENGTH_SHORT).show()
                                     } else if (devStepCount == 7) {
                                        prefs.edit().putBoolean("dev_mode_enabled", true).apply()
                                        showLogs = true
                                        Toast.makeText(context, "Developer Mode Enabled! 👩‍💻", Toast.LENGTH_SHORT).show()
                                     }
                                }
                            )
                            
                            SettingsActionItem(
                                 title = stringResource(R.string.update_check_title),
                                 icon = R.drawable.ic_sync,
                                 onClick = onCheckUpdate
                            )
            
                            if (showLogs) {
                                 SettingsActionItem(
                                     title = stringResource(R.string.settings_console_log),
                                     icon = android.R.drawable.ic_menu_info_details,
                                     onClick = onShowLogs
                                 )
                            }

                        }
                        1 -> { // Capsule
                            SettingsSectionHeader(text = "Capsule Settings")

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
                                    SettingsTextItem(
                                        title = stringResource(R.string.settings_icon_style),
                                        value = styleDisplayName,
                                        onClick = { showIconStyleDialog = true }
                                    )
                                }
                            }

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
                        2 -> { // Notification
                             SettingsSectionHeader(text = "Notification Settings")
                             
                             // Permissions
                            SettingsSwitchItem(
                                title = stringResource(R.string.perm_read_notif),
                                subtitle = stringResource(R.string.perm_read_notif_desc),
                                checked = notificationGranted,
                                enabled = true,
                                onClick = {
                                    if (notificationGranted) {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    } else {
                                        showPrivacyDialog = true
                                    }
                                },
                                onCheckedChange = {}
                            )
            
                            SettingsSwitchItem(
                                title = stringResource(R.string.perm_post_notif),
                                subtitle = stringResource(R.string.perm_post_notif_desc),
                                checked = postNotificationGranted,
                                enabled = true,
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    intent.data = Uri.fromParts("package", context.packageName, null)
                                    context.startActivity(intent)
                                },
                                onCheckedChange = {}
                            )
                            
                             SettingsActionItem(
                                title = stringResource(R.string.parser_rule_title),
                                icon = android.R.drawable.ic_menu_edit,
                                onClick = {
                                    context.startActivity(Intent(context, ParserRuleActivity::class.java))
                                }
                            )
                            
                             // Notification Action Style
                            val actionStyleDisplay = when (actionStyle) {
                                "media_controls" -> stringResource(R.string.settings_action_style_media)
                                "miplay" -> stringResource(R.string.settings_action_style_miplay)
                                else -> stringResource(R.string.settings_action_style_off)
                            }
                            SettingsTextItem(
                                title = stringResource(R.string.settings_notification_actions),
                                value = actionStyleDisplay,
                                onClick = { showActionStyleDialog = true }
                            )
            
                            // Notification Click Action
                            val clickStyleDisplay = when (notificationClickStyle) {
                                "media_controls" -> stringResource(R.string.settings_click_action_media)
                                else -> stringResource(R.string.settings_click_action_default)
                            }
                            SettingsTextItem(
                                title = stringResource(R.string.settings_click_action_title),
                                value = clickStyleDisplay,
                                onClick = { showNotificationClickDialog = true }
                            )
                            
                             // Dismiss Delay
                            val dismissDelayText = when (dismissDelay) {
                                0L -> stringResource(R.string.dismiss_delay_immediate)
                                1000L -> stringResource(R.string.dismiss_delay_1s)
                                3000L -> stringResource(R.string.dismiss_delay_3s)
                                5000L -> stringResource(R.string.dismiss_delay_5s)
                                else -> stringResource(R.string.dismiss_delay_immediate)
                            }
                            SettingsTextItem(
                                title = stringResource(R.string.settings_dismiss_delay_title),
                                value = dismissDelayText,
                                onClick = { showDismissDelayDialog = true }
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
            
             if (showIconStyleDialog) {
                IconStyleSelectionDialog(
                    currentStyle = iconStyle,
                    onStyleSelected = { style ->
                        iconStyle = style
                        prefs.edit().putString("dynamic_icon_style", style).apply()
                        showIconStyleDialog = false
                    },
                    onDismiss = { showIconStyleDialog = false }
                )
            }

            if (showActionStyleDialog) {
                NotificationActionsDialog(
                    currentStyle = actionStyle,
                    isHyperOsSupported = isHyperOsSupported,
                    onStyleSelected = { style ->
                        actionStyle = style
                        prefs.edit().putString("notification_actions_style", style).apply()
                        showActionStyleDialog = false
                    },
                    onDismiss = { showActionStyleDialog = false }
                )
            }

            if (showNotificationClickDialog) {
                NotificationClickDialog(
                    currentStyle = notificationClickStyle,
                    onStyleSelected = { style ->
                        notificationClickStyle = style
                        prefs.edit().putString("notification_click_style", style).apply()
                        showNotificationClickDialog = false
                    },
                    onDismiss = { showNotificationClickDialog = false }
                )
            }

            if (showDismissDelayDialog) {
                DismissDelaySelectionDialog(
                    currentDelay = dismissDelay,
                    onDelaySelected = { delay ->
                        dismissDelay = delay
                        prefs.edit().putLong("notification_dismiss_delay", delay).apply()
                        showDismissDelayDialog = false
                    },
                    onDismiss = { showDismissDelayDialog = false }
                )
            }
        }
    }
}
