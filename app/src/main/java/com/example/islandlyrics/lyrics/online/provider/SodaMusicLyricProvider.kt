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
import com.example.islandlyrics.lyrics.online.selection.CandidateMatcher
import com.example.islandlyrics.lyrics.online.selection.SearchCandidate

import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

internal class SodaMusicLyricProvider(
    private val httpClient: OnlineLyricHttpClient
) {
    suspend fun fetch(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L
    ): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                val keyword = ProviderSearchTerm.build(title, artist, album)
                val searchUrl = "https://api.qishui.com/luna/pc/search/track?aid=386088&app_name=&region=&geo_region=&os_region=&sim_region=&device_id=&cdid=&iid=&version_name=&version_code=&channel=&build_mode=&network_carrier=&ac=&tz_name=&resolution=&device_platform=&device_type=&os_version=&fp=&q=${keyword.encodeURL()}&cursor=&search_id=&search_method=input&debug_params=&from_search_id=&search_scene="
                val searchResponse = httpClient.get(searchUrl, headers = sodaHeaders()) ?: return@withContext null
                val searchJson = JSONObject(searchResponse)
                val resultGroups = searchJson.optJSONArray("result_groups")
                if (resultGroups == null || resultGroups.length() == 0) return@withContext null

                var tracks: List<JSONObject> = emptyList()
                for (groupIndex in 0 until resultGroups.length()) {
                    val group = resultGroups.optJSONObject(groupIndex) ?: continue
                    val data = group.optJSONArray("data") ?: continue
                    val groupTracks = mutableListOf<JSONObject>()
                    for (itemIndex in 0 until data.length()) {
                        val item = data.optJSONObject(itemIndex) ?: continue
                        val meta = item.optJSONObject("meta")
                        if (meta?.optString("item_type") != "track") continue
                        item.optJSONObject("entity")?.optJSONObject("track")?.let { groupTracks.add(it) }
                    }
                    if (groupTracks.isNotEmpty()) {
                        tracks = groupTracks
                        break
                    }
                }
                if (tracks.isEmpty()) return@withContext null

                val candidates = tracks.map { SodaTrackCandidate(it) }
                val best = CandidateMatcher.pickBest(candidates, title, artist, album, durationMs)
                    ?: return@withContext null
                fetchById(best.providerTrackId.orEmpty(), best)
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "SodaMusic API错误: ${e.message}")
                null
            }
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
                        matchedAlbum = matchedAlbum,
                        matchedDurationMs = matchedDurationMs,
                        providerTrackId = providerTrackId,
                        error = "无歌词内容"
                    )
                }

                val hasSyllable = OnlineLyricParser.isWordLevelLyrics(lyricContent, lyricType)
                val parsedLines = OnlineLyricParser.parseSodaLyrics(lyricContent, lyricType)
                val matched = candidate ?: detailJson.optJSONObject("seo_track")?.optJSONObject("track")
                    ?.let(::SodaTrackCandidate)
                OnlineLyricFetcher.LyricResult(
                    api = "SodaMusic",
                    lyrics = lyricContent,
                    parsedLines = parsedLines,
                    hasSyllable = hasSyllable,
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

    private fun sodaHeaders(): Map<String, String> = mapOf(
        "User-Agent" to "LunaPC/2.6.5(197449790)",
        "Referer" to "https://api.qishui.com/"
    )

    private fun String.encodeURL(): String =
        URLEncoder.encode(this, "UTF-8")

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


