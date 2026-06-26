package com.example.islandlyrics.feature.settings.miuix

import com.example.islandlyrics.ui.miuix.theme.rememberIslandLyricsMiuixThemeController
import com.example.islandlyrics.ui.miuix.effects.miuixPageScroll
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurTopAppBar
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.islandlyrics.R
import com.example.islandlyrics.core.feed.CommunityFeed
import com.example.islandlyrics.core.feed.CommunityFeedRepository
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.update.UpdateChecker
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.feature.settings.CommunityDialogState
import com.example.islandlyrics.feature.settings.ReleaseDialogState
import com.example.islandlyrics.feature.update.miuix.MiuixUpdateDialog
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackIcon
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayListPopup as SuperListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference as SuperArrow
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as SuperDropdown
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixAboutScreen(
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
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    val showFeedbackPopup = remember { mutableStateOf(false) }
    val devModeEnabled by LyricRepository.getInstance().devModeEnabled.observeAsState(false)
    val offlineModeEnabled = remember { OfflineModeManager.isEnabled(context) }
    var autoUpdateEnabled by remember { mutableStateOf(UpdateChecker.isAutoUpdateEnabled(context)) }
    var currentChannel by remember { mutableStateOf(UpdateChecker.getUpdateChannel(context)) }
    var experimentUpdatesEnabled by remember { mutableStateOf(LabFeatureManager.isExperimentUpdatesEnabled(context)) }
    var feedSourcePriority by remember { mutableStateOf(LabFeatureManager.getFeedSourcePriority(context)) }
    var communityFeed by remember { mutableStateOf<CommunityFeed?>(null) }
    var communityFeedLoaded by remember { mutableStateOf(false) }
    val showCommunityDialog = remember { mutableStateOf<CommunityDialogState?>(null) }
    var devStepCount by remember { mutableIntStateOf(0) }
    val devModeAlreadyEnabledText = stringResource(R.string.toast_dev_mode_already_enabled)
    val devModeStepsFormat = stringResource(R.string.toast_dev_mode_steps)
    val devModeEnabledText = stringResource(R.string.toast_dev_mode_enabled)
    val copiedText = stringResource(R.string.toast_copied)
    fun handleDevModeTap(): Boolean {
        if (devModeEnabled) {
            Toast.makeText(context, devModeAlreadyEnabledText, Toast.LENGTH_SHORT).show()
            return true
        }
        devStepCount++
        if (devStepCount in 3..6) {
            Toast.makeText(context, String.format(devModeStepsFormat, 7 - devStepCount), Toast.LENGTH_SHORT).show()
            return true
        } else if (devStepCount == 7) {
            LyricRepository.getInstance().setDevMode(context, true)
            Toast.makeText(context, devModeEnabledText, Toast.LENGTH_SHORT).show()
            return true
        }
        return false
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

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = stringResource(R.string.community_about_title),
                largeTitle = stringResource(R.string.community_about_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = { onBack?.invoke() ?: (context as? android.app.Activity)?.finish() },
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        MiuixBackIcon(contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {},
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .miuixPageScroll(scrollBehavior),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            item { SmallTitle(text = stringResource(R.string.settings_community_header)) }
            item {
                Card(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    val announcementSectionTitle = stringResource(R.string.community_announcement_title)
                    val pollSectionTitle = stringResource(R.string.community_poll_title)

                    if (!offlineModeEnabled) {
                        val feedSourceOptions = listOf(
                            LabFeatureManager.FEED_SOURCE_GITHUB,
                            LabFeatureManager.FEED_SOURCE_GITEE
                        )
                        val feedSourceIndex = feedSourceOptions.indexOf(feedSourcePriority).takeIf { it >= 0 } ?: 0

                        SuperDropdown(
                            title = stringResource(R.string.diag_lab_feed_source_title),
                            summary = stringResource(R.string.diag_lab_feed_source_desc),
                            items = listOf(
                                stringResource(R.string.diag_lab_feed_source_github),
                                stringResource(R.string.diag_lab_feed_source_gitee)
                            ),
                            selectedIndex = feedSourceIndex,
                            onSelectedIndexChange = { index ->
                                val source = feedSourceOptions[index]
                                feedSourcePriority = source
                                LabFeatureManager.setFeedSourcePriority(context, source)
                            }
                        )
                    }

                    if (!communityFeedLoaded) {
                        SuperArrow(
                            title = stringResource(R.string.community_loading_title),
                            summary = stringResource(R.string.community_loading_desc),
                            onClick = {}
                        )
                    } else {
                        communityFeed?.announcements?.forEach { announcement ->
                            CommunityArrowItem(
                                title = announcementSectionTitle,
                                item = announcement,
                                fallbackSummary = stringResource(R.string.community_open_in_browser),
                                onClick = { showCommunityDialog.value = CommunityDialogState(announcementSectionTitle, announcement) }
                            )
                        }

                        communityFeed?.polls?.forEach { poll ->
                            CommunityArrowItem(
                                title = pollSectionTitle,
                                item = poll,
                                fallbackSummary = stringResource(R.string.community_open_in_browser),
                                onClick = { showCommunityDialog.value = CommunityDialogState(pollSectionTitle, poll) }
                            )
                        }
                    }
                }
            }

            if (!offlineModeEnabled) {
                item { SmallTitle(text = stringResource(R.string.update_check_title)) }
                item {
                    Card(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                        SuperSwitch(
                            title = stringResource(R.string.settings_auto_update),
                            summary = stringResource(R.string.settings_auto_update_desc),
                            checked = autoUpdateEnabled,
                            onCheckedChange = {
                                autoUpdateEnabled = it
                                UpdateChecker.setAutoUpdateEnabled(context, it)
                            }
                        )

                        val channelOptions = buildList {
                            add(UpdateChecker.CHANNEL_STABLE)
                            add(UpdateChecker.CHANNEL_PREVIEW)
                            if (experimentUpdatesEnabled) {
                                add(UpdateChecker.CHANNEL_EXPERIMENT)
                            }
                        }
                        val channelIndex = channelOptions.indexOf(currentChannel).takeIf { it >= 0 } ?: 0

                        SuperDropdown(
                            title = stringResource(R.string.settings_prerelease_update),
                            summary = stringResource(R.string.settings_prerelease_update_desc),
                            items = channelOptions,
                            selectedIndex = channelIndex,
                            onSelectedIndexChange = { index ->
                                val channel = channelOptions[index]
                                UpdateChecker.setUpdateChannel(context, channel)
                                currentChannel = channel
                                experimentUpdatesEnabled = LabFeatureManager.isExperimentUpdatesEnabled(context)
                            }
                        )

                        SuperArrow(
                            title = stringResource(R.string.update_check_title),
                            summary = stringResource(R.string.summary_check_updates_now),
                            onClick = onCheckUpdate
                        )
                        SuperArrow(
                            title = stringResource(R.string.update_current_version_changelog_title_short),
                            summary = stringResource(R.string.summary_view_current_version_changelog),
                            onClick = onViewCurrentVersionChangelog
                        )
                    }
                }
            }

            item { SmallTitle(text = stringResource(R.string.about_title)) }
            item {
                Card(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    if (!offlineModeEnabled) {
                        Box {
                            SuperArrow(
                                title = stringResource(R.string.settings_feedback),
                                summary = stringResource(R.string.summary_feedback),
                                onClick = { showFeedbackPopup.value = true }
                            )

                            SuperListPopup(
                                show = showFeedbackPopup.value,
                                alignment = PopupPositionProvider.Align.End,
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
                                            val browserIntent = Intent(
                                                Intent.ACTION_VIEW,
                                                "https://github.com/FrancoGiudans/Capsulyric/issues/new?template=bug_report.yml".toUri()
                                            )
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
                                            val browserIntent = Intent(Intent.ACTION_VIEW, "https://f.wps.cn/g/qACKW9I3/".toUri())
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
                                val browserIntent = Intent(Intent.ACTION_VIEW, "https://github.com/FrancoGiudans/Capsulyric".toUri())
                                context.startActivity(browserIntent)
                            }
                        )
                    }

                    BasicComponent(
                        title = stringResource(R.string.about_version),
                        summary = updateVersionText,
                        onClick = {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val copyText = "$updateVersionText（$updateCodenameText，$updateBuildText）"
                            cm.setPrimaryClip(ClipData.newPlainText("Version", copyText))
                            if (!handleDevModeTap()) {
                                Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    BasicComponent(
                        title = stringResource(R.string.about_codename),
                        summary = updateCodenameText
                    )

                    BasicComponent(
                        title = stringResource(R.string.about_commit),
                        summary = updateBuildText,
                        onClick = { handleDevModeTap() }
                    )
                }
            }
        }

        showCommunityDialog.value?.let { dialogState ->
            CommunityDetailsDialog(
                state = dialogState,
                onDismiss = { showCommunityDialog.value = null },
                onOpen = {
                    if (dialogState.item.hasUrl) {
                        val browserIntent = Intent(Intent.ACTION_VIEW, dialogState.item.url.toUri())
                        context.startActivity(browserIntent)
                    }
                    showCommunityDialog.value = null
                }
            )
        }

        releaseDialogState?.let { dialogState ->
            MiuixUpdateDialog(
                show = true,
                releaseInfo = dialogState.releaseInfo,
                mode = dialogState.mode,
                onDismiss = onReleaseDialogDismiss,
                onIgnore = onUpdateIgnore
            )
        }

        if (releaseLookupMessage != null) {
            MiuixBlurDialog(
                title = stringResource(R.string.update_current_version_changelog_unavailable_title),
                summary = releaseLookupMessage,
                show = true,
                onDismissRequest = onReleaseLookupMessageDismiss
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = stringResource(R.string.update_close),
                            onClick = onReleaseLookupMessageDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }
    }
}


