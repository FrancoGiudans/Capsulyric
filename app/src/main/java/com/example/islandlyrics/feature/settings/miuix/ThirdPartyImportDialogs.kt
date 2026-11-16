package com.example.islandlyrics.feature.settings.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.settings.ImportTaskUiState
import com.example.islandlyrics.feature.settings.LyrimuseImportUiState
import com.example.islandlyrics.feature.settings.ThirdPartyImportUiState
import com.example.islandlyrics.lyrics.importer.LyricifyImportMode
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurDialog
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MiuixThirdPartyImportDialogs(ui: ThirdPartyImportUiState) {
    MiuixBlurDialog(
        show = ui.showSources,
        title = stringResource(R.string.settings_import_other_apps),
        onDismissRequest = ui.dismissSources,
    ) {
        Column(Modifier.fillMaxWidth()) {
            TextButton(
                text = stringResource(R.string.lyrimuse_import_title),
                onClick = {
                    ui.selectLyrimuse()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                text = stringResource(R.string.lab_lyricify_import_button),
                onClick = {
                    ui.selectLyricify()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = ui.dismissSources,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.onSurfaceVariantActions),
            )
        }
    }
    MiuixLyricImportModeDialog(
        show = ui.showLyricifyOptions,
        onDismiss = ui.dismissLyricifyOptions,
        onChooseMode = ui.chooseLyricifyFile,
    )
    MiuixLyricImportProgressDialog(ui.lyricify.task)
    MiuixLyrimuseImportDialog(ui.lyrimuse)
}

@Composable
private fun MiuixLyrimuseImportDialog(ui: LyrimuseImportUiState) {
    MiuixLyricImportModeDialog(
        show = ui.showOptions,
        onDismiss = ui.dismissOptions,
        onChooseMode = ui.importPrepared,
        titleRes = R.string.lyrimuse_import_title,
        includeLyrics = ui.includeLyrics,
        enabled = ui.includeLyrics || ui.includeLastFm,
    ) {
        ui.previewMessage?.let {
            Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary)
            Spacer(Modifier.height(8.dp))
        }
        CheckboxPreference(
            title = stringResource(R.string.lyrimuse_import_lyrics),
            checked = ui.includeLyrics,
            enabled = ui.hasLyrics,
            onCheckedChange = ui.selectLyrics,
        )
        CheckboxPreference(
            title = stringResource(R.string.lyrimuse_import_lastfm),
            checked = ui.includeLastFm,
            enabled = ui.hasLastFm,
            onCheckedChange = ui.selectLastFm,
        )
        if (ui.hasLyrics && !ui.hasLastFm) {
            Text(stringResource(R.string.lyrimuse_import_lastfm_invalid), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
    }
    MiuixLyricImportProgressDialog(ui.task, titleRes = R.string.lyrimuse_import_title)
}

@Composable
private fun MiuixLyricImportProgressDialog(
    importer: ImportTaskUiState,
    titleRes: Int = R.string.lab_lyricify_import_button,
) {
    MiuixBlurDialog(
        title = stringResource(titleRes),
        show = importer.showDialog,
        onDismissRequest = {}
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            importer.message?.let {
                Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurface)
            }
            if (importer.importing) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = importer.state.progress.fraction.takeIf { importer.state.canCancel }
                )
                importer.count?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(
                text = stringResource(if (importer.importing) android.R.string.cancel else R.string.lab_lyricify_import_done),
                onClick = if (importer.importing) importer.cancel else importer.dismiss,
                enabled = !importer.importing || importer.state.canCancel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MiuixLyricImportModeDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onChooseMode: (LyricifyImportMode) -> Unit,
    titleRes: Int = R.string.lab_lyricify_import_mode_title,
    includeLyrics: Boolean = true,
    enabled: Boolean = true,
    options: @Composable () -> Unit = {},
) {
    MiuixBlurDialog(
        title = stringResource(titleRes),
        summary = if (includeLyrics) stringResource(R.string.lab_lyricify_import_mode_desc) else null,
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            options()
            if (includeLyrics) {
                listOf(
                    Triple(LyricifyImportMode.OVERWRITE, R.string.lab_lyricify_import_overwrite_title, R.string.lab_lyricify_import_overwrite_summary),
                    Triple(LyricifyImportMode.MERGE, R.string.lab_lyricify_import_merge_title, R.string.lab_lyricify_import_merge_summary),
                    Triple(LyricifyImportMode.SKIP, R.string.lab_lyricify_import_skip_title, R.string.lab_lyricify_import_skip_summary),
                ).forEach { (mode, title, summary) ->
                    BasicComponent(
                        title = stringResource(title),
                        summary = stringResource(summary),
                        enabled = enabled,
                        insideMargin = PaddingValues(vertical = 12.dp),
                        role = Role.Button,
                        onClick = { onChooseMode(mode) },
                    )
                }
            } else {
                TextButton(
                    text = stringResource(R.string.backup_dialog_confirm),
                    enabled = enabled,
                    onClick = { onChooseMode(LyricifyImportMode.MERGE) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.onSurfaceVariantActions),
            )
        }
    }
}
