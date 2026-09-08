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
import java.text.Normalizer
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Apple Music 歌词提供者（匿名模式）。
 *
 * - AccessToken：从 `music.apple.com/us/browse` 的 index*.js 中抓取 Web JWT（[AppleMusicStateCache]）
 * - 地区/语言：读 [AppleMusicStateCache]（全局默认），可由规则覆盖参数 [storefront]/[language] 传入
 * - 歌词接口 `l` 参数已参数化（参考项目硬编码 zh-hans-cn，此处用配置语言）
 * - TTML 逐字：优先 `ttmlLocalizations` 回退 `ttml`，需含 begin=/end= 才采用
 */
data class AppleMusicCatalogAlias(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val providerTrackId: String,
    val isrc: String?,
    val storefront: String
)

/** Pure parsing helpers for Android MediaMetadata IDs/URIs. */
internal object AppleMusicMediaRef {
    fun extractStorefront(value: String): String? {
        return Regex("""music\.apple\.com/([a-z]{2})(?:/|$)""", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
            ?.takeIf { it.length == 2 }
    }

    fun extractSongId(value: String): String? {
        val structured = Regex("^apple:(?:song|track)[:/](\\d+)$", RegexOption.IGNORE_CASE)
            .matchEntire(value.trim())
            ?.groupValues
            ?.getOrNull(1)
        if (structured != null) return structured
        val queryId = Regex("""[?&](?:i|id)=(\d+)""", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
        if (queryId != null) return queryId
        return Regex("""/song/[^/]+/(\d+)""", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
    }
}

/**
 * Conservative storefront hints used only when the player does not expose an
 * Apple Music storefront. Script-specific titles are much more likely to be
 * discoverable in their native catalog than in the globally configured one.
 */
internal object AppleMusicStorefrontHint {
    fun infer(vararg values: String): String? {
        val text = values.joinToString(" ")
        return when {
            text.any { it in '\u3040'..'\u30ff' || it in '\uff65'..'\uff9f' } -> "jp"
            text.any { it in '\u1100'..'\u11ff' || it in '\u3130'..'\u318f' || it in '\uac00'..'\ud7af' } -> "kr"
            else -> null
        }
    }

    fun candidates(
        sourceStorefront: String?,
        configuredStorefront: String,
        title: String,
        artist: String,
        album: String
    ): List<String> = linkedSetOf<String>().apply {
        sourceStorefront?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.length == 2 }?.let(::add)
        infer(title, artist, album)?.let(::add)
        configuredStorefront.trim().lowercase(Locale.ROOT).takeIf { it.length == 2 }?.let(::add)
        add("us")
    }.toList()
}

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
        album: String = "",
        durationMs: Long = 0L,
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
                "?term=${"$title $artist".trim().encodeURL()}" +
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
            val best = CandidateMatcher.pickBest(candidates, title, artist, album, durationMs)
                ?: return@withContext null
            val songId = best.song.optString("id", "")
            val matchedTitle = best.matchedTitle
            val matchedArtist = best.matchedArtist
            val matchedAlbum = best.matchedAlbum
            val matchedDurationMs = best.matchedDurationMs
            val providerTrackId = best.providerTrackId
            val isrc = best.isrc
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
                    matchedAlbum = matchedAlbum,
                    matchedDurationMs = matchedDurationMs,
                    providerTrackId = providerTrackId,
                    isrc = isrc,
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
                matchedArtist = matchedArtist,
                matchedAlbum = matchedAlbum,
                matchedDurationMs = matchedDurationMs,
                providerTrackId = providerTrackId,
                isrc = isrc
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

    /**
     * Resolve Apple catalog metadata without requiring a media-user-token.
     *
     * The source storefront is inferred from a Media URI/ID when possible. A
     * bounded storefront list is resolved in deterministic order, then the returned ISRC is used
     * to obtain localized catalog aliases from that storefront and mainland
     * China. These aliases are evidence-backed search terms for domestic lyric
     * providers; they never replace the player's metadata.
     */
    suspend fun resolveCatalogAliases(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        mediaId: String = "",
        mediaUri: String = ""
    ): List<AppleMusicCatalogAlias> = withContext(Dispatchers.IO) {
        if (title.isBlank() && artist.isBlank()) return@withContext emptyList()

        val cacheKey = buildAliasCacheKey(title, artist, album, durationMs, mediaId, mediaUri)
        val now = System.currentTimeMillis()
        aliasCache[cacheKey]?.takeIf { it.expiresAt > now }?.let { return@withContext it.aliases }

        val aliases = try {
            AppleMusicStateCache.ensureInit(httpClient)
            if (AppleMusicStateCache.accessToken.isBlank()) return@withContext emptyList()
            resolveCatalogResolution(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                mediaId = mediaId,
                mediaUri = mediaUri
            )?.aliases.orEmpty()
        } catch (error: Exception) {
            AppLogger.getInstance().w("OnlineLyric", "Apple catalog identity bridge failed: ${error.message}")
            emptyList()
        }

        aliasCache[cacheKey] = CachedAliases(
            expiresAt = now + if (aliases.isEmpty()) NEGATIVE_ALIAS_TTL_MS else ALIAS_TTL_MS,
            aliases = aliases
        )
        aliases
    }

    private data class CatalogAnchor(
        val storefront: String,
        val candidate: AppleSongCandidate
    )

    private data class CatalogResolution(
        val canonicalIsrc: String,
        val anchor: CatalogAnchor,
        val aliases: List<AppleMusicCatalogAlias>
    )

    /** Resolve one source recording before querying any target storefront. */
    private suspend fun resolveCatalogResolution(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        mediaId: String,
        mediaUri: String
    ): CatalogResolution? {
        val sourceStorefront = extractStorefront(mediaUri) ?: extractStorefront(mediaId)
        val sourceSongId = extractSongId(mediaUri) ?: extractSongId(mediaId)
        val anchorStorefronts = AppleMusicStorefrontHint.candidates(
            sourceStorefront = sourceStorefront,
            configuredStorefront = AppleMusicStateCache.storefront,
            title = title,
            artist = artist,
            album = album
        )

        // A native-script storefront is only a search hint. Every accepted
        // anchor still has to pass the normal identity matcher before its ISRC
        // can be used, so fallback storefronts cannot silently replace a song.
        val anchor = anchorStorefronts.firstNotNullOfOrNull { storefront ->
            if (sourceSongId != null) {
                fetchCatalogSongs(storefront, "songs/$sourceSongId")
                    .firstOrNull()
                    ?.let { CatalogAnchor(storefront, it) }
            } else {
                resolveSearchAnchor(storefront, title, artist, album, durationMs)
            }
        } ?: return null

        val canonicalIsrc = AppleMusicIsrc.normalize(anchor.candidate.isrc) ?: return null
        val storefronts = linkedSetOf(anchor.storefront, "cn")
        val verifiedAliases = buildList {
            for (storefront in storefronts) {
                fetchCatalogSongsByIsrc(
                    storefront = storefront,
                    isrc = canonicalIsrc,
                    language = if (storefront == "cn") "zh-Hans" else AppleMusicStateCache.language
                ).forEach { candidate ->
                    val itemIsrc = AppleMusicIsrc.normalize(candidate.isrc) ?: return@forEach
                    if (itemIsrc != canonicalIsrc) return@forEach
                    if (!CandidateMatcher.isDurationCompatible(
                            anchor.candidate.matchedDurationMs ?: durationMs,
                            candidate.matchedDurationMs
                        )
                    ) return@forEach
                    if (CandidateMatcher.hasVersionConflict(
                            anchor.candidate.matchedTitle,
                            anchor.candidate.matchedAlbum.orEmpty(),
                            candidate.matchedTitle,
                            candidate.matchedAlbum
                        )
                    ) return@forEach
                    add(candidate.toAlias(storefront, canonicalIsrc))
                }
            }
            add(anchor.candidate.toAlias(anchor.storefront, canonicalIsrc))
        }

        val aliases = verifiedAliases
            .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
            .distinctBy { aliasDedupKey(it) }
            .sortedWith(
                compareBy<AppleMusicCatalogAlias> {
                    if (it.storefront == "cn") 0 else 1
                }.thenBy {
                    if (it.title.equals(anchor.candidate.matchedTitle, ignoreCase = true) &&
                        it.artist.equals(anchor.candidate.matchedArtist, ignoreCase = true)
                    ) 1 else 0
                }.thenByDescending { if (it.album.isNullOrBlank()) 0 else 1 }
            )

        return CatalogResolution(canonicalIsrc, anchor, aliases)
    }

    private suspend fun resolveSearchAnchor(
        storefront: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): CatalogAnchor? {
        val candidates = searchCatalogSongs(storefront, title, artist, album)
        val best = CandidateMatcher.pickBestWithMargin(
            candidates = candidates,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs
        ) ?: return null
        return CatalogAnchor(storefront, best)
    }

    private fun aliasDedupKey(alias: AppleMusicCatalogAlias): String = listOf(
        normalizeAliasText(alias.title),
        normalizeAliasText(alias.artist),
        AppleMusicIsrc.normalize(alias.isrc).orEmpty()
    ).joinToString("|")

    private fun normalizeAliasText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")

    private suspend fun searchCatalogSongs(
        storefront: String,
        title: String,
        artist: String,
        album: String
    ): List<AppleSongCandidate> {
        val terms = buildList {
            add("$title $artist".trim())
            if (album.isNotBlank()) add("$title $artist $album".trim())
        }.distinct()
        // Ask a storefront in its native language first. This is essential for
        // Japanese/Korean player metadata: requesting zh-Hans from `jp` can
        // otherwise return an English-localized title that the matcher rejects
        // before an ISRC is ever extracted.
        val languages = listOf(
            preferredCatalogLanguage(storefront),
            AppleMusicStateCache.language,
            "en-US"
        )
            .distinct()
        val candidates = mutableListOf<AppleSongCandidate>()
        for (term in terms) {
            for (language in languages) {
                val url = "https://amp-api.music.apple.com/v1/catalog/$storefront/search" +
                    "?term=${term.encodeURL()}&types=songs&limit=10&l=${language.encodeURL()}"
                getWithTokenRetry(url)?.let(::parseSongCandidates)?.let(candidates::addAll)
            }
        }
        return candidates.distinctBy { it.providerTrackId ?: it.song.toString() }
    }

    private fun preferredCatalogLanguage(storefront: String): String = when (storefront.lowercase(Locale.ROOT)) {
        "cn" -> "zh-Hans"
        "hk", "mo", "tw" -> "zh-Hant"
        "jp" -> "ja-JP"
        "kr" -> "ko-KR"
        else -> "en-US"
    }

    private suspend fun fetchCatalogSongs(
        storefront: String,
        path: String
    ): List<AppleSongCandidate> {
        val url = "https://amp-api.music.apple.com/v1/catalog/$storefront/$path" +
            "?l=${AppleMusicStateCache.language.encodeURL()}"
        return getWithTokenRetry(url)?.let(::parseSongCandidates).orEmpty()
    }

    private suspend fun fetchCatalogSongsByIsrc(
        storefront: String,
        isrc: String,
        language: String = AppleMusicStateCache.language
    ): List<AppleSongCandidate> {
        val canonicalIsrc = AppleMusicIsrc.normalize(isrc) ?: return emptyList()
        val url = "https://amp-api.music.apple.com/v1/catalog/$storefront/songs" +
            "?filter%5Bisrc%5D=${canonicalIsrc.encodeURL()}&l=${language.encodeURL()}"
        return getWithTokenRetry(url)?.let(::parseSongCandidates).orEmpty()
    }

    private fun parseSongCandidates(response: String): List<AppleSongCandidate> {
        val songs = JSONObject(response).optJSONArray("data")
            ?: JSONObject(response).optJSONObject("results")
                ?.optJSONObject("songs")
                ?.optJSONArray("data")
            ?: return emptyList()
        return buildList {
            for (index in 0 until songs.length()) {
                songs.optJSONObject(index)?.let { add(AppleSongCandidate(it)) }
            }
        }
    }

    private fun extractStorefront(value: String): String? {
        return AppleMusicMediaRef.extractStorefront(value)
    }

    private fun extractSongId(value: String): String? {
        return AppleMusicMediaRef.extractSongId(value)
    }

    private fun buildAliasCacheKey(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        mediaId: String,
        mediaUri: String
    ): String = listOf(title, artist, album, durationMs, mediaId, mediaUri)
        .joinToString("|") { it.toString().trim().lowercase() }

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

        override val matchedAlbum: String?
            get() = song.optJSONObject("attributes")?.optString("albumName", "")
                .orEmpty()
                .takeIf { it.isNotBlank() }

        override val matchedDurationMs: Long?
            get() = song.optJSONObject("attributes")?.optLong("durationInMillis", 0L)
                ?.takeIf { it > 0L }

        override val providerTrackId: String?
            get() = song.optString("id", "").takeIf { it.isNotBlank() }

        override val isrc: String?
            get() = song.optJSONObject("attributes")?.optString("isrc", "")
                ?.takeIf { it.isNotBlank() }

        fun toAlias(storefront: String, canonicalIsrc: String = isrc.orEmpty()): AppleMusicCatalogAlias = AppleMusicCatalogAlias(
            title = matchedTitle,
            artist = matchedArtist,
            album = matchedAlbum,
            durationMs = matchedDurationMs,
            providerTrackId = providerTrackId.orEmpty(),
            isrc = canonicalIsrc.takeIf { it.isNotBlank() },
            storefront = storefront
        )
    }

    private companion object {
        private data class CachedAliases(
            val expiresAt: Long,
            val aliases: List<AppleMusicCatalogAlias>
        )

        private val aliasCache = ConcurrentHashMap<String, CachedAliases>()
        private const val ALIAS_TTL_MS = 24 * 60 * 60 * 1000L
        private const val NEGATIVE_ALIAS_TTL_MS = 5 * 60 * 1000L
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}

/** Apple Music 登录凭据（media-user-token）无效或过期。 */
internal class AppleMusicAuthException(message: String) : Exception(message)
