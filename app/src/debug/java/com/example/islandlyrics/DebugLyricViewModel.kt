package com.example.islandlyrics

import android.util.Base64
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.lyric.OnlineLyricFetcher
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import javax.net.ssl.HostnameVerifier
import kotlin.coroutines.resume

class DebugLyricViewModel : ViewModel() {

    private val repo = LyricRepository.getInstance()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .hostnameVerifier(HostnameVerifier { _, _ -> true })
        .build()

    private val _apiResults = MutableLiveData<List<OnlineLyricFetcher.LyricResult>>(emptyList())
    val apiResults: LiveData<List<OnlineLyricFetcher.LyricResult>> = _apiResults

    private val _selectedResult = MutableLiveData<OnlineLyricFetcher.LyricResult?>(null)
    val selectedResult: LiveData<OnlineLyricFetcher.LyricResult?> = _selectedResult

    private val _isFetching = MutableLiveData(false)
    val isFetching: LiveData<Boolean> = _isFetching

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    // State for live preview
    private val _parsedLyrics = MutableLiveData<List<OnlineLyricFetcher.LyricLine>>(emptyList())
    val parsedLyrics: LiveData<List<OnlineLyricFetcher.LyricLine>> = _parsedLyrics

    val liveMetadata = repo.liveMetadata
    val liveLyric = repo.liveLyric
    val liveProgress = repo.liveProgress
    val isPlaying = repo.isPlaying

    fun fetchLyrics() {
        val mediaInfo = liveMetadata.value
        if (mediaInfo == null || mediaInfo.title.isBlank() || mediaInfo.artist.isBlank()) {
            _error.value = "没有可用的歌曲信息"
            return
        }

        _isFetching.value = true
        _error.value = null
        _apiResults.value = emptyList()
        _parsedLyrics.value = emptyList()
        _selectedResult.value = null

        viewModelScope.launch {
            try {
                val results = fetchAllApisInternal(mediaInfo.title, mediaInfo.artist)
                _apiResults.value = results.filterNotNull()
                
                if (results.any { it != null }) {
                    val filtered = results.filterNotNull()
                    if (filtered.isNotEmpty()) {
                        selectBestResult(filtered, mediaInfo.title, mediaInfo.artist)
                    }
                } else {
                    _error.value = "所有API都未返回结果"
                }
            } catch (e: Exception) {
                _error.value = "获取失败: ${e.message}"
            } finally {
                _isFetching.value = false
            }
        }
    }

    private suspend fun fetchAllApisInternal(title: String, artist: String): List<OnlineLyricFetcher.LyricResult?> = coroutineScope {
        listOf(
            async { fetchKugouAsync(title, artist) },
            async { fetchNeteaseAsync(title, artist) },
            async { fetchLrcApiAsync(title, artist) }
        ).awaitAll()
    }

    private fun selectBestResult(results: List<OnlineLyricFetcher.LyricResult>, originalTitle: String, originalArtist: String) {
        results.forEach { result ->
            var score = 0
            if (result.lyrics != null && result.lyrics.isNotEmpty() && result.parsedLines != null && result.parsedLines.isNotEmpty()) {
                score += 10
            }
            if (result.hasSyllable) {
                score += 30
            }
            val timestampRegex = Regex("\\[\\d{1,2}[.:]\\d{1,2}[.:]\\d{1,3}]")
            if (result.hasSyllable || (result.lyrics != null && timestampRegex.containsMatchIn(result.lyrics))) {
                score += 20
            }
            val apiTitle = result.matchedTitle
            val apiArtist = result.matchedArtist
            
            if (!apiTitle.isNullOrEmpty() || !apiArtist.isNullOrEmpty()) {
                val titleMatch = apiTitle?.trim()?.equals(originalTitle.trim(), ignoreCase = true) == true
                val artistMatch = apiArtist?.trim()?.equals(originalArtist.trim(), ignoreCase = true) == true

                if (titleMatch && artistMatch) {
                    score += 40
                } else if (titleMatch || artistMatch) {
                    score += 20
                }
            }
            result.score = score
        }

        val sorted = results.sortedByDescending { it.score }
        _apiResults.value = sorted
        
        if (sorted.isNotEmpty()) {
            val bestResult = sorted[0]
            _selectedResult.value = bestResult
            _parsedLyrics.value = bestResult.parsedLines ?: emptyList()
            AppLogger.getInstance().log("DebugLyric", "自动选择: ${bestResult.api} (分: ${bestResult.score})")
        }
    }

    // --- API Fetchers (Migrated from Activity) ---

