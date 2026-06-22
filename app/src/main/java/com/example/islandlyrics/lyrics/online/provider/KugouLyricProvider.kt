package com.example.islandlyrics.lyrics.online.provider

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser

import android.util.Base64
import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.zip.Inflater

internal class KugouLyricProvider(
    private val httpClient: OnlineLyricHttpClient
) {
    suspend fun fetch(title: String, artist: String): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                val keywords = "$title $artist"
                val searchUrl = "https://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=${keywords.encodeURL()}&page=1&pagesize=20&showtype=1"
                val searchResponse = httpClient.get(searchUrl)
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
                    return@withContext OnlineLyricFetcher.LyricResult(
                        "Kugou",
                        null,
                        null,
                        false,
                        provider = OnlineLyricProvider.Kugou,
                        matchedTitle = matchedTitle,
                        matchedArtist = matchedArtist,
                        error = "无歌曲hash"
                    )
                }

                val lyricUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=&duration=&hash=$hash"
                val lyricResponse = httpClient.get(lyricUrl)
                    ?: return@withContext OnlineLyricFetcher.LyricResult(
                        "Kugou",
                        null,
                        null,
                        false,
                        provider = OnlineLyricProvider.Kugou,
                        matchedTitle = matchedTitle,
                        matchedArtist = matchedArtist,
                        error = "歌词请求失败"
                    )

                val lyricJson = JSONObject(lyricResponse)
                val candidates = lyricJson.optJSONArray("candidates")
                    ?: return@withContext OnlineLyricFetcher.LyricResult(
                        "Kugou",
                        null,
                        null,
                        false,
                        provider = OnlineLyricProvider.Kugou,
                        matchedTitle = matchedTitle,
                        matchedArtist = matchedArtist,
                        error = "无候选歌词"
                    )
                if (candidates.length() == 0) {
                    return@withContext OnlineLyricFetcher.LyricResult(
                        "Kugou",
                        null,
                        null,
                        false,
                        provider = OnlineLyricProvider.Kugou,
                        matchedTitle = matchedTitle,
                        matchedArtist = matchedArtist,
                        error = "无候选歌词"
                    )
                }

                val lyricInfo = candidates.getJSONObject(0)
                var lyricEncoded = lyricInfo.optString("content", "")
                if (lyricEncoded.isEmpty()) {
                    val id = lyricInfo.optString("id", "")
                    val accessKey = lyricInfo.optString("accesskey", "")
                    if (id.isNotEmpty() && accessKey.isNotEmpty()) {
                        val downloadUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=krc&charset=utf8"
                        val downloadResponse = httpClient.get(downloadUrl)
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
                    return@withContext OnlineLyricFetcher.LyricResult(
                        "Kugou",
                        null,
                        null,
                        false,
                        provider = OnlineLyricProvider.Kugou,
                        matchedTitle = matchedTitle,
                        matchedArtist = matchedArtist,
                        error = "歌词内容为空"
                    )
                }

                val lyricContent = decodeKugouLyric(lyricEncoded)
                val hasSyllable = OnlineLyricParser.isWordLevelLyrics(lyricContent)
                val parsedLines = if (hasSyllable) {
                    OnlineLyricParser.parseWordLevelLyrics(lyricContent)
                } else {
                    OnlineLyricParser.parseLrcLyrics(lyricContent)
                }

                OnlineLyricFetcher.LyricResult(
                    "Kugou",
                    lyricContent,
                    parsedLines,
                    hasSyllable,
                    provider = OnlineLyricProvider.Kugou,
                    matchedTitle = matchedTitle,
                    matchedArtist = matchedArtist
                )
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "Kugou API错误: ${e.message}")
                null
            }
        }

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

    private fun String.encodeURL(): String =
        URLEncoder.encode(this, "UTF-8")
}


