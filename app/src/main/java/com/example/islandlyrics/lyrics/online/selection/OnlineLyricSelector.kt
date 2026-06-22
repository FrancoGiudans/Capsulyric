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
        useSmartSelection: Boolean
    ): OnlineLyricFetcher.LyricResult? {
        val usableResults = attempts.mapNotNull { it.result }
            .filter { isUsableResult(it) }

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
            result.score = buildQualityScore(result, targetTitle, targetArtist)
        }

        return results
            .filter { isUsableResult(it) }
            .sortedWith(
                compareByDescending<OnlineLyricFetcher.LyricResult> { it.score }
                    .thenBy { providerPriority[it.provider] ?: Int.MAX_VALUE }
                    .thenByDescending { it.hasSyllable }
            )
            .firstOrNull()
    }

    private fun isUsableResult(result: OnlineLyricFetcher.LyricResult): Boolean {
        return !result.lyrics.isNullOrBlank() && !result.parsedLines.isNullOrEmpty()
    }

    private fun buildQualityScore(
        result: OnlineLyricFetcher.LyricResult,
        targetTitle: String,
        targetArtist: String
    ): Int {
        var score = 0

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

    private fun scoreTitleMatch(targetTitle: String, matchedTitle: String?): Int {
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


