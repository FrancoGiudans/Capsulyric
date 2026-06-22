package com.example.islandlyrics.lyrics.online.provider

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser

import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

internal class LrclibLyricProvider(
    private val httpClient: OnlineLyricHttpClient
) {
    suspend fun fetch(title: String, artist: String): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://lrclib.net/api/get?track_name=${title.encodeURL()}&artist_name=${artist.encodeURL()}"
                val response = httpClient.get(url) ?: return@withContext null
                val json = JSONObject(response)

                if (json.optBoolean("instrumental", false)) {
                    return@withContext OnlineLyricFetcher.LyricResult(
                        api = "LRCLIB",
                        lyrics = null,
                        parsedLines = null,
                        hasSyllable = false,
                        provider = OnlineLyricProvider.Lrclib,
                        matchedTitle = json.optString("trackName"),
                        matchedArtist = json.optString("artistName"),
                        error = "纯音乐"
                    )
                }

                val synced = json.optString("syncedLyrics", "")
                val plain = json.optString("plainLyrics", "")
                val lyricContent = synced.ifBlank { plain }
                if (lyricContent.isBlank()) return@withContext null

                val parsedLines = if (synced.isNotBlank()) {
                    OnlineLyricParser.parseLrcLyrics(synced)
                } else {
                    emptyList()
                }
                OnlineLyricFetcher.LyricResult(
                    api = "LRCLIB",
                    lyrics = lyricContent,
                    parsedLines = parsedLines,
                    hasSyllable = false,
                    provider = OnlineLyricProvider.Lrclib,
                    matchedTitle = json.optString("trackName"),
                    matchedArtist = json.optString("artistName")
                )
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "LRCLIB错误: ${e.message}")
                null
            }
        }

    private fun String.encodeURL(): String =
        URLEncoder.encode(this, "UTF-8")
}


