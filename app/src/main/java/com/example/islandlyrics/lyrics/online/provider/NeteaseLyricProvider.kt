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

// Ported from Lyricify-Lyrics-Helper (C# → Kotlin)
// (https://github.com/WXRIW/Lyricify-Lyrics-Helper)
// Copyright (C) WXRIW/Lyricify-Lyrics-Helper contributors
// Licensed under Apache-2.0

package com.example.islandlyrics.lyrics.online.provider

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.crypto.NeteaseEapiCrypto
import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser
import com.example.islandlyrics.lyrics.online.parser.YrcParser
import com.example.islandlyrics.lyrics.online.selection.CandidateMatcher
import com.example.islandlyrics.lyrics.online.selection.SearchCandidate

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

                val candidates = buildList {
                    for (index in 0 until songs.length()) {
                        songs.optJSONObject(index)?.let { add(NeteaseSongCandidate(it)) }
                    }
                }
                val best = CandidateMatcher.pickBest(candidates, title, artist)
                    ?: return@withContext null
                val firstSong = best.song
                val matchedTitle = best.matchedTitle
                val matchedArtist = best.matchedArtist
                val songId = firstSong.optLong("id", 0)

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
                val yrcContent = lyricJson.optLyricText("yrc")
                val translationContent = lyricJson.optLyricText("tlyric")
                    .ifBlank { lyricJson.optLyricText("ytlrc") }
                val romanContent = lyricJson.optLyricText("romalrc")
                    .ifBlank { lyricJson.optLyricText("yromalrc") }

                if (lyricContent.isEmpty()) {
                    return@withContext null
                }

                // YRC 逐字优先：eapi 返回的 yrc 字段若为逐字形态则用逐字解析
                val hasSyllable = yrcContent.isNotBlank() && YrcParser.isYrcContent(yrcContent)
                val parsedLines = if (hasSyllable) {
                    OnlineLyricParser.parseYrcLyrics(yrcContent)
                } else {
                    OnlineLyricParser.parseLrcLyrics(lyricContent)
                }
                OnlineLyricFetcher.LyricResult(
                    api = "Netease",
                    lyrics = if (hasSyllable) yrcContent else lyricContent,
                    parsedLines = parsedLines,
                    hasSyllable = hasSyllable,
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
                "yv" to "1",
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

    private class NeteaseSongCandidate(
        val song: JSONObject
    ) : SearchCandidate {
        override val matchedTitle: String
            get() = song.optString("name", "")

        override val matchedArtist: String
            get() = song.optJSONArray("artists")
                ?.let { artists ->
                    buildString {
                        for (index in 0 until artists.length()) {
                            val name = artists.optJSONObject(index)?.optString("name").orEmpty()
                            if (name.isBlank()) continue
                            if (isNotEmpty()) append("/")
                            append(name)
                        }
                    }
                }
                .orEmpty()
    }

    private companion object {
        private const val NETEASE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        private const val NETEASE_EAPI_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; PCT-AL10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.64 HuaweiBrowser/10.0.3.311 Mobile Safari/537.36"
    }
}


