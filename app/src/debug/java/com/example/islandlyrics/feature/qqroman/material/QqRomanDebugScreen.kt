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
    val scrollState = rememberScrollState()

    LaunchedEffect(currentSong?.packageName, currentSong?.title, currentSong?.artist) {
        if (uiState.queryTitle.isBlank() && currentSong != null) {
            viewModel.syncFromCurrentSong()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QQ 罗马音抓取") },
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

            DebugCard(title = "查询条件") {
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
                            Text("抓取罗马音")
                        }
                    }
                }
                uiState.error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }

            uiState.result?.let { result ->
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
                    }
                }

                DebugCard(title = "完整罗马音") {
                    TextBlock(result.romanLyrics.ifBlank { "(空)" })
                }

                DebugCard(title = "解密后原始内容") {
                    TextBlock(result.decryptedRomanPayload.ifBlank { "(空)" })
                }

                if (result.decryptError != null) {
                    DebugCard(title = "原始 contentroma 预览") {
                        TextBlock(result.rawRomanPayloadPreview.ifBlank { "(空)" })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
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
