package com.example.islandlyrics

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onCheckUpdate: () -> Unit,
    onShowLogs: () -> Unit,
    updateVersionText: String,
    updateBuildText: String
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    val scrollState = rememberScrollState()

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

    // ... (lifecycle observer) ...

    // Force disable unsupported features if they were previously enabled
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

    AppTheme(
        darkTheme = useDarkTheme,
        dynamicColor = dynamicColor,
        pureBlack = pureBlack && useDarkTheme
    ) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text("Settings") },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {

                // ═══════════════════════════════════════
                // ── 1. Core Services ──
                // ═══════════════════════════════════════
                SettingsSectionHeader(text = stringResource(R.string.settings_core_services_header))

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

                // Parser Rules (Notification Rules)
                SettingsActionItem(
                    title = stringResource(R.string.parser_rule_title),
                    icon = android.R.drawable.ic_menu_edit,
                    onClick = {
                        context.startActivity(Intent(context, ParserRuleActivity::class.java))
                    }
                )

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

                // ═══════════════════════════════════════
                // ── 2. Personalization ──
                // ═══════════════════════════════════════
                SettingsSectionHeader(text = stringResource(R.string.settings_personalization_header))

                // Theme
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

                // Dynamically update Window Background for Pure Black
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

                SettingsSwitchItem(
                    title = stringResource(R.string.settings_theme_dynamic_color),
                    subtitle = stringResource(R.string.settings_theme_dynamic_color_desc),
                    checked = dynamicColor,
                    onCheckedChange = {
                        dynamicColor = it
                        ThemeHelper.setDynamicColor(context, it)
                    }
                )

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
                    
                    // Icon Style Selection (only visible when dynamic icon is enabled)
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

                // Notification
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

                var progressColorEnabled by remember { mutableStateOf(prefs.getBoolean("progress_bar_color_enabled", false)) }
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_progress_color),
                    subtitle = stringResource(R.string.settings_progress_color_desc),
                    checked = progressColorEnabled,
                    onCheckedChange = {
                        progressColorEnabled = it
                        prefs.edit().putBoolean("progress_bar_color_enabled", it).apply()
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // ═══════════════════════════════════════
                // ── 3. General ──
                // ═══════════════════════════════════════
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

                var hideRecentsEnabled by remember { mutableStateOf(prefs.getBoolean("hide_recents_enabled", false)) }
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_hide_recents),
                    subtitle = stringResource(R.string.settings_hide_recents_desc),
                    checked = hideRecentsEnabled,
                    onCheckedChange = {
                        hideRecentsEnabled = it
                        prefs.edit().putBoolean("hide_recents_enabled", it).apply()
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // ═══════════════════════════════════════
                // ── 4. Help & About ──
                // ═══════════════════════════════════════
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

                // Version
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

                Spacer(modifier = Modifier.height(24.dp))
            }

            // --- Dialogs ---

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
        }
    }
}

@Composable
fun LanguageSelectionDialog(onDismiss: () -> Unit) {
    val languages = listOf("System Default" to "", "English" to "en", "简体中文" to "zh-CN")
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                languages.forEach { (label, code) ->
                    TextButton(
                        onClick = {
                            ThemeHelper.setLanguage(context, code)
                            (context as? Activity)?.recreate()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text(
                            text = label, 
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start
                        )
                    }
                }
            }
        },
        confirmButton = {
             TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
fun IconStyleSelectionDialog(
    currentStyle: String,
    onStyleSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val styles = listOf(
        "classic" to R.string.icon_style_classic to R.string.icon_style_classic_desc,
        "advanced" to R.string.icon_style_advanced to R.string.icon_style_advanced_desc
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_icon_style_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                styles.forEach { (styleInfo, descId) ->
                    val (styleId, nameId) = styleInfo
                    val isSelected = currentStyle == styleId
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStyleSelected(styleId) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onStyleSelected(styleId) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(nameId),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(descId),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
fun NotificationActionsDialog(
    currentStyle: String,
    isHyperOsSupported: Boolean,
    onStyleSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val allStyles = listOf(
        "disabled" to R.string.settings_action_style_off to R.string.settings_action_style_off_desc,
        "media_controls" to R.string.settings_action_style_media to R.string.settings_action_style_media_desc,
        "miplay" to R.string.settings_action_style_miplay to R.string.settings_action_style_miplay_desc
    )
    
    val styles = allStyles.filter { 
        val (styleInfo, _) = it
        val (styleId, _) = styleInfo
        if (styleId == "miplay") isHyperOsSupported else true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_notification_actions)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                styles.forEach { (styleInfo, descId) ->
                    val (styleId, nameId) = styleInfo
                    val isSelected = currentStyle == styleId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStyleSelected(styleId) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onStyleSelected(styleId) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(nameId),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(descId),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}


@Composable
fun SettingsSectionHeader(text: String, marginTop: androidx.compose.ui.unit.Dp = 8.dp) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = marginTop, bottom = 8.dp)
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    // If onClick is provided, the Row handles click.
    // If enabled is false, Row click is disabled, but Switch is also disabled.
    // We want: visible as enabled (not alpha 0.5), but switch might look different?
    // User wants permissions NOT grayed out.
    // So we pass enabled=true. The caller handles logic.
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick?.invoke() ?: onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 18.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked, 
            onCheckedChange = { 
                if (onClick != null) {
                    onClick()
                } else {
                    onCheckedChange(it)
                }
            },
            enabled = enabled
        )
    }
}

@Composable
fun SettingsActionItem(
    title: String,
    icon: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun SettingsTextItem(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsValueItem(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HorizontalDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 24.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
