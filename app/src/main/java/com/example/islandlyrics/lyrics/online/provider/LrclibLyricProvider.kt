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
        fetchUrl("https://lrclib.net/api/get?track_name=${title.encodeURL()}&artist_name=${artist.encodeURL()}")

    suspend fun fetchById(trackId: String): OnlineLyricFetcher.LyricResult? =
        fetchUrl("https://lrclib.net/api/get/$trackId")

    private suspend fun fetchUrl(url: String): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
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
                        providerTrackId = json.optString("id").takeIf { it.isNotBlank() },
                        error = "纯音乐"
                    )
                }

                val synced = if (json.isNull("syncedLyrics")) "" else json.optString("syncedLyrics", "")
                val plain = if (json.isNull("plainLyrics")) "" else json.optString("plainLyrics", "")
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
                    matchedArtist = json.optString("artistName"),
                    matchedAlbum = json.optString("albumName").takeIf { it.isNotBlank() },
                    providerTrackId = json.optString("id").takeIf { it.isNotBlank() }
                )
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "LRCLIB错误: ${e.message}")
                null
            }
        }

    private fun String.encodeURL(): String =
        URLEncoder.encode(this, "UTF-8")
}


