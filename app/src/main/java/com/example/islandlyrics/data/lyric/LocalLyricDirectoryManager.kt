package com.example.islandlyrics.data.lyric

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LocalLyricDirectoryManager private constructor(private val context: Context) {

    enum class ExportDirectoryStatus {
        AVAILABLE,
        NONE_CONFIGURED,
        NOT_WRITABLE
    }

    data class DirectoryEntry(
        val uri: Uri,
        val displayName: String
    )

    data class IndexedFile(
        val uri: Uri,
        val normalizedName: String,
        val originalName: String
    )

    data class LrcFileInfo(
        val uri: Uri,
        val fileName: String,
        val metadata: LocalLrcParser.LrcMetadata?,
        val customMatch: CustomMatch?
    )

    data class CustomMatch(
        val fileUri: String,
        val title: String,
        val artist: String
    )

    data class ExportDirectoryResolution(
        val uri: Uri?,
        val status: ExportDirectoryStatus
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val indexMutex = Mutex()
    private var fileIndex: List<IndexedFile> = emptyList()
    private var indexBuilt = false

    fun getDirectories(): List<DirectoryEntry> {
        val json = prefs.getString(KEY_DIRECTORIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val uriStr = obj.optString("uri") ?: return@mapNotNull null
                val uri = Uri.parse(uriStr)
                if (!isUriPermissionValid(uri)) return@mapNotNull null
                DirectoryEntry(uri, obj.optString("name", uriStr))
            }
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Failed to load directories: ${e.message}")
            emptyList()
        }
    }

    fun addDirectory(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            AppLogger.getInstance().e(TAG, "Failed to take URI permission: ${e.message}")
            return
        }

        val displayName = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "Unknown"

        val current = getDirectoriesRaw()
        for (i in 0 until current.length()) {
            if (current.getJSONObject(i).optString("uri") == uri.toString()) return
        }

        val entry = JSONObject().apply {
            put("uri", uri.toString())
            put("name", displayName)
        }
        current.put(entry)
        prefs.edit().putString(KEY_DIRECTORIES, current.toString()).apply()
        invalidateIndex()
        AppLogger.getInstance().i(TAG, "Added directory: $displayName")
    }

    fun removeDirectory(uri: Uri) {
        val current = getDirectoriesRaw()
        val filtered = JSONArray()
        for (i in 0 until current.length()) {
            val obj = current.getJSONObject(i)
            if (obj.optString("uri") != uri.toString()) {
                filtered.put(obj)
            }
        }
        prefs.edit().putString(KEY_DIRECTORIES, filtered.toString()).apply()

        try {
            context.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {}

        invalidateIndex()
        AppLogger.getInstance().i(TAG, "Removed directory: $uri")
    }

    fun invalidateIndex() {
        indexBuilt = false
        fileIndex = emptyList()
    }

    suspend fun ensureIndexBuilt() {
        if (indexBuilt) return
        rebuildIndex()
    }

    suspend fun rebuildIndex() = indexMutex.withLock {
        withContext(Dispatchers.IO) {
            val directories = getDirectories()
            val indexed = mutableListOf<IndexedFile>()

            for (dir in directories) {
                try {
                    val tree = DocumentFile.fromTreeUri(context, dir.uri) ?: continue
                    val files = tree.listFiles()
                    for (file in files) {
                        val name = file.name ?: continue
                        if (!isLrcFile(name)) continue
                        val normalized = normalizeName(name)
                        indexed.add(IndexedFile(file.uri, normalized, name))
                    }
                } catch (e: Exception) {
                    AppLogger.getInstance().e(TAG, "Failed to scan directory ${dir.displayName}: ${e.message}")
                }
            }

            fileIndex = indexed
            indexBuilt = true
            AppLogger.getInstance().i(TAG, "Index built: ${indexed.size} LRC files")
        }
    }

    suspend fun findMatch(title: String, artist: String): IndexedFile? {
        ensureIndexBuilt()

        val customMatches = getCustomMatches()
        val normalizedTitle = normalizeForMatch(title)
        val normalizedArtist = normalizeForMatch(artist)
        for ((uriStr, match) in customMatches) {
            if (normalizeForMatch(match.title) == normalizedTitle &&
                normalizeForMatch(match.artist) == normalizedArtist) {
                val uri = Uri.parse(uriStr)
                val indexed = fileIndex.firstOrNull { it.uri == uri }
                if (indexed != null) return indexed
            }
        }

        val candidates = buildMatchCandidates(title, artist)
        for (candidate in candidates) {
            val match = fileIndex.firstOrNull { it.normalizedName == candidate }
            if (match != null) return match
        }
        return null
    }

    fun getFirstDirectoryUri(): Uri? {
        return getDirectories().firstOrNull()?.uri
    }

    fun getExportDirectoryUri(): Uri? {
        return resolveExportDirectory().uri
    }

    fun resolveExportDirectory(): ExportDirectoryResolution {
        val directories = getDirectories()
        if (directories.isEmpty()) {
            return ExportDirectoryResolution(
                uri = null,
                status = ExportDirectoryStatus.NONE_CONFIGURED
            )
        }

        val saved = prefs.getString(KEY_EXPORT_DIRECTORY, null)
        if (saved != null) {
            val uri = Uri.parse(saved)
            if (isUriWritablePermissionValid(uri)) {
                return ExportDirectoryResolution(
                    uri = uri,
                    status = ExportDirectoryStatus.AVAILABLE
                )
            }
        }

        val fallbackUri = directories.firstOrNull { isUriWritablePermissionValid(it.uri) }?.uri
        return if (fallbackUri != null) {
            ExportDirectoryResolution(
                uri = fallbackUri,
                status = ExportDirectoryStatus.AVAILABLE
            )
        } else {
            ExportDirectoryResolution(
                uri = null,
                status = ExportDirectoryStatus.NOT_WRITABLE
            )
        }
    }

    fun setExportDirectory(uri: Uri) {
        prefs.edit().putString(KEY_EXPORT_DIRECTORY, uri.toString()).apply()
    }

    fun isExportDirectory(uri: Uri): Boolean {
        val saved = prefs.getString(KEY_EXPORT_DIRECTORY, null) ?: return false
        return saved == uri.toString()
    }

    fun hasDirectories(): Boolean {
        return getDirectories().isNotEmpty()
    }

    fun isExportMatchSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_EXPORT_MATCH_SYNC_ENABLED, true)
    }

    fun setExportMatchSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXPORT_MATCH_SYNC_ENABLED, enabled).apply()
        invalidateIndex()
    }

    suspend fun listFilesInDirectory(directoryUri: Uri): List<LrcFileInfo> = withContext(Dispatchers.IO) {
        try {
            val tree = DocumentFile.fromTreeUri(context, directoryUri) ?: return@withContext emptyList()
            val customMatches = getCustomMatches()
            tree.listFiles()
                .filter { it.name?.let { n -> isLrcFile(n) } == true }
                .map { file ->
                    val name = file.name ?: "unknown.lrc"
                    val metadata = LocalLrcParser.extractMetadata(context, file.uri)
                    val custom = customMatches[file.uri.toString()]
                    LrcFileInfo(file.uri, name, metadata, custom)
                }
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Failed to list files: ${e.message}")
            emptyList()
        }
    }

    fun getCustomMatches(): Map<String, CustomMatch> {
        val json = prefs.getString(KEY_CUSTOM_MATCHES, null) ?: return emptyMap()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).associate { i ->
                val obj = array.getJSONObject(i)
                val uri = obj.getString("uri")
                uri to CustomMatch(uri, obj.getString("title"), obj.getString("artist"))
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun setCustomMatch(fileUri: Uri, title: String, artist: String) {
        val matches = getCustomMatches().toMutableMap()
        matches[fileUri.toString()] = CustomMatch(fileUri.toString(), title, artist)
        saveCustomMatches(matches)
        invalidateIndex()
    }

    fun removeCustomMatch(fileUri: Uri) {
        val matches = getCustomMatches().toMutableMap()
        matches.remove(fileUri.toString())
        saveCustomMatches(matches)
        invalidateIndex()
    }

    private fun saveCustomMatches(matches: Map<String, CustomMatch>) {
        val array = JSONArray()
        for ((_, match) in matches) {
            array.put(JSONObject().apply {
                put("uri", match.fileUri)
                put("title", match.title)
                put("artist", match.artist)
            })
        }
        prefs.edit().putString(KEY_CUSTOM_MATCHES, array.toString()).apply()
    }

    private fun buildMatchCandidates(title: String, artist: String): List<String> {
        val cleanTitle = normalizeForMatch(title)
        val cleanArtist = normalizeForMatch(artist)
        val candidates = mutableListOf<String>()

        if (cleanArtist.isNotBlank()) {
            candidates.add("$cleanArtist - $cleanTitle")
            candidates.add("$cleanTitle - $cleanArtist")
        }
        candidates.add(cleanTitle)

        val strippedTitle = stripSuffixes(cleanTitle)
        if (strippedTitle != cleanTitle) {
            if (cleanArtist.isNotBlank()) {
                candidates.add("$cleanArtist - $strippedTitle")
                candidates.add("$strippedTitle - $cleanArtist")
            }
            candidates.add(strippedTitle)
        }

        return candidates.distinct()
    }

    private fun normalizeForMatch(value: String): String {
        return value.trim().lowercase()
    }

    private fun stripSuffixes(title: String): String {
        var clean = title
        clean = clean.replace(Regex("""\(.*?\)"""), "")
        clean = clean.replace(Regex("""\[.*?]"""), "")
        val suffixes = listOf("feat.", "ft.", "remix", "version", "live", "cover", "radio edit", "mix")
        for (suffix in suffixes) {
            clean = clean.replace(suffix, "", ignoreCase = true)
        }
        return clean.trim().replace(Regex("""\s+"""), " ")
    }

    private fun normalizeName(filename: String): String {
        val nameWithoutExt = filename.substringBeforeLast(".")
        return nameWithoutExt.trim().lowercase()
    }

    private fun isLrcFile(name: String): Boolean {
        return name.lowercase().endsWith(".lrc")
    }

    private fun isUriPermissionValid(uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    private fun isUriWritablePermissionValid(uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
    }

    private fun getDirectoriesRaw(): JSONArray {
        val json = prefs.getString(KEY_DIRECTORIES, null) ?: return JSONArray()
        return try { JSONArray(json) } catch (_: Exception) { JSONArray() }
    }

    companion object {
        private const val TAG = "LocalLyricDirMgr"
        private const val PREFS_NAME = "IslandLyricsPrefs"
        private const val KEY_DIRECTORIES = "local_lyric_directories_json"
        private const val KEY_CUSTOM_MATCHES = "local_lyric_custom_matches_json"
        private const val KEY_EXPORT_DIRECTORY = "local_lyric_export_directory_uri"
        private const val KEY_EXPORT_MATCH_SYNC_ENABLED = "local_lyric_export_match_sync_enabled"

        @Volatile
        private var instance: LocalLyricDirectoryManager? = null

        fun getInstance(context: Context): LocalLyricDirectoryManager {
            return instance ?: synchronized(this) {
                instance ?: LocalLyricDirectoryManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
