package com.example.islandlyrics.lyrics.online

import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.provider.KugouLyricProvider
import com.example.islandlyrics.lyrics.online.provider.LrcApiLyricProvider
import com.example.islandlyrics.lyrics.online.provider.LrclibLyricProvider
import com.example.islandlyrics.lyrics.online.provider.NeteaseLyricProvider
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import com.example.islandlyrics.lyrics.online.provider.QqMusicLyricProvider
import com.example.islandlyrics.lyrics.online.provider.SodaMusicLyricProvider
import com.example.islandlyrics.lyrics.online.selection.OnlineLyricSelector

import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.*
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
        val artist: String
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
        val translationLyrics: String? = null,
        val romanLyrics: String? = null,
        val error: String? = null            // 错误信息
    )

    data class ProviderAttempt(
        val provider: OnlineLyricProvider,
        val result: LyricResult?,
        val durationMs: Long,
        val usedCleanTitleFallback: Boolean
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
    private val selector = OnlineLyricSelector(::cleanTitle)
    
    /**
     * 从多个API获取歌词并选择最佳结果
     */
    suspend fun fetchBestLyrics(
        title: String,
        artist: String,
        providerOrderIds: List<String> = OnlineLyricProvider.defaultIds(),
        useSmartSelection: Boolean = true
    ): LyricResult? {
        return fetchLyrics(title, artist, providerOrderIds, useSmartSelection).bestResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun fetchLyrics(
        title: String,
        artist: String,
        providerOrderIds: List<String> = OnlineLyricProvider.defaultIds(),
        useSmartSelection: Boolean = true
    ): FetchOutcome {
        val providerOrder = OnlineLyricProvider.normalizeOrder(providerOrderIds)
        val query = LyricQuery(title = title, artist = artist)
        if (!networkAllowed()) {
            AppLogger.getInstance().i("OnlineLyric", "Offline mode enabled, online lyric fetch blocked")
            return FetchOutcome(query, null, emptyList(), false)
        }
        val exactAttempts = fetchAllProviders(query, providerOrder, usedCleanTitleFallback = false)
        val exactBest = selector.selectBestResult(exactAttempts, title, artist, providerOrder, useSmartSelection)
        if (exactBest != null) {
            return FetchOutcome(query, exactBest, exactAttempts, false)
        }

        val cleanTitle = cleanTitle(title)
        if (cleanTitle != title) {
            AppLogger.getInstance().i("OnlineLyric", "精确搜索未找到，尝试清理标题: $cleanTitle")
            val cleanQuery = query.copy(title = cleanTitle)
            val cleanAttempts = fetchAllProviders(cleanQuery, providerOrder, usedCleanTitleFallback = true)
            return FetchOutcome(
                query = query,
                bestResult = selector.selectBestResult(cleanAttempts, cleanTitle, artist, providerOrder, useSmartSelection),
                attempts = exactAttempts + cleanAttempts,
                usedCleanTitleFallback = cleanAttempts.any { it.result != null }
            )
        }

        return FetchOutcome(query, null, exactAttempts, false)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun fetchAllProviders(
        query: LyricQuery,
        providerOrder: List<OnlineLyricProvider>,
        usedCleanTitleFallback: Boolean
    ): List<ProviderAttempt> {
        return withContext(Dispatchers.IO) {
            try {
                val deferreds = providerOrder.map { provider ->
                    async {
                        val startedAt = System.currentTimeMillis()
                        val result = when (provider) {
                            OnlineLyricProvider.QQMusic -> qqMusicProvider.fetch(query.title, query.artist)
                            OnlineLyricProvider.Kugou -> kugouProvider.fetch(query.title, query.artist)
                            OnlineLyricProvider.SodaMusic -> sodaMusicProvider.fetch(query.title, query.artist)
                            OnlineLyricProvider.Lrclib -> lrclibProvider.fetch(query.title, query.artist)
                            OnlineLyricProvider.Netease -> neteaseProvider.fetch(query.title, query.artist)
                            OnlineLyricProvider.LrcApi -> lrcApiProvider.fetch(query.title, query.artist)
                        }
                        ProviderAttempt(
                            provider = provider,
                            result = result,
                            durationMs = System.currentTimeMillis() - startedAt,
                            usedCleanTitleFallback = usedCleanTitleFallback
                        )
                    }
                }

                try {
                    withTimeout(10000) {
                        deferreds.awaitAll()
                    }
                } catch (e: TimeoutCancellationException) {
                    AppLogger.getInstance().i("OnlineLyric", "部分或者全部请求超时，尝试收集已完成结果")
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
}

