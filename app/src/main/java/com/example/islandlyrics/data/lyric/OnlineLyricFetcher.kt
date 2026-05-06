package com.example.islandlyrics.data.lyric

import android.util.Base64
import android.text.Html
import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HostnameVerifier
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.text.RegexOption.DOT_MATCHES_ALL

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
            val songId = firstSong.optString("id", "")
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
            val downloadExtras = fetchQQLyricExtras(songId)
            val transContent = downloadExtras.translation.ifBlank {
                decodeBase64Text(lyricJson.optString("trans", ""))
            }
            val romanContent = downloadExtras.romanization
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
                matchedArtist = matchedArtist,
                translationLyrics = transContent.takeIf { it.isNotBlank() },
                romanLyrics = romanContent.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            AppLogger.getInstance().log("OnlineLyric", "QQMusic API错误: ${e.message}")
            null
        }
    }

    private data class QqLyricExtras(
        val translation: String = "",
        val romanization: String = ""
    )

    private suspend fun fetchQQLyricExtras(songId: String): QqLyricExtras {
        if (songId.isBlank()) return QqLyricExtras()
        return runCatching {
            val response = postForm(
                url = "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg",
                form = linkedMapOf(
                    "version" to "15",
                    "miniversion" to "82",
                    "lrctype" to "4",
                    "musicid" to songId
                ),
                headers = qqHeaders("https://c.y.qq.com/")
            ).orEmpty()
                .replace("<!--", "")
                .replace("-->", "")
                .trim()
            val rawTranslationPayload = extractTagContent(response, "contentts").orEmpty()
            val rawRomanPayload = extractTagContent(response, "contentroma").orEmpty()
            QqLyricExtras(
                translation = decodeQqDownloadLyricPayload(rawTranslationPayload),
                romanization = decodeQqDownloadLyricPayload(rawRomanPayload)
            )
        }.onFailure {
            AppLogger.getInstance().d("OnlineLyric", "QQ lyric extras fetch skipped: ${it.message}")
        }.getOrDefault(QqLyricExtras())
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
            val hasSyllable = isWordLevelLyrics(lyricContent)
            val parsedLines = if (hasSyllable) parseWordLevelLyrics(lyricContent) else parseLrcLyrics(lyricContent)

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

            val hasSyllable = isWordLevelLyrics(lyricContent, lyricType)
            val parsedLines = parseSodaLyrics(lyricContent, lyricType)
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
            
            val lyricResponse = fetchNeteaseLyricV1(songId)
                ?: executeRequest(
                    url = "https://music.163.com/api/song/lyric?id=$songId&lv=-1&tv=-1&rv=-1&kv=-1&yv=-1",
                    headers = neteaseHeaders()
                )
            
            if (lyricResponse == null) {
                return@withContext null
            }
            
            val lyricJson = JSONObject(lyricResponse)
            val lyricContent = lyricJson.optLyricText("lrc")
            val translationContent = lyricJson.optLyricText("tlyric")
                .ifBlank { lyricJson.optLyricText("ytlrc") }
            val romanContent = lyricJson.optLyricText("romalrc")
                .ifBlank { lyricJson.optLyricText("yromalrc") }
            
            if (lyricContent.isEmpty()) {
                return@withContext null
            }
            
            val parsedLines = parseLrcLyrics(lyricContent)
            LyricResult(
                "Netease",
                lyricContent,
                parsedLines,
                false,
                provider = OnlineLyricProvider.Netease,
                matchedTitle = matchedTitle,
                matchedArtist = matchedArtist,
                translationLyrics = translationContent.takeIf { it.isNotBlank() },
                romanLyrics = romanContent.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            AppLogger.getInstance().log("OnlineLyric", "Netease API错误: ${e.message}")
            null
        }
    }

    private fun fetchNeteaseLyricV1(songId: Long): String? {
        return executeNeteaseEapi(
            url = "https://interface3.music.163.com/eapi/song/lyric/v1",
            data = linkedMapOf(
                "id" to songId.toString(),
                "cp" to "false",
                "lv" to "0",
                "kv" to "0",
                "tv" to "0",
                "rv" to "0",
                "yv" to "0",
                "ytv" to "0",
                "yrv" to "0",
                "csrf_token" to ""
            )
        )
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
            
            val hasSyllable = isWordLevelLyrics(response)
            val parsedLines = if (hasSyllable) parseWordLevelLyrics(response) else parseLrcLyrics(response)
            
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
            .filter { isUsableResult(it) }

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
            result.score = buildQualityScore(result, targetTitle, targetArtist)
        }

        return results
            .filter { isUsableResult(it) }
            .sortedWith(
                compareByDescending<LyricResult> { it.score }
                    .thenBy { providerPriority[it.provider] ?: Int.MAX_VALUE }
                    .thenByDescending { it.hasSyllable }
            )
            .firstOrNull()
    }

    private fun isUsableResult(result: LyricResult): Boolean {
        return !result.lyrics.isNullOrBlank() && !result.parsedLines.isNullOrEmpty()
    }

    private fun buildQualityScore(
        result: LyricResult,
        targetTitle: String,
        targetArtist: String
    ): Int {
        var score = 0

        val parsedLines = result.parsedLines.orEmpty()
        val lineCount = parsedLines.size
        val wordLineCount = parsedLines.count { !it.syllables.isNullOrEmpty() }

        if (result.hasSyllable || wordLineCount > 0) {
            score += 42
        } else if (lineCount > 0) {
            score += 24
        }

        score += minOf(lineCount, 12)
        if (wordLineCount > 0) {
            score += minOf(wordLineCount, 10)
        }

        score += when (result.provider) {
            OnlineLyricProvider.QQMusic -> 11
            OnlineLyricProvider.Kugou -> 10
            OnlineLyricProvider.SodaMusic -> 11
            OnlineLyricProvider.Lrclib -> 8
            OnlineLyricProvider.LrcApi -> 8
            OnlineLyricProvider.Netease -> 9
        }

        score += scoreTitleMatch(targetTitle, result.matchedTitle)
        score += scoreArtistMatch(targetArtist, result.matchedArtist)

        if (result.lyrics?.contains("纯音乐", ignoreCase = true) == true ||
            result.lyrics?.contains("No lyrics", ignoreCase = true) == true) {
            score -= 100
        }

        return score
    }

    private fun scoreTitleMatch(targetTitle: String, matchedTitle: String?): Int {
        if (matchedTitle.isNullOrBlank()) return -8
        if (matchedTitle.equals(targetTitle, ignoreCase = true)) return 36

        val cleanTarget = cleanTitle(targetTitle).lowercase()
        val cleanMatched = cleanTitle(matchedTitle).lowercase()
        return when {
            cleanTarget.isBlank() || cleanMatched.isBlank() -> -8
            cleanMatched == cleanTarget -> 20
            cleanMatched.contains(cleanTarget) || cleanTarget.contains(cleanMatched) -> 8
            else -> -30
        }
    }

    private fun scoreArtistMatch(targetArtist: String, matchedArtist: String?): Int {
        if (targetArtist.isBlank()) return 0
        if (matchedArtist.isNullOrBlank()) return -4

        val targetTokens = normalizeArtistTokens(targetArtist)
        val matchedTokens = normalizeArtistTokens(matchedArtist)
        if (targetTokens.isEmpty() || matchedTokens.isEmpty()) return -4

        val overlap = targetTokens.intersect(matchedTokens).size
        return when {
            overlap == 0 -> -18
            overlap == targetTokens.size && overlap == matchedTokens.size -> 18
            overlap == targetTokens.size || overlap == matchedTokens.size -> 12
            else -> 6
        }
    }

    private fun normalizeArtistTokens(artist: String): Set<String> {
        return artist
            .split("/", "&", ",", "、", " feat. ", " ft. ", " x ", " X ", ";")
            .map { it.trim().lowercase() }
            .map { it.replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .toSet()
    }
    
    // ========== KRC解析 ==========

    private fun parseWordLevelLyrics(content: String): List<LyricLine> {
        val trimmed = content.trimStart()
        return when {
            trimmed.startsWith("<tt") -> parseTtmlLyrics(content)
            else -> parseBracketWordLyrics(content)
        }
    }

    private fun isWordLevelLyrics(content: String, lyricTypeHint: String? = null): Boolean {
        val trimmed = content.trimStart()
        if (trimmed.startsWith("<tt")) return true
        if (lyricTypeHint?.contains("word", ignoreCase = true) == true) return true
        if (lyricTypeHint?.contains("syllable", ignoreCase = true) == true) return true

        val bracketWordRegex = Regex("""(?m)^\[\d+(?:,\d+)?]\s*(?:<\d+,\d+,\d+>[^<\r\n]*)+""")
        return bracketWordRegex.containsMatchIn(content)
    }
    
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

    private fun parseBracketWordLyrics(content: String): List<LyricLine> {
        val lines = content.lines()
            .mapNotNull { parseBracketWordLine(it) }
            .sortedBy { it.startTime }

        if (lines.isEmpty()) return emptyList()

        return lines.mapIndexed { index, line ->
            val nextStart = lines.getOrNull(index + 1)?.startTime
            if (nextStart != null && line.endTime <= line.startTime) {
                line.copy(endTime = nextStart)
            } else {
                line
            }
        }
    }

    private fun parseBracketWordLine(line: String): LyricLine? {
        val headerMatch = Regex("""^\[(\d+)(?:,(\d+))?]""").find(line) ?: return null
        val lineStartTime = headerMatch.groupValues[1].toLongOrNull() ?: return null
        val explicitDuration = headerMatch.groupValues.getOrNull(2)?.toLongOrNull()
        val contentPart = line.substring(headerMatch.range.last + 1)

        val syllableRegex = Regex("""<(\d+),(\d+),(\d+)>([^<]*)""")
        val syllables = mutableListOf<SyllableInfo>()
        val fullText = StringBuilder()

        for (match in syllableRegex.findAll(contentPart)) {
            val offset = match.groupValues[1].toLong()
            val duration = match.groupValues[2].toLong()
            val text = match.groupValues[4]
            if (text.isBlank()) continue

            val absStartTime = lineStartTime + offset
            val absEndTime = absStartTime + duration
            syllables.add(SyllableInfo(absStartTime, absEndTime, text))
            fullText.append(text)
        }

        if (syllables.isEmpty()) return null

        val lineEndTime = explicitDuration?.let { lineStartTime + it }
            ?: syllables.maxOf { it.endTime }

        return LyricLine(
            startTime = lineStartTime,
            endTime = lineEndTime,
            text = fullText.toString(),
            syllables = syllables
        )
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

    private fun parseSodaLyrics(lyricContent: String, lyricType: String): List<LyricLine> {
        val trimmed = lyricContent.trimStart()
        return when {
            trimmed.startsWith("<tt") -> parseTtmlLyrics(lyricContent)
            isWordLevelLyrics(lyricContent, lyricType) -> parseWordLevelLyrics(lyricContent)
            hasLrcTimestamps(lyricContent) -> parseLrcLyrics(lyricContent)
            lyricType.contains("lrc", ignoreCase = true) -> parseLrcLyrics(lyricContent)
            else -> emptyList()
        }
    }

    private fun hasLrcTimestamps(content: String): Boolean {
        return Regex("""\[\d{2}:\d{2}\.\d{2,3}]""").containsMatchIn(content)
    }

    private fun parseTtmlLyrics(ttmlContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val lineRegex = Regex("<p\\b([^>]*)>(.*?)</p>", DOT_MATCHES_ALL)
        val spanRegex = Regex("<span\\b([^>]*)>(.*?)</span>", DOT_MATCHES_ALL)

        for (lineMatch in lineRegex.findAll(ttmlContent)) {
            val lineAttributes = parseXmlAttributes(lineMatch.groupValues[1])
            val lineBody = lineMatch.groupValues[2]
            val lineStart = parseFlexibleTimeToMs(lineAttributes["begin"]) ?: continue
            val lineEnd = parseFlexibleTimeToMs(lineAttributes["end"])

            val syllables = mutableListOf<SyllableInfo>()
            for (spanMatch in spanRegex.findAll(lineBody)) {
                val spanAttributes = parseXmlAttributes(spanMatch.groupValues[1])
                val start = parseFlexibleTimeToMs(spanAttributes["begin"]) ?: continue
                val end = parseFlexibleTimeToMs(spanAttributes["end"]) ?: continue
                val text = decodeXmlText(stripXmlTags(spanMatch.groupValues[2])).trim()
                if (text.isBlank()) continue
                syllables.add(SyllableInfo(start, end, text))
            }

            val text = decodeXmlText(stripXmlTags(lineBody))
                .replace(Regex("\\s+"), " ")
                .trim()
            if (text.isBlank()) continue

            lines.add(
                LyricLine(
                    startTime = lineStart,
                    endTime = lineEnd ?: syllables.lastOrNull()?.endTime ?: (lineStart + 5000),
                    text = text,
                    syllables = syllables.ifEmpty { null }
                )
            )
        }

        return lines.sortedBy { it.startTime }
    }

    private fun parseXmlAttributes(raw: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        val attrRegex = Regex("""([A-Za-z_:][A-Za-z0-9_:\-.]*)="([^"]*)"""")
        for (match in attrRegex.findAll(raw)) {
            attributes[match.groupValues[1]] = match.groupValues[2]
        }
        return attributes
    }

    private fun stripXmlTags(text: String): String {
        return text.replace(Regex("<[^>]+>"), " ")
    }

    private fun decodeXmlText(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

    private fun parseFlexibleTimeToMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim().removeSuffix("s")
        val parts = normalized.split(":")
        return try {
            when (parts.size) {
                1 -> (parts[0].toDouble() * 1000).toLong()
                2 -> ((parts[0].toLong() * 60_000) + (parts[1].toDouble() * 1000)).toLong()
                3 -> ((parts[0].toLong() * 3_600_000) + (parts[1].toLong() * 60_000) + (parts[2].toDouble() * 1000)).toLong()
                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        }
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

    private fun neteaseHeaders(): Map<String, String> = mapOf(
        "User-Agent" to NETEASE_USER_AGENT,
        "Referer" to "https://music.163.com/"
    )

    private fun executeNeteaseEapi(url: String, data: LinkedHashMap<String, String>): String? {
        val header = linkedMapOf(
            "__csrf" to "",
            "appver" to "8.0.0",
            "buildver" to (System.currentTimeMillis() / 1000L).toString(),
            "channel" to "",
            "deviceId" to "",
            "mobilename" to "",
            "resolution" to "1920x1080",
            "os" to "android",
            "osver" to "",
            "requestId" to "${System.currentTimeMillis()}_${(0..999).random().toString().padStart(4, '0')}",
            "versioncode" to "140",
            "MUSIC_U" to ""
        )
        val payload = LinkedHashMap(data)
        payload["header"] = JSONObject(header as Map<*, *>).toString()

        val requestBody = FormBody.Builder()
            .add("params", buildNeteaseEapiParams(url, JSONObject(payload as Map<*, *>).toString()))
            .build()
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Referer", "https://music.163.com/")
            .header("User-Agent", NETEASE_EAPI_USER_AGENT)
            .header("Cookie", header.entries.joinToString("; ") { "${it.key}=${it.value}" })
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body.string()
            }
        }.onFailure {
            AppLogger.getInstance().d("OnlineLyric", "Netease eapi failed: ${it.message}")
        }.getOrNull()
    }

    private fun buildNeteaseEapiParams(url: String, payloadJson: String): String {
        val path = url
            .replace("https://interface3.music.163.com/e", "/")
            .replace("https://interface.music.163.com/e", "/")
        val digest = md5Hex("nobody${path}use${payloadJson}md5forencrypt")
        val data = "${path}-36cd479b6b5-${payloadJson}-36cd479b6b5-${digest}"
        return aesEcbEncryptHex(data)
    }

    private fun aesEcbEncryptHex(value: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(NETEASE_EAPI_KEY.toByteArray(Charsets.US_ASCII), "AES"))
        return cipher.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }

    private fun md5Hex(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

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

    private fun JSONObject.optLyricText(key: String): String {
        return optJSONObject(key)?.optString("lyric", "").orEmpty()
    }

    private fun unwrapJsonp(callback: String, raw: String): String? {
        val prefix = "$callback("
        if (!raw.startsWith(prefix) || !raw.endsWith(")")) return null
        return raw.removePrefix(prefix).dropLast(1)
    }

    private fun decodeQqDownloadLyricPayload(payload: String): String {
        if (payload.isBlank()) return ""
        val decoded = runCatching {
            decryptQqQrcPayload(payload)
        }.getOrElse {
            if (payload.isLikelyLrc()) payload else return ""
        }
        return normalizeQqDownloadLyricPayload(decoded)
    }

    private fun String.isLikelyLrc(): Boolean {
        return Regex("""(?m)^\[\d{1,2}:\d{2}(?:\.\d{1,3})?]""").containsMatchIn(this)
    }

    private fun normalizeQqDownloadLyricPayload(payload: String): String {
        val trimmed = payload.trim()
        if (!trimmed.startsWith("<?xml")) {
            return trimmed
        }

        val attrMatch = Regex("""LyricContent="([^"]*)"""").find(trimmed)
        if (attrMatch != null) {
            return Html.fromHtml(attrMatch.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
        }

        val lyricMatch = extractTagContent(trimmed, "Lyric_1")
            ?: extractTagContent(trimmed, "lyric")
        if (!lyricMatch.isNullOrBlank()) {
            return Html.fromHtml(lyricMatch, Html.FROM_HTML_MODE_LEGACY).toString()
        }

        return trimmed
    }

    private fun decryptQqQrcPayload(encryptedLyrics: String): String {
        val decrypted = QqQrcDecrypter.decryptToCompressedBytes(encryptedLyrics)
        val inflated = inflateQqQrcPayload(decrypted)
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val content = if (inflated.size >= utf8Bom.size && inflated.copyOfRange(0, utf8Bom.size).contentEquals(utf8Bom)) {
            inflated.copyOfRange(utf8Bom.size, inflated.size)
        } else {
            inflated
        }
        return String(content, Charsets.UTF_8)
    }

    private fun inflateQqQrcPayload(decrypted: ByteArray): ByteArray {
        val zlibOffset = findLikelyZlibOffset(decrypted)
        val candidates = buildList {
            add(decrypted)
            if (zlibOffset > 0) add(decrypted.copyOfRange(zlibOffset, decrypted.size))
        }.distinctBy { it.contentHashCode() }

        for (candidate in candidates) {
            runCatching {
                return InflaterInputStream(candidate.inputStream()).use { it.readBytes() }
            }
            runCatching {
                return inflateRawDeflate(candidate)
            }
        }
        error("QQ QRC inflate failed")
    }

    private fun findLikelyZlibOffset(bytes: ByteArray): Int {
        for (index in 0 until bytes.size - 1) {
            val first = bytes[index].toInt() and 0xFF
            val second = bytes[index + 1].toInt() and 0xFF
            if (first == 0x78 && second in listOf(0x01, 0x5E, 0x9C, 0xDA)) {
                return index
            }
        }
        return 0
    }

    private fun inflateRawDeflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater(true)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        inflater.setInput(bytes)
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count > 0) {
                    output.write(buffer, 0, count)
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    break
                }
            }
        } finally {
            inflater.end()
        }
        val result = output.toByteArray()
        if (result.isEmpty()) error("empty output")
        return result
    }

    private fun extractTagContent(content: String, tagName: String): String? {
        val cdataRegex = Regex("""<$tagName[^>]*><!\[CDATA\[(.*?)]]></$tagName>""", setOf(DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        cdataRegex.find(content)?.let { return it.groupValues[1] }

        val textRegex = Regex("""<$tagName[^>]*>(.*?)</$tagName>""", setOf(DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return textRegex.find(content)?.groupValues?.getOrNull(1)
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

    private companion object {
        private const val NETEASE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        private const val NETEASE_EAPI_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; PCT-AL10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.64 HuaweiBrowser/10.0.3.311 Mobile Safari/537.36"
        private const val NETEASE_EAPI_KEY = "e82ckenh8dichen8"
    }

}
