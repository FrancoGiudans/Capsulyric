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

/**
 * 搜索候选（多候选挑选的公共接口）。
 *
 * provider 搜索接口返回多个候选曲目时，实现本接口交给 [CandidateMatcher] 挑选
 * 与当前播放信息最匹配的一条，替代原来"直接取第 0 条"的简化行为。
 */
internal interface SearchCandidate {
    /** 候选曲目标题 */
    val matchedTitle: String
    /** 候选曲目歌手 */
    val matchedArtist: String
    val matchedAlbum: String?
        get() = null
    val matchedDurationMs: Long?
        get() = null
    val providerTrackId: String?
        get() = null
    val isrc: String?
        get() = null
}

/**
 * 多候选挑选器（混合式搜索架构的第一层）。
 *
 * 在 provider 内部对搜索返回的多个候选做轻量身份匹配，选出最匹配的一条
 * 再取歌词；全部不匹配时返回 null，让上层继续扩展查询或等待人工确认。
 */
internal object CandidateMatcher {

    internal data class ScoredCandidate<T : SearchCandidate>(
        val candidate: T,
        val score: Int
    )

    internal data class IdentityAssessment(
        val score: Int,
        val titleScore: Int,
        val artistScore: Int,
        val albumScore: Int,
        val durationScore: Int,
        val stableBonus: Int,
        val versionScore: Int,
        val acceptable: Boolean,
        val evidence: String,
        val missingTargetVersion: Boolean = false
    ) {
        /**
         * Identity confidence tier used to prevent a weak substring match or an
         * unlabeled studio track (when the target explicitly requests Live /
         * Acoustic / Remix) with word-by-word timing from outranking a true
         * version-matched track.
         */
        val tier: Int
            get() = when {
                missingTargetVersion -> 1
                titleScore >= 20 && artistScore >= 6 -> 3
                titleScore >= 20 || stableBonus > 0 -> 2
                else -> 1
            }
    }

