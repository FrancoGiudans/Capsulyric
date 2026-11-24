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

package com.example.islandlyrics.feature.cache.miuix

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.search.SettingsNavigationTarget
import com.example.islandlyrics.core.settings.search.SettingsSearchAction
import com.example.islandlyrics.core.cache.AppImageCacheManager
import com.example.islandlyrics.lyrics.local.LocalLyricDirectoryManager
import com.example.islandlyrics.lyrics.cache.OnlineLyricCacheStore
import android.content.Intent
import com.example.islandlyrics.feature.cache.CacheManagementViewModel
import com.example.islandlyrics.feature.cache.LyricCacheFilter
import com.example.islandlyrics.feature.cache.filterByCacheCategory
import com.example.islandlyrics.feature.cache.filterByCacheQuery
import com.example.islandlyrics.feature.onlinelyricdebug.OnlineLyricDebugActivity
import com.example.islandlyrics.feature.settings.SettingsActivity
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackHandler
import com.example.islandlyrics.ui.miuix.search.MiuixLookingForOtherSettings
import com.example.islandlyrics.ui.miuix.search.OtherSettingLink
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurTopAppBar
import com.example.islandlyrics.ui.miuix.effects.miuixPageScroll
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurSnackbar
import top.yukonga.miuix.kmp.basic.SnackbarHost as MiuixSnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiuixCacheManagementScreen(
    onBack: () -> Unit,
    viewModel: CacheManagementViewModel = viewModel()
) {
    val context = LocalContext.current
    val dirManager = remember { LocalLyricDirectoryManager.getInstance(context) }
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val lyricStats by viewModel.lyricStats.observeAsState(OnlineLyricCacheStore.LyricCacheStats())
    val lyricEntries by viewModel.lyricEntries.observeAsState(emptyList())
    val imageStats by viewModel.imageStats.observeAsState(AppImageCacheManager.ImageCacheStats())
    val busy by viewModel.busy.observeAsState(false)
    val statusMessage by viewModel.statusMessage.observeAsState()
    val selectedIds by viewModel.selectedIds.observeAsState(emptySet())
    val isSelectionMode by viewModel.isSelectionMode.observeAsState(false)
    var exportMatchInfoEnabled by remember { mutableStateOf(dirManager.isExportMatchSyncEnabled()) }
    val snackbarHostState = remember { MiuixSnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var activeFilter by rememberSaveable { mutableStateOf<LyricCacheFilter?>(null) }
    var pendingDeleteEntryId by remember { mutableStateOf<String?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showClearAppDataDialog by remember { mutableStateOf(false) }
    LaunchedEffect(lyricEntries.isEmpty()) {
        if (lyricEntries.isEmpty()) activeFilter = null
    }
    val filteredLyricEntries = remember(lyricEntries, activeFilter, searchQuery) {
        lyricEntries.filterByCacheCategory(activeFilter).filterByCacheQuery(searchQuery)
    }
    val pendingDeleteEntry = remember(lyricEntries, pendingDeleteEntryId) {
        lyricEntries.firstOrNull { it.id == pendingDeleteEntryId }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var viewingDetail by remember { mutableStateOf<OnlineLyricCacheStore.LyricCacheDetail?>(null) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(message = it)
            viewModel.consumeStatusMessage()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MiuixBackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = if (isSelectionMode) {
                    stringResource(R.string.cache_management_selected_count, selectedIds.size)
                } else {
                    stringResource(R.string.title_cache_management)
                },
                largeTitle = if (isSelectionMode) {
                    stringResource(R.string.cache_management_selected_count, selectedIds.size)
                } else {
                    stringResource(R.string.title_cache_management)
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.exitSelectionMode() }, modifier = Modifier.padding(start = 12.dp)) {
                            Icon(MiuixIcons.Close, contentDescription = stringResource(R.string.cache_management_deselect), tint = MiuixTheme.colorScheme.onBackground)
                        }
                    } else {
                        IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                            Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.cache_management_back), tint = MiuixTheme.colorScheme.onBackground)
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(MiuixIcons.SelectAll, contentDescription = stringResource(R.string.cache_management_select_all), tint = MiuixTheme.colorScheme.onBackground)
                        }
                        IconButton(onClick = { viewModel.exportSelected() }) {
                            Icon(MiuixIcons.Share, contentDescription = stringResource(R.string.cache_management_export_selected), tint = MiuixTheme.colorScheme.onBackground)
                        }
                        IconButton(
                            onClick = { if (selectedIds.isNotEmpty()) showDeleteSelectedDialog = true },
                            enabled = selectedIds.isNotEmpty() && !busy
                        ) {
                            Icon(MiuixIcons.Delete, contentDescription = stringResource(R.string.cache_management_delete_selected), tint = MiuixTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.cache_management_refresh), tint = MiuixTheme.colorScheme.onBackground)
                        }
                    }
                }
            )
        },
        snackbarHost = {
            MiuixSnackbarHost(
                state = snackbarHostState,
                content = { data -> MiuixBlurSnackbar(data = data) }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .miuixPageScroll(scrollBehavior),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            item { SmallTitle(text = stringResource(R.string.cache_management_lyric_cache)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    MiuixLyricCacheOverview(
                        stats = lyricStats,
                        activeFilter = activeFilter,
                        onFilterToggle = { tapped ->
                            if (lyricStats.entryCount > 0 || activeFilter != null) {
                                activeFilter = if (activeFilter == tapped) null else tapped
                            }
                        },
                        onClearFilter = { activeFilter = null }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .height(0.5.dp)
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    )
                    SuperSwitch(
                        title = stringResource(R.string.cache_management_export_match_info_title),
                        summary = stringResource(R.string.cache_management_export_match_info_desc),
                        checked = exportMatchInfoEnabled,
                        onCheckedChange = { enabled ->
                            exportMatchInfoEnabled = enabled
                            dirManager.setExportMatchSyncEnabled(enabled)
                        }
                    )
                    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
                        Button(onClick = { viewModel.clearLyricCache() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cache_management_clear_lyric_cache))
                        }
                    }
                }
            }
            item { SmallTitle(text = stringResource(R.string.cache_management_image_cache)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MiuixStatRow(stringResource(R.string.cache_management_file_count), imageStats.fileCount.toString())
                        MiuixStatRow(stringResource(R.string.cache_management_total_size), formatBytes(imageStats.totalBytes))
                        MiuixStatRow(stringResource(R.string.cache_management_last_updated), formatTimestamp(stringResource(R.string.cache_management_none), imageStats.lastUpdatedAt))
                        Text(stringResource(R.string.cache_management_remote_images_hint), color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.clearImageCache() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cache_management_clear_image_cache))
                        }
                    }
                }
            }
            item { SmallTitle(text = stringResource(R.string.cache_management_danger_zone)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.cache_management_clear_all_title), fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.cache_management_clear_all_desc),
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.clearAllCaches() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cache_management_clear_all))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.cache_management_clear_all_app_data_title), fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.cache_management_clear_all_app_data_desc),
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showClearAppDataDialog = true },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.cache_management_clear_all_app_data), color = MiuixTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
            item { SmallTitle(text = stringResource(R.string.cache_management_entries_title)) }
            item {
                SearchBar(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    inputField = {
                        InputField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { searchExpanded = true },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            label = stringResource(R.string.cache_management_search_entries)
                        )
                    },
                    insideMargin = androidx.compose.ui.unit.DpSize(0.dp, 0.dp),
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it }
                ) {}
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            if (lyricEntries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.cache_management_no_entries),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            } else if (filteredLyricEntries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.cache_management_no_search_results),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            } else {
                items(filteredLyricEntries, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(entry.id)
                                    } else {
                                        coroutineScope.launch {
                                            viewingDetail = viewModel.getEntryDetail(entry.id)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) viewModel.enterSelectionMode(entry.id)
                                    else viewModel.toggleSelection(entry.id)
                                }
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelectionMode) {
                                    Checkbox(
                                        state = if (selectedIds.contains(entry.id)) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off,
                                        onClick = { viewModel.toggleSelection(entry.id) }
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${entry.title} - ${entry.artist}", fontWeight = FontWeight.SemiBold)
                                    Text(entry.packageName, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            MiuixStatRow(stringResource(R.string.cache_management_query_info), "${entry.queryTitle} / ${entry.queryArtist}")
                            MiuixStatRow(stringResource(R.string.cache_management_provider), entry.providerLabel.ifBlank { stringResource(R.string.cache_management_unknown) })
                            MiuixStatRow(stringResource(R.string.cache_management_sidecars), formatSidecarFlags(entry.hasTranslation, entry.hasRomanization))
                            MiuixStatRow(stringResource(R.string.cache_management_total_size), formatBytes(entry.sizeBytes))
                            MiuixStatRow(stringResource(R.string.cache_management_updated_at), formatTimestamp(stringResource(R.string.cache_management_none), entry.updatedAt))
                            if (entry.hasCustomMatch) {
                                Text(stringResource(R.string.cache_management_has_custom_match), color = MiuixTheme.colorScheme.primary)
                            }
                            if (!isSelectionMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { viewModel.exportEntry(entry.id) },
                                        enabled = !busy
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Share,
                                            contentDescription = stringResource(R.string.cache_management_export),
                                            tint = MiuixTheme.colorScheme.onBackground
                                        )
                                    }
                                    IconButton(
                                        onClick = { pendingDeleteEntryId = entry.id },
                                        enabled = !busy
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Delete,
                                            contentDescription = stringResource(R.string.cache_management_delete),
                                            tint = MiuixTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                MiuixLookingForOtherSettings(
                    links = listOf(
                        OtherSettingLink(
                            titleRes = R.string.online_lyric_rematch_title,
                            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.ONLINE_LYRIC_REMATCH),
                            onClick = {
                                context.startActivity(Intent(context, OnlineLyricDebugActivity::class.java))
                            }
                        ),
                        OtherSettingLink(
                            titleRes = R.string.settings_local_lyrics_title,
                            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.LOCAL_LYRIC_DIRECTORIES),
                            onClick = {
                                context.startActivity(Intent(context, SettingsActivity::class.java).apply {
                                    putExtra("open_page", "local_lyrics")
                                })
                            }
                        )
                    ),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        pendingDeleteEntryId?.let { entryId ->
            MiuixBlurDialog(
                title = stringResource(R.string.cache_management_delete_confirm_title),
                summary = stringResource(
                    R.string.cache_management_delete_confirm_message,
                    pendingDeleteEntry?.displayName() ?: stringResource(R.string.cache_management_unknown)
                ),
                show = true,
                renderInRootScaffold = false,
                onDismissRequest = { pendingDeleteEntryId = null }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { pendingDeleteEntryId = null },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(
                        text = stringResource(R.string.cache_management_delete),
                        onClick = {
                            pendingDeleteEntryId = null
                            viewModel.deleteLyricEntry(entryId)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.error
                        )
                    )
                }
            }
        }

        if (showDeleteSelectedDialog) {
            MiuixBlurDialog(
                title = stringResource(R.string.cache_management_delete_selected_confirm_title),
                summary = stringResource(
                    R.string.cache_management_delete_selected_confirm_message,
                    selectedIds.size
                ),
                show = true,
                renderInRootScaffold = false,
                onDismissRequest = { showDeleteSelectedDialog = false }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { showDeleteSelectedDialog = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(
                        text = stringResource(R.string.cache_management_delete),
                        onClick = {
                            showDeleteSelectedDialog = false
                            viewModel.deleteSelected()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.error
                        )
                    )
                }
            }
        }

        if (showClearAppDataDialog) {
            MiuixBlurDialog(
                title = stringResource(R.string.cache_management_clear_all_app_data_confirm_title),
                summary = stringResource(R.string.cache_management_clear_all_app_data_confirm_message),
                show = true,
                renderInRootScaffold = false,
                onDismissRequest = { showClearAppDataDialog = false }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { showClearAppDataDialog = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(
                        text = stringResource(R.string.cache_management_delete),
                        onClick = {
                            showClearAppDataDialog = false
                            viewModel.clearAllAppData()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.error
                        )
                    )
                }
            }
        }

        viewingDetail?.let { detail ->
            MiuixBlurDialog(
                title = detail.displayName(),
                show = true,
                renderInRootScaffold = false,
                onDismissRequest = { viewingDetail = null }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    if (detail.providerLabel.isNotBlank() || detail.queryTitle.isNotBlank() || detail.queryArtist.isNotBlank()) {
                        val queryInfo = if (detail.queryTitle.isNotBlank() || detail.queryArtist.isNotBlank()) {
                            "${detail.queryTitle} / ${detail.queryArtist}"
                        } else ""
                        val subtitle = listOf(detail.providerLabel, queryInfo).filter { it.isNotBlank() }.joinToString(" • ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (detail.isInstrumental) {
                            Text(
                                text = stringResource(R.string.online_lyric_debug_instrumental_status),
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        } else if (detail.lyrics.isBlank()) {
                            Text(
                                text = stringResource(R.string.online_lyric_rematch_no_lyrics),
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.online_lyric_debug_result_main_lyrics),
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = detail.lyrics)
                            if (!detail.translationLyrics.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = stringResource(R.string.online_lyric_debug_result_translation),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = detail.translationLyrics)
                            }
                            if (!detail.romanLyrics.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = stringResource(R.string.online_lyric_debug_result_romanization),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = detail.romanLyrics)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewingDetail = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.online_lyric_debug_close))
                    }
                }
            }
        }
    }
}

