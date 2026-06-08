@file:Suppress("UnusedMaterial3ScaffoldPaddingParameter")

package com.example.islandlyrics.feature.settings.miuix

import com.example.islandlyrics.ui.miuix.MiuixBackHandler
import android.annotation.SuppressLint
import android.app.Activity
import com.example.islandlyrics.R
import com.example.islandlyrics.core.feed.CommunityFeedItem
import com.example.islandlyrics.core.update.UpdateChecker
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.feature.faq.FAQActivity
import com.example.islandlyrics.feature.settings.AboutActivity
import com.example.islandlyrics.feature.settings.CommunityDialogState
import com.example.islandlyrics.feature.settings.CommunityMarkdownBody
import com.example.islandlyrics.feature.settings.buildCommunityMarkdown
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference as SuperArrow
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as SuperDropdown
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.example.islandlyrics.feature.update.miuix.MiuixUpdateDialog
import com.example.islandlyrics.ui.miuix.*
import com.example.islandlyrics.core.settings.SettingsBackupManager
import com.example.islandlyrics.core.settings.SettingsBackupManager.ParserConflict
import com.example.islandlyrics.core.settings.BackupCategories
import com.example.islandlyrics.core.settings.SettingsBackupManager.PreviewResult
import com.example.islandlyrics.data.LyricRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import top.yukonga.miuix.kmp.basic.Snackbar as MiuixSnackbar
import top.yukonga.miuix.kmp.basic.SnackbarHost as MiuixSnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import java.util.Locale