    /**
     * Evaluates candidate identity using one shared rule set across both
     * provider candidate selection and cross-provider final selection.
     */
    fun assessIdentity(
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String = "",
        targetDurationMs: Long = 0L,
        matchedTitle: String?,
        matchedArtist: String?,
        matchedAlbum: String? = null,
        matchedDurationMs: Long? = null,
        versionReferenceTitle: String = targetTitle,
        missingTitlePenalty: Int = -30
    ): IdentityAssessment {
        if (durationConflict(targetDurationMs, matchedDurationMs)) {
            return IdentityAssessment(
                score = -100,
                titleScore = 0,
                artistScore = 0,
                albumScore = 0,
                durationScore = -40,
                stableBonus = 0,
                versionScore = 0,
                acceptable = false,
                evidence = "duration-conflict"
            )
        }

        val targetVersionTags = VersionTagDetector.detect(versionReferenceTitle, targetAlbum)
        val candidateVersionTags = VersionTagDetector.detect(matchedTitle, matchedAlbum)
        if (hasHardVersionConflict(targetVersionTags, candidateVersionTags)) {
            return IdentityAssessment(
                score = -100,
                titleScore = 0,
                artistScore = 0,
                albumScore = 0,
                durationScore = 0,
                stableBonus = 0,
                versionScore = -30,
                acceptable = false,
                evidence = "version-conflict"
            )
        }

        val titleScore = scoreTitleMatch(targetTitle, matchedTitle, missingTitlePenalty)
        val artistScore = scoreArtistMatch(targetArtist, matchedArtist)
        val rawAlbumScore = scoreAlbumMatch(targetAlbum, matchedAlbum)
        val durationScore = scoreDurationMatch(targetDurationMs, matchedDurationMs)
        val versionScore = versionCompatibilityScore(targetVersionTags, candidateVersionTags)
        val missingTargetVersion = (targetVersionTags - candidateVersionTags).isNotEmpty()

        val candidateTitleText = matchedTitle.orEmpty()
        val candidateArtistText = matchedArtist.orEmpty()
        val crossScriptTitle = targetTitle.isNotBlank() &&
            candidateTitleText.isNotBlank() &&
            MatchNormalizer.hasScriptDifference(targetTitle, candidateTitleText)
        val crossScriptArtist = targetArtist.isNotBlank() &&
            candidateArtistText.isNotBlank() &&
            MatchNormalizer.hasScriptDifference(targetArtist, candidateArtistText)
        val albumSameAsTitle = targetAlbum.isNotBlank() &&
            targetTitle.isNotBlank() &&
            MatchNormalizer.normalizeCompact(MatchNormalizer.cleanTitle(targetAlbum)) ==
            MatchNormalizer.normalizeCompact(MatchNormalizer.cleanTitle(targetTitle))

        // When title + artist + version tags already match and duration closely agrees (<= 3.5s),
        // both candidates represent the SAME master recording regardless of whether
        // one comes from a single, compilation, OST, or full album. Keep album as a
        // tiny tie-breaker (+4/+2/0) instead of letting album metadata (+15 + 40 vs -8)
        // penalize a valid single-edition word-by-word lyric below a plain LRC!
        val sameRecordingByDuration =
            !missingTargetVersion &&
                titleScore >= 20 &&
                (artistScore >= 6 || targetArtist.isBlank()) &&
                durationScore >= 14
        val albumScore = if (sameRecordingByDuration) {
            when {
                rawAlbumScore >= 15 -> 4
                rawAlbumScore > 0 -> 2
                else -> 0
            }
        } else {
            rawAlbumScore
        }

        // Exact album + near-exact duration bridges localized titles/artists ONLY
        // when title or artist could not match directly; never stack +40 onto
        // already-matched tracks just for sharing an album name.
        val needsCrossLanguageBridge = titleScore < 20 || artistScore < 0
        val allowTitleBridge = titleScore >= 0 || crossScriptTitle
        val allowArtistBridge = artistScore >= 0 ||
            (crossScriptTitle && !albumSameAsTitle) ||
            (crossScriptArtist && !albumSameAsTitle && titleScore >= 20)
        val stableBonus = if (
            needsCrossLanguageBridge &&
            rawAlbumScore >= 15 &&
            durationScore >= 14 &&
            allowTitleBridge &&
            allowArtistBridge
        ) {
            40
        } else {
            0
        }

        val score = titleScore + artistScore + albumScore + durationScore + stableBonus + versionScore

        val rejectedSameScriptTitleMismatch =
            targetTitle.isNotBlank() &&
                titleScore < 0 &&
                !crossScriptTitle
        // Guard against unlabeled same-title cover versions whose duration
        // happens to match the original backing track. Allow cross-script
        // localized artist names (e.g. "周深" vs "Zhou Shen", "米津玄师" vs
        // "Kenshi Yonezu") when title is an exact/clean match and duration
        // closely agrees, without letting a same-language cover artist through.
        val allowLocalizedArtistWithExactTrack =
            crossScriptArtist &&
                titleScore >= 20 &&
                durationScore >= 14
        val rejectedUnrelatedArtist =
            targetArtist.isNotBlank() &&
                candidateArtistText.isNotBlank() &&
                artistScore < 0 &&
                stableBonus == 0 &&
                !allowLocalizedArtistWithExactTrack
        // When searching without a title (artist_only cross-language fallback),
        // require strong album + near-exact duration + artist match so another
        // song by the same artist with a similar length cannot slip through.
        val rejectedWeakTitlelessMatch =
            targetTitle.isBlank() &&
                (artistScore < 6 || albumScore < 15 || durationScore < 14)

        val acceptable = !rejectedSameScriptTitleMismatch &&
            !rejectedUnrelatedArtist &&
            !rejectedWeakTitlelessMatch &&
            score >= MIN_ACCEPT_SCORE

        val evidence = buildString {
            append("title=").append(titleScore)
            append(",artist=").append(artistScore)
            append(",album=").append(albumScore)
            append(",duration=").append(durationScore)
            append(",stable=").append(stableBonus)
            if (versionScore != 0) append(",version=").append(versionScore)
            if (!acceptable) {
                when {
                    rejectedSameScriptTitleMismatch -> append(",reject=title-mismatch")
                    rejectedUnrelatedArtist -> append(",reject=artist-mismatch")
                    rejectedWeakTitlelessMatch -> append(",reject=weak-titleless")
                    else -> append(",reject=below-threshold")
                }
            }
        }
        return IdentityAssessment(
            score = score,
            titleScore = titleScore,
            artistScore = artistScore,
            albumScore = albumScore,
            durationScore = durationScore,
            stableBonus = stableBonus,
            versionScore = versionScore,
            acceptable = acceptable,
            evidence = evidence,
            missingTargetVersion = missingTargetVersion
        )
    }

