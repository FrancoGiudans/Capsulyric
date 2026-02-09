package com.example.islandlyrics

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Base64
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import javax.net.ssl.HostnameVerifier
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DebugLyricActivity : AppCompatActivity() {

    private lateinit var tvCurrentSong: TextView
    private lateinit var tvCurrentArtist: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvLyricsResult: TextView
    private lateinit var tvCurrentLyricLine: TextView
    private lateinit var tvPrevLyricLine: TextView
    private lateinit var tvNextLyricLine: TextView
    private lateinit var btnFetchLyrics: Button
    private lateinit var btnBack: Button

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .hostnameVerifier(HostnameVerifier { _, _ -> true })
        .build()

    // API结果数据类
    data class LyricResult(
        val api: String,                    // "Kugou" / "Netease" / "LrcApi"
        val lyrics: String?,                 // 歌词原文
        val parsedLines: List<LyricLine>?,   // 解析后的歌词行
        val hasSyllable: Boolean,            // 是否有逐字信息
        var score: Int = 0,                  // 评分
        val matchedTitle: String? = null,    // 匹配到的标题
        val matchedArtist: String? = null,   // 匹配到的艺术家
        val error: String? = null            // 错误信息
    )

    // 歌词数据
    private var parsedLyrics: List<LyricLine> = emptyList()
    private var hasSyllableInfo: Boolean = false
    private var currentPosition: Long = 0
    private var currentDuration: Long = 0

    // 多API结果
    private var allApiResults: MutableList<LyricResult> = mutableListOf()
    private var selectedResult: LyricResult? = null

    // 缓存当前歌词行的 Spannable，避免每次都重建
    private var currentLineText: String = ""
    private var currentLineSyllables: List<SyllableInfo>? = null
    private var cachedSpannable: SpannableStringBuilder? = null

    // Handler for UI updates - 使用 50ms 更新频率使动画更流畅
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateCurrentLyric()
            handler.postDelayed(this, 50) // 每50ms更新一次，使动画更流畅
        }
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_lyric)

        tvCurrentSong = findViewById(R.id.tv_current_song)
        tvCurrentArtist = findViewById(R.id.tv_current_artist)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvLyricsResult = findViewById(R.id.tv_lyrics_result)
        tvCurrentLyricLine = findViewById(R.id.tv_current_lyric_line)
        tvPrevLyricLine = findViewById(R.id.tv_prev_lyric_line)
        tvNextLyricLine = findViewById(R.id.tv_next_lyric_line)
        btnFetchLyrics = findViewById(R.id.btn_fetch_lyrics)
        btnBack = findViewById(R.id.btn_back)

        // 显示当前播放的音乐信息
        val repo = LyricRepository.getInstance()
        repo.liveMetadata.observe(this, Observer { mediaInfo ->
            if (mediaInfo != null) {
                tvCurrentSong.text = "歌曲: ${mediaInfo.title}"
                tvCurrentArtist.text = "歌手: ${mediaInfo.artist}"
            } else {
                tvCurrentSong.text = "歌曲: 无"
                tvCurrentArtist.text = "歌手: 无"
            }
        })

        // 监听播放进度
        repo.liveProgress.observe(this, Observer { progress ->
            if (progress != null) {
                currentPosition = progress.position
                currentDuration = progress.duration
                updateTimeDisplay()
            }
        })

        if (repo.liveMetadata.value == null) {
            tvCurrentSong.text = "歌曲: 无"
            tvCurrentArtist.text = "歌手: 无"
        }

        btnFetchLyrics.setOnClickListener {
            fetchAllApis()
        }

        btnBack.setOnClickListener {
            finish()
        }

        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    private fun updateTimeDisplay() {
        val posStr = formatTime(currentPosition)
        val durStr = formatTime(currentDuration)
        tvCurrentTime.text = "播放时间: $posStr / $durStr"
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateCurrentLyric() {
        if (parsedLyrics.isEmpty()) return
        
        val position = currentPosition
        val currentLine = findCurrentLyricLine(position)
        
        if (currentLine == null) {
            tvCurrentLyricLine.text = "等待歌词..."
            tvPrevLyricLine.text = ""
            tvNextLyricLine.text = ""
            currentLineText = ""
            currentLineSyllables = null
            cachedSpannable = null
            return
        }
        
        // 更新上一行和下一行歌词
        val currentIndex = parsedLyrics.indexOf(currentLine)
        if (currentIndex > 0) {
            tvPrevLyricLine.text = parsedLyrics[currentIndex - 1].text
        } else {
            tvPrevLyricLine.text = ""
        }
        if (currentIndex < parsedLyrics.size - 1) {
            tvNextLyricLine.text = parsedLyrics[currentIndex + 1].text
        } else {
            tvNextLyricLine.text = ""
        }
        
        // 如果是新的一行，重新构建Spannable
        if (currentLine.text != currentLineText) {
            currentLineText = currentLine.text
            currentLineSyllables = currentLine.syllables
            
            // 使用 SpannableStringBuilder 以支持动态修改
            val builder = android.text.SpannableStringBuilder(currentLine.text)
            // 设置默认颜色（未唱的字 - 使用灰色）
            val unsungColor = ContextCompat.getColor(this, android.R.color.darker_gray)
            builder.setSpan(
                ForegroundColorSpan(unsungColor),
                0,
                currentLine.text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            cachedSpannable = builder
            tvCurrentLyricLine.text = cachedSpannable
        }

        // 如果没有逐字信息，跳过
        val syllables = currentLineSyllables
        if (syllables == null || syllables.isEmpty()) return

        // 只更新需要高亮的部分（已唱的字 - 使用亮紫色）
        val sungColor = ContextCompat.getColor(this, R.color.lyric_highlight)
        var currentSyllableIndex = 0
        var hasChanges = false
        
        for (syllable in syllables) {
            val start = currentSyllableIndex
            val end = currentSyllableIndex + syllable.text.length
            
            if (end <= currentLine.text.length) {
                // 检查当前字是否已经唱过或正在唱
                // Use startTime for "Karaoke Active" feel (highlight as soon as it starts)
                val shouldBeHighlighted = position >= syllable.startTime
                val existingSpans = cachedSpannable?.getSpans(start, end, ForegroundColorSpan::class.java)
                val isCurrentlyHighlighted = existingSpans?.any { it.foregroundColor == sungColor } ?: false
                
                // Debug log for first few updates to avoid spam
                if (position % 1000 < 100) { 
                    AppLogger.getInstance().log("DebugSyllable", "Pos: $position, SyllEnd: ${syllable.endTime}, ShouldHL: $shouldBeHighlighted, Text: ${syllable.text}")
                }

                // 只在颜色需要改变时才更新
                if (shouldBeHighlighted && !isCurrentlyHighlighted) {
                    existingSpans?.forEach { cachedSpannable?.removeSpan(it) }
                    cachedSpannable?.setSpan(
                        ForegroundColorSpan(sungColor),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    hasChanges = true
                }
            }
            currentSyllableIndex = end
        }
        
        // 只有当有改变时才更新 TextView，或者直接更新以确保一致性
        if (hasChanges) {
             tvCurrentLyricLine.text = cachedSpannable
        }
    }

    private fun findCurrentLyricLine(position: Long): LyricLine? {
        // 使用二分查找优化性能
        var left = 0
        var right = parsedLyrics.size - 1
        
        while (left <= right) {
            val mid = (left + right) / 2
            val line = parsedLyrics[mid]
            
            if (position >= line.startTime && position < line.endTime) {
                return line
            } else if (position < line.startTime) {
                right = mid - 1
            } else {
                left = mid + 1
            }
        }
        
        // 如果超过最后一行，返回最后一行
        if (parsedLyrics.isNotEmpty() && position >= parsedLyrics.last().startTime) {
            return parsedLyrics.last()
        }
        return null
    }

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

    private fun String.encodeURL(): String {
        return URLEncoder.encode(this, "UTF-8")
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

    private fun parseKrcLine(line: String): LyricLine? {
        try {
            // regex to extract line start time and duration: [start,duration]
            // Note: KRC lines start with [offset,duration]
            val lineHeaderCallback = Regex("^\\[(\\d+),(\\d+)\\]")
            val headerMatch = lineHeaderCallback.find(line) ?: return null
            
            val lineStartTime = headerMatch.groupValues[1].toLong()
            val lineDuration = headerMatch.groupValues[2].toLong()
            val lineEndTime = lineStartTime + lineDuration
            
            val contentPart = line.substring(headerMatch.range.last + 1)
            
            // Regex to parse syllables: <offset,duration,id>SyllableText
            // id is usually 0, but can be 1 (duet)
            val syllableRegex = Regex("<(\\d+),(\\d+),(\\d+)>([^<]*)")
            val matches = syllableRegex.findAll(contentPart)
            
            val syllables = mutableListOf<SyllableInfo>()
            val fullText = StringBuilder()
            
            for (match in matches) {
                val offset = match.groupValues[1].toLong()
                val duration = match.groupValues[2].toLong()
                val text = match.groupValues[4] // The text part
                
                val absStartTime = lineStartTime + offset
                val absEndTime = absStartTime + duration
                
                syllables.add(SyllableInfo(absStartTime, absEndTime, text))
                fullText.append(text)
            }
            
            if (syllables.isEmpty()) {
                // Should not happen for valid KRC lines unless empty
                // But if text is really empty or just tags?
                // Fallback: if content exists but regex failed?
                if (contentPart.isNotEmpty() && fullText.isEmpty()) {
                     // Maybe it's not KRC standard?
                     return null
                }
            }

            return if (syllables.isNotEmpty()) {
                LyricLine(lineStartTime, lineEndTime, fullText.toString(), syllables)
            } else {
                // Empty line
                null
            }
        } catch (e: Exception) {
            AppLogger.getInstance().log("DebugLyric", "解析KRC行失败: ${e.message}, line: $line")
            return null
        }
    }

    private fun decodeKugouLyric(encoded: String): String {
        try {
            val data = Base64.decode(encoded, Base64.DEFAULT)
            // KRC encrypyted data starts after 4 bytes
            val dataWithoutHeader = data.copyOfRange(4, data.size)
            val decryptKey = byteArrayOf(
                0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
                0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69
            )
            
            for (i in dataWithoutHeader.indices) {
                dataWithoutHeader[i] = (dataWithoutHeader[i].toInt() xor decryptKey[i % decryptKey.size].toInt()).toByte()
            }
            
            // Checks for 'krc1' magic header which indicates successful decryption 
            // but before decompression? 
            // Actually KRC decrypted content IS a zip file usually.
            
            val decompressedData = inflateData(dataWithoutHeader)
            
            // The result of decompression is the actual UTF-8 text
            val result = String(decompressedData, Charsets.UTF_8)
            
            // C# implementation skips the first character (res[1..])
            return if (result.isNotEmpty()) result.substring(1) else result
            
        } catch (e: Exception) {
            AppLogger.getInstance().log("DebugLyric", "Kugou歌词解密失败: ${e.message}")
            return encoded // Return original on failure for debugging
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
             AppLogger.getInstance().log("DebugLyric", "解压失败: ${e.message}")
             throw e
        }
    }

    // ========== 新增：多API并发调用系统 ==========

    private fun fetchAllApis() {
        val repo = LyricRepository.getInstance()
        val mediaInfo = repo.liveMetadata.value

        if (mediaInfo == null || mediaInfo.title.isBlank() || mediaInfo.artist.isBlank()) {
            Toast.makeText(this, "没有可用的歌曲信息", Toast.LENGTH_SHORT).show()
            return
        }

        tvLyricsResult.text = "正在从三个API获取歌词...\n\n酷狗: 查询中...\n网易云: 查询中...\nLrcApi: 查询中..."
        tvCurrentLyricLine.text = "正在获取歌词..."
        parsedLyrics = emptyList()
        hasSyllableInfo = false
        btnFetchLyrics.isEnabled = false

        val title = mediaInfo.title
        val artist = mediaInfo.artist

        lifecycleScope.launch {
            try {
                // 并发调用三个API，总超时10秒
                val results = withTimeoutOrNull(10000) {
                    listOf(
                        async { fetchKugouAsync(title, artist) },
                        async { fetchNeteaseAsync(title, artist) },
                        async { fetchLrcApiAsync(title, artist) }
                    ).awaitAll()
                } ?: run {
                    withContext(Dispatchers.Main) {
                        tvLyricsResult.text = "请求超时，请重试"
                        btnFetchLyrics.isEnabled = true
                    }
                    return@launch
                }

                allApiResults.clear()
                allApiResults.addAll(results.filterNotNull())

                withContext(Dispatchers.Main) {
                    if (allApiResults.isEmpty()) {
                        tvLyricsResult.text = "所有API都未返回结果"
                        btnFetchLyrics.isEnabled = true
                        return@withContext
                    }

                    // 计算评分并选择最佳结果
                    selectBestResult(title, artist)
                    displayAllResults()
                    btnFetchLyrics.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvLyricsResult.text = "获取歌词失败: ${e.message}"
                    btnFetchLyrics.isEnabled = true
                    AppLogger.getInstance().log("DebugLyric", "多API获取失败: ${e.message}")
                }
            }
        }
    }

    private suspend fun fetchKugouAsync(title: String, artist: String): LyricResult? = withContext(Dispatchers.IO) {
        try {
            val keywords = "$title $artist"
            val searchUrl = "https://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=${keywords.encodeURL()}&page=1&pagesize=20&showtype=1"

            val searchResponse = suspendCancellableCoroutine<String?> { continuation ->
                val request = Request.Builder().url(searchUrl).build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response.body?.string())
                    }
                })
            }

            if (searchResponse == null) {
                AppLogger.getInstance().log("DebugLyric", "Kugou搜索请求失败: Response is null")
                return@withContext LyricResult("Kugou", null, null, false, error = "搜索请求失败")
            }

            val searchJson = JSONObject(searchResponse)
            val dataObj = searchJson.optJSONObject("data")
            val infoArray = dataObj?.optJSONArray("info")

            if (infoArray == null || infoArray.length() == 0) {
                AppLogger.getInstance().log("DebugLyric", "Kugou未找到歌曲: JSON=$searchResponse")
                return@withContext LyricResult("Kugou", null, null, false, error = "未找到歌曲")
            }

            val firstSong = infoArray.getJSONObject(0)
            val matchedTitle = firstSong.optString("songname", "")
            val matchedArtist = firstSong.optString("singername", "")
            val hash = firstSong.optString("hash", "")

            if (hash.isEmpty()) {
                return@withContext LyricResult("Kugou", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "无歌曲hash")
            }

            // 获取歌词
            // 原始实现参考 C#: https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword={keywords}&duration={duration}&hash={hash}
            val durationParam = if (currentDuration > 0) currentDuration else ""
            val lyricUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=&duration=$durationParam&hash=$hash"

            val lyricResponse = suspendCancellableCoroutine<String?> { continuation ->
                val request = Request.Builder().url(lyricUrl).build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response.body?.string())
                    }
                })
            }

            if (lyricResponse == null) {
                AppLogger.getInstance().log("DebugLyric", "Kugou歌词请求失败: Response is null")
                return@withContext LyricResult("Kugou", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "歌词请求失败")
            }

            val lyricJson = JSONObject(lyricResponse)
            val candidates = lyricJson.optJSONArray("candidates")

            if (candidates == null || candidates.length() == 0) {
                val msg = "无歌词. JSON: $lyricResponse"
                AppLogger.getInstance().log("DebugLyric", "Kugou无候选歌词: $msg")
                return@withContext LyricResult("Kugou", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = msg)
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
                                continuation.resume(response.body?.string())
                            }
                        })
                    }

                    if (downloadResponse != null) {
                        try {
                            val downloadJson = JSONObject(downloadResponse)
                            lyricEncoded = downloadJson.optString("content", "")
                        } catch (e: Exception) {
                            AppLogger.getInstance().log("DebugLyric", "Kugou下载响应解析失败: ${e.message}")
                        }
                    } else {
                        AppLogger.getInstance().log("DebugLyric", "Kugou下载请求失败")
                    }
                }
            }

            if (lyricEncoded.isEmpty()) {
                val msg = "歌词内容为空 (已尝试下载). JSON: $lyricInfo"
                AppLogger.getInstance().log("DebugLyric", "Kugou歌词内容字段为空: $msg")
                return@withContext LyricResult("Kugou", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = msg)
            }


            // lyricEncoded is base64-encoded encrypted binary data
            // Pass it directly to decodeKugouLyric which will handle base64 decode, XOR, and decompression
            val lyricContent = decodeKugouLyric(lyricEncoded)


            val hasSyllable = lyricContent.contains("<") && lyricContent.contains(">")
            val parsedLines = if (hasSyllable) parseKrcLyrics(lyricContent) else parseLrcLyrics(lyricContent)

            LyricResult("Kugou", lyricContent, parsedLines, hasSyllable, matchedTitle = matchedTitle, matchedArtist = matchedArtist)

        } catch (e: Exception) {
            AppLogger.getInstance().log("DebugLyric", "Kugou API失败: ${e.message}")
            LyricResult("Kugou", null, null, false, error = e.message)
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
                        continuation.resume(response.body?.string())
                    }
                })
            }

            if (searchResponse == null) {
                AppLogger.getInstance().log("DebugLyric", "Netease搜索请求失败: Response is null")
                return@withContext LyricResult("Netease", null, null, false, error = "搜索请求失败")
            }

            val searchJson = JSONObject(searchResponse)
            val result = searchJson.optJSONObject("result")
            val songs = result?.optJSONArray("songs")

            if (songs == null || songs.length() == 0) {
                AppLogger.getInstance().log("DebugLyric", "Netease未找到歌曲: JSON=$searchResponse")
                return@withContext LyricResult("Netease", null, null, false, error = "未找到歌曲")
            }

            val firstSong = songs.getJSONObject(0)
            val matchedTitle = firstSong.optString("name", "")
            val songId = firstSong.optLong("id", 0)
            val artists = firstSong.optJSONArray("artists")
            val matchedArtist = if (artists != null && artists.length() > 0) {
                artists.getJSONObject(0).optString("name", "")
            } else ""

            if (songId == 0L) {
                return@withContext LyricResult("Netease", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "无歌曲ID")
            }

            // 获取歌词
            val lyricUrl = "https://music.163.com/api/song/lyric?id=$songId&lv=-1&tv=-1"

            val lyricResponse = suspendCancellableCoroutine<String?> { continuation ->
                val request = Request.Builder().url(lyricUrl).build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response.body?.string())
                    }
                })
            }

            if (lyricResponse == null) {
                AppLogger.getInstance().log("DebugLyric", "Netease歌词请求失败: Response is null")
                return@withContext LyricResult("Netease", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = "歌词请求失败")
            }

            val lyricJson = JSONObject(lyricResponse)
            val lrc = lyricJson.optJSONObject("lrc")
            val lyricContent = lrc?.optString("lyric", "") ?: ""

            if (lyricContent.isEmpty()) {
                val msg = "无歌词内容. JSON: $lyricResponse"
                AppLogger.getInstance().log("DebugLyric", "Netease无歌词内容: $msg")
                return@withContext LyricResult("Netease", null, null, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist, error = msg)
            }

            val parsedLines = parseLrcLyrics(lyricContent)
            LyricResult("Netease", lyricContent, parsedLines, false, matchedTitle = matchedTitle, matchedArtist = matchedArtist)

        } catch (e: Exception) {
            AppLogger.getInstance().log("DebugLyric", "Netease API失败: ${e.message}")
            LyricResult("Netease", null, null, false, error = e.message)
        }
    }

    private suspend fun fetchLrcApiAsync(title: String, artist: String): LyricResult? = withContext(Dispatchers.IO) {
        try {
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
                        continuation.resume(response.body?.string())
                    }
                })
            }

            if (response == null) {
                AppLogger.getInstance().log("DebugLyric", "LrcApi请求失败: Response is null")
                return@withContext LyricResult("LrcApi", null, null, false, error = "请求失败")
            }

            if (response.isEmpty() || response.contains("Lyrics not found")) {
                val msg = "未找到歌词. Response: $response"
                AppLogger.getInstance().log("DebugLyric", "LrcApi未找到歌词: $msg")
                return@withContext LyricResult("LrcApi", null, null, false, error = msg)
            }

            val hasSyllable = response.contains("<") && response.contains(">") && response.contains("[") && response.length > 1 && response[1].isDigit()
            val parsedLines = if (hasSyllable) parseKrcLyrics(response) else parseLrcLyrics(response)

            // LrcApi不返回匹配的标题，使用null
            LyricResult("LrcApi", response, parsedLines, hasSyllable)

        } catch (e: Exception) {
            AppLogger.getInstance().log("DebugLyric", "LrcApi失败: ${e.message}")
            LyricResult("LrcApi", null, null, false, error = e.message)
        }
    }

    private fun selectBestResult(originalTitle: String, originalArtist: String) {
        // 评分权重
        // - 标题歌手完全匹配：40分
        // - 逐字歌词：30分
        // - 标题歌手其中一个完全匹配：20分
        // - 成功获取歌词：10分

        allApiResults.forEach { result ->
            var score = 0

            // 1. 成功获取歌词基础分（10分）
            if (result.lyrics != null && result.lyrics.isNotEmpty() && result.parsedLines != null && result.parsedLines.isNotEmpty()) {
                score += 10
            }

            // 2. 逐字歌词（30分）
            if (result.hasSyllable) {
                score += 30
            }

            // 歌词带有时间轴 20分
            // 如果是逐字歌词(hasSyllable)，必然包含时间轴
            // 否则检查是否包含 [MM:SS.xx] 或 [MM.SS.xxx] 格式的时间标签
            val timestampRegex = Regex("\\[\\d{1,2}[.:]\\d{1,2}[.:]\\d{1,3}]")
            if (result.hasSyllable || (result.lyrics != null && timestampRegex.containsMatchIn(result.lyrics))) {
                score += 20
            }

            // 4. 标题歌手匹配
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
            } else if (result.api == "LrcApi" && result.lyrics != null) {
                // LrcApi 是直接精确搜索，如果不返回元数据但有结果，可视为部分匹配或完全匹配?
                // 既然LrcApi是用来兜底的，且通过精确参数请求，暂不额外加分，避免干扰Kugou/Netease的高分
            }

            result.score = score
        }

        // 按分数降序排序，分数相同优先保留原有顺序（或按API优先级）
        allApiResults.sortByDescending { it.score }
        
        // 自动选择最高分结果
        if (allApiResults.isNotEmpty()) {
            val bestResult = allApiResults[0]
            selectedResult = bestResult // Update selectedResult
            parsedLyrics = bestResult.parsedLines ?: emptyList() // Update parsedLyrics
            hasSyllableInfo = bestResult.hasSyllable // Update hasSyllableInfo
            currentLineText = ""
            currentLineSyllables = null
            cachedSpannable = null
            
            AppLogger.getInstance().log("DebugLyric", "自动选择: ${bestResult.api} (分: ${bestResult.score})")
        }
    }

    private fun calculateStringSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        // 使用Jaccard相似度
        val set1 = s1.toSet()
        val set2 = s2.toSet()
        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size

        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    private fun displayAllResults() {
        val resultText = StringBuilder("所有API结果（按评分排序）:\n\n")

        allApiResults.forEach { result ->
            val isSelected = result == selectedResult
            val prefix = if (isSelected) "★ [已选择] " else "  "

            resultText.append("$prefix${result.api} (得分: ${result.score})\n")

            if (result.error != null) {
                resultText.append("  ✗ 错误: ${result.error}\n")
            } else {
                result.matchedTitle?.let {
                    resultText.append("  标题: $it\n")
                }
                result.matchedArtist?.let {
                    resultText.append("  艺术家: $it\n")
                }
                if (result.hasSyllable) {
                    resultText.append("  ✓ 有逐字歌词 (+30分)\n")
                } else if (result.lyrics != null) {
                    resultText.append("  标准LRC歌词\n")
                }
                if (result.lyrics != null) {
                    val preview = result.lyrics.take(100).replace("\n", " ")
                    resultText.append("  预览: $preview...\n")
                }
            }
            resultText.append("\n")
        }

        selectedResult?.let {
            if (it.lyrics != null) {
                resultText.append("─────────────────────\n")
                resultText.append("已应用 ${it.api} 的歌词\n")
                resultText.append("─────────────────────\n\n")
                resultText.append(it.lyrics)
            }
        }

        tvLyricsResult.text = resultText.toString()

        if (selectedResult != null && parsedLyrics.isNotEmpty()) {
            Toast.makeText(this, "已自动选择最佳结果：${selectedResult!!.api} (得分:${selectedResult!!.score})", Toast.LENGTH_LONG).show()
        }
    }
}
