package com.example.islandlyrics.feature.settings.material

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.islandlyrics.feature.settings.ReleaseDialogState
import com.example.islandlyrics.feature.update.material.UpdateDialog
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    updateVersionText: String,
    updateCodenameText: String,
    updateBuildText: String,
    onShowDiagnostics: () -> Unit,
    onCheckUpdate: () -> Unit,
    onViewCurrentVersionChangelog: () -> Unit,
    releaseDialogState: ReleaseDialogState? = null,
    onReleaseDialogDismiss: () -> Unit = {},
    onUpdateIgnore: (String) -> Unit = {},
    releaseLookupMessage: String? = null,
    onReleaseLookupMessageDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val devModeEnabled by LyricRepository.getInstance().devModeEnabled.observeAsState(false)
    val showDiagnostics = BuildConfig.DEBUG || devModeEnabled
    var showFeedbackDialog by remember { mutableStateOf(false) }
    val offlineModeEnabled = remember { OfflineModeManager.isEnabled(context) }
    var autoUpdateEnabled by remember { mutableStateOf(UpdateChecker.isAutoUpdateEnabled(context)) }
    var showChannelDropdown by remember { mutableStateOf(false) }
    var currentChannel by remember { mutableStateOf(UpdateChecker.getUpdateChannel(context)) }
    var experimentUpdatesEnabled by remember { mutableStateOf(LabFeatureManager.isExperimentUpdatesEnabled(context)) }
    var feedSourcePriority by remember { mutableStateOf(LabFeatureManager.getFeedSourcePriority(context)) }
    var showFeedSourceDropdown by remember { mutableStateOf(false) }
    var communityFeed by remember { mutableStateOf<CommunityFeed?>(null) }
    var communityFeedLoaded by remember { mutableStateOf(false) }
    var communityDialogState by remember { mutableStateOf<CommunityDialogState?>(null) }
    var devStepCount by remember { mutableIntStateOf(0) }
    val announcementSectionTitle = stringResource(R.string.community_announcement_title)
    val pollSectionTitle = stringResource(R.string.community_poll_title)

    LaunchedEffect(offlineModeEnabled, feedSourcePriority) {
        communityFeedLoaded = false
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
        containerColor = materialPageContainerColor()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── Community ────────────────────────────────────────────────────
            item { SettingsSectionHeader(text = stringResource(R.string.settings_community_header), marginTop = 0.dp) }

            if (!offlineModeEnabled) {
                item {
                    SettingsCard {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            SettingsTextItem(
                                title = stringResource(R.string.diag_lab_feed_source_title),
                                value = if (feedSourcePriority == LabFeatureManager.FEED_SOURCE_GITEE) {
                                    stringResource(R.string.diag_lab_feed_source_gitee)
                                } else {
                                    stringResource(R.string.diag_lab_feed_source_github)
                                },
                                onClick = { showFeedSourceDropdown = true }
                            )
                            Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.CenterEnd)) {
                                DropdownMenu(
                                    expanded = showFeedSourceDropdown,
                                    onDismissRequest = { showFeedSourceDropdown = false }
                                ) {
                                    listOf(
                                        LabFeatureManager.FEED_SOURCE_GITHUB to stringResource(R.string.diag_lab_feed_source_github),
                                        LabFeatureManager.FEED_SOURCE_GITEE to stringResource(R.string.diag_lab_feed_source_gitee)
                                    ).forEach { (sourceKey, sourceLabel) ->
                                        DropdownMenuItem(
                                            text = { Text(sourceLabel) },
                                            onClick = {
                                                feedSourcePriority = sourceKey
                                                LabFeatureManager.setFeedSourcePriority(context, sourceKey)
                                                showFeedSourceDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!communityFeedLoaded) {
                item {
                    SettingsCard {
                        SettingsActionItem(
                            title = stringResource(R.string.community_loading_title),
                            summary = stringResource(R.string.community_loading_desc),
                            icon = Icons.Filled.Info,
                            onClick = {}
                        )
                    }
                }
            } else {
                val hasAnnouncements = !communityFeed?.announcements.isNullOrEmpty()
                val hasPolls = !communityFeed?.polls.isNullOrEmpty()
                if (hasAnnouncements || hasPolls) {
                    item {
                        SettingsCard {
                            communityFeed?.announcements?.forEachIndexed { idx, announcement ->
                                if (idx > 0) SettingsCardDivider()
                                CommunityActionItem(
                                    title = announcementSectionTitle,
                                    item = announcement,
                                    fallbackSummary = stringResource(R.string.community_open_in_browser),
                                    icon = Icons.Filled.Info,
                                    onClick = { communityDialogState = CommunityDialogState(announcementSectionTitle, announcement) }
                                )
                            }
                            val announcements = communityFeed?.announcements ?: emptyList()
                            communityFeed?.polls?.forEachIndexed { idx, poll ->
                                if (idx > 0 || announcements.isNotEmpty()) SettingsCardDivider()
                                CommunityActionItem(
                                    title = pollSectionTitle,
                                    item = poll,
                                    fallbackSummary = stringResource(R.string.community_open_in_browser),
                                    icon = Icons.Filled.Link,
                                    onClick = { communityDialogState = CommunityDialogState(pollSectionTitle, poll) }
                                )
                            }
                        }
                    }
                }
            }

            // ── Updates ──────────────────────────────────────────────────────
            if (!offlineModeEnabled) {
                item { SettingsSectionHeader(text = stringResource(R.string.update_check_title)) }
                item {
                    SettingsCard {
                        SettingsSwitchItem(
                            title = stringResource(R.string.settings_auto_update),
                            subtitle = stringResource(R.string.settings_auto_update_desc),
                            checked = autoUpdateEnabled,
                            onCheckedChange = {
                                autoUpdateEnabled = it
                                UpdateChecker.setAutoUpdateEnabled(context, it)
                            }
                        )
                        SettingsCardDivider()
                        Box(modifier = Modifier.fillMaxWidth()) {
                            SettingsTextItem(
                                title = stringResource(R.string.settings_prerelease_update),
                                value = currentChannel,
                                onClick = { showChannelDropdown = true }
                            )
                            Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.CenterEnd)) {
                                DropdownMenu(
                                    expanded = showChannelDropdown,
                                    onDismissRequest = { showChannelDropdown = false }
                                ) {
                                    val channels = buildList {
                                        add(UpdateChecker.CHANNEL_STABLE)
                                        add(UpdateChecker.CHANNEL_PREVIEW)
                                        if (experimentUpdatesEnabled) add(UpdateChecker.CHANNEL_EXPERIMENT)
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
                        }
                        SettingsCardDivider()
                        SettingsActionItem(
                            title = stringResource(R.string.update_check_title),
                            summary = stringResource(R.string.summary_check_updates_now),
                            icon = Icons.Filled.Sync,
                            onClick = onCheckUpdate
                        )
                        SettingsCardDivider()
                        SettingsActionItem(
                            title = stringResource(R.string.update_current_version_changelog_title_short),
                            summary = stringResource(R.string.summary_view_current_version_changelog),
                            icon = Icons.Filled.Info,
                            onClick = onViewCurrentVersionChangelog
                        )
                    }
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            item { SettingsSectionHeader(text = stringResource(R.string.about_title)) }
            item {
                SettingsCard {
                    if (!offlineModeEnabled) {
                        SettingsActionItem(
                            title = stringResource(R.string.settings_feedback),
                            summary = stringResource(R.string.summary_feedback),
                            icon = Icons.AutoMirrored.Filled.Send,
                            onClick = { showFeedbackDialog = true }
                        )
                        SettingsCardDivider()
                        SettingsActionItem(
                            title = stringResource(R.string.settings_about_github),
                            summary = stringResource(R.string.summary_github),
                            icon = Icons.Filled.Link,
                            onClick = {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FrancoGiudans/Capsulyric"))
                                context.startActivity(browserIntent)
                            }
                        )
                        SettingsCardDivider()
                    }
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
                    SettingsCardDivider()
                    SettingsValueItem(
                        title = stringResource(R.string.about_codename),
                        value = updateCodenameText
                    )
                    SettingsCardDivider()
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
                        SettingsCardDivider()
                        SettingsActionItem(
                            title = stringResource(R.string.title_diagnostics),
                            summary = stringResource(R.string.summary_diagnostics),
                            icon = Icons.Filled.Info,
                            onClick = onShowDiagnostics
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

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

        releaseDialogState?.let { dialogState ->
            UpdateDialog(
                releaseInfo = dialogState.releaseInfo,
                mode = dialogState.mode,
                onDismiss = onReleaseDialogDismiss,
                onIgnore = onUpdateIgnore
            )
        }

        if (releaseLookupMessage != null) {
            AlertDialog(
                onDismissRequest = onReleaseLookupMessageDismiss,
                title = { Text(stringResource(R.string.update_current_version_changelog_unavailable_title)) },
                text = { Text(releaseLookupMessage) },
                confirmButton = {
                    TextButton(onClick = onReleaseLookupMessageDismiss) {
                        Text(stringResource(R.string.update_close))
                    }
                }
            )
        }
    }
}
