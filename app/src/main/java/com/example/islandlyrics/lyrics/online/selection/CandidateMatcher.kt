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

    /**
     * 从候选列表中挑选与 (title, artist, album, duration) 最匹配的一条。
     * 评分规则：
     *  - 标题相等 +36 / cleanTitle 后相等 +20 / 互相包含 +8 / 不匹配 -30
     *  - 歌手 token 交集：全等 +18 / 单向全等 +12 / 部分 +6 / 无交集 -18 / 空 -4
     *  - 时长相差超过 12 秒的候选直接拒绝；总身份分低于阈值时返回 null
     */
    fun <T : SearchCandidate> pickBest(
        candidates: List<T>,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L
    ): T? {
        return rank(candidates, title, artist, album, durationMs)
            .firstOrNull()
            ?.takeIf { it.score >= MIN_ACCEPT_SCORE }
            ?.candidate
    }

    /**
     * Returns candidates in descending identity score order.  The resolver uses
     * this instead of merging independent storefront searches and taking the
     * first non-empty ISRC.
     */
    fun <T : SearchCandidate> rank(
        candidates: List<T>,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L
    ): List<ScoredCandidate<T>> {
        if (candidates.isEmpty()) return emptyList()

        val scored = mutableListOf<ScoredCandidate<T>>()
        val targetVersionTags = VersionTagDetector.detect(title, album)
        for (candidate in candidates) {
            if (durationConflict(durationMs, candidate.matchedDurationMs)) continue
            val candidateVersionTags = VersionTagDetector.detect(candidate.matchedTitle, candidate.matchedAlbum)
            if (hasHardVersionConflict(targetVersionTags, candidateVersionTags)) continue
            val score = scoreCandidate(
                candidate,
                title,
                artist,
                album,
                durationMs,
                targetVersionTags,
                candidateVersionTags
            )
            scored += ScoredCandidate(candidate, score)
        }
        return scored.sortedByDescending { it.score }
    }

    /**
     * Search fallback must have a clear winner.  A direct song-id lookup does
     * not use this method because the platform already supplied the identity.
     */
    fun <T : SearchCandidate> pickBestWithMargin(
        candidates: List<T>,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        minimumMargin: Int = 12
    ): T? {
        val ranked = rank(candidates, title, artist, album, durationMs)
        val best = ranked.firstOrNull() ?: return null
        val runnerUp = ranked.drop(1).firstOrNull {
            !hasSameStableIdentity(best.candidate, it.candidate)
        }
        if (best.score < MIN_ACCEPT_SCORE) return null
        if (runnerUp != null && best.score - runnerUp.score < minimumMargin) return null
        return best.candidate
    }

    private fun hasSameStableIdentity(first: SearchCandidate, second: SearchCandidate): Boolean {
        val firstIsrc = normalizeStableId(first.isrc)
        val secondIsrc = normalizeStableId(second.isrc)
        if (firstIsrc.isNotBlank() && firstIsrc == secondIsrc) return true

        val firstTrackId = first.providerTrackId?.trim().orEmpty()
        val secondTrackId = second.providerTrackId?.trim().orEmpty()
        return firstTrackId.isNotBlank() && firstTrackId == secondTrackId
    }

    private fun normalizeStableId(value: String?): String = value.orEmpty()
        .filter(Char::isLetterOrDigit)
        .uppercase()

    private fun scoreCandidate(
        candidate: SearchCandidate,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        targetVersionTags: Set<VersionTag>,
        candidateVersionTags: Set<VersionTag>
    ): Int {
        val albumScore = scoreAlbumMatch(album, candidate.matchedAlbum)
        val durationScore = scoreDurationMatch(durationMs, candidate.matchedDurationMs)
        // Exact album + near-exact duration can bridge both localized title
        // and localized artist names. Treat this as a strong identity pair.
        val stableEvidenceBonus = if (albumScore >= 15 && durationScore >= 14) 40 else 0
        return scoreTitleMatch(title, candidate.matchedTitle) +
                scoreArtistMatch(artist, candidate.matchedArtist) +
                albumScore +
                durationScore +
                stableEvidenceBonus +
                versionCompatibilityScore(targetVersionTags, candidateVersionTags)
    }

    fun scoreTitleMatch(targetTitle: String, matchedTitle: String?): Int {
        if (targetTitle.isBlank()) return 0
        if (matchedTitle.isNullOrBlank()) return -30
        if (matchedTitle.equals(targetTitle, ignoreCase = true)) return 36

        val cleanTarget = cleanTitle(targetTitle).lowercase()
        val cleanMatched = cleanTitle(matchedTitle).lowercase()
        return when {
            cleanTarget.isBlank() || cleanMatched.isBlank() -> -30
            cleanMatched == cleanTarget -> 20
            cleanMatched.contains(cleanTarget) || cleanTarget.contains(cleanMatched) -> 8
            else -> -30
        }
    }

    fun scoreAlbumMatch(targetAlbum: String, matchedAlbum: String?): Int {
        if (targetAlbum.isBlank() || matchedAlbum.isNullOrBlank()) return 0
        val cleanTarget = cleanTitle(targetAlbum).lowercase()
        val cleanMatched = cleanTitle(matchedAlbum).lowercase()
        return when {
            cleanTarget.isBlank() || cleanMatched.isBlank() -> 0
            cleanTarget == cleanMatched -> 15
            cleanMatched.contains(cleanTarget) || cleanTarget.contains(cleanMatched) -> 8
            else -> -8
        }
    }

    fun scoreDurationMatch(targetDurationMs: Long, matchedDurationMs: Long?): Int {
        if (targetDurationMs <= 0L || matchedDurationMs == null || matchedDurationMs <= 0L) return 0
        val delta = kotlin.math.abs(targetDurationMs - matchedDurationMs)
        return when {
            delta <= 1_500L -> 20
            delta <= 3_000L -> 14
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
        val hardTags = setOf(
            VersionTag.LIVE,
            VersionTag.REMIX,
            VersionTag.COVER,
            VersionTag.INSTRUMENTAL,
            VersionTag.KARAOKE,
            VersionTag.RADIO_EDIT
        )
        return candidateTags.any { it in hardTags && it !in targetTags }
    }

    private fun versionCompatibilityScore(
        targetTags: Set<VersionTag>,
        candidateTags: Set<VersionTag>
    ): Int {
        if (targetTags == candidateTags) return if (targetTags.isEmpty()) 0 else 12
        if (targetTags.isEmpty() && candidateTags.isEmpty()) return 0
        if (targetTags.intersect(candidateTags).isNotEmpty()) return 5
        // A target with a version marker and a candidate without it is not a
        // hard reject, because some providers omit suffixes in their metadata.
        return if (targetTags.isNotEmpty() && candidateTags.isEmpty()) -8 else -18
    }

    fun scoreArtistMatch(targetArtist: String, matchedArtist: String?): Int {
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

    fun normalizeArtistTokens(artist: String): Set<String> {
        return artist
            .split("/", "&", ",", "、", " feat. ", " ft. ", " x ", " X ", ";")
            .map { it.trim().lowercase() }
            .map { it.replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .toSet()
    }

    /**
     * 清理标题：移除括号内容与常见后缀，供标题匹配使用。
     * 与 OnlineLyricFetcher 的 cleanTitle 保持同语义。
     */
    fun cleanTitle(title: String): String {
        var clean = title
        clean = clean.replace("\\(.*?\\)".toRegex(), " ")
        clean = clean.replace("\\[.*?\\]".toRegex(), " ")
        val suffixes = listOf("feat.", "ft.", "remix", "version", "live", "cover", "radio edit", "mix")
        for (suffix in suffixes) {
            clean = clean.replace(suffix, "", ignoreCase = true)
        }
        return clean.trim().replace("\\s+".toRegex(), " ")
    }

    private const val MIN_ACCEPT_SCORE = 20
}
