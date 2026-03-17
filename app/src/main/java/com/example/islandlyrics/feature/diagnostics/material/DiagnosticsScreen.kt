package com.example.islandlyrics.feature.diagnostics.material

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.feature.logviewer.LogViewerActivity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()
    
    val diagnostics by LyricRepository.getInstance().liveDiagnostics.observeAsState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_diagnostics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Log Viewer Section
            DiagnosticsCard(
                title = stringResource(R.string.menu_log),
                icon = Icons.Default.Terminal
            ) {
                Button(
                    onClick = { LogViewerActivity.start(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.summary_view_logs))
                }
            }

            // Service Diagnostics Section
            DiagnosticsCard(
                title = "服务诊断数据",
                icon = Icons.Default.MonitorHeart
            ) {
                if (diagnostics == null) {
                    Text("等待服务数据...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    InfoRow("连接状态", if (diagnostics?.isConnected == true) "🟢 已连接" else "🔴 未连接")
                    InfoRow("总控制器数", diagnostics?.totalControllers?.toString() ?: "0")
                    InfoRow("白名单内控制器", diagnostics?.whitelistedControllers?.toString() ?: "0")
                    InfoRow("当前主要包名", diagnostics?.primaryPackage ?: "无")
                    InfoRow("白名单长度", diagnostics?.whitelistSize?.toString() ?: "0")
                    InfoRow("最后参数", diagnostics?.lastUpdateParams ?: "无")
                    InfoRow("上次更新", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(diagnostics?.timestamp ?: 0)))
                }
            }

            // System Info Section
            DiagnosticsCard(
                title = "系统环境信息",
                icon = Icons.Default.Info
            ) {
                InfoRow("Android 版本", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                val romInfo = remember { RomUtils.getRomInfo() }
                if (romInfo.isNotEmpty()) {
                    InfoRow("ROM 版本", romInfo)
                }
                InfoRow("ROM 类型", RomUtils.getRomType().toString())
                InfoRow("设备型号", "${Build.MANUFACTURER} ${Build.MODEL}")
                InfoRow("架构", Build.SUPPORTED_ABIS.joinToString(", "))
                InfoRow("Build ID", Build.DISPLAY)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Disable Diagnostics Button
            var showDisableDialog by remember { mutableStateOf(false) }
            val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", android.content.Context.MODE_PRIVATE) }

            OutlinedButton(
                onClick = { showDisableDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.btn_disable_diagnostics))
            }

            if (showDisableDialog) {
                AlertDialog(
                    onDismissRequest = { showDisableDialog = false },
                    title = { Text(stringResource(R.string.dialog_disable_diagnostics_title)) },
                    text = { Text(stringResource(R.string.dialog_disable_diagnostics_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                prefs.edit().remove("dev_mode_enabled").apply()
                                com.example.islandlyrics.core.logging.AppLogger.getInstance().enableLogging(false)
                                showDisableDialog = false
                                (context as? Activity)?.finish()
                            }
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDisableDialog = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DiagnosticsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
