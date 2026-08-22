/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

@file:Suppress("UnusedMaterial3ScaffoldPaddingParameter")

package com.example.islandlyrics.feature.settings.material



import com.example.islandlyrics.ui.material.blur.MaterialBlurDropdownMenu

import com.example.islandlyrics.ui.material.blur.MaterialBlurAlertDialog

import android.annotation.SuppressLint
import android.app.Activity
import com.example.islandlyrics.R
import com.example.islandlyrics.core.feed.CommunityFeedItem
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.feature.faq.FAQActivity
import com.example.islandlyrics.feature.lastfm.LastFmSettingsActivity
import com.example.islandlyrics.feature.settings.AboutActivity
import com.example.islandlyrics.feature.settings.CommunityDialogState
import com.example.islandlyrics.feature.settings.CommunityMarkdownBody
import com.example.islandlyrics.feature.settings.ParserBackupPreviewReader
import com.example.islandlyrics.feature.settings.buildCommunityMarkdown
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider as MaterialHorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.core.settings.LauncherAliasManager
import com.example.islandlyrics.core.settings.SettingsBackupManager
import com.example.islandlyrics.core.settings.SettingsBackupManager.ParserConflict
import com.example.islandlyrics.core.settings.BackupCategories
import com.example.islandlyrics.core.settings.SettingsBackupManager.PreviewResult
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.runtime.service.MediaMonitorService
import com.example.islandlyrics.runtime.playingapp.NewPlayingAppNotifier
import com.example.islandlyrics.ui.material.blur.MaterialBlurScaffold
import com.example.islandlyrics.ui.theme.material.MaterialBlurTopAppBar
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("BatteryLife")
@Suppress("UNUSED_PARAMETER")
fun SettingsScreen(
    onCheckUpdate: () -> Unit,
    onShowDiagnostics: () -> Unit,
    updateVersionText: String,
    updateCodenameText: String,
    updateBuildText: String,
    onOpenCustomSettings: () -> Unit = {},
    onOpenCapsuleNotification: () -> Unit = {},
    onOpenDesktopLyrics: (() -> Unit)? = null,
    onOpenCommunity: (() -> Unit)? = null,
    onOpenFaq: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
    onOpenLocalLyricDirectories: () -> Unit = {},
    onOpenLocalLyricDirectory: ((Uri, String) -> Unit)? = null,
    onOpenOnlineLyricRematch: (() -> Unit)? = null,
    onOpenLastFm: (() -> Unit)? = null,
    onOpenCacheManagement: (() -> Unit)? = null,
    onOpenLab: (() -> Unit)? = null,
    showBackButton: Boolean = true,
    extraBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val backupExportSuccessFormat = stringResource(R.string.settings_backup_export_success)
    val backupExportSuccessWithCacheFormat = stringResource(R.string.settings_backup_export_success_with_cache)
    val backupExportSuccessWithSensitiveFormat = stringResource(R.string.settings_backup_export_success_with_sensitive)
    val backupExportFailedText = stringResource(R.string.settings_backup_export_failed)
    val backupImportSuccessFormat = stringResource(R.string.settings_backup_import_success)
    val backupImportSuccessWithCacheFormat = stringResource(R.string.settings_backup_import_success_with_cache)
    val backupImportSuccessWithSensitiveFormat = stringResource(R.string.settings_backup_import_success_with_sensitive)
    val backupImportFailedText = stringResource(R.string.settings_backup_import_failed)
    val sensitivePasswordInvalidText = stringResource(R.string.backup_sensitive_password_invalid)
    val devModeEnabled by LyricRepository.getInstance().devModeEnabled.observeAsState(false)
    var floatingLyricsLabEnabled by remember { mutableStateOf(LabFeatureManager.isFloatingLyricsEnabled(prefs)) }
    val offlineModeEnabled = OfflineModeManager.isEnabled(context)

    // Backup category selection states
    var showExportCategoryDialog by remember { mutableStateOf(false) }
    var selectedExportCategories by remember {
        mutableStateOf(BackupCategories.ALL_CATEGORIES.flatMap { c ->
            if (c.subGroups.isNotEmpty()) c.subGroups.map { it.id } else listOf(c.id)
        }.toSet())
    }
    var selectedSensitiveExportItems by remember { mutableStateOf(emptySet<String>()) }
    var pendingSensitiveExportPassword by remember { mutableStateOf<CharArray?>(null) }
    var showSensitiveExportPasswordDialog by remember { mutableStateOf(false) }
    var showImportPreviewDialog by remember { mutableStateOf(false) }
    var importPreviewResult by remember { mutableStateOf<PreviewResult?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImportCategories by remember { mutableStateOf(setOf<String>()) }
    var selectedSensitiveImportItems by remember { mutableStateOf(emptySet<String>()) }
    var pendingSensitiveImportPassword by remember { mutableStateOf<CharArray?>(null) }
    var showSensitiveImportPasswordDialog by remember { mutableStateOf(false) }

    // Parser conflict resolution state
    var showParserConflictDialog by remember { mutableStateOf(false) }
    var showHideLauncherDialog by remember { mutableStateOf(false) }
    var parserConflicts by remember { mutableStateOf<List<ParserConflict>>(emptyList()) }
    var pendingConflictImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingConflictSelections by remember { mutableStateOf(setOf<String>()) }
    var conflictKeepExisting by remember { mutableStateOf(setOf<String>()) }

    fun importBackup(
        uri: Uri,
        preview: PreviewResult,
        selectedLeafIds: Set<String>,
        selectedSensitiveItemIds: Set<String>,
        sensitivePassword: CharArray? = null
    ) {
        coroutineScope.launch {
            val result = try {
                if (preview.isZip) {
                    SettingsBackupManager.importFromZip(
                        context,
                        uri,
                        selectedLeafIds,
                        selectedSensitiveItemIds,
                        sensitivePassword
                    )
                } else {
                    SettingsBackupManager.importSelected(context, uri, selectedLeafIds)
                }
            } finally {
                if (!preview.isZip) sensitivePassword?.fill('\u0000')
            }
            if (result.success && result.parserConflicts.isNotEmpty()) {
                parserConflicts = result.parserConflicts
                pendingConflictImportUri = uri
                pendingConflictSelections = selectedLeafIds
                conflictKeepExisting = emptySet()
                showParserConflictDialog = true
            } else {
                val message = if (result.success) {
                    if (result.sensitiveItemCount > 0) {
                        String.format(
                            Locale.getDefault(),
                            backupImportSuccessWithSensitiveFormat,
                            result.importedCount,
                            result.lyricCacheCount,
                            result.sensitiveItemCount
                        )
                    } else if (result.lyricCacheCount > 0) {
                        String.format(
                            Locale.getDefault(),
                            backupImportSuccessWithCacheFormat,
                            result.importedCount,
                            result.lyricCacheCount
                        )
                    } else {
                        String.format(Locale.getDefault(), backupImportSuccessFormat, result.importedCount)
                    }
                } else {
                    backupImportFailedText
                }
                snackbarHostState.showSnackbar(message)
            }
            selectedSensitiveImportItems = emptySet()
            pendingImportUri = null
            importPreviewResult = null
        }
    }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) {
            pendingSensitiveExportPassword?.fill('\u0000')
            pendingSensitiveExportPassword = null
            selectedSensitiveExportItems = emptySet()
            return@rememberLauncherForActivityResult
        }
        val sensitivePassword = pendingSensitiveExportPassword
        pendingSensitiveExportPassword = null
        val sensitiveItemIds = selectedSensitiveExportItems
        selectedSensitiveExportItems = emptySet()
        coroutineScope.launch {
            val includeLyricCache = selectedExportCategories.contains("lyric_cache")
            val result = SettingsBackupManager.exportToZip(
                context,
                uri,
                selectedExportCategories,
                includeLyricCache,
                sensitiveItemIds,
                sensitivePassword
            )
            val message = if (result.success) {
                if (result.sensitiveItemCount > 0) {
                    String.format(
                        Locale.getDefault(),
                        backupExportSuccessWithSensitiveFormat,
                        result.exportedCount,
                        result.lyricCacheCount,
                        result.sensitiveItemCount
                    )
                } else if (result.lyricCacheCount > 0) {
                    String.format(Locale.getDefault(), backupExportSuccessWithCacheFormat, result.exportedCount, result.lyricCacheCount)
                } else {
                    String.format(Locale.getDefault(), backupExportSuccessFormat, result.exportedCount)
                }
            } else {
                backupExportFailedText
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    val importSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val preview = SettingsBackupManager.previewImportFile(context, uri)
            if (preview.success) {
                importPreviewResult = preview
                pendingImportUri = uri
                // Build dynamic categories list (including parser rules from backup file)
                val dynamicCategoriesList = BackupCategories.ALL_CATEGORIES.map { cat ->
                    when (cat.id) {
                        "parser_rules" -> {
                            val parserJson = ParserBackupPreviewReader.readBlocking(context, uri)
                            cat.copy(subGroups = BackupCategories.parserAppSubGroupsFromJson(parserJson))
                        }
                        else -> cat
                    }
                }
                // Convert category IDs to leaf IDs for the dialog - ALL selected by default
                selectedImportCategories = preview.categoryCounts.keys.flatMap { catId ->
                    val cat = dynamicCategoriesList.find { it.id == catId }
                    if (cat != null && cat.subGroups.isNotEmpty()) {
                        cat.subGroups.map { it.id }
                    } else {
                        listOf(catId)
                    }
                }.toSet()
                // Also include lyric_cache if present in ZIP
                if (preview.lyricCacheEntryCount != 0) {
                    selectedImportCategories = selectedImportCategories + "lyric_cache"
                }
                showImportPreviewDialog = true
            } else {
                snackbarHostState.showSnackbar(backupImportFailedText)
            }
        }
    }
    var dynamicIconEnabled by remember { mutableStateOf(prefs.getBoolean("dynamic_icon_enabled", false)) }
    var iconStyle by remember { mutableStateOf(prefs.getString("dynamic_icon_style", "classic") ?: "classic") }
    
    // Dialog State
    var showLanguageDropdown by remember { mutableStateOf(false) }
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

    // Check for HyperOS 3.0.300+
    val isHyperOsSupported = remember { RomUtils.isHyperOsVersionAtLeast(3, 0, 300) }
    val isHyperOs = remember { RomUtils.isHyperOs() }

    // Logic for permissions status
    fun checkNotificationPermission(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    fun checkPostNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    var notificationGranted by remember { mutableStateOf(checkNotificationPermission()) }
    var postNotificationGranted by remember { mutableStateOf(checkPostNotificationPermission()) }

    // ... (lifecycle observer) ...

    // Force disable unsupported features if they were previously enabled
    LaunchedEffect(isHyperOsSupported) {
        if (!isHyperOsSupported) {
            if (dynamicIconEnabled) {
                dynamicIconEnabled = false
                prefs.edit { putBoolean("dynamic_icon_enabled", false) }
            }
            if (actionStyle == "miplay") {
                actionStyle = "disabled"
                prefs.edit { putString("notification_actions_style", "disabled") }
            }
        }
    }
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationGranted = checkNotificationPermission()
                postNotificationGranted = checkPostNotificationPermission()
                floatingLyricsLabEnabled = LabFeatureManager.isFloatingLyricsEnabled(prefs)
            }
        }
        val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LabFeatureManager.KEY_FLOATING_LYRICS_ENABLED) {
                floatingLyricsLabEnabled = LabFeatureManager.isFloatingLyricsEnabled(prefs)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    MaterialBlurScaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 96.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                SnackbarHost(hostState = snackbarHostState)
            }
        },
        topBar = {
            MaterialBlurTopAppBar(
                title = { Text(stringResource(R.string.title_app_settings)) },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = { (context as? Activity)?.finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                } else {
                    {}
                },
            )
        },
        containerColor = materialPageContainerColor()
    ) { paddingValues ->
        val currentLangCode = ThemeHelper.getLanguage(context)
        val currentLangText = when (currentLangCode) {
            "en" -> stringResource(R.string.lang_english)
            "zh-CN" -> stringResource(R.string.lang_chinese)
            else -> stringResource(R.string.lang_sys_default)
        }
        var recommendMediaAppEnabled by remember { mutableStateOf(prefs.getBoolean("recommend_media_app", true)) }
        var hideRecentsEnabled by remember { mutableStateOf(prefs.getBoolean("hide_recents_enabled", false)) }
        var launcherHidden by remember { mutableStateOf(LauncherAliasManager.isHidden(context)) }
        var newPlayingAppAlertEnabled by remember { mutableStateOf(prefs.getBoolean(NewPlayingAppNotifier.PREF_ENABLED, false)) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = paddingValues.calculateStartPadding(layoutDirection),
                top = paddingValues.calculateTopPadding(),
                end = paddingValues.calculateEndPadding(layoutDirection),
                bottom = paddingValues.calculateBottomPadding() + 24.dp + extraBottomPadding,
            )
        ) {
            item { SettingsSectionHeader(text = stringResource(R.string.settings_core_header)) }
            item {
                SettingsCard {
                    SettingsActionItem(
                        title = stringResource(R.string.settings_capsule_notification),
                        icon = Icons.Filled.MusicNote,
                        onClick = onOpenCapsuleNotification
                    )
                    if (floatingLyricsLabEnabled) {
                        SettingsCardDivider()
                        SettingsActionItem(
                            title = stringResource(R.string.settings_floating_lyrics),
                            icon = Icons.Filled.MusicNote,
                            onClick = {
                                onOpenDesktopLyrics?.invoke() ?: onOpenCustomSettings()
                            }
                        )
                    }
                }
            }

            item { SettingsSectionHeader(text = stringResource(R.string.settings_general_header)) }
            item {
                SettingsCard {
                    SettingsActionItem(
                        title = stringResource(R.string.page_title_personalization),
                        icon = Icons.Filled.Palette,
                        onClick = onOpenCustomSettings
                    )
                    SettingsCardDivider()
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SettingsTextItem(
                            title = stringResource(R.string.settings_language),
                            value = currentLangText,
                            onClick = { showLanguageDropdown = true }
                        )
                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.CenterEnd)) {
                            MaterialBlurDropdownMenu(
                                expanded = showLanguageDropdown,
                                onDismissRequest = { showLanguageDropdown = false }
                            ) {
                                val languages = listOf(
                                    stringResource(R.string.lang_sys_default) to "",
                                    stringResource(R.string.lang_english) to "en",
                                    stringResource(R.string.lang_chinese) to "zh-CN"
                                )
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
                    SettingsCardDivider()
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_recommend_media_app),
                        subtitle = stringResource(R.string.settings_recommend_media_app_desc),
                        checked = recommendMediaAppEnabled,
                        onCheckedChange = {
                            recommendMediaAppEnabled = it
                            prefs.edit { putBoolean("recommend_media_app", it) }
                        }
                    )
                    SettingsCardDivider()
                    // Extension of the above: proactive notification when an
                    // unconfigured app is detected playing.
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_new_playing_app_alert),
                        subtitle = stringResource(R.string.settings_new_playing_app_alert_desc),
                        checked = newPlayingAppAlertEnabled,
                        onCheckedChange = {
                            newPlayingAppAlertEnabled = it
                            prefs.edit { putBoolean(NewPlayingAppNotifier.PREF_ENABLED, it) }
                            if (it) {
                                MediaMonitorService.triggerRecheck()
                            } else {
                                NewPlayingAppNotifier.cancelAll(context)
                            }
                        }
                    )
                    SettingsCardDivider()
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_hide_recents),
                        subtitle = stringResource(R.string.settings_hide_recents_desc),
                        checked = hideRecentsEnabled,
                        onCheckedChange = {
                            hideRecentsEnabled = it
                            prefs.edit { putBoolean("hide_recents_enabled", it) }
                        }
                    )
                    SettingsCardDivider()
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_hide_launcher),
                        subtitle = stringResource(R.string.settings_hide_launcher_desc),
                        checked = launcherHidden,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                showHideLauncherDialog = true
                            } else {
                                launcherHidden = false
                                LauncherAliasManager.setAliasEnabled(context, true)
                            }
                        }
                    )
                }
            }

            item { SettingsSectionHeader(text = stringResource(R.string.settings_permissions_header)) }
            item {
                SettingsCard {
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
                    SettingsCardDivider()
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
                    SettingsCardDivider()
                    SettingsActionItem(
                        title = stringResource(R.string.settings_general_battery),
                        icon = Icons.Filled.BatterySaver,
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = "package:${context.packageName}".toUri()
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // 歌词工具：本地歌词 + 歌词工具
            item { SettingsSectionHeader(text = stringResource(R.string.settings_lyric_tools_header)) }
            item {
                SettingsCard {
                    SettingsActionItem(
                        title = stringResource(R.string.settings_local_lyrics_title),
                        summary = stringResource(R.string.settings_local_lyrics_directories),
                        icon = Icons.Filled.Folder,
                        onClick = onOpenLocalLyricDirectories
                    )
                    SettingsCardDivider()
                    SettingsActionItem(
                        title = stringResource(R.string.title_cache_management),
                        summary = stringResource(R.string.settings_cache_management_desc),
                        icon = Icons.Filled.Storage,
                        onClick = {
                            onOpenCacheManagement?.invoke()
                                ?: context.startActivity(Intent(context, com.example.islandlyrics.feature.cache.CacheManagementActivity::class.java))
                        }
                    )
                    if (!offlineModeEnabled) {
                        SettingsCardDivider()
                        SettingsActionItem(
                            title = stringResource(R.string.online_lyric_rematch_title),
                            summary = stringResource(R.string.online_lyric_rematch_settings_desc),
                            icon = Icons.Filled.Refresh,
                            onClick = {
                                onOpenOnlineLyricRematch?.invoke()
                                    ?: context.startActivity(Intent(context, com.example.islandlyrics.feature.onlinelyricdebug.OnlineLyricDebugActivity::class.java))
                            }
                        )
                        SettingsCardDivider()
                        SettingsActionItem(
                            title = stringResource(R.string.lastfm_title),
                            summary = stringResource(R.string.lastfm_settings_summary),
                            icon = Icons.Filled.Link,
                            onClick = {
                                onOpenLastFm?.invoke()
                                ?: context.startActivity(Intent(context, LastFmSettingsActivity::class.java))
                            }
                        )
                        SettingsCardDivider()
                        SettingsActionItem(
                            title = stringResource(R.string.apple_music_settings_title),
                            summary = stringResource(R.string.apple_music_settings_summary),
                            icon = Icons.Filled.Link,
                            onClick = {
                                context.startActivity(Intent(context, com.example.islandlyrics.feature.applemusic.AppleMusicSettingsActivity::class.java))
                            }
                        )
                    }
                }
            }
            item { SettingsSectionHeader(text = stringResource(R.string.settings_backup_header)) }
            item {
                SettingsCard {
                    SettingsActionItem(
                        title = stringResource(R.string.settings_backup_export),
                        summary = stringResource(R.string.settings_backup_export_desc),
                        icon = Icons.Filled.Upload,
                        onClick = {
                            showExportCategoryDialog = true
                        }
                    )
                    SettingsCardDivider()
                    SettingsActionItem(
                        title = stringResource(R.string.settings_backup_import),
                        summary = stringResource(R.string.settings_backup_import_desc),
                        icon = Icons.Filled.Download,
                        onClick = {
                            importSettingsLauncher.launch(arrayOf("application/zip", "application/json", "*/*"))
                        }
                    )
                }
            }

            item { SettingsSectionHeader(text = stringResource(R.string.settings_help_about_header)) }
            item {
                SettingsCard {
                    SettingsActionItem(
                        title = stringResource(R.string.faq_title),
                        icon = Icons.AutoMirrored.Filled.Help,
                        onClick = {
                            onOpenFaq?.invoke()
                                ?: context.startActivity(Intent(context, FAQActivity::class.java))
                        }
                    )
                    SettingsCardDivider()
                    if (!offlineModeEnabled) {
                        SettingsActionItem(
                            title = stringResource(R.string.settings_community_header),
                            icon = Icons.Filled.Info,
                            onClick = {
                                onOpenCommunity?.invoke()
                            }
                        )
                        SettingsCardDivider()
                    }
                    SettingsActionItem(
                        title = stringResource(R.string.about_title),
                        icon = Icons.Filled.Info,
                        onClick = {
                            onOpenAbout?.invoke()
                                ?: context.startActivity(Intent(context, AboutActivity::class.java))
                        }
                    )
                }
            }

            if (devModeEnabled) {
                item { SettingsSectionHeader(text = stringResource(R.string.settings_developer_mode_header)) }
                item {
                    SettingsCard {
                        SettingsActionItem(
                            title = stringResource(R.string.title_diagnostics),
                            summary = stringResource(R.string.summary_diagnostics),
                            icon = Icons.Filled.BugReport,
                            onClick = onShowDiagnostics
                        )
                        SettingsCardDivider()
                        SettingsActionItem(
                            title = stringResource(R.string.title_lab),
                            summary = stringResource(R.string.diag_lab_page_desc),
                            icon = Icons.Filled.Science,
                            onClick = {
                                onOpenLab?.invoke()
                                    ?: context.startActivity(Intent(context, com.example.islandlyrics.feature.lab.LabActivity::class.java))
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        if (showPrivacyDialog) {
            MaterialBlurAlertDialog(
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


        if (showHideLauncherDialog) {
            MaterialBlurAlertDialog(
                onDismissRequest = { showHideLauncherDialog = false },
                title = { Text(stringResource(R.string.dialog_hide_launcher_title)) },
                text = { Text(stringResource(R.string.dialog_hide_launcher_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showHideLauncherDialog = false
                        launcherHidden = true
                        LauncherAliasManager.setAliasEnabled(context, false)
                        LauncherAliasManager.showAddTileToast(context)
                    }) {
                        Text(stringResource(R.string.dialog_hide_launcher_btn_hide_now))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHideLauncherDialog = false }) {
                        Text(stringResource(R.string.dialog_hide_launcher_btn_cancel))
                    }
                }
            )
        }
        if (showIconStyleDialog) {
            IconStyleSelectionDialog(
                currentStyle = iconStyle,
                isHyperOs = isHyperOs,
                onStyleSelected = { style ->
                    iconStyle = style
                    prefs.edit { putString("dynamic_icon_style", style) }
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
                    prefs.edit { putString("notification_actions_style", style) }
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
                    prefs.edit { putString("notification_click_style", style) }
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
                    prefs.edit { putLong("notification_dismiss_delay", delay) }
                    showDismissDelayDialog = false
                },
                onDismiss = { showDismissDelayDialog = false }
            )
        }

        // Backup category export dialog
        if (showExportCategoryDialog) {
            val publicCategories = BackupCategories.ALL_CATEGORIES.map { cat ->
                if (cat.id == "parser_rules") {
                    cat.copy(subGroups = BackupCategories.parserAppSubGroups(context))
                } else cat
            }
            val categories = publicCategories + listOfNotNull(
                SettingsBackupManager.sensitiveExportCategory(context)
            )
            val allLeafIds = publicCategories.flatMap { c ->
                if (c.subGroups.isNotEmpty()) c.subGroups.map { it.id } else listOf(c.id)
            }.toSet()
            BackupCategoryDialog(
                titleRes = R.string.backup_dialog_export_title,
                categories = categories,
                initialSelected = allLeafIds,
                onConfirm = { selected ->
                    val sensitiveItemIds = SettingsBackupManager.selectedSensitiveItemIds(selected)
                    selectedExportCategories = selected - sensitiveItemIds
                    selectedSensitiveExportItems = sensitiveItemIds
                    showExportCategoryDialog = false
                    if (sensitiveItemIds.isEmpty()) {
                        exportSettingsLauncher.launch(SettingsBackupManager.buildExportFileName())
                    } else {
                        showSensitiveExportPasswordDialog = true
                    }
                },
                onDismiss = {
                    selectedSensitiveExportItems = emptySet()
                    showExportCategoryDialog = false
                }
            )
        }

        // Backup category import preview dialog
        if (showImportPreviewDialog && importPreviewResult != null) {
            val preview = importPreviewResult!!
            val publicCategories = BackupCategories.ALL_CATEGORIES.map { cat ->
                when (cat.id) {
                    "parser_rules" -> {
                        val uri = pendingImportUri
                        val parserJson = if (uri != null) {
                            remember(uri) { ParserBackupPreviewReader.readBlocking(context, uri) }
                        } else null
                        cat.copy(subGroups = BackupCategories.parserAppSubGroupsFromJson(parserJson))
                    }
                    else -> cat
                }
            }
            val categories = publicCategories + listOfNotNull(
                SettingsBackupManager.sensitiveImportCategory(preview)
            )
            BackupCategoryDialog(
                titleRes = R.string.backup_dialog_import_title,
                categories = categories,
                categoryKeyCounts = preview.categoryCounts,
                initialSelected = selectedImportCategories,
                onConfirm = { selected ->
                    val sensitiveItemIds = SettingsBackupManager.selectedSensitiveItemIds(selected)
                    val selectedLeafIds = selected - sensitiveItemIds
                    selectedImportCategories = selected
                    showImportPreviewDialog = false
                    val uri = pendingImportUri ?: return@BackupCategoryDialog
                    if (sensitiveItemIds.isEmpty()) {
                        selectedSensitiveImportItems = emptySet()
                        importBackup(uri, preview, selectedLeafIds, emptySet())
                    } else {
                        selectedSensitiveImportItems = sensitiveItemIds
                        showSensitiveImportPasswordDialog = true
                    }
                },
                onDismiss = {
                    showImportPreviewDialog = false
                    pendingImportUri = null
                    importPreviewResult = null
                    selectedSensitiveImportItems = emptySet()
                }
            )
        }

        if (showSensitiveExportPasswordDialog) {
            SensitiveBackupPasswordDialog(
                title = stringResource(R.string.backup_sensitive_password_export_title),
                description = stringResource(R.string.backup_sensitive_password_export_description),
                requireConfirmation = true,
                onSubmit = { password ->
                    pendingSensitiveExportPassword?.fill('\u0000')
                    pendingSensitiveExportPassword = password.toCharArray()
                    null
                },
                onSuccess = {
                    showSensitiveExportPasswordDialog = false
                    exportSettingsLauncher.launch(SettingsBackupManager.buildExportFileName())
                },
                onDismiss = {
                    pendingSensitiveExportPassword?.fill('\u0000')
                    pendingSensitiveExportPassword = null
                    selectedSensitiveExportItems = emptySet()
                    showSensitiveExportPasswordDialog = false
                }
            )
        }

        if (showSensitiveImportPasswordDialog) {
            SensitiveBackupPasswordDialog(
                title = stringResource(R.string.backup_sensitive_password_import_title),
                description = stringResource(R.string.backup_sensitive_password_import_description),
                requireConfirmation = false,
                onSubmit = { password ->
                    val uri = pendingImportUri
                    val preview = importPreviewResult
                    if (uri == null || preview == null || !SettingsBackupManager.verifySensitiveImport(
                            context,
                            uri,
                            selectedSensitiveImportItems,
                            password.toCharArray()
                        )
                    ) {
                        sensitivePasswordInvalidText
                    } else {
                        pendingSensitiveImportPassword?.fill('\u0000')
                        pendingSensitiveImportPassword = password.toCharArray()
                        null
                    }
                },
                onSuccess = {
                    showSensitiveImportPasswordDialog = false
                    val uri = pendingImportUri
                    val preview = importPreviewResult
                    val password = pendingSensitiveImportPassword
                    pendingSensitiveImportPassword = null
                    if (uri != null && preview != null && password != null) {
                        val sensitiveItemIds = selectedSensitiveImportItems
                        importBackup(
                            uri,
                            preview,
                            selectedImportCategories - sensitiveItemIds,
                            sensitiveItemIds,
                            password
                        )
                    } else {
                        password?.fill('\u0000')
                        showImportPreviewDialog = true
                    }
                },
                onDismiss = {
                    pendingSensitiveImportPassword?.fill('\u0000')
                    pendingSensitiveImportPassword = null
                    showSensitiveImportPasswordDialog = false
                    showImportPreviewDialog = true
                }
            )
        }

        // Parser conflict resolution dialog
        if (showParserConflictDialog && parserConflicts.isNotEmpty()) {
            MaterialBlurAlertDialog(
                onDismissRequest = { showParserConflictDialog = false },
                title = { Text(stringResource(R.string.backup_conflict_title)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.backup_conflict_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        for (conflict in parserConflicts) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        conflictKeepExisting = if (conflict.packageName in conflictKeepExisting) {
                                            conflictKeepExisting - conflict.packageName
                                        } else {
                                            conflictKeepExisting + conflict.packageName
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = conflict.packageName in conflictKeepExisting,
                                    onCheckedChange = { checked ->
                                        conflictKeepExisting = if (checked) {
                                            conflictKeepExisting + conflict.packageName
                                        } else {
                                            conflictKeepExisting - conflict.packageName
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(conflict.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(conflict.packageName, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showParserConflictDialog = false
                        val uri = pendingConflictImportUri ?: return@TextButton
                        coroutineScope.launch {
                            val parserJson = ParserBackupPreviewReader.read(context, uri)
                            val allConflictPkgs = parserConflicts.map { it.packageName }.toSet()
                            val packagesToKeep = allConflictPkgs - conflictKeepExisting
                            SettingsBackupManager.resolveParserConflicts(context, parserJson, packagesToKeep)
                            snackbarHostState.showSnackbar(
                                String.format(Locale.getDefault(), backupImportSuccessFormat,
                                    pendingConflictSelections.size))
                            pendingConflictImportUri = null
                            pendingConflictSelections = emptySet()
                        }
                    }) { Text(stringResource(R.string.backup_conflict_apply)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showParserConflictDialog = false
                        pendingConflictImportUri = null
                    }) { Text(stringResource(R.string.backup_dialog_cancel)) }
                }
            )
        }
    }
}

@Composable
fun FeedbackSelectionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    MaterialBlurAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_feedback_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val browserIntent = Intent(Intent.ACTION_VIEW, "https://github.com/FrancoGiudans/Capsulyric/issues/new?template=bug_report.yml".toUri())
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
                            val browserIntent = Intent(Intent.ACTION_VIEW, "https://f.wps.cn/g/qACKW9I3/".toUri())
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val browserIntent = Intent(Intent.ACTION_VIEW, "https://v.wjx.cn/vm/rGK24xY.aspx".toUri())
                            context.startActivity(browserIntent)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dialog_feedback_wjx),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.dialog_feedback_wjx_desc),
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
        "media_controls" to stringResource(R.string.settings_click_action_media) to stringResource(R.string.settings_click_action_media_desc),
        "open_playing_app" to stringResource(R.string.settings_click_action_open_playing_app) to stringResource(R.string.settings_click_action_open_playing_app_desc)
    )

    MaterialBlurAlertDialog(
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

    MaterialBlurAlertDialog(
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
    val languages = listOf(
        stringResource(R.string.lang_sys_default) to "",
        stringResource(R.string.lang_english) to "en",
        stringResource(R.string.lang_chinese) to "zh-CN"
    )
    val context = LocalContext.current
    
    MaterialBlurAlertDialog(
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
    isHyperOs: Boolean,
    onStyleSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val styles = buildList {
        add(Triple("classic", R.string.icon_style_classic, R.string.icon_style_classic_desc))
        if (isHyperOs) add(Triple("advanced", R.string.icon_style_advanced, R.string.icon_style_advanced_desc))
        add(Triple("album_art", R.string.icon_style_album_art, R.string.icon_style_album_art_desc))
    }
    
    MaterialBlurAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_icon_style_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                styles.forEach { (styleId, nameId, descId) ->
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

    MaterialBlurAlertDialog(
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
fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

@Composable
fun SettingsCardDivider() {
    MaterialHorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun SettingsSectionHeader(text: String, marginTop: androidx.compose.ui.unit.Dp = 8.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
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
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick?.invoke() ?: onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
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
    )
}

@Composable
fun SettingsActionItem(
    title: String,
    icon: ImageVector,
    summary: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = summary?.let {
            {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
fun CommunityActionItem(
    title: String,
    item: CommunityFeedItem,
    fallbackSummary: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val summaryLines = buildList {
        add(item.title)
        item.summary.takeIf { it.isNotBlank() }?.let { add(it) }
    }

    SettingsActionItem(
        title = title,
        icon = icon,
        summary = summaryLines.joinToString("\n").ifBlank { fallbackSummary },
        onClick = onClick
    )
}

@Composable
fun CommunityDetailsDialog(
    state: CommunityDialogState,
    onDismiss: () -> Unit,
    onOpen: () -> Unit
) {
    val markdown = buildCommunityMarkdown(state.item)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hasUrl = state.item.hasUrl
    val openText = state.item.actionText.takeIf { it.isNotBlank() } ?: stringResource(R.string.community_dialog_open)

    MaterialBlurAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = state.sectionTitle,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = state.item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                CommunityMarkdownBody(
                    markdown = markdown,
                    textColor = textColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (hasUrl) {
                TextButton(onClick = onOpen) {
                    Text(openText)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.community_dialog_close))
            }
        }
    )
}



@Composable
fun SettingsTextItem(
    title: String,
    subtitle: String? = null,
    value: String,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
fun SettingsValueItem(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        headlineContent = {
            Text(
            text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        trailingContent = {
            Text(
            text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
private fun SensitiveBackupPasswordDialog(
    title: String,
    description: String,
    requireConfirmation: Boolean,
    onSubmit: suspend (String) -> String?,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val requiredText = stringResource(R.string.backup_sensitive_password_required)
    val mismatchText = stringResource(R.string.backup_sensitive_password_mismatch)
    val submitFailedText = stringResource(R.string.backup_sensitive_password_invalid)
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    MaterialBlurAlertDialog(
        onDismissRequest = {
            if (!submitting) onDismiss()
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.backup_sensitive_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = {
                            confirmation = it
                            error = null
                        },
                        label = { Text(stringResource(R.string.backup_sensitive_password_confirm)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = error != null,
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    when {
                        password.isBlank() -> error = requiredText
                        requireConfirmation && password != confirmation -> error = mismatchText
                        else -> {
                            coroutineScope.launch {
                                submitting = true
                                val submitError = try {
                                    onSubmit(password)
                                } catch (_: Exception) {
                                    submitFailedText
                                }
                                submitting = false
                                if (submitError == null) {
                                    password = ""
                                    confirmation = ""
                                    error = null
                                    onSuccess()
                                } else {
                                    error = submitError
                                }
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.backup_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) {
                Text(stringResource(R.string.backup_dialog_cancel))
            }
        }
    )
}

