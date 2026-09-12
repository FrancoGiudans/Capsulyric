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

package com.example.islandlyrics.feature.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.islandlyrics.core.settings.SettingsBackupManager
import com.example.islandlyrics.core.settings.SettingsBackupManager.ImportProgress
import com.example.islandlyrics.core.settings.SettingsBackupManager.ImportResult
import com.example.islandlyrics.core.settings.SettingsBackupManager.ImportStage
import com.example.islandlyrics.core.settings.SettingsBackupManager.PreviewResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

object ParserBackupPreviewReader {
    fun readBlocking(context: Context, uri: Uri): String {
        return runBlocking { read(context, uri) }
    }

    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val header = ByteArray(4)
            val isZip = context.contentResolver.openInputStream(uri)?.use { input ->
                if (input.read(header) == 4) {
                    header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                        header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
                } else {
                    false
                }
            } ?: false

            if (isZip) {
                readFromZip(context, uri)
            } else {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.readText()
                    ?: return@withContext "[]"
                extractParserJson(text)
            }
        } catch (_: Exception) {
            "[]"
        }
    }

    private fun readFromZip(context: Context, uri: Uri): String {
        val tempDir = File(context.cacheDir, "parser_preview_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "settings.json" && !entry.isDirectory) {
                            val targetFile = File(tempDir, "settings.json")
                            targetFile.outputStream().use { fileOut -> zip.copyTo(fileOut) }
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            val settingsFile = File(tempDir, "settings.json")
            if (settingsFile.exists()) {
                return extractParserJson(settingsFile.readText(Charsets.UTF_8))
            }
            return "[]"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun extractParserJson(text: String): String {
        val root = JSONObject(text)
        val schemaVersion = root.optInt("schema_version", 1)
        val rawValue = if (schemaVersion >= 2 && root.has("categories")) {
            val catObj = root.optJSONObject("categories")
            val parserBlock = catObj?.optJSONObject("parser_rules")
            parserBlock?.optJSONArray("parsers")
                ?: parserBlock?.optJSONObject("preferences")?.opt("parser_rules_json")
        } else {
            val prefsJson = root.optJSONObject("preferences") ?: root
            prefsJson.opt("parser_rules_json")
        }
        return SettingsBackupManager.parserRulesJsonFromBackupValue(rawValue) ?: "[]"
    }
}

/** The two user-visible operations that can report backup progress. */
enum class BackupImportOperation {
    PREVIEW,
    IMPORT,
}

/** State shared by the Material and Miuix backup surfaces. */
data class BackupImportStatus(
    val operation: BackupImportOperation,
    val isZip: Boolean,
    val selectedLeafIds: Set<String> = emptySet(),
    val selectedSensitiveItemIds: Set<String> = emptySet(),
    val progress: ImportProgress,
)

/** Shared progress/lifecycle façade around [SettingsBackupManager]. */
class BackupImportCoordinator(context: Context) {
    private val appContext = context.applicationContext

    var status by mutableStateOf<BackupImportStatus?>(null)
        private set

    val isBusy: Boolean
        get() = status != null

    suspend fun preview(uri: Uri): PreviewResult {
        return runOperation(
            operation = BackupImportOperation.PREVIEW,
            isZip = false,
        ) { onProgress ->
            SettingsBackupManager.previewImportFile(appContext, uri, onProgress)
        }
    }

    suspend fun importSelected(
        uri: Uri,
        preview: PreviewResult,
        selectedLeafIds: Set<String>,
        selectedSensitiveItemIds: Set<String> = emptySet(),
        sensitivePassword: CharArray? = null,
    ): ImportResult {
        return runOperation(
            operation = BackupImportOperation.IMPORT,
            isZip = preview.isZip,
            selectedLeafIds = selectedLeafIds,
            selectedSensitiveItemIds = selectedSensitiveItemIds,
        ) { onProgress ->
            if (preview.isZip) {
                SettingsBackupManager.importFromZip(
                    appContext,
                    uri,
                    selectedLeafIds,
                    selectedSensitiveItemIds,
                    sensitivePassword,
                    onProgress,
                )
            } else {
                SettingsBackupManager.importSelected(
                    appContext,
                    uri,
                    selectedLeafIds,
                    onProgress,
                )
            }
        }
    }

    private suspend fun <T> runOperation(
        operation: BackupImportOperation,
        isZip: Boolean,
        selectedLeafIds: Set<String> = emptySet(),
        selectedSensitiveItemIds: Set<String> = emptySet(),
        block: suspend (suspend (ImportProgress) -> Unit) -> T,
    ): T {
        var archiveDetected = isZip
        publishStatus(
            BackupImportStatus(
                operation = operation,
                isZip = archiveDetected,
                selectedLeafIds = selectedLeafIds,
                selectedSensitiveItemIds = selectedSensitiveItemIds,
                progress = ImportProgress(ImportStage.READING_BACKUP),
            )
        )

        val onProgress: suspend (ImportProgress) -> Unit = { progress ->
            if (progress.stage == ImportStage.EXTRACTING_ARCHIVE) {
                archiveDetected = true
            }
            publishStatus(
                BackupImportStatus(
                    operation = operation,
                    isZip = archiveDetected,
                    selectedLeafIds = selectedLeafIds,
                    selectedSensitiveItemIds = selectedSensitiveItemIds,
                    progress = progress,
                )
            )
        }

        return try {
            block(onProgress)
        } finally {
            publishStatus(null)
        }
    }

    private suspend fun publishStatus(value: BackupImportStatus?) {
        withContext(Dispatchers.Main.immediate) {
            status = value
        }
    }
}

/** Keep the progress timeline in one place for both theme-specific dialogs. */
fun BackupImportStatus.visibleStages(): List<ImportStage> {
    return buildList {
        add(ImportStage.READING_BACKUP)
        if (operation == BackupImportOperation.PREVIEW) {
            if (progress.stage == ImportStage.EXTRACTING_ARCHIVE) {
                add(ImportStage.EXTRACTING_ARCHIVE)
            }
            return@buildList
        }

        if (isZip || progress.stage == ImportStage.EXTRACTING_ARCHIVE) {
            add(ImportStage.EXTRACTING_ARCHIVE)
        }
        add(ImportStage.IMPORTING_SETTINGS)
        if (
            selectedLeafIds.any { it.startsWith("parser_") } ||
                progress.stage == ImportStage.IMPORTING_PARSER_RULES
        ) {
            add(ImportStage.IMPORTING_PARSER_RULES)
        }
        if (
            selectedLeafIds.contains("lyric_cache") ||
                progress.stage == ImportStage.IMPORTING_LYRIC_CACHE
        ) {
            add(ImportStage.IMPORTING_LYRIC_CACHE)
        }
        if (
            selectedSensitiveItemIds.isNotEmpty() ||
                progress.stage == ImportStage.RESTORING_SENSITIVE_DATA
        ) {
            add(ImportStage.RESTORING_SENSITIVE_DATA)
        }
    }
}
