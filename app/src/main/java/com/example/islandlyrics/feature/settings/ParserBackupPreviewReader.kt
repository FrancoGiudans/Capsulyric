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
import com.example.islandlyrics.core.settings.SettingsBackupManager
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
