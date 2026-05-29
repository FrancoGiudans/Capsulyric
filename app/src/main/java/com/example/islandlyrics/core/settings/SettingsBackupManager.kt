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
    private const val SCHEMA_VERSION = 1
    private val FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HH_mm_ss", Locale.US)
    private val EXCLUDED_KEYS = setOf("is_setup_complete")

    data class ExportResult(
        val success: Boolean,
        val exportedCount: Int,
        val error: String? = null
    )

    data class ImportResult(
        val success: Boolean,
        val importedCount: Int,
        val error: String? = null
    )

    fun buildExportFileName(now: LocalDateTime = LocalDateTime.now()): String {
        return "Capsulyric_${now.format(FILE_TIME_FORMATTER)}.json"
    }

    suspend fun exportToUri(context: Context, uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val all = prefs.all
            val entries = JSONObject()
            var count = 0

            all.toSortedMap().forEach { (key, value) ->
                if (EXCLUDED_KEYS.contains(key)) return@forEach
                val wrapped = wrapValue(value) ?: return@forEach
                entries.put(key, wrapped)
                count += 1
            }

            val root = JSONObject().apply {
                put("schema_version", SCHEMA_VERSION)
                put("app", "Capsulyric")
                put("exported_at", LocalDateTime.now().toString())
                put("preferences", entries)
            }

            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("openOutputStream returned null")

            ExportResult(success = true, exportedCount = count)
        }.getOrElse {
            ExportResult(success = false, exportedCount = 0, error = it.message)
        }
    }

    suspend fun importFromUri(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IllegalStateException("openInputStream returned null")

            val root = JSONObject(text)
            val prefsJson = root.optJSONObject("preferences") ?: root
            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()

            var count = 0
            val iterator = prefsJson.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (EXCLUDED_KEYS.contains(key)) continue
                val value = prefsJson.opt(key)
                if (applyValue(editor, key, value)) {
                    count += 1
                }
            }

            editor.apply()
            ImportResult(success = true, importedCount = count)
        }.getOrElse {
            ImportResult(success = false, importedCount = 0, error = it.message)
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
}
