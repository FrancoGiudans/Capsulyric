package com.example.islandlyrics.core.update

import android.content.Context
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.core.logging.AppLogger
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
    private const val KEY_PRERELEASE_CHANNEL = "prerelease_channel" // Alpha, Beta, Pre, Canary

    data class ReleaseInfo(
        val tagName: String,        // e.g., "v1.0_C25"
        val name: String,            // Release title
        val body: String,            // Changelog (Markdown)
        val htmlUrl: String,         // GitHub release page URL
        val publishedAt: String,      // ISO 8601 timestamp
        val prerelease: Boolean = false
    )

    fun isAutoUpdateEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_UPDATE, true)
    }

    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }

    fun isPrereleaseEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PRERELEASE_UPDATES, false)
    }

    fun setPrereleaseEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PRERELEASE_UPDATES, enabled).apply()
    }

    fun getPrereleaseChannel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PRERELEASE_CHANNEL, "Alpha") ?: "Alpha"
    }

    fun setPrereleaseChannel(context: Context, channel: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PRERELEASE_CHANNEL, channel).apply()
    }

    fun getIgnoredVersion(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ignored = prefs.getString(KEY_IGNORED_VERSION, null)
        if (ignored != null) {
            val currentVersion = BuildConfig.VERSION_NAME
            if (compareVersions(currentVersion, ignored) >= 0) {
                clearIgnoredVersion(context)
                return null
            }
        }
        return ignored
    }

    fun setIgnoredVersion(context: Context, version: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_IGNORED_VERSION, version).apply()
    }

    fun clearIgnoredVersion(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_IGNORED_VERSION).apply()
    }

    fun getLastCheckTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_CHECK, 0)
    }

    private fun updateLastCheckTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    suspend fun fetchAbsoluteLatestRelease(context: Context, currentVersionOverride: String? = null): ReleaseInfo? = withContext(Dispatchers.IO) {
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
                    if (isUpdateAllowedForChannel(release, userChannel)) {
                        if (currentVersionOverride != null) {
                            return@withContext checkForUpdate(context, currentVersionOverride)
                        }
                        return@withContext release
                    }
                }
            }
            null
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "fetchAbsoluteLatestRelease failed", e)
            null
        }
    }

    suspend fun checkForUpdate(context: Context, currentVersionOverride: String? = null): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            updateLastCheckTime(context)
            val includePrerelease = isPrereleaseEnabled(context)
            val apiUrl = if (includePrerelease) "https://api.github.com/repos/FrancoGiudans/Capsulyric/releases" else GITHUB_API_URL
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val allReleases = mutableListOf<ReleaseInfo>()
                if (includePrerelease) {
                    val jsonArray = org.json.JSONArray(response)
                    for (i in 0 until jsonArray.length()) {
                        allReleases.add(parseRelease(jsonArray.getJSONObject(i)))
                    }
                } else {
                    allReleases.add(parseRelease(JSONObject(response)))
                }

                val currentVersion = currentVersionOverride ?: BuildConfig.VERSION_NAME
                val userChannel = if (includePrerelease) getPrereleaseChannel(context) else "Release"
                val newerReleases = mutableListOf<ReleaseInfo>()

                for (release in allReleases) {
                    if (isUpdateAllowedForChannel(release, userChannel)) {
                        if (compareVersions(release.tagName, currentVersion) > 0) {
                            newerReleases.add(release)
                        }
                    }
                }

                if (newerReleases.isEmpty()) return@withContext null
                val latestRelease = newerReleases.first()
                val ignoredVersion = getIgnoredVersion(context)
                if (ignoredVersion != null && latestRelease.tagName == ignoredVersion) return@withContext null

                if (newerReleases.size == 1) return@withContext latestRelease
                return@withContext latestRelease.copy(body = mergeChangelogs(newerReleases))
            }
            null
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "checkForUpdate failed", e)
            null
        }
    }

    private const val CN_HEADER = "## \uD83C\uDDE8\uD83C\uDDF3"
    private const val EN_HEADER = "## \uD83C\uDDEC\uD83C\uDDE7"

    private fun mergeChangelogs(releases: List<ReleaseInfo>): String {
        val cnMerged = StringBuilder()
        val enMerged = StringBuilder()
        for (release in releases) {
            val (cn, en) = extractSections(release.body)
            if (cn.isNotEmpty()) {
                if (cnMerged.isNotEmpty()) cnMerged.append("\n\n")
                cnMerged.append("### ${release.tagName}\n$cn")
            }
            if (en.isNotEmpty()) {
                if (enMerged.isNotEmpty()) enMerged.append("\n\n")
                enMerged.append("### ${release.tagName}\n$en")
            }
        }
        return "$CN_HEADER\n${cnMerged}\n\n$EN_HEADER\n${enMerged}"
    }

    private fun extractSections(body: String): Pair<String, String> {
        val cnStart = body.indexOf(CN_HEADER)
        val enStart = body.indexOf(EN_HEADER)
        if (cnStart == -1 || enStart == -1) return Pair(body, "")
        val cnSection = if (cnStart < enStart) body.substring(cnStart + CN_HEADER.length, enStart).trim() else body.substring(cnStart + CN_HEADER.length).trim()
        val enSection = if (enStart < cnStart) body.substring(enStart + EN_HEADER.length, cnStart).trim() else body.substring(enStart + EN_HEADER.length).trim()
        return Pair(cnSection, enSection)
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo {
        return ReleaseInfo(
            tagName = json.getString("tag_name"),
            name = json.getString("name"),
            body = json.getString("body"),
            htmlUrl = json.getString("html_url"),
            publishedAt = json.optString("published_at", ""),
            prerelease = json.optBoolean("prerelease", false)
        )
    }

    /**
     * Compare version strings using commit count (_C) as the absolute source of truth.
     * @return Positive if v1 > v2, negative if v1 < v2, 0 if equal
     */
    fun compareVersions(v1: String, v2: String): Int {
        val c1 = extractCommitCount(v1)
        val c2 = extractCommitCount(v2)
        return c1.compareTo(c2)
    }

    private fun extractCommitCount(version: String): Int {
        val cIdx = version.indexOf("_C")
        if (cIdx >= 0) {
            // Take digits after _C until end or next non-digit
            val countStr = version.substring(cIdx + 2).takeWhile { it.isDigit() }
            return countStr.toIntOrNull() ?: 0
        }
        return 0
    }

    private fun isUpdateAllowedForChannel(release: ReleaseInfo, userChannel: String): Boolean {
        // Rule 1: Everyone receives stable releases
        if (!release.prerelease) return true

        // Rule 2: Release channel receives NO prereleases
        if (userChannel == "Release") return false

        val tag = release.tagName
        
        // Rule 3: Canary channel ONLY receives Canary updates
        if (userChannel == "Canary") {
            return tag.startsWith("Canary.Version")
        }

        // Rule 4: Tagged prerelease channels (Alpha, Beta, Pre) never receive Canary
        val isCanary = tag.startsWith("Canary.Version") || (tag.contains("_C") && !tag.contains(".Alpha") && !tag.contains(".Beta") && !tag.contains(".Pre"))
        if (isCanary) return false

        // Rule 5: Traditional pre-release hierarchy
        val isAlpha = tag.contains(".Alpha")
        val isBeta = tag.contains(".Beta")
        val isPre = tag.contains(".Pre")

        return when (userChannel) {
            "Pre" -> isPre
            "Beta" -> isBeta || isPre
            "Alpha" -> isAlpha || isBeta || isPre
            else -> false
        }
    }
}
