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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Musixmatch 歌词提供者。
 *
 * token 自动获取（`token.get?app_id=web-desktop-app-v1.0`，伪装桌面客户端），
 * 并跨进程持久化复用，降低验证码触发概率；
 * 歌词走 `macro.subtitles.get`（richsync 逐字优先，回退 LRC 字幕）。
 * 应用只支持有时间轴的歌词，纯文本（无时间轴）无法展示，故不做回退。
 *
 * captcha 反爬应对（照搬参考实现）：
 * - 401 + `hint:renew` → 清 token 重取
 * - 401 + `hint:captcha` → 延迟 1s 重试（最多约 3 次）
 * - 仍失败 → 抛 [MusixmatchCaptchaException]，由本类转成 error 结果
 *   （不抛出 async 块，不影响其他源选择）
 */
internal class MusixmatchLyricProvider(
    private val httpClient: OnlineLyricHttpClient,
    private val tokenStore: MusixmatchTokenStore? = null
) {
    init {
        // 复用上次会话的 token，避免冷启动就触发 token.get 验证码
        if (userToken.isNullOrBlank()) {
            userToken = tokenStore?.load()
        }
    }

    suspend fun fetch(title: String, artist: String): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                // 1. 搜索曲目
                val trackResponse = musixmatchGet(
                    "matcher.track.get" +
                        "?q_track=${title.encodeURL()}" +
                        "&q_artist=${artist.encodeURL()}"
                ) ?: return@withContext null

                val track = JSONObject(trackResponse)
                    .optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optJSONObject("track")
                    ?: return@withContext null
                val trackId = track.optString("track_id", "")
                if (trackId.isBlank()) return@withContext null
                val matchedTitle = track.optString("track_name", "")
                val matchedArtist = track.optString("artist_name", "")

                // 2. 取歌词（richsync 优先）
                val lyricResponse = musixmatchGet(
                    "macro.subtitles.get" +
                        "?namespace=lyrics_richsynched" +
                        "&optional_calls=track.richsync" +
                        "&subtitle_format=lrc" +
                        "&track_id=$trackId" +
                        "&f_subtitle_length_max_deviation=40"
                ) ?: return@withContext null

                val macroCalls = JSONObject(lyricResponse)
                    .optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optJSONObject("macro_calls")

                // 2a. richsync 逐字
                val richsyncBody = macroCalls
                    ?.optJSONObject("track.richsync.get")
                    ?.optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optJSONObject("richsync")
                    ?.optString("richsync_body", "")
                    .orEmpty()
                if (richsyncBody.isNotBlank()) {
                    val parsedLines = MusixmatchRichsyncParser.parseRichsync(richsyncBody)
                    if (parsedLines.isNotEmpty()) {
                        return@withContext OnlineLyricFetcher.LyricResult(
                            api = "Musixmatch",
                            lyrics = richsyncBody,
                            parsedLines = parsedLines,
                            hasSyllable = true,
                            provider = OnlineLyricProvider.Musixmatch,
                            matchedTitle = matchedTitle,
                            matchedArtist = matchedArtist
                        )
                    }
                }

                // 2b. 普通 LRC 字幕
                val subtitleBody = macroCalls
                    ?.optJSONObject("track.subtitles.get")
                    ?.optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optJSONObject("subtitle")
                    ?.optString("subtitle_body", "")
                    .orEmpty()
                if (subtitleBody.isNotBlank()) {
                    val parsedLines = OnlineLyricParser.parseLrcLyrics(subtitleBody)
                    if (parsedLines.isNotEmpty()) {
                        return@withContext OnlineLyricFetcher.LyricResult(
                            api = "Musixmatch",
                            lyrics = subtitleBody,
                            parsedLines = parsedLines,
                            hasSyllable = false,
                            provider = OnlineLyricProvider.Musixmatch,
                            matchedTitle = matchedTitle,
                            matchedArtist = matchedArtist
                        )
                    }
                }

                // 应用只支持有时间轴的歌词；纯文本（无时间轴）无法展示，不在此回退
                null
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

    /** Musixmatch GET 请求，带 renew/captcha 重试状态机。 */
    private suspend fun musixmatchGet(req: String, maxTrial: Int = MAX_TRIAL): String? {
        if (maxTrial < 0) return null

        ensureUserToken()

        val url = "https://apic-desktop.musixmatch.com/ws/1.1/" +
            req +
            "&usertoken=${userToken.orEmpty()}" +
            "&format=json" +
            "&app_id=web-desktop-app-v1.0" +
            "&t=${randomId()}"
        val response = httpClient.get(url, headers = musixmatchHeaders()) ?: return null

        if (response.contains("\"status_code\":401")) {
            when {
                response.contains("\"hint\":\"renew\"") -> {
                    userToken = null
                    tokenStore?.clear()
                    return musixmatchGet(req, maxTrial - 1)
                }
                response.contains("\"hint\":\"captcha\"") -> {
                    delay(1000)
                    return musixmatchGet(req, maxTrial - 1)
                }
            }
        }

        if (response.contains("\"status_code\":401") &&
            response.contains("\"hint\":\"captcha\"")
        ) {
            throw MusixmatchCaptchaException()
        }

        return response
    }

    private suspend fun ensureUserToken() {
        if (userToken != null) return
        refreshUserToken()
    }

    private suspend fun refreshUserToken() {
        var tokenResult = getToken()
        var maxTry = 10
        while (tokenResult?.statusCode == 401 &&
            tokenResult.hint == "captcha" &&
            maxTry-- > 0
        ) {
            delay(1000)
            tokenResult = getToken()
        }
        val token = tokenResult?.userToken
        if (token.isNullOrBlank()) {
            throw MusixmatchCaptchaException("User Token failed to refresh")
        }
        userToken = token
        tokenStore?.save(token)
    }

    private suspend fun getToken(): TokenResult? {
        val response = httpClient.get(
            "https://apic-desktop.musixmatch.com/ws/1.1/token.get" +
                "?app_id=web-desktop-app-v1.0" +
                "&t=${randomId()}",
            headers = musixmatchHeaders()
        ) ?: return null
        return runCatching {
            val json = JSONObject(response)
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

    private data class TokenResult(
        val statusCode: Int,
        val hint: String,
        val userToken: String
    )

    /** 生成请求随机参数（base36 随机串，仅取字母段）。 */
    private fun randomId(): String {
        val code = java.lang.Long.toString(
            (Math.random() * Long.MAX_VALUE).toLong(),
            36
        )
        val letters = code.filter { it.isLetter() }
        if (letters.length <= 2) return "abcdefgh"
        return letters.substring(2, minOf(8, letters.length - 2) + 2)
    }

    private fun String.encodeURL(): String =
        URLEncoder.encode(this, "UTF-8")

    private companion object {
        // 对齐参考项目 BaseApi：Musixmatch 会按 UA/authority 等请求头做反爬判定
        const val MAX_TRIAL = 3
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        const val AUTHORITY = "apic-desktop.musixmatch.com"

        fun musixmatchHeaders(): Map<String, String> = mapOf(
            "authority" to AUTHORITY,
            "User-Agent" to USER_AGENT
        )

        @Volatile
        var userToken: String? = null
    }
}

/** Musixmatch 触发验证码（401 + hint=captcha）达到重试上限。 */
internal class MusixmatchCaptchaException(message: String = "Musixmatch captcha") : Exception(message)
