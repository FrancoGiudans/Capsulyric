package com.example.islandlyrics.utils

import android.content.Context
import com.example.islandlyrics.BuildConfig
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Release API client for checking updates.
 * Repository: FrancoGiudans/Capsulyric
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL = "https://api.github.com/repos/FrancoGiudans/Capsulyric/releases/latest"
    private const val PREFS_NAME = "IslandLyricsPrefs"
    private const val KEY_IGNORED_VERSION = "ignored_update_version"
    private const val KEY_AUTO_UPDATE = "auto_check_updates"
    private const val KEY_LAST_CHECK = "last_update_check_time"
    private const val KEY_PRERELEASE_UPDATES = "allow_prerelease_updates"
    private const val KEY_PRERELEASE_CHANNEL = "prerelease_channel" // Alpha, Beta, Pre

    data class ReleaseInfo(
        val tagName: String,        // e.g., "v1.0_C25"
        val name: String,            // Release title
        val body: String,            // Changelog (Markdown)
        val htmlUrl: String,         // GitHub release page URL
        val publishedAt: String,      // ISO 8601 timestamp
        val prerelease: Boolean = false
    )

    /**
     * Check if auto-update is enabled in settings.
     */
    fun isAutoUpdateEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_UPDATE, true)  // Default: enabled
    }

    /**
     * Set auto-update preference.
     */
    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }
    /**
     * Check if prerelease updates are enabled.
     */
    fun isPrereleaseEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PRERELEASE_UPDATES, false)
    }

    /**
     * Set prerelease update preference.
     */
    fun setPrereleaseEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PRERELEASE_UPDATES, enabled).apply()
    }

    /**
     * Get the selected prerelease channel (Alpha, Beta, Pre).
     * Defaults to Alpha (receives all prereleases).
     */
    fun getPrereleaseChannel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PRERELEASE_CHANNEL, "Alpha") ?: "Alpha"
    }

    /**
     * Set the selected prerelease channel.
     */
    fun setPrereleaseChannel(context: Context, channel: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PRERELEASE_CHANNEL, channel).apply()
    }

    /**
     * Get the ignored version from preferences.
     * Auto-clears if current version >= ignored version.
     */
    fun getIgnoredVersion(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ignored = prefs.getString(KEY_IGNORED_VERSION, null)
        
        // Auto-clear if current version is >= ignored version
        if (ignored != null) {
            val currentVersion = BuildConfig.VERSION_NAME
            if (compareVersions(currentVersion, ignored) >= 0) {
                AppLogger.getInstance().d(TAG, "Auto-clearing outdated ignored version: $ignored (current: $currentVersion)")
                clearIgnoredVersion(context)
                return null
            }
        }
        
        return ignored
    }

    /**
     * Set version to ignore.
     */
    fun setIgnoredVersion(context: Context, version: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_IGNORED_VERSION, version).apply()
        AppLogger.getInstance().d(TAG, "Ignored version set to: $version")
    }

    /**
     * Clear ignored version.
     */
    fun clearIgnoredVersion(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_IGNORED_VERSION).apply()
    }

    /**
     * Get last check timestamp.
     */
    fun getLastCheckTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_CHECK, 0)
    }

    /**
     * Update last check timestamp.
     */
    private fun updateLastCheckTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    /**
     * Check for updates from GitHub Releases API.
     * @return ReleaseInfo if newer version available, null otherwise
     */
    /**
     * Fetch the absolute latest release information from GitHub without comparing versions, including prereleases.
     * Modified for Debug Center to respect channels.
     */
    suspend fun fetchAbsoluteLatestRelease(context: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://api.github.com/repos/FrancoGiudans/Capsulyric/releases"
            
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONArray(response)
                
                val includePrerelease = isPrereleaseEnabled(context)
                val userChannel = if (includePrerelease) getPrereleaseChannel(context) else "Release"

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    val release = parseRelease(json)
                    if (isUpdateAllowedForChannel(release.tagName, userChannel)) {
                        return@withContext release
                    }
                }
            } else {
                AppLogger.getInstance().e(TAG, "GitHub API error: ${connection.responseCode}")
            }
            connection.disconnect()
            null
        } catch (e: Exception) {
             AppLogger.getInstance().e(TAG, "Failed to fetch absolute latest release", e)
             null
        }
    }

    /**
     * Check for updates from GitHub Releases API.
     * @return ReleaseInfo if newer version available, null otherwise
     */
    suspend fun checkForUpdate(context: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            updateLastCheckTime(context)
            
            // For Android 15 version, we only check the latest stable-track release
            // We search for releases that contain "_A15." in the tag name
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val release = parseRelease(json)
                
                val currentVersion = BuildConfig.VERSION_NAME
                val remoteVersion = release.tagName.removePrefix("v")
                
                // Only allow if it's an A15 version
                if (!remoteVersion.contains("_A15.")) {
                    AppLogger.getInstance().d(TAG, "Latest release is not an A15 version: ${release.tagName}")
                    return@withContext null
                }
                
                if (compareVersions(remoteVersion, currentVersion) > 0) {
                    val ignoredVersion = getIgnoredVersion(context)
                    if (ignoredVersion != null && release.tagName == ignoredVersion) {
                        AppLogger.getInstance().d(TAG, "Skipping ignored version: ${release.tagName}")
                        return@withContext null
                    }
                    
                    AppLogger.getInstance().d(TAG, "A15 Update available: ${release.tagName}")
                    return@withContext release
                }
            } else {
                AppLogger.getInstance().e(TAG, "GitHub API error: ${connection.responseCode}")
            }
            connection.disconnect()
            null
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Failed to check for updates", e)
            null
        }
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo {
        return ReleaseInfo(
            tagName = json.getString("tag_name"),
            name = json.getString("name"),
            body = json.getString("body"),
            htmlUrl = json.getString("html_url"),
            publishedAt = json.getString("published_at"),
            prerelease = json.optBoolean("prerelease", false)
        )
    }

    fun compareVersions(v1: String, v2: String): Int {
        try {
            // Extract commit count from "1.0_A15.20" format
            val commit1 = v1.substringAfter("_A15.", "0").toIntOrNull() ?: 0
            val commit2 = v2.substringAfter("_A15.", "0").toIntOrNull() ?: 0
            
            return commit1.compareTo(commit2)
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Version comparison error", e)
            return 0
        }
    }

    /**
     * Helper to determine if a specific tag is allowed for the user's selected channel.
     * tagName: "Version.1.3.Alpha2_C300" or "Version.1.2_C200" or "v1.2_C200"
     * userChannel: "Alpha", "Beta", "Pre", or "Release"
     */
    private fun isUpdateAllowedForChannel(tagName: String, userChannel: String): Boolean {
        // Extract suffix. If no Alpha, Beta, Pre, RC, Fix, then it's a normal Release.
        // We mainly care about Prerelease suffixes: Alpha, Beta, Pre.
        val isAlpha = tagName.contains(".Alpha")
        val isBeta = tagName.contains(".Beta")
        val isPre = tagName.contains(".Pre")
        val isPrereleaseTag = isAlpha || isBeta || isPre
        
        // Proper releases (no suffix, or RC/Fix) are ALWAYS allowed for ALL channels.
        if (!isPrereleaseTag) {
            return true
        }
        
        // If it's a prerelease tag, but user channel is "Release", block it.
        if (userChannel == "Release") {
            return false
        }
        
        // Pre channel: allows ONLY Pre and stable.
        if (userChannel == "Pre") {
            return isPre
        }
        
        // Beta channel: allows Beta and Pre (and stable).
        if (userChannel == "Beta") {
            return isBeta || isPre
        }
        
        // Alpha channel: allows Alpha, Beta, Pre (and stable)
        if (userChannel == "Alpha") {
            return isAlpha || isBeta || isPre
        }
        
        return false
    }
}
