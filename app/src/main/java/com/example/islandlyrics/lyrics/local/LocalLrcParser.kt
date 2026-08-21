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

package com.example.islandlyrics.lyrics.local

import android.content.Context
import android.net.Uri
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import java.io.InputStream
import java.nio.charset.Charset

object LocalLrcParser {

    data class LrcMetadata(
        val title: String?,
        val artist: String?,
        val album: String?,
        val by: String?
    )

    data class ParseResult(
        val lines: List<OnlineLyricFetcher.LyricLine>,
        val hasSyllable: Boolean,
        val translationLines: Map<Long, String>,
        val romanLines: Map<Long, String>
    )

    fun extractMetadata(context: Context, uri: Uri): LrcMetadata? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(2048)
                val read = stream.read(buffer)
                if (read > 0) buffer.copyOf(read) else null
            } ?: return null
            val header = decodeWithFallback(bytes)
            parseMetadataFromHeader(header)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMetadataFromHeader(header: String): LrcMetadata {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var by: String? = null

        for (line in header.lines()) {
            val match = METADATA_EXTRACT_REGEX.find(line) ?: continue
            val tag = match.groupValues[1].lowercase()
            val value = match.groupValues[2].trim()
            if (value.isBlank()) continue
            when (tag) {
                "ti" -> title = value
                "ar" -> artist = value
                "al" -> album = value
                "by" -> by = value
            }
            if (title != null && artist != null) break
        }
        return LrcMetadata(title, artist, album, by)
    }

    fun parse(context: Context, uri: Uri): ParseResult? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            val content = decodeWithFallback(bytes)
            parseContent(content)
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Failed to parse local LRC: ${e.message}")
            null
        }
    }

    fun parseContent(content: String): ParseResult? {
        if (content.isBlank()) return null

        val lines = content.lines()
        val mainLines = mutableListOf<Pair<Long, String>>()
        val esLyricLines = mutableListOf<OnlineLyricFetcher.LyricLine>()
        val translationLines = mutableMapOf<Long, String>()
        val romanLines = mutableMapOf<Long, String>()
        var hasEsLyric = false

        for (line in lines) {
            if (line.isBlank()) continue
            // 纯元数据行（整行即 [ti:] 等）直接跳过；但若行内同时含 QQ 段则不跳过，交给后续 QQ 正则处理
            if (isMetadataTag(line)) continue

            val timestamps = extractTimestamps(line)
            if (timestamps.isNotEmpty()) {
                val textPart = line.replace(TIMESTAMP_REGEX, "").trim()
                if (textPart.isBlank()) continue

                if (hasEsLyricTokens(textPart)) {
                    hasEsLyric = true
                    for (ts in timestamps) {
                        parseEsLyricLine(ts, textPart)?.let { esLyricLines.add(it) }
                    }
                } else {
                    for (ts in timestamps) {
                        mainLines.add(ts to textPart)
                    }
                }
                continue
            }

            // 标准 LRC 时间戳未命中时，尝试解析 QQ 行级格式 [start,duration]text（也兼容 [start] 单参）
            // 该格式常见于 QQ 音乐 fcg_query_lyric_new 返回的逐行歌词，需与 Local 文件下载场景兼容
            val qqRegex = Regex("""\[(\d+)(?:,(\d+))?]([^\[]*)""")
            var qqMatched = false
            for (m in qqRegex.findAll(line)) {
                val startMs = m.groupValues[1].toLongOrNull() ?: continue
                var segText = m.groupValues[3].trim()
                if (segText.isBlank()) continue
                // 防御性：段文本若以元数据开头则清理
                while (segText.startsWith("[")) {
                    val c2 = segText.indexOf("]")
                    if (c2 == -1) break
                    val ins2 = segText.substring(1, c2)
                    if (ins2.startsWith("ti:") || ins2.startsWith("ar:") || ins2.startsWith("al:") || ins2.startsWith("by:") || ins2.startsWith("offset:")) {
                        segText = segText.substring(c2 + 1).trim()
                    } else break
                }
                if (segText.isEmpty()) continue
                // QQ 行若含 <start,dur> 音节标记，则按逐字处理
                if (hasEsLyricTokens(segText)) {
                    hasEsLyric = true
                    parseEsLyricLine(startMs, segText)?.let { esLyricLines.add(it) }
                } else {
                    mainLines.add(startMs to segText)
                }
                qqMatched = true
            }
            if (qqMatched) continue
        }

        if (hasEsLyric && esLyricLines.isNotEmpty()) {
            val sorted = esLyricLines.sortedBy { it.startTime }
            val withEndTimes = sorted.mapIndexed { index, lyricLine ->
                if (lyricLine.endTime <= lyricLine.startTime) {
                    val nextStart = sorted.getOrNull(index + 1)?.startTime
                    lyricLine.copy(endTime = nextStart ?: (lyricLine.startTime + 5000))
                } else {
                    lyricLine
                }
            }
            return ParseResult(withEndTimes, true, translationLines, romanLines)
        }

        if (mainLines.isEmpty()) return null

        mainLines.sortBy { it.first }
        val parsed = mainLines.mapIndexed { index, (startTime, text) ->
            val endTime = if (index < mainLines.size - 1) {
                mainLines[index + 1].first
            } else {
                startTime + 5000
            }
            OnlineLyricFetcher.LyricLine(startTime, endTime, text, null)
        }

        return ParseResult(parsed, false, translationLines, romanLines)
    }

    private fun parseEsLyricLine(lineStartTime: Long, textPart: String): OnlineLyricFetcher.LyricLine? {
        val syllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
        val fullText = StringBuilder()

        for (match in ES_LYRIC_TOKEN_REGEX.findAll(textPart)) {
            val offset = match.groupValues[1].toLongOrNull() ?: continue
            val duration = match.groupValues[2].toLongOrNull() ?: continue
            val text = match.groupValues[3]
            if (text.isBlank()) continue

            val absStart = lineStartTime + offset
            val absEnd = absStart + duration
            syllables.add(OnlineLyricFetcher.SyllableInfo(absStart, absEnd, text))
            fullText.append(text)
        }

        if (syllables.isEmpty()) {
            return OnlineLyricFetcher.LyricLine(lineStartTime, lineStartTime, textPart, null)
        }

        val lineEnd = syllables.maxOf { it.endTime }
        return OnlineLyricFetcher.LyricLine(lineStartTime, lineEnd, fullText.toString(), syllables)
    }

    private fun extractTimestamps(line: String): List<Long> {
        return TIMESTAMP_REGEX.findAll(line).mapNotNull { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val fraction = match.groupValues[3]
            val millis = when (fraction.length) {
                2 -> fraction.toLongOrNull()?.times(10L) ?: 0L
                3 -> fraction.toLongOrNull() ?: 0L
                else -> fraction.toLongOrNull()?.times(10L) ?: 0L
            }
            minutes * 60000L + seconds * 1000L + millis
        }.toList()
    }

    private fun hasEsLyricTokens(text: String): Boolean {
        return ES_LYRIC_TOKEN_REGEX.containsMatchIn(text)
    }

    private fun isMetadataTag(line: String): Boolean {
        return METADATA_TAG_REGEX.matches(line)
    }

    private fun decodeWithFallback(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('�')) return utf8

        return try {
            String(bytes, Charset.forName("GBK"))
        } catch (_: Exception) {
            utf8
        }
    }

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})\.(\d{2,3})]""")
    private val ES_LYRIC_TOKEN_REGEX = Regex("""<(\d+),(\d+)>([^<]*)""")
    private val METADATA_TAG_REGEX = Regex("""^\[[a-zA-Z]+:.*]$""")
    private val METADATA_EXTRACT_REGEX = Regex("""^\[([a-zA-Z]+):(.*)]$""")
    private const val TAG = "LocalLrcParser"
}
