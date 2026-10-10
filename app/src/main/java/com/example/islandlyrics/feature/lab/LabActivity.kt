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

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.lyrics.importer.LyricifyDatabaseImporter
import com.example.islandlyrics.lyrics.importer.LyricifyImportMode
import com.example.islandlyrics.feature.lab.material.LabScreen
import com.example.islandlyrics.feature.lab.miuix.MiuixLabScreen
import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class LyricifyImportUiState(
    val importing: Boolean, val message: String?, val chooseFile: (LyricifyImportMode) -> Unit
)

// Lab is also embedded in MainActivity and SettingsActivity; all hosts share this picker.
@Composable
internal fun rememberLyricifyImporter(): LyricifyImportUiState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importMode by remember { mutableStateOf(LyricifyImportMode.MERGE) }
    var importing by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importing && LabFeatureManager.isLyricifyImportEnabled(context)) {
            importing = true
            importMessage = null
            scope.launch {
                try {
                    val report = withContext(Dispatchers.IO) {
                        LyricifyDatabaseImporter.importDatabase(context, uri, importMode)
                    }
                    importMessage = context.getString(R.string.lab_lyricify_import_result, report.imported, report.skipped, report.failed)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    importMessage = context.getString(R.string.lab_lyricify_import_error, error.message.orEmpty())
                } finally {
                    importing = false
                }
            }
        }
    }

    return LyricifyImportUiState(importing, importMessage) { mode ->
        if (!importing && LabFeatureManager.isLyricifyImportEnabled(context)) {
            importMode = mode
            importPicker.launch(arrayOf("*/*"))
        }
    }
}

class LabActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            if (isMiuixEnabled(this)) {
                MiuixAppTheme {
                    PredictiveBackActivity {
                        MiuixLabScreen(onBack = { finish() })
                    }
                }
            } else {
                AppTheme {
                    PredictiveBackActivity {
                        LabScreen(onBack = { finish() })
                    }
                }
            }
        }
    }
}


