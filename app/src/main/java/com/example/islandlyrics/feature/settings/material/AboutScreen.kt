package com.example.islandlyrics.feature.settings.material

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.islandlyrics.R
import com.example.islandlyrics.core.feed.CommunityFeed
import com.example.islandlyrics.core.feed.CommunityFeedRepository
import com.example.islandlyrics.core.feed.CommunityFeedStatus
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.update.UpdateChecker
import com.example.islandlyrics.feature.licenses.OpenSourceLicensesActivity
import com.example.islandlyrics.feature.settings.CommunityDialogState
import com.example.islandlyrics.feature.settings.ReleaseDialogState
import com.example.islandlyrics.feature.update.material.UpdateDialog
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    updateVersionText: String,
    updateCodenameText: String,
    updateBuildText: String,
    onCheckUpdate: () -> Unit,
    onViewCurrentVersionChangelog: () -> Unit,
    onBack: (() -> Unit)? = null,
    releaseDialogState: ReleaseDialogState? = null,
    onReleaseDialogDismiss: () -> Unit = {},
    onUpdateIgnore: (String) -> Unit = {},
    releaseLookupMessage: String? = null,
    onReleaseLookupMessageDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val devModeEnabled by LyricRepository.getInstance().devModeEnabled.observeAsState(false)
    val offlineModeEnabled = remember { OfflineModeManager.isEnabled(context) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
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

    val versionInfoText = "$updateVersionText\n$updateCodenameText\n$updateBuildText"
    val copiedText = stringResource(R.string.toast_copied)
    val devModeAlreadyEnabledText = stringResource(R.string.toast_dev_mode_already_enabled)
    val devModeStepsFormat = stringResource(R.string.toast_dev_mode_steps)
    val devModeEnabledText = stringResource(R.string.toast_dev_mode_enabled)

    fun copyVersionInfo() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Capsulyric version", versionInfoText))
        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
    }

    fun handleDevModeTap() {
        if (devModeEnabled) {
            Toast.makeText(context, devModeAlreadyEnabledText, Toast.LENGTH_SHORT).show()
            return
        }
        devStepCount++
        if (devStepCount in 3..6) {
            Toast.makeText(context, String.format(devModeStepsFormat, 7 - devStepCount), Toast.LENGTH_SHORT).show()
        } else if (devStepCount == 7) {
            LyricRepository.getInstance().setDevMode(context, true)
            Toast.makeText(context, devModeEnabledText, Toast.LENGTH_SHORT).show()
        }
    }

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
                    IconButton(onClick = { onBack?.invoke() ?: (context as? Activity)?.finish() }) {
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
            item {
                MaterialAboutHeader(
                    versionInfoText = versionInfoText,
                    onVersionClick = ::copyVersionInfo,
                    onHeaderTap = ::handleDevModeTap
                )
            }

            item { SettingsSectionHeader(text = stringResource(R.string.settings_community_header), marginTop = 8.dp) }
            item {
                SettingsCard {
                    if (!offlineModeEnabled) {
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
                        SettingsCardDivider()
                    }
                    CommunitySection(
                        communityFeed = communityFeed,
                        communityFeedLoaded = communityFeedLoaded,
                        onCommunityItemClick = { communityDialogState = it }
                    )
                }
            }

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
                    }
                    SettingsActionItem(
                        title = stringResource(R.string.settings_about_github),
                        summary = stringResource(R.string.summary_github),
                        icon = Icons.Filled.Link,
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/FrancoGiudans/Capsulyric".toUri()))
                        }
                    )
                    SettingsCardDivider()
                    MaterialVersionInfoItem(
                        title = stringResource(R.string.about_version),
                        value = versionInfoText,
                        onClick = ::copyVersionInfo
                    )
                }
            }

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
                            icon = Icons.AutoMirrored.Filled.Article,
                            onClick = onViewCurrentVersionChangelog
                        )
                    }
                }
            }

            item { SettingsSectionHeader(text = stringResource(R.string.open_source_licenses_header)) }
            item {
                SettingsCard {
                    SettingsActionItem(
                        title = stringResource(R.string.open_source_licenses_title),
                        summary = stringResource(R.string.open_source_licenses_summary),
                        icon = Icons.Filled.Link,
                        onClick = {
                            context.startActivity(Intent(context, OpenSourceLicensesActivity::class.java))
                        }
                    )
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
                        context.startActivity(Intent(Intent.ACTION_VIEW, dialogState.item.url.toUri()))
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

@Composable
private fun MaterialAboutHeader(
    versionInfoText: String,
    onVersionClick: () -> Unit,
    onHeaderTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { onHeaderTap() }
            }
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.size(92.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_monochrome),
                    contentDescription = stringResource(R.string.app_name),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(72.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = versionInfoText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onVersionClick)
        )
    }
}

@Composable
private fun MaterialVersionInfoItem(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CommunitySection(
    communityFeed: CommunityFeed?,
    communityFeedLoaded: Boolean,
    onCommunityItemClick: (CommunityDialogState) -> Unit
) {
    val announcementSectionTitle = stringResource(R.string.community_announcement_title)
    val pollSectionTitle = stringResource(R.string.community_poll_title)

    if (!communityFeedLoaded) {
        SettingsActionItem(
            title = stringResource(R.string.community_loading_title),
            summary = stringResource(R.string.community_loading_desc),
            icon = Icons.Filled.Campaign,
            onClick = {}
        )
        return
    }

    val announcements = communityFeed?.announcements ?: emptyList()
    val polls = communityFeed?.polls ?: emptyList()
    if (announcements.isNotEmpty() || polls.isNotEmpty()) {
        announcements.forEachIndexed { index, announcement ->
            if (index > 0) SettingsCardDivider()
            CommunityActionItem(
                title = announcementSectionTitle,
                item = announcement,
                fallbackSummary = stringResource(R.string.community_open_in_browser),
                icon = Icons.Filled.Campaign,
                onClick = { onCommunityItemClick(CommunityDialogState(announcementSectionTitle, announcement)) }
            )
        }
        polls.forEachIndexed { index, poll ->
            if (index > 0 || announcements.isNotEmpty()) SettingsCardDivider()
            CommunityActionItem(
                title = pollSectionTitle,
                item = poll,
                fallbackSummary = stringResource(R.string.community_open_in_browser),
                icon = Icons.Filled.Poll,
                onClick = { onCommunityItemClick(CommunityDialogState(pollSectionTitle, poll)) }
            )
        }
        return
    }

    SettingsActionItem(
        title = if (communityFeed?.status == CommunityFeedStatus.UNAVAILABLE) {
            stringResource(R.string.community_unavailable_title)
        } else {
            stringResource(R.string.community_empty_title)
        },
        summary = if (communityFeed?.status == CommunityFeedStatus.UNAVAILABLE) {
            stringResource(R.string.community_unavailable_desc)
        } else {
            stringResource(R.string.community_empty_desc)
        },
        icon = Icons.Filled.Info,
        onClick = {}
    )
}
