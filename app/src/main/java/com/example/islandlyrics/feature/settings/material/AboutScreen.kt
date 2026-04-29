package com.example.islandlyrics.feature.settings.material

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.feed.CommunityFeed
import com.example.islandlyrics.core.feed.CommunityFeedRepository
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.update.UpdateChecker
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.feature.settings.CommunityDialogState
import com.example.islandlyrics.feature.update.material.UpdateDialog
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    updateVersionText: String,
    updateCodenameText: String,
    updateBuildText: String,
    onShowDiagnostics: () -> Unit,
    onCheckUpdate: () -> Unit,
    updateReleaseInfo: UpdateChecker.ReleaseInfo? = null,
    onUpdateDismiss: () -> Unit = {},
    onUpdateIgnore: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val devModeEnabled by LyricRepository.getInstance().devModeEnabled.observeAsState(false)
    val showDiagnostics = BuildConfig.DEBUG || devModeEnabled
    var showFeedbackDialog by remember { mutableStateOf(false) }
    val offlineModeEnabled = remember { OfflineModeManager.isEnabled(context) }
    var autoUpdateEnabled by remember { mutableStateOf(UpdateChecker.isAutoUpdateEnabled(context)) }
    var showChannelDropdown by remember { mutableStateOf(false) }
    var currentChannel by remember { mutableStateOf(UpdateChecker.getUpdateChannel(context)) }
    var experimentUpdatesEnabled by remember { mutableStateOf(LabFeatureManager.isExperimentUpdatesEnabled(context)) }
    var communityFeed by remember { mutableStateOf<CommunityFeed?>(null) }
    var communityFeedLoaded by remember { mutableStateOf(false) }
    var communityDialogState by remember { mutableStateOf<CommunityDialogState?>(null) }

    LaunchedEffect(offlineModeEnabled) {
        if (offlineModeEnabled) {
            communityFeed = null
            communityFeedLoaded = true
        } else {
            communityFeed = CommunityFeedRepository.fetchFeed(context)
            communityFeedLoaded = true
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.community_about_title)) },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = neutralMaterialTopBarColors()
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            val announcementSectionTitle = stringResource(R.string.community_announcement_title)
            val pollSectionTitle = stringResource(R.string.community_poll_title)

            SettingsSectionHeader(text = stringResource(R.string.settings_community_header), marginTop = 0.dp)

            if (!communityFeedLoaded) {
                SettingsActionItem(
                    title = stringResource(R.string.community_loading_title),
                    summary = stringResource(R.string.community_loading_desc),
                    icon = Icons.Filled.Info,
                    onClick = {}
                )
            } else {
                communityFeed?.announcements?.forEach { announcement ->
                    CommunityActionItem(
                        title = announcementSectionTitle,
                        item = announcement,
                        fallbackSummary = stringResource(R.string.community_open_in_browser),
                        icon = Icons.Filled.Info,
                        onClick = { communityDialogState = CommunityDialogState(announcementSectionTitle, announcement) }
                    )
                }

                communityFeed?.polls?.forEach { poll ->
                    CommunityActionItem(
                        title = pollSectionTitle,
                        item = poll,
                        fallbackSummary = stringResource(R.string.community_open_in_browser),
                        icon = Icons.Filled.Link,
                        onClick = { communityDialogState = CommunityDialogState(pollSectionTitle, poll) }
                    )
                }
            }

            if (!offlineModeEnabled) {
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

                SettingsTextItem(
                    title = stringResource(R.string.settings_prerelease_update),
                    value = currentChannel,
                    onClick = { showChannelDropdown = true }
                )
                Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.CenterEnd)) {
                    DropdownMenu(
                        expanded = showChannelDropdown,
                        onDismissRequest = { showChannelDropdown = false }
                    ) {
                        val channels = buildList {
                            add(UpdateChecker.CHANNEL_STABLE)
                            add(UpdateChecker.CHANNEL_PREVIEW)
                            if (experimentUpdatesEnabled) {
                                add(UpdateChecker.CHANNEL_EXPERIMENT)
                            }
                        }
                        channels.forEach { channel ->
                            DropdownMenuItem(
                                text = { Text(channel) },
                                onClick = {
                                    currentChannel = channel
                                    UpdateChecker.setUpdateChannel(context, channel)
                                    experimentUpdatesEnabled = LabFeatureManager.isExperimentUpdatesEnabled(context)
                                    showChannelDropdown = false
                                }
                            )
                        }
                    }
                }

                SettingsActionItem(
                    title = stringResource(R.string.update_check_title),
                    summary = stringResource(R.string.summary_check_updates_now),
                    icon = Icons.Filled.Sync,
                    onClick = onCheckUpdate
                )
            }

            SettingsSectionHeader(text = stringResource(R.string.about_title))

            if (!offlineModeEnabled) {
                SettingsActionItem(
                    title = stringResource(R.string.settings_feedback),
                    summary = stringResource(R.string.summary_feedback),
                    icon = Icons.AutoMirrored.Filled.Send,
                    onClick = { showFeedbackDialog = true }
                )

                SettingsActionItem(
                    title = stringResource(R.string.settings_about_github),
                    summary = stringResource(R.string.summary_github),
                    icon = Icons.Filled.Link,
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FrancoGiudans/Capsulyric"))
                        context.startActivity(browserIntent)
                    }
                )
            }

            // Version (tap to copy)
            SettingsValueItem(
                title = stringResource(R.string.about_version),
                value = updateVersionText,
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val copyText = "$updateVersionText（$updateCodenameText，$updateBuildText）"
                    cm.setPrimaryClip(ClipData.newPlainText("Version", copyText))
                    Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                }
            )

            SettingsValueItem(
                title = stringResource(R.string.about_codename),
                value = updateCodenameText
            )

            // Commit Hash (also dev-mode unlock trigger)
            var devStepCount by remember { mutableIntStateOf(0) }
            SettingsValueItem(
                title = stringResource(R.string.about_commit),
                value = updateBuildText,
                onClick = {
                    if (devModeEnabled) {
                        Toast.makeText(context, context.getString(R.string.toast_dev_mode_already_enabled), Toast.LENGTH_SHORT).show()
                        return@SettingsValueItem
                    }
                    devStepCount++
                    if (devStepCount in 3..6) {
                        Toast.makeText(context, context.getString(R.string.toast_dev_mode_steps, 7 - devStepCount), Toast.LENGTH_SHORT).show()
                    } else if (devStepCount == 7) {
                        LyricRepository.getInstance().setDevMode(context, true)
                        Toast.makeText(context, context.getString(R.string.toast_dev_mode_enabled), Toast.LENGTH_SHORT).show()
                    }
                }
            )

            if (showDiagnostics) {
                SettingsActionItem(
                    title = stringResource(R.string.title_diagnostics),
                    summary = stringResource(R.string.summary_diagnostics),
                    icon = Icons.Filled.Info,
                    onClick = onShowDiagnostics
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (showFeedbackDialog) {
                FeedbackSelectionDialog(onDismiss = { showFeedbackDialog = false })
            }

            communityDialogState?.let { dialogState ->
                CommunityDetailsDialog(
                    state = dialogState,
                    onDismiss = { communityDialogState = null },
                    onOpen = {
                        if (dialogState.item.hasUrl) {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(dialogState.item.url))
                            context.startActivity(browserIntent)
                        }
                        communityDialogState = null
                    }
                )
            }

            if (updateReleaseInfo != null) {
                UpdateDialog(
                    releaseInfo = updateReleaseInfo,
                    onDismiss = onUpdateDismiss,
                    onIgnore = onUpdateIgnore
                )
            }
        }
    }
}
