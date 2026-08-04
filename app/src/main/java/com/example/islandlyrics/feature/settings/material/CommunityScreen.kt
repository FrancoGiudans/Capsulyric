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
 *  *
 *  *
 */

package com.example.islandlyrics.feature.settings.material

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.islandlyrics.R
import com.example.islandlyrics.core.feed.CommunityFeed
import com.example.islandlyrics.core.feed.CommunityFeedRepository
import com.example.islandlyrics.core.feed.CommunityFeedStatus
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.feature.settings.CommunityDialogState
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = androidx.compose.material3.rememberTopAppBarState().let {
        androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior(it)
    }
    val offlineModeEnabled = remember { OfflineModeManager.isEnabled(context) }
    var feedSourcePriority by remember { mutableStateOf(LabFeatureManager.getFeedSourcePriority(context)) }
    var showFeedSourceDropdown by remember { mutableStateOf(false) }
    var communityFeed by remember { mutableStateOf<CommunityFeed?>(null) }
    var communityFeedLoaded by remember { mutableStateOf(false) }
    var communityDialogState by remember { mutableStateOf<CommunityDialogState?>(null) }

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
                title = { Text(stringResource(R.string.settings_community_header)) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
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
        }
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
