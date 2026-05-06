package com.example.islandlyrics.feature.qqroman.material

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.lyric.NeteaseRomanFetcher
import com.example.islandlyrics.data.lyric.QqRomanFetcher
import com.example.islandlyrics.feature.qqroman.QqRomanDebugViewModel
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QqRomanDebugScreen(
    onBack: () -> Unit,
    viewModel: QqRomanDebugViewModel = viewModel()
) {
    val uiState by viewModel.uiState.observeAsState(QqRomanDebugViewModel.UiState())
    val currentSong by viewModel.liveMetadata.observeAsState()
    val liveLyric by viewModel.liveLyric.observeAsState()
    val superLyricDebug by viewModel.superLyricDebug.observeAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(currentSong?.packageName, currentSong?.title, currentSong?.artist) {
        if (uiState.queryTitle.isBlank() && currentSong != null) {
            viewModel.syncFromCurrentSong()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("罗马音/翻译调试") },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(1.dp))
            DebugCard(title = "当前播放") {
                Text("歌名: ${currentSong?.title ?: "无"}")
                Text("歌手: ${currentSong?.artist ?: "无"}")
                Text(
                    "包名: ${currentSong?.packageName ?: "无"}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DebugCard(title = "SuperLyric 实时翻译/罗马音") {
                RealtimeSourceBlock(
                    sourceName = "SuperLyric",
                    liveLyric = liveLyric
                )
            }

            DebugCard(title = "SuperLyric 原始回调") {
                SuperLyricRawBlock(superLyricDebug)
            }

            DebugCard(title = "Lyricon 实时翻译/罗马音") {
                RealtimeSourceBlock(
                    sourceName = "Lyricon",
                    liveLyric = liveLyric
                )
            }

            DebugCard(title = "查询条件") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QqRomanDebugViewModel.DebugSource.entries.forEach { source ->
                        FilterChip(
                            selected = uiState.selectedSource == source,
                            onClick = { viewModel.updateSource(source) },
                            label = { Text(source.displayName) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.queryTitle,
                    onValueChange = viewModel::updateTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("歌名") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.queryArtist,
                    onValueChange = viewModel::updateArtist,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("歌手") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.syncFromCurrentSong() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("填入当前歌曲")
                    }
                    Button(
                        onClick = { viewModel.fetch() },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.loading
                    ) {
                        if (uiState.loading) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("抓取当前源")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.fetchAll() },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.loading
                    ) {
                        Text("同时抓 QQ / 网易云")
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearResults() },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.loading && (uiState.qqResult != null || uiState.neteaseResult != null)
                    ) {
                        Text("清空结果")
                    }
                }
                uiState.error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }

            uiState.qqResult?.let { QqResult(it) }
            uiState.neteaseResult?.let { NeteaseResult(it) }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SuperLyricRawBlock(info: LyricRepository.SuperLyricDebugInfo?) {
    if (info == null) {
        Text("尚未收到 SuperLyric 回调")
        return
    }

    Text("publisher: ${info.publisher ?: "(null)"}")
    Text("package: ${info.packageName.ifBlank { "(空)" }}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    Text("hasLyric=${info.hasLyric}, hasTranslation=${info.hasTranslation}, hasSecondary=${info.hasSecondary}")
    Text("skip: ${info.skipReason ?: "(未跳过)"}")
    Text("extraKeys: ${info.extraKeys.joinToString().ifBlank { "(空)" }}")
    Spacer(modifier = Modifier.height(12.dp))
    Text("raw lyric line")
    TextBlock(info.lyricLineRaw ?: "(null)")
    Spacer(modifier = Modifier.height(12.dp))
    Text("raw translation line")
    TextBlock(info.translationLineRaw ?: "(null)")
    Spacer(modifier = Modifier.height(12.dp))
    Text("raw secondary line")
    TextBlock(info.secondaryLineRaw ?: "(null)")
    Spacer(modifier = Modifier.height(12.dp))
    Text("words preview")
    TextBlock(
        "lyric: ${info.lyricWordsPreview}\n" +
            "translation: ${info.translationWordsPreview}\n" +
            "secondary: ${info.secondaryWordsPreview}"
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text("原始主歌词")
    TextBlock(info.lyric.ifBlank { "(空)" })
    Spacer(modifier = Modifier.height(12.dp))
    Text("原始翻译")
    TextBlock(info.translation?.ifBlank { "(空)" } ?: "(空)")
    Spacer(modifier = Modifier.height(12.dp))
    Text("原始罗马音/副歌词")
    TextBlock(info.roma?.ifBlank { "(空)" } ?: "(空)")
}

@Composable
private fun RealtimeSourceBlock(
    sourceName: String,
    liveLyric: com.example.islandlyrics.data.LyricRepository.LyricInfo?
) {
    val matched = liveLyric?.apiPath.equals(sourceName, ignoreCase = true)
    if (!matched) {
        Text("当前没有 $sourceName 实时数据")
        Text(
            "当前来源: ${liveLyric?.apiPath ?: "无"}",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Text("当前歌词")
    TextBlock(liveLyric?.lyric?.ifBlank { "(空)" } ?: "(空)")
    Spacer(modifier = Modifier.height(12.dp))
    Text("翻译")
    TextBlock(liveLyric?.translation?.ifBlank { "(空)" } ?: "(空)")
    Spacer(modifier = Modifier.height(12.dp))
    Text("罗马音")
    TextBlock(liveLyric?.roma?.ifBlank { "(空)" } ?: "(空)")
}

@Composable
private fun QqResult(result: QqRomanFetcher.Result) {
    DebugCard(title = "QQ 命中结果") {
        Text("查询: ${result.queryTitle} / ${result.queryArtist.ifBlank { "—" }}")
        Text("命中歌曲: ${result.matchedTitle}")
        Text("命中歌手: ${result.matchedArtist.ifBlank { "—" }}")
        Text("songId: ${result.songId}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text("songMid: ${result.songMid}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        result.decryptError?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text("解密失败: $it", color = MaterialTheme.colorScheme.error)
            Text(
                "contentroma 长度: ${result.rawRomanPayloadLength}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (result.decryptedRomanPayloadHexPreview.isNotBlank()) {
                Text(
                    "解密后头部: ${result.decryptedRomanPayloadHexPreview}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    DebugCard(title = "QQ 完整罗马音") {
        TextBlock(result.romanLyrics.ifBlank { "(空)" })
    }

    DebugCard(title = "QQ 解密后原始内容") {
        TextBlock(result.decryptedRomanPayload.ifBlank { "(空)" })
    }

    if (result.decryptError != null) {
        DebugCard(title = "QQ 原始 contentroma 预览") {
            TextBlock(result.rawRomanPayloadPreview.ifBlank { "(空)" })
        }
    }
}

@Composable
private fun NeteaseResult(result: NeteaseRomanFetcher.Result) {
    DebugCard(title = "网易云命中结果") {
        Text("查询: ${result.queryTitle} / ${result.queryArtist.ifBlank { "—" }}")
        Text("命中歌曲: ${result.matchedTitle}")
        Text("命中歌手: ${result.matchedArtist.ifBlank { "—" }}")
        Text("songId: ${result.songId}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text("endpoint: ${result.lyricEndpoint}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text("raw length: ${result.rawLyricResponseLength}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }

    DebugCard(title = "网易云 Lrc 原文") {
        TextBlock(result.lyrics.ifBlank { "(空)" })
    }
    DebugCard(title = "网易云 Tlyric 翻译") {
        TextBlock(result.translatedLyrics.ifBlank { "(空)" })
    }
    DebugCard(title = "网易云 Romalrc 罗马音") {
        TextBlock(result.romanLyrics.ifBlank { "(空)" })
    }
    DebugCard(title = "网易云 Yrc 逐字") {
        TextBlock(result.yrcLyrics.ifBlank { "(空)" })
    }
    DebugCard(title = "网易云 Ytlrc 逐字翻译") {
        TextBlock(result.yTranslatedLyrics.ifBlank { "(空)" })
    }
    DebugCard(title = "网易云 Yromalrc 逐字罗马音") {
        TextBlock(result.yRomanLyrics.ifBlank { "(空)" })
    }
    DebugCard(title = "网易云原始响应预览") {
        TextBlock(result.rawLyricResponsePreview.ifBlank { "(空)" })
    }
}

@Composable
private fun DebugCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun TextBlock(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .background(MaterialTheme.colorScheme.surface, shape = CardDefaults.shape)
            .padding(12.dp),
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp
    )
}
