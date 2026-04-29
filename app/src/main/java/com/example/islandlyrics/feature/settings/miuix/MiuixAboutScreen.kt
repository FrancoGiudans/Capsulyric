package com.example.islandlyrics.feature.settings.miuix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.feed.CommunityFeed
import com.example.islandlyrics.core.feed.CommunityFeedRepository
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.update.UpdateChecker
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.feature.settings.CommunityDialogState
import com.example.islandlyrics.ui.miuix.*
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
    onShowDiagnostics: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    val showFeedbackPopup = remember { mutableStateOf(false) }
    val showPrereleaseDialog = remember { mutableStateOf(false) }
    val devModeEnabled by LyricRepository.getInstance().devModeEnabled.observeAsState(false)
    val showDiagnostics = BuildConfig.DEBUG || devModeEnabled
    val offlineModeEnabled = remember { OfflineModeManager.isEnabled(context) }
    var autoUpdateEnabled by remember { mutableStateOf(UpdateChecker.isAutoUpdateEnabled(context)) }
    var prereleaseEnabled by remember { mutableStateOf(UpdateChecker.isPrereleaseEnabled(context)) }
    var currentChannel by remember { mutableStateOf(UpdateChecker.getPrereleaseChannel(context)) }
    var communityFeed by remember { mutableStateOf<CommunityFeed?>(null) }
    var communityFeedLoaded by remember { mutableStateOf(false) }
    val showCommunityDialog = remember { mutableStateOf<CommunityDialogState?>(null) }

    LaunchedEffect(offlineModeEnabled) {
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
                        onClick = { (context as? android.app.Activity)?.finish() },
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
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
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            item { SmallTitle(text = stringResource(R.string.settings_community_header)) }
            item {
                Card(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    val announcementSectionTitle = stringResource(R.string.community_announcement_title)
                    val pollSectionTitle = stringResource(R.string.community_poll_title)

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

                        SuperSwitch(
                            title = stringResource(R.string.settings_prerelease_update),
                            summary = stringResource(R.string.settings_prerelease_update_desc),
                            checked = prereleaseEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    showPrereleaseDialog.value = true
                                } else {
                                    prereleaseEnabled = false
                                    UpdateChecker.setPrereleaseEnabled(context, false)
                                }
                            }
                        )

                        if (prereleaseEnabled) {
                            val channelOptions = listOf("Alpha", "Beta", "Pre", "Canary")
                            val channelNames = listOf("Alpha", "Beta", "Pre", "Canary")
                            val channelIndex = channelOptions.indexOf(currentChannel).takeIf { it >= 0 } ?: 0

                            SuperDropdown(
                                title = stringResource(R.string.settings_prerelease_channel),
                                items = channelNames,
                                selectedIndex = channelIndex,
                                onSelectedIndexChange = { index ->
                                    val channel = channelOptions[index]
                                    UpdateChecker.setPrereleaseChannel(context, channel)
                                    currentChannel = channel
                                }
                            )
                        }

                        SuperArrow(
                            title = stringResource(R.string.update_check_title),
                            summary = stringResource(R.string.summary_check_updates_now),
                            onClick = onCheckUpdate
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
                                                Uri.parse("https://github.com/FrancoGiudans/Capsulyric/issues/new?template=bug_report.yml")
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
                    }

                    BasicComponent(
                        title = stringResource(R.string.about_version),
                        summary = updateVersionText,
                        onClick = {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val copyText = "$updateVersionText（$updateCodenameText，$updateBuildText）"
                            cm.setPrimaryClip(ClipData.newPlainText("Version", copyText))
                            Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                        }
                    )

                    BasicComponent(
                        title = stringResource(R.string.about_codename),
                        summary = updateCodenameText
                    )

                    var devStepCount by remember { mutableIntStateOf(0) }
                    BasicComponent(
                        title = stringResource(R.string.about_commit),
                        summary = updateBuildText,
                        onClick = {
                            if (devModeEnabled) {
                                Toast.makeText(context, context.getString(R.string.toast_dev_mode_already_enabled), Toast.LENGTH_SHORT).show()
                                return@BasicComponent
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
                        SuperArrow(
                            title = stringResource(R.string.title_diagnostics),
                            summary = stringResource(R.string.summary_diagnostics),
                            onClick = onShowDiagnostics
                        )
                    }
                }
            }
        }

        showCommunityDialog.value?.let { dialogState ->
            CommunityDetailsDialog(
                state = dialogState,
                onDismiss = { showCommunityDialog.value = null },
                onOpen = {
                    if (dialogState.item.hasUrl) {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(dialogState.item.url))
                        context.startActivity(browserIntent)
                    }
                    showCommunityDialog.value = null
                }
            )
        }

        MiuixBlurDialog(
            title = stringResource(R.string.dialog_prerelease_warning_title),
            show = showPrereleaseDialog.value,
            onDismissRequest = {
                prereleaseEnabled = false
                UpdateChecker.setPrereleaseEnabled(context, false)
                showPrereleaseDialog.value = false
            }
        ) {
            androidx.compose.material3.Text(
                text = stringResource(R.string.dialog_prerelease_warning_message),
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = {
                        prereleaseEnabled = false
                        UpdateChecker.setPrereleaseEnabled(context, false)
                        showPrereleaseDialog.value = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        prereleaseEnabled = true
                        UpdateChecker.setPrereleaseEnabled(context, true)
                        showPrereleaseDialog.value = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }

    }
}
