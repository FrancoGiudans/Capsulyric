package com.example.islandlyrics.lyrics.online.parser

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider

import com.example.islandlyrics.rules.ParserRule
import kotlin.math.abs

internal object OnlineLyricSidecarMerger {
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

    fun missingRequestedSidecars(
        result: OnlineLyricFetcher.LyricResult,
        rule: ParserRule
    ): Boolean {
        val providerSupportsSidecars = result.provider in listOf(OnlineLyricProvider.QQMusic, OnlineLyricProvider.Netease)
        if (!providerSupportsSidecars) return false
        val missingTranslation = rule.receiveOnlineTranslation && result.translationLyrics.isNullOrBlank()
        val missingRomanization = rule.receiveOnlineRomanization && result.romanLyrics.isNullOrBlank()
        return missingTranslation || missingRomanization
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
                .replace(Regex("""\[[^\]]+]"""), "")
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


