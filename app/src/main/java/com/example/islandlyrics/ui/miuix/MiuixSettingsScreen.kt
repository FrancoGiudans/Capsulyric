package com.example.islandlyrics.ui.miuix

import android.app.Activity
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.utils.UpdateChecker
import com.example.islandlyrics.utils.ThemeHelper
import com.example.islandlyrics.utils.RomUtils
import com.example.islandlyrics.ui.FAQActivity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import top.yukonga.miuix.kmp.extra.SuperListPopup
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.extra.SuperDialog
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

    val showPrivacyDialog = remember { mutableStateOf(false) }
    val showFeedbackPopup = remember { mutableStateOf(false) }

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
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            // ═══ 1. General ═══
            item { SmallTitle(text = stringResource(R.string.settings_general_header)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    // Language
                    val langOptions = listOf("", "en", "zh-CN")
                    val langNames = listOf(
                        stringResource(R.string.lang_sys_default),
                        stringResource(R.string.lang_english),
                        stringResource(R.string.lang_chinese)
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
                                showPrivacyDialog.value = true
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
                        summary = stringResource(R.string.summary_optimize_battery),
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

                    SuperArrow(
                        title = stringResource(R.string.update_check_title),
                        summary = stringResource(R.string.summary_check_updates_now),
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
                        summary = stringResource(R.string.summary_faq),
                        onClick = { context.startActivity(Intent(context, FAQActivity::class.java)) }
                    )
                    Box {
                        SuperArrow(
                            title = stringResource(R.string.settings_feedback),
                            summary = stringResource(R.string.summary_feedback),
                            onClick = {
                                showFeedbackPopup.value = true
                            }
                        )
                        SuperListPopup(
                            show = showFeedbackPopup,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = { showFeedbackPopup.value = false }
                        ) {
                            ListPopupColumn {
                                DropdownImpl(
                                    text = stringResource(R.string.dialog_feedback_github),
                                    optionSize = 2,
                                    isSelected = false,
                                    index = 0,
                                    onSelectedIndexChange = {
                                        showFeedbackPopup.value = false
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FrancoGiudans/Capsulyric/issues/new"))
                                        context.startActivity(browserIntent)
                                    }
                                )
                                DropdownImpl(
                                    text = stringResource(R.string.dialog_feedback_wps),
                                    optionSize = 2,
                                    isSelected = false,
                                    index = 1,
                                    onSelectedIndexChange = {
                                        showFeedbackPopup.value = false
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://f.wps.cn/g/qACKW9I3/"))
                                        context.startActivity(browserIntent)
                                    }
                                )
                            }
                        }
                    }
                    SuperArrow(
                        title = stringResource(R.string.settings_about_github),
                        summary = stringResource(R.string.summary_github),
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
                                Toast.makeText(context, context.getString(R.string.toast_dev_mode_steps, 7 - devStepCount), Toast.LENGTH_SHORT).show()
                            } else if (devStepCount == 7) {
                                prefs.edit().putBoolean("dev_mode_enabled", true).apply()
                                showLogs = true
                                Toast.makeText(context, context.getString(R.string.toast_dev_mode_enabled), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    if (showLogs) {
                        SuperArrow(
                            title = stringResource(R.string.settings_console_log),
                            summary = stringResource(R.string.summary_view_logs),
                            onClick = onShowLogs
                        )
                    }
                }
            }
        }
    }

    // --- Dialogs ---
    if (showPrivacyDialog.value) {
        SuperDialog(
            title = stringResource(R.string.dialog_privacy_title),
            show = showPrivacyDialog,
            onDismissRequest = { showPrivacyDialog.value = false }
        ) {
            androidx.compose.material3.Text(
                text = stringResource(R.string.dialog_privacy_message),
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = stringResource(R.string.dialog_btn_cancel),
                    onClick = { showPrivacyDialog.value = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.dialog_btn_understand),
                    onClick = {
                        showPrivacyDialog.value = false
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}
