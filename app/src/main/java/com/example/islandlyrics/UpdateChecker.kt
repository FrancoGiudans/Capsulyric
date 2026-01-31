package com.example.islandlyrics

import android.content.Context
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

    data class ReleaseInfo(
        val tagName: String,        // e.g., "v1.0_C25"
        val name: String,            // Release title
        val body: String,            // Changelog (Markdown)
        val htmlUrl: String,         // GitHub release page URL
        val publishedAt: String      // ISO 8601 timestamp
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
                Log.d(TAG, "Auto-clearing outdated ignored version: $ignored (current: $currentVersion)")
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
        Log.d(TAG, "Ignored version set to: $version")
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
    suspend fun checkForUpdate(context: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            updateLastCheckTime(context)
            
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val release = ReleaseInfo(
                    tagName = json.getString("tag_name"),
                    name = json.getString("name"),
                    body = json.getString("body"),
                    htmlUrl = json.getString("html_url"),
                    publishedAt = json.getString("published_at")
                )

                Log.d(TAG, "Latest release: ${release.tagName}")

                // Compare versions
                val currentVersion = BuildConfig.VERSION_NAME
                val remoteVersion = release.tagName.removePrefix("v")

                if (compareVersions(remoteVersion, currentVersion) > 0) {
                    // Check if this version is ignored
                    val ignoredVersion = getIgnoredVersion(context)
                    if (ignoredVersion != null && release.tagName == ignoredVersion) {
                        Log.d(TAG, "Update available but ignored: ${release.tagName}")
                        return@withContext null
                    }
                    
                    Log.d(TAG, "Update available: $remoteVersion > $currentVersion")
                    return@withContext release
                } else {
                    Log.d(TAG, "No update available (current: $currentVersion, remote: $remoteVersion)")
                }
            } else {
                Log.e(TAG, "GitHub API error: ${connection.responseCode}")
            }

            connection.disconnect()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            null
        }
    }

    /**
     * Compare version strings in "1.0_C20" format.
     * @return Positive if v1 > v2, negative if v1 < v2, 0 if equal
     */
    fun compareVersions(v1: String, v2: String): Int {
        try {
            // Extract commit count from "1.0_C20" format
            val commit1 = v1.substringAfter("_C", "0").toIntOrNull() ?: 0
            val commit2 = v2.substringAfter("_C", "0").toIntOrNull() ?: 0
            
            return commit1.compareTo(commit2)
        } catch (e: Exception) {
            Log.e(TAG, "Version comparison error", e)
            return 0
        }
    }
}
