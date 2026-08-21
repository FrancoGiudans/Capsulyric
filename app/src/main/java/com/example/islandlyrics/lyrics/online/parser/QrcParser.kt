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

/**
 * QRC（QQ 音乐逐字歌词）解析器。
 *
 * 输入为 QQ `lyric_download.fcg` 的 `content` 字段解密后的明文，每行形态：
 * `[mm:ss.xx]字(相对行首毫秒,持续毫秒)字(...)...`，或行首为 `[startMs,durMs]` 数字形式，
 * 或仅元数据 `[ti:]/[ar:]/[offset:]` 无时间戳。
 *
 * QQ QRC 的行首为 `[startMs,durMs]` 时，行内 `(x,y)` 为绝对毫秒（x 即该字的起始时间），
 * 解析时仅剥离行首标记、不叠加行首时间；`[mm:ss.xx]` 或单参 `[startMs]` 行首仍按
 * 行首时间戳 + 字相对偏移换算。
 */
internal object QrcParser {

    private val lineHeaderTimeRegex = Regex("""^\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val segmentRegex = Regex("""\[(\d+)(?:,(\d+))?]""")
    private val syllableRegex = Regex("""(.*?)\((\d+),(\d+)\)""")

    fun parseQrcLyrics(content: String): List<OnlineLyricFetcher.LyricLine> {
        val lines = content.lines()
            .flatMap { parseQrcLine(it) }
            .sortedBy { it.startTime }
        return fillLineEnds(lines)
    }

    fun isQrcContent(content: String): Boolean {
        return Regex("""\((\d+),(\d+)\)""").containsMatchIn(content)
    }

    private fun parseQrcLine(line: String): List<OnlineLyricFetcher.LyricLine> {
        var trimmed = line.trim()
        if (trimmed.isBlank()) return emptyList()
        // 剥离行首连续元数据标签（[ti:]/[ar:]/[offset:]/[kana:] 等）：
        // 纯元数据行剥离后为空则跳过；若为单行合并形态（如 Html 折叠换行后的
        // "[ti:...][ar:...][0,3946]字(0,171)... [3947,2230]..."）则继续解析歌词段。
        while (trimmed.startsWith("[")) {
            val close = trimmed.indexOf(']')
            if (close == -1) break
            val inside = trimmed.substring(1, close)
            if (inside.firstOrNull()?.isLetter() == true && inside.contains(':')) {
                trimmed = trimmed.substring(close + 1).trim()
            } else {
                break
            }
        }
        if (trimmed.isBlank()) return emptyList()

        // [startMs,durMs] 段（可多个，兼容单行合并形态）；[kana:] 等注音内容不含段标记，
        // 剥离后即被上面的空判断过滤，不会产生注音垃圾行。
        val headers = segmentRegex.findAll(trimmed).toList()
        if (headers.isNotEmpty()) {
            val results = mutableListOf<OnlineLyricFetcher.LyricLine>()
            for ((index, headerMatch) in headers.withIndex()) {
                val start = headerMatch.groupValues[1].toLongOrNull() ?: continue
                val duration = headerMatch.groupValues[2].toLongOrNull()
                val segmentStart = headerMatch.range.last + 1
                val segmentEnd = headers.getOrNull(index + 1)?.range?.first ?: trimmed.length
                val body = trimmed.substring(segmentStart, segmentEnd).trim()
                val syllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
                val fullText = StringBuilder()
                for (match in syllableRegex.findAll(body)) {
                    val text = match.groupValues[1]
                    if (text.isBlank()) continue
                    val offset = match.groupValues[2].toLong()
                    val durationMs = match.groupValues[3].toLong()
                    // [startMs,durMs] 形态下行内 (x,y) 为绝对毫秒，不叠加行首；
                    // 裸 [startMs] 仍按相对偏移叠加，行为不变。
                    val absStart = if (duration != null) offset else (start + offset)
                    syllables.add(
                        OnlineLyricFetcher.SyllableInfo(
                            startTime = absStart,
                            endTime = absStart + durationMs,
                            text = text
                        )
                    )
                    fullText.append(text)
                }
                if (syllables.isNotEmpty()) {
                    results.add(
                        OnlineLyricFetcher.LyricLine(
                            startTime = start,
                            endTime = syllables.last().endTime,
                            text = fullText.toString(),
                            syllables = syllables
                        )
                    )
                } else if (body.isNotBlank()) {
                    // 无字级标记（逐行形态）：整段文本作为一句
                    val end = duration?.let { start + it } ?: (start + 5000)
                    results.add(OnlineLyricFetcher.LyricLine(start, end, body, null))
                }
            }
            if (results.isNotEmpty()) return results
        }

        // 兼容旧形态：仅 [mm:ss.xx] 行首 + 字相对偏移（[startMs]/[startMs,durMs] 已在上方段循环处理）
        val mmSsMatch = lineHeaderTimeRegex.find(trimmed) ?: return emptyList()
        val minutes = mmSsMatch.groupValues[1].toLong()
        val seconds = mmSsMatch.groupValues[2].toLong()
        val millis = mmSsMatch.groupValues[3].let {
            if (it.isBlank()) 0L
            else if (it.length == 1) it.toLong() * 100
            else if (it.length == 2) it.toLong() * 10
            else it.toLong()
        }
        val lineStartMs = minutes * 60_000 + seconds * 1_000 + millis
        val body = trimmed.substring(mmSsMatch.range.last + 1)

        val syllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
        val fullText = StringBuilder()
        for (match in syllableRegex.findAll(body)) {
            val text = match.groupValues[1]
            if (text.isBlank()) continue
            val offset = match.groupValues[2].toLong()
            val duration = match.groupValues[3].toLong()
            val absStart = lineStartMs + offset
            syllables.add(
                OnlineLyricFetcher.SyllableInfo(
                    startTime = absStart,
                    endTime = absStart + duration,
                    text = text
                )
            )
            fullText.append(text)
        }

        if (syllables.isEmpty()) {
            val text = body.trim()
            if (text.isBlank()) return emptyList()
            return listOf(OnlineLyricFetcher.LyricLine(lineStartMs, lineStartMs + 5000, text, null))
        }

        return listOf(
            OnlineLyricFetcher.LyricLine(
                startTime = syllables.first().startTime,
                endTime = syllables.last().endTime,
                text = fullText.toString(),
                syllables = syllables
            )
        )
    }

    private fun fillLineEnds(
        lines: List<OnlineLyricFetcher.LyricLine>
    ): List<OnlineLyricFetcher.LyricLine> {
        return lines.mapIndexed { index, line ->
            val nextStart = lines.getOrNull(index + 1)?.startTime
            if (nextStart != null && line.endTime <= line.startTime) {
                line.copy(endTime = nextStart)
            } else {
                line
            }
        }
    }
}
