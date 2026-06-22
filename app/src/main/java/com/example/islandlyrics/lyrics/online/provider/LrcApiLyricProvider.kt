package com.example.islandlyrics.lyrics.online.provider

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser

import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

internal class LrcApiLyricProvider(
    private val httpClient: OnlineLyricHttpClient
) {
    suspend fun fetch(title: String, artist: String): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.lrc.cx/lyrics?title=${title.encodeURL()}&artist=${artist.encodeURL()}"
                val response = httpClient.get(
                    url = url,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                ) ?: return@withContext null

                if (response.trim().startsWith("{")) {
                    try {
                        val json = JSONObject(response)
                        if (json.has("detail")) {
                            AppLogger.getInstance().log("OnlineLyric", "LrcApi未找到: ${json.optString("detail")}")
                            return@withContext null
                        }
                    } catch (_: Exception) {
                    }
                }

                if (response.isEmpty() || response.contains("Lyrics not found")) {
                    return@withContext null
                }

                val hasSyllable = OnlineLyricParser.isWordLevelLyrics(response)
                val parsedLines = if (hasSyllable) {
                    OnlineLyricParser.parseWordLevelLyrics(response)
                } else {
                    OnlineLyricParser.parseLrcLyrics(response)
                }

                OnlineLyricFetcher.LyricResult(
                    api = "LrcApi",
                    lyrics = response,
                    parsedLines = parsedLines,
                    hasSyllable = hasSyllable,
                    provider = OnlineLyricProvider.LrcApi
                )
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "LrcApi错误: ${e.message}")
                null
            }
        }

    private fun String.encodeURL(): String =
        URLEncoder.encode(this, "UTF-8")
}


