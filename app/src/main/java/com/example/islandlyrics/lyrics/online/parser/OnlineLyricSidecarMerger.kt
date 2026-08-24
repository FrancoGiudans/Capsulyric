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

import com.example.islandlyrics.rules.ParserRule
import kotlin.math.abs

internal object OnlineLyricSidecarMerger {

    /** 附加歌词类型：翻译 / 拼音 */
    enum class SidecarKind { TRANSLATION, ROMANIZATION }

    fun withSidecars(
        result: OnlineLyricFetcher.LyricResult,
        rule: ParserRule
    ): List<OnlineLyricFetcher.LyricLine> {
        val lines = result.parsedLines.orEmpty()
        if (lines.isEmpty()) return emptyList()
        val sidecars = result.buildSidecars(rule)
        if (sidecars.translationByTime.isEmpty() && sidecars.romanByTime.isEmpty()) return lines

        return lines.map { line ->
            line.copy(
                translation = sidecars.translationByTime.closestText(line.startTime) ?: line.translation,
                roma = sidecars.romanByTime.closestText(line.startTime) ?: line.roma
            )
        }
    }

    /**
     * 从所有携带 [kind] 附加歌词的 provider 结果中选择来源。
     *
     * 选择标准只要求 sidecar 匹配当前播放信息（标题/歌手）即可，不做行数、
     * 来源等质量评分——翻译/拼音歌词本身不是逐字的，有就是有、没有就是没有。
     * 匹配候选优先；主歌词来源在候选内优先（时间轴天然对齐）。
     */
    fun selectBestSidecarSource(
        attempts: List<OnlineLyricFetcher.ProviderAttempt>,
        preferredMain: OnlineLyricFetcher.LyricResult?,
        kind: SidecarKind,
        targetTitle: String,
        targetArtist: String
    ): OnlineLyricFetcher.LyricResult? {
        val candidates = attempts.mapNotNull { it.result }
            .filter { !sidecarContent(it, kind).isNullOrBlank() }
        if (candidates.isEmpty()) return null

        val matching = candidates.filter { matchesQuery(it, targetTitle, targetArtist) }
        val pool = if (matching.isNotEmpty()) matching else candidates
        return pool.maxWithOrNull(compareBy { it == preferredMain })
    }

    private fun sidecarContent(
        result: OnlineLyricFetcher.LyricResult,
        kind: SidecarKind
    ): String? = when (kind) {
        SidecarKind.TRANSLATION -> result.translationLyrics
        SidecarKind.ROMANIZATION -> result.romanLyrics
    }

    /** sidecar 内容是否对应当前播放的歌曲：标题匹配（忽略大小写/包含关系），歌手有 token 交集。 */
    private fun matchesQuery(
        result: OnlineLyricFetcher.LyricResult,
        targetTitle: String,
        targetArtist: String
    ): Boolean {
        val matchedTitle = result.matchedTitle?.trim().orEmpty()
        if (targetTitle.isNotBlank() && matchedTitle.isNotBlank()) {
            val titleMatches = matchedTitle.equals(targetTitle, ignoreCase = true) ||
                matchedTitle.contains(targetTitle, ignoreCase = true) ||
                targetTitle.contains(matchedTitle, ignoreCase = true)
            if (!titleMatches) return false
        }
        if (targetArtist.isNotBlank()) {
            val matchedArtist = result.matchedArtist?.trim().orEmpty()
            if (matchedArtist.isNotBlank() && !artistTokensOverlap(targetArtist, matchedArtist)) {
                return false
            }
        }
        return true
    }

    private fun artistTokensOverlap(left: String, right: String): Boolean {
        val tokensLeft = left.split("/", "&", ",", "、", "x", ";", "feat", "ft")
            .map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val tokensRight = right.split("/", "&", ",", "、", "x", ";", "feat", "ft")
            .map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        if (tokensLeft.isEmpty() || tokensRight.isEmpty()) return true
        return tokensLeft.intersect(tokensRight).isNotEmpty()
    }

