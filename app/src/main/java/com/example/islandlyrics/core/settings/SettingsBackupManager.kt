package com.example.islandlyrics.core.settings

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object SettingsBackupManager {

    private const val PREFS_NAME = "IslandLyricsPrefs"
    private const val SCHEMA_VERSION = 2
    private val FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HH_mm_ss", Locale.US)

    data class ExportResult(
        val success: Boolean,
        val exportedCount: Int,
        val error: String? = null
    )

    data class ImportResult(
        val success: Boolean,
        val importedCount: Int,
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
        val error: String? = null
    )

    fun buildExportFileName(now: LocalDateTime = LocalDateTime.now()): String {
        return "Capsulyric_${now.format(FILE_TIME_FORMATTER)}.json"
    }

    // ── v1 (legacy, full export) ────────────────────────────────────────

    /** Full export (v2 schema, all categories). Kept for backward compat callers. */
    suspend fun exportToUri(context: Context, uri: Uri): ExportResult {
        val allLeafIds = BackupCategories.ALL_CATEGORIES.flatMap { c ->
            if (c.subGroups.isNotEmpty()) c.subGroups.map { it.id } else listOf(c.id)
        }.toSet()
        return exportSelected(context, uri, allLeafIds)
    }

    /** Full import – compatible with v1 and v2 files. */
    suspend fun importFromUri(context: Context, uri: Uri): ImportResult {
        val allLeafIds = BackupCategories.ALL_CATEGORIES.flatMap { c ->
            if (c.subGroups.isNotEmpty()) c.subGroups.map { it.id } else listOf(c.id)
        }.toSet()
        return importSelected(context, uri, allLeafIds)
    }

    // ── v2 (selective) ─────────────────────────────────────────────────

    /** Export only the selected leaf items (category IDs for atomic, subGroup IDs for expandable). */
    suspend fun exportSelected(
        context: Context,
        uri: Uri,
        selectedLeafIds: Set<String>
    ): ExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val all = prefs.all
            val selectedKeys = BackupCategories.collectKeysForLeafIds(all.keys, selectedLeafIds)

            val categoriesJson = JSONObject()
            var count = 0
            val grouped = selectedKeys.groupBy { key -> BackupCategories.categoryForKey(key)?.id ?: "other" }

            // Detect if parser_rules is being exported with partial app selection
            val parserSelected = selectedLeafIds.any { it.startsWith("parser_") && it != "parser_all" }

            for ((catId, keys) in grouped) {
                val entries = JSONObject()
                for (key in keys.sorted()) {
                    val value = all[key] ?: continue
                    if (key in BackupCategories.EXCLUDED_KEYS) continue
                    
                    // Filter parser_rules_json to only selected apps
                    val exportValue = if (key == "parser_rules_json" && parserSelected && value is String) {
                        BackupCategories.filterParserRulesJson(value, selectedLeafIds)
                    } else {
                        value
                    }
                    
                    val wrapped = wrapValue(exportValue) ?: continue
                    entries.put(key, wrapped)
                    count += 1
                }
                if (entries.length() > 0) {
                    categoriesJson.put(catId, JSONObject().apply {
                        put("preferences", entries)
                    })
                }
            }

            val root = JSONObject().apply {
                put("schema_version", SCHEMA_VERSION)
                put("app", "Capsulyric")
                put("exported_at", LocalDateTime.now().toString())
                put("selected_leaf_ids", JSONArray(selectedLeafIds.toList()))
                put("categories", categoriesJson)
            }

            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("openOutputStream returned null")

            ExportResult(success = true, exportedCount = count)
        }.getOrElse {
            ExportResult(success = false, exportedCount = 0, error = it.message)
        }
    }

    /** Import only the selected leaf items. Handles both v1 (flat) and v2 (structured) files. */
    suspend fun importSelected(
        context: Context,
        uri: Uri,
        selectedLeafIds: Set<String>
    ): ImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IllegalStateException("openInputStream returned null")

            val root = JSONObject(text)
            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            val schemaVersion = root.optInt("schema_version", 1)

            var count = 0
            var importedParserJson: String? = null

            if (schemaVersion >= 2 && root.has("categories")) {
                // v2: structured categories – filter keys by leaf ID patterns
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
                            // Intercept parser_rules_json for merge (unwrap type wrapper)
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
                // v1: flat preferences block – map each key to its category, filter by leaf patterns
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

            // Check for parser rule conflicts
            val conflicts = if (importedParserJson != null && importedParserJson.isNotEmpty()) {
                val existingConflicts = checkParserConflicts(context, importedParserJson)
                if (existingConflicts.isEmpty()) {
                    // No conflicts — apply directly
                    BackupCategories.mergeParserRulesJson(context, importedParserJson)
                }
                existingConflicts
            } else {
                emptyList()
            }

            ImportResult(success = true, importedCount = count, parserConflicts = conflicts)
        }.getOrElse {
            ImportResult(success = false, importedCount = 0, error = it.message)
        }
    }

    /** Read a backup file and return which categories + key counts it contains. */
    suspend fun previewImportFile(context: Context, uri: Uri): PreviewResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IllegalStateException("openInputStream returned null")

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
                    PreviewResult(success = true, categoryCounts = counts, totalKeys = total)
                } else {
                    PreviewResult(success = false, error = "Missing categories in v2 file")
                }
            } else {
                // v1: detect categories from flat preferences
                val prefsJson = root.optJSONObject("preferences") ?: root
                val counts = BackupCategories.detectCategoriesFromJson(prefsJson)
                var total = 0
                counts.values.forEach { total += it }
                PreviewResult(success = true, categoryCounts = counts, totalKeys = total)
            }
        }.getOrElse {
            PreviewResult(success = false, error = it.message)
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