    /**
     * 从候选列表中挑选与 (title, artist, album, duration) 最匹配的一条。
     */
    fun <T : SearchCandidate> pickBest(
        candidates: List<T>,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        versionReferenceTitle: String = title
    ): T? {
        return pickTopCandidates(
            candidates = candidates,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            limit = 1,
            versionReferenceTitle = versionReferenceTitle
        )
            .firstOrNull()
    }

    /**
     * Returns up to [limit] acceptable candidates in descending identity score
     * order so providers can gracefully fall back when the top track has no
     * lyrics or is region-locked.
     */
    fun <T : SearchCandidate> pickTopCandidates(
        candidates: List<T>,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        limit: Int = 3,
        versionReferenceTitle: String = title
    ): List<T> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()
        return candidates.mapNotNull { candidate ->
            val assessment = assessIdentity(
                targetTitle = title,
                targetArtist = artist,
                targetAlbum = album,
                targetDurationMs = durationMs,
                matchedTitle = candidate.matchedTitle,
                matchedArtist = candidate.matchedArtist,
                matchedAlbum = candidate.matchedAlbum,
                matchedDurationMs = candidate.matchedDurationMs,
                versionReferenceTitle = versionReferenceTitle
            )
            if (!assessment.acceptable) return@mapNotNull null
            ScoredCandidate(candidate, assessment.score)
        }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.candidate }
    }

    /**
     * Returns candidates in descending identity score order. The resolver uses
     * this instead of merging independent storefront searches and taking the
     * first non-empty ISRC.
     */
    fun <T : SearchCandidate> rank(
        candidates: List<T>,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        versionReferenceTitle: String = title
    ): List<ScoredCandidate<T>> {
        if (candidates.isEmpty()) return emptyList()

        val scored = mutableListOf<ScoredCandidate<T>>()
        val targetVersionTags = VersionTagDetector.detect(versionReferenceTitle, album)
        for (candidate in candidates) {
            if (durationConflict(durationMs, candidate.matchedDurationMs)) continue
            val candidateVersionTags = VersionTagDetector.detect(candidate.matchedTitle, candidate.matchedAlbum)
            if (hasHardVersionConflict(targetVersionTags, candidateVersionTags)) continue
            val assessment = assessIdentity(
                targetTitle = title,
                targetArtist = artist,
                targetAlbum = album,
                targetDurationMs = durationMs,
                matchedTitle = candidate.matchedTitle,
                matchedArtist = candidate.matchedArtist,
                matchedAlbum = candidate.matchedAlbum,
                matchedDurationMs = candidate.matchedDurationMs,
                versionReferenceTitle = versionReferenceTitle
            )
            scored += ScoredCandidate(candidate, assessment.score)
        }
        return scored.sortedByDescending { it.score }
    }

    /**
     * Search fallback must have a clear winner. A direct song-id lookup does
     * not use this method because the platform already supplied the identity.
     */
    fun <T : SearchCandidate> pickBestWithMargin(
        candidates: List<T>,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        minimumMargin: Int = 12,
        versionReferenceTitle: String = title
    ): T? {
        val ranked = rank(candidates, title, artist, album, durationMs, versionReferenceTitle)
        val best = ranked.firstOrNull() ?: return null
        val bestAssessment = assessIdentity(
            targetTitle = title,
            targetArtist = artist,
            targetAlbum = album,
            targetDurationMs = durationMs,
            matchedTitle = best.candidate.matchedTitle,
            matchedArtist = best.candidate.matchedArtist,
            matchedAlbum = best.candidate.matchedAlbum,
            matchedDurationMs = best.candidate.matchedDurationMs,
            versionReferenceTitle = versionReferenceTitle
        )
        if (!bestAssessment.acceptable) return null

        val runnerUp = ranked.drop(1).firstOrNull {
            !hasSameStableIdentity(best.candidate, it.candidate)
        }
        if (runnerUp != null && best.score - runnerUp.score < minimumMargin) return null
        return best.candidate
    }

    fun hasSameStableIdentity(first: SearchCandidate, second: SearchCandidate): Boolean {
        val firstIsrc = normalizeStableId(first.isrc)
        val secondIsrc = normalizeStableId(second.isrc)
        if (firstIsrc.isNotBlank() && firstIsrc == secondIsrc) return true

        val firstTrackId = first.providerTrackId?.trim().orEmpty()
        val secondTrackId = second.providerTrackId?.trim().orEmpty()
        if (firstTrackId.isNotBlank() && firstTrackId == secondTrackId) return true

        // Same title + same artist + near-identical duration (e.g. single vs album edition)
        val sameTitle = MatchNormalizer.normalizeCompact(first.matchedTitle)
            .let { it.isNotBlank() && it == MatchNormalizer.normalizeCompact(second.matchedTitle) }
        val sameArtist = MatchNormalizer.computeArtistOverlap(first.matchedArtist, second.matchedArtist).isExactAll
        val sameDuration = first.matchedDurationMs != null && second.matchedDurationMs != null &&
            kotlin.math.abs(first.matchedDurationMs!! - second.matchedDurationMs!!) <= 1_500L
        return sameTitle && sameArtist && sameDuration
    }

    private fun normalizeStableId(value: String?): String = value.orEmpty()
        .filter(Char::isLetterOrDigit)
        .uppercase()

    fun scoreTitleMatch(
        targetTitle: String,
        matchedTitle: String?,
        missingPenalty: Int = -30
    ): Int {
        if (targetTitle.isBlank()) return 0
        if (matchedTitle.isNullOrBlank()) return missingPenalty

        val normTarget = MatchNormalizer.normalizeText(targetTitle)
        val normMatched = MatchNormalizer.normalizeText(matchedTitle)
        if (normTarget.isNotBlank() && normMatched == normTarget) return 36

        val compactTarget = MatchNormalizer.normalizeCompact(targetTitle)
        val compactMatched = MatchNormalizer.normalizeCompact(matchedTitle)
        if (compactTarget.isNotBlank() && compactMatched == compactTarget) return 36

        val cleanTarget = MatchNormalizer.normalizeCompact(cleanTitle(targetTitle))
        val cleanMatched = MatchNormalizer.normalizeCompact(cleanTitle(matchedTitle))
        if (cleanTarget.isBlank() || cleanMatched.isBlank()) return missingPenalty
        if (cleanMatched == cleanTarget) return 30
        if (JapaneseRomajiMatcher.areEquivalent(cleanTarget, cleanMatched)) return 24

        // Bilingual title equivalence (e.g. "无中生有 Ex Nihilo" vs "无中生有")
        val targetPrimary = MatchNormalizer.extractBilingualPrimaryTitle(targetTitle)
            ?.let(MatchNormalizer::normalizeCompact)
        val matchedPrimary = MatchNormalizer.extractBilingualPrimaryTitle(matchedTitle)
            ?.let(MatchNormalizer::normalizeCompact)
        if ((targetPrimary != null && targetPrimary == cleanMatched) ||
            (matchedPrimary != null && matchedPrimary == cleanTarget) ||
            (targetPrimary != null && targetPrimary == matchedPrimary)
        ) {
            return 28
        }

        return if (isSafeTitleContainment(cleanTarget, cleanMatched)) 8 else -30
    }

    fun scoreAlbumMatch(targetAlbum: String, matchedAlbum: String?): Int {
        if (targetAlbum.isBlank() || matchedAlbum.isNullOrBlank()) return 0
        val cleanTarget = MatchNormalizer.normalizeCompact(cleanTitle(targetAlbum))
        val cleanMatched = MatchNormalizer.normalizeCompact(cleanTitle(matchedAlbum))
        return when {
            cleanTarget.isBlank() || cleanMatched.isBlank() -> 0
            cleanTarget == cleanMatched -> 15
            isSafeTitleContainment(cleanTarget, cleanMatched) -> 8
            else -> -8
        }
    }

    fun scoreDurationMatch(targetDurationMs: Long, matchedDurationMs: Long?): Int {
        if (targetDurationMs <= 0L || matchedDurationMs == null || matchedDurationMs <= 0L) return 0
        val delta = kotlin.math.abs(targetDurationMs - matchedDurationMs)
        return when {
            // QQ/Kugou expose whole-second durations; 2.0s tolerance prevents
            // millisecond truncation from penalizing identical recordings.
            delta <= 2_000L -> 20
            delta <= 3_500L -> 14
            delta <= 8_000L -> 5
            else -> -40
        }
    }

    private fun durationConflict(targetDurationMs: Long, matchedDurationMs: Long?): Boolean {
        if (targetDurationMs <= 0L || matchedDurationMs == null || matchedDurationMs <= 0L) return false
        return kotlin.math.abs(targetDurationMs - matchedDurationMs) > 12_000L
    }

    fun isDurationCompatible(targetDurationMs: Long, matchedDurationMs: Long?): Boolean =
        !durationConflict(targetDurationMs, matchedDurationMs)

    fun hasVersionConflict(
        targetTitle: String,
        targetAlbum: String,
        matchedTitle: String,
        matchedAlbum: String?
    ): Boolean = hasHardVersionConflict(
        VersionTagDetector.detect(targetTitle, targetAlbum),
        VersionTagDetector.detect(matchedTitle, matchedAlbum)
    )

    private fun hasHardVersionConflict(
        targetTags: Set<VersionTag>,
        candidateTags: Set<VersionTag>
    ): Boolean {
        // Bidirectional hard tags: neither side may drop or introduce these
        // distinct recordings (e.g. THE FIRST TAKE live re-recordings often
        // share the exact total duration of the studio track, such as 残響散歌 184s,
        // yet have completely different intro pauses and vocal phrasing).
        val bidirectionalHardTags = setOf(
            VersionTag.THE_FIRST_TAKE,
            VersionTag.INSTRUMENTAL,
            VersionTag.KARAOKE
        )
        if (bidirectionalHardTags.any { tag -> (tag in targetTags) != (tag in candidateTags) }) {
            return true
        }

        val candidateOnlyHardTags = setOf(
            VersionTag.LIVE,
            VersionTag.REMIX,
            VersionTag.COVER,
            VersionTag.RADIO_EDIT
        )
        return candidateTags.any { it in candidateOnlyHardTags && it !in targetTags }
    }

    private fun versionCompatibilityScore(
        targetTags: Set<VersionTag>,
        candidateTags: Set<VersionTag>
    ): Int {
        if (targetTags == candidateTags) return if (targetTags.isEmpty()) 0 else 12
        if (targetTags.isEmpty() && candidateTags.isEmpty()) return 0
        if ((targetTags - candidateTags).isNotEmpty()) {
            // Target explicitly requested a version label (e.g. Live / Acoustic / Remix)
            // that the candidate lacks. Heavily penalize so version-matched lyrics always win.
            return if (targetTags.intersect(candidateTags).isNotEmpty()) -8 else -20
        }
        if (targetTags.intersect(candidateTags).isNotEmpty()) return 5
        return -18
    }

    fun scoreArtistMatch(targetArtist: String, matchedArtist: String?): Int {
        if (targetArtist.isBlank()) return 0
        if (matchedArtist.isNullOrBlank()) return -4

        val overlap = MatchNormalizer.computeArtistOverlap(targetArtist, matchedArtist)
        if (overlap.targetTotal == 0 || overlap.candidateTotal == 0) return -4
        return when {
            !overlap.hasOverlap -> -18
            overlap.isExactAll -> 18
            overlap.isSubsetMatch -> 12
            else -> 6
        }
    }

    fun normalizeArtistTokens(artist: String): Set<String> =
        MatchNormalizer.normalizeArtistTokens(artist)

    /**
     * 清理标题：移除括号内容与常见后缀，供标题匹配与清理搜索使用。
     */
    fun cleanTitle(title: String): String =
        MatchNormalizer.cleanTitle(title)

    private fun isSafeTitleContainment(left: String, right: String): Boolean {
        val (shorter, longer) = if (left.length <= right.length) left to right else right to left
        val shorterNoSpace = shorter.replace(" ", "")
        if (shorterNoSpace.length < 2) return false
        val isPureLatin = shorterNoSpace.all { it in 'a'..'z' || it in '0'..'9' }
        if (isPureLatin && shorterNoSpace.length < 3) return false
        if (!longer.contains(shorter)) return false

        // For Latin substrings, require word boundaries so "love" never matches "beloved".
        if (isPureLatin) {
            val boundaryRegex = Regex("""(?<![a-z0-9])${Regex.escape(shorter)}(?![a-z0-9])""")
            if (!boundaryRegex.containsMatchIn(longer)) return false
            val longerNoSpace = longer.replace(" ", "")
            return shorterNoSpace.length * 100 >= longerNoSpace.length * 30
        }
        // CJK characters are much denser than Latin translations in bilingual titles
        // (e.g. "无中生有" inside "无中生有 Ex Nihilo").
        if (shorterNoSpace.length >= 3) return true
        val longerNoSpace = longer.replace(" ", "")
        return shorterNoSpace.length * 100 >= longerNoSpace.length * 25
    }

    private const val MIN_ACCEPT_SCORE = 20
}
