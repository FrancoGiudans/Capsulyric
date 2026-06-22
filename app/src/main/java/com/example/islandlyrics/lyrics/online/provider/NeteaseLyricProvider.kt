package com.example.islandlyrics.lyrics.online.provider

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.crypto.NeteaseEapiCrypto
import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser

import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

internal class NeteaseLyricProvider(
    private val httpClient: OnlineLyricHttpClient
) {
    suspend fun fetch(title: String, artist: String): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                val keywords = "$title $artist"
                val searchUrl = "https://music.163.com/api/search/get?s=${keywords.encodeURL()}&type=1&limit=10"
                val searchResponse = httpClient.get(searchUrl) ?: return@withContext null

                val searchJson = JSONObject(searchResponse)
                val result = searchJson.optJSONObject("result")
                val songs = result?.optJSONArray("songs")
                if (songs == null || songs.length() == 0) {
                    return@withContext null
                }

                val firstSong = songs.getJSONObject(0)
                val matchedTitle = firstSong.optString("name", "")
                val songId = firstSong.optLong("id", 0)
                val artists = firstSong.optJSONArray("artists")
                val matchedArtist = if (artists != null && artists.length() > 0) {
                    artists.getJSONObject(0).optString("name", "")
                } else {
                    ""
                }

                if (songId == 0L) {
                    return@withContext null
                }

                val lyricResponse = fetchLyricV1(songId)
                    ?: httpClient.get(
                        url = "https://music.163.com/api/song/lyric?id=$songId&lv=-1&tv=-1&rv=-1&kv=-1&yv=-1",
                        headers = neteaseHeaders()
                    )
                    ?: return@withContext null

                val lyricJson = JSONObject(lyricResponse)
                val lyricContent = lyricJson.optLyricText("lrc")
                val translationContent = lyricJson.optLyricText("tlyric")
                    .ifBlank { lyricJson.optLyricText("ytlrc") }
                val romanContent = lyricJson.optLyricText("romalrc")
                    .ifBlank { lyricJson.optLyricText("yromalrc") }

                if (lyricContent.isEmpty()) {
                    return@withContext null
                }

                val parsedLines = OnlineLyricParser.parseLrcLyrics(lyricContent)
                OnlineLyricFetcher.LyricResult(
                    api = "Netease",
                    lyrics = lyricContent,
                    parsedLines = parsedLines,
                    hasSyllable = false,
                    provider = OnlineLyricProvider.Netease,
                    matchedTitle = matchedTitle,
                    matchedArtist = matchedArtist,
                    translationLyrics = translationContent.takeIf { it.isNotBlank() },
                    romanLyrics = romanContent.takeIf { it.isNotBlank() }
                )
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "Netease API错误: ${e.message}")
                null
            }
        }

    private fun fetchLyricV1(songId: Long): String? {
        return executeEapi(
            url = "https://interface3.music.163.com/eapi/song/lyric/v1",
            data = linkedMapOf(
                "id" to songId.toString(),
                "cp" to "false",
                "lv" to "0",
                "kv" to "0",
                "tv" to "0",
                "rv" to "0",
                "yv" to "0",
                "ytv" to "0",
                "yrv" to "0",
                "csrf_token" to ""
            )
        )
    }

    private fun executeEapi(url: String, data: LinkedHashMap<String, String>): String? {
        val header = linkedMapOf(
            "__csrf" to "",
            "appver" to "8.0.0",
            "buildver" to (System.currentTimeMillis() / 1000L).toString(),
            "channel" to "",
            "deviceId" to "",
            "mobilename" to "",
            "resolution" to "1920x1080",
            "os" to "android",
            "osver" to "",
            "requestId" to "${System.currentTimeMillis()}_${(0..999).random().toString().padStart(4, '0')}",
            "versioncode" to "140",
            "MUSIC_U" to ""
        )
        val payload = LinkedHashMap(data)
        payload["header"] = JSONObject(header as Map<*, *>).toString()

        return httpClient.postFormBlocking(
            url = url,
            form = mapOf(
                "params" to NeteaseEapiCrypto.buildParams(url, JSONObject(payload as Map<*, *>).toString())
            ),
            headers = mapOf(
                "Referer" to "https://music.163.com/",
                "User-Agent" to NETEASE_EAPI_USER_AGENT,
                "Cookie" to header.entries.joinToString("; ") { "${it.key}=${it.value}" }
            )
        ).also {
            if (it == null) {
                AppLogger.getInstance().d("OnlineLyric", "Netease eapi failed")
            }
        }
    }

    private fun neteaseHeaders(): Map<String, String> = mapOf(
        "User-Agent" to NETEASE_USER_AGENT,
        "Referer" to "https://music.163.com/"
    )

    private fun JSONObject.optLyricText(key: String): String {
        return optJSONObject(key)?.optString("lyric", "").orEmpty()
    }

    private fun String.encodeURL(): String =
        URLEncoder.encode(this, "UTF-8")

    private companion object {
        private const val NETEASE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        private const val NETEASE_EAPI_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; PCT-AL10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.64 HuaweiBrowser/10.0.3.311 Mobile Safari/537.36"
    }
}


