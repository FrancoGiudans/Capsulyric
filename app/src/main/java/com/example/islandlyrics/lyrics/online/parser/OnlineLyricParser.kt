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

package com.example.islandlyrics.lyrics.online.parser

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher

import kotlin.text.RegexOption.DOT_MATCHES_ALL

internal object OnlineLyricParser {
    fun parseWordLevelLyrics(content: String): List<OnlineLyricFetcher.LyricLine> {
        val trimmed = content.trimStart()
        return when {
            // 酷狗 KRC（<x,y,z> 音节标记）优先于 QRC 判定：酷狗内容可能带 [kana:]/(x,y) 注音行，
            // 但主歌词是 <x,y,z> 形态，不能误走 QQ 的 (x,y) 解析器（否则会把 <x,y,z> 当原文文本）。
            hasKrcSyllableTokens(trimmed) -> parseBracketWordLyrics(content)
            trimmed.startsWith("<tt") -> parseTtmlLyrics(content)
            QrcParser.isQrcContent(trimmed) -> QrcParser.parseQrcLyrics(content)
            else -> parseBracketWordLyrics(content)
        }
    }

    private fun hasKrcSyllableTokens(content: String): Boolean {
        return Regex("""<(\d+),(\d+),(\d+)>""").containsMatchIn(content)
    }

    fun isWordLevelLyrics(content: String, lyricTypeHint: String? = null): Boolean {
        val trimmed = content.trimStart()
        if (trimmed.startsWith("<tt")) return true
        if (lyricTypeHint?.contains("word", ignoreCase = true) == true) return true
        if (lyricTypeHint?.contains("syllable", ignoreCase = true) == true) return true
        if (QrcParser.isQrcContent(trimmed)) return true

        val bracketWordRegex = Regex("""(?m)^\[\d+(?:,\d+)?]\s*(?:<\d+,\d+,\d+>[^<\r\n]*)+""")
        return bracketWordRegex.containsMatchIn(content)
    }

    /** 检测 QQ 逐行/逐字 `[startMs,durMs]text` 段格式（无需字级 `(x,y)` 标记）。 */
    internal fun hasQqLineSegments(content: String): Boolean {
        return Regex("""\[\d+,\d+][^[]*""").containsMatchIn(content)
    }

    /** QQ QRC 逐字歌词入口。 */
    fun parseQrcLyrics(content: String): List<OnlineLyricFetcher.LyricLine> =
        QrcParser.parseQrcLyrics(content)

    /** 网易 YRC 逐字歌词入口。 */
    fun parseYrcLyrics(content: String): List<OnlineLyricFetcher.LyricLine> =
        YrcParser.parseYrcLyrics(content)

    /** Musixmatch richsync 逐字歌词入口。 */
    fun parseMusixmatchRichsync(content: String): List<OnlineLyricFetcher.LyricLine> =
        MusixmatchRichsyncParser.parseRichsync(content)

