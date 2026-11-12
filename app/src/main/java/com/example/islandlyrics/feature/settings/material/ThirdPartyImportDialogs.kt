package com.example.islandlyrics.feature.settings.material

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.settings.ImportTaskUiState
import com.example.islandlyrics.feature.settings.LyrimuseImportUiState
import com.example.islandlyrics.feature.settings.ThirdPartyImportUiState
import com.example.islandlyrics.lyrics.importer.LyricifyImportMode
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.DialogProperties
import com.example.islandlyrics.ui.material.blur.MaterialBlurAlertDialog

@Composable
internal fun ThirdPartyImportDialogs(ui: ThirdPartyImportUiState) {
    if (ui.showSources) {
        MaterialBlurAlertDialog(
            onDismissRequest = ui.dismissSources,
            title = { Text(stringResource(R.string.settings_import_other_apps)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            ui.selectLyrimuse()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.lyrimuse_import_title)) }
                    TextButton(
                        onClick = {
                            ui.selectLyricify()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.lab_lyricify_import_button)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = ui.dismissSources) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    LyricImportModeDialog(
        show = ui.showLyricifyOptions,
        onDismiss = ui.dismissLyricifyOptions,
        onChooseMode = ui.chooseLyricifyFile,
    )
    LyricImportProgressDialog(ui.lyricify.task)
    LyrimuseImportDialog(ui.lyrimuse)
}

@Composable
private fun LyrimuseImportDialog(ui: LyrimuseImportUiState) {
    LyricImportModeDialog(
        show = ui.showOptions,
        onDismiss = ui.dismissOptions,
        onChooseMode = ui.importPrepared,
        titleRes = R.string.lyrimuse_import_title,
        includeLyrics = ui.includeLyrics,
        enabled = ui.includeLyrics || ui.includeLastFm,
    ) {
        ui.previewMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = ui.includeLyrics, enabled = ui.hasLyrics, role = Role.Checkbox, onValueChange = ui.selectLyrics),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = ui.includeLyrics, onCheckedChange = null, enabled = ui.hasLyrics)
            Text(stringResource(R.string.lyrimuse_import_lyrics), color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (ui.hasLyrics) 1f else 0.38f))
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = ui.includeLastFm, enabled = ui.hasLastFm, role = Role.Checkbox, onValueChange = ui.selectLastFm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = ui.includeLastFm, onCheckedChange = null, enabled = ui.hasLastFm)
            Text(stringResource(R.string.lyrimuse_import_lastfm), color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (ui.hasLastFm) 1f else 0.38f))
        }
        if (ui.hasLyrics && !ui.hasLastFm) {
            Text(stringResource(R.string.lyrimuse_import_lastfm_invalid), style = MaterialTheme.typography.bodySmall)
        }
    }
    LyricImportProgressDialog(ui.task, titleRes = R.string.lyrimuse_import_title)
}

@Composable
private fun LyricImportProgressDialog(
    importer: ImportTaskUiState,
    titleRes: Int = R.string.lab_lyricify_import_button,
) {
    if (importer.showDialog) {
        MaterialBlurAlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(titleRes)) },
            text = {
                Column {
                    importer.message?.let { Text(it) }
                    if (importer.importing) {
                        Spacer(Modifier.height(16.dp))
                        val progress = importer.state.progress.fraction.takeIf { importer.state.canCancel }
                        if (progress == null) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        }
                        importer.count?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                if (importer.importing) {
                    TextButton(onClick = importer.cancel, enabled = importer.state.canCancel) {
                        Text(stringResource(android.R.string.cancel))
                    }
                } else {
                    TextButton(onClick = importer.dismiss) {
                        Text(stringResource(R.string.lab_lyricify_import_done))
                    }
                }
            }
        )
    }
}

@Composable
private fun LyricImportModeDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onChooseMode: (LyricifyImportMode) -> Unit,
    titleRes: Int = R.string.lab_lyricify_import_mode_title,
    includeLyrics: Boolean = true,
    enabled: Boolean = true,
    options: @Composable () -> Unit = {},
) {
    if (!show) return
    MaterialBlurAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options()
                if (includeLyrics) {
                    Text(stringResource(R.string.lab_lyricify_import_mode_desc))
                    listOf(
                        LyricifyImportMode.OVERWRITE to R.string.lab_lyricify_import_overwrite,
                        LyricifyImportMode.MERGE to R.string.lab_lyricify_import_merge,
                        LyricifyImportMode.SKIP to R.string.lab_lyricify_import_skip,
                    ).forEach { (mode, label) ->
                        TextButton(onClick = { onChooseMode(mode) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(label))
                        }
                    }
                } else {
                    TextButton(onClick = { onChooseMode(LyricifyImportMode.MERGE) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.backup_dialog_confirm))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
