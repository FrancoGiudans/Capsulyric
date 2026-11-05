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
import com.example.islandlyrics.lyrics.importer.LyricifyImportMode
import com.example.islandlyrics.lyrics.importer.LyricifyImportStage
import com.example.islandlyrics.lyrics.importer.LyrimuseBackupImporter

internal data class LyrimuseImportUiState(
    val importer: LyricifyImportUiState,
    val showOptions: Boolean,
    val includeLyrics: Boolean,
    val includeLastFm: Boolean,
    val openOptions: () -> Unit,
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
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var includeLyrics by rememberSaveable { mutableStateOf(true) }
    var includeLastFm by rememberSaveable { mutableStateOf(false) }
    var pendingMode by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val mode = pendingMode?.let { saved -> LyricifyImportMode.entries.firstOrNull { it.name == saved } }
        pendingMode = null
        if (uris.isNotEmpty() && !model.state.value.showDialog) {
            if (mode == null) {
                model.fail(IllegalStateException(context.getString(R.string.lab_lyricify_import_mode_lost)))
                return@rememberLauncherForActivityResult
            }
            val importLyrics = includeLyrics
            val importLastFm = includeLastFm
            model.runImport(LyricifyImportStage.VALIDATING) { checkActive, onProgress ->
                val prepared = LyrimuseBackupImporter.prepare(appContext, uris, checkActive)
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
    }
    val importer = rememberLyricImportUiState(model, pendingMode != null, chooseFile = { mode ->
        if (!model.state.value.showDialog && pendingMode == null && (includeLyrics || includeLastFm)) {
            pendingMode = mode.name
            showOptions = false
            picker.launch(arrayOf("*/*"))
        }
    })
    val state = importer.state
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
        importer = importer.copy(message = message),
        showOptions = showOptions,
        includeLyrics = includeLyrics,
        includeLastFm = includeLastFm,
        openOptions = {
            if (!model.state.value.showDialog && pendingMode == null) {
                includeLyrics = true
                includeLastFm = false
                showOptions = true
            }
        },
        selectLyrics = { includeLyrics = it },
        selectLastFm = { includeLastFm = it },
        dismissOptions = { showOptions = false },
    )
}
