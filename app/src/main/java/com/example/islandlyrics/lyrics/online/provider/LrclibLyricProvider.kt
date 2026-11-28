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

import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser
import com.example.islandlyrics.lyrics.online.selection.CandidateMatcher
import com.example.islandlyrics.lyrics.online.selection.SearchCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToLong

internal class LrclibLyricProvider(
    private val httpClient: OnlineLyricHttpClient
) {
    suspend fun fetch(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        versionReferenceTitle: String = title
    ): OnlineLyricFetcher.LyricResult? = withContext(Dispatchers.IO) {
        if (title.isNotBlank()) {
            val exactUrl = buildString {
                append("https://lrclib.net/api/get?track_name=").append(title.encodeURL())
                append("&artist_name=").append(artist.encodeURL())
                if (album.isNotBlank()) append("&album_name=").append(album.encodeURL())
                if (durationMs > 0L) append("&duration=").append((durationMs / 1000.0).roundToLong())
            }
            val exactResult = fetchUrl(exactUrl)
            if (exactResult != null && !exactResult.parsedLines.isNullOrEmpty()) {
                val acceptable = CandidateMatcher.assessIdentity(
                    targetTitle = title,
                    targetArtist = artist,
                    targetAlbum = album,
                    targetDurationMs = durationMs,
                    matchedTitle = exactResult.matchedTitle,
                    matchedArtist = exactResult.matchedArtist,
                    matchedAlbum = exactResult.matchedAlbum,
                    matchedDurationMs = exactResult.matchedDurationMs,
                    versionReferenceTitle = versionReferenceTitle
                ).acceptable
                if (acceptable) {
                    return@withContext exactResult
                }
            }
        }

        // Fall back to /api/search so minor title/album punctuation or localized
        // differences (e.g. `夜に駆ける (From "THE FIRST TAKE")` on clean_title)
        // still return verifiable candidates scored by CandidateMatcher.
        searchFallback(title, artist, album, durationMs, versionReferenceTitle)
    }

    suspend fun fetchById(trackId: String): OnlineLyricFetcher.LyricResult? =
        fetchUrl("https://lrclib.net/api/get/$trackId")

    private suspend fun searchFallback(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        versionReferenceTitle: String = title
    ): OnlineLyricFetcher.LyricResult? {
        try {
            val queryUrl = if (title.isNotBlank()) {
                buildString {
                    append("https://lrclib.net/api/search?track_name=").append(title.encodeURL())
                    if (artist.isNotBlank()) append("&artist_name=").append(artist.encodeURL())
                    if (album.isNotBlank()) append("&album_name=").append(album.encodeURL())
                }
            } else {
                val term = ProviderSearchTerm.build(title, artist, album)
                if (term.isBlank()) return null
                "https://lrclib.net/api/search?q=${term.encodeURL()}"
            }
            val response = httpClient.get(queryUrl) ?: return null
            val array = JSONArray(response)
            if (array.length() == 0) return null

            val candidates = buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let { add(LrclibCandidate(it)) }
                }
            }
            val topCandidates = CandidateMatcher.pickTopCandidates(
                candidates,
                title,
                artist,
                album,
                durationMs,
                limit = 3,
                versionReferenceTitle = versionReferenceTitle
            )
            for (candidate in topCandidates) {
                val parsed = parseTrackObject(candidate.json)
                if (parsed != null && !parsed.parsedLines.isNullOrEmpty()) {
                    return parsed
                }
            }
            return null
        } catch (e: Exception) {
            AppLogger.getInstance().log("OnlineLyric", "LRCLIB搜索错误: ${e.message}")
            return null
        }
    }

    private suspend fun fetchUrl(url: String): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                val response = httpClient.get(url) ?: return@withContext null
                parseTrackObject(JSONObject(response))
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "LRCLIB错误: ${e.message}")
                null
            }
        }

    private fun parseTrackObject(json: JSONObject): OnlineLyricFetcher.LyricResult? {
        val matchedDurationMs = json.optDouble("duration", 0.0)
            .takeIf { !it.isNaN() && it > 0.0 }
            ?.let { (it * 1000.0).roundToLong() }

        if (json.optBoolean("instrumental", false)) {
            return OnlineLyricFetcher.LyricResult(
                api = "LRCLIB",
                lyrics = null,
                parsedLines = null,
                hasSyllable = false,
                provider = OnlineLyricProvider.Lrclib,
                matchedTitle = json.optString("trackName"),
                matchedArtist = json.optString("artistName"),
                matchedAlbum = json.optString("albumName").takeIf { it.isNotBlank() },
                matchedDurationMs = matchedDurationMs,
                providerTrackId = json.optString("id").takeIf { it.isNotBlank() },
                error = "纯音乐"
            )
        }

        val synced = if (json.isNull("syncedLyrics")) "" else json.optString("syncedLyrics", "")
        val plain = if (json.isNull("plainLyrics")) "" else json.optString("plainLyrics", "")
        val lyricContent = synced.ifBlank { plain }
        if (lyricContent.isBlank()) return null

        val parsedLines = if (synced.isNotBlank()) {
            OnlineLyricParser.parseLrcLyrics(synced)
        } else {
            emptyList()
        }
        return OnlineLyricFetcher.LyricResult(
            api = "LRCLIB",
            lyrics = lyricContent,
            parsedLines = parsedLines,
            hasSyllable = parsedLines.any { !it.syllables.isNullOrEmpty() },
            provider = OnlineLyricProvider.Lrclib,
            matchedTitle = json.optString("trackName"),
            matchedArtist = json.optString("artistName"),
            matchedAlbum = json.optString("albumName").takeIf { it.isNotBlank() },
            matchedDurationMs = matchedDurationMs,
            providerTrackId = json.optString("id").takeIf { it.isNotBlank() }
        )
    }

    private class LrclibCandidate(
        val json: JSONObject
    ) : SearchCandidate {
        override val matchedTitle: String
            get() = json.optString("trackName", "")

        override val matchedArtist: String
            get() = json.optString("artistName", "")

        override val matchedAlbum: String?
            get() = json.optString("albumName", "").takeIf { it.isNotBlank() }

        override val matchedDurationMs: Long?
            get() = json.optDouble("duration", 0.0)
                .takeIf { !it.isNaN() && it > 0.0 }
                ?.let { (it * 1000.0).roundToLong() }

        override val providerTrackId: String?
            get() = json.optString("id", "").takeIf { it.isNotBlank() }
    }

    private fun String.encodeURL(): String =
        URLEncoder.encode(this, "UTF-8")
}
