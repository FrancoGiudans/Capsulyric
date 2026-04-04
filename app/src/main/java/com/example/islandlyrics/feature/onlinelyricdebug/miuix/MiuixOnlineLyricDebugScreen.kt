package com.example.islandlyrics.feature.onlinelyricdebug.miuix

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
import com.example.islandlyrics.feature.onlinelyricdebug.OnlineLyricDebugViewModel
import com.example.islandlyrics.ui.miuix.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.MiuixBlurTopAppBar
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixOnlineLyricDebugScreen(
    onBack: () -> Unit,
    viewModel: OnlineLyricDebugViewModel = viewModel()
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val mediaInfo by viewModel.liveMetadata.observeAsState()
    val liveProgress by viewModel.liveProgress.observeAsState()
    val liveLyric by viewModel.liveLyric.observeAsState()
    val isPlaying by viewModel.isPlaying.observeAsState(false)
    val isFetching by viewModel.isFetching.observeAsState(false)
    val attempts by viewModel.attempts.observeAsState(emptyList())
    val selectedResult by viewModel.selectedResult.observeAsState()
    val error by viewModel.error.observeAsState()
    val providerOrder by viewModel.providerOrder.observeAsState(OnlineLyricProvider.defaultOrder())
    val usedCleanTitleFallback by viewModel.usedCleanTitleFallback.observeAsState(false)
    val dialogAttempt by viewModel.dialogAttempt.observeAsState()
    val showPrioritySection = remember(mediaInfo?.packageName) {
        val pkg = mediaInfo?.packageName
        pkg != null && (ParserRuleHelper.getRuleForPackage(context, pkg)?.useOnlineLyrics == true)
    }

    LaunchedEffect(mediaInfo?.packageName) {
        if (mediaInfo?.packageName != null) viewModel.syncProviderOrderFromCurrentRule()
    }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = "在线歌词调试",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MiuixTheme.colorScheme.onBackground)
                    }
                }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 24.dp)
        ) {
            item { SmallTitle(text = "当前播放音乐") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("歌曲: ${mediaInfo?.title ?: "无"}")
                        Text("歌手: ${mediaInfo?.artist ?: "无"}", color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        Text("播放时间: ${formatTime(liveProgress?.position ?: 0)} / ${formatTime(liveProgress?.duration ?: 0)}", color = MiuixTheme.colorScheme.primary)
                    }
                }
            }
            item { SmallTitle(text = "实时歌词状态") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("歌词来源: ${liveLyric?.apiPath ?: "—"} / ${liveLyric?.sourceApp?.ifBlank { "—" } ?: "—"}")
                        Text("当前歌词: ${liveLyric?.lyric?.ifBlank { "(空)" } ?: "—"}", color = MiuixTheme.colorScheme.primary)
                        Text("播放应用: ${mediaInfo?.packageName ?: "—"}", color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        Text("播放状态: ${if (isPlaying) "▶ 播放中" else "⏸ 暂停"}")
                    }
                }
            }
            if (showPrioritySection) {
                item { SmallTitle(text = "在线歌词优先级") }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            providerOrder.forEachIndexed { index, provider ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}. ${provider.displayName}", modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.moveProvider(provider, -1) }, enabled = index > 0) {
                                        androidx.compose.material3.Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                                    }
                                    IconButton(onClick = { viewModel.moveProvider(provider, 1) }, enabled = index < providerOrder.lastIndex) {
                                        androidx.compose.material3.Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                                    }
                                }
                            }
                            Button(onClick = { viewModel.resetProviderOrder() }) {
                                androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("恢复默认顺序")
                            }
                        }
                    }
                }
            }
            item { SmallTitle(text = "获取歌词") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.fetchLyrics() }, enabled = !isFetching, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isFetching) "获取中..." else "获取并自动选择最佳歌词")
                        }
                        error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
                        Text("Provider 顺序: ${providerOrder.joinToString(" > ") { it.displayName }}")
                        Text("标题清洗兜底: ${if (usedCleanTitleFallback) "已触发" else "未触发"}")
                        attempts.forEach { attempt ->
                            val result = attempt.result
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = result != null) { viewModel.openAttempt(attempt) }
                                    .padding(vertical = 6.dp)
                            ) {
                                Text("${if (result == selectedResult) "★ " else ""}${attempt.provider.displayName} (${attempt.durationMs}ms)")
                                Text(
                                    when {
                                        result == null -> "无可用结果"
                                        result.error != null -> "错误: ${result.error}"
                                        else -> "${result.api} / ${result.score}分 / ${if (result.hasSyllable) "逐字" else "LRC或文本"}"
                                    },
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    fontSize = 13.sp
                                )
                                if (result != null && result.error == null) {
                                    Text("点击查看完整结果", color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Text("返回")
                }
            }
        }

        dialogAttempt?.let { attempt ->
            val result = attempt.result
            MiuixBlurDialog(
                title = "${attempt.provider.displayName} 最终结果",
                show = true,
                onDismissRequest = { viewModel.closeDialog() }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("耗时: ${attempt.durationMs}ms")
                    if (attempt.usedCleanTitleFallback) {
                        Text("本次使用了清洗标题兜底", color = MiuixTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result?.error?.let { "错误: $it" } ?: result?.lyrics ?: "无可用结果",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
