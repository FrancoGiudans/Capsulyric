package com.example.islandlyrics.feature.settings

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.R
import com.example.islandlyrics.lyrics.importer.LyricifyImportMode
import com.example.islandlyrics.lyrics.importer.LyricifyImportProgress
import com.example.islandlyrics.lyrics.importer.LyricifyImportStage
import com.example.islandlyrics.lyrics.importer.LyrimuseBackupImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Activity-owned state shared by the two settings presentations. */
internal class LyrimuseImportController(application: Application) : AndroidViewModel(application) {
    enum class Phase { IDLE, PREPARING, PREVIEW, IMPORTING, CANCELLING, FINISHED, FAILED }
    data class State(
        val phase: Phase = Phase.IDLE,
        val prepared: LyrimuseBackupImporter.Prepared? = null,
        val includeLyrics: Boolean = false,
        val includeLastFm: Boolean = false,
        val mode: LyricifyImportMode = LyricifyImportMode.MERGE,
        val progress: LyricifyImportProgress = LyricifyImportProgress(LyricifyImportStage.VALIDATING),
        val report: LyrimuseBackupImporter.Report? = null,
        val failureMessage: Int = R.string.lyrimuse_import_failed,
    ) {
        val busy: Boolean get() = phase in listOf(Phase.PREPARING, Phase.IMPORTING, Phase.CANCELLING)
        val canCancel: Boolean get() = phase == Phase.PREPARING || phase == Phase.IMPORTING && progress.canCancel
    }

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private val cancellationRequested = AtomicBoolean(false)

    fun prepare(uris: List<Uri>) {
        if (state.value.busy || uris.isEmpty()) return
        cancellationRequested.set(false)
        mutableState.value = State(phase = Phase.PREPARING)
        viewModelScope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) {
                    val job = currentCoroutineContext()
                    LyrimuseBackupImporter.prepare(getApplication(), uris) {
                        job.ensureActive()
                        if (cancellationRequested.get()) throw CancellationException("Import cancelled")
                    }
                }
                if (cancellationRequested.get()) throw CancellationException("Import cancelled")
                mutableState.value = State(phase = Phase.PREVIEW, prepared = prepared, includeLyrics = prepared.tracks.isNotEmpty())
            } catch (cancelled: CancellationException) {
                finishCancellation(cancelled)
            } catch (_: Exception) {
                mutableState.value = State(phase = Phase.FAILED, failureMessage = R.string.lyrimuse_import_invalid_file)
            }
        }
    }

    fun selectLyrics(selected: Boolean) = mutableState.update {
        if (it.phase == Phase.PREVIEW) it.copy(includeLyrics = selected && it.prepared?.tracks?.isNotEmpty() == true) else it
    }

    fun selectLastFm(selected: Boolean) = mutableState.update {
        if (it.phase == Phase.PREVIEW) it.copy(includeLastFm = selected && it.prepared?.credentials != null) else it
    }

    fun selectMode(mode: LyricifyImportMode) = mutableState.update {
        if (it.phase == Phase.PREVIEW) it.copy(mode = mode) else it
    }

    fun startImport() {
        val selection = state.value
        val prepared = selection.prepared ?: return
        if (selection.phase != Phase.PREVIEW || !selection.includeLyrics && !selection.includeLastFm) return
        cancellationRequested.set(false)
        mutableState.value = selection.copy(phase = Phase.IMPORTING)
        viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    val job = currentCoroutineContext()
                    LyrimuseBackupImporter.importPrepared(getApplication(), prepared,
                        selection.includeLyrics, selection.includeLastFm, selection.mode,
                        checkActive = {
                            job.ensureActive()
                            if (cancellationRequested.get()) throw CancellationException("Import cancelled")
                        },
                        onProgress = { progress -> mutableState.update { it.copy(progress = progress) } },
                    )
                }
                mutableState.value = State(phase = Phase.FINISHED, report = report)
            } catch (cancelled: CancellationException) {
                finishCancellation(cancelled)
            } catch (error: Exception) {
                mutableState.value = State(phase = Phase.FAILED, failureMessage =
                    if (error.suppressed.isNotEmpty()) R.string.lyrimuse_import_recovery_failed else R.string.lyrimuse_import_failed)
            }
        }
    }

    private fun finishCancellation(error: CancellationException) {
        if (error.suppressed.isNotEmpty()) mutableState.value = State(phase = Phase.FAILED, failureMessage = R.string.lyrimuse_import_recovery_failed)
        else if (cancellationRequested.get()) mutableState.value = State()
        else throw error
    }

    fun cancel() {
        if (state.value.canCancel) {
            cancellationRequested.set(true)
            mutableState.update { it.copy(phase = Phase.CANCELLING) }
        }
    }

    fun dismiss() {
        if (!state.value.busy) mutableState.value = State()
    }
}