    private suspend fun fetchKugouAsync(title: String, artist: String): OnlineLyricFetcher.LyricResult? = withContext(Dispatchers.IO) {
        try {
            val keywords = "$title $artist"
            val searchUrl = "https://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=${URLEncoder.encode(keywords, "UTF-8")}&page=1&pagesize=20&showtype=1"

            val searchResponse = executeRequest(searchUrl) ?: return@withContext OnlineLyricFetcher.LyricResult("Kugou", null, null, false, error = "搜索请求失败")

            val searchJson = JSONObject(searchResponse)
            val dataObj = searchJson.optJSONObject("data")
            val infoArray = dataObj?.optJSONArray("info")

            if (infoArray == null || infoArray.length() == 0) {
                return@withContext OnlineLyricFetcher.LyricResult("Kugou", null, null, false, error = "未找到歌曲")
            }

            val firstSong = infoArray.getJSONObject(0)
            val matchedTitle = firstSong.optString("songname", "")
            val matchedArtist = firstSong.optString("singername", "")
            val hash = firstSong.optString("hash", "")

            if (hash.isEmpty()) {
                return@withContext OnlineLyricFetcher.LyricResult("Kugou", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "无歌曲hash")
            }

            val durationParam = repo.liveProgress.value?.duration ?: ""
            val lyricUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=&duration=$durationParam&hash=$hash"

            val lyricResponse = executeRequest(lyricUrl) ?: return@withContext OnlineLyricFetcher.LyricResult("Kugou", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "歌词请求失败")

            val lyricJson = JSONObject(lyricResponse)
            val candidates = lyricJson.optJSONArray("candidates")

            if (candidates == null || candidates.length() == 0) {
                return@withContext OnlineLyricFetcher.LyricResult("Kugou", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "无歌词")
            }

            val lyricInfo = candidates.getJSONObject(0)
            var lyricEncoded = lyricInfo.optString("content", "")

            if (lyricEncoded.isEmpty()) {
                val id = lyricInfo.optString("id", "")
                val accessKey = lyricInfo.optString("accesskey", "")
                if (id.isNotEmpty() && accessKey.isNotEmpty()) {
                    val downloadUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=krc&charset=utf8"
                    val downloadResponse = executeRequest(downloadUrl)
                    if (downloadResponse != null) {
                        lyricEncoded = JSONObject(downloadResponse).optString("content", "")
                    }
                }
            }

            if (lyricEncoded.isEmpty()) {
                return@withContext OnlineLyricFetcher.LyricResult("Kugou", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "歌词内容为空")
            }

            val lyricContent = decodeKugouLyric(lyricEncoded)
            val hasSyllable = lyricContent.contains("<") && lyricContent.contains(">")
            val parsedLines = if (hasSyllable) parseKrcLyrics(lyricContent) else parseLrcLyrics(lyricContent)

            OnlineLyricFetcher.LyricResult("Kugou", lyricContent, parsedLines, hasSyllable, matchedTitle = matchedTitle, matchedArtist = matchedArtist)
        } catch (e: Exception) {
            OnlineLyricFetcher.LyricResult("Kugou", null, null, false, error = e.message)
        }
    }

    private suspend fun fetchNeteaseAsync(title: String, artist: String): OnlineLyricFetcher.LyricResult? = withContext(Dispatchers.IO) {
        try {
            val keywords = "$title $artist"
            val searchUrl = "https://music.163.com/api/search/get?s=${URLEncoder.encode(keywords, "UTF-8")}&type=1&limit=10"

            val searchResponse = executeRequest(searchUrl) ?: return@withContext OnlineLyricFetcher.LyricResult("Netease", null, null, false, error = "搜索请求失败")

            val searchJson = JSONObject(searchResponse)
            val result = searchJson.optJSONObject("result")
            val songs = result?.optJSONArray("songs")

            if (songs == null || songs.length() == 0) {
                return@withContext OnlineLyricFetcher.LyricResult("Netease", null, null, false, error = "未找到歌曲")
            }

            val firstSong = songs.getJSONObject(0)
            val matchedTitle = firstSong.optString("name", "")
            val songId = firstSong.optLong("id", 0)
            val artists = firstSong.optJSONArray("artists")
            val matchedArtist = if (artists != null && artists.length() > 0) artists.getJSONObject(0).optString("name", "") else ""

            if (songId == 0L) {
                return@withContext OnlineLyricFetcher.LyricResult("Netease", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "无歌曲ID")
            }

            val lyricUrl = "https://music.163.com/api/song/lyric?id=$songId&lv=-1&tv=-1"
            val lyricResponse = executeRequest(lyricUrl) ?: return@withContext OnlineLyricFetcher.LyricResult("Netease", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "歌词请求失败")

            val lyricJson = JSONObject(lyricResponse)
            val lrc = lyricJson.optJSONObject("lrc")
            val lyricContent = lrc?.optString("lyric", "") ?: ""

            if (lyricContent.isEmpty()) {
                return@withContext OnlineLyricFetcher.LyricResult("Netease", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "无歌词内容")
            }

            val parsedLines = parseLrcLyrics(lyricContent)
            OnlineLyricFetcher.LyricResult("Netease", lyricContent, parsedLines, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist)
        } catch (e: Exception) {
            OnlineLyricFetcher.LyricResult("Netease", null, null, false, error = e.message)
        }
    }

