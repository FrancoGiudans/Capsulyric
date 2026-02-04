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
    var serviceEnabled by remember { mutableStateOf(prefs.getBoolean("service_enabled", true)) }
    var autoUpdateEnabled by remember { mutableStateOf(UpdateChecker.isAutoUpdateEnabled(context)) }
    
    // Theme State
    var followSystem by remember { mutableStateOf(prefs.getBoolean("theme_follow_system", true)) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("theme_dark_mode", false)) }
    var pureBlack by remember { mutableStateOf(prefs.getBoolean("theme_pure_black", false)) }
    var dynamicColor by remember { mutableStateOf(prefs.getBoolean("theme_dynamic_color", true)) }
    var dynamicIconEnabled by remember { mutableStateOf(prefs.getBoolean("dynamic_icon_enabled", false)) }
    
    // Dialog State
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // Logic for permissions status
    val notificationGranted = remember(LocalContext.current) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    
    // Post Notification Logic (Android 13+)
    val postNotificationGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    // Determine actual dark mode for AppTheme
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDarkTheme = if (followSystem) isSystemDark else darkMode

    AppTheme(
        darkTheme = useDarkTheme,
        dynamicColor = dynamicColor,
        pureBlack = pureBlack && useDarkTheme
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Header
                Text(
                    text = "Settings",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(24.dp)
                )

                // --- Service ---
                SettingsSectionHeader(text = "Service")
                SettingsSwitchItem(
                    title = "Enable Service",
                    checked = serviceEnabled,
                    onCheckedChange = { 
                        serviceEnabled = it
                        prefs.edit().putBoolean("service_enabled", it).apply()
                    }
                )

                // --- Permissions ---
                SettingsSectionHeader(text = stringResource(R.string.perm_cat_title))
                
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
                         if (true) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.fromParts("package", context.packageName, null)
                            context.startActivity(intent)
                        }
                    },
                    onCheckedChange = {}
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // --- Appearance ---
                SettingsSectionHeader(text = stringResource(R.string.settings_header_appearance))
                
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
                    enabled = useDarkTheme, // Only valid in dark mode
                    onCheckedChange = {
                        pureBlack = it
                        ThemeHelper.setPureBlack(context, it)
                        // No need to recreate; LaunchedEffect handles window background
                    }
                )

                // Dynamically update Window Background for Pure Black to prevent jitter from recreate()
                LaunchedEffect(pureBlack, useDarkTheme) {
                    val activity = context as? Activity
                    if (activity != null) {
                         if (pureBlack && useDarkTheme) {
                             activity.window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
                             activity.window.setBackgroundDrawableResource(android.R.color.black)
                         } else {
                             // Reset to default (usually handled by theme, but safe to set to null or theme color)
                             // Simple approach: Set to background color of current theme or null to let Theme handle it
                             // activity.window.setBackgroundDrawable(null) // potentially checks theme
                             // Better: Set to Surface color or standard background
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

                SettingsSwitchItem(
                    title = stringResource(R.string.settings_dynamic_icon),
                    subtitle = stringResource(R.string.settings_dynamic_icon_desc),
                    checked = dynamicIconEnabled,
                    onCheckedChange = {
                        dynamicIconEnabled = it
                        prefs.edit().putBoolean("dynamic_icon_enabled", it).apply()
                    }
                )

                // --- General ---
                SettingsSectionHeader(text = stringResource(R.string.settings_general_header), marginTop = 16.dp)
                
                SettingsActionItem(
                    title = stringResource(R.string.parser_rule_title),
                    icon = android.R.drawable.ic_menu_edit,
                    onClick = {
                        context.startActivity(Intent(context, ParserRuleActivity::class.java))
                    }
                )

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
                    title = "Ignore Battery Optimization",
                    icon = R.drawable.ic_music_note,
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    }
                )

                 HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // --- Help ---
                SettingsSectionHeader(text = "Help")
                SettingsActionItem(
                    title = stringResource(R.string.settings_guide_item),
                    icon = android.R.drawable.ic_menu_help,
                    onClick = { showGuideDialog = true }
                )

                // --- About ---
                SettingsSectionHeader(text = "About", marginTop = 16.dp)

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

            if (showGuideDialog) {
                AlertDialog(
                    onDismissRequest = { showGuideDialog = false },
                    title = { Text(text = stringResource(R.string.guide_title)) },
                    text = { 
                        // Compose doesn't support HTML easily. 
                        // For now we strip or just show simple text, or use AndroidView for Html.fromHtml
                        // Let's assume the string is simple enough or we just show it.
                        // Ideally we use a simple text or replicate the logic.
                        Text(text = stringResource(R.string.guide_message)) 
                    },
                    confirmButton = {
                        TextButton(onClick = { showGuideDialog = false }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
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
            onCheckedChange = { onCheckedChange(it) },
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
