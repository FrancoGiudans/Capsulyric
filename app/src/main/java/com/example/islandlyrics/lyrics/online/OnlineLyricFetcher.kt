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
import com.example.islandlyrics.lyrics.online.selection.CandidateMatcher
import com.example.islandlyrics.lyrics.online.selection.OnlineLyricSelector

import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import okhttp3.Dispatcher
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
    
    /** 对唱/交替演唱的视觉对齐方向 */
    enum class LyricAlignment {
        UNSPECIFIED,
        LEFT,
        RIGHT
    }

    // 歌词行数据类
    data class LyricLine(
        val startTime: Long,  // 毫秒
        val endTime: Long,    // 毫秒
        val text: String,
        val syllables: List<SyllableInfo>? = null,  // 逐字信息
        val translation: String? = null,
        val roma: String? = null,
        val alignment: LyricAlignment = LyricAlignment.UNSPECIFIED
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

    private data class DiagnosticQueryPlan(
        val query: LyricQuery,
        val providers: List<OnlineLyricProvider>,
        val variant: String,
        val isFallback: Boolean
    )

    private data class DiagnosticQueryResult(
        val plan: DiagnosticQueryPlan,
        val attempts: List<ProviderAttempt>,
        val bestResult: LyricResult?
    )

    private data class DiagnosticAliasResolution(
        val aliases: List<AppleMusicCatalogAlias>,
        val durationMs: Long
    )
    
    private val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 12
        })
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
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

    /** Fetch exactly one platform ID, without search, scoring or cross-provider fallback. */
    suspend fun fetchLyricsById(provider: OnlineLyricProvider, trackId: String): LyricResult? {
        val id = provider.normalizeTrackId(trackId) ?: return null
        if (!networkAllowed()) return null
        currentCoroutineContext().ensureActive()
        val result = when (provider) {
            OnlineLyricProvider.QQMusic -> qqMusicProvider.fetchById(id)
            OnlineLyricProvider.Kugou -> kugouProvider.fetchById(id)
            OnlineLyricProvider.SodaMusic -> sodaMusicProvider.fetchById(id)
            OnlineLyricProvider.Lrclib -> lrclibProvider.fetchById(id)
            OnlineLyricProvider.Netease -> neteaseProvider.fetchById(id)
            OnlineLyricProvider.AppleMusic -> appleMusicProvider.fetchById(id)
            OnlineLyricProvider.Musixmatch -> musixmatchProvider.fetchById(id)
            OnlineLyricProvider.LrcApi -> null
        }
        currentCoroutineContext().ensureActive()
        return result?.copy(providerTrackId = result.providerTrackId ?: id)
    }
    
    /**
     * 从多个API获取歌词并选择最佳结果
     */
    suspend fun fetchBestLyrics(
        title: String,
        artist: String,
        providerOrderIds: List<String> = OnlineLyricProvider.defaultIds(),
        useSmartSelection: Boolean = true,
        disabledProviderIds: Set<String> = emptySet(),
        collectAllResults: Boolean = false
    ): LyricResult? {
        return fetchLyrics(
            title = title,
            artist = artist,
            providerOrderIds = providerOrderIds,
            useSmartSelection = useSmartSelection,
            disabledProviderIds = disabledProviderIds,
            collectAllResults = collectAllResults
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
        disabledProviderIds: Set<String> = emptySet(),
        collectAllResults: Boolean = false
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
        if (collectAllResults) {
            return fetchDiagnosticResults(
                query = query,
                providerOrder = providerOrder,
                useSmartSelection = useSmartSelection,
                cachedAppleAliases = cachedAppleAliases,
                onAppleAliasesResolved = onAppleAliasesResolved
            )
        }

        // Resolve the Apple catalog identity bridge while the cheap exact
        // provider queries are running. Previously this work started only
        // after exact and cleaned-title batches had both timed out, so a
        // cross-language track could spend 20–30 seconds waiting before its
        // localized query even began. The bridge is bounded and cancelled as
        // soon as an earlier identity-safe result wins.
        return supervisorScope {
            val shouldResolveAppleAliases =
                providerOrder.any { it != OnlineLyricProvider.AppleMusic } &&
                    (album.isNotBlank() || durationMs > 0L) &&
                    cachedAppleAliases.isEmpty()
            val appleAliasesDeferred = if (shouldResolveAppleAliases) {
                async(Dispatchers.IO) {
                    withTimeoutOrNull(APPLE_BRIDGE_TIMEOUT_MS) {
                        appleMusicProvider.resolveCatalogAliases(
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            mediaId = mediaId,
                            mediaUri = mediaUri
                        ).also { resolved ->
                            if (resolved.isNotEmpty()) {
                                val _ = runCatching { onAppleAliasesResolved?.invoke(resolved) }
                            }
                        }
                    }.orEmpty()
                }
            } else {
                null
            }
            val cleanTitle = cleanTitle(title)
            val cleanQuery = query.copy(title = cleanTitle)

            try {
                val exactAttempts = fetchAllProviders(
                    query = query,
                    providerOrder = providerOrder,
                    usedCleanTitleFallback = false,
                    queryVariant = "exact",
                    collectAllResults = false,
                    useSmartSelection = useSmartSelection,
                    versionReferenceTitle = title
                )
                val exactBest = selector.selectBestResult(
                    attempts = exactAttempts,
                    targetTitle = title,
                    targetArtist = artist,
                    providerOrder = providerOrder,
                    useSmartSelection = useSmartSelection,
                    targetAlbum = album,
                    targetDurationMs = durationMs,
                    versionReferenceTitle = title
                )
                if (exactBest != null &&
                    (!useSmartSelection || selector.isStrongExactMatch(exactBest, title, artist, album, durationMs))
                ) {
                    return@supervisorScope FetchOutcome(query, exactBest, exactAttempts, false)
                }

                var fallbackAttempts = exactAttempts
                var bestSoFar: LyricResult? = exactBest
                if (cleanTitle.isNotBlank() && cleanTitle != title) {
                    AppLogger.getInstance().i("OnlineLyric", "精确搜索未达强匹配，尝试清理标题: $cleanTitle")
                    val cleanAttempts = fetchAllProviders(
                        query = cleanQuery,
                        providerOrder = providerOrder,
                        usedCleanTitleFallback = true,
                        queryVariant = "clean_title",
                        collectAllResults = false,
                        useSmartSelection = useSmartSelection,
                        versionReferenceTitle = title
                    )
                    val allAttempts = exactAttempts + cleanAttempts
                    fallbackAttempts = allAttempts
                    val cleanBest = selector.selectBestResult(
                        attempts = allAttempts,
                        targetTitle = cleanTitle,
                        targetArtist = artist,
                        providerOrder = providerOrder,
                        useSmartSelection = useSmartSelection,
                        targetAlbum = album,
                        targetDurationMs = durationMs,
                        versionReferenceTitle = title
                    )
                    if (cleanBest != null && (bestSoFar == null || cleanBest.score > bestSoFar.score)) {
                        bestSoFar = cleanBest
                    }
                    if (bestSoFar != null &&
                        (!useSmartSelection || selector.isStrongExactMatch(
                            bestSoFar,
                            cleanTitle,
                            artist,
                            album,
                            durationMs,
                            versionReferenceTitle = title
                        ))
                    ) {
                        return@supervisorScope FetchOutcome(
                            query = query,
                            bestResult = bestSoFar,
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
            // Apple Catalog is an identity bridge, not the Apple lyric source.
            // Keep it available even when the user disables Apple lyrics; the
            // resulting localized aliases are used by the remaining sources.
            providerOrder.any { it != OnlineLyricProvider.AppleMusic } &&
            (album.isNotBlank() || durationMs > 0L)
        ) {
            val aliasAttempts = mutableListOf<ProviderAttempt>()
            val aliases = if (cachedAppleAliases.isNotEmpty()) {
                cachedAppleAliases
            } else {
                appleAliasesDeferred?.await().orEmpty()
            }
            val aliasProviders = providerOrder.filterNot { it == OnlineLyricProvider.AppleMusic }
            // Filter and deduplicate before applying the request budget.  The
            // source alias is often returned first; it must not consume the
            // slot that is intended for the localized CN alias.
            val filteredAliases = aliases
                .filterNot {
                    it.title.equals(title, ignoreCase = true) &&
                        it.artist.equals(artist, ignoreCase = true)
                }
                .distinctBy {
                    listOf(
                        it.title.trim().lowercase(),
                        it.artist.trim().lowercase(),
                        it.album.orEmpty().trim().lowercase()
                    ).joinToString("|")
                }
            // If the catalog only returns the source storefront, keep one
            // verified anchor visible and queryable instead of silently
            // collapsing the whole ISRC path to artist-only fallback.
            val aliasQueries = (filteredAliases.ifEmpty { aliases.take(1) })
                .take(MAX_APPLE_ALIAS_QUERIES)
            for (alias in aliasQueries) {
                val aliasQuery = query.copy(
                    title = alias.title,
                    artist = alias.artist,
                    album = alias.album ?: album,
                    durationMs = alias.durationMs ?: durationMs
                )
                val aliasVersionRefTitle = "$title ${aliasQuery.title}".trim()
                val attempts = fetchAllProviders(
                    query = aliasQuery,
                    providerOrder = aliasProviders,
                    usedCleanTitleFallback = true,
                    queryVariant = "apple_alias",
                    collectAllResults = false,
                    useSmartSelection = useSmartSelection,
                    versionReferenceTitle = aliasVersionRefTitle
                )
                aliasAttempts += attempts
                val aliasBest = selector.selectBestResult(
                    attempts = attempts,
                    targetTitle = aliasQuery.title,
                    targetArtist = aliasQuery.artist,
                    providerOrder = aliasProviders,
                    useSmartSelection = useSmartSelection,
                    targetAlbum = aliasQuery.album,
                    targetDurationMs = aliasQuery.durationMs,
                    versionReferenceTitle = aliasVersionRefTitle
                )
                if (aliasBest != null && (bestSoFar == null || aliasBest.score > bestSoFar.score)) {
                    bestSoFar = aliasBest
                }
                if (aliasBest != null &&
                    (!useSmartSelection || selector.isStrongExactMatch(
                        aliasBest,
                        aliasQuery.title,
                        aliasQuery.artist,
                        aliasQuery.album,
                        aliasQuery.durationMs,
                        versionReferenceTitle = aliasVersionRefTitle
                    ))
                ) {
                    return@supervisorScope FetchOutcome(
                        query = query,
                        bestResult = bestSoFar,
                        attempts = fallbackAttempts + aliasAttempts,
                        usedCleanTitleFallback = true
                    )
                }
            }
            fallbackAttempts = fallbackAttempts + aliasAttempts
        }

        // If earlier stages already yielded a valid match (e.g. substring),
        // prefer it over an aggressive titleless artist-only search.
        if (bestSoFar != null) {
            return@supervisorScope FetchOutcome(
                query = query,
                bestResult = bestSoFar,
                attempts = fallbackAttempts,
                usedCleanTitleFallback = fallbackAttempts.size > exactAttempts.size
            )
        }

        // Cross-language fallback: search by artist + album only when
        // title-based queries produced no identity-safe result. Evaluate
        // ONLY this batch (never resurrect rejected title-mismatched attempts
        // from earlier stages with an empty targetTitle).
        if (artist.isNotBlank() && album.isNotBlank() && durationMs > 0L) {
            val artistQuery = query.copy(title = "")
            val artistAttempts = fetchAllProviders(
                query = artistQuery,
                providerOrder = providerOrder,
                usedCleanTitleFallback = true,
                queryVariant = "artist_only",
                collectAllResults = false,
                useSmartSelection = useSmartSelection,
                versionReferenceTitle = title
            )
            val allAttempts = fallbackAttempts + artistAttempts
            val artistBest = selector.selectBestResult(
                attempts = artistAttempts,
                targetTitle = "",
                targetArtist = artist,
                providerOrder = providerOrder,
                useSmartSelection = useSmartSelection,
                targetAlbum = album,
                targetDurationMs = durationMs,
                versionReferenceTitle = title
            )
            return@supervisorScope FetchOutcome(
                query = query,
                bestResult = artistBest,
                attempts = allAttempts,
                usedCleanTitleFallback = allAttempts.size > exactAttempts.size
            )
        }

        FetchOutcome(
            query = query,
            bestResult = null,
            attempts = fallbackAttempts,
            usedCleanTitleFallback = fallbackAttempts.size > exactAttempts.size
        )
            } finally {
                appleAliasesDeferred?.cancel()
            }
        }
    }

    /**
     * Exhaustive mode used by rematch/debug surfaces. Every bounded query
     * variant keeps every provider attempt and scores it against the query that
     * produced it; no fast result is allowed to cancel another source.
     */
    private suspend fun fetchDiagnosticResults(
        query: LyricQuery,
        providerOrder: List<OnlineLyricProvider>,
        useSmartSelection: Boolean,
        cachedAppleAliases: List<AppleMusicCatalogAlias>,
        onAppleAliasesResolved: (suspend (List<AppleMusicCatalogAlias>) -> Unit)?
    ): FetchOutcome = coroutineScope {
        val plans = mutableListOf(
            DiagnosticQueryPlan(query, providerOrder, "exact", false)
        )

        val normalizedTitle = cleanTitle(query.title)
        if (normalizedTitle.isNotBlank() && normalizedTitle != query.title) {
            plans += DiagnosticQueryPlan(
                query.copy(title = normalizedTitle),
                providerOrder,
                "clean_title",
                true
            )
        }
        if (query.artist.isNotBlank() && query.album.isNotBlank() && query.durationMs > 0L) {
            plans += DiagnosticQueryPlan(
                query.copy(title = ""),
                providerOrder,
                "artist_album",
                true
            )
        }

        val shouldResolveAliases =
            // The Apple Catalog bridge is independent from the Apple lyric
            // provider toggle; it supplies identity-backed terms to other
            // providers even when Apple lyrics are disabled.
            providerOrder.any { it != OnlineLyricProvider.AppleMusic } &&
                (query.album.isNotBlank() || query.durationMs > 0L)
        val aliasesDeferred = async {
            if (!shouldResolveAliases) return@async DiagnosticAliasResolution(emptyList(), 0L)
            val startedAt = System.currentTimeMillis()
            val resolved = if (cachedAppleAliases.isNotEmpty()) {
                cachedAppleAliases
            } else {
                appleMusicProvider.resolveCatalogAliases(
                    title = query.title,
                    artist = query.artist,
                    album = query.album,
                    durationMs = query.durationMs,
                    mediaId = query.mediaId,
                    mediaUri = query.mediaUri
                ).also { aliases ->
                    if (aliases.isNotEmpty()) onAppleAliasesResolved?.invoke(aliases)
                }
            }
            DiagnosticAliasResolution(
                aliases = resolved,
                durationMs = System.currentTimeMillis() - startedAt
            )
        }

        suspend fun execute(plan: DiagnosticQueryPlan): DiagnosticQueryResult {
            val versionRefTitle = if (plan.variant.startsWith("apple_alias:")) {
                "${query.title} ${plan.query.title}".trim()
            } else {
                query.title
            }
            val attempts = fetchAllProviders(
                query = plan.query,
                providerOrder = plan.providers,
                usedCleanTitleFallback = plan.isFallback,
                queryVariant = plan.variant,
                collectAllResults = true,
                useSmartSelection = useSmartSelection,
                versionReferenceTitle = versionRefTitle
            )
            val best = selector.selectBestResult(
                attempts = attempts,
                targetTitle = plan.query.title,
                targetArtist = plan.query.artist,
                providerOrder = plan.providers,
                useSmartSelection = useSmartSelection,
                targetAlbum = plan.query.album,
                targetDurationMs = plan.query.durationMs,
                versionReferenceTitle = versionRefTitle
            )
            return DiagnosticQueryResult(plan, attempts, best)
        }

        val baseResults = plans.map { plan -> async { execute(plan) } }.awaitAll()
        val aliasResolution = aliasesDeferred.await()
        val filteredAliases = aliasResolution.aliases
            .filterNot {
                it.title.equals(query.title, ignoreCase = true) &&
                    it.artist.equals(query.artist, ignoreCase = true)
            }
            .distinctBy {
                listOf(
                    it.title.trim().lowercase(),
                    it.artist.trim().lowercase(),
                    it.album.orEmpty().trim().lowercase()
                ).joinToString("|")
            }
        val aliases = (filteredAliases.ifEmpty { aliasResolution.aliases.take(1) })
            .take(MAX_DIAGNOSTIC_ALIAS_QUERIES)
        val aliasProviders = providerOrder.filterNot { it == OnlineLyricProvider.AppleMusic }
        val aliasResults = aliases.map { alias ->
            val aliasQuery = query.copy(
                title = alias.title,
                artist = alias.artist,
                album = alias.album ?: query.album,
                durationMs = alias.durationMs ?: query.durationMs
            )
            async {
                execute(
                    DiagnosticQueryPlan(
                        query = aliasQuery,
                        providers = aliasProviders,
                        variant = "apple_alias:${alias.storefront}",
                        isFallback = true
                    )
                )
            }
        }.awaitAll()

        val results = baseResults + aliasResults
        val providerPriority = providerOrder.withIndex().associate { it.value to it.index }
        val best = results.mapNotNull { it.bestResult }
            .sortedWith(
                compareByDescending<LyricResult> { it.score }
                    .thenByDescending { it.hasSyllable }
                    .thenBy { providerPriority[it.provider] ?: Int.MAX_VALUE }
            )
            .firstOrNull()
        val isrcBridgeAttempt = if (shouldResolveAliases) {
            val verifiedAliases = aliasResolution.aliases
            val canonicalIsrc = verifiedAliases.firstNotNullOfOrNull { it.isrc }
            val storefronts = verifiedAliases.map { it.storefront }.distinct().joinToString(",")
            val resolved = canonicalIsrc != null
            ProviderAttempt(
                provider = OnlineLyricProvider.AppleMusic,
                result = LyricResult(
                    api = "AppleMusic ISRC",
                    lyrics = null,
                    parsedLines = null,
                    hasSyllable = false,
                    provider = OnlineLyricProvider.AppleMusic,
                    matchedTitle = verifiedAliases.firstOrNull()?.title,
                    matchedArtist = verifiedAliases.firstOrNull()?.artist,
                    matchedAlbum = verifiedAliases.firstOrNull()?.album,
                    matchedDurationMs = verifiedAliases.firstOrNull()?.durationMs,
                    providerTrackId = verifiedAliases.firstOrNull()?.providerTrackId,
                    isrc = canonicalIsrc,
                    score = 0,
                    identityScore = 0,
                    identityEvidence = if (resolved) "catalog_isrc_verified" else "catalog_isrc_unresolved",
                    error = if (resolved) {
                        "ISRC 桥接成功：$canonicalIsrc；已验证区服：${storefronts.ifBlank { "—" }}"
                    } else {
                        "ISRC 桥接未生成别名：Apple 目录令牌、歌曲锚点、ISRC 或目标区服结果不可用"
                    }
                ),
                durationMs = aliasResolution.durationMs,
                usedCleanTitleFallback = false,
                queryTitle = query.title,
                queryArtist = query.artist,
                queryVariant = if (resolved) "isrc_bridge:verified" else "isrc_bridge:unresolved"
            )
        } else {
            null
        }
        val allAttempts = (results.flatMap { it.attempts } + listOfNotNull(isrcBridgeAttempt))
            .sortedWith(
                compareBy<ProviderAttempt> { providerPriority[it.provider] ?: Int.MAX_VALUE }
                    .thenByDescending { it.result?.score ?: Int.MIN_VALUE }
                    .thenBy { it.queryVariant }
            )
        FetchOutcome(
            query = query,
            bestResult = best,
            attempts = allAttempts,
            usedCleanTitleFallback = results.any { result ->
                result.plan.isFallback && result.attempts.any { it.result != null }
            }
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun fetchAllProviders(
        query: LyricQuery,
        providerOrder: List<OnlineLyricProvider>,
        usedCleanTitleFallback: Boolean,
        queryVariant: String = if (usedCleanTitleFallback) "fallback" else "exact",
        collectAllResults: Boolean = false,
        useSmartSelection: Boolean = true,
        versionReferenceTitle: String = query.title
    ): List<ProviderAttempt> {
        return withContext(Dispatchers.IO) {
            raceProviders(
                query = query,
                providerOrder = providerOrder,
                usedCleanTitleFallback = usedCleanTitleFallback,
                queryVariant = queryVariant,
                collectAllResults = collectAllResults,
                useSmartSelection = useSmartSelection,
                versionReferenceTitle = versionReferenceTitle,
                fetchTimeoutMs = if (collectAllResults) DIAGNOSTIC_FETCH_TIMEOUT_MS else FETCH_TIMEOUT_MS,
                fetchSingleProvider = { provider ->
                    when (provider) {
                        OnlineLyricProvider.QQMusic -> qqMusicProvider.fetch(
                            query.title,
                            query.artist,
                            query.album,
                            query.durationMs,
                            versionReferenceTitle = versionReferenceTitle
                        )
                        OnlineLyricProvider.Kugou -> kugouProvider.fetch(
                            query.title,
                            query.artist,
                            query.album,
                            query.durationMs,
                            versionReferenceTitle = versionReferenceTitle
                        )
                        OnlineLyricProvider.SodaMusic -> sodaMusicProvider.fetch(
                            query.title,
                            query.artist,
                            query.album,
                            query.durationMs,
                            versionReferenceTitle = versionReferenceTitle
                        )
                        OnlineLyricProvider.Lrclib -> lrclibProvider.fetch(
                            query.title,
                            query.artist,
                            query.album,
                            query.durationMs,
                            versionReferenceTitle = versionReferenceTitle
                        )
                        OnlineLyricProvider.Netease -> neteaseProvider.fetch(
                            query.title,
                            query.artist,
                            query.album,
                            query.durationMs,
                            versionReferenceTitle = versionReferenceTitle
                        )
                        OnlineLyricProvider.LrcApi -> lrcApiProvider.fetch(query.title, query.artist)
                        OnlineLyricProvider.AppleMusic -> appleMusicProvider.fetch(
                            query.title,
                            query.artist,
                            query.album,
                            query.durationMs,
                            versionReferenceTitle = versionReferenceTitle
                        )
                        OnlineLyricProvider.Musixmatch -> musixmatchProvider.fetch(
                            query.title,
                            query.artist,
                            query.album,
                            query.durationMs,
                            versionReferenceTitle = versionReferenceTitle
                        )
                    }
                }
            )
        }
    }

    /**
     * Syllable-aware and priority-aware provider race.
     *
     * Exposed as `internal` with an injectable provider fetcher and timing
     * parameters so unit tests can deterministically verify that fast plain-LRC
     * sources do not prematurely cancel slower word-level or higher-priority sources.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun raceProviders(
        query: LyricQuery,
        providerOrder: List<OnlineLyricProvider>,
        usedCleanTitleFallback: Boolean,
        queryVariant: String,
        collectAllResults: Boolean,
        useSmartSelection: Boolean,
        versionReferenceTitle: String = query.title,
        fetchTimeoutMs: Long = FETCH_TIMEOUT_MS,
        syllableGracePeriodMs: Long = SYLLABLE_GRACE_PERIOD_MS,
        lrcWaitForSyllableMs: Long = LRC_WAIT_FOR_SYLLABLE_MS,
        plainNoSyllableGraceMs: Long = PLAIN_NO_SYLLABLE_GRACE_MS,
        prioritySidecarGraceMs: Long = PRIORITY_SIDECAR_GRACE_MS,
        fetchSingleProvider: suspend (OnlineLyricProvider) -> LyricResult?
    ): List<ProviderAttempt> = supervisorScope {
        if (providerOrder.isEmpty()) return@supervisorScope emptyList()
        val orderIndex = providerOrder.withIndex().associate { it.value to it.index }
        val attemptChannel = Channel<ProviderAttempt>(capacity = Channel.UNLIMITED)

        val deferreds = providerOrder.map { provider ->
            async {
                val startedAt = System.currentTimeMillis()
                val result = try {
                    fetchSingleProvider(provider)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    LyricResult(
                        api = provider.id,
                        lyrics = null,
                        parsedLines = null,
                        hasSyllable = false,
                        provider = provider,
                        error = error.message ?: error::class.simpleName
                    )
                }
                ProviderAttempt(
                    provider = provider,
                    result = result,
                    durationMs = System.currentTimeMillis() - startedAt,
                    usedCleanTitleFallback = usedCleanTitleFallback,
                    queryTitle = query.title,
                    queryArtist = query.artist,
                    queryVariant = queryVariant
                ).also { attemptChannel.trySend(it) }
            }
        }

        try {
            if (collectAllResults) {
                val completedResults = linkedMapOf<OnlineLyricProvider, ProviderAttempt>()
                withTimeoutOrNull(fetchTimeoutMs) {
                    while (completedResults.size < providerOrder.size) {
                        val attempt = attemptChannel.receive()
                        completedResults[attempt.provider] = attempt
                    }
                }
                deferreds.forEach { deferred ->
                    if (deferred.isCompleted && !deferred.isCancelled) {
                        runCatching { deferred.getCompleted() }
                            .getOrNull()
                            ?.let { completedResults.putIfAbsent(it.provider, it) }
                    } else {
                        deferred.cancel()
                    }
                }
                providerOrder.filterNot { it in completedResults }.forEach { provider ->
                    completedResults[provider] = ProviderAttempt(
                        provider = provider,
                        result = LyricResult(
                            api = provider.id,
                            lyrics = null,
                            parsedLines = null,
                            hasSyllable = false,
                            provider = provider,
                            error = "Timed out after ${fetchTimeoutMs}ms"
                        ),
                        durationMs = fetchTimeoutMs,
                        usedCleanTitleFallback = usedCleanTitleFallback,
                        queryTitle = query.title,
                        queryArtist = query.artist,
                        queryVariant = queryVariant
                    )
                }
                return@supervisorScope completedResults.values.sortedBy { orderIndex[it.provider] ?: Int.MAX_VALUE }
            }

            val completedByProvider = linkedMapOf<OnlineLyricProvider, ProviderAttempt>()
            val batchStartMs = System.currentTimeMillis()
            var softDeadlineMs = batchStartMs + fetchTimeoutMs
            var hasHighConfidenceSyllable = false
            var hasHighConfidencePlain = false
            val activeSyllableProviders = providerOrder.filter { it in SYLLABLE_PROVIDERS }.toMutableSet()

            while (completedByProvider.size < providerOrder.size) {
                val remainingMs = (softDeadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
                if (remainingMs == 0L) break
                val nextAttempt = withTimeoutOrNull(remainingMs) { attemptChannel.receive() } ?: break
                completedByProvider[nextAttempt.provider] = nextAttempt
                activeSyllableProviders.remove(nextAttempt.provider)

                if (completedByProvider.size >= providerOrder.size) break

                val result = nextAttempt.result
                val now = System.currentTimeMillis()
                if (useSmartSelection) {
                    val isHighSyllable = selector.isHighConfidenceSyllableMatch(
                        result,
                        query.title,
                        query.artist,
                        query.album,
                        query.durationMs,
                        versionReferenceTitle
                    )
                    val isPotential = isHighSyllable || selector.isPotentiallyMatching(
                        result,
                        query.title,
                        query.artist,
                        query.album,
                        query.durationMs,
                        versionReferenceTitle
                    )
                    val isAcceptable = isPotential || selector.isAcceptableMatch(
                        result,
                        query.title,
                        query.artist,
                        query.album,
                        query.durationMs,
                        versionReferenceTitle
                    )
                    if (isHighSyllable) {
                        hasHighConfidenceSyllable = true
                        softDeadlineMs = minOf(softDeadlineMs, now + syllableGracePeriodMs)
                    } else if (isPotential && !hasHighConfidenceSyllable) {
                        hasHighConfidencePlain = true
                        val graceMs = if (activeSyllableProviders.isNotEmpty()) {
                            lrcWaitForSyllableMs
                        } else {
                            plainNoSyllableGraceMs
                        }
                        softDeadlineMs = minOf(softDeadlineMs, now + graceMs)
                    } else if (isAcceptable && !hasHighConfidenceSyllable && !hasHighConfidencePlain) {
                        // Even a medium-confidence acceptable match should bound
                        // unreachable providers so the batch does not stall for the full timeout.
                        softDeadlineMs = minOf(softDeadlineMs, now + lrcWaitForSyllableMs)
                    } else if (hasHighConfidencePlain && !hasHighConfidenceSyllable && activeSyllableProviders.isEmpty()) {
                        softDeadlineMs = minOf(softDeadlineMs, now + plainNoSyllableGraceMs)
                    }
                } else {
                    val highestUsableIndex = providerOrder.indexOfFirst { provider ->
                        val attempt = completedByProvider[provider]
                        attempt != null && selector.isPotentiallyMatching(
                            attempt.result,
                            query.title,
                            query.artist,
                            query.album,
                            query.durationMs,
                            versionReferenceTitle
                        )
                    }
                    if (highestUsableIndex >= 0) {
                        val higherStillPending = providerOrder.take(highestUsableIndex)
                            .any { it !in completedByProvider }
                        if (!higherStillPending) {
                            softDeadlineMs = minOf(softDeadlineMs, now + prioritySidecarGraceMs)
                        }
                    }
                }
            }

            deferreds.forEach { deferred ->
                if (deferred.isCompleted && !deferred.isCancelled) {
                    runCatching { deferred.getCompleted() }
                        .getOrNull()
                        ?.let { completedByProvider.putIfAbsent(it.provider, it) }
                } else {
                    deferred.cancel()
                }
            }
            completedByProvider.values.sortedBy { orderIndex[it.provider] ?: Int.MAX_VALUE }
        } catch (cancelled: CancellationException) {
            deferreds.forEach { it.cancel() }
            throw cancelled
        } catch (e: Exception) {
            deferreds.forEach { it.cancel() }
            AppLogger.getInstance().e("OnlineLyric", "获取歌词失败: ${e.message}")
            emptyList()
        } finally {
            attemptChannel.close()
        }
    }

    // 清理标题：委托统一的词边界归一化实现
    private fun cleanTitle(title: String): String = CandidateMatcher.cleanTitle(title)

    private companion object {
        private const val FETCH_TIMEOUT_MS = 6_500L
        private const val DIAGNOSTIC_FETCH_TIMEOUT_MS = 10_000L
        private const val APPLE_BRIDGE_TIMEOUT_MS = 10_000L
        private const val SYLLABLE_GRACE_PERIOD_MS = 1_200L
        private const val LRC_WAIT_FOR_SYLLABLE_MS = 3_500L
        private const val PLAIN_NO_SYLLABLE_GRACE_MS = 500L
        private const val PRIORITY_SIDECAR_GRACE_MS = 800L
        private const val MAX_APPLE_ALIAS_QUERIES = 3
        private const val MAX_DIAGNOSTIC_ALIAS_QUERIES = 8

        private val SYLLABLE_PROVIDERS = setOf(
            OnlineLyricProvider.QQMusic,
            OnlineLyricProvider.Kugou,
            OnlineLyricProvider.SodaMusic,
            OnlineLyricProvider.Netease,
            OnlineLyricProvider.AppleMusic,
            OnlineLyricProvider.Musixmatch
        )
    }
}

