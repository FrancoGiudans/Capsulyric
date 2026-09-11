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

package com.example.islandlyrics.core.feed

import android.content.Context
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale

data class CommunityFeedItem(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
    val actions: List<CommunityFeedAction>
) {
    /** Compatibility projection for code that still expects the V1 primary action. */
    val primaryAction: CommunityFeedAction?
        get() = actions.firstOrNull { it.style == CommunityFeedActionStyle.PRIMARY } ?: actions.firstOrNull()

    /** Compatibility projection for V1 callers. */
    val url: String
        get() = primaryAction?.url.orEmpty()

    /** Compatibility projection for V1 callers. */
    val actionText: String
        get() = primaryAction?.text.orEmpty()

    val hasUrl: Boolean
        get() = primaryAction != null
}

data class CommunityFeed(
    val announcements: List<CommunityFeedItem> = emptyList(),
    val polls: List<CommunityFeedItem> = emptyList(),
    val status: CommunityFeedStatus = CommunityFeedStatus.EMPTY
) {
    val hasContent: Boolean
        get() = announcements.isNotEmpty() || polls.isNotEmpty()
}

enum class CommunityFeedStatus {
    AVAILABLE,
    EMPTY,
    UNAVAILABLE
}

object CommunityFeedRepository {
    private const val TAG = "CommunityFeed"
    private const val ANNOUNCEMENTS_PATH = "announcements.json"
    private const val POLLS_PATH = "polls.json"
    private const val ANNOUNCEMENTS_V2_PATH = "v2/announcements.json"
    private const val POLLS_V2_PATH = "v2/polls.json"
    private const val V2_SCHEMA_VERSION = 2
    private const val MAX_ACTIONS_PER_ITEM = 10
    private const val PRIMARY_BASE_URL = "https://raw.githubusercontent.com/FrancoGiudans/CapsulyricFeed/main/data"
    private const val LEGACY_BASE_URL = "https://raw.githubusercontent.com/FrancoGiudans/Caps-feed/main/data"
    private const val GITEE_BASE_URL = "https://gitee.com/franklinsmithson/caps-feed/raw/main/data"

    suspend fun fetchFeed(context: Context): CommunityFeed = withContext(Dispatchers.IO) {
        if (OfflineModeManager.isEnabled(context)) {
            AppLogger.getInstance().i(TAG, "Offline mode enabled, skipping community feed")
            return@withContext CommunityFeed()
        }
        val announcements = fetchFeedItems(context, ANNOUNCEMENTS_V2_PATH, ANNOUNCEMENTS_PATH)
        val polls = fetchFeedItems(context, POLLS_V2_PATH, POLLS_PATH)
        val hasContent = announcements.items.isNotEmpty() || polls.items.isNotEmpty()
        val hasRemoteResponse = announcements.remoteAvailable || polls.remoteAvailable
        CommunityFeed(
            announcements = announcements.items,
            polls = polls.items,
            status = when {
                hasContent -> CommunityFeedStatus.AVAILABLE
                hasRemoteResponse -> CommunityFeedStatus.EMPTY
                else -> CommunityFeedStatus.UNAVAILABLE
            }
        )
    }

    private data class FetchItemsResult(
        val items: List<CommunityFeedItem>,
        val remoteAvailable: Boolean,
        val valid: Boolean
    )

    private fun fetchFeedItems(
        context: Context,
        v2Path: String,
        v1Path: String
    ): FetchItemsResult {
        val v2 = fetchItemsCached(context, v2Path) { rawJson -> parseV2Items(rawJson, context) }
        if (v2.valid) return v2

        val v1 = fetchItemsCached(context, v1Path) { rawJson -> parseV1Items(rawJson, context) }
        return v1.copy(remoteAvailable = v1.remoteAvailable || v2.remoteAvailable)
    }

    private fun fetchItemsCached(
        context: Context,
        relativePath: String,
        parser: (String) -> List<CommunityFeedItem>?
    ): FetchItemsResult {
        val cacheFile = java.io.File(context.cacheDir, "feed_${relativePath.replace('/', '_')}")

        val response = buildBaseUrls(context)
            .asSequence()
            .map { baseUrl -> baseUrl to fetchText(buildFeedUrl(baseUrl, relativePath)) }
            .firstOrNull { (_, response) -> response != null }
            ?.second

        if (response != null) {
            val parsed = try { parser(response) } catch (_: Exception) { null }
            if (parsed != null) {
                try { cacheFile.writeText(response) } catch (_: Exception) { }
                return FetchItemsResult(items = parsed, remoteAvailable = true, valid = true)
            }
            AppLogger.getInstance().log(TAG, "Ignoring invalid feed document: $relativePath")
        }

        // Network failed or returned an invalid document — serve a valid stale cache.
        val cached = if (cacheFile.exists()) {
            try { parser(cacheFile.readText()) } catch (_: Exception) { null }
        } else {
            null
        }
        return if (cached != null) {
            FetchItemsResult(items = cached, remoteAvailable = response != null, valid = true)
        } else {
            FetchItemsResult(items = emptyList(), remoteAvailable = response != null, valid = false)
        }
    }

