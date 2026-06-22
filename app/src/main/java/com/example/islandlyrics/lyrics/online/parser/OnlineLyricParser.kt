package com.example.islandlyrics.lyrics.online.parser

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher

import kotlin.text.RegexOption.DOT_MATCHES_ALL

internal object OnlineLyricParser {
    fun parseWordLevelLyrics(content: String): List<OnlineLyricFetcher.LyricLine> {
        val trimmed = content.trimStart()
        return when {
            trimmed.startsWith("<tt") -> parseTtmlLyrics(content)
            else -> parseBracketWordLyrics(content)
        }
    }

    fun isWordLevelLyrics(content: String, lyricTypeHint: String? = null): Boolean {
        val trimmed = content.trimStart()
        if (trimmed.startsWith("<tt")) return true
        if (lyricTypeHint?.contains("word", ignoreCase = true) == true) return true
        if (lyricTypeHint?.contains("syllable", ignoreCase = true) == true) return true

        val bracketWordRegex = Regex("""(?m)^\[\d+(?:,\d+)?]\s*(?:<\d+,\d+,\d+>[^<\r\n]*)+""")
        return bracketWordRegex.containsMatchIn(content)
    }

    fun parseKrcLyrics(krcContent: String): List<OnlineLyricFetcher.LyricLine> {
        val lines = mutableListOf<OnlineLyricFetcher.LyricLine>()
        val krcLines = krcContent.lines()

        for (line in krcLines) {
            if (line.startsWith('[') && line.length >= 5 && line[1].isDigit()) {
                val parsedLine = parseKrcLine(line)
                if (parsedLine != null) {
                    lines.add(parsedLine)
                }
            }
        }

        return lines.sortedBy { it.startTime }
    }

    fun parseLrcLyrics(lrcContent: String): List<OnlineLyricFetcher.LyricLine> {
        val lines = mutableListOf<OnlineLyricFetcher.LyricLine>()
        val lrcLines = lrcContent.lines()

        val timedLines = mutableListOf<Pair<Long, String>>()
        val timeRegex = "\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]".toRegex()

        for (line in lrcLines) {
            val matches = timeRegex.findAll(line)
            val text = line.replace(timeRegex, "").trim()

            for (match in matches) {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val millis = match.groupValues[3].let {
                    if (it.length == 2) it.toLong() * 10 else it.toLong()
                }
                val totalMs = minutes * 60000 + seconds * 1000 + millis
                timedLines.add(Pair(totalMs, text))
            }
        }

        timedLines.sortBy { it.first }

        for (i in timedLines.indices) {
            val startTime = timedLines[i].first
            val text = timedLines[i].second
            val endTime = if (i < timedLines.size - 1) {
                timedLines[i + 1].first
            } else {
                startTime + 5000
            }
            lines.add(OnlineLyricFetcher.LyricLine(startTime, endTime, text, null))
        }

        return lines
    }

    fun parseSodaLyrics(
        lyricContent: String,
        lyricType: String
    ): List<OnlineLyricFetcher.LyricLine> {
        val trimmed = lyricContent.trimStart()
        return when {
            trimmed.startsWith("<tt") -> parseTtmlLyrics(lyricContent)
            isWordLevelLyrics(lyricContent, lyricType) -> parseWordLevelLyrics(lyricContent)
            hasLrcTimestamps(lyricContent) -> parseLrcLyrics(lyricContent)
            lyricType.contains("lrc", ignoreCase = true) -> parseLrcLyrics(lyricContent)
            else -> emptyList()
        }
    }

    private fun parseBracketWordLyrics(content: String): List<OnlineLyricFetcher.LyricLine> {
        val lines = content.lines()
            .mapNotNull { parseBracketWordLine(it) }
            .sortedBy { it.startTime }

        if (lines.isEmpty()) return emptyList()

        return lines.mapIndexed { index, line ->
            val nextStart = lines.getOrNull(index + 1)?.startTime
            if (nextStart != null && line.endTime <= line.startTime) {
                line.copy(endTime = nextStart)
            } else {
                line
            }
        }
    }

    private fun parseBracketWordLine(line: String): OnlineLyricFetcher.LyricLine? {
        val headerMatch = Regex("""^\[(\d+)(?:,(\d+))?]""").find(line) ?: return null
        val lineStartTime = headerMatch.groupValues[1].toLongOrNull() ?: return null
        val explicitDuration = headerMatch.groupValues.getOrNull(2)?.toLongOrNull()
        val contentPart = line.substring(headerMatch.range.last + 1)

        val syllableRegex = Regex("""<(\d+),(\d+),(\d+)>([^<]*)""")
        val syllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
        val fullText = StringBuilder()

        for (match in syllableRegex.findAll(contentPart)) {
            val offset = match.groupValues[1].toLong()
            val duration = match.groupValues[2].toLong()
            val text = match.groupValues[4]
            if (text.isBlank()) continue

            val absStartTime = lineStartTime + offset
            val absEndTime = absStartTime + duration
            syllables.add(OnlineLyricFetcher.SyllableInfo(absStartTime, absEndTime, text))
            fullText.append(text)
        }

        if (syllables.isEmpty()) return null

        val lineEndTime = explicitDuration?.let { lineStartTime + it }
            ?: syllables.maxOf { it.endTime }

        return OnlineLyricFetcher.LyricLine(
            startTime = lineStartTime,
            endTime = lineEndTime,
            text = fullText.toString(),
            syllables = syllables
        )
    }

