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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.R
import com.example.islandlyrics.lyrics.importer.LyricifyDatabaseImporter
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

internal data class ImportTaskUiState(
    val state: ThirdPartyImportViewModel.State,
    val message: String?,
    val count: String?,
    val cancel: () -> Unit,
    val dismiss: () -> Unit,
) {
    val importing: Boolean get() = state.importing
    val showDialog: Boolean get() = state.showDialog
}

internal data class LyricifyImportUiState(
    val task: ImportTaskUiState,
    val pendingSelection: Boolean,
    val chooseFile: (LyricifyImportMode) -> Unit,
)

internal data class LyrimuseImportUiState(
    val task: ImportTaskUiState,
    val pendingSelection: Boolean,
    val showOptions: Boolean,
    val includeLyrics: Boolean,
    val includeLastFm: Boolean,
    val hasLyrics: Boolean,
    val hasLastFm: Boolean,
    val previewMessage: String?,
    val chooseFiles: () -> Unit,
    val importPrepared: (LyricifyImportMode) -> Unit,
    val selectLyrics: (Boolean) -> Unit,
    val selectLastFm: (Boolean) -> Unit,
    val dismissOptions: () -> Unit,
)

internal data class ThirdPartyImportUiState(
    val lyricify: LyricifyImportUiState,
    val lyrimuse: LyrimuseImportUiState,
    val showSources: Boolean,
    val showLyricifyOptions: Boolean,
    val openSources: () -> Unit,
    val dismissSources: () -> Unit,
    val selectLyrimuse: () -> Unit,
    val selectLyricify: () -> Unit,
    val dismissLyricifyOptions: () -> Unit,
    val chooseLyricifyFile: (LyricifyImportMode) -> Unit,
) {
    val importing: Boolean get() = lyricify.task.importing || lyrimuse.task.importing
    val busy: Boolean get() = lyricify.task.showDialog || lyricify.pendingSelection ||
        lyrimuse.task.showDialog || lyrimuse.pendingSelection || lyrimuse.showOptions ||
        showSources || showLyricifyOptions
    val restoreSettings: Boolean get() = busy
}

/** One task implementation, with a separate Activity-owned instance for each source. */
internal class ThirdPartyImportViewModel(application: Application) : AndroidViewModel(application) {
    enum class Status { IDLE, PREPARING, READY, IMPORTING, CANCELLING, FINISHED, FAILED }

    data class LyricsReport(val imported: Int, val skipped: Int, val failed: Int)
    data class Result(val lyrics: LyricsReport? = null, val extraMessages: List<Int> = emptyList())

    data class State(
        val status: Status = Status.IDLE,
        val progress: LyricifyImportProgress = LyricifyImportProgress(LyricifyImportStage.COPYING),
        val prepared: LyrimuseBackupImporter.Prepared? = null,
        val result: Result? = null,
        val error: Throwable? = null,
    ) {
        val importing: Boolean get() = status == Status.PREPARING || status == Status.IMPORTING || status == Status.CANCELLING
        val showDialog: Boolean get() = status != Status.IDLE && status != Status.READY
        val canCancel: Boolean get() = (status == Status.PREPARING || status == Status.IMPORTING) && progress.canCancel
    }

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private val cancellationRequested = AtomicBoolean(false)

    fun importLyricify(uri: Uri, mode: LyricifyImportMode) {
        runTask(Status.IMPORTING, LyricifyImportStage.COPYING) { _, onProgress ->
            val report = LyricifyDatabaseImporter.importDatabase(
                getApplication<Application>(), uri, mode, onProgress, cancellationRequested::get,
            )
            State(status = Status.FINISHED, result = Result(LyricsReport(report.imported, report.skipped, report.failed)))
        }
    }

    fun prepareLyrimuse(uris: List<Uri>) {
        runTask(Status.PREPARING, LyricifyImportStage.VALIDATING) { checkActive, _ ->
            val prepared = LyrimuseBackupImporter.prepare(getApplication<Application>(), uris, checkActive)
            checkActive()
            State(status = Status.READY, prepared = prepared)
        }
    }

