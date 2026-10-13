/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.feature.lab

import android.app.Application
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.lyrics.importer.LyricifyDatabaseImporter
import com.example.islandlyrics.lyrics.importer.LyricifyImportMode
import com.example.islandlyrics.lyrics.importer.LyricifyImportProgress
import com.example.islandlyrics.lyrics.importer.LyricifyImportStage
import com.example.islandlyrics.feature.lab.material.LabScreen
import com.example.islandlyrics.feature.lab.miuix.MiuixLabScreen
import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

internal data class LyricifyImportUiState(
    val state: LyricifyImportViewModel.State,
    val message: String?,
    val count: String?,
    val pendingSelection: Boolean,
    val chooseFile: (LyricifyImportMode) -> Unit,
    val cancel: () -> Unit,
    val dismiss: () -> Unit
) {
    val importing: Boolean get() = state.importing
    val showDialog: Boolean get() = state.showDialog
    val restoreLab: Boolean get() = showDialog || pendingSelection
}

internal class LyricifyImportViewModel(application: Application) : AndroidViewModel(application) {
    enum class Status { IDLE, IMPORTING, CANCELLING, FINISHED, FAILED }

    data class State(
        val status: Status = Status.IDLE,
        val progress: LyricifyImportProgress = LyricifyImportProgress(LyricifyImportStage.COPYING),
        val report: LyricifyDatabaseImporter.Report? = null,
        val error: Throwable? = null
    ) {
        val importing: Boolean get() = status == Status.IMPORTING || status == Status.CANCELLING
        val showDialog: Boolean get() = status != Status.IDLE
        val canCancel: Boolean get() = status == Status.IMPORTING && progress.canCancel
    }

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private val cancellationRequested = AtomicBoolean(false)

    fun startImport(uri: Uri, mode: LyricifyImportMode) {
        if (state.value.showDialog) return
        cancellationRequested.set(false)
        mutableState.value = State(status = Status.IMPORTING)
        viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    LyricifyDatabaseImporter.importDatabase(
                        getApplication<Application>(), uri, mode,
                        onProgress = { progress -> mutableState.update { it.copy(progress = progress) } },
                        isCancellationRequested = cancellationRequested::get
                    )
                }
                mutableState.value = State(status = Status.FINISHED, report = report)
            } catch (cancelled: CancellationException) {
                if (cancelled.suppressed.isNotEmpty()) {
                    fail(cancelled)
                } else if (cancellationRequested.get()) {
                    mutableState.value = State()
                } else {
                    throw cancelled
                }
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
        mutableState.value = State(status = Status.FAILED, error = error)
    }
}

// Keep the picker at each Activity's composition root so its result key survives page recreation.
@Composable
internal fun rememberLyricifyImporter(): LyricifyImportUiState {
    val context = LocalContext.current
    val activity = checkNotNull(LocalActivity.current) as ComponentActivity
    val model: LyricifyImportViewModel = viewModel(viewModelStoreOwner = activity)
    val state by model.state.collectAsState()
    var pendingImportMode by rememberSaveable { mutableStateOf<String?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val mode = pendingImportMode?.let { saved -> LyricifyImportMode.entries.firstOrNull { it.name == saved } }
        pendingImportMode = null
        if (uri != null && !model.state.value.showDialog && LabFeatureManager.isLyricifyImportEnabled(context)) {
            if (mode == null) {
                model.fail(IllegalStateException(context.getString(R.string.lab_lyricify_import_mode_lost)))
                return@rememberLauncherForActivityResult
            }
            model.startImport(uri, mode)
        }
    }

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
        LyricifyImportViewModel.Status.IDLE -> null
        LyricifyImportViewModel.Status.IMPORTING -> context.getString(stageLabel)
        LyricifyImportViewModel.Status.CANCELLING -> context.getString(R.string.lab_lyricify_import_cancelling)
        LyricifyImportViewModel.Status.FINISHED -> state.report?.let {
            context.getString(R.string.lab_lyricify_import_result, it.imported, it.skipped, it.failed)
        }
        LyricifyImportViewModel.Status.FAILED -> state.error?.let { error ->
            val recoveryError = error.suppressed.firstOrNull()
            if (recoveryError != null) context.getString(R.string.lab_lyricify_import_rollback_error, recoveryError.message.orEmpty())
            else context.getString(R.string.lab_lyricify_import_error, error.message.orEmpty())
        }
    }
    val count = state.progress.total?.takeIf { state.status == LyricifyImportViewModel.Status.IMPORTING }?.let { total ->
        val label = when (state.progress.stage) {
            LyricifyImportStage.PARSING, LyricifyImportStage.MATCHING -> R.string.lab_lyricify_import_song_count
            LyricifyImportStage.WRITING -> R.string.lab_lyricify_import_entry_count
            else -> R.string.lab_lyricify_import_file_count
        }
        context.getString(label, state.progress.processed, total)
    }
    return LyricifyImportUiState(
        state, message, count, pendingImportMode != null,
        chooseFile = { mode ->
            if (!model.state.value.showDialog && pendingImportMode == null && LabFeatureManager.isLyricifyImportEnabled(context)) {
                pendingImportMode = mode.name
                importPicker.launch(arrayOf("*/*"))
            }
        },
        cancel = model::cancel,
        dismiss = model::dismiss
    )
}

class LabActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            val importer = rememberLyricifyImporter()
            if (isMiuixEnabled(this)) {
                MiuixAppTheme {
                    PredictiveBackActivity {
                        MiuixLabScreen(onBack = { finish() }, lyricifyImporter = importer)
                    }
                }
            } else {
                AppTheme {
                    PredictiveBackActivity {
                        LabScreen(onBack = { finish() }, lyricifyImporter = importer)
                    }
                }
            }
        }
    }
}


