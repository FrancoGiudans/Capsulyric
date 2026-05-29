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
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
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
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference as SuperArrow
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as SuperDropdown
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.example.islandlyrics.feature.update.miuix.MiuixUpdateDialog
import com.example.islandlyrics.ui.miuix.*
import com.example.islandlyrics.core.settings.SettingsBackupManager
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
    val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", android.content.Context.MODE_PRIVATE) }
    val snackbarHostState = remember { MiuixSnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val backupExportSuccessFormat = stringResource(R.string.settings_backup_export_success)
    val backupExportFailedText = stringResource(R.string.settings_backup_export_failed)
    val backupImportSuccessFormat = stringResource(R.string.settings_backup_import_success)
    val backupImportFailedText = stringResource(R.string.settings_backup_import_failed)
    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = SettingsBackupManager.exportToUri(context, uri)
            val message = if (result.success) {
                String.format(Locale.getDefault(), backupExportSuccessFormat, result.exportedCount)
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
            val result = SettingsBackupManager.importFromUri(context, uri)
            val message = if (result.success) {
                String.format(Locale.getDefault(), backupImportSuccessFormat, result.importedCount)
            } else {
                backupImportFailedText
            }
            snackbarHostState.showSnackbar(message = message)
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
            MiuixSnackbarHost(
                state = snackbarHostState,
                content = { data -> MiuixSnackbar(data = data) }
            )
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
                    SuperArrow(
                        title = stringResource(R.string.settings_backup_export),
                        summary = stringResource(R.string.settings_backup_export_desc),
                        onClick = {
                            exportSettingsLauncher.launch(SettingsBackupManager.buildExportFileName())
                        }
                    )
                    SuperArrow(
                        title = stringResource(R.string.settings_backup_import),
                        summary = stringResource(R.string.settings_backup_import_desc),
                        onClick = {
                            importSettingsLauncher.launch(arrayOf("application/json"))
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
    }
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
