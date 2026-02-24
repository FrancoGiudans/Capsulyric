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
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.vector.ImageVector

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
    var showLanguageDropdown by remember { mutableStateOf(false) }
    var showIconStyleDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    // Notification Action Style State
    var actionStyle by remember { mutableStateOf(prefs.getString("notification_actions_style", "disabled") ?: "disabled") }
    var showActionStyleDialog by remember { mutableStateOf(false) }

    // Notification Click Action State
    var notificationClickStyle by remember { mutableStateOf(prefs.getString("notification_click_style", "default") ?: "default") }
    var showNotificationClickDialog by remember { mutableStateOf(false) }

    // Dismiss Delay State
    var dismissDelay by remember { mutableLongStateOf(prefs.getLong("notification_dismiss_delay", 0L)) }
    var showDismissDelayDialog by remember { mutableStateOf(false) }

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
                    title = { Text(stringResource(R.string.title_app_settings)) },
                    navigationIcon = {
                        IconButton(onClick = { (context as? Activity)?.finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
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
                // ── 1. General ──
                // ═══════════════════════════════════════
                SettingsSectionHeader(text = stringResource(R.string.settings_general_header))

                // Language
                val currentLangCode = prefs.getString("language_code", "")
                val currentLangText = when(currentLangCode) {
                    "en" -> "English"
                    "zh-CN" -> "简体中文"
                    else -> stringResource(R.string.settings_theme_follow_system)
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    SettingsTextItem(
                        title = stringResource(R.string.settings_language),
                        value = currentLangText,
                        onClick = { showLanguageDropdown = true }
                    )
                    Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center)) {
                        DropdownMenu(
                            expanded = showLanguageDropdown,
                            onDismissRequest = { showLanguageDropdown = false }
                        ) {
                            val languages = listOf("System Default" to "", "English" to "en", "简体中文" to "zh-CN")
                            languages.forEach { (label, code) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        ThemeHelper.setLanguage(context, code)
                                        (context as? Activity)?.recreate()
                                        showLanguageDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Suggest Current App
                var recommendMediaAppEnabled by remember { mutableStateOf(prefs.getBoolean("recommend_media_app", true)) }
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_recommend_media_app),
                    subtitle = stringResource(R.string.settings_recommend_media_app_desc),
                    checked = recommendMediaAppEnabled,
                    onCheckedChange = {
                        recommendMediaAppEnabled = it
                        prefs.edit().putBoolean("recommend_media_app", it).apply()
                    }
                )

                // Hide Recents
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
                // ── 2. System & Permissions ──
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

                // Battery
                SettingsActionItem(
                    title = stringResource(R.string.settings_general_battery),
                    icon = Icons.Filled.MusicNote,
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // ═══════════════════════════════════════
                // ── 3. Updates ──
                // ═══════════════════════════════════════
                SettingsSectionHeader(text = stringResource(R.string.update_check_title))

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
                     title = stringResource(R.string.update_check_title),
                     icon = Icons.Filled.Sync,
                     onClick = onCheckUpdate
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // ═══════════════════════════════════════
                // ── 4. Help & About ──
                // ═══════════════════════════════════════
                SettingsSectionHeader(text = stringResource(R.string.settings_help_about_header))

                SettingsActionItem(
                    title = stringResource(R.string.faq_title),
                    icon = Icons.AutoMirrored.Filled.Help,
                    onClick = {
                        context.startActivity(Intent(context, FAQActivity::class.java))
                    }
                )

                SettingsActionItem(
                    title = stringResource(R.string.settings_feedback),
                    icon = Icons.AutoMirrored.Filled.Send,
                    onClick = {
                        showFeedbackDialog = true
                    }
                )

                SettingsActionItem(
                    title = stringResource(R.string.settings_about_github),
                    icon = Icons.Filled.Link,
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

                if (showLogs) {
                     SettingsActionItem(
                         title = stringResource(R.string.settings_console_log),
                         icon = Icons.Filled.Info,
                         onClick = onShowLogs
                     )
                }

                Spacer(modifier = Modifier.height(24.dp))
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

            if (showFeedbackDialog) {
                FeedbackSelectionDialog(onDismiss = { showFeedbackDialog = false })
            }
        }
    }
}

@Composable
fun FeedbackSelectionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_feedback_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FrancoGiudans/Capsulyric/issues/new"))
                            context.startActivity(browserIntent)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dialog_feedback_github),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.dialog_feedback_github_desc),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://f.wps.cn/g/qACKW9I3/"))
                            context.startActivity(browserIntent)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dialog_feedback_wps),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.dialog_feedback_wps_desc),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
fun NotificationClickDialog(
    currentStyle: String,
    onStyleSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val styles = listOf(
        "default" to stringResource(R.string.settings_click_action_default) to stringResource(R.string.settings_click_action_default_desc),
        "media_controls" to stringResource(R.string.settings_click_action_media) to stringResource(R.string.settings_click_action_media_desc)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_click_action_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                styles.forEach { (styleInfo, desc) ->
                    val (styleId, name) = styleInfo
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
                                text = name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = desc,
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
fun DismissDelaySelectionDialog(
    currentDelay: Long,
    onDelaySelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        0L to R.string.dismiss_delay_immediate,
        1000L to R.string.dismiss_delay_1s,
        3000L to R.string.dismiss_delay_3s,
        5000L to R.string.dismiss_delay_5s
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_dismiss_delay_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { (delay, labelRes) ->
                    val isSelected = currentDelay == delay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDelaySelected(delay) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onDelaySelected(delay) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(labelRes),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
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
    icon: ImageVector,
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
             imageVector = icon,
             contentDescription = null,
             tint = MaterialTheme.colorScheme.onSurfaceVariant
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
