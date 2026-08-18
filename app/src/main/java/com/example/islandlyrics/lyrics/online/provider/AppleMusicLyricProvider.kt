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
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser
import com.example.islandlyrics.lyrics.online.selection.CandidateMatcher
import com.example.islandlyrics.lyrics.online.selection.SearchCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Apple Music 歌词提供者（匿名模式）。
 *
 * - AccessToken：从 `music.apple.com/us/browse` 的 index*.js 中抓取 Web JWT（[AppleMusicStateCache]）
 * - 地区/语言：读 [AppleMusicStateCache]（全局默认），可由规则覆盖参数 [storefront]/[language] 传入
 * - 歌词接口 `l` 参数已参数化（参考项目硬编码 zh-hans-cn，此处用配置语言）
 * - TTML 逐字：优先 `ttmlLocalizations` 回退 `ttml`，需含 begin=/end= 才采用
 */
internal class AppleMusicLyricProvider {
    // Apple 专用客户端：跟随重定向（music.apple.com 在部分网络下会 302 到地区页，
    // 共享客户端 followRedirects(false) 会抓不到 index*.js 导致 accessToken 为空 -> catalog 401）
    private val httpClient: OnlineLyricHttpClient = OnlineLyricHttpClient(
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    )

    suspend fun fetch(
        title: String,
        artist: String,
        storefront: String? = null,
        language: String? = null
    ): OnlineLyricFetcher.LyricResult? = withContext(Dispatchers.IO) {
        try {
            // Apple 的歌词接口要求登录态（media-user-token），匿名模式只能搜索到歌曲、拿不到歌词
            if (AppleMusicStateCache.mediaUserToken.isBlank()) {
                return@withContext OnlineLyricFetcher.LyricResult(
                    api = "AppleMusic",
                    lyrics = null,
                    parsedLines = null,
                    hasSyllable = false,
                    provider = OnlineLyricProvider.AppleMusic,
                    error = "Apple Music 需要登录（media-user-token）后才能获取歌词"
                )
            }
            AppleMusicStateCache.ensureInit(httpClient)
            // 抓不到匿名访问令牌（网络失败/页面结构变化）时不发送无认证请求，给出明确错误
            if (AppleMusicStateCache.accessToken.isBlank()) {
                return@withContext OnlineLyricFetcher.LyricResult(
                    api = "AppleMusic",
                    lyrics = null,
                    parsedLines = null,
                    hasSyllable = false,
                    provider = OnlineLyricProvider.AppleMusic,
                    error = "无法获取 Apple Music 访问令牌，请检查网络后重试"
                )
            }
            val effectiveStorefront = if (AppleMusicStateCache.mediaUserToken.isNotBlank()) {
                // 登录后地区始终使用账号所在地区（手动/规则覆盖不再生效）
                AppleMusicStateCache.storefront
            } else {
                storefront ?: AppleMusicStateCache.storefront
            }
            val effectiveLanguage = language ?: AppleMusicStateCache.language
            AppLogger.getInstance().d(
                "OnlineLyric",
                "AppleMusic fetch: storefront=$effectiveStorefront lang=$effectiveLanguage " +
                    "mut=${AppleMusicStateCache.mediaUserToken.take(6)}..."
            )

            // 1. 搜索（多候选）
            val searchUrl = "https://amp-api.music.apple.com/v1/catalog/$effectiveStorefront/search" +
                "?term=${title.encodeURL()}" +
                "&types=songs&limit=20" +
                "&l=${effectiveLanguage.encodeURL()}"
            val searchResponse = getWithTokenRetry(searchUrl) ?: return@withContext null
            val searchJson = JSONObject(searchResponse)
            val songs = searchJson
                .optJSONObject("results")
                ?.optJSONObject("songs")
                ?.optJSONArray("data")
                ?: return@withContext null

            val candidates = buildList {
                for (index in 0 until songs.length()) {
                    songs.optJSONObject(index)?.let { add(AppleSongCandidate(it)) }
                }
            }
            val best = CandidateMatcher.pickBest(candidates, title, artist)
                ?: return@withContext null
            val songId = best.song.optString("id", "")
            val matchedTitle = best.matchedTitle
            val matchedArtist = best.matchedArtist
            if (songId.isBlank()) return@withContext null

            // 2. 歌词（逐字 TTML）
            val lyricUrl = "https://amp-api.music.apple.com/v1/catalog/$effectiveStorefront/songs/$songId" +
                "?include[songs]=syllable-lyrics" +
                "&l=${effectiveLanguage.encodeURL()}" +
                "&extend=ttmlLocalizations"
            val lyricResponse = getWithTokenRetry(lyricUrl) ?: return@withContext null

            var ttml = extractTtml(lyricResponse)
            if (ttml.isBlank()) {
                // 回退：子资源端点（apple-music-downloader 等带 MUT 获取歌词的常用方式）
                val subUrls = listOf(
                    "https://amp-api.music.apple.com/v1/catalog/$effectiveStorefront/songs/$songId/syllable-lyrics" +
                        "?l=${effectiveLanguage.encodeURL()}",
                    "https://amp-api.music.apple.com/v1/catalog/$effectiveStorefront/songs/$songId/lyrics" +
                        "?l=${effectiveLanguage.encodeURL()}"
                )
                for (subUrl in subUrls) {
                    val subResponse = getWithTokenRetry(subUrl) ?: continue
                    ttml = extractTtml(subResponse)
                    if (ttml.isNotBlank()) break
                }
            }
            if (ttml.isBlank()) {
                return@withContext OnlineLyricFetcher.LyricResult(
                    api = "AppleMusic",
                    lyrics = null,
                    parsedLines = null,
                    hasSyllable = false,
                    provider = OnlineLyricProvider.AppleMusic,
                    matchedTitle = matchedTitle,
                    matchedArtist = matchedArtist,
                    error = "无歌词内容"
                )
            }

            val parsedLines = OnlineLyricParser.parseTtmlLyrics(ttml)
            OnlineLyricFetcher.LyricResult(
                api = "AppleMusic",
                lyrics = ttml,
                parsedLines = parsedLines,
                hasSyllable = parsedLines.any { !it.syllables.isNullOrEmpty() },
                provider = OnlineLyricProvider.AppleMusic,
                matchedTitle = matchedTitle,
                matchedArtist = matchedArtist
            )
        } catch (e: AppleMusicAuthException) {
            AppLogger.getInstance().w("OnlineLyric", "AppleMusic 登录: ${e.message}")
            OnlineLyricFetcher.LyricResult(
                api = "AppleMusic",
                lyrics = null,
                parsedLines = null,
                hasSyllable = false,
                provider = OnlineLyricProvider.AppleMusic,
                error = e.message ?: "Apple Music 登录凭据无效或已过期"
            )
        } catch (e: Exception) {
            AppLogger.getInstance().log("OnlineLyric", "AppleMusic API错误: ${e.message}")
            null
        }
    }