    fun importLyrimuse(includeLyrics: Boolean, includeLastFm: Boolean, mode: LyricifyImportMode) {
        val prepared = state.value.prepared ?: return
        if (state.value.status != Status.READY || (!includeLyrics && !includeLastFm)) return
        val stage = if (includeLyrics) LyricifyImportStage.READING_CACHE else LyricifyImportStage.COMMITTING
        runTask(Status.IMPORTING, stage) { checkActive, onProgress ->
            val report = LyrimuseBackupImporter.importPrepared(
                getApplication<Application>(), prepared, includeLyrics, includeLastFm, mode, checkActive, onProgress,
            )
            State(status = Status.FINISHED, result = Result(
                lyrics = if (includeLyrics) LyricsReport(report.imported, report.skipped, report.failed) else null,
                extraMessages = buildList {
                    if (report.lastFmImported) add(R.string.lyrimuse_import_lastfm_done)
                    if (report.lastFmFailed) add(R.string.lyrimuse_import_lastfm_failed)
                },
            ))
        }
    }

    private fun runTask(
        status: Status,
        initialStage: LyricifyImportStage,
        task: suspend (checkActive: () -> Unit, onProgress: (LyricifyImportProgress) -> Unit) -> State,
    ) {
        if (state.value.showDialog) return
        cancellationRequested.set(false)
        mutableState.value = State(status = status, progress = LyricifyImportProgress(initialStage))
        viewModelScope.launch {
            try {
                val completed = withContext(Dispatchers.IO) {
                    val job = currentCoroutineContext()
                    task(
                        {
                            job.ensureActive()
                            if (cancellationRequested.get()) throw CancellationException("Import cancelled")
                        },
                        { progress -> mutableState.update { it.copy(progress = progress) } },
                    )
                }
                mutableState.value = if (status == Status.PREPARING && cancellationRequested.get()) State() else completed
            } catch (cancelled: CancellationException) {
                if (cancelled.suppressed.isNotEmpty()) fail(cancelled)
                else if (cancellationRequested.get()) mutableState.value = State()
                else throw cancelled
            } catch (error: Exception) {
                fail(error)
            }
        }
    }

    fun cancel() {
        if (!state.value.canCancel) return
        // A cooperative request lets a completed commit return its actual result.
        cancellationRequested.set(true)
        mutableState.update { if (it.importing) it.copy(status = Status.CANCELLING) else it }
    }

    fun dismiss() {
        if (!state.value.importing) mutableState.value = State()
    }

    fun fail(error: Throwable) {
        mutableState.value = State(status = Status.FAILED, progress = state.value.progress, error = error)
    }
}

/** Register both pickers before the Activity's page/theme branches. */
@Composable
internal fun rememberThirdPartyImporter(): ThirdPartyImportUiState {
    val lyricify = rememberLyricifyImporter()
    val lyrimuse = rememberLyrimuseImporter()
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showLyricifyOptions by rememberSaveable { mutableStateOf(false) }
    val hasTask = lyricify.task.showDialog || lyricify.pendingSelection ||
        lyrimuse.task.showDialog || lyrimuse.pendingSelection || lyrimuse.showOptions
    return ThirdPartyImportUiState(
        lyricify, lyrimuse, showSources, showLyricifyOptions,
        openSources = { if (!hasTask && !showLyricifyOptions) showSources = true },
        dismissSources = { showSources = false },
        selectLyrimuse = {
            showSources = false
            if (!hasTask) lyrimuse.chooseFiles()
        },
        selectLyricify = {
            showSources = false
            if (!hasTask) showLyricifyOptions = true
        },
        dismissLyricifyOptions = { showLyricifyOptions = false },
        chooseLyricifyFile = { mode ->
            showLyricifyOptions = false
            if (!hasTask) lyricify.chooseFile(mode)
        },
    )
}

@Composable
private fun rememberLyricifyImporter(): LyricifyImportUiState {
    val context = LocalContext.current
    val activity = checkNotNull(LocalActivity.current) as ComponentActivity
    val model: ThirdPartyImportViewModel = viewModel(viewModelStoreOwner = activity, key = "lyricify-import")
    var pendingMode by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val mode = pendingMode?.let { saved -> LyricifyImportMode.entries.firstOrNull { it.name == saved } }
        pendingMode = null
        if (uri != null && !model.state.value.showDialog) {
            if (mode == null) model.fail(IllegalStateException(context.getString(R.string.lab_lyricify_import_mode_lost)))
            else model.importLyricify(uri, mode)
        }
    }
    return LyricifyImportUiState(rememberImportTaskUiState(model, isLyrimuse = false), pendingMode != null) { mode ->
        if (!model.state.value.showDialog && pendingMode == null) {
            pendingMode = mode.name
            picker.launch(arrayOf("*/*"))
        }
    }
}

