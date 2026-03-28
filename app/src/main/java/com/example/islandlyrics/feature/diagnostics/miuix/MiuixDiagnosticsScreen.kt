package com.example.islandlyrics.feature.diagnostics.miuix

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.ServiceDiagnostics
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.feature.logviewer.LogViewerActivity
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
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
                bottom = padding.calculateBottomPadding() + 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            item { SmallTitle(text = stringResource(R.string.diag_header_tools)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.menu_log),
                        summary = stringResource(R.string.summary_view_logs),
                        onClick = { LogViewerActivity.start(context) }
                    )
                }
            }

            item { SmallTitle(text = stringResource(R.string.diag_header_service)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    if (diagnostics == null) {
                        Text(
                            text = stringResource(R.string.diag_waiting_data),
                            modifier = Modifier.padding(16.dp),
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    } else {
                        MiuixInfoRow(
                            stringResource(R.string.diag_label_connection),
                            if (diagnostics?.isConnected == true) stringResource(R.string.diag_status_connected) else stringResource(R.string.diag_status_disconnected)
                        )
                        MiuixInfoRow(stringResource(R.string.diag_label_controllers), diagnostics?.totalControllers?.toString() ?: "0")
                        MiuixInfoRow(stringResource(R.string.diag_label_whitelisted), diagnostics?.whitelistedControllers?.toString() ?: "0")
                        MiuixInfoRow(stringResource(R.string.diag_label_primary_pkg), diagnostics?.primaryPackage ?: stringResource(R.string.diag_none))
                        MiuixInfoRow(stringResource(R.string.diag_label_whitelist_size), diagnostics?.whitelistSize?.toString() ?: "0")
                        MiuixInfoRow(stringResource(R.string.diag_label_last_params), diagnostics?.lastUpdateParams ?: stringResource(R.string.diag_none))
                        MiuixInfoRow(stringResource(R.string.diag_label_last_update), SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(diagnostics?.timestamp ?: 0)))
                    }
                }
            }

            item { SmallTitle(text = stringResource(R.string.diag_header_system)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    MiuixInfoRow(stringResource(R.string.diag_label_android_ver), "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    val romInfo = remember { RomUtils.getRomInfo() }
                    if (romInfo.isNotEmpty()) {
                        MiuixInfoRow(stringResource(R.string.diag_label_rom_ver), romInfo)
                    }
                    MiuixInfoRow(stringResource(R.string.diag_label_rom_type), RomUtils.getRomType())
                    MiuixInfoRow(stringResource(R.string.diag_label_device), "${Build.MANUFACTURER} ${Build.MODEL}")
                    MiuixInfoRow(stringResource(R.string.diag_label_arch), Build.SUPPORTED_ABIS.joinToString(", "))
                    MiuixInfoRow(stringResource(R.string.diag_label_build_id), Build.DISPLAY)
                }
            }

            // Advanced Feature Checks (Compatibility based)
            val isAndroid16 = Build.VERSION.SDK_INT >= 36
            val isXiaomi = RomUtils.isXiaomi()

            if (isAndroid16 || isXiaomi) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SmallTitle(text = stringResource(R.string.diag_header_advanced))
                        }
                        IconButton(onClick = { LyricRepository.getInstance().refreshAdvancedDiagnostics(context) }) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.diag_btn_refresh),
                                tint = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        if (diagnostics == null) {
                            Text(
                                text = stringResource(R.string.diag_waiting_data),
                                modifier = Modifier.padding(16.dp),
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        } else {
                            if (isAndroid16) {
                                // Add a sub-header style if Miuix has one, otherwise just normal rows
                                MiuixInfoRow("--- ${stringResource(R.string.diag_section_android16)} ---", "")
                                MiuixInfoRow(stringResource(R.string.diag_label_can_post_promoted), if (diagnostics?.canPostPromoted == true) stringResource(R.string.diag_enabled) else stringResource(R.string.diag_disabled))
                                MiuixInfoRow(stringResource(R.string.diag_label_has_promoted_char), if (diagnostics?.hasPromotableChar == true) stringResource(R.string.diag_yes) else stringResource(R.string.diag_no))
                                MiuixInfoRow(stringResource(R.string.diag_label_is_promoted), if (diagnostics?.isCurrentlyPromoted == true) stringResource(R.string.diag_yes) else stringResource(R.string.diag_no))
                            }
                            
                            if (isXiaomi) {
                                MiuixInfoRow("--- ${stringResource(R.string.diag_section_xiaomi)} ---", "")
                                MiuixInfoRow(stringResource(R.string.diag_label_island_support), if (diagnostics?.isIslandSupported == true) stringResource(R.string.diag_supported) else stringResource(R.string.diag_unsupported))
                                MiuixInfoRow(stringResource(R.string.diag_label_focus_version), diagnostics?.islandVersion?.toString() ?: "0")
                                MiuixInfoRow(stringResource(R.string.diag_label_focus_perm), if (diagnostics?.hasFocusPermission == true) stringResource(R.string.diag_enabled) else stringResource(R.string.diag_disabled))
                            }
                        }
                    }
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
                                    LyricRepository.getInstance().setDevMode(context, false)
                                    onBack()
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = label, 
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = value, 
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
