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

package com.example.islandlyrics.feature.cache.material

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.example.islandlyrics.ui.material.blur.MaterialBlurAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.example.islandlyrics.R
import com.example.islandlyrics.core.cache.AppImageCacheManager
import com.example.islandlyrics.lyrics.local.LocalLyricDirectoryManager
import com.example.islandlyrics.lyrics.cache.OnlineLyricCacheStore
import com.example.islandlyrics.feature.cache.CacheManagementViewModel
import com.example.islandlyrics.feature.cache.LyricCacheFilter
import com.example.islandlyrics.feature.cache.filterByCacheCategory
import com.example.islandlyrics.feature.cache.filterByCacheQuery
import com.example.islandlyrics.feature.settings.material.SettingsCardDivider
import com.example.islandlyrics.feature.settings.material.SettingsSwitchItem
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.material.blur.MaterialBlurScaffold
import com.example.islandlyrics.ui.theme.material.MaterialBlurTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CacheManagementScreen(
    onBack: () -> Unit,
    viewModel: CacheManagementViewModel = viewModel()
) {
    val context = LocalContext.current
    val dirManager = remember { LocalLyricDirectoryManager.getInstance(context) }
    val lyricStats by viewModel.lyricStats.observeAsState(OnlineLyricCacheStore.LyricCacheStats())
    val lyricEntries by viewModel.lyricEntries.observeAsState(emptyList())
    val imageStats by viewModel.imageStats.observeAsState(AppImageCacheManager.ImageCacheStats())
    val busy by viewModel.busy.observeAsState(false)
    val statusMessage by viewModel.statusMessage.observeAsState()
    val selectedIds by viewModel.selectedIds.observeAsState(emptySet())
    val isSelectionMode by viewModel.isSelectionMode.observeAsState(false)
    var exportMatchInfoEnabled by remember { mutableStateOf(dirManager.isExportMatchSyncEnabled()) }
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
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
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var viewingDetail by remember { mutableStateOf<OnlineLyricCacheStore.LyricCacheDetail?>(null) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
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

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isSelectionMode,
        onBackCompleted = { viewModel.exitSelectionMode() }
    )

    MaterialBlurScaffold(
        topBar = {
            MaterialBlurTopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) {
                            stringResource(R.string.cache_management_selected_count, selectedIds.size)
                        } else {
                            stringResource(R.string.title_cache_management)
                        }
                    )
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cache_management_deselect))
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cache_management_back))
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.cache_management_select_all))
                        }
                        IconButton(onClick = { viewModel.exportSelected() }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.cache_management_export_selected))
                        }
                        IconButton(
                            onClick = { if (selectedIds.isNotEmpty()) showDeleteSelectedDialog = true },
                            enabled = selectedIds.isNotEmpty() && !busy
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cache_management_delete_selected), tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cache_management_refresh))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = materialPageContainerColor()
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = padding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current) + 24.dp,
                top = padding.calculateTopPadding() + 24.dp,
                end = padding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current) + 24.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CacheSectionCard(title = stringResource(R.string.cache_management_lyric_cache)) {
                    MaterialLyricCacheOverview(
                        stats = lyricStats,
                        activeFilter = activeFilter,
                        onFilterToggle = { tapped ->
                            if (lyricStats.entryCount > 0 || activeFilter != null) {
                                activeFilter = if (activeFilter == tapped) null else tapped
                            }
                        },
                        onClearFilter = { activeFilter = null }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsCardDivider()
                    SettingsSwitchItem(
                        title = stringResource(R.string.cache_management_export_match_info_title),
                        subtitle = stringResource(R.string.cache_management_export_match_info_desc),
                        checked = exportMatchInfoEnabled,
                        onCheckedChange = { enabled ->
                            exportMatchInfoEnabled = enabled
                            dirManager.setExportMatchSyncEnabled(enabled)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.clearLyricCache() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cache_management_clear_lyric_cache))
                    }
                }
            }

            item {
                CacheSectionCard(title = stringResource(R.string.cache_management_image_cache)) {
                    CacheStatRow(stringResource(R.string.cache_management_file_count), imageStats.fileCount.toString())
                    CacheStatRow(stringResource(R.string.cache_management_total_size), formatBytes(imageStats.totalBytes))
                    CacheStatRow(stringResource(R.string.cache_management_last_updated), formatTimestamp(stringResource(R.string.cache_management_none), imageStats.lastUpdatedAt))
                    Text(
                        text = stringResource(R.string.cache_management_remote_images_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.clearImageCache() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cache_management_clear_image_cache))
                    }
                }
            }

            item {
                CacheSectionCard(title = stringResource(R.string.cache_management_danger_zone)) {
                    Text(
                        text = stringResource(R.string.cache_management_clear_all_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.clearAllCaches() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cache_management_clear_all), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsCardDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.cache_management_clear_all_app_data_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showClearAppDataDialog = true },
                        enabled = !busy,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cache_management_clear_all_app_data))
                    }
                }
            }

            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.cache_management_entries_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.cache_management_search_entries)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (lyricEntries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.cache_management_no_entries),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (filteredLyricEntries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.cache_management_no_search_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredLyricEntries, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
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
                            ),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelectionMode) {
                                    Checkbox(
                                        checked = selectedIds.contains(entry.id),
                                        onCheckedChange = { viewModel.toggleSelection(entry.id) }
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${entry.title} - ${entry.artist}", fontWeight = FontWeight.SemiBold)
                                    Text(entry.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            CacheStatRow(stringResource(R.string.cache_management_query_info), "${entry.queryTitle} / ${entry.queryArtist}")
                            CacheStatRow(stringResource(R.string.cache_management_provider), entry.providerLabel.ifBlank { stringResource(R.string.cache_management_unknown) })
                            CacheStatRow(stringResource(R.string.cache_management_sidecars), formatSidecarFlags(entry.hasTranslation, entry.hasRomanization))
                            CacheStatRow(stringResource(R.string.cache_management_total_size), formatBytes(entry.sizeBytes))
                            CacheStatRow(stringResource(R.string.cache_management_updated_at), formatTimestamp(stringResource(R.string.cache_management_none), entry.updatedAt))
                            if (entry.hasCustomMatch) {
                                Text(stringResource(R.string.cache_management_has_custom_match), color = MaterialTheme.colorScheme.primary)
                            }
                            if (!isSelectionMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { viewModel.exportEntry(entry.id) }, enabled = !busy) {
                                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.cache_management_export))
                                    }
                                    IconButton(onClick = { pendingDeleteEntryId = entry.id }, enabled = !busy) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cache_management_delete), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteEntryId?.let { entryId ->
        MaterialBlurAlertDialog(
            onDismissRequest = { pendingDeleteEntryId = null },
            title = { Text(stringResource(R.string.cache_management_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.cache_management_delete_confirm_message,
                        pendingDeleteEntry?.displayName() ?: stringResource(R.string.cache_management_unknown)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteEntryId = null
                        viewModel.deleteLyricEntry(entryId)
                    }
                ) {
                    Text(stringResource(R.string.cache_management_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntryId = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showDeleteSelectedDialog) {
        MaterialBlurAlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(R.string.cache_management_delete_selected_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.cache_management_delete_selected_confirm_message,
                        selectedIds.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteSelectedDialog = false
                        viewModel.deleteSelected()
                    }
                ) {
                    Text(stringResource(R.string.cache_management_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showClearAppDataDialog) {
        MaterialBlurAlertDialog(
            onDismissRequest = { showClearAppDataDialog = false },
            title = { Text(stringResource(R.string.cache_management_clear_all_app_data_confirm_title)) },
            text = {
                Text(stringResource(R.string.cache_management_clear_all_app_data_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAppDataDialog = false
                        viewModel.clearAllAppData()
                    }
                ) {
                    Text(stringResource(R.string.cache_management_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAppDataDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    viewingDetail?.let { detail ->
        MaterialBlurAlertDialog(
            onDismissRequest = { viewingDetail = null },
            title = {
                Text(
                    text = detail.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (detail.providerLabel.isNotBlank() || detail.queryTitle.isNotBlank() || detail.queryArtist.isNotBlank()) {
                        val queryInfo = if (detail.queryTitle.isNotBlank() || detail.queryArtist.isNotBlank()) {
                            "${detail.queryTitle} / ${detail.queryArtist}"
                        } else ""
                        val subtitle = listOf(detail.providerLabel, queryInfo).filter { it.isNotBlank() }.joinToString(" • ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                    if (detail.isInstrumental) {
                        Text(
                            text = stringResource(R.string.online_lyric_debug_instrumental_status),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (detail.lyrics.isBlank()) {
                        Text(
                            text = stringResource(R.string.online_lyric_rematch_no_lyrics),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.online_lyric_debug_result_main_lyrics),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = detail.lyrics,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!detail.translationLyrics.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.online_lyric_debug_result_translation),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = detail.translationLyrics,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (!detail.romanLyrics.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.online_lyric_debug_result_romanization),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = detail.romanLyrics,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingDetail = null }) {
                    Text(stringResource(R.string.online_lyric_debug_close))
                }
            }
        )
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
private fun CacheSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun CacheStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun MaterialLyricCacheOverview(
    stats: OnlineLyricCacheStore.LyricCacheStats,
    activeFilter: LyricCacheFilter?,
    onFilterToggle: (LyricCacheFilter) -> Unit,
    onClearFilter: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

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
        modifier = Modifier.fillMaxWidth(),
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
                        style = typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
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
                        style = typography.labelLarge,
                        color = if (focusedInfo != null) colors.primary else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp)
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
                    style = typography.bodySmall,
                    color = if (focusedInfo != null) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = formatBytes(stats.totalBytes),
                    style = typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSecondaryContainer,
                    maxLines = 1
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaterialBentoCapsuleCell(
                    label = syllableLabel,
                    count = stats.syllableCount,
                    total = stats.entryCount,
                    accent = colors.primary,
                    selected = activeFilter == LyricCacheFilter.SYLLABLE,
                    onClick = { onFilterToggle(LyricCacheFilter.SYLLABLE) },
                    modifier = Modifier.weight(1f)
                )
                MaterialBentoCapsuleCell(
                    label = lineLabel,
                    count = stats.lineCount,
                    total = stats.entryCount,
                    accent = colors.secondary,
                    selected = activeFilter == LyricCacheFilter.LINE,
                    onClick = { onFilterToggle(LyricCacheFilter.LINE) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaterialBentoCapsuleCell(
                    label = instLabel,
                    count = stats.instrumentalCount,
                    total = stats.entryCount,
                    accent = colors.outline,
                    selected = activeFilter == LyricCacheFilter.INSTRUMENTAL,
                    onClick = { onFilterToggle(LyricCacheFilter.INSTRUMENTAL) },
                    modifier = Modifier.weight(1f)
                )
                MaterialBentoCapsuleCell(
                    label = matchLabel,
                    count = stats.matchOnlyCount,
                    total = stats.entryCount,
                    accent = colors.tertiary,
                    selected = activeFilter == LyricCacheFilter.MATCH_ONLY,
                    onClick = { onFilterToggle(LyricCacheFilter.MATCH_ONLY) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MaterialSidecarTrackRow(
                badge = stringResource(R.string.cache_management_sidecar_translation_badge),
                label = transLabel,
                count = stats.translationCount,
                total = stats.entryCount,
                selected = activeFilter == LyricCacheFilter.TRANSLATION,
                onClick = { onFilterToggle(LyricCacheFilter.TRANSLATION) }
            )
            MaterialSidecarTrackRow(
                badge = stringResource(R.string.cache_management_sidecar_romanization_badge),
                label = romaLabel,
                count = stats.romanizationCount,
                total = stats.entryCount,
                selected = activeFilter == LyricCacheFilter.ROMANIZATION,
                onClick = { onFilterToggle(LyricCacheFilter.ROMANIZATION) }
            )
        }
    }
}

@Composable
private fun MaterialBentoCapsuleCell(
    label: String,
    count: Int,
    total: Int,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val targetFill = if (total > 0 && count > 0) {
        (count.toFloat() / total.toFloat()).coerceIn(0.06f, 1f)
    } else 0f
    val animatedFill by animateFloatAsState(targetValue = targetFill, label = "md3BentoFill")
    val cornerRadius by animateDpAsState(targetValue = if (selected) 24.dp else 16.dp, label = "md3BentoRadius")
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        label = "md3BentoContainer"
    )
    val labelColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    val valueColor = when {
        selected -> colors.onPrimaryContainer
        count == 0 -> colors.onSurfaceVariant
        else -> colors.onSurface
    }
    val barColor = if (selected) colors.onPrimaryContainer else accent
    val trackColor = colors.outlineVariant.copy(alpha = 0.45f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
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
                style = typography.labelMedium,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            )
            Text(
                text = formatPercentText(count, total),
                style = typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = labelColor,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = count.toString(),
            style = typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(trackColor)
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
private fun MaterialSidecarTrackRow(
    badge: String,
    label: String,
    count: Int,
    total: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val targetFill = if (total > 0 && count > 0) {
        (count.toFloat() / total.toFloat()).coerceIn(0.04f, 1f)
    } else 0f
    val animatedFill by animateFloatAsState(targetValue = targetFill, label = "md3SidecarFill")
    val cornerRadius by animateDpAsState(targetValue = if (selected) 20.dp else 14.dp, label = "md3SidecarRadius")
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        label = "md3SidecarContainer"
    )
    val mainTextColor = if (selected) colors.onPrimaryContainer else colors.onSurface
    val subTextColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    val barColor = if (selected) colors.onPrimaryContainer else colors.primary
    val trackColor = colors.outlineVariant.copy(alpha = 0.45f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
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
                        style = typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPrimary,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = typography.labelMedium,
                    color = mainTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = count.toString(),
                    style = typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = mainTextColor,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatPercentText(count, total),
                    style = typography.labelSmall.copy(fontFeatureSettings = "tnum"),
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
                .background(trackColor)
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
