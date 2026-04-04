package com.example.islandlyrics.feature.debug

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.SolidColor
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.DebugLyricViewModel
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.lyric.OnlineLyricFetcher
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLyricScreen(
    onBack: () -> Unit,
    viewModel: DebugLyricViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val mediaInfo by viewModel.liveMetadata.observeAsState()
    val liveProgress by viewModel.liveProgress.observeAsState()
    val liveLyric by viewModel.liveLyric.observeAsState()
    val isPlaying by viewModel.isPlaying.observeAsState(false)
    val isFetching by viewModel.isFetching.observeAsState(false)
    val apiResults by viewModel.apiResults.observeAsState(emptyList())
    val attempts by viewModel.attempts.observeAsState(emptyList())
    val selectedResult by viewModel.selectedResult.observeAsState()
    val parsedLyrics by viewModel.parsedLyrics.observeAsState(emptyList())
    val error by viewModel.error.observeAsState()
    val providerOrder by viewModel.providerOrder.observeAsState(OnlineLyricProvider.defaultOrder())
    val usedCleanTitleFallback by viewModel.usedCleanTitleFallback.observeAsState(false)
    val context = androidx.compose.ui.platform.LocalContext.current
    val showPrioritySection = remember(mediaInfo?.packageName) {
        val pkg = mediaInfo?.packageName
        pkg != null && (ParserRuleHelper.getRuleForPackage(context, pkg)?.useOnlineLyrics == true)
    }

    LaunchedEffect(mediaInfo?.packageName) {
        if (mediaInfo?.packageName != null) {
            viewModel.syncProviderOrderFromCurrentRule()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歌词调试页面") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Current Music Info Card
            DebugInfoCard(title = "当前播放音乐") {
                Text("歌曲: ${mediaInfo?.title ?: "无"}")
                Text("歌手: ${mediaInfo?.artist ?: "无"}")
                Text(
                    "播放时间: ${formatTime(liveProgress?.position ?: 0)} / ${formatTime(liveProgress?.duration ?: 0)}",
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Real-time Status Card
            DebugInfoCard(title = "实时歌词状态") {
                Text("歌词来源: ${liveLyric?.apiPath ?: "—"} / ${liveLyric?.sourceApp?.ifBlank { "—" } ?: "—"}")
                Text(
                    "当前歌词: ${liveLyric?.lyric?.ifBlank { "(空)" } ?: "—"}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    "播放应用: ${mediaInfo?.packageName ?: "—"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "播放状态: ${if (isPlaying) "▶ 播放中" else "⏸ 暂停"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (showPrioritySection) {
                DebugInfoCard(title = "在线歌词优先级") {
                    Text(
                        "当前调试请求会按下面顺序参与评分与兜底。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    providerOrder.forEachIndexed { index, provider ->
                        ProviderOrderRow(
                            provider = provider,
                            index = index,
                            canMoveUp = index > 0,
                            canMoveDown = index < providerOrder.lastIndex,
                            onMoveUp = { viewModel.moveProvider(provider, -1) },
                            onMoveDown = { viewModel.moveProvider(provider, 1) }
                        )
                    }
                    TextButton(onClick = { viewModel.resetProviderOrder() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("恢复默认顺序")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Fetch Lyrics Card
            DebugInfoCard(title = "获取歌词") {
                Text(
                    "从以下API获取歌词:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = { viewModel.fetchLyrics() },
                    enabled = !isFetching,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("获取并自动选择最佳歌词")
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("歌词结果:", fontWeight = FontWeight.Bold)

                // Syllable Preview Area
                SyllablePreview(
                    parsedLyrics = parsedLyrics,
                    currentPosition = liveProgress?.position ?: 0
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("请求详情:", fontWeight = FontWeight.Bold)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                        .padding(16.dp)
                ) {
                    val resultText = buildString {
                        if (attempts.isEmpty()) {
                            append("等待获取歌词...")
                        } else {
                            append("Provider 顺序: ${providerOrder.joinToString(" > ") { it.displayName(context) }}\n")
                            append("标题清洗兜底: ${if (usedCleanTitleFallback) "已触发" else "未触发"}\n\n")
                            attempts.forEach { attempt ->
                                val result = attempt.result
                                val prefix = if (result == selectedResult) "★ [已选择] " else "  "
                                append("$prefix${attempt.provider.displayName(context)} (${attempt.durationMs}ms)\n")
                                if (attempt.usedCleanTitleFallback) {
                                    append("  使用清洗标题重试\n")
                                }
                                if (result == null) {
                                    append("  ✗ 无可用结果\n")
                                } else if (result.error != null) {
                                    append("  ✗ 错误: ${result.error}\n")
                                } else {
                                    append("  来源: ${result.api} / 得分: ${result.score}\n")
                                    result.matchedTitle?.let { append("  标题: $it\n") }
                                    result.matchedArtist?.let { append("  艺术家: $it\n") }
                                    if (result.hasSyllable) append("  ✓ 有逐字歌词\n")
                                    else if (result.lyrics != null) append("  标准LRC歌词\n")
                                    result.lyrics?.let {
                                        append("  完整结果:\n")
                                        append(it)
                                        append("\n")
                                    }
                                }
                                append("\n")
                            }
                        }
                    }
                    Text(resultText, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("返回")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProviderOrderRow(
    provider: OnlineLyricProvider,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${index + 1}. ${provider.displayName(context)}", modifier = Modifier.weight(1f))
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
        }
    }
}

@Composable
fun DebugInfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
fun SyllablePreview(
    parsedLyrics: List<OnlineLyricFetcher.LyricLine>,
    currentPosition: Long
) {
    // We update this locally to maintain smoothness if needed, 
    // but the ViewModel/Repository updates already come at 50ms usually.
    // For Compose, we'll just react to currentPosition.
    
    val currentLine = findCurrentLine(parsedLyrics, currentPosition)
    val currentIndex = if (currentLine != null) parsedLyrics.indexOf(currentLine) else -1
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.primary)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "逐字歌词预览",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Previous Line
            Text(
                text = if (currentIndex > 0) parsedLyrics[currentIndex - 1].text else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(0.6f).heightIn(min = 20.dp),
                textAlign = TextAlign.Center
            )

            // Current Line with Syllable Highlighting
            if (currentLine != null) {
                val highlightedText = buildAnnotatedString {
                    val syllables = currentLine.syllables
                    if (syllables.isNullOrEmpty()) {
                        append(currentLine.text)
                    } else {
                        syllables.forEach { syllable ->
                            val isSung = currentPosition >= syllable.startTime
                            withStyle(
                                style = SpanStyle(
                                    color = if (isSung) Color(0xFF9162D1) else Color.Gray, // Highlight color vs Unsung color
                                    fontWeight = if (isSung) FontWeight.Bold else FontWeight.Normal
                                )
                            ) {
                                append(syllable.text)
                            }
                        }
                    }
                }
                Text(
                    text = highlightedText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp).heightIn(min = 40.dp)
                )
            } else {
                Text(
                    "等待歌词...",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp).heightIn(min = 40.dp)
                )
            }

            // Next Line
            Text(
                text = if (currentIndex != -1 && currentIndex < parsedLyrics.size - 1) parsedLyrics[currentIndex + 1].text else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(0.6f).heightIn(min = 20.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun findCurrentLine(lyrics: List<OnlineLyricFetcher.LyricLine>, position: Long): OnlineLyricFetcher.LyricLine? {
    if (lyrics.isEmpty()) return null
    
    // Binary search for performance
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
