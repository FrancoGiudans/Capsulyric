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

package com.example.islandlyrics.core.update

import android.content.Context
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.feature.update.UpdateParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dual-source (GitHub + Gitee) Release API client for checking updates.
 * GitHub: FrancoGiudans/Capsulyric
 * Gitee:  franklinsmithson/Capsulyric  (https://gitee.com/franklinsmithson/Capsulyric/releases/tag/{tag})
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL = "https://api.github.com/repos/FrancoGiudans/Capsulyric/releases/latest"
    private const val GITHUB_API_LIST_URL = "https://api.github.com/repos/FrancoGiudans/Capsulyric/releases"
    private const val GITEE_API_URL = "https://gitee.com/api/v5/repos/franklinsmithson/Capsulyric/releases/latest"
    private const val GITEE_API_LIST_URL = "https://gitee.com/api/v5/repos/franklinsmithson/Capsulyric/releases"
    private const val GITEE_HTML_BASE = "https://gitee.com/franklinsmithson/Capsulyric/releases/tag/"
    private const val GITEE_API_PER_PAGE = 100
    private val VERSION_IN_TITLE_REGEX = Regex("""\d{2}\.\d+(?:\.[A-Za-z0-9]+)*_C\d+""")
    private val VERSION_IN_BODY_REGEX = Regex("""(?im)^\s*[-*]\s*\*\*Version:\*\*\s*`(\d{2}\.\d+(?:\.[A-Za-z0-9]+)*_C\d+)`""")

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
        val prefs = AppPreferences.of(context)
        return prefs.getBoolean(AppPreferences.Keys.AUTO_CHECK_UPDATES, true)
    }

    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        val prefs = AppPreferences.of(context)
        prefs.edit().putBoolean(AppPreferences.Keys.AUTO_CHECK_UPDATES, enabled).apply()
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
        val prefs = AppPreferences.of(context)
        val newChannel = prefs.getString(AppPreferences.Keys.UPDATE_CHANNEL, null)
        if (!newChannel.isNullOrBlank()) {
            return normalizeChannel(newChannel)
        }

        val prereleaseEnabled = prefs.getBoolean(AppPreferences.Keys.ALLOW_PRERELEASE_UPDATES, false)
        val legacyChannel = prefs.getString(AppPreferences.Keys.PRERELEASE_CHANNEL, LEGACY_CHANNEL_ALPHA).orEmpty()
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
        val prefs = AppPreferences.of(context)
        val normalizedChannel = normalizeChannel(channel)
        val legacyChannel = when (normalizedChannel) {
            CHANNEL_EXPERIMENT -> LEGACY_CHANNEL_CANARY
            CHANNEL_PREVIEW -> LEGACY_CHANNEL_PRE
            else -> LEGACY_CHANNEL_PRE
        }
        prefs.edit()
            .putString(AppPreferences.Keys.UPDATE_CHANNEL, normalizedChannel)
            .putBoolean(AppPreferences.Keys.ALLOW_PRERELEASE_UPDATES, normalizedChannel != CHANNEL_STABLE)
            .putString(AppPreferences.Keys.PRERELEASE_CHANNEL, legacyChannel)
            .apply()
    }

    fun getIgnoredVersion(context: Context): String? {
        val prefs = AppPreferences.of(context)
        val ignored = prefs.getString(AppPreferences.Keys.IGNORED_UPDATE_VERSION, null)
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
        val prefs = AppPreferences.of(context)
        prefs.edit().putString(AppPreferences.Keys.IGNORED_UPDATE_VERSION, version).apply()
    }

    fun clearIgnoredVersion(context: Context) {
        val prefs = AppPreferences.of(context)
        prefs.edit().remove(AppPreferences.Keys.IGNORED_UPDATE_VERSION).apply()
    }

    fun getLastCheckTime(context: Context): Long {
        val prefs = AppPreferences.of(context)
        return prefs.getLong(AppPreferences.Keys.LAST_UPDATE_CHECK_TIME, 0)
    }

    private fun updateLastCheckTime(context: Context) {
        val prefs = AppPreferences.of(context)
        prefs.edit().putLong(AppPreferences.Keys.LAST_UPDATE_CHECK_TIME, System.currentTimeMillis()).apply()
    }

    suspend fun fetchAbsoluteLatestRelease(context: Context, currentVersionOverride: String? = null): ReleaseInfo? = withContext(Dispatchers.IO) {
        if (OfflineModeManager.isEnabled(context)) {
            AppLogger.getInstance().i(TAG, "Offline mode enabled, skipping absolute release lookup")
            return@withContext null
        }
        try {
            val userChannel = getUpdateChannel(context)
            // Dual-source: fetch GitHub + Gitee lists, merge by commit count (priority-aware for same tag)
            val githubReleases = fetchGithubReleaseList(GITHUB_API_LIST_URL) ?: emptyList()
            val giteeReleases = fetchGiteeReleaseList("$GITEE_API_LIST_URL?per_page=$GITEE_API_PER_PAGE&page=1") ?: emptyList()
            val merged = mergeAndSortReleasesWithPriority(context, githubReleases, giteeReleases)
            for (release in merged) {
                if (isUpdateAllowedForChannel(release, userChannel)) {
                    if (currentVersionOverride != null) {
                        return@withContext checkForUpdate(context, currentVersionOverride)
                    }
                    return@withContext release
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
            val currentVersion = currentVersionOverride ?: BuildConfig.VERSION_NAME

            // Dual-source fetch
            val allReleases = mutableListOf<ReleaseInfo>()
            if (userChannel == CHANNEL_STABLE) {
                // Stable: latest single from both sources (priority decides tie-break)
                val githubSingle = fetchGithubSingleRelease(GITHUB_API_URL)
                val giteeSingle = fetchGiteeSingleRelease(GITEE_API_URL)
                val priority = try { LabFeatureManager.getUpdateSourcePriority(context) } catch (_: Exception) { LabFeatureManager.UPDATE_SOURCE_GITHUB }
                if (priority == LabFeatureManager.UPDATE_SOURCE_GITEE) {
                    giteeSingle?.let { allReleases.add(it) }
                    githubSingle?.let { allReleases.add(it) }
                } else {
                    githubSingle?.let { allReleases.add(it) }
                    giteeSingle?.let { allReleases.add(it) }
                }
            } else {
                val githubList = fetchGithubReleaseList(GITHUB_API_LIST_URL) ?: emptyList()
                val giteeList = fetchGiteeReleaseList("$GITEE_API_LIST_URL?per_page=$GITEE_API_PER_PAGE&page=1") ?: emptyList()
                allReleases.addAll(mergeAndSortReleasesWithPriority(context, githubList, giteeList))
            }

            if (allReleases.isEmpty()) return@withContext null

            // For stable the list is just 1-2 items, ensure sorted by version (latest first), dedup by priority
            val sorted = if (userChannel == CHANNEL_STABLE) {
                // Deduplicate by tag with priority, then sort
                val priority = try { LabFeatureManager.getUpdateSourcePriority(context) } catch (_: Exception) { LabFeatureManager.UPDATE_SOURCE_GITHUB }
                val ordered = if (priority == LabFeatureManager.UPDATE_SOURCE_GITEE) allReleases.sortedBy { if (it.htmlUrl.contains("gitee.com")) 0 else 1 } else allReleases
                mergeAndSortReleases(ordered.distinctBy { it.tagName })
            } else allReleases

            val newerReleases = mutableListOf<ReleaseInfo>()
            for (release in sorted) {
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
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "checkForUpdate failed", e)
            null
        }
    }

    suspend fun fetchReleaseForVersion(
        context: Context,
        currentVersionOverride: String? = null
    ): ReleaseInfo? = withContext(Dispatchers.IO) {
        if (OfflineModeManager.isEnabled(context)) {
            AppLogger.getInstance().i(TAG, "Offline mode enabled, skipping release lookup")
            return@withContext null
        }
        try {
            val targetVersion = currentVersionOverride ?: BuildConfig.VERSION_NAME
            // Try GitHub first (paginate), then Gitee
            var page = 1
            var matchedRelease: ReleaseInfo? = null
            while (matchedRelease == null) {
                val githubList = fetchGithubReleaseList("https://api.github.com/repos/FrancoGiudans/Capsulyric/releases?per_page=100&page=$page")
                if (githubList == null) break
                if (githubList.isEmpty()) break
                for (release in githubList) {
                    if (getComparableVersion(release) == targetVersion) {
                        matchedRelease = release
                        break
                    }
                }
                if (matchedRelease != null) break
                if (githubList.size < 100) break
                page++
                if (page > 5) break
            }
            if (matchedRelease != null) return@withContext matchedRelease
            // Fallback to Gitee (single page, 100)
            val giteeList = fetchGiteeReleaseList("$GITEE_API_LIST_URL?per_page=$GITEE_API_PER_PAGE&page=1")
            if (giteeList != null) {
                for (release in giteeList) {
                    if (getComparableVersion(release) == targetVersion) {
                        return@withContext release
                    }
                }
            }
            null
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "fetchReleaseForVersion failed", e)
            null
        }
    }

    // ---------- Dual-source helpers ----------
    private fun fetchHttpText(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) { null }
    }

    private fun fetchGithubSingleRelease(url: String): ReleaseInfo? {
        val text = fetchHttpText(url) ?: return null
        return try { parseRelease(JSONObject(text)) } catch (_: Exception) { null }
    }

    private fun fetchGiteeSingleRelease(url: String): ReleaseInfo? {
        val text = fetchHttpText(url) ?: return null
        return try { parseGiteeRelease(JSONObject(text)) } catch (_: Exception) { null }
    }

    private fun fetchGithubReleaseList(url: String): List<ReleaseInfo>? {
        val text = fetchHttpText(url) ?: return null
        return try {
            val arr = org.json.JSONArray(text)
            (0 until arr.length()).map { parseRelease(arr.getJSONObject(it)) }
        } catch (_: Exception) { null }
    }

    private fun fetchGiteeReleaseList(url: String): List<ReleaseInfo>? {
        val text = fetchHttpText(url) ?: return null
        return try {
            val arr = org.json.JSONArray(text)
            (0 until arr.length()).map { parseGiteeRelease(arr.getJSONObject(it)) }
        } catch (_: Exception) { null }
    }

    private fun mergeAndSortReleases(releases: List<ReleaseInfo>): List<ReleaseInfo> {
        // Deduplicate by tag, keep first occurrence (GitHub preferred), then sort by commit count desc
        val distinct = releases.distinctBy { it.tagName }
        return distinct.sortedWith(compareByDescending { extractCommitCount(getComparableVersion(it)) })
    }

    private fun mergeAndSortReleasesWithPriority(context: Context, github: List<ReleaseInfo>, gitee: List<ReleaseInfo>): List<ReleaseInfo> {
        val priority = try { LabFeatureManager.getUpdateSourcePriority(context) } catch (_: Exception) { LabFeatureManager.UPDATE_SOURCE_GITHUB }
        val ordered = if (priority == LabFeatureManager.UPDATE_SOURCE_GITEE) gitee + github else github + gitee
        val distinct = ordered.distinctBy { it.tagName }
        return distinct.sortedWith(compareByDescending { extractCommitCount(getComparableVersion(it)) })
    }

    private fun parseGiteeRelease(json: JSONObject): ReleaseInfo {
        val tag = json.getString("tag_name")
        val name = json.optString("name", tag)
        val body = json.optString("body", "")
        // Gitee uses created_at, GitHub uses published_at
        val publishedAt = json.optString("created_at", json.optString("published_at", ""))
        val prerelease = json.optBoolean("prerelease", false)
        val htmlUrl = json.optString("html_url", "").ifBlank { "$GITEE_HTML_BASE$tag" }
        return ReleaseInfo(
            tagName = tag,
            name = name,
            body = body,
            htmlUrl = htmlUrl,
            publishedAt = publishedAt,
            prerelease = prerelease
        )
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
        val sections = UpdateParser.extractSections(body)
        if (!sections.hasLocalizedContent) {
            return Triple(body.trim(), "", "")
        }
        return Triple(sections.chinese, sections.english, sections.shared)
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
        return extractVersionFromBody(release.body)
            ?: extractVersionFromTitle(release.name)
            ?: release.tagName.removePrefix("v")
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

    private fun extractVersionFromBody(body: String): String? {
        return VERSION_IN_BODY_REGEX.find(body)?.groupValues?.getOrNull(1)
    }

    private fun isCanaryTag(tag: String): Boolean {
        return tag.startsWith("Canary.Version") ||
            tag.contains(".Canary") ||
            tag.contains(".Experiment")
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
