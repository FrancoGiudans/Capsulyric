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

package com.example.islandlyrics.lyrics.online

import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.provider.AppleMusicLyricProvider
import com.example.islandlyrics.lyrics.online.provider.AppleMusicCatalogAlias
import com.example.islandlyrics.lyrics.online.provider.KugouLyricProvider
import com.example.islandlyrics.lyrics.online.provider.LrcApiLyricProvider
import com.example.islandlyrics.lyrics.online.provider.LrclibLyricProvider
import com.example.islandlyrics.lyrics.online.provider.MusixmatchLyricProvider
import com.example.islandlyrics.lyrics.online.provider.NeteaseLyricProvider
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import com.example.islandlyrics.lyrics.online.provider.QqMusicLyricProvider
import com.example.islandlyrics.lyrics.online.provider.SodaMusicLyricProvider
import com.example.islandlyrics.lyrics.online.selection.OnlineLyricSelector

import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier

/**
 * 在线歌词获取器
 * 支持从多个在线源(酷狗/网易/LrcApi)获取带时间轴的歌词
 */
class OnlineLyricFetcher(
    private val networkAllowed: () -> Boolean = { true }
) {

    data class LyricQuery(
        val title: String,
        val artist: String,
        val album: String = "",
        val durationMs: Long = 0L,
        val albumArtist: String = "",
        val mediaId: String = "",
        val mediaUri: String = ""
    )
    
    // 歌词行数据类
    data class LyricLine(
        val startTime: Long,  // 毫秒
        val endTime: Long,    // 毫秒
        val text: String,
        val syllables: List<SyllableInfo>? = null,  // 逐字信息
        val translation: String? = null,
        val roma: String? = null
    )
    
    // 逐字信息数据类
    data class SyllableInfo(
        val startTime: Long,  // 毫秒
        val endTime: Long,    // 毫秒
        val text: String
    )
    
    // API结果数据类
    data class LyricResult(
        val api: String,                    // "Kugou" / "Netease" / "LrcApi"
        val lyrics: String?,                 // 歌词原文
        val parsedLines: List<LyricLine>?,   // 解析后的歌词行
        val hasSyllable: Boolean,            // 是否有逐字信息
        var score: Int = 0,                  // 评分
        val provider: OnlineLyricProvider,
        val matchedTitle: String? = null,    // 匹配到的标题
        val matchedArtist: String? = null,   // 匹配到的艺术家
        val matchedAlbum: String? = null,
        val matchedDurationMs: Long? = null,
        val providerTrackId: String? = null,
        val isrc: String? = null,
        var identityScore: Int = 0,
        var identityEvidence: String? = null,
        val translationLyrics: String? = null,
        val romanLyrics: String? = null,
        val error: String? = null            // 错误信息
    )

    data class ProviderAttempt(
        val provider: OnlineLyricProvider,
        val result: LyricResult?,
        val durationMs: Long,
        val usedCleanTitleFallback: Boolean,
        val queryTitle: String = "",
        val queryArtist: String = "",
        val queryVariant: String = "exact"
    )

    data class FetchOutcome(
        val query: LyricQuery,
        val bestResult: LyricResult?,
        val attempts: List<ProviderAttempt>,
        val usedCleanTitleFallback: Boolean
    )
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .hostnameVerifier(HostnameVerifier { _, _ -> true })
        .build()
    private val httpClient = OnlineLyricHttpClient(client, networkAllowed)
    private val lrclibProvider = LrclibLyricProvider(httpClient)
    private val lrcApiProvider = LrcApiLyricProvider(httpClient)
    private val sodaMusicProvider = SodaMusicLyricProvider(httpClient)
    private val neteaseProvider = NeteaseLyricProvider(httpClient)
    private val kugouProvider = KugouLyricProvider(httpClient)
    private val qqMusicProvider = QqMusicLyricProvider(httpClient)
    private val appleMusicProvider = AppleMusicLyricProvider()
    private val musixmatchProvider = MusixmatchLyricProvider(httpClient)
    private val selector = OnlineLyricSelector(::cleanTitle)
    
    /**
     * 从多个API获取歌词并选择最佳结果
     */
    suspend fun fetchBestLyrics(
        title: String,
        artist: String,
        providerOrderIds: List<String> = OnlineLyricProvider.defaultIds(),
        useSmartSelection: Boolean = true,
        disabledProviderIds: Set<String> = emptySet()
    ): LyricResult? {
        return fetchLyrics(
            title = title,
            artist = artist,
            providerOrderIds = providerOrderIds,
            useSmartSelection = useSmartSelection,
            disabledProviderIds = disabledProviderIds
        ).bestResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        albumArtist: String = "",
        mediaId: String = "",
        mediaUri: String = "",
        cachedAppleAliases: List<AppleMusicCatalogAlias> = emptyList(),
        onAppleAliasesResolved: (suspend (List<AppleMusicCatalogAlias>) -> Unit)? = null,
        providerOrderIds: List<String> = OnlineLyricProvider.defaultIds(),
        useSmartSelection: Boolean = true,
        disabledProviderIds: Set<String> = emptySet()
    ): FetchOutcome {
        val providerOrder = OnlineLyricProvider.normalizeOrder(providerOrderIds)
            .filterNot { it.id in disabledProviderIds }
        val query = LyricQuery(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            albumArtist = albumArtist,
            mediaId = mediaId,
            mediaUri = mediaUri
        )
        if (!networkAllowed()) {
            AppLogger.getInstance().i("OnlineLyric", "Offline mode enabled, online lyric fetch blocked")
            return FetchOutcome(query, null, emptyList(), false)
        }
        val exactAttempts = fetchAllProviders(query, providerOrder, usedCleanTitleFallback = false)
        val exactBest = selector.selectBestResult(
            exactAttempts,
            title,
            artist,
            providerOrder,
            useSmartSelection,
            album,
            durationMs
        )
        if (exactBest != null) {
            return FetchOutcome(query, exactBest, exactAttempts, false)
        }

        var fallbackAttempts = exactAttempts
        val cleanTitle = cleanTitle(title)
        if (cleanTitle != title) {
            AppLogger.getInstance().i("OnlineLyric", "精确搜索未找到，尝试清理标题: $cleanTitle")
            val cleanQuery = query.copy(title = cleanTitle)
            val cleanAttempts = fetchAllProviders(
                cleanQuery,
                providerOrder,
                usedCleanTitleFallback = true,
                queryVariant = "clean_title"
            )
            val allAttempts = exactAttempts + cleanAttempts
            fallbackAttempts = allAttempts
            val cleanBest = selector.selectBestResult(
                allAttempts,
                cleanTitle,
                artist,
                providerOrder,
                useSmartSelection,
                album,
                durationMs
            )
            if (cleanBest != null) {
                return FetchOutcome(
                    query = query,
                    bestResult = cleanBest,
                    attempts = allAttempts,
                    usedCleanTitleFallback = cleanAttempts.any { it.result != null }
                )
            }
        }

        // Apple Music bridge: resolve a bounded set of catalog aliases using
        // source storefront metadata and ISRC, then retry domestic providers
        // with the evidence-backed localized title/artist. This is kept after
        // the cheap title paths so normal tracks pay no extra network cost.
        if (
            OnlineLyricProvider.AppleMusic in providerOrder &&
            providerOrder.any { it != OnlineLyricProvider.AppleMusic } &&
            (album.isNotBlank() || durationMs > 0L)
        ) {
            val aliasAttempts = mutableListOf<ProviderAttempt>()
            val aliases = if (cachedAppleAliases.isNotEmpty()) {
                cachedAppleAliases
            } else {
                appleMusicProvider.resolveCatalogAliases(
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    mediaId = mediaId,
                    mediaUri = mediaUri
                ).also { resolved ->
                    if (resolved.isNotEmpty()) onAppleAliasesResolved?.invoke(resolved)
                }
            }
            val aliasProviders = providerOrder.filterNot { it == OnlineLyricProvider.AppleMusic }
            for (alias in aliases.take(MAX_APPLE_ALIAS_QUERIES)) {
                if (alias.title.equals(title, ignoreCase = true) &&
                    alias.artist.equals(artist, ignoreCase = true)
                ) continue

                val aliasQuery = query.copy(
                    title = alias.title,
                    artist = alias.artist,
                    album = alias.album ?: album,
                    durationMs = alias.durationMs ?: durationMs
                )
                val attempts = fetchAllProviders(
                    aliasQuery,
                    aliasProviders,
                    usedCleanTitleFallback = true,
                    queryVariant = "apple_alias"
                )
                aliasAttempts += attempts
                val aliasBest = selector.selectBestResult(
                    attempts = attempts,
                    targetTitle = aliasQuery.title,
                    targetArtist = aliasQuery.artist,
                    providerOrder = aliasProviders,
                    useSmartSelection = useSmartSelection,
                    targetAlbum = aliasQuery.album,
                    targetDurationMs = aliasQuery.durationMs
                )
                if (aliasBest != null) {
                    return FetchOutcome(
                        query = query,
                        bestResult = aliasBest,
                        attempts = fallbackAttempts + aliasAttempts,
                        usedCleanTitleFallback = true
                    )
                }
            }
            fallbackAttempts = fallbackAttempts + aliasAttempts
        }

        // Cross-language fallback: search by artist only when title-based queries
        // produced no identity-safe result. Candidate duration/album metadata is
        // then used to reject unrelated songs with the same artist.
        if (artist.isNotBlank() && (album.isNotBlank() || durationMs > 0L)) {
            val artistQuery = query.copy(title = "")
            val artistAttempts = fetchAllProviders(
                artistQuery,
                providerOrder,
                usedCleanTitleFallback = true,
                queryVariant = "artist_only"
            )
            val allAttempts = fallbackAttempts + artistAttempts
            return FetchOutcome(
                query = query,
                bestResult = selector.selectBestResult(
                    allAttempts,
                    "",
                    artist,
                    providerOrder,
                    useSmartSelection,
                    album,
                    durationMs
                ),
                attempts = allAttempts,
                usedCleanTitleFallback = allAttempts.size > exactAttempts.size
            )
        }

        return FetchOutcome(query, null, exactAttempts, false)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun fetchAllProviders(
        query: LyricQuery,
        providerOrder: List<OnlineLyricProvider>,
        usedCleanTitleFallback: Boolean,
        queryVariant: String = if (usedCleanTitleFallback) "fallback" else "exact"
    ): List<ProviderAttempt> {
        return withContext(Dispatchers.IO) {
            try {
                val deferreds = providerOrder.map { provider ->
                    async {
                        val startedAt = System.currentTimeMillis()
                        val result = when (provider) {
                            OnlineLyricProvider.QQMusic -> qqMusicProvider.fetch(query.title, query.artist, query.album, query.durationMs)
                            OnlineLyricProvider.Kugou -> kugouProvider.fetch(query.title, query.artist, query.album, query.durationMs)
                            OnlineLyricProvider.SodaMusic -> sodaMusicProvider.fetch(query.title, query.artist, query.album, query.durationMs)
                            OnlineLyricProvider.Lrclib -> lrclibProvider.fetch(query.title, query.artist)
                            OnlineLyricProvider.Netease -> neteaseProvider.fetch(query.title, query.artist, query.album, query.durationMs)
                            OnlineLyricProvider.LrcApi -> lrcApiProvider.fetch(query.title, query.artist)
                            OnlineLyricProvider.AppleMusic -> appleMusicProvider.fetch(
                                query.title,
                                query.artist,
                                query.album,
                                query.durationMs
                            )
                            OnlineLyricProvider.Musixmatch -> musixmatchProvider.fetch(query.title, query.artist)
                        }
                        ProviderAttempt(
                            provider = provider,
                            result = result,
                            durationMs = System.currentTimeMillis() - startedAt,
                            usedCleanTitleFallback = usedCleanTitleFallback,
                            queryTitle = query.title,
                            queryArtist = query.artist,
                            queryVariant = queryVariant
                        )
                    }
                }

                val firstResult = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                    deferreds.asFlow()
                        .flatMapMerge(concurrency = Int.MAX_VALUE) { deferred -> flow { emit(deferred.await()) } }
                        .filter {
                            selector.isPotentiallyMatching(
                                it.result,
                                query.title,
                                query.artist,
                                query.album,
                                query.durationMs
                            )
                        }
                        .firstOrNull()
                }?.result

                if (firstResult != null) {
                    delay(FAST_RESULT_GRACE_PERIOD_MS)
                    AppLogger.getInstance().i(
                        "OnlineLyric",
                        "首个可用歌词结果已到达，等待 ${FAST_RESULT_GRACE_PERIOD_MS}ms 收集其他源结果"
                    )
                }

                deferreds.mapNotNull {
                    if (it.isCompleted && !it.isCancelled) {
                        try {
                            it.getCompleted()
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        it.cancel()
                        null
                    }
                }

            } catch (e: Exception) {
                AppLogger.getInstance().e("OnlineLyric", "获取歌词失败: ${e.message}")
                emptyList()
            }
        }
    }
    
    // 清理标题：移除括号、Remix、Feat等干扰词
    private fun cleanTitle(title: String): String {
        var clean = title
        
        // 1. 移除 (...) 和 [...] 内容
        clean = clean.replace("\\(.*?\\)".toRegex(), " ")
        clean = clean.replace("\\[.*?\\]".toRegex(), " ")
        
        // 2. 移除常见后缀 (不区分大小写)
        val suffixes = listOf("feat.", "ft.", "remix", "version", "live", "cover", "radio edit", "mix")
        for (suffix in suffixes) {
            clean = clean.replace(suffix, "", ignoreCase = true)
        }
        
        // 3. 移除多余空格
        return clean.trim().replace("\\s+".toRegex(), " ")
    }

    private companion object {
        private const val FETCH_TIMEOUT_MS = 10_000L
        private const val FAST_RESULT_GRACE_PERIOD_MS = 500L
        private const val MAX_APPLE_ALIAS_QUERIES = 3
    }
}

