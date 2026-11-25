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
    private val titleCleaner: (String) -> String = CandidateMatcher::cleanTitle
) {
    fun selectBestResult(
        attempts: List<OnlineLyricFetcher.ProviderAttempt>,
        targetTitle: String,
        targetArtist: String,
        providerOrder: List<OnlineLyricProvider>,
        useSmartSelection: Boolean,
        targetAlbum: String = "",
        targetDurationMs: Long = 0L,
        versionReferenceTitle: String = targetTitle
    ): OnlineLyricFetcher.LyricResult? {
        val assessments = IdentityMap()
        attempts.mapNotNull { it.result }.forEach { result ->
            assessments[result] = annotateIdentity(
                result = result,
                targetTitle = targetTitle,
                targetArtist = targetArtist,
                targetAlbum = targetAlbum,
                targetDurationMs = targetDurationMs,
                versionReferenceTitle = versionReferenceTitle
            )
        }
        val usableResults = attempts.mapNotNull { it.result }
            .filter { result ->
                isUsableResult(result) && isIdentityAcceptable(
                    result = result,
                    assessment = assessments[result],
                    targetTitle = targetTitle,
                    targetAlbum = targetAlbum,
                    targetDurationMs = targetDurationMs,
                    versionReferenceTitle = versionReferenceTitle
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
                result = result,
                targetTitle = targetTitle,
                targetArtist = targetArtist,
                targetAlbum = targetAlbum,
                targetDurationMs = targetDurationMs,
                versionReferenceTitle = versionReferenceTitle,
                cachedAssessment = assessments[result]
            )
        }

        return usableResults
            .sortedWith(
                compareByDescending<OnlineLyricFetcher.LyricResult> {
                    assessments[it]?.tier ?: 0
                }
                    .thenByDescending { it.score }
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
     * True when [result] has strong verified identity evidence suitable for
     * race early-completion checks.
     */
    internal fun isPotentiallyMatching(
        result: OnlineLyricFetcher.LyricResult?,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String,
        targetDurationMs: Long,
        versionReferenceTitle: String = targetTitle
    ): Boolean {
        if (!isUsableResult(result)) return false
        val value = result ?: return false
        if (!hasCandidateMetadata(value)) return false
        val assessment = assessCandidate(
            result = value,
            targetTitle = targetTitle,
            targetArtist = targetArtist,
            targetAlbum = targetAlbum,
            targetDurationMs = targetDurationMs,
            versionReferenceTitle = versionReferenceTitle
        )
        if (!assessment.acceptable) return false
        return assessment.score >= MIN_EARLY_IDENTITY_SCORE && assessment.tier >= 2
    }

    internal fun isAcceptableMatch(
        result: OnlineLyricFetcher.LyricResult?,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String,
        targetDurationMs: Long,
        versionReferenceTitle: String = targetTitle
    ): Boolean {
        if (!isUsableResult(result)) return false
        val value = result ?: return false
        if (!hasCandidateMetadata(value)) return false
        return assessCandidate(
            result = value,
            targetTitle = targetTitle,
            targetArtist = targetArtist,
            targetAlbum = targetAlbum,
            targetDurationMs = targetDurationMs,
            versionReferenceTitle = versionReferenceTitle
        ).acceptable
    }

    /**
     * Used by the smart race to decide whether a fast result is already
     * high-confidence enough to start the short grace window. Word-level
     * results qualify immediately once identity is strong.
     */
    internal fun isHighConfidenceSyllableMatch(
        result: OnlineLyricFetcher.LyricResult?,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String,
        targetDurationMs: Long,
        versionReferenceTitle: String = targetTitle
    ): Boolean {
        val value = result ?: return false
        if (!value.hasSyllable) return false
        return isPotentiallyMatching(
            result = value,
            targetTitle = targetTitle,
            targetArtist = targetArtist,
            targetAlbum = targetAlbum,
            targetDurationMs = targetDurationMs,
            versionReferenceTitle = versionReferenceTitle
        )
    }

    /**
     * True when the exact-query batch already found a strong identity match so
     * expensive fallback variants (clean_title / apple_alias) can be skipped.
     */
    internal fun isStrongExactMatch(
        result: OnlineLyricFetcher.LyricResult?,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String,
        targetDurationMs: Long,
        versionReferenceTitle: String = targetTitle
    ): Boolean {
        val value = result ?: return false
        if (!isUsableResult(value) || !hasCandidateMetadata(value)) return false
        val assessment = assessCandidate(
            result = value,
            targetTitle = targetTitle,
            targetArtist = targetArtist,
            targetAlbum = targetAlbum,
            targetDurationMs = targetDurationMs,
            versionReferenceTitle = versionReferenceTitle
        )
        return assessment.acceptable && assessment.tier >= 3
    }

    internal fun buildQualityScore(
        result: OnlineLyricFetcher.LyricResult,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String = "",
        targetDurationMs: Long = 0L,
        versionReferenceTitle: String = targetTitle,
        cachedAssessment: CandidateMatcher.IdentityAssessment? = null
    ): Int {
        var score = 0

        val identity = cachedAssessment ?: annotateIdentity(
            result = result,
            targetTitle = targetTitle,
            targetArtist = targetArtist,
            targetAlbum = targetAlbum,
            targetDurationMs = targetDurationMs,
            versionReferenceTitle = versionReferenceTitle
        )
        score += identity.score
        if (!isUsableResult(result)) return score

        val parsedLines = result.parsedLines.orEmpty()
        val lineCount = parsedLines.size
        val wordLineCount = parsedLines.count { !it.syllables.isNullOrEmpty() }

        if (result.hasSyllable || wordLineCount > 0) {
            // Once track identity is confirmed (tier >= 2), word-by-word timing
            // should decisively outscore plain line-level LRC (+72 + 12 vs +24),
            // while weak substring matches (tier == 1) get a modest bonus so
            // displayed scores stay aligned with tier priority.
            score += if (identity.tier >= 2) 72 else 28
            score += if (identity.tier >= 2) minOf(wordLineCount, 12) else minOf(wordLineCount, 4)
        } else if (lineCount > 0) {
            score += 24
        }

        score += minOf(lineCount, 12)

        score += when (result.provider) {
            OnlineLyricProvider.QQMusic -> 11
            OnlineLyricProvider.Kugou -> 10
            OnlineLyricProvider.SodaMusic -> 11
            OnlineLyricProvider.Lrclib -> 8
            OnlineLyricProvider.LrcApi -> 8
            OnlineLyricProvider.Netease -> 9
            OnlineLyricProvider.AppleMusic -> 11
            OnlineLyricProvider.Musixmatch -> 9
        }

        if (result.lyrics?.contains("纯音乐", ignoreCase = true) == true ||
            result.lyrics?.contains("No lyrics", ignoreCase = true) == true
        ) {
            score -= 100
        }

        return score
    }

    private fun annotateIdentity(
        result: OnlineLyricFetcher.LyricResult,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String,
        targetDurationMs: Long,
        versionReferenceTitle: String
    ): CandidateMatcher.IdentityAssessment {
        if (!hasCandidateMetadata(result)) {
            result.identityScore = 0
            result.identityEvidence = "legacy-no-metadata"
            return CandidateMatcher.IdentityAssessment(
                score = 0,
                titleScore = 0,
                artistScore = 0,
                albumScore = 0,
                durationScore = 0,
                stableBonus = 0,
                versionScore = 0,
                acceptable = isLegacyFallbackAllowed(
                    targetTitle = targetTitle,
                    targetAlbum = targetAlbum,
                    targetDurationMs = targetDurationMs,
                    versionReferenceTitle = versionReferenceTitle
                ),
                evidence = "legacy-no-metadata"
            )
        }

        val assessment = assessCandidate(
            result = result,
            targetTitle = targetTitle,
            targetArtist = targetArtist,
            targetAlbum = targetAlbum,
            targetDurationMs = targetDurationMs,
            versionReferenceTitle = versionReferenceTitle
        )
        result.identityScore = assessment.score
        result.identityEvidence = assessment.evidence
        return assessment
    }

    private fun assessCandidate(
        result: OnlineLyricFetcher.LyricResult,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String,
        targetDurationMs: Long,
        versionReferenceTitle: String
    ): CandidateMatcher.IdentityAssessment {
        // Respect custom titleCleaner if a caller/test injected a non-default cleaner.
        val cleanedTarget = titleCleaner(targetTitle)
        val cleanedMatched = result.matchedTitle?.let(titleCleaner)
        val base = CandidateMatcher.assessIdentity(
            targetTitle = targetTitle,
            targetArtist = targetArtist,
            targetAlbum = targetAlbum,
            targetDurationMs = targetDurationMs,
            matchedTitle = result.matchedTitle,
            matchedArtist = result.matchedArtist,
            matchedAlbum = result.matchedAlbum,
            matchedDurationMs = result.matchedDurationMs,
            versionReferenceTitle = versionReferenceTitle,
            missingTitlePenalty = -8
        )
        if (base.titleScore >= 20 || cleanedTarget.isBlank() || cleanedMatched.isNullOrBlank()) {
            return base
        }
        val normTarget = MatchNormalizer.normalizeCompact(cleanedTarget)
        val normMatched = MatchNormalizer.normalizeCompact(cleanedMatched)
        if (normTarget.isNotBlank() && normTarget == normMatched) {
            return CandidateMatcher.assessIdentity(
                targetTitle = cleanedTarget,
                targetArtist = targetArtist,
                targetAlbum = targetAlbum,
                targetDurationMs = targetDurationMs,
                matchedTitle = cleanedMatched,
                matchedArtist = result.matchedArtist,
                matchedAlbum = result.matchedAlbum,
                matchedDurationMs = result.matchedDurationMs,
                versionReferenceTitle = versionReferenceTitle,
                missingTitlePenalty = -8
            )
        }
        return base
    }

    private fun isIdentityAcceptable(
        result: OnlineLyricFetcher.LyricResult,
        assessment: CandidateMatcher.IdentityAssessment?,
        targetTitle: String,
        targetAlbum: String,
        targetDurationMs: Long,
        versionReferenceTitle: String
    ): Boolean {
        if (!hasCandidateMetadata(result)) {
            return isLegacyFallbackAllowed(
                targetTitle = targetTitle,
                targetAlbum = targetAlbum,
                targetDurationMs = targetDurationMs,
                versionReferenceTitle = versionReferenceTitle
            )
        }
        return assessment?.acceptable == true
    }

    private fun isLegacyFallbackAllowed(
        targetTitle: String,
        targetAlbum: String,
        targetDurationMs: Long,
        versionReferenceTitle: String = targetTitle
    ): Boolean = targetTitle.isNotBlank() &&
        targetAlbum.isBlank() &&
        targetDurationMs <= 0L &&
        VersionTagDetector.detect(versionReferenceTitle, targetAlbum).isEmpty()

    private fun hasCandidateMetadata(result: OnlineLyricFetcher.LyricResult): Boolean = listOf(
        result.matchedTitle,
        result.matchedArtist,
        result.matchedAlbum,
        result.matchedDurationMs?.toString(),
        result.providerTrackId,
        result.isrc
    ).any { !it.isNullOrBlank() }

    private class IdentityMap : java.util.IdentityHashMap<OnlineLyricFetcher.LyricResult, CandidateMatcher.IdentityAssessment>()

    private companion object {
        private const val MIN_EARLY_IDENTITY_SCORE = 50
    }
}
