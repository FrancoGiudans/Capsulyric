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

import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.parser.MusixmatchRichsyncParser
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser
import com.example.islandlyrics.lyrics.online.selection.CandidateMatcher
import com.example.islandlyrics.lyrics.online.selection.SearchCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

/**
 * Musixmatch 歌词提供者（对齐 Lyricify-Lyrics-Helper v0.2.1 Android API）。
 *
 * - 默认采用 Android 客户端端点（`apic.musixmatch.com/ws/1.1/`，`android-player-v1.0`），
 *   相较桌面端大幅降低验证码风控触发概率；
 * - 使用互斥锁 + 250ms 最小请求间隔避免并发瞬时限流；
 * - 搜索优先走 `track.search` 多候选列表并由 [CandidateMatcher] 严格筛选，空结果回退 `matcher.track.get`；
 * - 歌词走 `macro.subtitles.get`（richsync 逐字优先，回退 `subtitle_list` / `subtitle` 普通 LRC）。
 */
internal class MusixmatchLyricProvider(
    private val httpClient: OnlineLyricHttpClient,
    private val tokenStore: MusixmatchTokenStore? = null
) {
    init {
        if (!isUsableToken(userToken)) {
            val stored = tokenStore?.load()
            userToken = if (isUsableToken(stored)) stored else null
        }
    }

    suspend fun fetch(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        versionReferenceTitle: String = title
    ): OnlineLyricFetcher.LyricResult? =
        fetchTrack(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            versionReferenceTitle = versionReferenceTitle
        )

    suspend fun fetchById(trackId: String): OnlineLyricFetcher.LyricResult? =
        fetchTrack("", "", "", 0L, directTrackId = trackId)

    private suspend fun fetchTrack(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        directTrackId: String? = null,
        versionReferenceTitle: String = title
    ): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                // 1. 搜索候选曲目（优先 track.search 多候选，回退 matcher.track.get）
                val candidatesToTry: List<Pair<String, MusixmatchTrackCandidate?>> = if (directTrackId == null) {
                    val searched = searchTrackCandidates(title, artist, album, durationMs)
                    val matchedFromSearch = CandidateMatcher.pickTopCandidates(
                        candidates = searched,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        limit = 2,
                        versionReferenceTitle = versionReferenceTitle
                    )
                    val selectedCandidates = matchedFromSearch.ifEmpty {
                        fetchMatcherCandidate(title, artist, album, durationMs)?.let { single ->
                            CandidateMatcher.pickTopCandidates(
                                candidates = listOf(single),
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = durationMs,
                                limit = 1,
                                versionReferenceTitle = versionReferenceTitle
                            )
                        }.orEmpty()
                    }
                    selectedCandidates.mapNotNull { candidate ->
                        candidate.providerTrackId?.takeIf { it.isNotBlank() }?.let { it to candidate }
                    }
                } else {
                    listOf(directTrackId to null)
                }
                if (candidatesToTry.isEmpty()) return@withContext null

                var firstErrorResult: OnlineLyricFetcher.LyricResult? = null
                for ((trackId, candidate) in candidatesToTry) {
                    val result = fetchLyricsForTrack(trackId, candidate)
                    if (result != null && !result.parsedLines.isNullOrEmpty()) {
                        return@withContext result
                    }
                    if (firstErrorResult == null && result != null) {
                        firstErrorResult = result
                    }
                }
                firstErrorResult
            } catch (e: MusixmatchCaptchaException) {
                AppLogger.getInstance().w("OnlineLyric", "Musixmatch captcha: ${e.message}")
                OnlineLyricFetcher.LyricResult(
                    api = "Musixmatch",
                    lyrics = null,
                    parsedLines = null,
                    hasSyllable = false,
                    provider = OnlineLyricProvider.Musixmatch,
                    error = "Musixmatch 需过验证码，请稍后重试"
                )
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "Musixmatch API错误: ${e.message}")
                null
            }
        }

    private suspend fun searchTrackCandidates(
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): List<MusixmatchTrackCandidate> {
        val params = mutableListOf(
            "page_size=10",
            "page=1",
            "s_track_rating=desc"
        )
        if (title.isNotBlank()) {
            params += "q_track=${title.encodeURL()}"
        } else {
            val keyword = ProviderSearchTerm.build(title, artist, album)
            if (keyword.isNotBlank()) params += "q=${keyword.encodeURL()}"
        }
        if (artist.isNotBlank()) params += "q_artist=${artist.encodeURL()}"
        if (durationMs > 0L) params += "q_duration=${durationMs / 1000L}"

        val response = sendApiRequest("track.search?${params.joinToString("&")}") ?: return emptyList()
        return parseSearchCandidates(response)
    }

    private suspend fun fetchMatcherCandidate(
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): MusixmatchTrackCandidate? {
        if (title.isBlank() && artist.isBlank()) return null
        val query = buildString {
            append("matcher.track.get")
            append("?q_track=").append(title.encodeURL())
            append("&q_artist=").append(artist.encodeURL())
            if (album.isNotBlank()) append("&q_album=").append(album.encodeURL())
            if (durationMs > 0L) append("&f_track_length=").append(durationMs / 1000L)
        }
        val response = sendApiRequest(query) ?: return null
        val track = response
            .optJSONObject("message")
            ?.optJSONObject("body")
            ?.optJSONObject("track")
            ?: return null
        return MusixmatchTrackCandidate(track).takeIf { !it.providerTrackId.isNullOrBlank() }
    }

    private suspend fun fetchLyricsForTrack(
        trackId: String,
        candidate: MusixmatchTrackCandidate?
    ): OnlineLyricFetcher.LyricResult? {
        val lyricResponse = sendApiRequest(
            "macro.subtitles.get" +
                "?namespace=lyrics_richsynched" +
                "&optional_calls=track.richsync" +
                "&subtitle_format=lrc" +
                "&track_id=${trackId.encodeURL()}" +
                "&f_subtitle_length_max_deviation=40"
        ) ?: return null

        val macroCalls = lyricResponse
            .optJSONObject("message")
            ?.optJSONObject("body")
            ?.optJSONObject("macro_calls")
            ?: return null

        val matchedTrackObj = macroCalls
            .optJSONObject("matcher.track.get")
            ?.optJSONObject("message")
            ?.optJSONObject("body")
            ?.optJSONObject("track")
        val resolvedCandidate = candidate ?: matchedTrackObj?.let(::MusixmatchTrackCandidate)
        val matchedTitle = resolvedCandidate?.matchedTitle?.takeIf { it.isNotBlank() }
        val matchedArtist = resolvedCandidate?.matchedArtist?.takeIf { it.isNotBlank() }
        val matchedAlbum = resolvedCandidate?.matchedAlbum
        val matchedDurationMs = resolvedCandidate?.matchedDurationMs
        val isrc = resolvedCandidate?.isrc

        val isInstrumental = (resolvedCandidate?.track ?: matchedTrackObj)?.optInt("instrumental", 0) == 1
        if (isInstrumental) {
            return OnlineLyricFetcher.LyricResult(
                api = "Musixmatch",
                lyrics = null,
                parsedLines = null,
                hasSyllable = false,
                provider = OnlineLyricProvider.Musixmatch,
                matchedTitle = matchedTitle,
                matchedArtist = matchedArtist,
                matchedAlbum = matchedAlbum,
                matchedDurationMs = matchedDurationMs,
                providerTrackId = trackId,
                isrc = isrc,
                error = "纯音乐"
            )
        }

        // 2a. richsync 逐字（需校验子调用 status_code == 200）
        val richsyncCall = macroCalls.optJSONObject("track.richsync.get")
        if (isCallStatusOk(richsyncCall)) {
            val richsyncBody = richsyncCall
                ?.optJSONObject("message")
                ?.optJSONObject("body")
                ?.optJSONObject("richsync")
                ?.optString("richsync_body", "")
                .orEmpty()
            if (richsyncBody.isNotBlank()) {
                val parsedLines = MusixmatchRichsyncParser.parseRichsync(richsyncBody)
                if (parsedLines.isNotEmpty()) {
                    return OnlineLyricFetcher.LyricResult(
                        api = "Musixmatch",
                        lyrics = richsyncBody,
                        parsedLines = parsedLines,
                        hasSyllable = true,
                        provider = OnlineLyricProvider.Musixmatch,
                        matchedTitle = matchedTitle,
                        matchedArtist = matchedArtist,
                        matchedAlbum = matchedAlbum,
                        matchedDurationMs = matchedDurationMs,
                        providerTrackId = trackId,
                        isrc = isrc
                    )
                }
            }
        }

        // 2b. 普通 LRC 字幕（兼容 subtitle_list[0].subtitle 与直接 subtitle 两种响应结构）
        val subtitleBody = extractSubtitleBody(macroCalls.optJSONObject("track.subtitles.get"))
        if (subtitleBody.isNotBlank()) {
            val parsedLines = OnlineLyricParser.parseLrcLyrics(subtitleBody)
            if (parsedLines.isNotEmpty()) {
                return OnlineLyricFetcher.LyricResult(
                    api = "Musixmatch",
                    lyrics = subtitleBody,
                    parsedLines = parsedLines,
                    hasSyllable = parsedLines.any { !it.syllables.isNullOrEmpty() },
                    provider = OnlineLyricProvider.Musixmatch,
                    matchedTitle = matchedTitle,
                    matchedArtist = matchedArtist,
                    matchedAlbum = matchedAlbum,
                    matchedDurationMs = matchedDurationMs,
                    providerTrackId = trackId,
                    isrc = isrc
                )
            }
        }

        return null
    }

    /** Musixmatch GET 请求，带互斥节流与 renew/captcha 状态机。 */
    private suspend fun sendApiRequest(req: String): JSONObject? {
        for (attempt in 0 until REQUEST_RETRY_COUNT) {
            val token = ensureUserToken()
            val separator = if ('?' in req) '&' else '?'
            val url = API_BASE_URL +
                req +
                separator +
                "usertoken=${token.encodeURL()}" +
                "&format=json" +
                "&app_id=${APP_ID.encodeURL()}" +
                "&t=${newRequestId()}"

            val httpResult = pacedGet(url)
            if (httpResult != null && httpResult.body.isNotBlank()) {
                val json = runCatching { JSONObject(httpResult.body) }.getOrNull()
                val header = json?.optJSONObject("message")?.optJSONObject("header")
                val statusCode = header?.optInt("status_code", 0) ?: 0
                val hint = header?.optString("hint", "").orEmpty()

                if (statusCode == 404) return json
                if (httpResult.statusCode in 200..299 && statusCode == 200) return json

                if (statusCode == 401 && hint.equals("renew", ignoreCase = true)) {
                    invalidateToken()
                } else if (statusCode == 401 && hint.equals("captcha", ignoreCase = true)) {
                    throw MusixmatchCaptchaException()
                }
            }

            if (attempt + 1 < REQUEST_RETRY_COUNT) {
                delay(minOf(500L * (1 shl attempt), 2_000L))
            }
        }
        return null
    }

    private suspend fun ensureUserToken(): String {
        userToken?.takeIf(::isUsableToken)?.let { return it }
        return tokenMutex.withLock {
            userToken?.takeIf(::isUsableToken)?.let { return@withLock it }
            refreshUserTokenLocked()
        }
    }

    private suspend fun refreshUserTokenLocked(): String {
        var tokenResult = requestToken()
        var maxTry = 3
        while (tokenResult?.statusCode == 401 &&
            tokenResult.hint.equals("captcha", ignoreCase = true) &&
            maxTry-- > 0
        ) {
            delay(1_000L)
            tokenResult = requestToken()
        }
        if (tokenResult?.statusCode == 401 && tokenResult.hint.equals("captcha", ignoreCase = true)) {
            throw MusixmatchCaptchaException()
        }
        val token = tokenResult?.userToken?.takeIf(::isUsableToken)
            ?: throw MusixmatchCaptchaException("User Token failed to refresh")
        userToken = token
        tokenStore?.save(token)
        return token
    }

    private suspend fun requestToken(): TokenResult? {
        val httpResult = pacedGet(
            API_BASE_URL +
                "token.get?user_language=en" +
                "&app_id=${APP_ID.encodeURL()}" +
                "&t=${newRequestId()}"
        ) ?: return null
        if (httpResult.statusCode !in 200..299 || httpResult.body.isBlank()) return null

        return runCatching {
            val json = JSONObject(httpResult.body)
            val header = json.optJSONObject("message")?.optJSONObject("header")
            TokenResult(
                statusCode = header?.optInt("status_code", 0) ?: 0,
                hint = header?.optString("hint", "").orEmpty(),
                userToken = json.optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optString("user_token", "")
                    .orEmpty()
            )
        }.getOrNull()
    }

    private suspend fun pacedGet(url: String) = requestMutex.withLock {
        val elapsed = System.currentTimeMillis() - lastRequestAtMs
        if (elapsed in 0 until MIN_REQUEST_INTERVAL_MS) {
            delay(MIN_REQUEST_INTERVAL_MS - elapsed)
        }
        httpClient.getDetailed(url, headers = musixmatchHeaders()).also {
            lastRequestAtMs = System.currentTimeMillis()
        }
    }

    private fun invalidateToken() {
        userToken = null
        tokenStore?.clear()
    }

    private data class TokenResult(
        val statusCode: Int,
        val hint: String,
        val userToken: String
    )

    internal class MusixmatchTrackCandidate(
        val track: JSONObject
    ) : SearchCandidate {
        override val matchedTitle: String
            get() = track.optString("track_name", "")

        override val matchedArtist: String
            get() = track.optString("artist_name", "")

        override val matchedAlbum: String?
            get() = track.optString("album_name", "").takeIf { it.isNotBlank() }

        override val matchedDurationMs: Long?
            get() = track.optLong("track_length", 0L)
                .takeIf { it > 0L }
                ?.times(1000L)

        override val providerTrackId: String?
            get() = track.optString("track_id", "")
                .ifBlank { track.optLong("track_id", 0L).takeIf { it > 0L }?.toString().orEmpty() }
                .takeIf { it.isNotBlank() }

        override val isrc: String?
            get() = track.optString("track_isrc", "").takeIf { it.isNotBlank() }
    }

    private fun String.encodeURL(): String =
        URLEncoder.encode(this, "UTF-8")

    internal companion object {
        private const val REQUEST_RETRY_COUNT = 3
        private const val MIN_REQUEST_INTERVAL_MS = 250L
        private const val API_BASE_URL = "https://apic.musixmatch.com/ws/1.1/"
        private const val APP_ID = "android-player-v1.0"
        private const val USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 13)"
        private const val COOKIE = "AWSELB=0; AWSELBCORS=0"

        private val requestMutex = Mutex()
        private val tokenMutex = Mutex()

        @Volatile
        private var lastRequestAtMs: Long = 0L

        @Volatile
        var userToken: String? = null

        fun musixmatchHeaders(): Map<String, String> = mapOf(
            "User-Agent" to USER_AGENT,
            "Cookie" to COOKIE
        )

        internal fun isUsableToken(token: String?): Boolean =
            !token.isNullOrBlank() &&
                !token.equals("null", ignoreCase = true) &&
                token.any { it != '0' }

        private fun newRequestId(): String =
            UUID.randomUUID().toString().replace("-", "")

        internal fun parseSearchCandidates(response: JSONObject): List<MusixmatchTrackCandidate> {
            val list = response
                .optJSONObject("message")
                ?.optJSONObject("body")
                ?.optJSONArray("track_list")
                ?: return emptyList()
            return buildList {
                for (i in 0 until list.length()) {
                    val track = list.optJSONObject(i)?.optJSONObject("track") ?: continue
                    val candidate = MusixmatchTrackCandidate(track)
                    if (!candidate.providerTrackId.isNullOrBlank()) {
                        add(candidate)
                    }
                }
            }
        }

        internal fun isCallStatusOk(callObj: JSONObject?): Boolean {
            val header = callObj?.optJSONObject("message")?.optJSONObject("header") ?: return false
            return header.optInt("status_code", 0) == 200
        }

        internal fun extractSubtitleBody(subtitlesCall: JSONObject?): String {
            if (!isCallStatusOk(subtitlesCall)) return ""
            val body = subtitlesCall
                ?.optJSONObject("message")
                ?.optJSONObject("body")
                ?: return ""
            val fromList = body.optJSONArray("subtitle_list")
                ?.optJSONObject(0)
                ?.optJSONObject("subtitle")
                ?.optString("subtitle_body", "")
                .orEmpty()
            if (fromList.isNotBlank()) return fromList
            return body.optJSONObject("subtitle")
                ?.optString("subtitle_body", "")
                .orEmpty()
        }
    }
}

/** Musixmatch 触发验证码（401 + hint=captcha）达到重试上限。 */
internal class MusixmatchCaptchaException(message: String = "Musixmatch captcha") : Exception(message)
