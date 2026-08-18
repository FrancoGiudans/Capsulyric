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
}

/**
 * 多候选挑选器（混合式搜索架构的第一层）。
 *
 * 在 provider 内部对搜索返回的多个候选做轻量标题/歌手匹配，选出最匹配的一条
 * 再取歌词；全部不匹配时回退第 0 条（保持原有兜底行为）。
 */
internal object CandidateMatcher {

    /**
     * 从候选列表中挑选与 (title, artist) 最匹配的一条。
     * 评分规则：
     *  - 标题相等 +36 / cleanTitle 后相等 +20 / 互相包含 +8 / 不匹配 -30
     *  - 歌手 token 交集：全等 +18 / 单向全等 +12 / 部分 +6 / 无交集 -18 / 空 -4
     *  - 标题分 <= 0（完全不匹配）的候选不入选，全部不匹配则回退第 0 条
     */
    fun <T : SearchCandidate> pickBest(
        candidates: List<T>,
        title: String,
        artist: String
    ): T? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates[0]

        var best: T? = null
        var bestScore = Int.MIN_VALUE
        for (candidate in candidates) {
            val score = scoreCandidate(candidate, title, artist)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }

        // 标题完全不匹配的候选不采用，回退第 0 条
        return if (bestScore > 0) best ?: candidates[0] else candidates[0]
    }

    private fun scoreCandidate(
        candidate: SearchCandidate,
        title: String,
        artist: String
    ): Int {
        return scoreTitleMatch(title, candidate.matchedTitle) +
                scoreArtistMatch(artist, candidate.matchedArtist)
    }

    fun scoreTitleMatch(targetTitle: String, matchedTitle: String?): Int {
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
}