    private fun buildFeedUrl(baseUrl: String, relativePath: String): String {
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl/$relativePath${separator}t=${System.currentTimeMillis() / 60_000L}"
    }

    private fun buildBaseUrls(context: Context): List<String> {
        val githubUrls = listOf(
            BuildConfig.COMMUNITY_FEED_BASE_URL.trimEnd('/'),
            PRIMARY_BASE_URL,
            LEGACY_BASE_URL
        ).filter { it.isNotBlank() }
        val giteeUrls = listOf(GITEE_BASE_URL)

        val ordered = when (LabFeatureManager.getFeedSourcePriority(context)) {
            LabFeatureManager.FEED_SOURCE_GITEE -> giteeUrls + githubUrls
            else -> githubUrls + giteeUrls
        }
        return ordered.distinct()
    }

    private fun fetchText(urlString: String): String? {
        return try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000

            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                AppLogger.getInstance().log(TAG, "Feed request failed: $urlString (${connection.responseCode})")
                null
            }
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Feed request failed: $urlString", e)
            null
        }
    }

    private fun parseV1Items(rawJson: String, context: Context): List<CommunityFeedItem>? {
        return try {
            val root = rawJson.trim()
            val items = when {
                root.startsWith("[") -> JSONArray(root)
                root.startsWith("{") -> JSONObject(root).optJSONArray("items") ?: return null
                else -> return null
            }

            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    parseItem(item, context, isV2 = false)?.let { add(it) }
                }
            }
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Failed to parse community feed", e)
            null
        }
    }

    private fun parseV2Items(rawJson: String, context: Context): List<CommunityFeedItem>? {
        return try {
            val root = JSONObject(rawJson.trim())
            if (root.optInt("schemaVersion", -1) != V2_SCHEMA_VERSION) return null
            val items = root.optJSONArray("items") ?: return null

            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    parseItem(item, context, isV2 = true)?.let { add(it) }
                }
            }
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Failed to parse community feed V2", e)
            null
        }
    }

    private fun parseItem(item: JSONObject, context: Context, isV2: Boolean): CommunityFeedItem? {
        if (!item.optBoolean("enabled", true)) return null

        val preferChinese = prefersChinese(context)
        val title = getLocalizedText(item, "title", preferChinese)
        if (title.isEmpty()) return null
        if (!matchesChannels(item, context)) return null
        if (!matchesVersionWindow(item)) return null
        if (!matchesTimeWindow(item)) return null

        return CommunityFeedItem(
            id = item.optString("id").ifBlank { title },
            title = title,
            summary = getLocalizedText(item, "summary", preferChinese),
            body = getLocalizedText(item, "body", preferChinese),
            actions = if (isV2) parseActions(item, preferChinese) else parseLegacyAction(item, preferChinese)
        )
    }

    private fun parseLegacyAction(item: JSONObject, preferChinese: Boolean): List<CommunityFeedAction> {
        val url = item.optString("url").trim()
        if (!isSafeCommunityUrl(url)) return emptyList()
        return listOf(
            CommunityFeedAction(
                id = "legacy-open",
                type = CommunityFeedActionType.OPEN_URL,
                text = getLocalizedText(item, "actionText", preferChinese),
                url = url,
                style = CommunityFeedActionStyle.PRIMARY
            )
        )
    }

    private fun parseActions(
        item: JSONObject,
        preferChinese: Boolean
    ): List<CommunityFeedAction> {
        val actions = item.optJSONArray("actions") ?: return emptyList()
        val seenIds = HashSet<String>()
        var hasPrimary = false

        return buildList {
            for (index in 0 until actions.length()) {
                if (size >= MAX_ACTIONS_PER_ITEM) {
                    AppLogger.getInstance().log(TAG, "Ignoring actions beyond $MAX_ACTIONS_PER_ITEM for ${item.optString("id")}")
                    break
                }
                val action = actions.optJSONObject(index) ?: continue
                val id = action.optString("id").trim()
                val type = action.optString("type").trim().lowercase(Locale.ROOT)
                val url = action.optString("url").trim()
                val text = getLocalizedActionText(action, preferChinese)
                if (id.isEmpty()) {
                    AppLogger.getInstance().log(TAG, "Ignoring action without id in ${item.optString("id")}")
                    continue
                }
                if (!seenIds.add(id)) {
                    AppLogger.getInstance().log(TAG, "Ignoring duplicate action $id in ${item.optString("id")}")
                    continue
                }
                if (type != "open_url" || !isSafeCommunityUrl(url)) {
                    AppLogger.getInstance().log(TAG, "Ignoring unsupported or unsafe action $id in ${item.optString("id")}")
                    continue
                }

                val requestedStyle = action.optString("style", "secondary").trim().lowercase(Locale.ROOT)
                val style = if (requestedStyle == "primary" && !hasPrimary) {
                    hasPrimary = true
                    CommunityFeedActionStyle.PRIMARY
                } else {
                    CommunityFeedActionStyle.SECONDARY
                }
                add(
                    CommunityFeedAction(
                        id = id,
                        type = CommunityFeedActionType.OPEN_URL,
                        text = text,
                        url = url,
                        style = style
                    )
                )
            }
        }
    }

    private fun getLocalizedActionText(action: JSONObject, preferChinese: Boolean): String {
        val preferredKey = if (preferChinese) "textZh" else "textEn"
        val fallbackKey = if (preferChinese) "textEn" else "textZh"
        return action.optString(preferredKey).trim()
            .ifEmpty { action.optString("text").trim() }
            .ifEmpty { action.optString(fallbackKey).trim() }
    }

    private fun prefersChinese(context: Context): Boolean {
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        return locale.language.startsWith("zh", ignoreCase = true)
    }

    private fun getLocalizedText(item: JSONObject, key: String, preferChinese: Boolean): String {
        val preferredKey = if (preferChinese) "${key}Zh" else "${key}En"
        val fallbackKey = if (preferChinese) "${key}En" else "${key}Zh"

        val preferred = item.optString(preferredKey).trim()
        if (preferred.isNotEmpty()) return preferred

        val base = item.optString(key).trim()
        if (base.isNotEmpty()) return base

        return item.optString(fallbackKey).trim()
    }

    private fun matchesChannels(item: JSONObject, context: Context): Boolean {
        val channels = item.optJSONArray("channels") ?: return true
        if (channels.length() == 0) return true

        val currentChannel = UpdateChecker.getUpdateChannel(context)

        for (index in 0 until channels.length()) {
            val value = channels.optString(index)
            if (value.equals("All", ignoreCase = true) || value.equals(currentChannel, ignoreCase = true)) {
                return true
            }
            if (currentChannel == UpdateChecker.CHANNEL_STABLE && value.equals("Release", ignoreCase = true)) {
                return true
            }
            if (currentChannel == UpdateChecker.CHANNEL_PREVIEW && (
                value.equals("Preview", ignoreCase = true) ||
                    value.equals("Alpha", ignoreCase = true) ||
                    value.equals("Beta", ignoreCase = true) ||
                    value.equals("Pre", ignoreCase = true) ||
                    value.equals("Release", ignoreCase = true)
                )
            ) {
                return true
            }
            if (currentChannel == UpdateChecker.CHANNEL_EXPERIMENT && value.equals("Canary", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun matchesVersionWindow(item: JSONObject): Boolean {
        val minVersion = item.optString("minVersion").trim()
        if (minVersion.isNotEmpty() && UpdateChecker.compareVersions(BuildConfig.VERSION_NAME, minVersion) < 0) {
            return false
        }

        val maxVersion = item.optString("maxVersion").trim()
        if (maxVersion.isNotEmpty() && UpdateChecker.compareVersions(BuildConfig.VERSION_NAME, maxVersion) > 0) {
            return false
        }

        val currentCommitCount = extractCommitCount(BuildConfig.VERSION_NAME)
        val minCommitCount = item.optInt("minCommitCount", Int.MIN_VALUE)
        val maxCommitCount = item.optInt("maxCommitCount", Int.MAX_VALUE)
        return currentCommitCount in minCommitCount..maxCommitCount
    }

    private fun matchesTimeWindow(item: JSONObject): Boolean {
        val now = Instant.now()

        val startsAt = item.optString("startsAt").trim()
        if (startsAt.isNotEmpty()) {
            val startInstant = parseInstant(startsAt) ?: return false
            if (now.isBefore(startInstant)) return false
        }

        val expiresAt = item.optString("expiresAt").trim()
        if (expiresAt.isNotEmpty()) {
            val endInstant = parseInstant(expiresAt) ?: return false
            if (now.isAfter(endInstant)) return false
        }

        return true
    }

    private fun parseInstant(value: String): Instant? {
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractCommitCount(version: String): Int {
        val commitIndex = version.indexOf("_C")
        if (commitIndex < 0) return 0
        return version.substring(commitIndex + 2).takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }
}
