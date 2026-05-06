package com.example.islandlyrics.feature.qqroman.miuix

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.lyric.NeteaseRomanFetcher
import com.example.islandlyrics.data.lyric.QqRomanFetcher
import com.example.islandlyrics.feature.qqroman.QqRomanDebugViewModel
import com.example.islandlyrics.ui.miuix.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.MiuixBlurTopAppBar
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixQqRomanDebugScreen(
    onBack: () -> Unit,
    viewModel: QqRomanDebugViewModel = viewModel()
) {
    val uiState by viewModel.uiState.observeAsState(QqRomanDebugViewModel.UiState())
    val currentSong by viewModel.liveMetadata.observeAsState()
    val liveLyric by viewModel.liveLyric.observeAsState()
    val superLyricDebug by viewModel.superLyricDebug.observeAsState()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(currentSong?.packageName, currentSong?.title, currentSong?.artist) {
        if (uiState.queryTitle.isBlank() && currentSong != null) {
            viewModel.syncFromCurrentSong()
        }
    }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = "罗马音/翻译调试",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
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
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SmallTitle(text = "当前播放") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("歌名: ${currentSong?.title ?: "无"}")
                        Text("歌手: ${currentSong?.artist ?: "无"}")
                        Text(
                            "包名: ${currentSong?.packageName ?: "无"}",
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            item { SmallTitle(text = "SuperLyric 实时翻译/罗马音") }
            item {
                RealtimeSourceCard(
                    sourceName = "SuperLyric",
                    liveLyric = liveLyric
                )
            }
            item { SmallTitle(text = "SuperLyric 原始回调") }
            item { SuperLyricRawCard(superLyricDebug) }
            item { SmallTitle(text = "Lyricon 实时翻译/罗马音") }
            item {
                RealtimeSourceCard(
                    sourceName = "Lyricon",
                    liveLyric = liveLyric
                )
            }
            item { SmallTitle(text = "查询条件") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QqRomanDebugViewModel.DebugSource.entries.forEach { source ->
                                Button(
                                    onClick = { viewModel.updateSource(source) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (uiState.selectedSource == source) "✓ ${source.displayName}" else source.displayName)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(
                            value = uiState.queryTitle,
                            onValueChange = viewModel::updateTitle,
                            label = "歌名",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(
                            value = uiState.queryArtist,
                            onValueChange = viewModel::updateArtist,
                            label = "歌手",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
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
                                Text(if (uiState.loading) "抓取中..." else "抓取当前源")
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.fetchAll() },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.loading
                            ) {
                                Text("同时抓 QQ / 网易云")
                            }
                            Button(
                                onClick = { viewModel.clearResults() },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.loading && (uiState.qqResult != null || uiState.neteaseResult != null)
                            ) {
                                Text("清空结果")
                            }
                        }
                        uiState.error?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(it, color = MiuixTheme.colorScheme.error)
                        }
                    }
                }
            }
            uiState.qqResult?.let { result ->
                item { SmallTitle(text = "QQ 命中结果") }
                item { QqResultCard(result) }
                item { SmallTitle(text = "QQ 完整罗马音") }
                item { TextDumpCard(result.romanLyrics.ifBlank { "(空)" }) }
                item { SmallTitle(text = "QQ 解密后原始内容") }
                item { TextDumpCard(result.decryptedRomanPayload.ifBlank { "(空)" }) }
                if (result.decryptError != null) {
                    item { SmallTitle(text = "QQ 原始 contentroma 预览") }
                    item { TextDumpCard(result.rawRomanPayloadPreview.ifBlank { "(空)" }) }
                }
            }
            uiState.neteaseResult?.let { result ->
                item { SmallTitle(text = "网易云命中结果") }
                item { NeteaseResultCard(result) }
                item { SmallTitle(text = "网易云 Lrc 原文") }
                item { TextDumpCard(result.lyrics.ifBlank { "(空)" }) }
                item { SmallTitle(text = "网易云 Tlyric 翻译") }
                item { TextDumpCard(result.translatedLyrics.ifBlank { "(空)" }) }
                item { SmallTitle(text = "网易云 Romalrc 罗马音") }
                item { TextDumpCard(result.romanLyrics.ifBlank { "(空)" }) }
                item { SmallTitle(text = "网易云 Yrc 逐字") }
                item { TextDumpCard(result.yrcLyrics.ifBlank { "(空)" }) }
                item { SmallTitle(text = "网易云 Ytlrc 逐字翻译") }
                item { TextDumpCard(result.yTranslatedLyrics.ifBlank { "(空)" }) }
                item { SmallTitle(text = "网易云 Yromalrc 逐字罗马音") }
                item { TextDumpCard(result.yRomanLyrics.ifBlank { "(空)" }) }
                item { SmallTitle(text = "网易云原始响应预览") }
                item { TextDumpCard(result.rawLyricResponsePreview.ifBlank { "(空)" }) }
            }
        }
    }
}

