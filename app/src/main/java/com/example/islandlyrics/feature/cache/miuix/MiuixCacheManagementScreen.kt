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
import com.example.islandlyrics.ui.miuix.MiuixBlurSmallTopAppBar
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
            MiuixBlurSmallTopAppBar(
                title = stringResource(R.string.title_cache_management),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MiuixTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = MiuixTheme.colorScheme.onBackground)
                    }
                }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            item { SmallTitle(text = "歌词缓存") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MiuixStatRow("缓存条目", lyricStats.entryCount.toString())
                        MiuixStatRow("缓存体积", formatBytes(lyricStats.totalBytes))
                        MiuixStatRow("最后更新", formatTimestamp(lyricStats.lastUpdatedAt))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { viewModel.clearLyricCache() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                Text("清空歌词缓存")
                            }
                            Button(onClick = { viewModel.clearAllCaches() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                Text("清空全部缓存")
                            }
                        }
                    }
                }
            }
            item { SmallTitle(text = "图片缓存") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MiuixStatRow("缓存文件", imageStats.fileCount.toString())
                        MiuixStatRow("缓存体积", formatBytes(imageStats.totalBytes))
                        MiuixStatRow("最后更新", formatTimestamp(imageStats.lastUpdatedAt))
                        Text("包括更新日志中的远程图片缓存", color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.clearImageCache() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("清空图片缓存")
                        }
                    }
                }
            }
            item { SmallTitle(text = "歌词缓存条目") }
            if (lyricEntries.isEmpty()) {
                item {
                    Text(
                        text = "当前没有可管理的歌词缓存",
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
                                    text = "删除",
                                    onClick = { viewModel.deleteLyricEntry(entry.id) }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            MiuixStatRow("查询信息", "${entry.queryTitle} / ${entry.queryArtist}")
                            MiuixStatRow("来源", entry.providerLabel.ifBlank { "未知" })
                            MiuixStatRow("缓存体积", formatBytes(entry.sizeBytes))
                            MiuixStatRow("更新时间", formatTimestamp(entry.updatedAt))
                            if (entry.hasCustomMatch) {
                                Text("包含自定义匹配信息", color = MiuixTheme.colorScheme.primary)
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

private fun formatTimestamp(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "无"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
