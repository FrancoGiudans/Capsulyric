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

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.crypto.QqLyricPayloadDecoder
import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser
import com.example.islandlyrics.lyrics.online.parser.QrcParser
import com.example.islandlyrics.lyrics.online.selection.CandidateMatcher
import com.example.islandlyrics.lyrics.online.selection.SearchCandidate

import android.util.Base64
import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class QqMusicLyricProvider(
    private val httpClient: OnlineLyricHttpClient
) {
    suspend fun fetch(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        versionReferenceTitle: String = title
    ): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                val keyword = ProviderSearchTerm.build(title, artist, album)
                val candidates = searchMobile(keyword).ifEmpty { searchDesktop(keyword) }
                if (candidates.isEmpty()) return@withContext null

                val topCandidates = CandidateMatcher.pickTopCandidates(
                    candidates,
                    title,
                    artist,
                    album,
                    durationMs,
                    limit = 2,
                    versionReferenceTitle = versionReferenceTitle
                )
                if (topCandidates.isEmpty()) return@withContext null

                var firstErrorResult: OnlineLyricFetcher.LyricResult? = null
                for (candidate in topCandidates) {
                    val result = fetchSong(candidate)
                    if (result != null && !result.parsedLines.isNullOrEmpty()) {
                        return@withContext result
                    }
                    if (firstErrorResult == null && result != null) {
                        firstErrorResult = result
                    }
                }
                firstErrorResult
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "QQMusic API错误: ${e.message}")
                null
            }
        }

    private suspend fun searchMobile(keyword: String): List<QqSongCandidate> {
        return runCatching {
            val payload = JSONObject()
                .put(
                    "comm",
                    JSONObject()
                        .put("ct", "11")
                        // 匿名调用中 ct=11 与 cv 同发会返回空结果，仅保留协议版本 v。
                        .put("v", MOBILE_CLIENT_VERSION)
                        .put("tmeLoginType", "0")
                        .put("tmeLoginMethod", "0")
                )
                .put(
                    MOBILE_SEARCH_REQUEST_KEY,
                    JSONObject()
                        .put("module", SEARCH_MODULE)
                        .put("method", MOBILE_SEARCH_METHOD)
                        .put(
                            "param",
                            JSONObject()
                                .put("query", keyword)
                                .put("highlight", 1)
                                .put("searchid", "")
                                .put("sub_searchid", 0)
                                .put("search_type", 0)
                                .put("nqc_flag", 0)
                                .put("sin", 0)
                                .put("ein", 30)
                                .put("page_num", 1)
                                .put("num_per_page", 20)
                                .put("cat", 2)
                                .put("grp", 1)
                                .put("remoteplace", "")
                                .put("from", "")
                                .put("multi_zhida", 1)
                                .put("sem", 0)
                                .put("related_data", "")
                                .put("ambi_data", "")
                        )
                )
                .toString()

            val response = httpClient.postJsonString(
                url = MUSICU_URL,
                bodyJson = payload,
                headers = mapOf(
                    "User-Agent" to "QQMusic $MOBILE_CLIENT_VERSION(android)",
                    "Accept" to "*/*"
                ),
                contentType = "application/x-www-form-urlencoded",
                requireSuccessful = true
            ) ?: return emptyList()

            parseMobileSearchCandidates(response)
        }.getOrDefault(emptyList())
    }

    private suspend fun searchDesktop(keyword: String): List<QqSongCandidate> {
        return runCatching {
            val payload = JSONObject()
                .put(
                    SEARCH_MODULE,
                    JSONObject()
                        .put("method", DESKTOP_SEARCH_METHOD)
                        .put("module", SEARCH_MODULE)
                        .put(
                            "param",
                            JSONObject()
                                .put("num_per_page", 15)
                                .put("page_num", 1)
                                .put("query", keyword)
                                .put("search_type", 0)
                                .put("grp", 1)
                        )
                )
                .toString()

            val response = httpClient.postJsonString(
                url = MUSICU_URL,
                bodyJson = payload,
                headers = qqHeaders("https://c.y.qq.com/")
            ) ?: return emptyList()

            parseDesktopSearchCandidates(response)
        }.getOrDefault(emptyList())
    }

    suspend fun fetchById(trackId: String): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                val parameter = if (trackId.all { it in '0'..'9' }) "songid" else "songmid"
                val response = httpClient.get(
                    "https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg?format=json&$parameter=$trackId",
                    headers = qqHeaders()
                ) ?: return@withContext null
                val song = JSONObject(response).optJSONArray("data")?.optJSONObject(0)
                    ?: return@withContext null
                fetchSong(QqSongCandidate(song))
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "QQMusic API错误: ${e.message}")
                null
            }
        }

    private suspend fun fetchSong(best: QqSongCandidate): OnlineLyricFetcher.LyricResult? =
        withContext(Dispatchers.IO) {
            try {
                val firstSong = best.song
                val songId = firstSong.optString("id").ifBlank { firstSong.optString("songid", "") }
                val songMid = firstSong.optString("mid").ifBlank { firstSong.optString("songmid", "") }
                val matchedTitle = best.matchedTitle
                val matchedArtist = best.matchedArtist
                val matchedAlbum = best.matchedAlbum
                val matchedDurationMs = best.matchedDurationMs
                val providerTrackId = best.providerTrackId

                if (songMid.isBlank()) {
                    return@withContext OnlineLyricFetcher.LyricResult(
                        api = "QQMusic",
                        lyrics = null,
                        parsedLines = null,
                        hasSyllable = false,
                        provider = OnlineLyricProvider.QQMusic,
                        matchedTitle = matchedTitle,
                        matchedArtist = matchedArtist,
                        matchedAlbum = matchedAlbum,
                        matchedDurationMs = matchedDurationMs,
                        providerTrackId = providerTrackId,
                        error = "无 songMid"
                    )
                }

                val callback = "MusicJsonCallback_lrc"
                val (lyricResponse, downloadExtras) = coroutineScope {
                    val lrcDeferred = async {
                        httpClient.postForm(
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
                            headers = qqHeaders("https://y.qq.com/")
                        )
                    }
                    val extrasDeferred = async { fetchLyricExtras(songId) }
                    lrcDeferred.await() to extrasDeferred.await()
                }

                val lyricJson = lyricResponse
                    ?.let { unwrapJsonp(callback, it) }
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                val lyricContent = lyricJson?.let { decodeBase64Text(it.optString("lyric", "")) }.orEmpty()
                val transContent = downloadExtras.translation.ifBlank {
                    lyricJson?.let { decodeBase64Text(it.optString("trans", "")) }.orEmpty()
                }
                val romanContent = downloadExtras.romanization
                val qrcContent = downloadExtras.qrcContent
                val mergedContent = lyricContent.ifBlank { transContent }.ifBlank { qrcContent }

                if (mergedContent.isBlank()) {
                    return@withContext OnlineLyricFetcher.LyricResult(
                        api = "QQMusic",
                        lyrics = null,
                        parsedLines = null,
                        hasSyllable = false,
                        provider = OnlineLyricProvider.QQMusic,
                        matchedTitle = matchedTitle,
                        matchedArtist = matchedArtist,
                        matchedAlbum = matchedAlbum,
                        matchedDurationMs = matchedDurationMs,
                        providerTrackId = providerTrackId,
                        error = "无歌词内容"
                    )
                }

                // QRC 逐字优先：lyric_download.fcg 的 content 字段解密后若为 QRC 形态则用逐字解析；
                // 若非 QRC 但含 [startMs,durMs] 逐行段，同样优先于 fcg_query_lyric_new 的普通 LRC。
                // 若 QRC/逐行解析得到 0 行，平滑降级为普通 LRC 解析，保证有效歌词不被丢弃。
                val hasSyllable = QrcParser.isQrcContent(qrcContent)
                val lineSegmentedContent = qrcContent.takeIf {
                    !hasSyllable && OnlineLyricParser.hasQqLineSegments(it)
                }
                val rawParsedLines = when {
                    hasSyllable -> OnlineLyricParser.parseQrcLyrics(qrcContent)
                    lineSegmentedContent != null -> OnlineLyricParser.parseLrcLyrics(lineSegmentedContent)
                    else -> emptyList()
                }
                val (primaryContent, parsedLines) = if (rawParsedLines.isNotEmpty()) {
                    Pair(
                        if (hasSyllable) qrcContent else lineSegmentedContent ?: mergedContent,
                        rawParsedLines
                    )
                } else {
                    Pair(
                        mergedContent,
                        OnlineLyricParser.parseLrcLyrics(mergedContent)
                    )
                }
                OnlineLyricFetcher.LyricResult(
                    api = "QQMusic",
                    lyrics = primaryContent,
                    parsedLines = parsedLines,
                    hasSyllable = parsedLines.any { !it.syllables.isNullOrEmpty() },
                    provider = OnlineLyricProvider.QQMusic,
                    matchedTitle = matchedTitle,
                    matchedArtist = matchedArtist,
                    matchedAlbum = matchedAlbum,
                    matchedDurationMs = matchedDurationMs,
                    providerTrackId = providerTrackId,
                    translationLyrics = transContent.takeIf { it.isNotBlank() },
                    romanLyrics = romanContent.takeIf { it.isNotBlank() }
                )
            } catch (e: Exception) {
                AppLogger.getInstance().log("OnlineLyric", "QQMusic API错误: ${e.message}")
                null
            }
        }

    private data class QqLyricExtras(
        val qrcContent: String = "",
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
                headers = qqHeaders("https://y.qq.com/")
            ).orEmpty()
                .replace("<!--", "")
                .replace("-->", "")
                .trim()
            val rawContent = QqLyricPayloadDecoder.extractTagContent(response, "content").orEmpty()
            val rawTranslationPayload = QqLyricPayloadDecoder.extractTagContent(response, "contentts").orEmpty()
            val rawRomanPayload = QqLyricPayloadDecoder.extractTagContent(response, "contentroma").orEmpty()
            QqLyricExtras(
                qrcContent = QqLyricPayloadDecoder.decodeDownloadPayload(rawContent),
                translation = QqLyricPayloadDecoder.decodeDownloadPayload(rawTranslationPayload),
                romanization = QqLyricPayloadDecoder.decodeDownloadPayload(rawRomanPayload)
            )
        }.onFailure {
            AppLogger.getInstance().d("OnlineLyric", "QQ lyric extras fetch skipped: ${it.message}")
        }.getOrDefault(QqLyricExtras())
    }

    private fun qqHeaders(referer: String = "https://y.qq.com/"): Map<String, String> = mapOf(
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
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val prefix = "$callback("
        if (trimmed.startsWith(prefix) && trimmed.endsWith(")")) {
            return trimmed.removePrefix(prefix).dropLast(1).trim()
        }
        val match = Regex("""^[^(]*\(([\s\S]*)\)[^)]*$""").find(trimmed)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    internal class QqSongCandidate(
        val song: JSONObject
    ) : SearchCandidate {
        override val matchedTitle: String
            get() = song.optString("title")
                .ifBlank { song.optString("name") }
                .ifBlank { song.optString("songname", "") }

        override val matchedArtist: String
            get() = song.optJSONArray("singer")
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
                ?.ifBlank { song.optString("singername", "") }
                ?: song.optString("singername", "")

        override val matchedAlbum: String?
            get() = song.optJSONObject("album")?.optString("name").orEmpty()
                .ifBlank { song.optString("albumname", "") }
                .takeIf { it.isNotBlank() }

        override val matchedDurationMs: Long?
            get() = song.optLong("interval", 0L)
                .takeIf { it > 0L }
                ?.times(1000L)

        override val providerTrackId: String?
            get() = song.optString("mid")
                .ifBlank { song.optString("songmid") }
                .ifBlank { song.optString("id").ifBlank { song.optString("songid") } }
                .takeIf { it.isNotBlank() }
    }

    internal companion object {
        private const val MUSICU_URL = "https://u6.y.qq.com/cgi-bin/musicu.fcg"
        private const val SEARCH_MODULE = "music.search.SearchCgiService"
        private const val DESKTOP_SEARCH_METHOD = "DoSearchForQQMusicDesktop"
        private const val MOBILE_SEARCH_METHOD = "DoSearchForQQMusicMobile"
        private const val MOBILE_SEARCH_REQUEST_KEY = "$SEARCH_MODULE.$MOBILE_SEARCH_METHOD"
        private const val MOBILE_CLIENT_VERSION = "20080508"

        internal fun parseMobileSearchCandidates(rawResponse: String): List<QqSongCandidate> {
            val json = JSONObject(rawResponse)
            if (json.optInt("code", -1) != 0) return emptyList()
            val req = json.optJSONObject(MOBILE_SEARCH_REQUEST_KEY)
                ?: json.optJSONObject("req_1")
                ?: return emptyList()
            if (req.optInt("code", -1) != 0) return emptyList()
            val data = req.optJSONObject("data") ?: return emptyList()
            if (data.optInt("code", 0) != 0) return emptyList()
            val songs = data.optJSONObject("body")?.optJSONArray("item_song")
                ?: data.optJSONObject("body")?.optJSONObject("song")?.optJSONArray("list")
                ?: return emptyList()
            removeSearchHighlights(songs)
            return expandSongCandidates(songs)
        }

        internal fun parseDesktopSearchCandidates(rawResponse: String): List<QqSongCandidate> {
            val searchJson = JSONObject(rawResponse)
            val req = searchJson.optJSONObject(SEARCH_MODULE)
                ?: searchJson.optJSONObject("req_1")
                ?: return emptyList()
            if (req.has("code") && req.optInt("code", 0) != 0) return emptyList()
            val songs = req.optJSONObject("data")
                ?.optJSONObject("body")
                ?.optJSONObject("song")
                ?.optJSONArray("list")
                ?: return emptyList()
            removeSearchHighlights(songs)
            return expandSongCandidates(songs)
        }

        private fun expandSongCandidates(songs: JSONArray): List<QqSongCandidate> = buildList {
            for (index in 0 until songs.length()) {
                val track = songs.optJSONObject(index) ?: continue
                if (hasValidTrackIds(track)) {
                    add(QqSongCandidate(track))
                }
                val group = track.optJSONArray("grp") ?: continue
                for (subIndex in 0 until group.length()) {
                    val subTrack = group.optJSONObject(subIndex) ?: continue
                    if (hasValidTrackIds(subTrack)) {
                        add(QqSongCandidate(subTrack))
                    }
                }
            }
        }

        private fun hasValidTrackIds(song: JSONObject): Boolean {
            val id = song.optString("id").ifBlank { song.optString("songid") }
            val mid = song.optString("mid").ifBlank { song.optString("songmid") }
            return id.isNotBlank() && mid.isNotBlank()
        }

        private fun removeSearchHighlights(songs: JSONArray) {
            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                removeObjectHighlights(song)
                song.optJSONObject("album")?.let(::removeObjectHighlights)
                song.optJSONArray("singer")?.let { singers ->
                    for (s in 0 until singers.length()) {
                        singers.optJSONObject(s)?.let(::removeObjectHighlights)
                    }
                }
                song.optJSONArray("grp")?.let(::removeSearchHighlights)
            }
        }

        private fun removeObjectHighlights(item: JSONObject) {
            for (field in listOf("name", "title", "subtitle", "songname", "albumname", "singername")) {
                if (!item.has(field) || item.isNull(field)) continue
                val value = item.opt(field)
                if (value is String && value.contains("em>")) {
                    item.put(field, value.replace("<em>", "").replace("</em>", ""))
                }
            }
        }
    }
}


