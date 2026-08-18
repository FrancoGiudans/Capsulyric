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
 * 与参考实现（忽略行首、把 `(x,y)` 当绝对时间）不同，本实现按真实 QQ 语义换算：
 * 行首时间戳 + 字相对偏移 = 绝对毫秒；行首无时间戳时回退把 `x` 当绝对时间。
 */
internal object QrcParser {

    private val lineHeaderTimeRegex = Regex("""^\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val lineHeaderMsRegex = Regex("""^\[(\d+)]""")
    private val syllableRegex = Regex("""(.*?)\((\d+),(\d+)\)""")

    fun parseQrcLyrics(content: String): List<OnlineLyricFetcher.LyricLine> {
        val lines = content.lines()
            .mapNotNull { parseQrcLine(it) }
            .sortedBy { it.startTime }
        return fillLineEnds(lines)
    }

    fun isQrcContent(content: String): Boolean {
        return Regex("""\((\d+),(\d+)\)""").containsMatchIn(content)
    }

    private fun parseQrcLine(line: String): OnlineLyricFetcher.LyricLine? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null

        // 行首时间戳：优先 [mm:ss.xx]，其次 [ms] 数字形式
        var lineStartMs: Long? = null
        var body = trimmed
        val mmSsMatch = lineHeaderTimeRegex.find(trimmed)
        if (mmSsMatch != null) {
            val minutes = mmSsMatch.groupValues[1].toLong()
            val seconds = mmSsMatch.groupValues[2].toLong()
            val millis = mmSsMatch.groupValues[3].let {
                if (it.isBlank()) 0L
                else if (it.length == 1) it.toLong() * 100
                else if (it.length == 2) it.toLong() * 10
                else it.toLong()
            }
            lineStartMs = minutes * 60_000 + seconds * 1_000 + millis
            body = trimmed.substring(mmSsMatch.range.last + 1)
        } else {
            val msMatch = lineHeaderMsRegex.find(trimmed)
            if (msMatch != null) {
                lineStartMs = msMatch.groupValues[1].toLong()
                body = trimmed.substring(msMatch.range.last + 1)
            }
        }

        val syllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
        val fullText = StringBuilder()
        for (match in syllableRegex.findAll(body)) {
            val text = match.groupValues[1]
            if (text.isBlank()) continue
            val offset = match.groupValues[2].toLong()
            val duration = match.groupValues[3].toLong()
            val absStart = (lineStartMs ?: 0L) + offset
            syllables.add(
                OnlineLyricFetcher.SyllableInfo(
                    startTime = absStart,
                    endTime = absStart + duration,
                    text = text
                )
            )
            fullText.append(text)
        }

        if (syllables.isEmpty()) return null

        return OnlineLyricFetcher.LyricLine(
            startTime = syllables.first().startTime,
            endTime = syllables.last().endTime,
            text = fullText.toString(),
            syllables = syllables
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
