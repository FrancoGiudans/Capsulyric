package com.example.islandlyrics.feature.superlyricdebug

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.R
import com.example.islandlyrics.lyrics.source.SuperLyricTraceEntry
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurTopAppBar
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackIcon
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

private const val HELP = "进入此页后开始采集；暂停后可逐条查看。仅保留最近 20 条，原始字段和转换结果各最多 32,768 字符，超出会标记截断。退出后停止采集。时间单位为毫秒。"
private const val RAW_TITLE = "API 回调原始字段（SDK 已解码，应用尚未转换）"
private const val DECODED_TITLE = "应用转换结果（时间轴 / 当前行 / 逐字 / 翻译 / 罗马音）"

private fun sections(entry: SuperLyricTraceEntry?): List<Pair<String, String>> = if (entry == null) {
    listOf("等待数据" to "请在已启用 SuperLyric 的播放器规则下播放歌曲。此页使用现有连接，不会启动额外的 Receiver。")
} else listOf(
    "回调 #${entry.id} · ${entry.event} · ${entry.mode}" to
        "时间: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp))}\n${entry.decision}",
    RAW_TITLE to entry.raw,
    DECODED_TITLE to entry.decoded
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SuperLyricDebugScreen(onBack: () -> Unit, model: SuperLyricDebugViewModel = viewModel()) {
    val entries by model.entries.collectAsState()
    val capturing by model.capturing.collectAsState()
    val status by LyricRepository.getInstance().liveSuperLyricDebug.observeAsState()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val entry = entries.firstOrNull { it.id == selectedId } ?: entries.firstOrNull()
    val index = entries.indexOf(entry)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("SuperLyric 数据对照") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "返回") }
            })
        },
        containerColor = materialPageContainerColor()
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(HELP, style = MaterialTheme.typography.bodyMedium)
                Text("连接: ${status?.mode ?: "未知"} · 注册: ${status?.registrationResult ?: "等待状态"} · API: ${status?.serverApiVersion ?: "未知"}")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { selectedId = null; model.capture(!capturing) }, modifier = Modifier.weight(1f)) {
                        Text(if (capturing) "暂停采集" else "继续采集")
                    }
                    Button(onClick = { model.clear(); selectedId = null }, modifier = Modifier.weight(1f)) { Text("清空") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { selectedId = entries[index + 1].id }, enabled = !capturing && index >= 0 && index < entries.lastIndex, modifier = Modifier.weight(1f)) { Text("上一条") }
                    Button(onClick = { selectedId = entries[index - 1].id }, enabled = !capturing && index > 0, modifier = Modifier.weight(1f)) { Text("下一条") }
                    Button(onClick = { entry?.let { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("SuperLyric", it.export()))) } } }, enabled = entry != null, modifier = Modifier.weight(1f)) { Text("复制") }
                }
            }
            items(sections(entry), key = { it.first }) { (title, body) ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        SelectionContainer { Text(body, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MiuixSuperLyricDebugScreen(onBack: () -> Unit, model: SuperLyricDebugViewModel = viewModel()) {
    val entries by model.entries.collectAsState()
    val capturing by model.capturing.collectAsState()
    val status by LyricRepository.getInstance().liveSuperLyricDebug.observeAsState()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val entry = entries.firstOrNull { it.id == selectedId } ?: entries.firstOrNull()
    val index = entries.indexOf(entry)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(title = "SuperLyric 数据对照", scrollBehavior = scrollBehavior, navigationIcon = {
                MiuixIconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) { MiuixBackIcon(contentDescription = "返回") }
            })
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiuixText(HELP, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    MiuixText("连接: ${status?.mode ?: "未知"} · 注册: ${status?.registrationResult ?: "等待状态"} · API: ${status?.serverApiVersion ?: "未知"}", style = MiuixTheme.textStyles.body2)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiuixButton(onClick = { selectedId = null; model.capture(!capturing) }, modifier = Modifier.weight(1f)) { MiuixText(if (capturing) "暂停采集" else "继续采集") }
                        MiuixButton(onClick = { model.clear(); selectedId = null }, modifier = Modifier.weight(1f)) { MiuixText("清空") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiuixButton(onClick = { selectedId = entries[index + 1].id }, enabled = !capturing && index >= 0 && index < entries.lastIndex, modifier = Modifier.weight(1f)) { MiuixText("上一条") }
                        MiuixButton(onClick = { selectedId = entries[index - 1].id }, enabled = !capturing && index > 0, modifier = Modifier.weight(1f)) { MiuixText("下一条") }
                        MiuixButton(onClick = { entry?.let { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("SuperLyric", it.export()))) } } }, enabled = entry != null, modifier = Modifier.weight(1f)) { MiuixText("复制") }
                    }
                }
            }
            items(sections(entry), key = { it.first }) { (title, body) ->
                MiuixCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiuixText(title, style = MiuixTheme.textStyles.subtitle)
                        SelectionContainer { MiuixText(body, style = MiuixTheme.textStyles.body2.copy(fontFamily = FontFamily.Monospace), color = MiuixTheme.colorScheme.onSurface) }
                    }
                }
            }
        }
    }
}
