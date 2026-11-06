package com.example.islandlyrics.feature.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.lab.LyricifyImportUiState
import com.example.islandlyrics.feature.lab.LyricifyImportViewModel
import com.example.islandlyrics.feature.lab.rememberLyricImportUiState
import com.example.islandlyrics.lyrics.importer.LyricifyDatabaseImporter
import com.example.islandlyrics.lyrics.importer.LyricifyImportStage
import com.example.islandlyrics.lyrics.importer.LyrimuseBackupImporter

internal data class LyrimuseImportUiState(
    val importer: LyricifyImportUiState,
    val showOptions: Boolean,
    val includeLyrics: Boolean,
    val includeLastFm: Boolean,
    val hasLyrics: Boolean,
    val hasLastFm: Boolean,
    val previewMessage: String?,
    val chooseFiles: () -> Unit,
    val selectLyrics: (Boolean) -> Unit,
    val selectLastFm: (Boolean) -> Unit,
    val dismissOptions: () -> Unit,
)

/** Lyrimuse file/option adapter; task execution and presentation use the Lyricify implementation. */
@Composable
internal fun rememberLyrimuseImporter(): LyrimuseImportUiState {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = checkNotNull(LocalActivity.current) as ComponentActivity
    val model: LyricifyImportViewModel = viewModel(viewModelStoreOwner = activity, key = "lyrimuse-import")
    var includeLyrics by rememberSaveable { mutableStateOf(true) }
    var includeLastFm by rememberSaveable { mutableStateOf(false) }
    var pickingFile by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pickingFile = false
        if (uris.isNotEmpty() && !model.state.value.showDialog) {
            model.runImport(LyricifyImportStage.VALIDATING) { checkActive, _ ->
                LyricifyImportViewModel.Result(prepared = LyrimuseBackupImporter.prepare(appContext, uris, checkActive))
            }
        }
    }
    // The shared mode callback now imports the prepared file instead of opening the picker.
    val importer = rememberLyricImportUiState(model, pickingFile, chooseFile = { mode ->
        val prepared = model.state.value.report?.prepared
        val importLyrics = includeLyrics && prepared?.tracks?.isNotEmpty() == true
        val importLastFm = includeLastFm && prepared?.credentials != null
        if (model.state.value.status == LyricifyImportViewModel.Status.READY && prepared != null && (importLyrics || importLastFm)) {
            model.runImport(if (importLyrics) LyricifyImportStage.READING_CACHE else LyricifyImportStage.COMMITTING) { checkActive, onProgress ->
                val report = LyrimuseBackupImporter.importPrepared(
                    appContext, prepared, importLyrics, importLastFm, mode, checkActive, onProgress,
                )
                LyricifyImportViewModel.Result(
                    lyrics = if (importLyrics) LyricifyDatabaseImporter.Report(report.imported, report.skipped, report.failed) else null,
                    extraMessages = buildList {
                        if (report.lastFmImported) add(R.string.lyrimuse_import_lastfm_done)
                        if (report.lastFmFailed) add(R.string.lyrimuse_import_lastfm_failed)
                    },
                )
            }
        }
    })
    val state = importer.state
    val prepared = state.report?.prepared
    val showOptions = state.status == LyricifyImportViewModel.Status.READY
    val hasLyrics = prepared?.tracks?.isNotEmpty() == true
    val hasLastFm = prepared?.credentials != null
    val message = when {
        state.status == LyricifyImportViewModel.Status.IMPORTING && state.progress.stage == LyricifyImportStage.VALIDATING ->
            context.getString(R.string.lyrimuse_import_reading)
        state.status == LyricifyImportViewModel.Status.FAILED -> context.getString(when {
            state.error?.suppressed?.isNotEmpty() == true -> R.string.lyrimuse_import_recovery_failed
            state.progress.stage == LyricifyImportStage.VALIDATING -> R.string.lyrimuse_import_invalid_file
            else -> R.string.lyrimuse_import_failed
        })
        else -> importer.message
    }
    return LyrimuseImportUiState(
        importer = importer.copy(message = message, pendingSelection = pickingFile || showOptions),
        showOptions = showOptions,
        includeLyrics = includeLyrics && hasLyrics,
        includeLastFm = includeLastFm && hasLastFm,
        hasLyrics = hasLyrics,
        hasLastFm = hasLastFm,
        previewMessage = prepared?.let {
            if (!hasLyrics && !hasLastFm) context.getString(R.string.lyrimuse_import_no_data)
            else context.getString(R.string.lyrimuse_import_preview, it.tracks.size, it.skipped, it.failed)
        },
        chooseFiles = {
            if (!model.state.value.showDialog && !pickingFile) {
                model.dismiss()
                includeLyrics = true
                includeLastFm = false
                pickingFile = true
                picker.launch(arrayOf("*/*"))
            }
        },
        selectLyrics = { includeLyrics = it },
        selectLastFm = { includeLastFm = it },
        dismissOptions = model::dismiss,
    )
}
