package com.example.islandlyrics.core.update

import android.content.Context
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.network.OfflineModeManager
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
    private const val KEY_PRERELEASE_CHANNEL = "prerelease_channel" // Legacy: Alpha, Beta, Pre, Canary
    private const val KEY_UPDATE_CHANNEL = "update_channel" // Stable, Preview, Experiment
    private val VERSION_IN_TITLE_REGEX = Regex("""Version\.\d{2}\.\d+(?:\.[A-Za-z0-9]+)?_C\d+""")

    const val CHANNEL_STABLE = "Stable"
    const val CHANNEL_PREVIEW = "Preview"
    const val CHANNEL_EXPERIMENT = "Experiment"

    private const val LEGACY_CHANNEL_ALPHA = "Alpha"
    private const val LEGACY_CHANNEL_BETA = "Beta"
    private const val LEGACY_CHANNEL_PRE = "Pre"
    private const val LEGACY_CHANNEL_CANARY = "Canary"

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
        return getUpdateChannel(context) != CHANNEL_STABLE
    }

    fun setPrereleaseEnabled(context: Context, enabled: Boolean) {
        val currentChannel = getUpdateChannel(context)
        val nextChannel = when {
            enabled && currentChannel == CHANNEL_STABLE -> CHANNEL_PREVIEW
            enabled -> currentChannel
            else -> CHANNEL_STABLE
        }
        setUpdateChannel(context, nextChannel)
    }

    fun getPrereleaseChannel(context: Context): String {
        return when (getUpdateChannel(context)) {
            CHANNEL_EXPERIMENT -> LEGACY_CHANNEL_CANARY
            CHANNEL_PREVIEW -> LEGACY_CHANNEL_PRE
            else -> LEGACY_CHANNEL_PRE
        }
    }

    fun setPrereleaseChannel(context: Context, channel: String) {
        val mappedChannel = when (channel) {
            LEGACY_CHANNEL_CANARY -> CHANNEL_EXPERIMENT
            LEGACY_CHANNEL_ALPHA, LEGACY_CHANNEL_BETA, LEGACY_CHANNEL_PRE -> CHANNEL_PREVIEW
            CHANNEL_STABLE, CHANNEL_PREVIEW, CHANNEL_EXPERIMENT -> channel
            else -> CHANNEL_PREVIEW
        }
        setUpdateChannel(context, mappedChannel)
    }

    fun getUpdateChannel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val newChannel = prefs.getString(KEY_UPDATE_CHANNEL, null)
        if (!newChannel.isNullOrBlank()) {
            return normalizeChannel(newChannel)
        }

        val prereleaseEnabled = prefs.getBoolean(KEY_PRERELEASE_UPDATES, false)
        val legacyChannel = prefs.getString(KEY_PRERELEASE_CHANNEL, LEGACY_CHANNEL_ALPHA).orEmpty()
        return if (!prereleaseEnabled) {
            CHANNEL_STABLE
        } else {
            when (legacyChannel) {
                LEGACY_CHANNEL_CANARY -> CHANNEL_EXPERIMENT
                LEGACY_CHANNEL_ALPHA, LEGACY_CHANNEL_BETA, LEGACY_CHANNEL_PRE -> CHANNEL_PREVIEW
                else -> CHANNEL_PREVIEW
            }
        }
    }

    fun setUpdateChannel(context: Context, channel: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normalizedChannel = normalizeChannel(channel)
        val legacyChannel = when (normalizedChannel) {
            CHANNEL_EXPERIMENT -> LEGACY_CHANNEL_CANARY
            CHANNEL_PREVIEW -> LEGACY_CHANNEL_PRE
            else -> LEGACY_CHANNEL_PRE
        }
        prefs.edit()
            .putString(KEY_UPDATE_CHANNEL, normalizedChannel)
            .putBoolean(KEY_PRERELEASE_UPDATES, normalizedChannel != CHANNEL_STABLE)
            .putString(KEY_PRERELEASE_CHANNEL, legacyChannel)
            .apply()
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
        if (OfflineModeManager.isEnabled(context)) {
            AppLogger.getInstance().i(TAG, "Offline mode enabled, skipping absolute release lookup")
            return@withContext null
        }
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
                val userChannel = getUpdateChannel(context)

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
        if (OfflineModeManager.isEnabled(context)) {
            AppLogger.getInstance().i(TAG, "Offline mode enabled, skipping update check")
            return@withContext null
        }
        try {
            updateLastCheckTime(context)
            val userChannel = getUpdateChannel(context)
            val apiUrl = if (userChannel == CHANNEL_STABLE) GITHUB_API_URL else "https://api.github.com/repos/FrancoGiudans/Capsulyric/releases"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val allReleases = mutableListOf<ReleaseInfo>()
                if (userChannel != CHANNEL_STABLE) {
                    val jsonArray = org.json.JSONArray(response)
                    for (i in 0 until jsonArray.length()) {
                        allReleases.add(parseRelease(jsonArray.getJSONObject(i)))
                    }
                } else {
                    allReleases.add(parseRelease(JSONObject(response)))
                }

                val currentVersion = currentVersionOverride ?: BuildConfig.VERSION_NAME
                val newerReleases = mutableListOf<ReleaseInfo>()

                for (release in allReleases) {
                    if (isUpdateAllowedForChannel(release, userChannel)) {
                        if (compareVersions(getComparableVersion(release), currentVersion) > 0) {
                            newerReleases.add(release)
                        }
                    }
                }

                if (newerReleases.isEmpty()) return@withContext null
                val latestRelease = newerReleases.first()
                val ignoredVersion = getIgnoredVersion(context)
                if (ignoredVersion != null && getComparableVersion(latestRelease) == ignoredVersion) return@withContext null

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
        val ghMerged = StringBuilder()
        for (release in releases) {
            val sections = extractSections(release.body)
            val cn = sections.first
            val en = sections.second
            val gh = sections.third

            if (cn.isNotEmpty()) {
                if (cnMerged.isNotEmpty()) cnMerged.append("\n\n")
                cnMerged.append("### ${getComparableVersion(release)}\n$cn")
            }
            if (en.isNotEmpty()) {
                if (enMerged.isNotEmpty()) enMerged.append("\n\n")
                enMerged.append("### ${getComparableVersion(release)}\n$en")
            }
            if (gh.isNotEmpty()) {
                if (ghMerged.isNotEmpty()) ghMerged.append("\n\n")
                ghMerged.append("### ${getComparableVersion(release)}\n$gh")
            }
        }
        
        val result = StringBuilder()
        if (cnMerged.isNotEmpty()) {
            result.append("$CN_HEADER\n${cnMerged}")
        }
        if (enMerged.isNotEmpty()) {
            if (result.isNotEmpty()) result.append("\n\n---\n\n")
            result.append("$EN_HEADER\n${enMerged}")
        }
        if (ghMerged.isNotEmpty()) {
            if (result.isNotEmpty()) result.append("\n\n---\n\n")
            result.append(ghMerged)
        }
        return result.toString()
    }

    private fun extractSections(body: String): Triple<String, String, String> {
        val cnStart = body.indexOf(CN_HEADER)
        val enStart = body.indexOf(EN_HEADER)
        
        // GitHub-generated sections usually start with these headers
        val ghMarkers = listOf("## What's Changed", "## New Contributors", "**Full Changelog**")
        var ghStart = -1
        for (marker in ghMarkers) {
            val idx = body.indexOf(marker)
            if (idx != -1 && (ghStart == -1 || idx < ghStart)) {
                ghStart = idx
            }
        }
        
        // Collect all found section starts and their types
        val markers = mutableListOf<Pair<Int, String>>()
        if (cnStart != -1) markers.add(cnStart to "CN")
        if (enStart != -1) markers.add(enStart to "EN")
        if (ghStart != -1) markers.add(ghStart to "GH")
        markers.sortBy { it.first }
        
        var cn = ""
        var en = ""
        var gh = ""
        
        for (i in markers.indices) {
            val start = markers[i].first
            val type = markers[i].second
            val end = if (i + 1 < markers.size) markers[i+1].first else body.length
            
            val content = body.substring(start, end).trim()
            when (type) {
                "CN" -> cn = content.substring(CN_HEADER.length).trim()
                "EN" -> en = content.substring(EN_HEADER.length).trim()
                "GH" -> gh = content.trim()
            }
        }
        
        // If no markers found at all, treat whole body as CN for backward compatibility
        if (markers.isEmpty()) return Triple(body, "", "")
        
        return Triple(cn, en, gh)
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

    fun getComparableVersion(release: ReleaseInfo): String {
        val normalizedTag = release.tagName.removePrefix("v")
        if (!isCanaryTag(release.tagName)) {
            return normalizedTag
        }
        return extractVersionFromTitle(release.name) ?: normalizedTag
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

    private fun extractVersionFromTitle(title: String): String? {
        return VERSION_IN_TITLE_REGEX.find(title)?.value
    }

    private fun isCanaryTag(tag: String): Boolean {
        return tag.startsWith("Canary.Version") || tag.contains(".Canary")
    }

    private fun isPreviewTag(tag: String): Boolean {
        return tag.contains(".Preview") ||
            tag.contains(".Alpha") ||
            tag.contains(".Beta") ||
            tag.contains(".Pre")
    }

    private fun normalizeChannel(channel: String): String {
        return when (channel) {
            CHANNEL_PREVIEW -> CHANNEL_PREVIEW
            CHANNEL_EXPERIMENT -> CHANNEL_EXPERIMENT
            else -> CHANNEL_STABLE
        }
    }

    private fun isUpdateAllowedForChannel(release: ReleaseInfo, userChannel: String): Boolean {
        val tag = release.tagName

        // Experiment is an isolated canary-only rail.
        if (userChannel == CHANNEL_EXPERIMENT) {
            return isCanaryTag(tag)
        }

        // Stable and Preview never receive experiment releases.
        if (isCanaryTag(tag)) return false

        if (!release.prerelease) return true

        return when (userChannel) {
            CHANNEL_PREVIEW -> isPreviewTag(tag)
            else -> false
        }
    }
}
