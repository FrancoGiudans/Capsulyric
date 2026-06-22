package com.example.islandlyrics.lyrics.online.provider

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser

import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

internal class SodaMusicLyricProvider(
    private val httpClient: OnlineLyricHttpClient
) {
    suspend fun fetch(title: String, artist: String): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                val keyword = "$title $artist"
                val searchUrl = "https://api.qishui.com/luna/pc/search/track?aid=386088&app_name=&region=&geo_region=&os_region=&sim_region=&device_id=&cdid=&iid=&version_name=&version_code=&channel=&build_mode=&network_carrier=&ac=&tz_name=&resolution=&device_platform=&device_type=&os_version=&fp=&q=${keyword.encodeURL()}&cursor=&search_id=&search_method=input&debug_params=&from_search_id=&search_scene="
                val searchResponse = httpClient.get(searchUrl, headers = sodaHeaders()) ?: return@withContext null
                val searchJson = JSONObject(searchResponse)
                val resultGroups = searchJson.optJSONArray("result_groups")
                if (resultGroups == null || resultGroups.length() == 0) return@withContext null

                var firstTrack: JSONObject? = null
                for (groupIndex in 0 until resultGroups.length()) {
                    val group = resultGroups.optJSONObject(groupIndex) ?: continue
                    val data = group.optJSONArray("data") ?: continue
                    for (itemIndex in 0 until data.length()) {
                        val item = data.optJSONObject(itemIndex) ?: continue
                        val meta = item.optJSONObject("meta")
                        if (meta?.optString("item_type") != "track") continue
                        firstTrack = item.optJSONObject("entity")?.optJSONObject("track")
                        if (firstTrack != null) break
                    }
                    if (firstTrack != null) break
                }

                if (firstTrack == null) return@withContext null

                val trackId = firstTrack.optString("id", "")
                val matchedTitle = firstTrack.optString("name", "")
                val artists = firstTrack.optJSONArray("artists")
                val matchedArtist = buildString {
                    if (artists != null) {
                        for (index in 0 until artists.length()) {
                            val name = artists.optJSONObject(index)?.optString("name").orEmpty()
                            if (name.isBlank()) continue
                            if (isNotEmpty()) append("/")
                            append(name)
                        }
                    }
                }

                if (trackId.isBlank()) {
                    return@withContext OnlineLyricFetcher.LyricResult(
                        api = "SodaMusic",
                        lyrics = null,
                        parsedLines = null,
                        hasSyllable = false,
                        provider = OnlineLyricProvider.SodaMusic,
                        matchedTitle = matchedTitle,
                        matchedArtist = matchedArtist,
                        error = "无 track_id"
                    )
                }

                val detailResponse = httpClient.postForm(
                    url = "https://api.qishui.com/luna/pc/track_v2",
                    form = linkedMapOf(
                        "track_id" to trackId,
                        "media_type" to "track",
                        "queue_type" to ""
                    ),
                    headers = sodaHeaders()
                ) ?: return@withContext null

                val detailJson = JSONObject(detailResponse)
                val lyric = detailJson.optJSONObject("lyric")
                val lyricContent = lyric?.optString("content", "").orEmpty()
                val lyricType = lyric?.optString("type", "").orEmpty()

                if (lyricContent.isBlank()) {
                    return@withContext OnlineLyricFetcher.LyricResult(
                        api = "SodaMusic",
                        lyrics = null,
                        parsedLines = null,
                        hasSyllable = false,
                        provider = OnlineLyricProvider.SodaMusic,
                        matchedTitle = matchedTitle,
                        matchedArtist = matchedArtist,
                        error = "无歌词内容"
                    )
                }

                val hasSyllable = OnlineLyricParser.isWordLevelLyrics(lyricContent, lyricType)
                val parsedLines = OnlineLyricParser.parseSodaLyrics(lyricContent, lyricType)
                OnlineLyricFetcher.LyricResult(
                    api = "SodaMusic",
                    lyrics = lyricContent,
                    parsedLines = parsedLines,
                    hasSyllable = hasSyllable,
                    provider = OnlineLyricProvider.SodaMusic,
                    matchedTitle = matchedTitle,
                    matchedArtist = matchedArtist
                )
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "SodaMusic API错误: ${e.message}")
                null
            }
        }

    private fun sodaHeaders(): Map<String, String> = mapOf(
        "User-Agent" to "LunaPC/2.6.5(197449790)",
        "Referer" to "https://api.qishui.com/"
    )

    private fun String.encodeURL(): String =
        URLEncoder.encode(this, "UTF-8")
}


