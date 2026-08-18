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

package com.example.islandlyrics.core.settings

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.example.islandlyrics.integration.applemusic.AppleMusicSecureStore
import com.example.islandlyrics.integration.lastfm.LastFmCredentials
import com.example.islandlyrics.integration.lastfm.LastFmSecureStore
import com.example.islandlyrics.lyrics.cache.OnlineLyricCacheStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SettingsBackupManager {

    private const val PREF_PARSER_RULES = AppPreferences.Keys.PARSER_RULES_JSON
    private const val PREF_PARSER_RULE_TEMPLATE = AppPreferences.Keys.PARSER_RULE_TEMPLATE_JSON
    private const val SCHEMA_VERSION = 2
    private const val SENSITIVE_SCHEMA_VERSION = 1
    const val SENSITIVE_CATEGORY_ID = "sensitive_data"
    const val SENSITIVE_LASTFM_ID = "sensitive_lastfm"
    const val SENSITIVE_APPLE_MUSIC_ID = "sensitive_apple_music"
    private val FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HH_mm_ss", Locale.US)
    private val supportedSensitiveItemIds = setOf(SENSITIVE_LASTFM_ID, SENSITIVE_APPLE_MUSIC_ID)

    data class ExportResult(
        val success: Boolean,
        val exportedCount: Int,
        val lyricCacheCount: Int = 0,
        val sensitiveItemCount: Int = 0,
        val error: String? = null
    )

    data class ImportResult(
        val success: Boolean,
        val importedCount: Int,
        val lyricCacheCount: Int = 0,
        val sensitiveItemCount: Int = 0,
        val error: String? = null,
        /** Parser rule conflicts detected (empty = no conflicts or resolved). */
        val parserConflicts: List<ParserConflict> = emptyList()
    )

    data class ParserConflict(
        val packageName: String,
        val displayName: String
    )

    data class PreviewResult(
        val success: Boolean,
        /** categoryId → key count present in the file */
        val categoryCounts: Map<String, Int> = emptyMap(),
        val totalKeys: Int = 0,
        /** Number of lyric cache entries in the backup (-1 = present but not counted, ZIP only). */
        val lyricCacheEntryCount: Int = 0,
        /** Whether the file is a ZIP archive (vs legacy JSON). */
        val isZip: Boolean = false,
        /** Sensitive item IDs declared by an encrypted ZIP entry. */
        val sensitiveItemIds: Set<String> = emptySet(),
        val error: String? = null
    )

    fun buildExportFileName(now: LocalDateTime = LocalDateTime.now()): String {
        return "Capsulyric_${now.format(FILE_TIME_FORMATTER)}.zip"
    }

    fun sensitiveExportCategory(context: Context): BackupCategories.Category? {
        val itemIds = buildSet {
            if (LastFmSecureStore(context).hasBackupData()) add(SENSITIVE_LASTFM_ID)
            if (AppleMusicSecureStore(context).hasMediaUserToken()) add(SENSITIVE_APPLE_MUSIC_ID)
        }
        return sensitiveCategory(itemIds)
    }

    fun sensitiveImportCategory(preview: PreviewResult): BackupCategories.Category? {
        return sensitiveCategory(preview.sensitiveItemIds.intersect(supportedSensitiveItemIds))
    }

    fun selectedSensitiveItemIds(leafIds: Set<String>): Set<String> {
        return leafIds.intersect(supportedSensitiveItemIds)
    }

    private fun sensitiveCategory(itemIds: Set<String>): BackupCategories.Category? {
        if (itemIds.isEmpty()) return null
        return BackupCategories.Category(
            id = SENSITIVE_CATEGORY_ID,
            keyPatterns = emptyList(),
            subGroups = itemIds.sorted().map { itemId ->
                BackupCategories.SubGroup(itemId, emptyList())
            }
        )
    }

    // ── v1 (legacy, full export) ────────────────────────────────────────

    /** Full export as ZIP. Kept for backward compat callers. */
    @Suppress("unused")
    suspend fun exportToUri(context: Context, uri: Uri): ExportResult {
        val allLeafIds = BackupCategories.ALL_CATEGORIES.flatMap { c ->
            if (c.subGroups.isNotEmpty()) c.subGroups.map { it.id } else listOf(c.id)
        }.toSet()
        val includeLyricCache = allLeafIds.contains("lyric_cache")
        return exportToZip(context, uri, allLeafIds, includeLyricCache)
    }

    /** Full import – auto-detects ZIP vs legacy JSON. */
    suspend fun importFromUri(context: Context, uri: Uri): ImportResult {
        val allLeafIds = BackupCategories.ALL_CATEGORIES.flatMap { c ->
            if (c.subGroups.isNotEmpty()) c.subGroups.map { it.id } else listOf(c.id)
        }.toSet()
        return if (isZipFile(context, uri)) {
            importFromZip(context, uri, allLeafIds)
        } else {
            importSelected(context, uri, allLeafIds)
        }
    }

    // ── ZIP export (always ZIP format) ───────────────────────────────

    /**
     * Export settings (and optionally lyric cache) as a ZIP archive.
     * ZIP contains: settings.json + optionally lyric_cache/ and sensitive_data.json.
     */
    suspend fun exportToZip(
        context: Context,
        uri: Uri,
        selectedLeafIds: Set<String>,
        includeLyricCache: Boolean,
        selectedSensitiveItemIds: Set<String> = emptySet(),
        sensitivePassword: CharArray? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            runCatching {
                val sensitiveItemIds = validateSensitiveSelection(selectedSensitiveItemIds)
                val encryptedSensitiveData = if (sensitiveItemIds.isEmpty()) {
                    null
                } else {
                    val password = requireNotNull(sensitivePassword) {
                        "A sensitive backup password is required"
                    }
                    require(password.isNotEmpty()) { "A sensitive backup password is required" }
                    SensitiveBackupVault.encrypt(
                        buildSensitivePayload(context, sensitiveItemIds),
                        sensitiveItemIds,
                        password
                    )
                }

                val tempDir = File(context.cacheDir, "backup_export_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                try {
                    val settingsJson = buildSettingsJson(context, selectedLeafIds)
                    val settingsFile = File(tempDir, "settings.json")
                    settingsFile.writeText(settingsJson.toString(2), Charsets.UTF_8)
                    val settingsCount = countKeysInJson(settingsJson)

                    var cacheCount = 0
                    if (includeLyricCache) {
                        val cacheStore = OnlineLyricCacheStore(context)
                        cacheCount = cacheStore.exportCacheToDir(tempDir)
                    }

                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        ZipOutputStream(output).use { zip ->
                            addFileToZip(zip, settingsFile)
                            encryptedSensitiveData?.let {
                                addTextToZip(zip, SensitiveBackupVault.ZIP_ENTRY_NAME, it)
                            }
                            if (includeLyricCache) {
                                val cacheDir = File(tempDir, "lyric_cache")
                                if (cacheDir.exists()) {
                                    addDirToZip(zip, cacheDir)
                                }
                            }
                            zip.finish()
                            output.flush()
                        }
                    } ?: throw IllegalStateException("openOutputStream returned null")

                    ExportResult(
                        success = true,
                        exportedCount = settingsCount,
                        lyricCacheCount = cacheCount,
                        sensitiveItemCount = sensitiveItemIds.size
                    )
                } finally {
                    tempDir.deleteRecursively()
                }
            }.getOrElse {
                ExportResult(success = false, exportedCount = 0, error = it.message)
            }
        } finally {
            sensitivePassword?.fill('\u0000')
        }
    }

    /**
     * Import from a ZIP archive. Extracts settings.json and optionally lyric_cache/.
     */
    suspend fun importFromZip(
        context: Context,
        uri: Uri,
        selectedLeafIds: Set<String>,
        selectedSensitiveItemIds: Set<String> = emptySet(),
        sensitivePassword: CharArray? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            runCatching {
                val sensitiveItemIds = validateSensitiveSelection(selectedSensitiveItemIds)
                val sensitiveImport = if (sensitiveItemIds.isEmpty()) {
                    null
                } else {
                    val password = requireNotNull(sensitivePassword) {
                        "A sensitive backup password is required"
                    }
                    require(password.isNotEmpty()) { "A sensitive backup password is required" }
                    prepareSensitiveImport(context, uri, sensitiveItemIds, password)
                }

                val tempDir = File(context.cacheDir, "backup_import_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                if (entry.name == SensitiveBackupVault.ZIP_ENTRY_NAME) {
                                    zip.closeEntry()
                                    entry = zip.nextEntry
                                    continue
                                }

                                val targetFile = File(tempDir, entry.name)
                                if (!isSafeZipTarget(tempDir, targetFile)) {
                                    zip.closeEntry()
                                    entry = zip.nextEntry
                                    continue
                                }
                                if (entry.isDirectory) {
                                    targetFile.mkdirs()
                                } else {
                                    targetFile.parentFile?.mkdirs()
                                    targetFile.outputStream().use { fileOut ->
                                        zip.copyTo(fileOut)
                                    }
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                        }
                    } ?: throw IllegalStateException("openInputStream returned null")

                    val settingsFile = File(tempDir, "settings.json")
                    if (!settingsFile.exists()) {
                        ImportResult(
                            success = false,
                            importedCount = 0,
                            error = "settings.json not found in ZIP"
                        )
                    } else {
                        val settingsResult = importSettingsFromJson(
                            context,
                            settingsFile.readText(Charsets.UTF_8),
                            selectedLeafIds
                        )
                        if (!settingsResult.success) {
                            settingsResult
                        } else {
                            val cacheDir = File(tempDir, "lyric_cache")
                            var cacheCount = 0
                            if (cacheDir.exists() && selectedLeafIds.contains("lyric_cache")) {
                                val cacheStore = OnlineLyricCacheStore(context)
                                cacheCount = cacheStore.importCacheFromDir(tempDir)
                            }
                            sensitiveImport?.let { restoreSensitiveImport(context, it) }

                            ImportResult(
                                success = true,
                                importedCount = settingsResult.importedCount,
                                lyricCacheCount = cacheCount,
                                sensitiveItemCount = sensitiveItemIds.size,
                                parserConflicts = settingsResult.parserConflicts
                            )
                        }
                    }
                } finally {
                    tempDir.deleteRecursively()
                }
            }.getOrElse {
                ImportResult(success = false, importedCount = 0, error = it.message)
            }
        } finally {
            sensitivePassword?.fill('\u0000')
        }
    }

    /** Export only the selected leaf items (legacy, kept for internal use). */
    @Suppress("unused")
    suspend fun exportSelected(
        context: Context,
        uri: Uri,
        selectedLeafIds: Set<String>
    ): ExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val root = buildSettingsJson(context, selectedLeafIds)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("openOutputStream returned null")
            ExportResult(success = true, exportedCount = countKeysInJson(root))
        }.getOrElse {
            ExportResult(success = false, exportedCount = 0, error = it.message)
        }
    }

    /** Import only the selected leaf items. Handles both v1 (flat) and v2 (structured) JSON files. */
    suspend fun importSelected(
        context: Context,
        uri: Uri,
        selectedLeafIds: Set<String>
    ): ImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IllegalStateException("openInputStream returned null")

            importSettingsFromJson(context, text, selectedLeafIds)
        }.getOrElse {
            ImportResult(success = false, importedCount = 0, error = it.message)
        }
    }

    /** Read a backup file and return which categories + key counts it contains. Auto-detects ZIP vs JSON. */
    suspend fun previewImportFile(context: Context, uri: Uri): PreviewResult = withContext(Dispatchers.IO) {
        runCatching {
            if (isZipFile(context, uri)) {
                previewZipFile(context, uri)
            } else {
                previewJsonFile(context, uri)
            }
        }.getOrElse {
            PreviewResult(success = false, error = it.message)
        }
    }

    suspend fun verifySensitiveImport(
        context: Context,
        uri: Uri,
        selectedSensitiveItemIds: Set<String>,
        sensitivePassword: CharArray
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val sensitiveItemIds = validateSensitiveSelection(selectedSensitiveItemIds)
            if (sensitiveItemIds.isEmpty() || sensitivePassword.isEmpty()) {
                return@withContext false
            }
            prepareSensitiveImport(context, uri, sensitiveItemIds, sensitivePassword)
            true
        } catch (_: Exception) {
            false
        } finally {
            sensitivePassword.fill('\u0000')
        }
    }

    // ── Settings JSON helpers ────────────────────────────────────

    /** Build the settings.json JSONObject from SharedPreferences filtered by leaf IDs. */
    private fun buildSettingsJson(context: Context, selectedLeafIds: Set<String>): JSONObject {
        val prefs = AppPreferences.of(context)
        val all = prefs.all
        val selectedKeys = BackupCategories.collectKeysForLeafIds(all.keys, selectedLeafIds)

        val categoriesJson = JSONObject()
        val grouped = selectedKeys.groupBy { key ->
            BackupCategories.categoryForKey(key)?.id ?: "other"
        }

        val parserSelected = selectedLeafIds.any {
            it.startsWith("parser_") && it != "parser_all"
        }

        for ((catId, keys) in grouped) {
            val entries = JSONObject()
            val catBlock = JSONObject()
            for (key in keys.sorted()) {
                val value = all[key] ?: continue
                if (key in BackupCategories.EXCLUDED_KEYS) continue

                if (key == PREF_PARSER_RULES && value is String) {
                    val parserJson = if (parserSelected) {
                        BackupCategories.filterParserRulesJson(value, selectedLeafIds)
                    } else {
                        value
                    }
                    val parsers = parserRulesJsonToBackupArray(parserJson)
                    if (parsers.length() > 0) {
                        catBlock.put("parsers", parsers)
                    }
                    continue
                }

                if (key == PREF_PARSER_RULE_TEMPLATE && value is String) {
                    backupTemplateFromInternalJson(value)?.let { template ->
                        catBlock.put("template", template)
                    }
                    continue
                }

                val wrapped = wrapValue(value) ?: continue
                entries.put(key, wrapped)
            }
            if (entries.length() > 0) {
                catBlock.put("preferences", entries)
            }
            if (catBlock.length() > 0) {
                categoriesJson.put(catId, catBlock)
            }
        }

        return JSONObject().apply {
            put("schema_version", SCHEMA_VERSION)
            put("app", "Capsulyric")
            put("exported_at", LocalDateTime.now().toString())
            put("selected_leaf_ids", JSONArray(selectedLeafIds.toList()))
            put("categories", categoriesJson)
        }
    }

    /** Count the number of preference keys in a settings JSON root. */
    private fun countKeysInJson(root: JSONObject): Int {
        val categoriesObj = root.optJSONObject("categories") ?: return 0
        var count = 0
        val catIter = categoriesObj.keys()
        while (catIter.hasNext()) {
            val catId = catIter.next()
            val catBlock = categoriesObj.optJSONObject(catId) ?: continue
            val prefsJson = catBlock.optJSONObject("preferences")
            count += prefsJson?.length() ?: 0
            if (catId == "parser_rules") {
                count += catBlock.optJSONArray("parsers")?.length() ?: 0
                if (catBlock.optJSONObject("template") != null) count += 1
            }
        }
        return count
    }

    private fun validateSensitiveSelection(selectedSensitiveItemIds: Set<String>): Set<String> {
        require(supportedSensitiveItemIds.containsAll(selectedSensitiveItemIds)) {
            "Unsupported sensitive backup item"
        }
        return selectedSensitiveItemIds.toSet()
    }

    private fun buildSensitivePayload(context: Context, itemIds: Set<String>): JSONObject {
        val records = JSONObject()
        if (SENSITIVE_LASTFM_ID in itemIds) {
            val credentials = LastFmSecureStore(context).getCredentials()
            require(credentials.hasApiCredentials) { "No Last.fm credentials are available to export" }

            val lastFmRecord = JSONObject()
                .put("version", 1)
                .put("api_key", credentials.apiKey)
                .put("api_secret", credentials.apiSecret)
            credentials.sessionKey?.takeIf { it.isNotBlank() }?.let {
                lastFmRecord.put("session_key", it)
            }
            credentials.username?.takeIf { it.isNotBlank() }?.let {
                lastFmRecord.put("username", it)
            }
            records.put(SENSITIVE_LASTFM_ID, lastFmRecord)
        }
        if (SENSITIVE_APPLE_MUSIC_ID in itemIds) {
            val mediaUserToken = AppleMusicSecureStore(context).getMediaUserToken()
            require(!mediaUserToken.isNullOrBlank()) { "No Apple Music media-user-token is available to export" }
            records.put(
                SENSITIVE_APPLE_MUSIC_ID,
                JSONObject()
                    .put("version", 1)
                    .put("media_user_token", mediaUserToken)
            )
        }

        return JSONObject()
            .put("schema_version", SENSITIVE_SCHEMA_VERSION)
            .put("records", records)
    }

    private fun prepareSensitiveImport(
        context: Context,
        uri: Uri,
        selectedSensitiveItemIds: Set<String>,
        sensitivePassword: CharArray
    ): SensitiveImportData {
        val serializedVault = readZipEntryText(
            context,
            uri,
            SensitiveBackupVault.ZIP_ENTRY_NAME,
            SensitiveBackupVault.MAX_ENVELOPE_BYTES
        ) ?: throw IllegalArgumentException("Sensitive backup data not found in ZIP")
        val decryptedVault = SensitiveBackupVault.decrypt(serializedVault, sensitivePassword)
        require(decryptedVault.itemIds.containsAll(selectedSensitiveItemIds)) {
            "Selected sensitive backup data is not available"
        }

        val payload = decryptedVault.payload
        require(payload.optInt("schema_version", -1) == SENSITIVE_SCHEMA_VERSION) {
            "Unsupported sensitive backup payload"
        }
        val records = payload.optJSONObject("records") ?: throw IllegalArgumentException(
            "Invalid sensitive backup payload"
        )

        val lastFmCredentials = if (SENSITIVE_LASTFM_ID in selectedSensitiveItemIds) {
            parseLastFmCredentials(records)
        } else {
            null
        }
        val appleMusicMediaUserToken = if (SENSITIVE_APPLE_MUSIC_ID in selectedSensitiveItemIds) {
            parseAppleMusicMediaUserToken(records)
        } else {
            null
        }
        return SensitiveImportData(lastFmCredentials, appleMusicMediaUserToken)
    }

    private fun parseLastFmCredentials(records: JSONObject): LastFmCredentials {
        val record = records.optJSONObject(SENSITIVE_LASTFM_ID)
            ?: throw IllegalArgumentException("Last.fm backup data is missing")
        require(record.optInt("version", -1) == 1) { "Unsupported Last.fm backup data" }

        val apiKey = requiredString(record, "api_key", 4 * 1024)
        val apiSecret = requiredString(record, "api_secret", 4 * 1024)
        val sessionKey = optionalString(record, "session_key", 8 * 1024)
        val username = optionalString(record, "username", 1024)
        require(username == null || sessionKey != null) { "Invalid Last.fm backup data" }
        return LastFmCredentials(apiKey, apiSecret, sessionKey, username)
    }

    private fun parseAppleMusicMediaUserToken(records: JSONObject): String {
        val record = records.optJSONObject(SENSITIVE_APPLE_MUSIC_ID)
            ?: throw IllegalArgumentException("Apple Music backup data is missing")
        require(record.optInt("version", -1) == 1) { "Unsupported Apple Music backup data" }
        return requiredString(record, "media_user_token", 16 * 1024)
    }

    private fun requiredString(record: JSONObject, key: String, maxLength: Int): String {
        return optionalString(record, key, maxLength)
            ?: throw IllegalArgumentException("Invalid sensitive backup data")
    }

    private fun optionalString(record: JSONObject, key: String, maxLength: Int): String? {
        if (!record.has(key) || record.isNull(key)) return null
        val value = record.opt(key) as? String
            ?: throw IllegalArgumentException("Invalid sensitive backup data")
        require(value.isNotBlank() && value.length <= maxLength) { "Invalid sensitive backup data" }
        return value
    }

    private fun restoreSensitiveImport(context: Context, data: SensitiveImportData) {
        data.lastFmCredentials?.let { credentials ->
            LastFmSecureStore(context).restoreFromBackup(credentials)
        }
        data.appleMusicMediaUserToken?.let { token ->
            AppleMusicSecureStore(context).restoreFromBackup(token)
            com.example.islandlyrics.lyrics.online.provider.AppleMusicStateCache.setMediaUserToken(token)
        }
    }

    private data class SensitiveImportData(
        val lastFmCredentials: LastFmCredentials?,
        val appleMusicMediaUserToken: String? = null
    )

    /**
     * Import settings from raw JSON text (shared by legacy JSON path and ZIP path).
     * Handles v1 (flat) and v2 (structured) formats.
     */
    private fun importSettingsFromJson(
        context: Context,
        text: String,
        selectedLeafIds: Set<String>
    ): ImportResult {
        val root = JSONObject(text)
        val editor = AppPreferences.of(context).edit()
        val schemaVersion = root.optInt("schema_version", 1)

        var count = 0
        var importedParserJson: String? = null

        if (schemaVersion >= 2 && root.has("categories")) {
            val allowedPatterns = BackupCategories.patternsForLeafIds(selectedLeafIds)
            val categoriesObj = root.optJSONObject("categories")
            if (categoriesObj != null) {
                val catIter = categoriesObj.keys()
                while (catIter.hasNext()) {
                    val catId = catIter.next()
                    val catBlock = categoriesObj.optJSONObject(catId) ?: continue
                    val hasStructuredParsers = catBlock.optJSONArray("parsers") != null
                    val parserRulesSelected = catId == "parser_rules" &&
                        selectedLeafIds.any { it.startsWith("parser_") }
                    if (parserRulesSelected) {
                        val template = catBlock.optJSONObject("template")
                        if (template != null) {
                            editor.putString(PREF_PARSER_RULE_TEMPLATE, backupTemplateToInternalJson(template))
                            count += 1
                        }
                    }
                    if (parserRulesSelected) {
                        val parsers = catBlock.optJSONArray("parsers")
                        if (parsers != null) {
                            importedParserJson = BackupCategories.filterParserRulesJson(
                                parserRulesJsonFromBackupValue(parsers) ?: "[]",
                                selectedLeafIds
                            )
                            count += countParserRules(importedParserJson)
                        }
                    }
                    val prefsJson = catBlock.optJSONObject("preferences") ?: continue
                    val keyIter = prefsJson.keys()
                    while (keyIter.hasNext()) {
                        val key = keyIter.next()
                        if (key in BackupCategories.EXCLUDED_KEYS) continue
                        if (!allowedPatterns.any { BackupCategories.matchesPattern(key, it) }) continue
                        if (key == PREF_PARSER_RULES && hasStructuredParsers) continue
                        if (key == PREF_PARSER_RULE_TEMPLATE &&
                            parserRulesSelected &&
                            catBlock.optJSONObject("template") != null
                        ) continue
                        if (key == PREF_PARSER_RULES &&
                            parserRulesSelected
                        ) {
                            importedParserJson = BackupCategories.filterParserRulesJson(
                                parserRulesJsonFromBackupValue(prefsJson.opt(key)) ?: "[]",
                                selectedLeafIds
                            )
                            count += 1
                            continue
                        }
                        val value = prefsJson.opt(key)
                        if (applyValue(editor, key, value)) count += 1
                    }
                }
            }
        } else {
            val allowedPatterns = BackupCategories.patternsForLeafIds(selectedLeafIds)
            val prefsJson = root.optJSONObject("preferences") ?: root
            val iterator = prefsJson.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (key in BackupCategories.EXCLUDED_KEYS) continue
                if (allowedPatterns.isNotEmpty() &&
                    !allowedPatterns.any { BackupCategories.matchesPattern(key, it) }) continue
                if (key == PREF_PARSER_RULES && selectedLeafIds.any { it.startsWith("parser_") }) {
                    importedParserJson = BackupCategories.filterParserRulesJson(
                        parserRulesJsonFromBackupValue(prefsJson.opt(key)) ?: "[]",
                        selectedLeafIds
                    )
                    count += 1
                    continue
                }
                val value = prefsJson.opt(key)
                if (applyValue(editor, key, value)) count += 1
            }
        }

        editor.apply()

        val conflicts = if (!importedParserJson.isNullOrEmpty()) {
            val existingConflicts = checkParserConflicts(context, importedParserJson)
            if (existingConflicts.isEmpty()) {
                BackupCategories.mergeParserRulesJson(context, importedParserJson)
            }
            existingConflicts
        } else {
            emptyList()
        }

        return ImportResult(success = true, importedCount = count, parserConflicts = conflicts)
    }

    // ── Format detection ─────────────────────────────────────────

    /** Check whether a URI points to a ZIP file (magic bytes PK\x03\x04). */
    private fun isZipFile(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(4)
                if (input.read(header) == 4) {
                    header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                        header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
                } else false
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    // ── ZIP preview ──────────────────────────────────────────────

    /** Preview a ZIP backup: extract settings.json to get category counts. */
    private fun previewZipFile(context: Context, uri: Uri): PreviewResult {
        val tempDir = File(context.cacheDir, "backup_preview_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            var hasLyricCache = false
            val sensitiveItemIds = runCatching {
                readZipEntryText(
                    context,
                    uri,
                    SensitiveBackupVault.ZIP_ENTRY_NAME,
                    SensitiveBackupVault.MAX_ENVELOPE_BYTES
                )?.let { SensitiveBackupVault.readItemIds(it) } ?: emptySet()
            }.getOrDefault(emptySet())
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == SensitiveBackupVault.ZIP_ENTRY_NAME) {
                            zip.closeEntry()
                            entry = zip.nextEntry
                            continue
                        }
                        val targetFile = File(tempDir, entry.name)
                        if (!isSafeZipTarget(tempDir, targetFile)) {
                            zip.closeEntry()
                            entry = zip.nextEntry
                            continue
                        }
                        if (entry.name == "settings.json" && !entry.isDirectory) {
                            targetFile.parentFile?.mkdirs()
                            targetFile.outputStream().use { fileOut -> zip.copyTo(fileOut) }
                        }
                        if (entry.name.startsWith("lyric_cache/") && !hasLyricCache) {
                            hasLyricCache = true
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: throw IllegalStateException("openInputStream returned null")

            val settingsFile = File(tempDir, "settings.json")
            if (!settingsFile.exists()) {
                return PreviewResult(success = false, error = "settings.json not found in ZIP")
            }

            val jsonPreview = previewJsonFromText(settingsFile.readText(Charsets.UTF_8))
            return jsonPreview.copy(
                lyricCacheEntryCount = if (hasLyricCache) -1 else 0,
                isZip = true,
                sensitiveItemIds = sensitiveItemIds
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** Preview a legacy JSON backup. */
    private fun previewJsonFile(context: Context, uri: Uri): PreviewResult {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw IllegalStateException("openInputStream returned null")
        return previewJsonFromText(text).copy(isZip = false)
    }

    /** Parse JSON text and extract category counts. */
    private fun previewJsonFromText(text: String): PreviewResult {
        val root = JSONObject(text)
        val schemaVersion = root.optInt("schema_version", 1)

        if (schemaVersion >= 2 && root.has("categories")) {
            val categoriesObj = root.optJSONObject("categories")
            if (categoriesObj != null) {
                val counts = mutableMapOf<String, Int>()
                var total = 0
                val catIter = categoriesObj.keys()
                while (catIter.hasNext()) {
                    val catId = catIter.next()
                    val catBlock = categoriesObj.optJSONObject(catId) ?: continue
                    val prefsJson = catBlock.optJSONObject("preferences")
                    var n = prefsJson?.length() ?: 0
                    if (catId == "parser_rules") {
                        n += catBlock.optJSONArray("parsers")?.length() ?: 0
                        if (catBlock.optJSONObject("template") != null) n += 1
                    }
                    counts[catId] = n
                    total += n
                }
                return PreviewResult(success = true, categoryCounts = counts, totalKeys = total)
            }
            return PreviewResult(success = false, error = "Missing categories in v2 file")
        }
        // v1: detect categories from flat preferences
        val prefsJson = root.optJSONObject("preferences") ?: root
        val counts = BackupCategories.detectCategoriesFromJson(prefsJson)
        var total = 0
        counts.values.forEach { total += it }
        return PreviewResult(success = true, categoryCounts = counts, totalKeys = total)
    }

    // ── ZIP I/O helpers ──────────────────────────────────────────

    /** Add a single file to a ZIP output stream. */
    private fun addFileToZip(zip: ZipOutputStream, file: File) {
        zip.putNextEntry(ZipEntry("settings.json"))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun addTextToZip(zip: ZipOutputStream, entryName: String, text: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    /** Recursively add lyric cache files to a ZIP output stream. */
    private fun addDirToZip(zip: ZipOutputStream, dir: File) {
        dir.walkTopDown().forEach { file ->
            val relativePath = file.relativeTo(dir).path.replace(File.separatorChar, '/')
            if (file.isDirectory) {
                val dirEntry = if (relativePath.isEmpty()) "lyric_cache/" else "lyric_cache/$relativePath/"
                zip.putNextEntry(ZipEntry(dirEntry))
                zip.closeEntry()
            } else {
                zip.putNextEntry(ZipEntry("lyric_cache/$relativePath"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun readZipEntryText(
        context: Context,
        uri: Uri,
        entryName: String,
        maxBytes: Int
    ): String? {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == entryName) {
                        val bytes = readBoundedZipEntry(zip, maxBytes)
                        zip.closeEntry()
                        return String(bytes, Charsets.UTF_8)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("openInputStream returned null")
        return null
    }

    private fun readBoundedZipEntry(zip: ZipInputStream, maxBytes: Int): ByteArray {
        require(maxBytes > 0)
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0
        while (true) {
            val count = zip.read(buffer)
            if (count <= 0) break
            totalBytes += count
            require(totalBytes <= maxBytes) { "ZIP entry is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun isSafeZipTarget(tempDir: File, targetFile: File): Boolean {
        val rootPath = tempDir.canonicalFile.path
        val targetPath = targetFile.canonicalFile.path
        return targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
    }

    private fun wrapValue(value: Any?): JSONObject? {
        return when (value) {
            is Boolean -> JSONObject().put("type", "boolean").put("value", value)
            is Int -> JSONObject().put("type", "int").put("value", value)
            is Long -> JSONObject().put("type", "long").put("value", value)
            is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
            is String -> JSONObject().put("type", "string").put("value", value)
            is Set<*> -> {
                val array = JSONArray()
                value.forEach { item ->
                    if (item is String) array.put(item)
                }
                JSONObject().put("type", "string_set").put("value", array)
            }
            else -> null
        }
    }

    private fun applyValue(
        editor: android.content.SharedPreferences.Editor,
        key: String,
        rawValue: Any?
    ): Boolean {
        if (rawValue is JSONObject && rawValue.has("type")) {
            val type = rawValue.optString("type")
            val value = rawValue.opt("value")
            return when (type) {
                "boolean" -> {
                    editor.putBoolean(key, when (value) {
                        is Boolean -> value
                        null -> false
                        else -> value.toString().toBoolean()
                    })
                    true
                }
                "int" -> {
                    val parsed = when (value) {
                        is Number -> value.toInt()
                        null -> null
                        else -> value.toString().toIntOrNull()
                    } ?: return false
                    editor.putInt(key, parsed)
                    true
                }
                "long" -> {
                    val parsed = when (value) {
                        is Number -> value.toLong()
                        null -> null
                        else -> value.toString().toLongOrNull()
                    } ?: return false
                    editor.putLong(key, parsed)
                    true
                }
                "float" -> {
                    val parsed = when (value) {
                        is Number -> value.toFloat()
                        null -> null
                        else -> value.toString().toFloatOrNull()
                    } ?: return false
                    editor.putFloat(key, parsed)
                    true
                }
                "string" -> {
                    editor.putString(key, if (value == JSONObject.NULL) null else value?.toString())
                    true
                }
                "string_set" -> {
                    val arr = value as? JSONArray ?: return false
                    val set = linkedSetOf<String>()
                    for (i in 0 until arr.length()) {
                        set.add(arr.optString(i, ""))
                    }
                    editor.putStringSet(key, set)
                    true
                }
                else -> false
            }
        }

        return when (rawValue) {
            is Boolean -> {
                editor.putBoolean(key, rawValue)
                true
            }
            is Int -> {
                editor.putInt(key, rawValue)
                true
            }
            is Long -> {
                editor.putLong(key, rawValue)
                true
            }
            is Float -> {
                editor.putFloat(key, rawValue)
                true
            }
            is String -> {
                editor.putString(key, rawValue)
                true
            }
            else -> false
        }
    }

    // ── Parser rule conflict detection & resolution ────────────────────

    private fun parserRulesJsonToBackupArray(json: String): JSONArray {
        val source = runCatching { JSONArray(json) }.getOrNull() ?: return JSONArray()
        val result = JSONArray()
        for (i in 0 until source.length()) {
            val rule = source.optJSONObject(i) ?: continue
            val pkg = rule.optString("pkg", rule.optString("package", ""))
            if (pkg.isBlank()) continue

            val backupRule = JSONObject()
                .put("package", pkg)
                .put("name", optDisplayName(rule, pkg))
                .put("enabled", rule.optBoolean("enabled", true))
                .put("car_protocol", rule.optBoolean("usesCarProtocol", rule.optBoolean("car_protocol", true)))
                .put("separator", rule.optString("separator", "-"))
                .put("field_order", rule.optString("fieldOrder", rule.optString("field_order", "ARTIST_TITLE")))
                .put("sources", JSONObject()
                    .put("local_lyrics", rule.optBoolean("useLocalLyrics", false))
                    .put("online_lyrics", rule.optBoolean("useOnlineLyrics", false))
                    .put("smart_selection", rule.optBoolean("useSmartOnlineLyricSelection", true))
                    .put("use_raw_metadata", rule.optBoolean("useRawMetadataForOnlineMatching", false))
                    .put("lastfm_scrobble", rule.optBoolean("useLastFmScrobble", false)))
                .put("online_providers", rule.optJSONArray("onlineLyricProviderOrder") ?: JSONArray())
                .put("online_disabled_providers", rule.optJSONArray("onlineLyricDisabledProviders") ?: JSONArray())
                .put("apis", JSONObject()
                    .put("super_lyric", rule.optBoolean("useSuperLyricApi", false))
                    .put("lyric_getter", rule.optBoolean("useLyricGetterApi", false))
                    .put("lyricon", rule.optBoolean("useLyriconApi", false)))
                .put("translations", JSONObject()
                    .put("online_translation", rule.optBoolean("receiveOnlineTranslation", false))
                    .put("online_romanization", rule.optBoolean("receiveOnlineRomanization", false))
                    .put("lyricon_translation", rule.optBoolean("receiveLyriconTranslation", false))
                    .put("lyricon_romanization", rule.optBoolean("receiveLyriconRomanization", false)))
            result.put(backupRule)
        }
        return result
    }

    private fun backupTemplateFromInternalJson(json: String): JSONObject? {
        val rule = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return JSONObject()
            .put("car_protocol", rule.optBoolean("usesCarProtocol", true))
            .put("separator", rule.optString("separator", "-"))
            .put("field_order", rule.optString("fieldOrder", "ARTIST_TITLE"))
            .put("sources", JSONObject()
                .put("local_lyrics", rule.optBoolean("useLocalLyrics", false))
                .put("online_lyrics", rule.optBoolean("useOnlineLyrics", false))
                .put("smart_selection", rule.optBoolean("useSmartOnlineLyricSelection", true))
                .put("use_raw_metadata", rule.optBoolean("useRawMetadataForOnlineMatching", false))
                .put("lastfm_scrobble", rule.optBoolean("useLastFmScrobble", false)))
            .put("online_providers", rule.optJSONArray("onlineLyricProviderOrder") ?: JSONArray())
            .put("online_disabled_providers", rule.optJSONArray("onlineLyricDisabledProviders") ?: JSONArray())
            .put("apis", JSONObject()
                .put("super_lyric", rule.optBoolean("useSuperLyricApi", false))
                .put("lyric_getter", rule.optBoolean("useLyricGetterApi", false))
                .put("lyricon", rule.optBoolean("useLyriconApi", false)))
            .put("translations", JSONObject()
                .put("online_translation", rule.optBoolean("receiveOnlineTranslation", false))
                .put("online_romanization", rule.optBoolean("receiveOnlineRomanization", false))
                .put("lyricon_translation", rule.optBoolean("receiveLyriconTranslation", false))
                .put("lyricon_romanization", rule.optBoolean("receiveLyriconRomanization", false)))
    }

    private fun backupTemplateToInternalJson(template: JSONObject): String {
        val sources = template.optJSONObject("sources") ?: JSONObject()
        val apis = template.optJSONObject("apis") ?: JSONObject()
        val translations = template.optJSONObject("translations") ?: JSONObject()
        val providers = template.optJSONArray("online_providers")
            ?: template.optJSONArray("onlineLyricProviderOrder")
            ?: JSONArray()
        val disabledProviders = template.optJSONArray("online_disabled_providers")
            ?: template.optJSONArray("onlineLyricDisabledProviders")
            ?: JSONArray()

        return JSONObject()
            .put("pkg", "__parser_rule_template__")
            .put("name", JSONObject.NULL)
            .put("enabled", true)
            .put("usesCarProtocol", template.optBoolean("car_protocol", template.optBoolean("usesCarProtocol", true)))
            .put("separator", template.optString("separator", "-"))
            .put("fieldOrder", template.optString("field_order", template.optString("fieldOrder", "ARTIST_TITLE")))
            .put("useOnlineLyrics", sources.optBoolean("online_lyrics", template.optBoolean("useOnlineLyrics", false)))
            .put("useSmartOnlineLyricSelection", sources.optBoolean("smart_selection", template.optBoolean("useSmartOnlineLyricSelection", true)))
            .put("useRawMetadataForOnlineMatching", sources.optBoolean("use_raw_metadata", template.optBoolean("useRawMetadataForOnlineMatching", false)))
            .put("useLastFmScrobble", sources.optBoolean("lastfm_scrobble", template.optBoolean("useLastFmScrobble", false)))
            .put("receiveOnlineTranslation", translations.optBoolean("online_translation", template.optBoolean("receiveOnlineTranslation", false)))
            .put("receiveOnlineRomanization", translations.optBoolean("online_romanization", template.optBoolean("receiveOnlineRomanization", false)))
            .put("onlineLyricProviderOrder", providers)
            .put("onlineLyricDisabledProviders", disabledProviders)
            .put("useSuperLyricApi", apis.optBoolean("super_lyric", template.optBoolean("useSuperLyricApi", false)))
            .put("useLyricGetterApi", apis.optBoolean("lyric_getter", template.optBoolean("useLyricGetterApi", false)))
            .put("useLyriconApi", apis.optBoolean("lyricon", template.optBoolean("useLyriconApi", false)))
            .put("receiveLyriconTranslation", translations.optBoolean("lyricon_translation", template.optBoolean("receiveLyriconTranslation", false)))
            .put("receiveLyriconRomanization", translations.optBoolean("lyricon_romanization", template.optBoolean("receiveLyriconRomanization", false)))
            .put("useLocalLyrics", sources.optBoolean("local_lyrics", template.optBoolean("useLocalLyrics", false)))
            .toString()
    }

    internal fun parserRulesJsonFromBackupValue(rawValue: Any?): String? {
        return when (rawValue) {
            is JSONArray -> backupParserArrayToParserRulesJson(rawValue)
            is JSONObject -> {
                if (rawValue.has("type")) {
                    unwrapStringValue(rawValue)
                } else {
                    backupParserArrayToParserRulesJson(JSONArray().put(rawValue))
                }
            }
            is String -> rawValue.takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    private fun backupParserArrayToParserRulesJson(array: JSONArray): String {
        val result = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val pkg = obj.optString("package", obj.optString("pkg", ""))
            if (pkg.isBlank()) continue

            val sources = obj.optJSONObject("sources") ?: JSONObject()
            val apis = obj.optJSONObject("apis") ?: JSONObject()
            val translations = obj.optJSONObject("translations") ?: JSONObject()
            val providers = obj.optJSONArray("online_providers")
                ?: obj.optJSONArray("onlineLyricProviderOrder")
                ?: JSONArray()
            val disabledProviders = obj.optJSONArray("online_disabled_providers")
                ?: obj.optJSONArray("onlineLyricDisabledProviders")
                ?: JSONArray()

            result.put(JSONObject()
                .put("pkg", pkg)
                .put("name", optDisplayName(obj, pkg))
                .put("enabled", obj.optBoolean("enabled", true))
                .put("usesCarProtocol", obj.optBoolean("car_protocol", obj.optBoolean("usesCarProtocol", true)))
                .put("separator", obj.optString("separator", "-"))
                .put("fieldOrder", obj.optString("field_order", obj.optString("fieldOrder", "ARTIST_TITLE")))
                .put("useOnlineLyrics", sources.optBoolean("online_lyrics", obj.optBoolean("useOnlineLyrics", false)))
                .put("useSmartOnlineLyricSelection", sources.optBoolean("smart_selection", obj.optBoolean("useSmartOnlineLyricSelection", true)))
                .put("useRawMetadataForOnlineMatching", sources.optBoolean("use_raw_metadata", obj.optBoolean("useRawMetadataForOnlineMatching", false)))
                .put("useLastFmScrobble", sources.optBoolean("lastfm_scrobble", obj.optBoolean("useLastFmScrobble", false)))
                .put("receiveOnlineTranslation", translations.optBoolean("online_translation", obj.optBoolean("receiveOnlineTranslation", false)))
                .put("receiveOnlineRomanization", translations.optBoolean("online_romanization", obj.optBoolean("receiveOnlineRomanization", false)))
                .put("onlineLyricProviderOrder", providers)
                .put("onlineLyricDisabledProviders", disabledProviders)
                .put("useSuperLyricApi", apis.optBoolean("super_lyric", obj.optBoolean("useSuperLyricApi", false)))
                .put("useLyricGetterApi", apis.optBoolean("lyric_getter", obj.optBoolean("useLyricGetterApi", false)))
                .put("useLyriconApi", apis.optBoolean("lyricon", obj.optBoolean("useLyriconApi", false)))
                .put("receiveLyriconTranslation", translations.optBoolean("lyricon_translation", obj.optBoolean("receiveLyriconTranslation", false)))
                .put("receiveLyriconRomanization", translations.optBoolean("lyricon_romanization", obj.optBoolean("receiveLyriconRomanization", false)))
                .put("useLocalLyrics", sources.optBoolean("local_lyrics", obj.optBoolean("useLocalLyrics", false))))
        }
        return result.toString()
    }

    private fun countParserRules(json: String?): Int {
        if (json.isNullOrEmpty()) return 0
        return runCatching { JSONArray(json).length() }.getOrDefault(0)
    }

    private fun optDisplayName(obj: JSONObject, fallback: String): String {
        return obj.optString("name", fallback)
            .takeUnless { it.isBlank() || it == "null" }
            ?: fallback
    }

    /**
     * Unwrap a type-wrapped value (e.g. {"type":"string","value":"..."}) to its raw string.
     */
    internal fun unwrapStringValue(rawValue: Any?): String? {
        if (rawValue is JSONObject && rawValue.has("type")) {
            val type = rawValue.optString("type")
            val value = rawValue.opt("value")
            return when (type) {
                "string" -> if (value == JSONObject.NULL) null else value?.toString()
                else -> null
            }
        }
        return when (rawValue) {
            is String -> rawValue
            else -> null
        }?.takeIf { it.isNotEmpty() }
    }

    /**
     * Check if the imported parser_rules_json contains apps that already exist.
     * Returns list of conflicting package names with display names.
     */
    private fun checkParserConflicts(context: Context, importedJson: String): List<ParserConflict> {
        val prefs = AppPreferences.of(context)
        val existingJson = prefs.getString(PREF_PARSER_RULES, null) ?: return emptyList()
        return try {
            val existing = JSONArray(existingJson)
            val imported = JSONArray(importedJson)
            val existingPkgs = (0 until existing.length()).mapNotNull {
                existing.getJSONObject(it).optString("pkg", "").takeIf { pkg -> pkg.isNotEmpty() }
            }.toSet()
            val conflicts = mutableListOf<ParserConflict>()
            for (i in 0 until imported.length()) {
                val obj = imported.getJSONObject(i)
                val pkg = obj.optString("pkg", "")
                if (pkg.isNotEmpty() && pkg in existingPkgs) {
                    val name = if (obj.has("name") && !obj.isNull("name")) obj.getString("name") else pkg
                    conflicts.add(ParserConflict(packageName = pkg, displayName = name))
                }
            }
            conflicts
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Resolve parser rule conflicts after user choice.
     * @param keepExistingPkgs set of package names to keep existing (rest are replaced with imported).
     */
    fun resolveParserConflicts(
        context: Context,
        importedJson: String,
        keepExistingPkgs: Set<String>
    ) {
        val prefs = AppPreferences.of(context)
        val existingJson = prefs.getString(PREF_PARSER_RULES, "[]") ?: "[]"
        try {
            val existing = JSONArray(existingJson)
            val imported = JSONArray(importedJson)
            val byPkg = mutableMapOf<String, Int>()
            for (i in 0 until existing.length()) {
                byPkg[existing.getJSONObject(i).getString("pkg")] = i
            }
            for (i in 0 until imported.length()) {
                val obj = imported.getJSONObject(i)
                val pkg = obj.getString("pkg")
                if (pkg in keepExistingPkgs) continue
                val idx = byPkg[pkg]
                if (idx != null) {
                    existing.put(idx, obj)
                } else {
                    existing.put(obj)
                }
            }
            prefs.edit { putString(PREF_PARSER_RULES, existing.toString()) }
        } catch (_: Exception) { }
    }
}
