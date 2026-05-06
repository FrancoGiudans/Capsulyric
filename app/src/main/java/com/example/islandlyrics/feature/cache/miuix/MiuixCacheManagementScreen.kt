package com.example.islandlyrics.feature.cache.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
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
import com.example.islandlyrics.ui.miuix.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.MiuixBlurTopAppBar
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MiuixCacheManagementScreen(
    onBack: () -> Unit,
    viewModel: CacheManagementViewModel = viewModel()
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val lyricStats by viewModel.lyricStats.observeAsState(OnlineLyricCacheStore.LyricCacheStats())
    val lyricEntries by viewModel.lyricEntries.observeAsState(emptyList())
    val imageStats by viewModel.imageStats.observeAsState(AppImageCacheManager.ImageCacheStats())
    val busy by viewModel.busy.observeAsState(false)
    val statusMessage by viewModel.statusMessage.observeAsState()

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            viewModel.consumeStatusMessage()
        }
    }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = stringResource(R.string.title_cache_management),
                largeTitle = stringResource(R.string.title_cache_management),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cache_management_back), tint = MiuixTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cache_management_refresh), tint = MiuixTheme.colorScheme.onBackground)
                    }
                }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            item { SmallTitle(text = stringResource(R.string.cache_management_lyric_cache)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MiuixStatRow(stringResource(R.string.cache_management_entry_count), lyricStats.entryCount.toString())
                        MiuixStatRow(stringResource(R.string.cache_management_total_size), formatBytes(lyricStats.totalBytes))
                        MiuixStatRow(stringResource(R.string.cache_management_last_updated), formatTimestamp(stringResource(R.string.cache_management_none), lyricStats.lastUpdatedAt))
                        Spacer(modifier = Modifier.height(12.dp))
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
                    }
                }
            }
            item { SmallTitle(text = stringResource(R.string.cache_management_entries_title)) }
            if (lyricEntries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.cache_management_no_entries),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            } else {
                items(lyricEntries, key = { it.id }) { entry ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${entry.title} - ${entry.artist}", fontWeight = FontWeight.SemiBold)
                                    Text(entry.packageName, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                                }
                                TextButton(
                                    text = stringResource(R.string.cache_management_delete),
                                    onClick = { viewModel.deleteLyricEntry(entry.id) }
                                )
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
                        }
                    }
                }
            }
        }
    }
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
