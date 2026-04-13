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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.ServiceDiagnostics
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.feature.cache.CacheManagementActivity
import com.example.islandlyrics.feature.logviewer.LogViewerActivity
import com.example.islandlyrics.feature.onlinelyricdebug.OnlineLyricDebugActivity
import java.text.SimpleDateFormat
import java.util.*
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

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
                scrollBehavior = scrollBehavior,
                colors = neutralMaterialTopBarColors()
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
                    onClick = {
                        context.startActivity(android.content.Intent(context, OnlineLyricDebugActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("在线歌词调试")
                }
                if (BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            launchQqRomanDebug(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("QQ 罗马音抓取")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        context.startActivity(android.content.Intent(context, CacheManagementActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.title_cache_management))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { LogViewerActivity.start(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.summary_view_logs))
                }
            }

            // Service Diagnostics Section
            DiagnosticsCard(
                title = stringResource(R.string.diag_header_service),
                icon = Icons.Default.MonitorHeart
            ) {
                if (diagnostics == null) {
                    Text(stringResource(R.string.diag_waiting_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    InfoRow(
                        stringResource(R.string.diag_label_connection),
                        if (diagnostics?.isConnected == true) stringResource(R.string.diag_status_connected) else stringResource(R.string.diag_status_disconnected)
                    )
                    InfoRow(stringResource(R.string.diag_label_controllers), diagnostics?.totalControllers?.toString() ?: "0")
                    InfoRow(stringResource(R.string.diag_label_whitelisted), diagnostics?.whitelistedControllers?.toString() ?: "0")
                    InfoRow(stringResource(R.string.diag_label_primary_pkg), diagnostics?.primaryPackage ?: stringResource(R.string.diag_none))
                    InfoRow(stringResource(R.string.diag_label_whitelist_size), diagnostics?.whitelistSize?.toString() ?: "0")
                    InfoRow(stringResource(R.string.diag_label_last_params), diagnostics?.lastUpdateParams ?: stringResource(R.string.diag_none))
                    InfoRow(stringResource(R.string.diag_label_last_update), SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(diagnostics?.timestamp ?: 0)))
                }
            }

            // System Info Section
            DiagnosticsCard(
                title = stringResource(R.string.diag_header_system),
                icon = Icons.Default.Info
            ) {
                InfoRow(stringResource(R.string.diag_label_android_ver), "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                val romInfo = remember { RomUtils.getRomInfo() }
                if (romInfo.isNotEmpty()) {
                    InfoRow(stringResource(R.string.diag_label_rom_ver), romInfo)
                }
                InfoRow(stringResource(R.string.diag_label_rom_type), RomUtils.getRomType())
                InfoRow(stringResource(R.string.diag_label_device), "${Build.MANUFACTURER} ${Build.MODEL}")
                InfoRow(stringResource(R.string.diag_label_arch), Build.SUPPORTED_ABIS.joinToString(", "))
                InfoRow(stringResource(R.string.diag_label_build_id), Build.DISPLAY)
            }

            // Advanced Feature Checks (Compatibility based)
            val isAndroid16 = Build.VERSION.SDK_INT >= 36
            val isXiaomi = RomUtils.isXiaomi()

            if (isAndroid16 || isXiaomi) {
                DiagnosticsCard(
                    title = stringResource(R.string.diag_header_advanced),
                    icon = Icons.Default.Info,
                    trailingContent = {
                        IconButton(onClick = { LyricRepository.getInstance().refreshAdvancedDiagnostics(context) }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Refresh, contentDescription = stringResource(R.string.diag_btn_refresh), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                ) {
                    if (diagnostics == null) {
                        Text(stringResource(R.string.diag_waiting_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        if (isAndroid16) {
                            Text(stringResource(R.string.diag_section_android16), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            InfoRow(stringResource(R.string.diag_label_can_post_promoted), if (diagnostics?.canPostPromoted == true) stringResource(R.string.diag_enabled) else stringResource(R.string.diag_disabled))
                            InfoRow(stringResource(R.string.diag_label_has_promoted_char), if (diagnostics?.hasPromotableChar == true) stringResource(R.string.diag_yes) else stringResource(R.string.diag_no))
                            InfoRow(stringResource(R.string.diag_label_is_promoted), if (diagnostics?.isCurrentlyPromoted == true) stringResource(R.string.diag_yes) else stringResource(R.string.diag_no))
                            
                            if (isXiaomi) Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        if (isXiaomi) {
                            Text(stringResource(R.string.diag_section_xiaomi), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            InfoRow(stringResource(R.string.diag_label_island_support), if (diagnostics?.isIslandSupported == true) stringResource(R.string.diag_supported) else stringResource(R.string.diag_unsupported))
                            InfoRow(stringResource(R.string.diag_label_focus_version), diagnostics?.islandVersion?.toString() ?: "0")
                            InfoRow(stringResource(R.string.diag_label_focus_perm), if (diagnostics?.hasFocusPermission == true) stringResource(R.string.diag_enabled) else stringResource(R.string.diag_disabled))
                        }
                    }
                }
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
                                LyricRepository.getInstance().setDevMode(context, false)
                                onBack()
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
    trailingContent: @Composable (() -> Unit)? = null,
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
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (trailingContent != null) {
                    trailingContent()
                }
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

private fun launchQqRomanDebug(context: android.content.Context) {
    val activityClass = Class.forName("com.example.islandlyrics.feature.qqroman.QqRomanDebugActivity")
    context.startActivity(android.content.Intent(context, activityClass))
}
