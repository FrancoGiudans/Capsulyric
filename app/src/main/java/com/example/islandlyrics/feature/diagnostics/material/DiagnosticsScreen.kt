package com.example.islandlyrics.feature.diagnostics.material

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import com.example.islandlyrics.data.LyricRepository
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.feature.logviewer.LogViewerActivity
import com.example.islandlyrics.feature.settings.material.SettingsActionItem
import com.example.islandlyrics.feature.settings.material.SettingsCard
import com.example.islandlyrics.feature.settings.material.SettingsSectionHeader
import java.text.SimpleDateFormat
import java.util.*
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    onOpenLogViewer: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val diagnostics by LyricRepository.getInstance().liveDiagnostics.observeAsState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.title_diagnostics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.online_lyric_debug_back))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = neutralMaterialTopBarColors()
            )
        },
        containerColor = materialPageContainerColor()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (BuildConfig.DEBUG) {
                item { SettingsSectionHeader(text = stringResource(R.string.diag_header_debug_tools)) }
                item {
                    SettingsCard {
                        SettingsActionItem(
                            title = stringResource(R.string.diag_qq_roman_debug_title),
                            icon = Icons.Default.Terminal,
                            onClick = { launchQqRomanDebug(context) }
                        )
                    }
                }
            }

            item { SettingsSectionHeader(text = stringResource(R.string.diag_header_maintenance_tools)) }
            item {
                SettingsCard {
                    SettingsActionItem(
                        title = stringResource(R.string.summary_view_logs),
                        icon = Icons.Default.Info,
                        onClick = {
                            onOpenLogViewer?.invoke() ?: LogViewerActivity.start(context)
                        }
                    )
                }
            }

            // ── Service status ────────────────────────────────────────────────
            item { SettingsSectionHeader(text = stringResource(R.string.diag_header_service)) }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (diagnostics == null) {
                            Text(stringResource(R.string.diag_waiting_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            InfoRow(stringResource(R.string.diag_label_connection), if (diagnostics?.isConnected == true) stringResource(R.string.diag_status_connected) else stringResource(R.string.diag_status_disconnected))
                            InfoRow(stringResource(R.string.diag_label_controllers), diagnostics?.totalControllers?.toString() ?: "0")
                            InfoRow(stringResource(R.string.diag_label_whitelisted), diagnostics?.whitelistedControllers?.toString() ?: "0")
                            InfoRow(stringResource(R.string.diag_label_primary_pkg), diagnostics?.primaryPackage ?: stringResource(R.string.diag_none))
                            InfoRow(stringResource(R.string.diag_label_whitelist_size), diagnostics?.whitelistSize?.toString() ?: "0")
                            InfoRow(stringResource(R.string.diag_label_last_params), diagnostics?.lastUpdateParams ?: stringResource(R.string.diag_none))
                            val timestampText = remember(diagnostics?.timestamp, locale) {
                                SimpleDateFormat("HH:mm:ss", locale).format(Date(diagnostics?.timestamp ?: 0))
                            }
                            InfoRow(stringResource(R.string.diag_label_last_update), timestampText)
                        }
                    }
                }
            }

            // ── System ────────────────────────────────────────────────────────
            item { SettingsSectionHeader(text = stringResource(R.string.diag_header_system)) }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        InfoRow(stringResource(R.string.diag_label_android_ver), "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                        val romInfo = remember { RomUtils.getRomInfo() }
                        if (romInfo.isNotEmpty()) InfoRow(stringResource(R.string.diag_label_rom_ver), romInfo)
                        InfoRow(stringResource(R.string.diag_label_rom_type), RomUtils.getRomType())
                        InfoRow(stringResource(R.string.diag_label_device), "${Build.MANUFACTURER} ${Build.MODEL}")
                        InfoRow(stringResource(R.string.diag_label_arch), Build.SUPPORTED_ABIS.joinToString(", "))
                        InfoRow(stringResource(R.string.diag_label_build_id), Build.DISPLAY)
                    }
                }
            }

            // ── Advanced ──────────────────────────────────────────────────────
            val isAndroid16 = Build.VERSION.SDK_INT >= 36
            val isXiaomi = RomUtils.isXiaomi()
            if (isAndroid16 || isXiaomi) {
                item {
                    SettingsSectionHeader(
                        text = stringResource(R.string.diag_header_advanced),
                    )
                }
                item {
                    SettingsCard {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { LyricRepository.getInstance().refreshAdvancedDiagnostics(context) }) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.diag_btn_refresh), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
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
                }
            }

            // ── Disable diagnostics ───────────────────────────────────────────
            item {
                var showDisableDialog by remember { mutableStateOf(false) }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(
                        onClick = { showDisableDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.btn_disable_diagnostics))
                    }
                }
                if (showDisableDialog) {
                    AlertDialog(
                        onDismissRequest = { showDisableDialog = false },
                        title = { Text(stringResource(R.string.dialog_disable_diagnostics_title)) },
                        text = { Text(stringResource(R.string.dialog_disable_diagnostics_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                LyricRepository.getInstance().setDevMode(context, false)
                                onBack()
                            }) { Text(stringResource(android.R.string.ok)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDisableDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                        }
                    )
                }
            }
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