    private fun parseKrcLine(line: String): OnlineLyricFetcher.LyricLine? {
        return try {
            val lineHeaderRegex = Regex("^\\[(\\d+),(\\d+)\\]")
            val headerMatch = lineHeaderRegex.find(line) ?: return null

            val lineStartTime = headerMatch.groupValues[1].toLong()
            val lineDuration = headerMatch.groupValues[2].toLong()
            val lineEndTime = lineStartTime + lineDuration

            val contentPart = line.substring(headerMatch.range.last + 1)

            val syllableRegex = Regex("<(\\d+),(\\d+),(\\d+)>([^<]*)")
            val matches = syllableRegex.findAll(contentPart)

            val syllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
            val fullText = StringBuilder()

            for (match in matches) {
                val offset = match.groupValues[1].toLong()
                val duration = match.groupValues[2].toLong()
                val text = match.groupValues[4]

                val absStartTime = lineStartTime + offset
                val absEndTime = absStartTime + duration

                syllables.add(OnlineLyricFetcher.SyllableInfo(absStartTime, absEndTime, text))
                fullText.append(text)
            }

            if (syllables.isNotEmpty()) {
                OnlineLyricFetcher.LyricLine(lineStartTime, lineEndTime, fullText.toString(), syllables)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun hasLrcTimestamps(content: String): Boolean {
        return Regex("""\[\d{2}:\d{2}\.\d{2,3}]""").containsMatchIn(content)
    }

    private fun parseTtmlLyrics(ttmlContent: String): List<OnlineLyricFetcher.LyricLine> {
        val lines = mutableListOf<OnlineLyricFetcher.LyricLine>()
        val lineRegex = Regex("<p\\b([^>]*)>(.*?)</p>", DOT_MATCHES_ALL)
        val spanRegex = Regex("<span\\b([^>]*)>(.*?)</span>", DOT_MATCHES_ALL)

        for (lineMatch in lineRegex.findAll(ttmlContent)) {
            val lineAttributes = parseXmlAttributes(lineMatch.groupValues[1])
            val lineBody = lineMatch.groupValues[2]
            val lineStart = parseFlexibleTimeToMs(lineAttributes["begin"]) ?: continue
            val lineEnd = parseFlexibleTimeToMs(lineAttributes["end"])

            val syllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
            for (spanMatch in spanRegex.findAll(lineBody)) {
                val spanAttributes = parseXmlAttributes(spanMatch.groupValues[1])
                val start = parseFlexibleTimeToMs(spanAttributes["begin"]) ?: continue
                val end = parseFlexibleTimeToMs(spanAttributes["end"]) ?: continue
                val text = decodeXmlText(stripXmlTags(spanMatch.groupValues[2])).trim()
                if (text.isBlank()) continue
                syllables.add(OnlineLyricFetcher.SyllableInfo(start, end, text))
            }

            val text = decodeXmlText(stripXmlTags(lineBody))
                .replace(Regex("\\s+"), " ")
                .trim()
            if (text.isBlank()) continue

            lines.add(
                OnlineLyricFetcher.LyricLine(
                    startTime = lineStart,
                    endTime = lineEnd ?: syllables.lastOrNull()?.endTime ?: (lineStart + 5000),
                    text = text,
                    syllables = syllables.ifEmpty { null }
                )
            )
        }

        return lines.sortedBy { it.startTime }
    }

    private fun parseXmlAttributes(raw: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        val attrRegex = Regex("""([A-Za-z_:][A-Za-z0-9_:\-.]*)="([^"]*)"""")
        for (match in attrRegex.findAll(raw)) {
            attributes[match.groupValues[1]] = match.groupValues[2]
        }
        return attributes
    }

    private fun stripXmlTags(text: String): String {
        return text.replace(Regex("<[^>]+>"), " ")
    }

    private fun decodeXmlText(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

    private fun parseFlexibleTimeToMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim().removeSuffix("s")
        val parts = normalized.split(":")
        return try {
            when (parts.size) {
                1 -> (parts[0].toDouble() * 1000).toLong()
                2 -> ((parts[0].toLong() * 60_000) + (parts[1].toDouble() * 1000)).toLong()
                3 -> ((parts[0].toLong() * 3_600_000) + (parts[1].toLong() * 60_000) + (parts[2].toDouble() * 1000)).toLong()
                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}