private fun OnlineLyricCacheStore.LyricCacheDetail.displayName(): String {
    return listOf(title, artist)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .ifBlank { packageName.ifBlank { id } }
}

private fun OnlineLyricCacheStore.LyricCacheEntrySummary.displayName(): String {
    return listOf(title, artist)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .ifBlank { packageName.ifBlank { id } }
}

@Composable
private fun MiuixStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MiuixTheme.colorScheme.onSurfaceSecondary)
        Text(value)
    }
    Spacer(modifier = Modifier.height(6.dp))
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    return when {
        bytes >= mb -> String.format(Locale.getDefault(), "%.2f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.getDefault(), "%.2f KB", bytes / kb)
        else -> "$bytes B"
    }
}

@Composable
private fun formatSidecarFlags(hasTranslation: Boolean, hasRomanization: Boolean): String {
    val yes = stringResource(R.string.cache_management_yes)
    val no = stringResource(R.string.cache_management_no)
    return stringResource(
        R.string.cache_management_sidecars_fmt,
        if (hasTranslation) yes else no,
        if (hasRomanization) yes else no
    )
}

private fun formatTimestamp(emptyValue: String, timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return emptyValue
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

private fun formatCompactTimestamp(emptyValue: String, timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return emptyValue
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatPercentText(count: Int, total: Int): String {
    if (total <= 0 || count <= 0) return "0%"
    val pct = ((count * 100f) / total).toInt()
    return if (pct == 0) "<1%" else "$pct%"
}

@Composable
private fun MiuixLyricCacheOverview(
    stats: OnlineLyricCacheStore.LyricCacheStats,
    activeFilter: LyricCacheFilter?,
    onFilterToggle: (LyricCacheFilter) -> Unit,
    onClearFilter: () -> Unit
) {
    val colors = MiuixTheme.colorScheme
    val isLight = colors.surface.luminance() > 0.5f
    val lineAccent = remember(colors.primary, colors.onSurfaceVariantSummary) {
        lerp(colors.primary, colors.onSurfaceVariantSummary, 0.38f)
    }
    val cellBg = remember(colors.onSurface, isLight) {
        colors.onSurface.copy(alpha = if (isLight) 0.04f else 0.06f)
    }
    val selectedBg = remember(colors.primary, isLight) {
        colors.primary.copy(alpha = if (isLight) 0.12f else 0.22f)
    }
    val selectedBorder = remember(colors.primary, isLight) {
        colors.primary.copy(alpha = if (isLight) 0.28f else 0.40f)
    }
    val trackBg = remember(colors.onSurface, isLight) {
        colors.onSurface.copy(alpha = if (isLight) 0.08f else 0.12f)
    }

    val syllableLabel = stringResource(R.string.cache_management_type_syllable)
    val lineLabel = stringResource(R.string.cache_management_type_line)
    val instLabel = stringResource(R.string.cache_management_type_instrumental)
    val matchLabel = stringResource(R.string.cache_management_type_match_only)
    val transLabel = stringResource(R.string.cache_management_sidecar_translation)
    val romaLabel = stringResource(R.string.cache_management_sidecar_romanization)

    val focusedInfo: Pair<Int, String>? = when (activeFilter) {
        LyricCacheFilter.SYLLABLE -> stats.syllableCount to syllableLabel
        LyricCacheFilter.LINE -> stats.lineCount to lineLabel
        LyricCacheFilter.INSTRUMENTAL -> stats.instrumentalCount to instLabel
        LyricCacheFilter.MATCH_ONLY -> stats.matchOnlyCount to matchLabel
        LyricCacheFilter.TRANSLATION -> stats.translationCount to transLabel
        LyricCacheFilter.ROMANIZATION -> stats.romanizationCount to romaLabel
        null -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .then(if (focusedInfo != null) Modifier.clickable(onClick = onClearFilter) else Modifier)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = (focusedInfo?.first ?: stats.entryCount).toString(),
                        style = MiuixTheme.textStyles.title2.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = if (focusedInfo != null) colors.primary else colors.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (focusedInfo != null) {
                            stringResource(R.string.cache_management_filtered_unit_fmt, focusedInfo.second)
                        } else {
                            stringResource(R.string.cache_management_total_unit)
                        },
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                        color = if (focusedInfo != null) colors.primary else colors.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (focusedInfo != null) {
                        stringResource(R.string.cache_management_filter_active_hint)
                    } else {
                        stringResource(
                            R.string.cache_management_last_updated_fmt,
                            formatCompactTimestamp(stringResource(R.string.cache_management_none), stats.lastUpdatedAt)
                        )
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (focusedInfo != null) colors.primary else colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(cellBg)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = formatBytes(stats.totalBytes),
                    style = MiuixTheme.textStyles.footnote1.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = colors.onSurfaceSecondary,
                    maxLines = 1
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiuixBentoCapsuleCell(
                    label = syllableLabel,
                    count = stats.syllableCount,
                    total = stats.entryCount,
                    accent = colors.primary,
                    cellBg = cellBg,
                    selectedBg = selectedBg,
                    selectedBorder = selectedBorder,
                    trackBg = trackBg,
                    selected = activeFilter == LyricCacheFilter.SYLLABLE,
                    onClick = { onFilterToggle(LyricCacheFilter.SYLLABLE) },
                    modifier = Modifier.weight(1f)
                )
                MiuixBentoCapsuleCell(
                    label = lineLabel,
                    count = stats.lineCount,
                    total = stats.entryCount,
                    accent = lineAccent,
                    cellBg = cellBg,
                    selectedBg = selectedBg,
                    selectedBorder = selectedBorder,
                    trackBg = trackBg,
                    selected = activeFilter == LyricCacheFilter.LINE,
                    onClick = { onFilterToggle(LyricCacheFilter.LINE) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiuixBentoCapsuleCell(
                    label = instLabel,
                    count = stats.instrumentalCount,
                    total = stats.entryCount,
                    accent = colors.onSurfaceVariantSummary,
                    cellBg = cellBg,
                    selectedBg = selectedBg,
                    selectedBorder = selectedBorder,
                    trackBg = trackBg,
                    selected = activeFilter == LyricCacheFilter.INSTRUMENTAL,
                    onClick = { onFilterToggle(LyricCacheFilter.INSTRUMENTAL) },
                    modifier = Modifier.weight(1f)
                )
                MiuixBentoCapsuleCell(
                    label = matchLabel,
                    count = stats.matchOnlyCount,
                    total = stats.entryCount,
                    accent = colors.onSurfaceSecondary,
                    cellBg = cellBg,
                    selectedBg = selectedBg,
                    selectedBorder = selectedBorder,
                    trackBg = trackBg,
                    selected = activeFilter == LyricCacheFilter.MATCH_ONLY,
                    onClick = { onFilterToggle(LyricCacheFilter.MATCH_ONLY) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MiuixSidecarTrackRow(
                badge = stringResource(R.string.cache_management_sidecar_translation_badge),
                label = transLabel,
                count = stats.translationCount,
                total = stats.entryCount,
                cellBg = cellBg,
                selectedBg = selectedBg,
                selectedBorder = selectedBorder,
                trackBg = trackBg,
                selected = activeFilter == LyricCacheFilter.TRANSLATION,
                onClick = { onFilterToggle(LyricCacheFilter.TRANSLATION) }
            )
            MiuixSidecarTrackRow(
                badge = stringResource(R.string.cache_management_sidecar_romanization_badge),
                label = romaLabel,
                count = stats.romanizationCount,
                total = stats.entryCount,
                cellBg = cellBg,
                selectedBg = selectedBg,
                selectedBorder = selectedBorder,
                trackBg = trackBg,
                selected = activeFilter == LyricCacheFilter.ROMANIZATION,
                onClick = { onFilterToggle(LyricCacheFilter.ROMANIZATION) }
            )
        }
    }
}

@Composable
private fun MiuixBentoCapsuleCell(
    label: String,
    count: Int,
    total: Int,
    accent: Color,
    cellBg: Color,
    selectedBg: Color,
    selectedBorder: Color,
    trackBg: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val targetFill = if (total > 0 && count > 0) {
        (count.toFloat() / total.toFloat()).coerceIn(0.05f, 1f)
    } else 0f
    val animatedFill by animateFloatAsState(targetValue = targetFill, label = "miuixBentoFill")
    val bgColor by animateColorAsState(
        targetValue = if (selected) selectedBg else cellBg,
        label = "miuixBentoBg"
    )
    val labelColor = if (selected) colors.primary else colors.onSurfaceVariantSummary
    val valueColor = when {
        selected -> colors.primary
        count == 0 -> colors.onSurfaceVariantSummary
        else -> colors.onSurface
    }
    val barColor = if (selected) colors.primary else accent

    Column(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .then(
                if (selected) Modifier.border(width = 1.dp, color = selectedBorder, shape = shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            )
            Text(
                text = formatPercentText(count, total),
                style = MiuixTheme.textStyles.footnote1.copy(fontFeatureSettings = "tnum"),
                color = labelColor,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = count.toString(),
            style = MiuixTheme.textStyles.title4.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            color = valueColor,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(trackBg)
                .drawBehind {
                    if (animatedFill > 0f) {
                        drawRoundRect(
                            color = barColor,
                            size = size.copy(width = size.width * animatedFill),
                            cornerRadius = CornerRadius(size.height / 2f)
                        )
                    }
                }
        )
    }
}

@Composable
private fun MiuixSidecarTrackRow(
    badge: String,
    label: String,
    count: Int,
    total: Int,
    cellBg: Color,
    selectedBg: Color,
    selectedBorder: Color,
    trackBg: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val targetFill = if (total > 0 && count > 0) {
        (count.toFloat() / total.toFloat()).coerceIn(0.04f, 1f)
    } else 0f
    val animatedFill by animateFloatAsState(targetValue = targetFill, label = "miuixSidecarFill")
    val barColor = colors.primary
    val bgColor by animateColorAsState(
        targetValue = if (selected) selectedBg else cellBg,
        label = "miuixSidecarBg"
    )
    val mainTextColor = if (selected) colors.primary else colors.onSurface
    val subTextColor = if (selected) colors.primary else colors.onSurfaceVariantSummary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .then(
                if (selected) Modifier.border(width = 1.dp, color = selectedBorder, shape = shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.primary)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badge,
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onPrimary,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                    color = mainTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = count.toString(),
                    style = MiuixTheme.textStyles.footnote1.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = mainTextColor,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatPercentText(count, total),
                    style = MiuixTheme.textStyles.footnote1.copy(fontFeatureSettings = "tnum"),
                    color = subTextColor,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(trackBg)
                .drawBehind {
                    if (animatedFill > 0f) {
                        drawRoundRect(
                            color = barColor,
                            size = size.copy(width = size.width * animatedFill),
                            cornerRadius = CornerRadius(size.height / 2f)
                        )
                    }
                }
        )
    }
}


