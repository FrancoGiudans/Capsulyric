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

package com.example.islandlyrics.lyrics.online.selection

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider

internal class OnlineLyricSelector(
    private val titleCleaner: (String) -> String
) {
    fun selectBestResult(
        attempts: List<OnlineLyricFetcher.ProviderAttempt>,
        targetTitle: String,
        targetArtist: String,
        providerOrder: List<OnlineLyricProvider>,
        useSmartSelection: Boolean,
        targetAlbum: String = "",
        targetDurationMs: Long = 0L
    ): OnlineLyricFetcher.LyricResult? {
        val usableResults = attempts.mapNotNull { it.result }
            .filter {
                isUsableResult(it) && isIdentityAcceptable(
                    it,
                    targetTitle,
                    targetArtist,
                    targetAlbum,
                    targetDurationMs
                )
            }

        if (!useSmartSelection) {
            val firstByPriority = providerOrder.firstNotNullOfOrNull { provider ->
                usableResults.firstOrNull { it.provider == provider }
            }
            usableResults.forEach { result ->
                result.score = if (result == firstByPriority) 150 else 0
            }
            return firstByPriority
        }

        val providerPriority = providerOrder.withIndex().associate { it.value to it.index }
        val results = attempts.mapNotNull { it.result }
        for (result in results) {
            result.score = buildQualityScore(
                result,
                targetTitle,
                targetArtist,
                targetAlbum,
                targetDurationMs
            )
        }

        return results
            .filter {
                isUsableResult(it) && isIdentityAcceptable(
                    it,
                    targetTitle,
                    targetArtist,
                    targetAlbum,
                    targetDurationMs
                )
            }
            .sortedWith(
                compareByDescending<OnlineLyricFetcher.LyricResult> { it.score }
                    .thenBy { providerPriority[it.provider] ?: Int.MAX_VALUE }
                    .thenByDescending { it.hasSyllable }
            )
            .firstOrNull()
    }

    internal fun isUsableResult(result: OnlineLyricFetcher.LyricResult?): Boolean {
        if (result == null) return false
        return !result.lyrics.isNullOrBlank() && !result.parsedLines.isNullOrEmpty()
    }

    /**
     * Used by the fetch race to avoid treating any parseable lyric as a
     * trustworthy early result. Providers that cannot expose candidate
     * metadata remain compatible for normal title-based queries, while the
     * cross-language artist-only fallback requires identity evidence.
     */
    internal fun isPotentiallyMatching(
        result: OnlineLyricFetcher.LyricResult?,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String,
        targetDurationMs: Long
    ): Boolean {
        if (!isUsableResult(result)) return false
        val value = result ?: return false
        val hasMetadata = listOf(
            value.matchedTitle,
            value.matchedArtist,
            value.matchedAlbum,
            value.matchedDurationMs?.toString(),
            value.providerTrackId,
            value.isrc
        ).any { !it.isNullOrBlank() }
        // Legacy endpoints without candidate metadata can still be selected
        // after the race completes, but must not trigger early cancellation.
        if (!hasMetadata) return false
        return identityScore(value, targetTitle, targetArtist, targetAlbum, targetDurationMs) >= MIN_IDENTITY_SCORE
    }

    internal fun buildQualityScore(
        result: OnlineLyricFetcher.LyricResult,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String = "",
        targetDurationMs: Long = 0L
    ): Int {
        var score = 0

        val identityScore = identityScore(result, targetTitle, targetArtist, targetAlbum, targetDurationMs)
        score += identityScore

        val parsedLines = result.parsedLines.orEmpty()
        val lineCount = parsedLines.size
        val wordLineCount = parsedLines.count { !it.syllables.isNullOrEmpty() }

        if (result.hasSyllable || wordLineCount > 0) {
            score += 42
        } else if (lineCount > 0) {
            score += 24
        }

        score += minOf(lineCount, 12)
        if (wordLineCount > 0) {
            score += minOf(wordLineCount, 10)
        }

        score += when (result.provider) {
            OnlineLyricProvider.QQMusic -> 11
            OnlineLyricProvider.Kugou -> 10
            OnlineLyricProvider.SodaMusic -> 11
            OnlineLyricProvider.Lrclib -> 8
            OnlineLyricProvider.LrcApi -> 8
            OnlineLyricProvider.Netease -> 9
            else -> 3
        }

        score += scoreTitleMatch(targetTitle, result.matchedTitle)
        score += scoreArtistMatch(targetArtist, result.matchedArtist)

        if (result.lyrics?.contains("纯音乐", ignoreCase = true) == true ||
            result.lyrics?.contains("No lyrics", ignoreCase = true) == true
        ) {
            score -= 100
        }

        return score
    }

    private fun isIdentityAcceptable(
        result: OnlineLyricFetcher.LyricResult,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String,
        targetDurationMs: Long
    ): Boolean {
        val hasMetadata = listOf(
            result.matchedTitle,
            result.matchedArtist,
            result.matchedAlbum,
            result.matchedDurationMs?.toString(),
            result.providerTrackId,
            result.isrc
        ).any { !it.isNullOrBlank() }
        if (!hasMetadata) return targetTitle.isNotBlank()
        return identityScore(result, targetTitle, targetArtist, targetAlbum, targetDurationMs) >= MIN_IDENTITY_SCORE
    }

    private fun identityScore(
        result: OnlineLyricFetcher.LyricResult,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String,
        targetDurationMs: Long
    ): Int {
        var score = scoreTitleMatch(targetTitle, result.matchedTitle)
        score += scoreArtistMatch(targetArtist, result.matchedArtist)
        val albumScore = scoreAlbumMatch(targetAlbum, result.matchedAlbum)
        val durationScore = scoreDurationMatch(targetDurationMs, result.matchedDurationMs)
        score += albumScore
        score += durationScore
        if (albumScore >= 15 && durationScore >= 14) score += 40
        return score
    }

    private fun scoreTitleMatch(targetTitle: String, matchedTitle: String?): Int {
        if (targetTitle.isBlank()) return 0
        if (matchedTitle.isNullOrBlank()) return -8
        if (matchedTitle.equals(targetTitle, ignoreCase = true)) return 36

        val cleanTarget = titleCleaner(targetTitle).lowercase()
        val cleanMatched = titleCleaner(matchedTitle).lowercase()
        return when {
            cleanTarget.isBlank() || cleanMatched.isBlank() -> -8
            cleanMatched == cleanTarget -> 20
            cleanMatched.contains(cleanTarget) || cleanTarget.contains(cleanMatched) -> 8
            else -> -30
        }
    }

    private fun scoreAlbumMatch(targetAlbum: String, matchedAlbum: String?): Int {
        if (targetAlbum.isBlank() || matchedAlbum.isNullOrBlank()) return 0
        val target = titleCleaner(targetAlbum).lowercase()
        val matched = titleCleaner(matchedAlbum).lowercase()
        if (target.isBlank() || matched.isBlank()) return 0
        return when {
            target == matched -> 15
            target.contains(matched) || matched.contains(target) -> 8
            else -> -8
        }
    }

    private fun scoreDurationMatch(targetDurationMs: Long, matchedDurationMs: Long?): Int {
        if (targetDurationMs <= 0L || matchedDurationMs == null || matchedDurationMs <= 0L) return 0
        val delta = kotlin.math.abs(targetDurationMs - matchedDurationMs)
        return when {
            delta <= 1_500L -> 20
            delta <= 3_000L -> 14
            delta <= 8_000L -> 5
            else -> -40
        }
    }

    private companion object {
        private const val MIN_IDENTITY_SCORE = 20
    }

    private fun scoreArtistMatch(targetArtist: String, matchedArtist: String?): Int {
        if (targetArtist.isBlank()) return 0
        if (matchedArtist.isNullOrBlank()) return -4

        val targetTokens = normalizeArtistTokens(targetArtist)
        val matchedTokens = normalizeArtistTokens(matchedArtist)
        if (targetTokens.isEmpty() || matchedTokens.isEmpty()) return -4

        val overlap = targetTokens.intersect(matchedTokens).size
        return when {
            overlap == 0 -> -18
            overlap == targetTokens.size && overlap == matchedTokens.size -> 18
            overlap == targetTokens.size || overlap == matchedTokens.size -> 12
            else -> 6
        }
    }

    private fun normalizeArtistTokens(artist: String): Set<String> {
        return artist
            .split("/", "&", ",", "、", " feat. ", " ft. ", " x ", " X ", ";")
            .map { it.trim().lowercase() }
            .map { it.replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .toSet()
    }
}


