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
import com.example.islandlyrics.lyrics.online.provider.AppleMusicIsrc
import com.example.islandlyrics.lyrics.online.selection.CandidateMatcher
import com.example.islandlyrics.lyrics.online.selection.MatchNormalizer
import com.example.islandlyrics.rules.ParserRule
import kotlin.math.abs

internal object OnlineLyricSidecarMerger {

    /** 附加歌词类型：翻译 / 拼音 */
    enum class SidecarKind { TRANSLATION, ROMANIZATION }

    fun withSidecars(
        result: OnlineLyricFetcher.LyricResult,
        rule: ParserRule,
        filterInfoLines: Boolean = false
    ): List<OnlineLyricFetcher.LyricLine> {
        val lines = result.parsedLines.orEmpty()
        if (lines.isEmpty()) return emptyList()
        val sidecars = result.buildSidecars(rule, filterInfoLines)
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
     * 严格校验候选曲目身份（无版本冲突、时长兼容、标题与歌手匹配），
     * 宁缺毋滥——绝不把不匹配的 Cover/Live/错歌翻译或罗马音兜底嫁接到主歌词。
     */
    fun selectBestSidecarSource(
        attempts: List<OnlineLyricFetcher.ProviderAttempt>,
        preferredMain: OnlineLyricFetcher.LyricResult?,
        kind: SidecarKind,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String = "",
        targetDurationMs: Long = 0L
    ): OnlineLyricFetcher.LyricResult? {
        val candidates = attempts.mapNotNull { it.result }
            .filter { it.error == null && !sidecarContent(it, kind).isNullOrBlank() }
        if (candidates.isEmpty()) return null

        val refDurationMs = targetDurationMs.takeIf { it > 0L }
            ?: preferredMain?.matchedDurationMs?.takeIf { it > 0L }
            ?: 0L

        val matching = candidates.filter { candidate ->
            candidate == preferredMain || isSidecarIdentityCompatible(
                candidate = candidate,
                preferredMain = preferredMain,
                targetTitle = targetTitle,
                targetArtist = targetArtist,
                targetAlbum = targetAlbum,
                referenceDurationMs = refDurationMs
            )
        }
        if (matching.isEmpty()) return null

        val preferredIsrc = AppleMusicIsrc.normalize(preferredMain?.isrc)
        return matching.sortedWith(
            compareByDescending<OnlineLyricFetcher.LyricResult> { it == preferredMain }
                .thenByDescending {
                    preferredIsrc != null && AppleMusicIsrc.normalize(it.isrc) == preferredIsrc
                }
                .thenBy { candidate ->
                    val dur = candidate.matchedDurationMs
                    if (refDurationMs > 0L && dur != null && dur > 0L) {
                        abs(refDurationMs - dur)
                    } else {
                        Long.MAX_VALUE / 2
                    }
                }
                .thenByDescending { it.identityScore }
        ).firstOrNull()
    }

    private fun sidecarContent(
        result: OnlineLyricFetcher.LyricResult,
        kind: SidecarKind
    ): String? = when (kind) {
        SidecarKind.TRANSLATION -> result.translationLyrics
        SidecarKind.ROMANIZATION -> result.romanLyrics
    }

    /**
     * Checks whether a cross-provider sidecar candidate refers to the same
     * recording as the target track / selected main lyric.
     */
    private fun isSidecarIdentityCompatible(
        candidate: OnlineLyricFetcher.LyricResult,
        preferredMain: OnlineLyricFetcher.LyricResult?,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String,
        referenceDurationMs: Long
    ): Boolean {
        val matchedTitle = candidate.matchedTitle?.trim().orEmpty()
        val matchedArtist = candidate.matchedArtist?.trim().orEmpty()
        val matchedAlbum = candidate.matchedAlbum
        val matchedDurationMs = candidate.matchedDurationMs

        // Reject legacy no-metadata sidecars from cross-provider merging.
        if (matchedTitle.isBlank() && matchedArtist.isBlank() && candidate.isrc.isNullOrBlank()) {
            return false
        }

        // Reject any hard version conflict (e.g. Cover / Live / Remix / Karaoke).
        if (CandidateMatcher.hasVersionConflict(targetTitle, targetAlbum, matchedTitle, matchedAlbum)) {
            return false
        }
        if (preferredMain != null && !preferredMain.matchedTitle.isNullOrBlank() &&
            CandidateMatcher.hasVersionConflict(
                preferredMain.matchedTitle,
                preferredMain.matchedAlbum.orEmpty(),
                matchedTitle,
                matchedAlbum
            )
        ) {
            return false
        }

        // Require tight duration alignment when both sides expose duration, so
        // different arrangements / TV size / extended versions cannot cross-pollinate.
        if (referenceDurationMs > 0L && matchedDurationMs != null && matchedDurationMs > 0L) {
            if (abs(referenceDurationMs - matchedDurationMs) > SIDECAR_MAX_DURATION_DELTA_MS) {
                return false
            }
        }

        // Same canonical ISRC is definitive proof of the same recording.
        val preferredIsrc = AppleMusicIsrc.normalize(preferredMain?.isrc)
        val candidateIsrc = AppleMusicIsrc.normalize(candidate.isrc)
        if (preferredIsrc != null && candidateIsrc != null && preferredIsrc == candidateIsrc) {
            return true
        }

        // Title must match either the playback target or the accepted main lyric
        // (supporting cross-language alias matches where main and sidecar share
        // the localized title).
        val titleTargets = listOfNotNull(
            targetTitle.takeIf { it.isNotBlank() },
            preferredMain?.matchedTitle?.takeIf { it.isNotBlank() }
        )
        if (titleTargets.isNotEmpty()) {
            if (matchedTitle.isBlank()) return false
            val titleMatched = titleTargets.any { ref ->
                CandidateMatcher.scoreTitleMatch(ref, matchedTitle) >= 8
            }
            if (!titleMatched) return false
        }

        // Artist must overlap with either the playback target or the accepted
        // main lyric's artist; an unrelated cover artist must never pass.
        val artistTargets = listOfNotNull(
            targetArtist.takeIf { it.isNotBlank() },
            preferredMain?.matchedArtist?.takeIf { it.isNotBlank() }
        )
        if (artistTargets.isNotEmpty()) {
            if (matchedArtist.isBlank()) return false
            val artistMatched = artistTargets.any { ref ->
                MatchNormalizer.computeArtistOverlap(ref, matchedArtist).hasOverlap
            }
            if (!artistMatched) return false
        }

        return true
    }

    /**
     * Merges translation/romanization sidecars requested by [rule] into [main].
     */
    fun mergeSidecarsFromAttempts(
        main: OnlineLyricFetcher.LyricResult,
        attempts: List<OnlineLyricFetcher.ProviderAttempt>,
        rule: ParserRule,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String = "",
        targetDurationMs: Long = 0L
    ): OnlineLyricFetcher.LyricResult {
        val translation = if (rule.receiveOnlineTranslation) {
            selectBestSidecarSource(
                attempts = attempts,
                preferredMain = main,
                kind = SidecarKind.TRANSLATION,
                targetTitle = targetTitle,
                targetArtist = targetArtist,
                targetAlbum = targetAlbum,
                targetDurationMs = targetDurationMs
            )?.translationLyrics
        } else {
            null
        }
        val roman = if (rule.receiveOnlineRomanization) {
            selectBestSidecarSource(
                attempts = attempts,
                preferredMain = main,
                kind = SidecarKind.ROMANIZATION,
                targetTitle = targetTitle,
                targetArtist = targetArtist,
                targetAlbum = targetAlbum,
                targetDurationMs = targetDurationMs
            )?.romanLyrics
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

    private fun OnlineLyricFetcher.LyricResult.buildSidecars(
        rule: ParserRule,
        filterInfoLines: Boolean
    ): SidecarTimeline {
        val translationByTime = if (rule.receiveOnlineTranslation) {
            translationLyrics?.let { parseSidecarLrc(it, filterInfoLines) }.orEmpty()
        } else {
            emptyMap()
        }
        val romanByTime = if (rule.receiveOnlineRomanization) {
            romanLyrics?.let { parseSidecarLrc(it, filterInfoLines) }.orEmpty()
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

    private fun parseSidecarLrc(content: String, filterInfoLines: Boolean): Map<Long, String> {
        val lrcLines = parseLrcTimestampLines(content, filterInfoLines)
        if (lrcLines.isNotEmpty()) return lrcLines

        return parseQrcTimestampLines(content, filterInfoLines)
    }

    private fun parseLrcTimestampLines(content: String, filterInfoLines: Boolean): Map<Long, String> {
        val timestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")
        return content.lineSequence()
            .mapNotNull { rawLine ->
                val matches = timestampRegex.findAll(rawLine).toList()
                if (matches.isEmpty()) return@mapNotNull null
                val text = rawLine.replace(timestampRegex, "").trim()
                if (text.isBlank()) return@mapNotNull null
                if (filterInfoLines && OnlineLyricInfoFilter.isSidecarNoiseLine(text)) return@mapNotNull null
                matches.map { match -> match.toMillis() to text }
            }
            .flatten()
            .toMap()
    }

    private fun parseQrcTimestampLines(content: String, filterInfoLines: Boolean): Map<Long, String> {
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
            if (text.isBlank()) {
                null
            } else if (filterInfoLines && OnlineLyricInfoFilter.isSidecarNoiseLine(text)) {
                null
            } else {
                startTime to text
            }
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
    private const val SIDECAR_MAX_DURATION_DELTA_MS = 5_000L
}
