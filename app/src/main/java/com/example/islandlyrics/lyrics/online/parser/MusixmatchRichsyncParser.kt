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
import org.json.JSONArray
import org.json.JSONObject

/**
 * Musixmatch richsync（逐字歌词）解析器。
 *
 * 输入为 `macro.subtitles.get` 响应 `message.body.macro_calls["track.richsync.get"]
 * .message.body.richsync.richsync_body`（JSON 数组字符串）。
 *
 * 数据形态：每行 `{ ts, te, l: [{ c: chars, o: position }], x }`；
 * - `ts/te` 为秒制行开始/结束
 * - `o` 为词在行内的相对秒偏移
 * - 逐字粒度 = 词段（空格独立成段），需把独立空格段并入前段
 */
internal object MusixmatchRichsyncParser {

    fun parseRichsync(richsyncBody: String): List<OnlineLyricFetcher.LyricLine> {
        if (richsyncBody.isBlank()) return emptyList()
        val array = runCatching { JSONArray(richsyncBody) }.getOrNull() ?: return emptyList()

        val lines = mutableListOf<OnlineLyricFetcher.LyricLine>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            parseRichsyncedLine(obj)?.let { lines.add(it) }
        }
        return lines.sortedBy { it.startTime }
    }

    private fun parseRichsyncedLine(obj: JSONObject): OnlineLyricFetcher.LyricLine? {
        val ts = obj.optDouble("ts", -1.0)
        val te = obj.optDouble("te", -1.0)
        val words = obj.optJSONArray("l") ?: return null
        if (ts < 0 || words.length() == 0) return null

        val lineStartMs = (ts * 1000).toLong()
        val lineEndMs = if (te >= ts) (te * 1000).toLong() else lineStartMs

        // 词段：c=文本, o=相对行内秒
        val segments = mutableListOf<Pair<String, Long>>() // (text, startMs)
        for (w in 0 until words.length()) {
            val word = words.optJSONObject(w) ?: continue
            val text = word.optString("c", "")
            val offsetSec = word.optDouble("o", 0.0)
            segments.add(text to (lineStartMs + (offsetSec * 1000).toLong()))
        }
        if (segments.isEmpty()) return null

        // 空格段并入前段（StandardizeMusixmatchLyrics 等价行为）
        val merged = mutableListOf<Pair<String, Long>>()
        for ((text, start) in segments) {
            if (text == " " && merged.isNotEmpty()) {
                val (prevText, prevStart) = merged.removeAt(merged.size - 1)
                merged.add((prevText + text) to prevStart)
            } else {
                merged.add(text to start)
            }
        }

        val syllables = merged.mapIndexed { index, (text, startMs) ->
            val endMs = if (index < merged.size - 1) {
                merged[index + 1].second
            } else {
                lineEndMs.coerceAtLeast(startMs + 1)
            }
            OnlineLyricFetcher.SyllableInfo(
                startTime = startMs,
                endTime = endMs,
                text = text
            )
        }

        return OnlineLyricFetcher.LyricLine(
            startTime = lineStartMs,
            endTime = lineEndMs,
            text = obj.optString("x").ifBlank { syllables.joinToString("") { it.text } },
            syllables = syllables
        )
    }
}
