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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    var pendingDeleteEntryId by remember { mutableStateOf<String?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    val filteredLyricEntries = remember(lyricEntries, searchQuery) {
        lyricEntries.filterByCacheQuery(searchQuery)
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
                    CacheStatRow(stringResource(R.string.cache_management_entry_count), lyricStats.entryCount.toString())
                    CacheStatRow(stringResource(R.string.cache_management_total_size), formatBytes(lyricStats.totalBytes))
                    CacheStatRow(stringResource(R.string.cache_management_last_updated), formatTimestamp(stringResource(R.string.cache_management_none), lyricStats.lastUpdatedAt))
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
