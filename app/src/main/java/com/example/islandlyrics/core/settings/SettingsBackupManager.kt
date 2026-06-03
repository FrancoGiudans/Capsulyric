package com.example.islandlyrics.core.settings

import android.content.Context
import android.net.Uri
import com.example.islandlyrics.data.lyric.OnlineLyricCacheStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SettingsBackupManager {

    private const val PREFS_NAME = "IslandLyricsPrefs"
    private const val SCHEMA_VERSION = 2
    private val FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HH_mm_ss", Locale.US)

    data class ExportResult(
        val success: Boolean,
        val exportedCount: Int,
        val lyricCacheCount: Int = 0,
        val error: String? = null
    )

    data class ImportResult(
        val success: Boolean,
        val importedCount: Int,
        val lyricCacheCount: Int = 0,
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
        val error: String? = null
    )

    fun buildExportFileName(now: LocalDateTime = LocalDateTime.now()): String {
        return "Capsulyric_${now.format(FILE_TIME_FORMATTER)}.zip"
    }

    // ── v1 (legacy, full export) ────────────────────────────────────────

    /** Full export as ZIP. Kept for backward compat callers. */
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
     * ZIP contains: settings.json + optionally lyric_cache/
     */
    suspend fun exportToZip(
        context: Context,
        uri: Uri,
        selectedLeafIds: Set<String>,
        includeLyricCache: Boolean
    ): ExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val tempDir = File(context.cacheDir, "backup_export_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            try {
                // 1. Write settings.json
                val settingsJson = buildSettingsJson(context, selectedLeafIds)
                val settingsFile = File(tempDir, "settings.json")
                settingsFile.writeText(settingsJson.toString(2), Charsets.UTF_8)
                val settingsCount = countKeysInJson(settingsJson)

                // 2. Export lyric cache if requested
                var cacheCount = 0
                if (includeLyricCache) {
                    val cacheStore = OnlineLyricCacheStore(context)
                    cacheCount = cacheStore.exportCacheToDir(tempDir)
                }

                // 3. Create ZIP (explicit finish+flush for ContentResolver streams)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    ZipOutputStream(output).use { zip ->
                        addFileToZip(zip, settingsFile, "settings.json")
                        if (includeLyricCache) {
                            val cacheDir = File(tempDir, "lyric_cache")
                            if (cacheDir.exists()) {
                                addDirToZip(zip, cacheDir, "lyric_cache")
                            }
                        }
                        zip.finish()
                        output.flush()
                    }
                } ?: throw IllegalStateException("openOutputStream returned null")

                ExportResult(success = true, exportedCount = settingsCount, lyricCacheCount = cacheCount)
            } finally {
                tempDir.deleteRecursively()
            }
        }.getOrElse {
            ExportResult(success = false, exportedCount = 0, error = it.message)
        }
    }

    /**
     * Import from a ZIP archive. Extracts settings.json and optionally lyric_cache/.
     */
    suspend fun importFromZip(
        context: Context,
        uri: Uri,
        selectedLeafIds: Set<String>
    ): ImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val tempDir = File(context.cacheDir, "backup_import_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            try {
                // 1. Extract ZIP
                context.contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val targetFile = File(tempDir, entry.name)
                            // Prevent zip-slip attacks
                            if (!targetFile.canonicalPath.startsWith(tempDir.canonicalPath)) {
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

                // 2. Import settings from settings.json
                val settingsFile = File(tempDir, "settings.json")
                if (!settingsFile.exists()) {
                    return@withContext ImportResult(
                        success = false, importedCount = 0,
                        error = "settings.json not found in ZIP"
                    )
                }

                val settingsResult = importSettingsFromJson(
                    context, settingsFile.readText(Charsets.UTF_8), selectedLeafIds
                )

                // 3. Import lyric cache if present
                val cacheDir = File(tempDir, "lyric_cache")
                var cacheCount = 0
                if (cacheDir.exists() && selectedLeafIds.contains("lyric_cache")) {
                    val cacheStore = OnlineLyricCacheStore(context)
                    cacheCount = cacheStore.importCacheFromDir(tempDir)
                }

                ImportResult(
                    success = settingsResult.success,
                    importedCount = settingsResult.importedCount,
                    lyricCacheCount = cacheCount,
                    parserConflicts = settingsResult.parserConflicts
                )
            } finally {
                tempDir.deleteRecursively()
            }
        }.getOrElse {
            ImportResult(success = false, importedCount = 0, error = it.message)
        }
    }

    /** Export only the selected leaf items (legacy, kept for internal use). */
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

    // ── Settings JSON helpers ────────────────────────────────────

    /** Build the settings.json JSONObject from SharedPreferences filtered by leaf IDs. */
    private fun buildSettingsJson(context: Context, selectedLeafIds: Set<String>): JSONObject {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
            for (key in keys.sorted()) {
                val value = all[key] ?: continue
                if (key in BackupCategories.EXCLUDED_KEYS) continue

                val exportValue = if (key == "parser_rules_json" && parserSelected && value is String) {
                    BackupCategories.filterParserRulesJson(value, selectedLeafIds)
                } else {
                    value
                }

                val wrapped = wrapValue(exportValue) ?: continue
                entries.put(key, wrapped)
            }
            if (entries.length() > 0) {
                categoriesJson.put(catId, JSONObject().apply {
                    put("preferences", entries)
                })
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
            val prefsJson = catBlock.optJSONObject("preferences") ?: continue
            count += prefsJson.length()
        }
        return count
    }

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
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
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
                    val prefsJson = catBlock.optJSONObject("preferences") ?: continue
                    val keyIter = prefsJson.keys()
                    while (keyIter.hasNext()) {
                        val key = keyIter.next()
                        if (key in BackupCategories.EXCLUDED_KEYS) continue
                        if (!allowedPatterns.any { BackupCategories.matchesPattern(key, it) }) continue
                        if (key == "parser_rules_json" && selectedLeafIds.any { it.startsWith("parser_") }) {
                            importedParserJson = unwrapStringValue(prefsJson.opt(key))
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
                if (key == "parser_rules_json" && selectedLeafIds.any { it.startsWith("parser_") }) {
                    importedParserJson = unwrapStringValue(prefsJson.opt(key))
                    count += 1
                    continue
                }
                val value = prefsJson.opt(key)
                if (applyValue(editor, key, value)) count += 1
            }
        }

        editor.apply()

        val conflicts = if (importedParserJson != null && importedParserJson.isNotEmpty()) {
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
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val targetFile = File(tempDir, entry.name)
                        if (!targetFile.canonicalPath.startsWith(tempDir.canonicalPath)) {
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
                isZip = true
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
                    val prefsJson = catBlock.optJSONObject("preferences") ?: continue
                    val n = prefsJson.length()
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
    private fun addFileToZip(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /** Recursively add a directory to a ZIP output stream under [basePath]. */
    private fun addDirToZip(zip: ZipOutputStream, dir: File, basePath: String) {
        dir.walkTopDown().forEach { file ->
            val relativePath = file.relativeTo(dir).path.replace(File.separatorChar, '/')
            if (file.isDirectory) {
                val dirEntry = if (relativePath.isEmpty()) "$basePath/" else "$basePath/$relativePath/"
                zip.putNextEntry(ZipEntry(dirEntry))
                zip.closeEntry()
            } else {
                zip.putNextEntry(ZipEntry("$basePath/$relativePath"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
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
                        else -> value?.toString()?.toBoolean() ?: false
                    })
                    true
                }
                "int" -> {
                    val parsed = when (value) {
                        is Number -> value.toInt()
                        else -> value?.toString()?.toIntOrNull()
                    } ?: return false
                    editor.putInt(key, parsed)
                    true
                }
                "long" -> {
                    val parsed = when (value) {
                        is Number -> value.toLong()
                        else -> value?.toString()?.toLongOrNull()
                    } ?: return false
                    editor.putLong(key, parsed)
                    true
                }
                "float" -> {
                    val parsed = when (value) {
                        is Number -> value.toFloat()
                        else -> value?.toString()?.toFloatOrNull()
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingJson = prefs.getString("parser_rules_json", null) ?: return emptyList()
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
        } catch (e: Exception) {
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingJson = prefs.getString("parser_rules_json", "[]") ?: "[]"
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
            prefs.edit().putString("parser_rules_json", existing.toString()).apply()
        } catch (_: Exception) { }
    }
}
