package com.example.islandlyrics.lyrics.online.provider

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.crypto.QqLyricPayloadDecoder
import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser

import android.util.Base64
import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class QqMusicLyricProvider(
    private val httpClient: OnlineLyricHttpClient
) {
    suspend fun fetch(title: String, artist: String): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                val keyword = "$title $artist"
                val searchPayload = """
                    {"music.search.SearchCgiService":{"method":"DoSearchForQQMusicDesktop","module":"music.search.SearchCgiService","param":{"num_per_page":10,"page_num":1,"query":"${escapeJson(keyword)}","search_type":0}}}
                """.trimIndent()

                val searchResponse = httpClient.postJsonString(
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
                    return@withContext OnlineLyricFetcher.LyricResult(
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
                val lyricResponse = httpClient.postForm(
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
                val downloadExtras = fetchLyricExtras(songId)
                val transContent = downloadExtras.translation.ifBlank {
                    decodeBase64Text(lyricJson.optString("trans", ""))
                }
                val romanContent = downloadExtras.romanization
                val mergedContent = lyricContent.ifBlank { transContent }

                if (mergedContent.isBlank()) {
                    return@withContext OnlineLyricFetcher.LyricResult(
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

                val parsedLines = OnlineLyricParser.parseLrcLyrics(mergedContent)
                OnlineLyricFetcher.LyricResult(
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

    private suspend fun fetchLyricExtras(songId: String): QqLyricExtras {
        if (songId.isBlank()) return QqLyricExtras()
        return runCatching {
            val response = httpClient.postForm(
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
            val rawTranslationPayload = QqLyricPayloadDecoder.extractTagContent(response, "contentts").orEmpty()
            val rawRomanPayload = QqLyricPayloadDecoder.extractTagContent(response, "contentroma").orEmpty()
            QqLyricExtras(
                translation = QqLyricPayloadDecoder.decodeDownloadPayload(rawTranslationPayload),
                romanization = QqLyricPayloadDecoder.decodeDownloadPayload(rawRomanPayload)
            )
        }.onFailure {
            AppLogger.getInstance().d("OnlineLyric", "QQ lyric extras fetch skipped: ${it.message}")
        }.getOrDefault(QqLyricExtras())
    }

    private fun qqHeaders(referer: String): Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36",
        "Referer" to referer,
        "Cookie" to "os=pc;osver=Microsoft-Windows-10-Professional-build-16299.125-64bit;appver=2.0.3.131777;channel=netease;__remember_me=true"
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
}


