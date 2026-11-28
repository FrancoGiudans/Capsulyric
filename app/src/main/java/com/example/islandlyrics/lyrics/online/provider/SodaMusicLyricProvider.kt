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
import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser
import com.example.islandlyrics.lyrics.online.selection.CandidateMatcher
import com.example.islandlyrics.lyrics.online.selection.SearchCandidate

import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.random.Random

internal class SodaMusicLyricProvider(
    private val httpClient: OnlineLyricHttpClient
) {
    suspend fun fetch(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        versionReferenceTitle: String = title
    ): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                val keyword = ProviderSearchTerm.build(title, artist, album)
                val tracks = searchAndroidTracks(keyword).ifEmpty { searchPcTracks(keyword) }
                if (tracks.isEmpty()) return@withContext null

                val candidates = tracks.map { SodaTrackCandidate(it) }
                val topCandidates = CandidateMatcher.pickTopCandidates(
                    candidates,
                    title,
                    artist,
                    album,
                    durationMs,
                    limit = 2,
                    versionReferenceTitle = versionReferenceTitle
                )
                if (topCandidates.isEmpty()) return@withContext null

                var firstErrorResult: OnlineLyricFetcher.LyricResult? = null
                for (candidate in topCandidates) {
                    val result = fetchById(candidate.providerTrackId.orEmpty(), candidate)
                    if (result != null && !result.parsedLines.isNullOrEmpty()) {
                        return@withContext result
                    }
                    if (firstErrorResult == null && result != null) {
                        firstErrorResult = result
                    }
                }
                firstErrorResult
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "SodaMusic API错误: ${e.message}")
                null
            }
        }

    private suspend fun searchAndroidTracks(keyword: String): List<JSONObject> {
        val params = linkedMapOf(
            "device_platform" to "android",
            "os" to "android",
            "ssmix" to "a",
            "cdid" to "46556f98-1720-4248-83da-62b74b60b46a",
            "channel" to "xiaomi_8478_64",
            "aid" to "386088",
            "app_name" to "luna",
            "version_code" to "100198030",
            "version_name" to "19.8.0",
            "manifest_version_code" to "100198030",
            "update_version_code" to "100198030",
            "resolution" to "1080*1920",
            "dpi" to "480",
            "device_type" to "ABR-AL80",
            "device_brand" to "HUAWEI",
            "language" to "zh",
            "os_api" to "35",
            "os_version" to "15",
            "ac" to "wifi",
            "device_model" to "ABR-AL80",
            "tz_name" to "Asia/Shanghai",
            "tz_offset" to "28800",
            "package" to "com.luna.music",
            "sim_region" to "cn",
            "iid" to SEARCH_INSTALL_ID,
            "device_id" to SEARCH_DEVICE_ID,
            "_rticket" to System.currentTimeMillis().toString(),
            "q" to keyword,
            "cursor" to "0",
            "count" to "20"
        )
        val queryString = params.entries.joinToString("&") { (k, v) -> "${k.encodeURL()}=${v.encodeURL()}" }
        val searchUrl = "https://api.qishui.com/luna/search/track?$queryString"
        val response = httpClient.get(
            searchUrl,
            headers = mapOf(
                "Accept" to "*/*",
                "User-Agent" to SEARCH_USER_AGENT
            )
        ) ?: return emptyList()
        return parseSearchTracks(response)
    }

    private suspend fun searchPcTracks(keyword: String): List<JSONObject> {
        val searchUrl = "https://api.qishui.com/luna/pc/search/track?aid=386088&app_name=&region=&geo_region=&os_region=&sim_region=&device_id=&cdid=&iid=&version_name=&version_code=&channel=&build_mode=&network_carrier=&ac=&tz_name=&resolution=&device_platform=&device_type=&os_version=&fp=&q=${keyword.encodeURL()}&cursor=&search_id=&search_method=input&debug_params=&from_search_id=&search_scene="
        val response = httpClient.get(searchUrl, headers = sodaPcHeaders()) ?: return emptyList()
        return parseSearchTracks(response)
    }

    internal fun parseSearchTracks(rawResponse: String): List<JSONObject> {
        return runCatching {
            val searchJson = JSONObject(rawResponse)
            val resultGroups = searchJson.optJSONArray("result_groups") ?: return emptyList()
            val tracks = mutableListOf<JSONObject>()
            for (groupIndex in 0 until resultGroups.length()) {
                val group = resultGroups.optJSONObject(groupIndex) ?: continue
                val data = group.optJSONArray("data") ?: continue
                for (itemIndex in 0 until data.length()) {
                    val item = data.optJSONObject(itemIndex) ?: continue
                    val meta = item.optJSONObject("meta")
                    if (meta?.optString("item_type") != "track") continue
                    item.optJSONObject("entity")?.optJSONObject("track")?.let { tracks.add(it) }
                }
            }
            tracks
        }.getOrDefault(emptyList())
    }

    suspend fun fetchById(
        trackId: String,
        candidate: SearchCandidate? = null
    ): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                val matchedTitle = candidate?.matchedTitle
                val matchedArtist = candidate?.matchedArtist
                val matchedAlbum = candidate?.matchedAlbum
                val matchedDurationMs = candidate?.matchedDurationMs
                val providerTrackId = trackId

                if (trackId.isBlank()) {
                    return@withContext OnlineLyricFetcher.LyricResult(
                        api = "SodaMusic",
                        lyrics = null,
                        parsedLines = null,
                        hasSyllable = false,
                        provider = OnlineLyricProvider.SodaMusic,
                        matchedTitle = matchedTitle,
                        matchedArtist = matchedArtist,
                        matchedAlbum = matchedAlbum,
                        matchedDurationMs = matchedDurationMs,
                        providerTrackId = providerTrackId,
                        error = "无 track_id"
                    )
                }

                val detailResponse = httpClient.get(
                    url = "https://beta-luna.douyin.com/luna/h5/seo_track?track_id=$trackId&device_platform=web",
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                        "Accept" to "application/json",
                        "Referer" to "https://api.qishui.com/"
                    )
                ) ?: return@withContext null

                val detailJson = JSONObject(detailResponse)
                val seoTrack = detailJson.optJSONObject("seo_track")
                val detailTrackObj = detailJson.optJSONObject("track") ?: seoTrack?.optJSONObject("track")
                val matched = candidate ?: detailTrackObj?.let(::SodaTrackCandidate)
                val vocalFlag = detailTrackObj?.optInt("vocal", 0)
                    ?.takeIf { it > 0 }
                    ?: (candidate as? SodaTrackCandidate)?.track?.optInt("vocal", 0)
                    ?: 0

                // vocal == 2: 纯音乐、伴奏等无人声曲目（对齐上游 SodaMusic Response.cs）
                if (vocalFlag == 2) {
                    return@withContext OnlineLyricFetcher.LyricResult(
                        api = "SodaMusic",
                        lyrics = null,
                        parsedLines = null,
                        hasSyllable = false,
                        provider = OnlineLyricProvider.SodaMusic,
                        matchedTitle = matched?.matchedTitle,
                        matchedArtist = matched?.matchedArtist,
                        matchedAlbum = matched?.matchedAlbum,
                        matchedDurationMs = matched?.matchedDurationMs,
                        providerTrackId = providerTrackId,
                        error = "纯音乐"
                    )
                }

                val lyric = detailJson.optJSONObject("lyric")
                    ?: detailJson.optJSONObject("track_player")?.optJSONObject("lyric")
                    ?: seoTrack?.optJSONObject("track_player")?.optJSONObject("lyric")
                val lyricContent = lyric?.optString("content", "").orEmpty()
                val lyricType = lyric?.optString("type", "").orEmpty()

                if (lyricContent.isBlank()) {
                    return@withContext OnlineLyricFetcher.LyricResult(
                        api = "SodaMusic",
                        lyrics = null,
                        parsedLines = null,
                        hasSyllable = false,
                        provider = OnlineLyricProvider.SodaMusic,
                        matchedTitle = matched?.matchedTitle,
                        matchedArtist = matched?.matchedArtist,
                        matchedAlbum = matched?.matchedAlbum,
                        matchedDurationMs = matched?.matchedDurationMs,
                        providerTrackId = providerTrackId,
                        error = "无歌词内容"
                    )
                }

                val parsedLines = OnlineLyricParser.parseSodaLyrics(lyricContent, lyricType)
                OnlineLyricFetcher.LyricResult(
                    api = "SodaMusic",
                    lyrics = lyricContent,
                    parsedLines = parsedLines,
                    hasSyllable = parsedLines.any { !it.syllables.isNullOrEmpty() },
                    provider = OnlineLyricProvider.SodaMusic,
                    matchedTitle = matched?.matchedTitle,
                    matchedArtist = matched?.matchedArtist,
                    matchedAlbum = matched?.matchedAlbum,
                    matchedDurationMs = matched?.matchedDurationMs,
                    providerTrackId = providerTrackId
                )
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "SodaMusic API错误: ${e.message}")
                null
            }
        }

    private fun sodaPcHeaders(): Map<String, String> = mapOf(
        "User-Agent" to "LunaPC/2.6.5(197449790)",
        "Referer" to "https://api.qishui.com/"
    )

    private fun String.encodeURL(): String =
        URLEncoder.encode(this, "UTF-8")

    private companion object {
        private const val SEARCH_USER_AGENT =
            "com.luna.music/100198030 (Linux; U; Android 15; zh_CN_#Hans; ABR-AL80; Build/V417IR;tt-ok/3.12.13.19)"
        private val SEARCH_DEVICE_ID: String = generateClientId()
        private val SEARCH_INSTALL_ID: String = generateClientId()

        private fun generateClientId(): String =
            Random.nextInt(10_000_000, 100_000_000).toString() +
                Random.nextInt(10_000_000, 100_000_000).toString()
    }

    private class SodaTrackCandidate(
        val track: JSONObject
    ) : SearchCandidate {
        override val matchedTitle: String
            get() = track.optString("name", "")

        override val matchedArtist: String
            get() = track.optJSONArray("artists")
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

        override val matchedAlbum: String?
            get() = track.optJSONObject("album")?.optString("name", "")
                .orEmpty()
                .ifBlank { track.optString("album_name", "") }
                .takeIf { it.isNotBlank() }

        override val matchedDurationMs: Long?
            get() = track.optLong("duration_ms", 0L)
                .takeIf { it > 0L }
                ?: track.optLong("duration", 0L).takeIf { it > 0L }?.times(1000L)

        override val providerTrackId: String?
            get() = track.optString("id", "").takeIf { it.isNotBlank() }
    }
}


