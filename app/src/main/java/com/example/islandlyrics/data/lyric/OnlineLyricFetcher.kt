package com.example.islandlyrics.data.lyric

import android.util.Base64
import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import javax.net.ssl.HostnameVerifier
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 在线歌词获取器
 * 支持从多个在线源(酷狗/网易/LrcApi)获取带时间轴的歌词
 */
class OnlineLyricFetcher {

    data class LyricQuery(
        val title: String,
        val artist: String
    )
    
    // 歌词行数据类
    data class LyricLine(
        val startTime: Long,  // 毫秒
        val endTime: Long,    // 毫秒
        val text: String,
        val syllables: List<SyllableInfo>? = null  // 逐字信息
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
        val exactAttempts = fetchAllProviders(query, providerOrder, usedCleanTitleFallback = false)
        val exactBest = selectBestResult(exactAttempts, title, artist, providerOrder, useSmartSelection)
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
                bestResult = selectBestResult(cleanAttempts, cleanTitle, artist, providerOrder, useSmartSelection),
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
                            OnlineLyricProvider.QQMusic -> fetchQQMusicAsync(query.title, query.artist)
                            OnlineLyricProvider.Kugou -> fetchKugouAsync(query.title, query.artist)
                            OnlineLyricProvider.SodaMusic -> fetchSodaMusicAsync(query.title, query.artist)
                            OnlineLyricProvider.Lrclib -> fetchLrclibAsync(query.title, query.artist)
                            OnlineLyricProvider.Netease -> fetchNeteaseAsync(query.title, query.artist)
                            OnlineLyricProvider.LrcApi -> fetchLrcApiAsync(query.title, query.artist)
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
    
    // ========== QQ音乐API ==========

    private suspend fun fetchQQMusicAsync(title: String, artist: String): LyricResult? = withContext(Dispatchers.IO) {
        try {
            val keyword = "$title $artist"
            val searchPayload = """
                {"music.search.SearchCgiService":{"method":"DoSearchForQQMusicDesktop","module":"music.search.SearchCgiService","param":{"num_per_page":10,"page_num":1,"query":"${escapeJson(keyword)}","search_type":0}}}
            """.trimIndent()

            val searchResponse = postJsonString(
                url = "https://u.y.qq.com/cgi-bin/musicu.fcg",
                bodyJson = searchPayload,
                headers = qqHeaders("https://c.y.qq.com/")
            ) ?: return@withContext null

            val searchJson = JSONObject(searchResponse)
            val songs = searchJson
                .optJSONObject("music.search.SearchCgiService")
                ?.optJSONObject("data")
                ?.optJSONObject("body")
                ?.optJSONObject("song")
                ?.optJSONArray("list")
                ?: searchJson
                    .optJSONObject("req_1")
                    ?.optJSONObject("data")
                    ?.optJSONObject("body")
                    ?.optJSONObject("song")
                    ?.optJSONArray("list")

            if (songs == null || songs.length() == 0) return@withContext null

            val firstSong = songs.getJSONObject(0)
            val songMid = firstSong.optString("mid", "")
            val matchedTitle = firstSong.optString("title", firstSong.optString("name", ""))
            val matchedArtist = firstSong.optJSONArray("singer")
                ?.let { singers ->
                    buildString {
                        for (index in 0 until singers.length()) {
                            val name = singers.optJSONObject(index)?.optString("name").orEmpty()
                            if (name.isBlank()) continue
                            if (isNotEmpty()) append("/")
                            append(name)
                        }
                    }
                }
                .orEmpty()

            if (songMid.isBlank()) {
                return@withContext LyricResult(
                    api = "QQMusic",
                    lyrics = null,
                    parsedLines = null,
                    hasSyllable = false,
                    provider = OnlineLyricProvider.QQMusic,
                    matchedTitle = matchedTitle,
                    matchedArtist = matchedArtist,
                    error = "无 songMid"
                )
            }

            val callback = "MusicJsonCallback_lrc"
            val lyricResponse = postForm(
                url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg",
                form = linkedMapOf(
                    "callback" to callback,
                    "pcachetime" to System.currentTimeMillis().toString(),
                    "songmid" to songMid,
                    "g_tk" to "5381",
                    "jsonpCallback" to callback,
                    "loginUin" to "0",
                    "hostUin" to "0",
                    "format" to "jsonp",
                    "inCharset" to "utf8",
                    "outCharset" to "utf8",
                    "notice" to "0",
                    "platform" to "yqq",
                    "needNewCode" to "0"
                ),
                headers = qqHeaders("https://c.y.qq.com/")
            ) ?: return@withContext null

            val lyricJsonText = unwrapJsonp(callback, lyricResponse) ?: return@withContext null
            val lyricJson = JSONObject(lyricJsonText)
            val lyricContent = decodeBase64Text(lyricJson.optString("lyric", ""))
            val transContent = decodeBase64Text(lyricJson.optString("trans", ""))
            val mergedContent = lyricContent.ifBlank { transContent }

            if (mergedContent.isBlank()) {
                return@withContext LyricResult(
                    api = "QQMusic",
                    lyrics = null,
                    parsedLines = null,
                    hasSyllable = false,
                    provider = OnlineLyricProvider.QQMusic,
                    matchedTitle = matchedTitle,
                    matchedArtist = matchedArtist,
                    error = "无歌词内容"
                )
            }

            val parsedLines = parseLrcLyrics(mergedContent)
            LyricResult(
                api = "QQMusic",
                lyrics = mergedContent,
                parsedLines = parsedLines,
                hasSyllable = false,
                provider = OnlineLyricProvider.QQMusic,
                matchedTitle = matchedTitle,
                matchedArtist = matchedArtist
            )
        } catch (e: Exception) {
            AppLogger.getInstance().log("OnlineLyric", "QQMusic API错误: ${e.message}")
            null
        }
    }

    // ========== 酷狗API ==========
    
    private suspend fun fetchKugouAsync(title: String, artist: String): LyricResult? = withContext(Dispatchers.IO) {
        try {
            val keywords = "$title $artist"
            // V3 Search API (step 1)
            val searchUrl = "https://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=${keywords.encodeURL()}&page=1&pagesize=20&showtype=1"

            val searchResponse = suspendCancellableCoroutine<String?> { continuation ->
                val request = Request.Builder().url(searchUrl).build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response.body.string())
                    }
                })
            }

            if (searchResponse == null) {
                AppLogger.getInstance().log("OnlineLyric", "Kugou搜索请求失败")
                return@withContext null
            }

            val searchJson = JSONObject(searchResponse)
            val dataObj = searchJson.optJSONObject("data")
            val infoArray = dataObj?.optJSONArray("info")

            if (infoArray == null || infoArray.length() == 0) {
                AppLogger.getInstance().log("OnlineLyric", "Kugou未找到歌曲")
                return@withContext null
            }

            val firstSong = infoArray.getJSONObject(0)
            val matchedTitle = firstSong.optString("songname", "")
            val matchedArtist = firstSong.optString("singername", "")
            val hash = firstSong.optString("hash", "")

            if (hash.isEmpty()) {
                return@withContext LyricResult("Kugou", null, null, false, provider = OnlineLyricProvider.Kugou, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "无歌曲hash")
            }

            // 获取歌词 (step 2)
            val lyricUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=&duration=&hash=$hash"

            val lyricResponse = suspendCancellableCoroutine<String?> { continuation ->
                val request = Request.Builder().url(lyricUrl).build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response.body.string())
                    }
                })
            }

            if (lyricResponse == null) {
                return@withContext LyricResult("Kugou", null, null, false, provider = OnlineLyricProvider.Kugou, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "歌词请求失败")
            }

            val lyricJson = JSONObject(lyricResponse)
            val candidates = lyricJson.optJSONArray("candidates")

            if (candidates == null || candidates.length() == 0) {
                return@withContext LyricResult("Kugou", null, null, false, provider = OnlineLyricProvider.Kugou, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "无候选歌词")
            }

            val lyricInfo = candidates.getJSONObject(0)
            var lyricEncoded = lyricInfo.optString("content", "")

            // 如果搜索结果中没有直接包含歌词内容，使用 id 和 accesskey 下载
            if (lyricEncoded.isEmpty()) {
                val id = lyricInfo.optString("id", "")
                val accessKey = lyricInfo.optString("accesskey", "")
                
                if (id.isNotEmpty() && accessKey.isNotEmpty()) {
                    val downloadUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=krc&charset=utf8"
                    
                    val downloadResponse = suspendCancellableCoroutine<String?> { continuation ->
                        val request = Request.Builder().url(downloadUrl).build()
                        client.newCall(request).enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                continuation.resume(null)
                            }
                            override fun onResponse(call: Call, response: Response) {
                                continuation.resume(response.body.string())
                            }
                        })
                    }

                    if (downloadResponse != null) {
                        try {
                            val downloadJson = JSONObject(downloadResponse)
                            lyricEncoded = downloadJson.optString("content", "")
                        } catch (e: Exception) {
                            AppLogger.getInstance().log("OnlineLyric", "Kugou下载响应解析失败: ${e.message}")
                        }
                    }
                }
            }

            if (lyricEncoded.isEmpty()) {
                val msg = "歌词内容为空"
                return@withContext LyricResult("Kugou", null, null, false, provider = OnlineLyricProvider.Kugou, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = msg)
            }

            val lyricContent = decodeKugouLyric(lyricEncoded)
            val hasSyllable = lyricContent.contains("<") && lyricContent.contains(">")
            val parsedLines = if (hasSyllable) parseKrcLyrics(lyricContent) else parseLrcLyrics(lyricContent)

            LyricResult("Kugou", lyricContent, parsedLines, hasSyllable, provider = OnlineLyricProvider.Kugou, matchedTitle = matchedTitle, matchedArtist = matchedArtist)

        } catch (e: Exception) {
            AppLogger.getInstance().log("OnlineLyric", "Kugou API错误: ${e.message}")
            null
        }
    }
    
    // ========== 网易云API ==========

    private suspend fun fetchSodaMusicAsync(title: String, artist: String): LyricResult? = withContext(Dispatchers.IO) {
        try {
            val keyword = "$title $artist"
            val searchUrl = "https://api.qishui.com/luna/pc/search/track?aid=386088&app_name=&region=&geo_region=&os_region=&sim_region=&device_id=&cdid=&iid=&version_name=&version_code=&channel=&build_mode=&network_carrier=&ac=&tz_name=&resolution=&device_platform=&device_type=&os_version=&fp=&q=${keyword.encodeURL()}&cursor=&search_id=&search_method=input&debug_params=&from_search_id=&search_scene="
            val searchResponse = executeRequest(searchUrl, headers = sodaHeaders()) ?: return@withContext null
            val searchJson = JSONObject(searchResponse)
            val resultGroups = searchJson.optJSONArray("result_groups")
            if (resultGroups == null || resultGroups.length() == 0) return@withContext null

            var firstTrack: JSONObject? = null
            for (groupIndex in 0 until resultGroups.length()) {
                val group = resultGroups.optJSONObject(groupIndex) ?: continue
                val data = group.optJSONArray("data") ?: continue
                for (itemIndex in 0 until data.length()) {
                    val item = data.optJSONObject(itemIndex) ?: continue
                    val meta = item.optJSONObject("meta")
                    if (meta?.optString("item_type") != "track") continue
                    firstTrack = item.optJSONObject("entity")?.optJSONObject("track")
                    if (firstTrack != null) break
                }
                if (firstTrack != null) break
            }

            if (firstTrack == null) return@withContext null

            val trackId = firstTrack.optString("id", "")
            val matchedTitle = firstTrack.optString("name", "")
            val artists = firstTrack.optJSONArray("artists")
            val matchedArtist = buildString {
                if (artists != null) {
                    for (index in 0 until artists.length()) {
                        val name = artists.optJSONObject(index)?.optString("name").orEmpty()
                        if (name.isBlank()) continue
                        if (isNotEmpty()) append("/")
                        append(name)
                    }
                }
            }

            if (trackId.isBlank()) {
                return@withContext LyricResult(
                    api = "SodaMusic",
                    lyrics = null,
                    parsedLines = null,
                    hasSyllable = false,
                    provider = OnlineLyricProvider.SodaMusic,
                    matchedTitle = matchedTitle,
                    matchedArtist = matchedArtist,
                    error = "无 track_id"
                )
            }

            val detailResponse = postForm(
                url = "https://api.qishui.com/luna/pc/track_v2",
                form = linkedMapOf(
                    "track_id" to trackId,
                    "media_type" to "track",
                    "queue_type" to ""
                ),
                headers = sodaHeaders()
            ) ?: return@withContext null

            val detailJson = JSONObject(detailResponse)
            val lyric = detailJson.optJSONObject("lyric")
            val lyricContent = lyric?.optString("content", "").orEmpty()
            val lyricType = lyric?.optString("type", "").orEmpty()

            if (lyricContent.isBlank()) {
                return@withContext LyricResult(
                    api = "SodaMusic",
                    lyrics = null,
                    parsedLines = null,
                    hasSyllable = false,
                    provider = OnlineLyricProvider.SodaMusic,
                    matchedTitle = matchedTitle,
                    matchedArtist = matchedArtist,
                    error = "无歌词内容"
                )
            }

            val parsedLines = if (lyricContent.contains("[")) parseLrcLyrics(lyricContent) else emptyList()
            val hasSyllable = lyricType.contains("word", ignoreCase = true) || lyricType.contains("syllable", ignoreCase = true)
            LyricResult(
                api = "SodaMusic",
                lyrics = lyricContent,
                parsedLines = parsedLines,
                hasSyllable = hasSyllable,
                provider = OnlineLyricProvider.SodaMusic,
                matchedTitle = matchedTitle,
                matchedArtist = matchedArtist
            )
        } catch (e: Exception) {
            AppLogger.getInstance().log("OnlineLyric", "SodaMusic API错误: ${e.message}")
            null
        }
    }

    private suspend fun fetchLrclibAsync(title: String, artist: String): LyricResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://lrclib.net/api/get?track_name=${title.encodeURL()}&artist_name=${artist.encodeURL()}"
            val response = executeRequest(url) ?: return@withContext null
            val json = JSONObject(response)

            if (json.optBoolean("instrumental", false)) {
                return@withContext LyricResult(
                    api = "LRCLIB",
                    lyrics = null,
                    parsedLines = null,
                    hasSyllable = false,
                    provider = OnlineLyricProvider.Lrclib,
                    matchedTitle = json.optString("trackName"),
                    matchedArtist = json.optString("artistName"),
                    error = "纯音乐"
                )
            }

            val synced = json.optString("syncedLyrics", "")
            val plain = json.optString("plainLyrics", "")
            val lyricContent = synced.ifBlank { plain }
            if (lyricContent.isBlank()) return@withContext null

            val parsedLines = if (synced.isNotBlank()) parseLrcLyrics(synced) else emptyList()
            LyricResult(
                api = "LRCLIB",
                lyrics = lyricContent,
                parsedLines = parsedLines,
                hasSyllable = false,
                provider = OnlineLyricProvider.Lrclib,
                matchedTitle = json.optString("trackName"),
                matchedArtist = json.optString("artistName")
            )
        } catch (e: Exception) {
            AppLogger.getInstance().log("OnlineLyric", "LRCLIB错误: ${e.message}")
            null
        }
    }
    
    private suspend fun fetchNeteaseAsync(title: String, artist: String): LyricResult? = withContext(Dispatchers.IO) {
        try {
            val keywords = "$title $artist"
            val searchUrl = "https://music.163.com/api/search/get?s=${keywords.encodeURL()}&type=1&limit=10"
            
            val searchResponse = suspendCancellableCoroutine<String?> { continuation ->
                val request = Request.Builder().url(searchUrl).build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response.body.string())
                    }
                })
            }
            
            if (searchResponse == null) {
                return@withContext null
            }
            
            val searchJson = JSONObject(searchResponse)
            val result = searchJson.optJSONObject("result")
            val songs = result?.optJSONArray("songs")
            if (songs == null || songs.length() == 0) {
                return@withContext null
            }
            
            val firstSong = songs.getJSONObject(0)
            val matchedTitle = firstSong.optString("name", "")
            val songId = firstSong.optLong("id", 0)
            val artists = firstSong.optJSONArray("artists")
            val matchedArtist = if (artists != null && artists.length() > 0) {
                artists.getJSONObject(0).optString("name", "")
            } else ""
            
            if (songId == 0L) {
                return@withContext null
            }
            
            val lyricUrl = "https://music.163.com/api/song/lyric?id=$songId&lv=-1&tv=-1"
            val lyricResponse = suspendCancellableCoroutine<String?> { continuation ->
                val request = Request.Builder().url(lyricUrl).build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response.body.string())
                    }
                })
            }
            
            if (lyricResponse == null) {
                return@withContext null
            }
            
            val lyricJson = JSONObject(lyricResponse)
            val lrc = lyricJson.optJSONObject("lrc")
            val lyricContent = lrc?.optString("lyric", "") ?: ""
            
            if (lyricContent.isEmpty()) {
                return@withContext null
            }
            
            val parsedLines = parseLrcLyrics(lyricContent)
            LyricResult("Netease", lyricContent, parsedLines, false, provider = OnlineLyricProvider.Netease, matchedTitle = matchedTitle, matchedArtist = matchedArtist)
        } catch (e: Exception) {
            AppLogger.getInstance().log("OnlineLyric", "Netease API错误: ${e.message}")
            null
        }
    }
    
    // ========== LrcApi ==========
    
    private suspend fun fetchLrcApiAsync(title: String, artist: String): LyricResult? = withContext(Dispatchers.IO) {
        try {
            // Using precise search parameters
            val url = "https://api.lrc.cx/lyrics?title=${title.encodeURL()}&artist=${artist.encodeURL()}"
            
            val response = suspendCancellableCoroutine<String?> { continuation ->
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response.body.string())
                    }
                })
            }
            
            if (response == null) {
                return@withContext null
            }

            // Fix JSON Object parsing crash ({"detail": "Not Found"})
            if (response.trim().startsWith("{")) {
                 try {
                     val json = JSONObject(response)
                     if (json.has("detail")) {
                         AppLogger.getInstance().log("OnlineLyric", "LrcApi未找到: ${json.optString("detail")}")
                         return@withContext null
                     }
                 } catch (ignore: Exception) {}
            }
            
            if (response.isEmpty() || response.contains("Lyrics not found")) {
                return@withContext null
            }
            
            val hasSyllable = response.contains("<") && response.contains(">") && response.contains("[") && response.length > 1 && response[1].isDigit()
            val parsedLines = if (hasSyllable) parseKrcLyrics(response) else parseLrcLyrics(response)
            
            LyricResult("LrcApi", response, parsedLines, hasSyllable, provider = OnlineLyricProvider.LrcApi)
        } catch (e: Exception) {
            AppLogger.getInstance().log("OnlineLyric", "LrcApi错误: ${e.message}")
            null
        }
    }
    
    // ========== 评分选择系统 ==========
    
    // ========== 评分选择系统 ==========
    
    private fun selectBestResult(
        attempts: List<ProviderAttempt>,
        targetTitle: String,
        targetArtist: String,
        providerOrder: List<OnlineLyricProvider>,
        useSmartSelection: Boolean
    ): LyricResult? {
        val usableResults = attempts.mapNotNull { it.result }
            .filter { it.parsedLines?.isNotEmpty() == true }

        if (!useSmartSelection) {
            val firstByPriority = providerOrder.firstNotNullOfOrNull { provider ->
                usableResults.firstOrNull { it.provider == provider }
            }
            usableResults.forEach { result ->
                result.score = if (result == firstByPriority) 150 else 0
            }
            return firstByPriority
        }

        val providerPriority = providerOrder.withIndex().associate { it.value to it.index }
        val results = attempts.mapNotNull { it.result }
        for (result in results) {
            var score = 0
            
            // 有逐字歌词 30分
            if (result.hasSyllable) score += 30
            
            // 歌词带有时间轴 20分
            // 如果是逐字歌词(hasSyllable)，必然包含时间轴
            val timestampRegex = Regex("\\[\\d{1,2}[.:]\\d{1,2}[.:]\\d{1,3}]")
            if (result.hasSyllable || (result.lyrics != null && timestampRegex.containsMatchIn(result.lyrics))) {
                score += 20
            }
            
            // API质量与信任分
            when (result.api) {
                "QQMusic" -> score += if (result.parsedLines.isNullOrEmpty()) 3 else 12
                "Kugou" -> score += if (result.hasSyllable) 10 else 5
                "SodaMusic" -> score += if (result.parsedLines.isNullOrEmpty()) 4 else 13
                "LRCLIB" -> score += if (result.parsedLines.isNullOrEmpty()) 2 else 12
                // LrcApi: 基础分5 + 信任分50 = 55 (弥补无标题缺失)
                "LrcApi" -> score += 55 
                "Netease" -> score += 5
            }
            // 标题匹配加分逻辑 (V3 优化)
            val matchedTitle = result.matchedTitle
            
            if (matchedTitle != null && matchedTitle.isNotEmpty()) {
                if (matchedTitle.equals(targetTitle, ignoreCase = true)) {
                    // 原标题完全匹配：+50
                    score += 50
                } else {
                    val cleanTarget = cleanTitle(targetTitle)
                    val cleanMatched = cleanTitle(matchedTitle)
                    
                    if (cleanMatched.equals(cleanTarget, ignoreCase = true)) {
                        // 清洗后标题匹配：+20
                        score += 20
                    } else {
                        // 清洗后仍不匹配：-50 (强力惩罚)
                        score -= 50
                    }
                }
            } else {
                // 无标题信息，视为风险，微扣分
                score -= 10
            }
            
            // 过滤纯音/无歌词
             if (result.lyrics?.contains("纯音乐", ignoreCase = true) == true || 
                 result.lyrics?.contains("No lyrics", ignoreCase = true) == true) {
                 score -= 100 // 惩罚纯音乐，除非没有其他选择
             }
            
            result.score = score
        }

        return results
            .filter { it.parsedLines?.isNotEmpty() == true }
            .sortedWith(
                compareByDescending<LyricResult> { it.score }
                    .thenBy { providerPriority[it.provider] ?: Int.MAX_VALUE }
                    .thenByDescending { it.hasSyllable }
            )
            .firstOrNull()
    }
    
    // ========== KRC解析 ==========
    
    private fun parseKrcLyrics(krcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val krcLines = krcContent.lines()
        
        for (line in krcLines) {
            if (line.startsWith('[') && line.length >= 5 && line[1].isDigit()) {
                val parsedLine = parseKrcLine(line)
                if (parsedLine != null) {
                    lines.add(parsedLine)
                }
            }
        }
        
        return lines.sortedBy { it.startTime }
    }
    
    private fun parseKrcLine(line: String): LyricLine? {
        try {
            // Regex匹配 [offset,duration]
            val lineHeaderRegex = Regex("^\\[(\\d+),(\\d+)\\]")
            val headerMatch = lineHeaderRegex.find(line) ?: return null
            
            val lineStartTime = headerMatch.groupValues[1].toLong()
            val lineDuration = headerMatch.groupValues[2].toLong()
            val lineEndTime = lineStartTime + lineDuration
            
            val contentPart = line.substring(headerMatch.range.last + 1)
            
            // Regex解析逐字: <offset,duration,id>SyllableText
            val syllableRegex = Regex("<(\\d+),(\\d+),(\\d+)>([^<]*)")
            val matches = syllableRegex.findAll(contentPart)
            
            val syllables = mutableListOf<SyllableInfo>()
            val fullText = StringBuilder()
            
            for (match in matches) {
                val offset = match.groupValues[1].toLong()
                val duration = match.groupValues[2].toLong()
                val text = match.groupValues[4]
                
                val absStartTime = lineStartTime + offset
                val absEndTime = absStartTime + duration
                
                syllables.add(SyllableInfo(absStartTime, absEndTime, text))
                fullText.append(text)
            }
            
            return if (syllables.isNotEmpty()) {
                LyricLine(lineStartTime, lineEndTime, fullText.toString(), syllables)
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.getInstance().log("OnlineLyric", "解析KRC行失败: ${e.message}")
            return null
        }
    }
    
    // ========== LRC解析 ==========
    
    private fun parseLrcLyrics(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val lrcLines = lrcContent.lines()
        
        val timedLines = mutableListOf<Pair<Long, String>>()
        val timeRegex = "\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]".toRegex()
        
        for (line in lrcLines) {
            val matches = timeRegex.findAll(line)
            val text = line.replace(timeRegex, "").trim()
            
            for (match in matches) {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val millis = match.groupValues[3].let {
                    if (it.length == 2) it.toLong() * 10 else it.toLong()
                }
                val totalMs = minutes * 60000 + seconds * 1000 + millis
                timedLines.add(Pair(totalMs, text))
            }
        }
        
        timedLines.sortBy { it.first }
        
        for (i in timedLines.indices) {
            val startTime = timedLines[i].first
            val text = timedLines[i].second
            val endTime = if (i < timedLines.size - 1) {
                timedLines[i + 1].first
            } else {
                startTime + 5000
            }
            lines.add(LyricLine(startTime, endTime, text, null))
        }
        
        return lines
    }
    
    // ========== 酷狗解密 ==========
    
    private fun decodeKugouLyric(encoded: String): String {
        try {
            val data = Base64.decode(encoded, Base64.DEFAULT)
            val dataWithoutHeader = data.copyOfRange(4, data.size)
            val decryptKey = byteArrayOf(
                0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
                0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69
            )
            for (i in dataWithoutHeader.indices) {
                dataWithoutHeader[i] = (dataWithoutHeader[i].toInt() xor decryptKey[i % decryptKey.size].toInt()).toByte()
            }
            val decompressedData = inflateData(dataWithoutHeader)
            val result = String(decompressedData, Charsets.UTF_8)
            return if (result.isNotEmpty()) result.substring(1) else result
        } catch (e: Exception) {
            AppLogger.getInstance().log("OnlineLyric", "Kugou歌词解密失败: ${e.message}")
            return encoded
        }
    }
    
    private fun inflateData(data: ByteArray): ByteArray {
        try {
            val inflater = Inflater()
            inflater.setInput(data)
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput()) break
                    if (inflater.needsDictionary()) break
                }
                outputStream.write(buffer, 0, count)
            }
            inflater.end()
            return outputStream.toByteArray()
        } catch (e: Exception) {
            AppLogger.getInstance().log("OnlineLyric", "解压失败: ${e.message}")
            throw e
        }
    }
    
    private fun String.encodeURL(): String {
        return URLEncoder.encode(this, "UTF-8")
    }

    private fun qqHeaders(referer: String): Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36",
        "Referer" to referer,
        "Cookie" to "os=pc;osver=Microsoft-Windows-10-Professional-build-16299.125-64bit;appver=2.0.3.131777;channel=netease;__remember_me=true"
    )

    private fun sodaHeaders(): Map<String, String> = mapOf(
        "User-Agent" to "LunaPC/2.6.5(197449790)",
        "Referer" to "https://api.qishui.com/"
    )

    private fun decodeBase64Text(value: String): String {
        if (value.isBlank()) return ""
        return try {
            String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun unwrapJsonp(callback: String, raw: String): String? {
        val prefix = "$callback("
        if (!raw.startsWith(prefix) || !raw.endsWith(")")) return null
        return raw.removePrefix(prefix).dropLast(1)
    }

    private fun escapeJson(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private suspend fun executeRequest(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): String? = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response.body.string())
            }
        })
    }

    private suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): String? = suspendCancellableCoroutine { continuation ->
        val requestBody = FormBody.Builder().apply {
            form.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder().url(url).post(requestBody).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response.body.string())
            }
        })
    }

    private suspend fun postJsonString(
        url: String,
        bodyJson: String,
        headers: Map<String, String> = emptyMap()
    ): String? = suspendCancellableCoroutine { continuation ->
        val requestBody = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response.body.string())
            }
        })
    }

}
