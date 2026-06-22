package com.example.islandlyrics.lyrics.local

import android.content.Context
import android.net.Uri
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import java.io.InputStream
import java.nio.charset.Charset

object LocalLrcParser {

    data class LrcMetadata(
        val title: String?,
        val artist: String?,
        val album: String?,
        val by: String?
    )

    data class ParseResult(
        val lines: List<OnlineLyricFetcher.LyricLine>,
        val hasSyllable: Boolean,
        val translationLines: Map<Long, String>,
        val romanLines: Map<Long, String>
    )

    fun extractMetadata(context: Context, uri: Uri): LrcMetadata? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(2048)
                val read = stream.read(buffer)
                if (read > 0) buffer.copyOf(read) else null
            } ?: return null
            val header = decodeWithFallback(bytes)
            parseMetadataFromHeader(header)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMetadataFromHeader(header: String): LrcMetadata {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var by: String? = null

        for (line in header.lines()) {
            val match = METADATA_EXTRACT_REGEX.find(line) ?: continue
            val tag = match.groupValues[1].lowercase()
            val value = match.groupValues[2].trim()
            if (value.isBlank()) continue
            when (tag) {
                "ti" -> title = value
                "ar" -> artist = value
                "al" -> album = value
                "by" -> by = value
            }
            if (title != null && artist != null) break
        }
        return LrcMetadata(title, artist, album, by)
    }

    fun parse(context: Context, uri: Uri): ParseResult? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            val content = decodeWithFallback(bytes)
            parseContent(content)
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Failed to parse local LRC: ${e.message}")
            null
        }
    }

    fun parseContent(content: String): ParseResult? {
        if (content.isBlank()) return null

        val lines = content.lines()
        val mainLines = mutableListOf<Pair<Long, String>>()
        val esLyricLines = mutableListOf<OnlineLyricFetcher.LyricLine>()
        val translationLines = mutableMapOf<Long, String>()
        val romanLines = mutableMapOf<Long, String>()
        var hasEsLyric = false

        for (line in lines) {
            if (line.isBlank()) continue
            if (isMetadataTag(line)) continue

            val timestamps = extractTimestamps(line)
            if (timestamps.isEmpty()) continue

            val textPart = line.replace(TIMESTAMP_REGEX, "").trim()
            if (textPart.isBlank()) continue

            if (hasEsLyricTokens(textPart)) {
                hasEsLyric = true
                for (ts in timestamps) {
                    parseEsLyricLine(ts, textPart)?.let { esLyricLines.add(it) }
                }
            } else {
                for (ts in timestamps) {
                    mainLines.add(ts to textPart)
                }
            }
        }

        if (hasEsLyric && esLyricLines.isNotEmpty()) {
            val sorted = esLyricLines.sortedBy { it.startTime }
            val withEndTimes = sorted.mapIndexed { index, lyricLine ->
                if (lyricLine.endTime <= lyricLine.startTime) {
                    val nextStart = sorted.getOrNull(index + 1)?.startTime
                    lyricLine.copy(endTime = nextStart ?: (lyricLine.startTime + 5000))
                } else {
                    lyricLine
                }
            }
            return ParseResult(withEndTimes, true, translationLines, romanLines)
        }

        if (mainLines.isEmpty()) return null

        mainLines.sortBy { it.first }
        val parsed = mainLines.mapIndexed { index, (startTime, text) ->
            val endTime = if (index < mainLines.size - 1) {
                mainLines[index + 1].first
            } else {
                startTime + 5000
            }
            OnlineLyricFetcher.LyricLine(startTime, endTime, text, null)
        }

        return ParseResult(parsed, false, translationLines, romanLines)
    }

    private fun parseEsLyricLine(lineStartTime: Long, textPart: String): OnlineLyricFetcher.LyricLine? {
        val syllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
        val fullText = StringBuilder()

        for (match in ES_LYRIC_TOKEN_REGEX.findAll(textPart)) {
            val offset = match.groupValues[1].toLongOrNull() ?: continue
            val duration = match.groupValues[2].toLongOrNull() ?: continue
            val text = match.groupValues[3]
            if (text.isBlank()) continue

            val absStart = lineStartTime + offset
            val absEnd = absStart + duration
            syllables.add(OnlineLyricFetcher.SyllableInfo(absStart, absEnd, text))
            fullText.append(text)
        }

        if (syllables.isEmpty()) {
            return OnlineLyricFetcher.LyricLine(lineStartTime, lineStartTime, textPart, null)
        }

        val lineEnd = syllables.maxOf { it.endTime }
        return OnlineLyricFetcher.LyricLine(lineStartTime, lineEnd, fullText.toString(), syllables)
    }

    private fun extractTimestamps(line: String): List<Long> {
        return TIMESTAMP_REGEX.findAll(line).mapNotNull { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val fraction = match.groupValues[3]
            val millis = when (fraction.length) {
                2 -> fraction.toLongOrNull()?.times(10L) ?: 0L
                3 -> fraction.toLongOrNull() ?: 0L
                else -> fraction.toLongOrNull()?.times(10L) ?: 0L
            }
            minutes * 60000L + seconds * 1000L + millis
        }.toList()
    }

    private fun hasEsLyricTokens(text: String): Boolean {
        return ES_LYRIC_TOKEN_REGEX.containsMatchIn(text)
    }

    private fun isMetadataTag(line: String): Boolean {
        return METADATA_TAG_REGEX.matches(line)
    }

    private fun decodeWithFallback(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('�')) return utf8

        return try {
            String(bytes, Charset.forName("GBK"))
        } catch (_: Exception) {
            utf8
        }
    }

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})\.(\d{2,3})]""")
    private val ES_LYRIC_TOKEN_REGEX = Regex("""<(\d+),(\d+)>([^<]*)""")
    private val METADATA_TAG_REGEX = Regex("""^\[[a-zA-Z]+:.*]$""")
    private val METADATA_EXTRACT_REGEX = Regex("""^\[([a-zA-Z]+):(.*)]$""")
    private const val TAG = "LocalLrcParser"
}