@Composable
private fun rememberLyrimuseImporter(): LyrimuseImportUiState {
    val context = LocalContext.current
    val activity = checkNotNull(LocalActivity.current) as ComponentActivity
    val model: ThirdPartyImportViewModel = viewModel(viewModelStoreOwner = activity, key = "lyrimuse-import")
    var includeLyrics by rememberSaveable { mutableStateOf(true) }
    var includeLastFm by rememberSaveable { mutableStateOf(false) }
    var pickingFile by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pickingFile = false
        if (uris.isNotEmpty() && !model.state.value.showDialog) model.prepareLyrimuse(uris)
    }
    val task = rememberImportTaskUiState(model, isLyrimuse = true)
    val prepared = task.state.prepared
    val hasLyrics = prepared?.tracks?.isNotEmpty() == true
    val hasLastFm = prepared?.credentials != null
    return LyrimuseImportUiState(
        task = task,
        pendingSelection = pickingFile,
        showOptions = task.state.status == ThirdPartyImportViewModel.Status.READY,
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
        importPrepared = { mode -> model.importLyrimuse(includeLyrics && hasLyrics, includeLastFm && hasLastFm, mode) },
        selectLyrics = { includeLyrics = it },
        selectLastFm = { includeLastFm = it },
        dismissOptions = model::dismiss,
    )
}

@Composable
private fun rememberImportTaskUiState(model: ThirdPartyImportViewModel, isLyrimuse: Boolean): ImportTaskUiState {
    val context = LocalContext.current
    val state by model.state.collectAsState()
    val stageLabel = when (state.progress.stage) {
        LyricifyImportStage.COPYING -> R.string.lab_lyricify_import_copying
        LyricifyImportStage.VALIDATING -> R.string.lab_lyricify_import_validating
        LyricifyImportStage.PARSING -> R.string.lab_lyricify_import_parsing
        LyricifyImportStage.READING_CACHE -> R.string.lab_lyricify_import_reading_cache
        LyricifyImportStage.MATCHING -> R.string.lab_lyricify_import_matching
        LyricifyImportStage.BACKING_UP -> R.string.lab_lyricify_import_backing_up
        LyricifyImportStage.WRITING -> R.string.lab_lyricify_import_writing
        LyricifyImportStage.COMMITTING -> R.string.lab_lyricify_import_committing
        LyricifyImportStage.CLEANING_UP -> R.string.lab_lyricify_import_cleaning_up
        LyricifyImportStage.ROLLING_BACK -> R.string.lab_lyricify_import_rolling_back
    }
    val message = when (state.status) {
        ThirdPartyImportViewModel.Status.IDLE, ThirdPartyImportViewModel.Status.READY -> null
        ThirdPartyImportViewModel.Status.PREPARING -> context.getString(R.string.lyrimuse_import_reading)
        ThirdPartyImportViewModel.Status.IMPORTING -> context.getString(stageLabel)
        ThirdPartyImportViewModel.Status.CANCELLING -> context.getString(R.string.lab_lyricify_import_cancelling)
        ThirdPartyImportViewModel.Status.FINISHED -> state.result?.let { result ->
            buildList {
                result.lyrics?.let { add(context.getString(R.string.lab_lyricify_import_result, it.imported, it.skipped, it.failed)) }
                result.extraMessages.forEach { add(context.getString(it)) }
            }.joinToString("\n")
        }
        ThirdPartyImportViewModel.Status.FAILED -> state.error?.let { error ->
            val recoveryError = error.suppressed.firstOrNull()
            when {
                isLyrimuse -> context.getString(when {
                    recoveryError != null -> R.string.lyrimuse_import_recovery_failed
                    error is LyrimuseBackupImporter.LimitExceeded -> R.string.lyrimuse_import_limit_exceeded
                    state.progress.stage == LyricifyImportStage.VALIDATING -> R.string.lyrimuse_import_invalid_file
                    else -> R.string.lyrimuse_import_failed
                })
                recoveryError != null -> context.getString(R.string.lab_lyricify_import_rollback_error, recoveryError.message.orEmpty())
                else -> context.getString(R.string.lab_lyricify_import_error, error.message.orEmpty())
            }
        }
    }
    val count = state.progress.total?.takeIf { state.status == ThirdPartyImportViewModel.Status.IMPORTING }?.let { total ->
        val label = when (state.progress.stage) {
            LyricifyImportStage.PARSING, LyricifyImportStage.MATCHING -> R.string.lab_lyricify_import_song_count
            LyricifyImportStage.WRITING -> R.string.lab_lyricify_import_entry_count
            else -> R.string.lab_lyricify_import_file_count
        }
        context.getString(label, state.progress.processed, total)
    }
    return ImportTaskUiState(state, message, count, cancel = model::cancel, dismiss = model::dismiss)
}
