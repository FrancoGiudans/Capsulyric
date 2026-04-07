package com.example.islandlyrics.feature.onlinelyricdebug.material

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
import com.example.islandlyrics.feature.onlinelyricdebug.OnlineLyricDebugViewModel
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineLyricDebugScreen(
    onBack: () -> Unit,
    viewModel: OnlineLyricDebugViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val mediaInfo by viewModel.liveMetadata.observeAsState()
    val liveProgress by viewModel.liveProgress.observeAsState()
    val liveLyric by viewModel.liveLyric.observeAsState()
    val isPlaying by viewModel.isPlaying.observeAsState(false)
    val isFetching by viewModel.isFetching.observeAsState(false)
    val attempts by viewModel.attempts.observeAsState(emptyList())
    val selectedResult by viewModel.selectedResult.observeAsState()
    val error by viewModel.error.observeAsState()
    val providerOrder by viewModel.providerOrder.observeAsState(OnlineLyricProvider.defaultOrder())
    val useSmartSelection by viewModel.useSmartSelection.observeAsState(true)
    val usedCleanTitleFallback by viewModel.usedCleanTitleFallback.observeAsState(false)
    val dialogAttempt by viewModel.dialogAttempt.observeAsState()
    val customMatchTitle by viewModel.customMatchTitle.observeAsState("")
    val customMatchArtist by viewModel.customMatchArtist.observeAsState("")
    val effectiveQuery by viewModel.effectiveQuery.observeAsState("" to "")
    val querySourceLabel by viewModel.querySourceLabel.observeAsState("")
    val cacheStatus by viewModel.cacheStatus.observeAsState()
    val canSelectDialogResult = dialogAttempt?.result?.let { result ->
        result.error == null && !result.parsedLines.isNullOrEmpty()
    } == true
    val showPrioritySection = remember(mediaInfo?.packageName, useSmartSelection) {
        val pkg = mediaInfo?.packageName
        pkg != null &&
            (ParserRuleHelper.getRuleForPackage(context, pkg)?.useOnlineLyrics == true) &&
            !useSmartSelection
    }

    LaunchedEffect(mediaInfo?.packageName) {
        if (mediaInfo?.packageName != null) {
            viewModel.syncProviderOrderFromCurrentRule()
            viewModel.syncCurrentSongQuery()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("在线歌词调试") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = neutralMaterialTopBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(1.dp))
            DebugInfoCard(title = "当前播放音乐") {
                Text("歌曲: ${mediaInfo?.title ?: "无"}")
                Text("歌手: ${mediaInfo?.artist ?: "无"}")
                Text("匹配歌名: ${effectiveQuery.first.ifBlank { "无" }}")
                Text("匹配歌手: ${effectiveQuery.second.ifBlank { "无" }}")
                Text(querySourceLabel, color = MaterialTheme.colorScheme.secondary)
                cacheStatus?.let {
                    Text(it, color = MaterialTheme.colorScheme.tertiary)
                }
                Text(
                    "播放时间: ${formatTime(liveProgress?.position ?: 0)} / ${formatTime(liveProgress?.duration ?: 0)}",
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }

            DebugInfoCard(title = "当前歌曲在线匹配") {
                OutlinedTextField(
                    value = customMatchTitle,
                    onValueChange = viewModel::updateCustomMatchTitle,
                    label = { Text("自定义匹配歌名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = customMatchArtist,
                    onValueChange = viewModel::updateCustomMatchArtist,
                    label = { Text("自定义匹配歌手") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveCurrentSongMatchOverride() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存到缓存")
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearCurrentSongMatchOverride() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("清除自定义")
                    }
                }
            }

            DebugInfoCard(title = "实时歌词状态") {
                Text("歌词来源: ${liveLyric?.apiPath ?: "—"} / ${liveLyric?.sourceApp?.ifBlank { "—" } ?: "—"}")
                Text("当前歌词: ${liveLyric?.lyric?.ifBlank { "(空)" } ?: "—"}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("播放应用: ${mediaInfo?.packageName ?: "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("播放状态: ${if (isPlaying) "▶ 播放中" else "⏸ 暂停"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }

            if (showPrioritySection) {
                DebugInfoCard(title = "在线歌词优先级") {
                    providerOrder.forEachIndexed { index, provider ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("${index + 1}. ${provider.displayName(context)}", modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.moveProvider(provider, -1) }, enabled = index > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                            }
                            IconButton(onClick = { viewModel.moveProvider(provider, 1) }, enabled = index < providerOrder.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                            }
                        }
                    }
                    TextButton(onClick = { viewModel.resetProviderOrder() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("恢复默认顺序")
                    }
                }
            }

            DebugInfoCard(title = "获取歌词") {
                Button(onClick = { viewModel.fetchLyrics() }, enabled = !isFetching, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors()) {
                    if (isFetching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("优先使用缓存获取歌词")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.fetchLyrics(forceRefresh = true) },
                    enabled = !isFetching,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("忽略缓存并强制联网刷新")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (useSmartSelection) {
                        "获取模式: 智能获取"
                    } else {
                        "Provider 顺序: ${providerOrder.joinToString(" > ") { it.displayName(context) }}"
                    }
                )
                Text("标题清洗兜底: ${if (usedCleanTitleFallback) "已触发" else "未触发"}")
                Text("当前实际查询: ${effectiveQuery.first.ifBlank { "无" }} / ${effectiveQuery.second.ifBlank { "无" }}")
                Spacer(modifier = Modifier.height(8.dp))
                attempts.forEach { attempt ->
                    val result = attempt.result
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = result != null) { viewModel.openAttempt(attempt) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (result == selectedResult) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("${if (result == selectedResult) "★ " else ""}${attempt.provider.displayName(context)} (${attempt.durationMs}ms)", fontWeight = FontWeight.SemiBold)
                            if (attempt.usedCleanTitleFallback) {
                                Text("使用清洗标题重试", color = MaterialTheme.colorScheme.tertiary)
                            }
                            Text(
                                when {
                                    result == null -> "无可用结果"
                                    result.error != null -> "错误: ${result.error}"
                                    else -> "${result.api} / ${result.score}分 / ${if (result.hasSyllable) "逐字" else "LRC或文本"}"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (result != null && result.error == null) {
                                Text("点击查看完整结果", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    dialogAttempt?.let { attempt ->
        val result = attempt.result
        AlertDialog(
            onDismissRequest = { viewModel.closeDialog() },
            title = { Text("${attempt.provider.displayName(context)} 最终结果") },
            text = {
                Column {
                    Text("耗时: ${attempt.durationMs}ms")
                    if (attempt.usedCleanTitleFallback) {
                        Text("本次使用了清洗标题兜底", color = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result?.error?.let { "错误: $it" } ?: result?.lyrics ?: "无可用结果",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 360.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                if (canSelectDialogResult) {
                    TextButton(onClick = { viewModel.selectAttempt(attempt) }, enabled = !isFetching) {
                        Text("选中这个结果")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeDialog() }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun DebugInfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