@Composable
private fun SuperLyricRawCard(info: LyricRepository.SuperLyricDebugInfo?) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (info == null) {
                Text("尚未收到 SuperLyric 回调")
                return@Column
            }

            Text("publisher: ${info.publisher ?: "(null)"}")
            Text("package: ${info.packageName.ifBlank { "(空)" }}", fontSize = 12.sp)
            Text("hasLyric=${info.hasLyric}, hasTranslation=${info.hasTranslation}, hasSecondary=${info.hasSecondary}")
            Text("skip: ${info.skipReason ?: "(未跳过)"}")
            Text("extraKeys: ${info.extraKeys.joinToString().ifBlank { "(空)" }}")
            Spacer(modifier = Modifier.height(12.dp))
            Text("raw lyric line")
            TextDumpInner(info.lyricLineRaw ?: "(null)")
            Spacer(modifier = Modifier.height(12.dp))
            Text("raw translation line")
            TextDumpInner(info.translationLineRaw ?: "(null)")
            Spacer(modifier = Modifier.height(12.dp))
            Text("raw secondary line")
            TextDumpInner(info.secondaryLineRaw ?: "(null)")
            Spacer(modifier = Modifier.height(12.dp))
            Text("words preview")
            TextDumpInner(
                "lyric: ${info.lyricWordsPreview}\n" +
                    "translation: ${info.translationWordsPreview}\n" +
                    "secondary: ${info.secondaryWordsPreview}"
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("原始主歌词")
            TextDumpInner(info.lyric.ifBlank { "(空)" })
            Spacer(modifier = Modifier.height(12.dp))
            Text("原始翻译")
            TextDumpInner(info.translation?.ifBlank { "(空)" } ?: "(空)")
            Spacer(modifier = Modifier.height(12.dp))
            Text("原始罗马音/副歌词")
            TextDumpInner(info.roma?.ifBlank { "(空)" } ?: "(空)")
        }
    }
}

@Composable
private fun RealtimeSourceCard(
    sourceName: String,
    liveLyric: com.example.islandlyrics.data.LyricRepository.LyricInfo?
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val matched = liveLyric?.apiPath.equals(sourceName, ignoreCase = true)
            if (!matched) {
                Text("当前没有 $sourceName 实时数据")
                Text(
                    "当前来源: ${liveLyric?.apiPath ?: "无"}",
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    fontSize = 12.sp
                )
                return@Column
            }

            Text("当前歌词")
            TextDumpInner(liveLyric?.lyric?.ifBlank { "(空)" } ?: "(空)")
            Spacer(modifier = Modifier.height(12.dp))
            Text("翻译")
            TextDumpInner(liveLyric?.translation?.ifBlank { "(空)" } ?: "(空)")
            Spacer(modifier = Modifier.height(12.dp))
            Text("罗马音")
            TextDumpInner(liveLyric?.roma?.ifBlank { "(空)" } ?: "(空)")
        }
    }
}

@Composable
private fun QqResultCard(result: QqRomanFetcher.Result) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("查询: ${result.queryTitle} / ${result.queryArtist.ifBlank { "—" }}")
            Text("命中歌曲: ${result.matchedTitle}")
            Text("命中歌手: ${result.matchedArtist.ifBlank { "—" }}")
            Text("songId: ${result.songId}", fontSize = 12.sp)
            Text("songMid: ${result.songMid}", fontSize = 12.sp)
            result.decryptError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text("解密失败: $it", color = MiuixTheme.colorScheme.error)
                Text(
                    "contentroma 长度: ${result.rawRomanPayloadLength}",
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    fontSize = 12.sp
                )
                if (result.decryptedRomanPayloadHexPreview.isNotBlank()) {
                    Text(
                        "解密后头部: ${result.decryptedRomanPayloadHexPreview}",
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun NeteaseResultCard(result: NeteaseRomanFetcher.Result) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("查询: ${result.queryTitle} / ${result.queryArtist.ifBlank { "—" }}")
            Text("命中歌曲: ${result.matchedTitle}")
            Text("命中歌手: ${result.matchedArtist.ifBlank { "—" }}")
            Text("songId: ${result.songId}", fontSize = 12.sp)
            Text("endpoint: ${result.lyricEndpoint}", fontSize = 12.sp)
            Text("raw length: ${result.rawLyricResponseLength}", fontSize = 12.sp)
        }
    }
}

@Composable
private fun TextDumpCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        TextDumpInner(text)
    }
}

@Composable
private fun TextDumpInner(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .padding(16.dp),
        fontSize = 12.sp
    )
}
