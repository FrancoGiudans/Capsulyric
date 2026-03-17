package com.example.islandlyrics.feature.diagnostics.miuix

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.feature.logviewer.LogViewerActivity
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MiuixDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val diagnostics by LyricRepository.getInstance().liveDiagnostics.observeAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.title_diagnostics),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            item { SmallTitle(text = "常用工具") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.menu_log),
                        summary = stringResource(R.string.summary_view_logs),
                        onClick = { LogViewerActivity.start(context) }
                    )
                }
            }

            item { SmallTitle(text = "服务诊断数据") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    if (diagnostics == null) {
                        Text(
                            text = "等待服务数据...",
                            modifier = Modifier.padding(16.dp),
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    } else {
                        MiuixInfoRow("连接状态", if (diagnostics?.isConnected == true) "🟢 已连接" else "🔴 未连接")
                        MiuixInfoRow("总控制器数", diagnostics?.totalControllers?.toString() ?: "0")
                        MiuixInfoRow("白名单内控制器", diagnostics?.whitelistedControllers?.toString() ?: "0")
                        MiuixInfoRow("当前主要包名", diagnostics?.primaryPackage ?: "无")
                        MiuixInfoRow("最后更新", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(diagnostics?.timestamp ?: 0)))
                    }
                }
            }

            item { SmallTitle(text = "系统环境信息") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    MiuixInfoRow("Android 版本", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    val romInfo = remember { RomUtils.getRomInfo() }
                    if (romInfo.isNotEmpty()) {
                        MiuixInfoRow("ROM 版本", romInfo)
                    }
                    MiuixInfoRow("设备型号", "${Build.MANUFACTURER} ${Build.MODEL}")
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                var showDisableDialog = remember { mutableStateOf(false) }
                val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", android.content.Context.MODE_PRIVATE) }

                androidx.compose.material3.TextButton(
                    onClick = { showDisableDialog.value = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MiuixTheme.colorScheme.error
                    )
                ) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.btn_disable_diagnostics),
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                }

                if (showDisableDialog.value) {
                    SuperDialog(
                        title = stringResource(R.string.dialog_disable_diagnostics_title),
                        summary = stringResource(R.string.dialog_disable_diagnostics_message),
                        show = showDisableDialog,
                        onDismissRequest = { showDisableDialog.value = false }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                text = stringResource(android.R.string.cancel),
                                onClick = { showDisableDialog.value = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColors(
                                    textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            TextButton(
                                text = stringResource(android.R.string.ok),
                                onClick = {
                                    prefs.edit().remove("dev_mode_enabled").apply()
                                    com.example.islandlyrics.core.logging.AppLogger.getInstance().enableLogging(false)
                                    showDisableDialog.value = false
                                    (context as? android.app.Activity)?.finish()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiuixInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MiuixTheme.colorScheme.onSurfaceSecondary)
        Text(value, color = MiuixTheme.colorScheme.onSurface)
    }
}