    /** 提取 TTML：优先 ttmlLocalizations 回退 ttml，校验含 begin=/end=。 */
    private fun extractTtml(lyricResponse: String): String {
        return runCatching {
            val root = JSONObject(lyricResponse)
            val data = root.optJSONArray("data")
            val obj = data?.optJSONObject(0) ?: return ""
            // 形状 A：{data:[{relationships:{"syllable-lyrics":{data:[{attributes:{...}}]}}}]}
            // 形状 B（子资源端点）：{data:[{attributes:{...}}]}
            val attributes = obj.optJSONObject("relationships")
                ?.optJSONObject("syllable-lyrics")
                ?.optJSONArray("data")
                ?.optJSONObject(0)
                ?.optJSONObject("attributes")
                ?: obj.optJSONObject("attributes")
                ?: return ""
            val localized = attributes.optString("ttmlLocalizations", "")
            val fallback = attributes.optString("ttml", "")
            val ttml = localized.ifBlank { fallback }
            if (ttml.contains("begin=") && ttml.contains("end=")) ttml else ""
        }.getOrDefault("")
    }

    /** GET + Authorization，遇 401/403 清 token 重抓重试一次；仍失败则带真实状态码/错误码抛异常。 */
    private suspend fun getWithTokenRetry(url: String): String? {
        val first = httpClient.getDetailed(url, headers = headers())
        if (first != null && first.statusCode in 200..299) return first.body

        // 401/403：Web JWT 失效或请求被 Apple 拒绝 -> 重抓一次 Web JWT 再试
        if (first != null && (first.statusCode == 401 || first.statusCode == 403)) {
            AppleMusicStateCache.resetInit()
            AppleMusicStateCache.ensureInit(httpClient)
            val second = httpClient.getDetailed(url, headers = headers())
            if (second != null && second.statusCode in 200..299) return second.body
            if (second != null &&
                (second.statusCode == 401 || second.statusCode == 403) &&
                AppleMusicStateCache.mediaUserToken.isNotBlank()
            ) {
                val status = second.statusCode
                val appleCode = parseAppleErrorCode(second.body)
                AppLogger.getInstance().e(
                    "OnlineLyric",
                    "AppleMusic 请求被拒绝 url=$url status=$status appleCode=$appleCode body=${second.body.take(300)}"
                )
                throw AppleMusicAuthException(
                    "Apple Music 请求被拒绝（HTTP $status" +
                        (appleCode?.let { ", Apple 错误 $it" } ?: "") +
                        "）url=${url.take(160)}。403 通常表示账号无订阅或请求被限流；401 表示凭据未被 catalog 接口接受"
                )
            }
        }
        return null
    }

    private fun parseAppleErrorCode(body: String): String? {
        return runCatching {
            JSONObject(body)
                .optJSONArray("errors")
                ?.optJSONObject(0)
                ?.optString("code", "")
                .orEmpty()
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun headers(): Map<String, String> {
        val map = mutableMapOf(
            "Origin" to "https://music.apple.com",
            "Accept" to "application/json",
            "User-Agent" to USER_AGENT
        )
        if (AppleMusicStateCache.accessToken.isNotBlank()) {
            map["Authorization"] = "Bearer ${AppleMusicStateCache.accessToken}"
        }
        if (AppleMusicStateCache.mediaUserToken.isNotBlank()) {
            map["media-user-token"] = AppleMusicStateCache.mediaUserToken
        }
        map["Accept-Language"] = "${AppleMusicStateCache.language},en;q=0.9"
        return map
    }

    private fun String.encodeURL(): String =
        URLEncoder.encode(this, "UTF-8")

    private class AppleSongCandidate(
        val song: JSONObject
    ) : SearchCandidate {
        override val matchedTitle: String
            get() = song.optJSONObject("attributes")?.optString("name", "").orEmpty()

        override val matchedArtist: String
            get() = song.optJSONObject("attributes")?.optString("artistName", "").orEmpty()
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}

/** Apple Music 登录凭据（media-user-token）无效或过期。 */
internal class AppleMusicAuthException(message: String) : Exception(message)
