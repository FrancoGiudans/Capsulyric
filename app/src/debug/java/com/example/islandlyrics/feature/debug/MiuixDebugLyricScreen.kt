package com.example.islandlyrics.feature.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.DebugLyricViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixDebugLyricScreen(
    onBack: () -> Unit,
    viewModel: DebugLyricViewModel = viewModel()
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val mediaInfo by viewModel.liveMetadata.observeAsState()
    val liveProgress by viewModel.liveProgress.observeAsState()
    val liveLyric by viewModel.liveLyric.observeAsState()
    val isPlaying by viewModel.isPlaying.observeAsState(false)
    val isFetching by viewModel.isFetching.observeAsState(false)
    val apiResults by viewModel.apiResults.observeAsState(emptyList())
    val selectedResult by viewModel.selectedResult.observeAsState()
    val parsedLyrics by viewModel.parsedLyrics.observeAsState(emptyList())
    val error by viewModel.error.observeAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "歌词调试页面",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        }
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
            item { SmallTitle(text = "当前播放音乐") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("歌曲: ${mediaInfo?.title ?: "无"}")
                        Text("歌手: ${mediaInfo?.artist ?: "无"}", color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        Text(
                            "播放时间: ${formatTime(liveProgress?.position ?: 0)} / ${formatTime(liveProgress?.duration ?: 0)}",
                            color = MiuixTheme.colorScheme.primary
                        )
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

            item { SmallTitle(text = "获取歌词") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            text = if (isFetching) "获取中..." else "选择API并获取歌词",
                            onClick = { viewModel.fetchLyrics() },
                            enabled = !isFetching,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        error?.let {
                            Text(it, color = MiuixTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("逐字歌词预览", color = MiuixTheme.colorScheme.primary)
                        
                        // Reuse the SyllablePreview but adjust colors for Miuix if necessary
                        // For simplicity, I'll implement a Miuix-styled version here
                        MiuixSyllablePreview(
                            parsedLyrics = parsedLyrics,
                            currentPosition = liveProgress?.position ?: 0
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("API 结果评分", color = MiuixTheme.colorScheme.primary)
                        
                        apiResults.forEach { result ->
                            val isSelected = result == selectedResult
                            Text(
                                text = "${if (isSelected) "★ " else ""}${result.api}: ${result.score}分" + 
                                       (if (result.error != null) " (错误: ${result.error})" else ""),
                                color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    text = "返回",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@Composable
fun MiuixSyllablePreview(
    parsedLyrics: List<com.example.islandlyrics.data.lyric.OnlineLyricFetcher.LyricLine>,
    currentPosition: Long
) {
    // Similar to SyllablePreview in Material 3 version but with Miuix styling
    val currentLine = findCurrentLine(parsedLyrics, currentPosition)
    val currentIndex = if (currentLine != null) parsedLyrics.indexOf(currentLine) else -1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        // Previous
        Text(
            text = if (currentIndex > 0) parsedLyrics[currentIndex - 1].text else "",
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.heightIn(min = 20.dp),
            fontSize = MiuixTheme.textStyles.body2.fontSize
        )

        // Current
        if (currentLine != null) {
            val highlightedText = androidx.compose.ui.text.buildAnnotatedString {
                val syllables = currentLine.syllables
                if (syllables.isNullOrEmpty()) {
                    append(currentLine.text)
                } else {
                    syllables.forEach { syllable ->
                        val isSung = currentPosition >= syllable.startTime
                        withStyle(
                            style = androidx.compose.ui.text.SpanStyle(
                                color = if (isSung) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
                                fontWeight = if (isSung) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                        ) {
                            append(syllable.text)
                        }
                    }
                }
            }
            Text(
                text = highlightedText,
                fontSize = MiuixTheme.textStyles.title2.fontSize,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp).heightIn(min = 40.dp)
            )
        } else {
            Text(
                "等待歌词...",
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(vertical = 4.dp).heightIn(min = 40.dp)
            )
        }

        // Next
        Text(
            text = if (currentIndex != -1 && currentIndex < parsedLyrics.size - 1) parsedLyrics[currentIndex + 1].text else "",
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.heightIn(min = 20.dp),
            fontSize = MiuixTheme.textStyles.body2.fontSize
        )
    }
}

// Logic helpers (replicated from Material 3 version for self-containment or could be moved to a Utils file)
private fun findCurrentLine(lyrics: List<com.example.islandlyrics.data.lyric.OnlineLyricFetcher.LyricLine>, position: Long): com.example.islandlyrics.data.lyric.OnlineLyricFetcher.LyricLine? {
    if (lyrics.isEmpty()) return null
    var left = 0
    var right = lyrics.size - 1
    while (left <= right) {
        val mid = (left + right) / 2
        val line = lyrics[mid]
        if (position >= line.startTime && position < line.endTime) return line
        if (position < line.startTime) right = mid - 1 else left = mid + 1
    }
    if (position >= lyrics.last().startTime) return lyrics.last()
    return null
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
