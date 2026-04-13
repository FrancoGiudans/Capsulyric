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
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(currentSong?.packageName, currentSong?.title, currentSong?.artist) {
        if (uiState.queryTitle.isBlank() && currentSong != null) {
            viewModel.syncFromCurrentSong()
        }
    }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = "QQ 罗马音抓取",
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
            item { SmallTitle(text = "查询条件") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                Text(if (uiState.loading) "抓取中..." else "抓取罗马音")
                            }
                        }
                        uiState.error?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(it, color = MiuixTheme.colorScheme.error)
                        }
                    }
                }
            }
            uiState.result?.let { result ->
                item { SmallTitle(text = "QQ 命中结果") }
                item {
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
                            }
                        }
                    }
                }
                item { SmallTitle(text = "完整罗马音") }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Text(
                            text = result.romanLyrics.ifBlank { "(空)" },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .padding(16.dp),
                            fontSize = 12.sp
                        )
                    }
                }
                item { SmallTitle(text = "解密后原始内容") }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Text(
                            text = result.decryptedRomanPayload.ifBlank { "(空)" },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .padding(16.dp),
                            fontSize = 12.sp
                        )
                    }
                }
                if (result.decryptError != null) {
                    item { SmallTitle(text = "原始 contentroma 预览") }
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            Text(
                                text = result.rawRomanPayloadPreview.ifBlank { "(空)" },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp)
                                    .padding(16.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
