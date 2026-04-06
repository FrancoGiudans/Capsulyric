package com.example.islandlyrics.core.feed

import android.content.Context
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.network.OfflineModeManager
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
    val url: String,
    val actionText: String
)

data class CommunityFeed(
    val announcement: CommunityFeedItem? = null,
    val poll: CommunityFeedItem? = null
) {
    val hasContent: Boolean
        get() = announcement != null || poll != null
}

object CommunityFeedRepository {
    private const val TAG = "CommunityFeed"
    private const val ANNOUNCEMENTS_PATH = "announcements.json"
    private const val POLLS_PATH = "polls.json"
    private const val PRIMARY_BASE_URL = "https://raw.githubusercontent.com/FrancoGiudans/CapsulyricFeed/main/data"
    private const val LEGACY_BASE_URL = "https://raw.githubusercontent.com/FrancoGiudans/Caps-feed/main/data"

    suspend fun fetchFeed(context: Context): CommunityFeed = withContext(Dispatchers.IO) {
        if (OfflineModeManager.isEnabled(context)) {
            AppLogger.getInstance().i(TAG, "Offline mode enabled, skipping community feed")
            return@withContext CommunityFeed()
        }
        val announcements = fetchItems(context, ANNOUNCEMENTS_PATH)
        val polls = fetchItems(context, POLLS_PATH)
        CommunityFeed(
            announcement = announcements.firstOrNull(),
            poll = polls.firstOrNull()
        )
    }

    private fun fetchItems(context: Context, relativePath: String): List<CommunityFeedItem> {
        val response = buildBaseUrls()
            .asSequence()
            .map { baseUrl -> baseUrl to fetchText("$baseUrl/$relativePath") }
            .firstOrNull { (_, response) -> response != null }
            ?.second
            ?: return emptyList()
        return parseItems(response, context)
    }

    private fun buildBaseUrls(): List<String> {
        return linkedSetOf(
            BuildConfig.COMMUNITY_FEED_BASE_URL.trimEnd('/'),
            PRIMARY_BASE_URL,
            LEGACY_BASE_URL
        ).filter { it.isNotBlank() }
    }

    private fun fetchText(urlString: String): String? {
        return try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

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

    private fun parseItems(rawJson: String, context: Context): List<CommunityFeedItem> {
        return try {
            val root = rawJson.trim()
            val items = when {
                root.startsWith("[") -> JSONArray(root)
                else -> JSONObject(root).optJSONArray("items") ?: JSONArray()
            }

            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    parseItem(item, context)?.let { add(it) }
                }
            }
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Failed to parse community feed", e)
            emptyList()
        }
    }

    private fun parseItem(item: JSONObject, context: Context): CommunityFeedItem? {
        if (!item.optBoolean("enabled", true)) return null

        val preferChinese = prefersChinese(context)
        val title = getLocalizedText(item, "title", preferChinese)
        val url = item.optString("url").trim()
        if (title.isEmpty() || url.isEmpty()) return null
        if (!matchesChannels(item, context)) return null
        if (!matchesVersionWindow(item)) return null
        if (!matchesTimeWindow(item)) return null

        return CommunityFeedItem(
            id = item.optString("id").ifBlank { title },
            title = title,
            summary = getLocalizedText(item, "summary", preferChinese),
            body = getLocalizedText(item, "body", preferChinese),
            url = url,
            actionText = getLocalizedText(item, "actionText", preferChinese)
        )
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

        val currentChannel = if (UpdateChecker.isPrereleaseEnabled(context)) {
            UpdateChecker.getPrereleaseChannel(context)
        } else {
            "Release"
        }

        for (index in 0 until channels.length()) {
            val value = channels.optString(index)
            if (value.equals("All", ignoreCase = true) || value.equals(currentChannel, ignoreCase = true)) {
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
