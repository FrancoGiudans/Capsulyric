package com.example.islandlyrics.feature.cache.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.R
import com.example.islandlyrics.core.cache.AppImageCacheManager
import com.example.islandlyrics.data.lyric.OnlineLyricCacheStore
import com.example.islandlyrics.feature.cache.CacheManagementViewModel
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheManagementScreen(
    onBack: () -> Unit,
    viewModel: CacheManagementViewModel = viewModel()
) {
    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior()
    val lyricStats by viewModel.lyricStats.observeAsState(OnlineLyricCacheStore.LyricCacheStats())
    val lyricEntries by viewModel.lyricEntries.observeAsState(emptyList())
    val imageStats by viewModel.imageStats.observeAsState(AppImageCacheManager.ImageCacheStats())
    val busy by viewModel.busy.observeAsState(false)
    val statusMessage by viewModel.statusMessage.observeAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeStatusMessage()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_cache_management)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cache_management_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cache_management_refresh))
                    }
                },
                colors = neutralMaterialTopBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CacheSectionCard(title = stringResource(R.string.cache_management_lyric_cache)) {
                    CacheStatRow(stringResource(R.string.cache_management_entry_count), lyricStats.entryCount.toString())
                    CacheStatRow(stringResource(R.string.cache_management_total_size), formatBytes(lyricStats.totalBytes))
                    CacheStatRow(stringResource(R.string.cache_management_last_updated), formatTimestamp(stringResource(R.string.cache_management_none), lyricStats.lastUpdatedAt))
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
            }

            if (lyricEntries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.cache_management_no_entries),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(lyricEntries, key = { it.id }) { entry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${entry.title} - ${entry.artist}", fontWeight = FontWeight.SemiBold)
                                    Text(entry.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { viewModel.deleteLyricEntry(entry.id) }, enabled = !busy) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cache_management_delete))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            CacheStatRow(stringResource(R.string.cache_management_query_info), "${entry.queryTitle} / ${entry.queryArtist}")
                            CacheStatRow(stringResource(R.string.cache_management_provider), entry.providerLabel.ifBlank { stringResource(R.string.cache_management_unknown) })
                            CacheStatRow(stringResource(R.string.cache_management_total_size), formatBytes(entry.sizeBytes))
                            CacheStatRow(stringResource(R.string.cache_management_updated_at), formatTimestamp(stringResource(R.string.cache_management_none), entry.updatedAt))
                            if (entry.hasCustomMatch) {
                                Text(stringResource(R.string.cache_management_has_custom_match), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CacheSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            content()
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

private fun formatTimestamp(emptyValue: String, timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return emptyValue
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