    /**
     * Merges translation/romanization sidecars requested by [rule] into [main].
     *
     * 翻译与拼音各自从所有 provider 尝试中按"匹配当前播放信息"选取来源
     * （见 [selectBestSidecarSource]），不局限于主歌词来源自身。
     */
    fun mergeSidecarsFromAttempts(
        main: OnlineLyricFetcher.LyricResult,
        attempts: List<OnlineLyricFetcher.ProviderAttempt>,
        rule: ParserRule,
        targetTitle: String,
        targetArtist: String
    ): OnlineLyricFetcher.LyricResult {
        val translation = if (rule.receiveOnlineTranslation) {
            selectBestSidecarSource(attempts, main, SidecarKind.TRANSLATION, targetTitle, targetArtist)?.translationLyrics
        } else {
            null
        }
        val roman = if (rule.receiveOnlineRomanization) {
            selectBestSidecarSource(attempts, main, SidecarKind.ROMANIZATION, targetTitle, targetArtist)?.romanLyrics
        } else {
            null
        }
        if (translation == main.translationLyrics && roman == main.romanLyrics) return main
        return main.copy(translationLyrics = translation, romanLyrics = roman)
    }

    private data class SidecarTimeline(
        val translationByTime: Map<Long, String>,
        val romanByTime: Map<Long, String>
    )

    private fun OnlineLyricFetcher.LyricResult.buildSidecars(rule: ParserRule): SidecarTimeline {
        val translationByTime = if (rule.receiveOnlineTranslation) {
            translationLyrics?.let { parseSidecarLrc(it) }.orEmpty()
        } else {
            emptyMap()
        }
        val romanByTime = if (rule.receiveOnlineRomanization) {
            romanLyrics?.let { parseSidecarLrc(it) }.orEmpty()
        } else {
            emptyMap()
        }
        return SidecarTimeline(translationByTime, romanByTime)
    }

    private fun Map<Long, String>.closestText(time: Long): String? {
        if (isEmpty()) return null
        this[time]?.let { return it.takeIf(String::isNotBlank) }
        return entries
            .minByOrNull { abs(it.key - time) }
            ?.takeIf { abs(it.key - time) <= SIDECAR_TIME_TOLERANCE_MS }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseSidecarLrc(content: String): Map<Long, String> {
        val lrcLines = parseLrcTimestampLines(content)
        if (lrcLines.isNotEmpty()) return lrcLines

        return parseQrcTimestampLines(content)
    }

    private fun parseLrcTimestampLines(content: String): Map<Long, String> {
        val timestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")
        return content.lineSequence()
            .mapNotNull { rawLine ->
                val matches = timestampRegex.findAll(rawLine).toList()
                if (matches.isEmpty()) return@mapNotNull null
                val text = rawLine.replace(timestampRegex, "").trim()
                if (text.isBlank()) return@mapNotNull null
                matches.map { match -> match.toMillis() to text }
            }
            .flatten()
            .toMap()
    }

    private fun parseQrcTimestampLines(content: String): Map<Long, String> {
        val lineHeaderRegex = Regex("""\[(\d+),(\d+)]""")
        val wordTokenRegex = Regex("""(?:<|\()\d+,\d+(?:,\d+)?(?:>|\))""")
        val headers = lineHeaderRegex.findAll(content).toList()
        if (headers.isEmpty()) return emptyMap()

        return headers.mapIndexedNotNull { index, match ->
            val startTime = match.groupValues[1].toLongOrNull() ?: return@mapIndexedNotNull null
            val segmentStart = match.range.last + 1
            val segmentEnd = headers.getOrNull(index + 1)?.range?.first ?: content.length
            if (segmentStart >= segmentEnd) return@mapIndexedNotNull null

            val text = content.substring(segmentStart, segmentEnd)
                .replace(wordTokenRegex, "")
                .replace(Regex("""\[[^\u005D]+\]"""), "")
                .trim()
            if (text.isBlank()) null else startTime to text
        }.toMap()
    }

    private fun MatchResult.toMillis(): Long {
        val minutes = groupValues[1].toLongOrNull() ?: 0L
        val seconds = groupValues[2].toLongOrNull() ?: 0L
        val fraction = groupValues.getOrNull(3).orEmpty()
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.times(100L) ?: 0L
            2 -> fraction.toLongOrNull()?.times(10L) ?: 0L
            else -> fraction.take(3).toLongOrNull() ?: 0L
        }
        return minutes * 60_000L + seconds * 1000L + millis
    }

    private const val SIDECAR_TIME_TOLERANCE_MS = 1500L
}


