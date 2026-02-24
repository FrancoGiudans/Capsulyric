package com.example.islandlyrics

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixSettingsScreen(
    onCheckUpdate: () -> Unit,
    onShowLogs: () -> Unit,
    updateVersionText: String,
    updateBuildText: String
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    // State
    var autoUpdateEnabled by remember { mutableStateOf(UpdateChecker.isAutoUpdateEnabled(context)) }
    var followSystem by remember { mutableStateOf(prefs.getBoolean("theme_follow_system", true)) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("theme_dark_mode", false)) }
    var pureBlack by remember { mutableStateOf(prefs.getBoolean("theme_pure_black", false)) }
    var dynamicIconEnabled by remember { mutableStateOf(prefs.getBoolean("dynamic_icon_enabled", false)) }

    var showPrivacyDialog by remember { mutableStateOf(false) }

    val isHyperOsSupported = remember { RomUtils.isHyperOsVersionAtLeast(3, 0, 300) }

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

    LaunchedEffect(isHyperOsSupported) {
        if (!isHyperOsSupported) {
            if (dynamicIconEnabled) {
                dynamicIconEnabled = false
                prefs.edit().putBoolean("dynamic_icon_enabled", false).apply()
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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.title_app_settings),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }, modifier = Modifier.padding(start = 12.dp)) {
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // ═══ 1. General ═══
            item { SmallTitle(text = stringResource(R.string.settings_general_header)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    // Language
                    val langOptions = listOf("", "en", "zh-CN")
                    val langNames = listOf(
                        stringResource(R.string.settings_theme_follow_system),
                        "English",
                        "简体中文"
                    )
                    val currentLangCode = prefs.getString("language_code", "") ?: ""
                    val currentLangIndex = langOptions.indexOf(currentLangCode).takeIf { it >= 0 } ?: 0
                    
                    SuperDropdown(
                        title = stringResource(R.string.settings_language),
                        items = langNames,
                        selectedIndex = currentLangIndex,
                        onSelectedIndexChange = { index ->
                            val code = langOptions[index]
                            prefs.edit().putString("language_code", code).apply()
                            ThemeHelper.setLanguage(context, code)
                            (context as? Activity)?.recreate()
                        }
                    )

                    // Recommend Media App
                    var recommendMediaAppEnabled by remember { mutableStateOf(prefs.getBoolean("recommend_media_app", true)) }
                    SuperSwitch(
                        title = stringResource(R.string.settings_recommend_media_app),
                        summary = stringResource(R.string.settings_recommend_media_app_desc),
                        checked = recommendMediaAppEnabled,
                        onCheckedChange = {
                            recommendMediaAppEnabled = it
                            prefs.edit().putBoolean("recommend_media_app", it).apply()
                        }
                    )

                    // Hide Recents
                    var hideRecentsEnabled by remember { mutableStateOf(prefs.getBoolean("hide_recents_enabled", false)) }
                    SuperSwitch(
                        title = stringResource(R.string.settings_hide_recents),
                        summary = stringResource(R.string.settings_hide_recents_desc),
                        checked = hideRecentsEnabled,
                        onCheckedChange = {
                            hideRecentsEnabled = it
                            prefs.edit().putBoolean("hide_recents_enabled", it).apply()
                        }
                    )
                }
            }

            // ═══ 2. System & Permissions ═══
            item { SmallTitle(text = stringResource(R.string.settings_core_services_header)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperSwitch(
                        title = stringResource(R.string.perm_read_notif),
                        summary = stringResource(R.string.perm_read_notif_desc),
                        checked = notificationGranted,
                        onCheckedChange = {
                            if (notificationGranted) {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            } else {
                                showPrivacyDialog = true
                            }
                        }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.perm_post_notif),
                        summary = stringResource(R.string.perm_post_notif_desc),
                        checked = postNotificationGranted,
                        onCheckedChange = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.fromParts("package", context.packageName, null)
                            context.startActivity(intent)
                        }
                    )
                    SuperArrow(
                        title = stringResource(R.string.settings_general_battery),
                        summary = "Optimize battery usage",
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // ═══ 3. Updates ═══
            item { SmallTitle(text = stringResource(R.string.update_check_title)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperSwitch(
                        title = stringResource(R.string.settings_auto_update),
                        summary = stringResource(R.string.settings_auto_update_desc),
                        checked = autoUpdateEnabled,
                        onCheckedChange = {
                            autoUpdateEnabled = it
                            UpdateChecker.setAutoUpdateEnabled(context, it)
                        }
                    )

                    var prereleaseEnabled by remember { mutableStateOf(UpdateChecker.isPrereleaseEnabled(context)) }
                    var showPrereleaseDialog by remember { mutableStateOf(false) }

                    SuperSwitch(
                        title = stringResource(R.string.settings_prerelease_update),
                        summary = stringResource(R.string.settings_prerelease_update_desc),
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
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showPrereleaseDialog = false },
                            title = { androidx.compose.material3.Text(stringResource(R.string.dialog_prerelease_warning_title)) },
                            text = { androidx.compose.material3.Text(stringResource(R.string.dialog_prerelease_warning_message)) },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    prereleaseEnabled = true
                                    UpdateChecker.setPrereleaseEnabled(context, true)
                                    showPrereleaseDialog = false
                                }) { androidx.compose.material3.Text(stringResource(android.R.string.ok)) }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { showPrereleaseDialog = false }) {
                                    androidx.compose.material3.Text(stringResource(android.R.string.cancel))
                                }
                            }
                        )
                    }

                    SuperArrow(
                        title = stringResource(R.string.update_check_title),
                        summary = "Check for updates now",
                        onClick = onCheckUpdate
                    )
                }
            }

            // ═══ 4. Help & About ═══
            item { SmallTitle(text = stringResource(R.string.settings_help_about_header)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.faq_title),
                        summary = "Frequently asked questions",
                        onClick = { context.startActivity(Intent(context, FAQActivity::class.java)) }
                    )
                    SuperArrow(
                        title = stringResource(R.string.settings_feedback),
                        summary = "Send feedback or report issues",
                        onClick = {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://francogiudans.github.io/CapsulyricFeed/"))
                            context.startActivity(browserIntent)
                        }
                    )
                    SuperArrow(
                        title = stringResource(R.string.settings_about_github),
                        summary = "View source on GitHub",
                        onClick = {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FrancoGiudans/Capsulyric"))
                            context.startActivity(browserIntent)
                        }
                    )

                    // Version
                    BasicComponent(
                        title = stringResource(R.string.about_version),
                        summary = updateVersionText
                    )

                    // Build Number (Dev Trigger)
                    var devStepCount by remember { mutableIntStateOf(0) }
                    val isDevMode = remember { prefs.getBoolean("dev_mode_enabled", false) }
                    var showLogs by remember { mutableStateOf(BuildConfig.DEBUG || isDevMode) }

                    BasicComponent(
                        title = stringResource(R.string.about_commit),
                        summary = updateBuildText,
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
                        SuperArrow(
                            title = stringResource(R.string.settings_console_log),
                            summary = "View logs",
                            onClick = onShowLogs
                        )
                    }
                }
            }
        }
    }

    // --- Dialogs (reuse Material3 dialogs) ---
    if (showPrivacyDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { androidx.compose.material3.Text(stringResource(R.string.dialog_privacy_title)) },
            text = { androidx.compose.material3.Text(stringResource(R.string.dialog_privacy_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showPrivacyDialog = false
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { androidx.compose.material3.Text(stringResource(R.string.dialog_btn_understand)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showPrivacyDialog = false }) {
                    androidx.compose.material3.Text(stringResource(R.string.dialog_btn_cancel))
                }
            }
        )
    }
}
