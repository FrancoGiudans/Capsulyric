@file:Suppress("UnusedMaterial3ScaffoldPaddingParameter")

package com.example.islandlyrics.feature.settings.material

import android.annotation.SuppressLint
import android.app.Activity
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.feed.CommunityFeedItem
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.feature.faq.FAQActivity
import com.example.islandlyrics.feature.settings.AboutActivity
import com.example.islandlyrics.feature.settings.CommunityDialogState
import com.example.islandlyrics.feature.settings.CommunityMarkdownBody
import com.example.islandlyrics.feature.settings.buildCommunityMarkdown
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider as MaterialHorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors
import com.example.islandlyrics.core.settings.SettingsBackupManager
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
    onOpenFaq: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
    onOpenLocalLyricDirectory: ((Uri, String) -> Unit)? = null,
    showBackButton: Boolean = true,
    extraBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
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
            snackbarHostState.showSnackbar(message)
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
            snackbarHostState.showSnackbar(message)
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
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            MediumTopAppBar(
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
                scrollBehavior = scrollBehavior,
                colors = neutralMaterialTopBarColors()
            )
        },
        containerColor = materialPageContainerColor()
    ) { paddingValues ->
        val currentLangCode = prefs.getString("language_code", "")
        val currentLangText = when (currentLangCode) {
            "en" -> stringResource(R.string.lang_english)
            "zh-CN" -> stringResource(R.string.lang_chinese)
            else -> stringResource(R.string.lang_sys_default)
        }
        var recommendMediaAppEnabled by remember { mutableStateOf(prefs.getBoolean("recommend_media_app", true)) }
        var hideRecentsEnabled by remember { mutableStateOf(prefs.getBoolean("hide_recents_enabled", false)) }

        LazyColumn(
            modifier = Modifier
                .padding(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    bottom = 0.dp
                )
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp + extraBottomPadding)
        ) {
            item { SettingsSectionHeader(text = stringResource(R.string.settings_personalization_header)) }
            item {
                SettingsCard {
                    SettingsActionItem(
                        title = stringResource(R.string.page_title_personalization),
                        icon = Icons.Filled.Palette,
                        onClick = onOpenCustomSettings
                    )
                }
            }

            item { SettingsSectionHeader(text = stringResource(R.string.settings_general_header)) }
            item {
                SettingsCard {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SettingsTextItem(
                            title = stringResource(R.string.settings_language),
                            value = currentLangText,
                            onClick = { showLanguageDropdown = true }
                        )
                        Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.CenterEnd)) {
                            DropdownMenu(
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
                    SettingsActionItem(
                        title = stringResource(R.string.settings_backup_export),
                        summary = stringResource(R.string.settings_backup_export_desc),
                        icon = Icons.Filled.Sync,
                        onClick = {
                            exportSettingsLauncher.launch(SettingsBackupManager.buildExportFileName())
                        }
                    )
                    SettingsCardDivider()
                    SettingsActionItem(
                        title = stringResource(R.string.settings_backup_import),
                        summary = stringResource(R.string.settings_backup_import_desc),
                        icon = Icons.Filled.Sync,
                        onClick = {
                            importSettingsLauncher.launch(arrayOf("application/json"))
                        }
                    )
                }
            }

            item { SettingsSectionHeader(text = stringResource(R.string.settings_core_services_header)) }
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
                        icon = Icons.Filled.MusicNote,
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = "package:${context.packageName}".toUri()
                            context.startActivity(intent)
                        }
                    )
                }
            }

            item {
                com.example.islandlyrics.feature.settings.LocalLyricDirectoriesSection(
                    onOpenDirectory = onOpenLocalLyricDirectory
                )
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
                    SettingsActionItem(
                        title = stringResource(R.string.community_about_title),
                        icon = Icons.Filled.Info,
                        onClick = {
                            onOpenAbout?.invoke()
                                ?: context.startActivity(Intent(context, AboutActivity::class.java))
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
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
    val languages = listOf(
        stringResource(R.string.lang_sys_default) to "",
        stringResource(R.string.lang_english) to "en",
        stringResource(R.string.lang_chinese) to "zh-CN"
    )
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
    isHyperOs: Boolean,
    onStyleSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val styles = buildList {
        add(Triple("classic", R.string.icon_style_classic, R.string.icon_style_classic_desc))
        if (isHyperOs) add(Triple("advanced", R.string.icon_style_advanced, R.string.icon_style_advanced_desc))
        add(Triple("album_art", R.string.icon_style_album_art, R.string.icon_style_album_art_desc))
    }
    
    AlertDialog(
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

    AlertDialog(
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