    private suspend fun fetchLrcApiAsync(title: String, artist: String): OnlineLyricFetcher.LyricResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.lrc.cx/lyrics?title=${URLEncoder.encode(title, "UTF-8")}&artist=${URLEncoder.encode(artist, "UTF-8")}"
            val response = executeRequest(url, mapOf("User-Agent" to "Mozilla/5.0")) ?: return@withContext OnlineLyricFetcher.LyricResult("LrcApi", null, null, false, error = "请求失败")

            if (response.isEmpty() || response.contains("Lyrics not found")) {
                return@withContext OnlineLyricFetcher.LyricResult("LrcApi", null, null, false, error = "未找到歌词")
            }

            val hasSyllable = response.contains("<") && response.contains(">") && response.contains("[") && response.length > 1 && response[1].isDigit()
            val parsedLines = if (hasSyllable) parseKrcLyrics(response) else parseLrcLyrics(response)

            OnlineLyricFetcher.LyricResult("LrcApi", response, parsedLines, hasSyllable)
        } catch (e: Exception) {
            OnlineLyricFetcher.LyricResult("LrcApi", null, null, false, error = e.message)
        }
    }

    private suspend fun executeRequest(url: String, headers: Map<String, String> = emptyMap()): String? = suspendCancellableCoroutine { continuation ->
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val request = builder.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resume(null) }
            override fun onResponse(call: Call, response: Response) { 
                continuation.resume(response.body.string())
            }
        })
    }

    // --- Parsing Logic (Migrated from Activity) ---

    private fun parseLrcLyrics(lrcContent: String): List<OnlineLyricFetcher.LyricLine> {
        val lines = mutableListOf<OnlineLyricFetcher.LyricLine>()
        val timeRegex = "\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]".toRegex()
        val timedLines = lrcContent.lines().flatMap { line ->
            val matches = timeRegex.findAll(line)
            val text = line.replace(timeRegex, "").trim()
            matches.map { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val millis = match.groupValues[3].let { if (it.length == 2) it.toLong() * 10 else it.toLong() }
                (minutes * 60000 + seconds * 1000 + millis) to text
            }
        }.sortedBy { it.first }

        for (i in timedLines.indices) {
            val startTime = timedLines[i].first
            val endTime = if (i < timedLines.size - 1) timedLines[i + 1].first else startTime + 5000
            lines.add(OnlineLyricFetcher.LyricLine(startTime, endTime, timedLines[i].second))
        }
        return lines
    }

    private fun parseKrcLyrics(krcContent: String): List<OnlineLyricFetcher.LyricLine> {
        val lines = mutableListOf<OnlineLyricFetcher.LyricLine>()
        krcContent.lines().forEach { line ->
            if (line.startsWith('[') && line.length >= 5 && line[1].isDigit()) {
                parseKrcLine(line)?.let { lines.add(it) }
            }
        }
        return lines.sortedBy { it.startTime }
    }

    private fun parseKrcLine(line: String): OnlineLyricFetcher.LyricLine? {
        return try {
            val headerRegex = Regex("^\\[(\\d+),(\\d+)\\]")
            val headerMatch = headerRegex.find(line) ?: return null
            val lineStartTime = headerMatch.groupValues[1].toLong()
            val lineDuration = headerMatch.groupValues[2].toLong()
            val contentPart = line.substring(headerMatch.range.last + 1)
            val syllableRegex = Regex("<(\\d+),(\\d+),(\\d+)>([^<]*)")
            val matches = syllableRegex.findAll(contentPart)
            val syllables = matches.map { match ->
                val offset = match.groupValues[1].toLong()
                val duration = match.groupValues[2].toLong()
                OnlineLyricFetcher.SyllableInfo(lineStartTime + offset, lineStartTime + offset + duration, match.groupValues[4])
            }.toList()
            if (syllables.isNotEmpty()) {
                OnlineLyricFetcher.LyricLine(lineStartTime, lineStartTime + lineDuration, syllables.joinToString("") { it.text }, syllables)
            } else null
        } catch (e: Exception) { null }
    }

    private fun decodeKugouLyric(encoded: String): String {
        return try {
            val data = Base64.decode(encoded, Base64.DEFAULT)
            val dataWithoutHeader = data.copyOfRange(4, data.size)
            val key = byteArrayOf(0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69)
            for (i in dataWithoutHeader.indices) dataWithoutHeader[i] = (dataWithoutHeader[i].toInt() xor key[i % key.size].toInt()).toByte()
            val result = String(inflateData(dataWithoutHeader), Charsets.UTF_8)
            if (result.isNotEmpty()) result.substring(1) else result
        } catch (e: Exception) { encoded }
    }

    private fun inflateData(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            outputStream.write(buffer, 0, count)
        }
        inflater.end()
        return outputStream.toByteArray()
    }
}
