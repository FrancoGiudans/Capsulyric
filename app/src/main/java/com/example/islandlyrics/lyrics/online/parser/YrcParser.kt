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
 * YRC（网易云逐字歌词）解析器。
 *
 * 输入为网易 `eapi/song/lyric/v1` 响应中 `yrc.lyric` 字段。歌词主体每行形态：
 * `[lineStartMs,lineDurMs]<wordStartMs,wordDurMs>字<...>字...`
 *
 * 时间语义：`<a,b>` 的 `a` 为相对行首毫秒偏移，`b` 为持续毫秒；
 * 行首 `[start,dur]` 为绝对毫秒。逐字粒度 = 字符（卡拉OK 级）。
 * 首尾 `{...}` 信息行（作词/图片 credit）按参考实现跳过，不参与时间轴。
 */
internal object YrcParser {

    private val lineHeaderRegex = Regex("""^\[(\d+),(\d+)]""")
    private val wordRegex = Regex("""<(\d+),(\d+)>([^<]*)""")

    /** 判断内容是否为 YRC 形态（行首 [ms,ms] + 至少一个 <ms,ms>字 字标签）。 */
    fun isYrcContent(content: String): Boolean {
        val trimmed = content.trimStart()
        if (trimmed.isBlank()) return false
        val headerMatch = lineHeaderRegex.find(trimmed)
            ?: return false
        val body = trimmed.substring(headerMatch.range.last + 1)
        return wordRegex.containsMatchIn(body)
    }

    fun parseYrcLyrics(content: String): List<OnlineLyricFetcher.LyricLine> {
        val lines = content.lines()
            .mapNotNull { parseYrcLine(it) }
            .sortedBy { it.startTime }
        return fillLineEnds(lines)
    }

    private fun parseYrcLine(line: String): OnlineLyricFetcher.LyricLine? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null
        // 信息行（{...} JSON）跳过
        if (trimmed.startsWith("{")) return null

        val headerMatch = lineHeaderRegex.find(trimmed)
        val lineStartMs = headerMatch?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val body = trimmed.substring(headerMatch.range.last + 1)

        val syllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
        val fullText = StringBuilder()
        for (match in wordRegex.findAll(body)) {
            val wordStartMs = match.groupValues[1].toLong()
            val wordDurMs = match.groupValues[2].toLong()
            val text = match.groupValues[3]
            if (text.isBlank()) continue

            val absStart = lineStartMs + wordStartMs
            syllables.add(
                OnlineLyricFetcher.SyllableInfo(
                    startTime = absStart,
                    endTime = absStart + wordDurMs,
                    text = text
                )
            )
            fullText.append(text)
        }

        if (syllables.isEmpty()) return null

        return OnlineLyricFetcher.LyricLine(
            startTime = lineStartMs,
            endTime = lineStartMs + (headerMatch.groupValues[2].toLongOrNull() ?: 0L),
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