    fun parseKrcLyrics(krcContent: String): List<OnlineLyricFetcher.LyricLine> {
        val lines = mutableListOf<OnlineLyricFetcher.LyricLine>()
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

    fun parseLrcLyrics(lrcContent: String): List<OnlineLyricFetcher.LyricLine> {
        val lines = mutableListOf<OnlineLyricFetcher.LyricLine>()
        val lrcLines = lrcContent.lines()

        val timedLines = mutableListOf<Pair<Long, String>>()
        val timeRegex = "\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]".toRegex()

        for (line in lrcLines) {
            val matches = timeRegex.findAll(line).toList()
            if (matches.isNotEmpty()) {
                var text = line.replace(timeRegex, "").trim()
                while (text.startsWith("[")) {
                    val close = text.indexOf("]")
                    if (close == -1) break
                    val inside = text.substring(1, close)
                    if (inside.startsWith("ti:") || inside.startsWith("ar:") || inside.startsWith("al:") || inside.startsWith("by:") || inside.startsWith("offset:")) {
                        text = text.substring(close + 1).trim()
                    } else break
                }
                for (match in matches) {
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toLong()
                    val millis = match.groupValues[3].let {
                        if (it.length == 2) it.toLong() * 10 else it.toLong()
                    }
                    val totalMs = minutes * 60000 + seconds * 1000 + millis
                    timedLines.add(Pair(totalMs, text))
                }
            } else {
                // QQ 行级逐字：[start,duration]text，同一物理行可能含多段（包括元数据挤在一行的情况）
                val qqLineRegex = Regex("""\[(\d+)(?:,(\d+))?]([^\[]*)""")
                var matchedAny = false
                for (m in qqLineRegex.findAll(line)) {
                    val startMs = m.groupValues[1].toLongOrNull() ?: continue
                    var segText = m.groupValues[3].trim()
                    if (segText.isBlank()) continue
                    while (segText.startsWith("[")) {
                        val c2 = segText.indexOf("]")
                        if (c2 == -1) break
                        val ins2 = segText.substring(1, c2)
                        if (ins2.startsWith("ti:") || ins2.startsWith("ar:") || ins2.startsWith("al:") || ins2.startsWith("by:") || ins2.startsWith("offset:")) {
                            segText = segText.substring(c2 + 1).trim()
                        } else break
                    }
                    if (segText.isEmpty()) continue
                    timedLines.add(Pair(startMs, segText))
                    matchedAny = true
                }
                if (!matchedAny) {
                    val trimmed = line.trim()
                    val lastOpen = trimmed.lastIndexOf("[")
                    val lastClose = trimmed.lastIndexOf("]")
                    if (lastOpen != -1 && lastClose > lastOpen) {
                        val insideLast = trimmed.substring(lastOpen + 1, lastClose)
                        if ("," in insideLast) {
                            val partsLast = insideLast.split(",")
                            if (partsLast.size == 2) {
                                val sLast = partsLast[0].trim().toLongOrNull()
                                var txtLast = trimmed.substring(lastClose + 1).trim()
                                while (txtLast.startsWith("[")) {
                                    val c2 = txtLast.indexOf("]")
                                    if (c2 == -1) break
                                    val ins2 = txtLast.substring(1, c2)
                                    if (ins2.startsWith("ti:") || ins2.startsWith("ar:") || ins2.startsWith("al:") || ins2.startsWith("by:") || ins2.startsWith("offset:")) {
                                        txtLast = txtLast.substring(c2 + 1).trim()
                                    } else break
                                }
                                if (sLast != null && txtLast.isNotEmpty()) {
                                    timedLines.add(Pair(sLast, txtLast))
                                }
                            }
                        }
                    }
                }
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
            lines.add(OnlineLyricFetcher.LyricLine(startTime, endTime, text, null))
        }

        return lines
    }

    fun parseSodaLyrics(
        lyricContent: String,
        lyricType: String
    ): List<OnlineLyricFetcher.LyricLine> {
        val trimmed = lyricContent.trimStart()
        return when {
            trimmed.startsWith("<tt") -> parseTtmlLyrics(lyricContent)
            isWordLevelLyrics(lyricContent, lyricType) -> parseWordLevelLyrics(lyricContent)
            hasLrcTimestamps(lyricContent) -> parseLrcLyrics(lyricContent)
            lyricType.contains("lrc", ignoreCase = true) -> parseLrcLyrics(lyricContent)
            else -> emptyList()
        }
    }

    private fun parseBracketWordLyrics(content: String): List<OnlineLyricFetcher.LyricLine> {
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

    private fun parseBracketWordLine(line: String): OnlineLyricFetcher.LyricLine? {
        val headerMatch = Regex("""^\[(\d+)(?:,(\d+))?]""").find(line) ?: return null
        val lineStartTime = headerMatch.groupValues[1].toLongOrNull() ?: return null
        val explicitDuration = headerMatch.groupValues.getOrNull(2)?.toLongOrNull()
        val contentPart = line.substring(headerMatch.range.last + 1)

        val syllableRegex = Regex("""<(\d+),(\d+),(\d+)>([^<]*)""")
        val syllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
        val fullText = StringBuilder()

        for (match in syllableRegex.findAll(contentPart)) {
            val offset = match.groupValues[1].toLong()
            val duration = match.groupValues[2].toLong()
            val text = match.groupValues[4]
            if (text.isBlank()) continue

            val absStartTime = lineStartTime + offset
            val absEndTime = absStartTime + duration
            syllables.add(OnlineLyricFetcher.SyllableInfo(absStartTime, absEndTime, text))
            fullText.append(text)
        }

        if (syllables.isEmpty()) return null

        val lineEndTime = explicitDuration?.let { lineStartTime + it }
            ?: syllables.maxOf { it.endTime }

        return OnlineLyricFetcher.LyricLine(
            startTime = lineStartTime,
            endTime = lineEndTime,
            text = fullText.toString(),
            syllables = syllables
        )
    }

    private fun parseKrcLine(line: String): OnlineLyricFetcher.LyricLine? {
        return try {
            val lineHeaderRegex = Regex("^\\[(\\d+),(\\d+)\\]")
            val headerMatch = lineHeaderRegex.find(line) ?: return null

            val lineStartTime = headerMatch.groupValues[1].toLong()
            val lineDuration = headerMatch.groupValues[2].toLong()
            val lineEndTime = lineStartTime + lineDuration

            val contentPart = line.substring(headerMatch.range.last + 1)

            val syllableRegex = Regex("<(\\d+),(\\d+),(\\d+)>([^<]*)")
            val matches = syllableRegex.findAll(contentPart)

            val syllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
            val fullText = StringBuilder()

            for (match in matches) {
                val offset = match.groupValues[1].toLong()
                val duration = match.groupValues[2].toLong()
                val text = match.groupValues[4]

                val absStartTime = lineStartTime + offset
                val absEndTime = absStartTime + duration

                syllables.add(OnlineLyricFetcher.SyllableInfo(absStartTime, absEndTime, text))
                fullText.append(text)
            }

            if (syllables.isNotEmpty()) {
                OnlineLyricFetcher.LyricLine(lineStartTime, lineEndTime, fullText.toString(), syllables)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun hasLrcTimestamps(content: String): Boolean {
        return Regex("""\[\d{2}:\d{2}\.\d{2,3}]""").containsMatchIn(content)
    }

    internal fun parseTtmlLyrics(ttmlContent: String): List<OnlineLyricFetcher.LyricLine> {
        val lines = mutableListOf<OnlineLyricFetcher.LyricLine>()
        val lineRegex = Regex("<p\\b([^>]*)>(.*?)</p>", DOT_MATCHES_ALL)
        val spanRegex = Regex("<span\\b([^>]*)>(.*?)</span>", DOT_MATCHES_ALL)

        for (lineMatch in lineRegex.findAll(ttmlContent)) {
            val lineAttributes = parseXmlAttributes(lineMatch.groupValues[1])
            val lineBody = lineMatch.groupValues[2]
            val lineStart = parseFlexibleTimeToMs(lineAttributes["begin"]) ?: continue
            val lineEnd = parseFlexibleTimeToMs(lineAttributes["end"])

            val syllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
            // 先收集 span 原始数据（begin/end/text），end 缺失时用行 end 或下一 span begin 兜底
            val spanData = mutableListOf<Triple<Long, Long?, String>>()
            for (spanMatch in spanRegex.findAll(lineBody)) {
                val spanAttributes = parseXmlAttributes(spanMatch.groupValues[1])
                val start = parseFlexibleTimeToMs(spanAttributes["begin"]) ?: continue
                val end = parseFlexibleTimeToMs(spanAttributes["end"])
                val text = decodeXmlText(stripXmlTags(spanMatch.groupValues[2])).trim()
                if (text.isBlank()) continue
                spanData.add(Triple(start, end, text))
            }
            for ((index, span) in spanData.withIndex()) {
                val (start, end, text) = span
                val effectiveEnd = end
                    ?: spanData.getOrNull(index + 1)?.first
                    ?: lineEnd
                    ?: (start + 500)
                syllables.add(OnlineLyricFetcher.SyllableInfo(start, effectiveEnd, text))
            }

            val text = decodeXmlText(stripXmlTags(lineBody))
                .replace(Regex("\\s+"), " ")
                .trim()
            if (text.isBlank()) continue

            lines.add(
                OnlineLyricFetcher.LyricLine(
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
}