internal data class LyrimuseImportUiState(
    val state: LyrimuseImportController.State,
    val message: String,
    val chooseFiles: () -> Unit,
    val selectLyrics: (Boolean) -> Unit,
    val selectLastFm: (Boolean) -> Unit,
    val selectMode: (LyricifyImportMode) -> Unit,
    val startImport: () -> Unit,
    val cancel: () -> Unit,
    val dismiss: () -> Unit,
)

@Composable
internal fun rememberLyrimuseImporter(): LyrimuseImportUiState {
    val context = LocalContext.current
    val activity = checkNotNull(LocalActivity.current) as ComponentActivity
    val model: LyrimuseImportController = viewModel(viewModelStoreOwner = activity)
    val state by model.state.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), model::prepare)
    val progressLabel = when (state.progress.stage) {
        LyricifyImportStage.COPYING, LyricifyImportStage.VALIDATING -> R.string.lyrimuse_import_reading
        LyricifyImportStage.PARSING -> R.string.lab_lyricify_import_parsing
        LyricifyImportStage.READING_CACHE -> R.string.lab_lyricify_import_reading_cache
        LyricifyImportStage.MATCHING -> R.string.lab_lyricify_import_matching
        LyricifyImportStage.BACKING_UP -> R.string.lab_lyricify_import_backing_up
        LyricifyImportStage.WRITING -> R.string.lab_lyricify_import_writing
        LyricifyImportStage.COMMITTING -> R.string.lab_lyricify_import_committing
        LyricifyImportStage.CLEANING_UP -> R.string.lab_lyricify_import_cleaning_up
        LyricifyImportStage.ROLLING_BACK -> R.string.lab_lyricify_import_rolling_back
    }
    val message = when (state.phase) {
        LyrimuseImportController.Phase.PREPARING -> context.getString(R.string.lyrimuse_import_reading)
        LyrimuseImportController.Phase.IMPORTING -> context.getString(progressLabel) +
            state.progress.total?.let {
                val label = when (state.progress.stage) {
                    LyricifyImportStage.PARSING, LyricifyImportStage.MATCHING -> R.string.lab_lyricify_import_song_count
                    LyricifyImportStage.WRITING -> R.string.lab_lyricify_import_entry_count
                    else -> R.string.lab_lyricify_import_file_count
                }
                "\n${context.getString(label, state.progress.processed, it)}"
            }.orEmpty()
        LyrimuseImportController.Phase.CANCELLING -> context.getString(R.string.lab_lyricify_import_cancelling)
        LyrimuseImportController.Phase.FAILED -> context.getString(state.failureMessage)
        LyrimuseImportController.Phase.FINISHED -> state.report?.let { report ->
            buildList {
                if (report.imported + report.skipped + report.failed > 0)
                    add(context.getString(R.string.lab_lyricify_import_result, report.imported, report.skipped, report.failed))
                if (report.lastFmImported) add(context.getString(R.string.lyrimuse_import_lastfm_done))
                if (report.lastFmFailed) add(context.getString(R.string.lyrimuse_import_lastfm_failed))
            }.joinToString("\n")
        }.orEmpty()
        else -> ""
    }
    return LyrimuseImportUiState(state, message,
        chooseFiles = { if (!model.state.value.busy) { model.dismiss(); picker.launch(arrayOf("*/*")) } },
        selectLyrics = model::selectLyrics, selectLastFm = model::selectLastFm, selectMode = model::selectMode,
        startImport = model::startImport, cancel = model::cancel, dismiss = model::dismiss)
}
