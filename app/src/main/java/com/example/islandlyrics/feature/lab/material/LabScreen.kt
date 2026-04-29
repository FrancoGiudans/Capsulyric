package com.example.islandlyrics.feature.lab.material

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.feature.customsettings.CustomSettingsActivity
import com.example.islandlyrics.feature.diagnostics.material.DiagnosticsCard
import com.example.islandlyrics.feature.settings.material.SettingsSwitchItem
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var offlineModeEnabled by remember { mutableStateOf(OfflineModeManager.isEnabled(context)) }
    var superIslandAdvancedStyleEnabled by remember {
        mutableStateOf(LabFeatureManager.isSuperIslandAdvancedStyleEnabled(context))
    }
    var floatingLyricsLabEnabled by remember {
        mutableStateOf(LabFeatureManager.isFloatingLyricsEnabled(context))
    }
    var experimentUpdatesEnabled by remember {
        mutableStateOf(LabFeatureManager.isExperimentUpdatesEnabled(context))
    }
    var showAdvancedStyleDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_lab)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.online_lyric_debug_back))
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            DiagnosticsCard(
                title = stringResource(R.string.diag_header_laboratory),
                icon = Icons.Default.Science
            ) {
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_full_offline_mode),
                    subtitle = stringResource(R.string.settings_full_offline_mode_desc),
                    checked = offlineModeEnabled,
                    onCheckedChange = {
                        offlineModeEnabled = it
                        OfflineModeManager.setEnabled(context, it)
                    }
                )

                if (RomUtils.isXiaomi()) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.diag_lab_super_island_advanced_style_title),
                        subtitle = stringResource(R.string.diag_lab_super_island_advanced_style_desc),
                        checked = superIslandAdvancedStyleEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showAdvancedStyleDialog = true
                            } else {
                                LabFeatureManager.setSuperIslandAdvancedStyleEnabled(context, false)
                                superIslandAdvancedStyleEnabled = false
                            }
                        }
                    )
                }

                SettingsSwitchItem(
                    title = stringResource(R.string.diag_lab_experiment_updates_title),
                    subtitle = stringResource(R.string.diag_lab_experiment_updates_desc),
                    checked = experimentUpdatesEnabled,
                    onCheckedChange = {
                        experimentUpdatesEnabled = it
                        LabFeatureManager.setExperimentUpdatesEnabled(context, it)
                    }
                )

                SettingsSwitchItem(
                    title = stringResource(R.string.diag_lab_floating_lyrics_title),
                    subtitle = stringResource(R.string.diag_lab_floating_lyrics_desc),
                    checked = floatingLyricsLabEnabled,
                    onCheckedChange = {
                        floatingLyricsLabEnabled = it
                        LabFeatureManager.setFloatingLyricsEnabled(context, it)
                    }
                )
            }
        }
    }

    if (showAdvancedStyleDialog) {
        AlertDialog(
            onDismissRequest = { showAdvancedStyleDialog = false },
            title = { Text(stringResource(R.string.diag_lab_super_island_dialog_title)) },
            text = { Text(stringResource(R.string.diag_lab_super_island_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        LabFeatureManager.setSuperIslandAdvancedStyleEnabled(context, true)
                        superIslandAdvancedStyleEnabled = true
                        showAdvancedStyleDialog = false
                        context.startActivity(Intent(context, CustomSettingsActivity::class.java))
                    }
                ) {
                    Text(stringResource(R.string.diag_lab_super_island_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdvancedStyleDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