@Composable
@SuppressLint("BatteryLife")
@Suppress("UNUSED_PARAMETER")
fun MiuixSettingsScreen(
    onCheckUpdate: () -> Unit,
    onShowDiagnostics: () -> Unit,
    updateVersionText: String,
    updateCodenameText: String,
    updateBuildText: String,
    onOpenCustomSettings: () -> Unit = {},
    onOpenFaq: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
    onOpenLocalLyricDirectory: ((Uri, String) -> Unit)? = null,
    showBackButton: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    onBottomBarVisibilityChange: (Boolean) -> Unit = {},
    updateReleaseInfo: UpdateChecker.ReleaseInfo? = null,
    onUpdateDismiss: () -> Unit = {},
    onUpdateIgnore: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    val snackbarHostState = remember { MiuixSnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val backupExportSuccessFormat = stringResource(R.string.settings_backup_export_success)
    val backupExportSuccessWithCacheFormat = stringResource(R.string.settings_backup_export_success_with_cache)
    val backupExportFailedText = stringResource(R.string.settings_backup_export_failed)
    val backupImportSuccessFormat = stringResource(R.string.settings_backup_import_success)
    val backupImportSuccessWithCacheFormat = stringResource(R.string.settings_backup_import_success_with_cache)
    val backupImportFailedText = stringResource(R.string.settings_backup_import_failed)
    val devModeEnabled by LyricRepository.getInstance().devModeEnabled.observeAsState(false)

    // Backup category selection states
    var showExportCategoryDialog by remember { mutableStateOf(false) }
    var selectedExportCategories by remember {
        mutableStateOf(BackupCategories.ALL_CATEGORIES.flatMap { c ->
            if (c.subGroups.isNotEmpty()) c.subGroups.map { it.id } else listOf(c.id)
        }.toSet())
    }
    var showImportPreviewDialog by remember { mutableStateOf(false) }
    var importPreviewResult by remember { mutableStateOf<PreviewResult?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImportCategories by remember { mutableStateOf(setOf<String>()) }

    var showParserConflictDialog by remember { mutableStateOf(false) }
    var parserConflicts by remember { mutableStateOf<List<ParserConflict>>(emptyList()) }
    var pendingConflictImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingConflictSelections by remember { mutableStateOf(setOf<String>()) }
    var conflictKeepExisting by remember { mutableStateOf(setOf<String>()) }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val includeLyricCache = selectedExportCategories.contains("lyric_cache")
            val result = SettingsBackupManager.exportToZip(context, uri, selectedExportCategories, includeLyricCache)
            val message = if (result.success) {
                if (result.lyricCacheCount > 0) {
                    String.format(Locale.getDefault(), backupExportSuccessWithCacheFormat, result.exportedCount, result.lyricCacheCount)
                } else {
                    String.format(Locale.getDefault(), backupExportSuccessFormat, result.exportedCount)
                }
            } else {
                backupExportFailedText
            }
            snackbarHostState.showSnackbar(message = message)
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
                val dynamicCategoriesList = BackupCategories.ALL_CATEGORIES.map { cat ->
                    when {
                        cat.id == "parser_rules" -> {
                            val parserJson = readParserJsonForPreviewMiuix(context, uri)
                            cat.copy(subGroups = BackupCategories.parserAppSubGroupsFromJson(parserJson))
                        }
                        cat.id == "lyric_cache" && preview.lyricCacheEntryCount != 0 -> cat
                        else -> cat
                    }
                }
                selectedImportCategories = preview.categoryCounts.keys.flatMap { catId ->
                    val cat = dynamicCategoriesList.find { it.id == catId }
                    if (cat != null && cat.subGroups.isNotEmpty()) {
                        cat.subGroups.map { it.id }
                    } else {
                        listOf(catId)
                    }
                }.toSet()
                if (preview.lyricCacheEntryCount != 0) {
                    selectedImportCategories = selectedImportCategories + "lyric_cache"
                }
                showImportPreviewDialog = true
            } else {
                snackbarHostState.showSnackbar(message = backupImportFailedText)
            }
        }
    }

    // State
    var dynamicIconEnabled by remember { mutableStateOf(prefs.getBoolean("dynamic_icon_enabled", false)) }

    val showPrivacyDialog = remember { mutableStateOf(false) }
    val localLyricDirState = rememberLocalLyricDirectoriesState()
    val showFeedbackPopup = remember { mutableStateOf(false) }

    MiuixBackHandler(enabled = showPrivacyDialog.value) { showPrivacyDialog.value = false }
    MiuixBackHandler(enabled = showFeedbackPopup.value) { showFeedbackPopup.value = false }

    val isHyperOsSupported = remember { RomUtils.isHyperOsVersionAtLeast(3, 0, 300) }

    fun checkNotificationPermission(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    fun checkPostNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    var notificationGranted by remember { mutableStateOf(checkNotificationPermission()) }
    var postNotificationGranted by remember { mutableStateOf(checkPostNotificationPermission()) }

    LaunchedEffect(isHyperOsSupported) {
        if (!isHyperOsSupported) {
            if (dynamicIconEnabled) {
                dynamicIconEnabled = false
                prefs.edit { putBoolean("dynamic_icon_enabled", false) }
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

    LaunchedEffect(listState) {
        var previousIndex = 0
        var previousOffset = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .map { (index, offset) ->
                val delta = when {
                    index != previousIndex -> (index - previousIndex) * 10_000 + (offset - previousOffset)
                    else -> offset - previousOffset
                }
                previousIndex = index
                previousOffset = offset
                delta
            }
            .distinctUntilChanged()
            .collect { delta ->
                when {
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 -> onBottomBarVisibilityChange(true)
                    delta > 6 -> onBottomBarVisibilityChange(false)
                    delta < -6 -> onBottomBarVisibilityChange(true)
                }
            }
    }

    val popupShowing = showPrivacyDialog.value ||
            showFeedbackPopup.value ||
            updateReleaseInfo != null

    LaunchedEffect(popupShowing) {
        if (popupShowing) {
            onBottomBarVisibilityChange(false)
        } else if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            onBottomBarVisibilityChange(true)
        }
    }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = stringResource(R.string.title_app_settings),
                largeTitle = stringResource(R.string.title_app_settings),
                scrollBehavior = scrollBehavior,
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = { (context as? Activity)?.finish() }, modifier = Modifier.padding(start = 12.dp)) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MiuixTheme.colorScheme.onBackground
                            )
                        }
                    }
                } else {
                    {}
                }
            )
        },
        snackbarHost = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 82.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                MiuixSnackbarHost(
                    state = snackbarHostState,
                    content = { data -> MiuixSnackbar(data = data) }
                )
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .miuixPageScroll(scrollBehavior),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 116.dp
            )
        ) {
            // ═══ 1. Personalization ═══
            item { SmallTitle(text = stringResource(R.string.settings_personalization_header)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.page_title_personalization),
                        onClick = onOpenCustomSettings
                    )
                }
            }

            // ═══ 2. General ═══
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
                            prefs.edit { putString("language_code", code) }
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
                            prefs.edit { putBoolean("recommend_media_app", it) }
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
                            prefs.edit { putBoolean("hide_recents_enabled", it) }
                        }
                    )
                }
            }

            // ═══ 3. System & Permissions ═══
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
                            intent.data = "package:${context.packageName}".toUri()
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // ═══ Local Lyrics ═══
            item {
                MiuixLocalLyricDirectoriesContent(
                    state = localLyricDirState,
                    onOpenDirectory = onOpenLocalLyricDirectory
                )
            }
            item { SmallTitle(text = stringResource(R.string.settings_backup_header)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.settings_backup_export),
                        summary = stringResource(R.string.settings_backup_export_desc),
                        onClick = {
                            showExportCategoryDialog = true
                        }
                    )
                    SuperArrow(
                        title = stringResource(R.string.settings_backup_import),
                        summary = stringResource(R.string.settings_backup_import_desc),
                        onClick = {
                            importSettingsLauncher.launch(arrayOf("application/zip", "application/json", "*/*"))
                        }
                    )
                }
            }

            // ═══ 5. Help & About ═══
            item { SmallTitle(text = stringResource(R.string.settings_help_about_header)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.faq_title),
                        summary = stringResource(R.string.summary_faq),
                        onClick = {
                            onOpenFaq?.invoke()
                                ?: context.startActivity(Intent(context, FAQActivity::class.java))
                        }
                    )
                    SuperArrow(
                        title = stringResource(R.string.community_about_title),
                        summary = stringResource(R.string.settings_about_capsulyric),
                        onClick = {
                            onOpenAbout?.invoke()
                                ?: context.startActivity(Intent(context, AboutActivity::class.java))
                        }
                    )
                }
            }

            if (devModeEnabled) {
                item { SmallTitle(text = stringResource(R.string.settings_developer_mode_header)) }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        SuperArrow(
                            title = stringResource(R.string.title_diagnostics),
                            summary = stringResource(R.string.summary_diagnostics),
                            onClick = onShowDiagnostics
                        )
                    }
                }
            }
        }

        // --- Dialogs (must be inside Scaffold content for MiuixPopupHost) ---
        MiuixBlurDialog(
            title = stringResource(R.string.dialog_privacy_title),
            show = showPrivacyDialog.value,
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

        if (updateReleaseInfo != null) {
            MiuixUpdateDialog(
                show = true,
                releaseInfo = updateReleaseInfo,
                onDismiss = onUpdateDismiss,
                onIgnore = onUpdateIgnore
            )
        }

        MiuixLocalLyricDirectoriesDialog(localLyricDirState)

        // Backup category export dialog
        MiuixBackupCategoryDialog(
            show = showExportCategoryDialog,
            titleRes = R.string.backup_dialog_export_title,
            categories = BackupCategories.ALL_CATEGORIES.map { cat ->
                if (cat.id == "parser_rules") cat.copy(subGroups = BackupCategories.parserAppSubGroups(context))
                else cat
            }.also { cats ->
                // Default to all leaf IDs for export
                if (showExportCategoryDialog) {
                    selectedExportCategories = cats.flatMap { c ->
                        if (c.subGroups.isNotEmpty()) c.subGroups.map { it.id } else listOf(c.id)
                    }.toSet()
                }
            },
            initialSelected = if (showExportCategoryDialog) selectedExportCategories else emptySet(),
            onConfirm = { selected ->
                selectedExportCategories = selected
                showExportCategoryDialog = false
                exportSettingsLauncher.launch(SettingsBackupManager.buildExportFileName())
            },
            onDismiss = { showExportCategoryDialog = false }
        )

        // Backup category import preview dialog
        MiuixBackupCategoryDialog(
            show = showImportPreviewDialog && importPreviewResult != null,
            titleRes = R.string.backup_dialog_import_title,
            categories = BackupCategories.ALL_CATEGORIES.map { cat ->
                when {
                    cat.id == "parser_rules" -> {
                        val uri = pendingImportUri
                        val parserJson = if (uri != null) {
                            remember(uri) { readParserJsonForPreviewMiuix(context, uri) }
                        } else null
                        cat.copy(subGroups = BackupCategories.parserAppSubGroupsFromJson(parserJson))
                    }
                    cat.id == "lyric_cache" && (importPreviewResult?.lyricCacheEntryCount ?: 0) != 0 -> cat
                    else -> cat
                }
            },
            categoryKeyCounts = importPreviewResult?.categoryCounts,
            initialSelected = selectedImportCategories,
            onConfirm = { selected ->
                selectedImportCategories = selected
                showImportPreviewDialog = false
                val uri = pendingImportUri ?: return@MiuixBackupCategoryDialog
                val isZip = importPreviewResult?.isZip == true
                coroutineScope.launch {
                    val result = if (isZip) {
                        SettingsBackupManager.importFromZip(context, uri, selected)
                    } else {
                        SettingsBackupManager.importSelected(context, uri, selected)
                    }
                    if (result.success && result.parserConflicts.isNotEmpty()) {
                        parserConflicts = result.parserConflicts
                        pendingConflictImportUri = uri
                        pendingConflictSelections = selected
                        conflictKeepExisting = emptySet()
                        showParserConflictDialog = true
                    } else {
                        val message = if (result.success) {
                            if (result.lyricCacheCount > 0) {
                                String.format(Locale.getDefault(), backupImportSuccessWithCacheFormat, result.importedCount, result.lyricCacheCount)
                            } else {
                                String.format(Locale.getDefault(), backupImportSuccessFormat, result.importedCount)
                            }
                        } else backupImportFailedText
                        snackbarHostState.showSnackbar(message = message)
                    }
                    pendingImportUri = null
                    importPreviewResult = null
                }
            },
            onDismiss = {
                showImportPreviewDialog = false
                pendingImportUri = null
                importPreviewResult = null
            }
        )

        // Parser conflict dialog (MIUIX)
        if (showParserConflictDialog && parserConflicts.isNotEmpty()) {
            MiuixBlurDialog(
                title = stringResource(R.string.backup_conflict_title),
                show = true,
                onDismissRequest = { showParserConflictDialog = false }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.backup_conflict_description),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
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
                                state = if (conflict.packageName in conflictKeepExisting)
                                    androidx.compose.ui.state.ToggleableState.On
                                else androidx.compose.ui.state.ToggleableState.Off,
                                onClick = {
                                    conflictKeepExisting = if (conflict.packageName in conflictKeepExisting) {
                                        conflictKeepExisting - conflict.packageName
                                    } else {
                                        conflictKeepExisting + conflict.packageName
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(conflict.displayName, color = MiuixTheme.colorScheme.onSurface,
                                    fontSize = MiuixTheme.textStyles.body2.fontSize)
                                Text(conflict.packageName, color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    fontSize = MiuixTheme.textStyles.body2.fontSize)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            text = stringResource(R.string.backup_dialog_cancel),
                            onClick = { showParserConflictDialog = false; pendingConflictImportUri = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.onSurfaceVariantActions)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(
                            text = stringResource(R.string.backup_conflict_apply),
                            onClick = {
                                showParserConflictDialog = false
                                val uri = pendingConflictImportUri ?: return@TextButton
                                coroutineScope.launch {
                                    val parserJson = readParserJsonFromFile(context, uri)
                                    val allConflictPkgs = parserConflicts.map { it.packageName }.toSet()
                                    val packagesToKeep = allConflictPkgs - conflictKeepExisting
                                    SettingsBackupManager.resolveParserConflicts(context, parserJson, packagesToKeep)
                                    snackbarHostState.showSnackbar(message =
                                        String.format(Locale.getDefault(), backupImportSuccessFormat, pendingConflictSelections.size))
                                    pendingConflictImportUri = null
                                    pendingConflictSelections = emptySet()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }
    }
}

/** Read parser_rules_json from a backup file (ZIP or legacy JSON). */
private fun readParserJsonForPreviewMiuix(context: Context, uri: Uri): String {
    return kotlinx.coroutines.runBlocking { readParserJsonFromFile(context, uri) }
}

private suspend fun readParserJsonFromFile(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    try {
        // Check if ZIP
        val header = ByteArray(4)
        val isZip = context.contentResolver.openInputStream(uri)?.use { input ->
            if (input.read(header) == 4) {
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            } else false
        } ?: false

        if (isZip) {
            val tempDir = java.io.File(context.cacheDir, "parser_preview_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.util.zip.ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (entry.name == "settings.json" && !entry.isDirectory) {
                                val targetFile = java.io.File(tempDir, "settings.json")
                                targetFile.outputStream().use { fileOut -> zip.copyTo(fileOut) }
                                break
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
                val settingsFile = java.io.File(tempDir, "settings.json")
                if (settingsFile.exists()) {
                    val text = settingsFile.readText(Charsets.UTF_8)
                    return@withContext extractParserJsonMiuix(text)
                }
            } finally {
                tempDir.deleteRecursively()
            }
        } else {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText()
                ?: return@withContext "[]"
            return@withContext extractParserJsonMiuix(text)
        }
        "[]"
    } catch (_: Exception) { "[]" }
}

private fun extractParserJsonMiuix(text: String): String {
    val root = org.json.JSONObject(text)
    val schemaVersion = root.optInt("schema_version", 1)
    val rawValue = if (schemaVersion >= 2 && root.has("categories")) {
        val catObj = root.optJSONObject("categories")
        val parserBlock = catObj?.optJSONObject("parser_rules")
        parserBlock?.optJSONArray("parsers")
            ?: parserBlock?.optJSONObject("preferences")?.opt("parser_rules_json")
    } else {
        val prefsJson = root.optJSONObject("preferences") ?: root
        prefsJson.opt("parser_rules_json")
    }
    return SettingsBackupManager.parserRulesJsonFromBackupValue(rawValue) ?: "[]"
}

@Composable
fun CommunityArrowItem(
    title: String,
    item: CommunityFeedItem,
    fallbackSummary: String,
    onClick: () -> Unit
) {
    val summaryLines = buildList {
        add(item.title)
        item.summary.takeIf { it.isNotBlank() }?.let { add(it) }
    }

    SuperArrow(
        title = title,
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
    val textColor = MiuixTheme.colorScheme.onSurface.toArgb()
    val hasUrl = state.item.hasUrl
    val openText = state.item.actionText.takeIf { it.isNotBlank() } ?: stringResource(R.string.community_dialog_open)

    MiuixBlurDialog(
        title = state.sectionTitle,
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = state.item.title,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    color = MiuixTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                CommunityMarkdownBody(
                    markdown = markdown,
                    textColor = textColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = stringResource(R.string.community_dialog_close),
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (hasUrl) {
                    TextButton(
                        text = openText,
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}
