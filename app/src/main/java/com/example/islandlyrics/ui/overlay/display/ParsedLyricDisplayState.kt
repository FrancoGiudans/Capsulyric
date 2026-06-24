package com.example.islandlyrics.ui.overlay.display
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.state.LyricRepository

internal data class ParsedLyricDisplayState(
    val scrollingMode: ScrollingMode = ScrollingMode.NONE,
    val lines: List<OnlineLyricFetcher.LyricLine>? = null,
    val timelineCapability: LyricRepository.TimelineCapability = LyricRepository.TimelineCapability.NONE
) {
    val useSyllableScrolling: Boolean
        get() = scrollingMode == ScrollingMode.SYLLABLE

    val useLrcScrolling: Boolean
        get() = scrollingMode == ScrollingMode.LRC

    fun currentLine(position: Long): OnlineLyricFetcher.LyricLine? {
        val currentLines = lines ?: return null
        return if (timelineCapability == LyricRepository.TimelineCapability.ACTIVE_LINE_ONLY) {
            currentLines.firstOrNull()
        } else {
            currentLineIndex(position).takeIf { it >= 0 }?.let(currentLines::get)
        }
    }

    fun currentLineIndex(position: Long): Int {
        val currentLines = lines ?: return -1
        return if (timelineCapability == LyricRepository.TimelineCapability.ACTIVE_LINE_ONLY) {
            if (currentLines.isNotEmpty()) 0 else -1
        } else {
            currentLines.indexOfFirst { line ->
                position >= line.startTime && position < line.endTime
            }
        }
    }

    fun previousLine(index: Int): OnlineLyricFetcher.LyricLine? {
        val currentLines = lines ?: return null
        if (index < 0) return null
        if (timelineCapability != LyricRepository.TimelineCapability.MULTI_LINE) return null
        return currentLines.getOrNull(index - 1)
    }

    fun nextLine(index: Int): OnlineLyricFetcher.LyricLine? {
        val currentLines = lines ?: return null
        if (index < 0) return null
        if (timelineCapability != LyricRepository.TimelineCapability.MULTI_LINE) return null
        return currentLines.getOrNull(index + 1)
    }

    fun lineIndexBefore(position: Long): Int {
        val currentLines = lines ?: return -1
        if (timelineCapability != LyricRepository.TimelineCapability.MULTI_LINE) return -1
        return currentLines.indexOfLast { line -> position >= line.endTime }
    }

    fun lineIndexAfter(position: Long): Int {
        val currentLines = lines ?: return -1
        if (timelineCapability != LyricRepository.TimelineCapability.MULTI_LINE) return -1
        return currentLines.indexOfFirst { line -> position < line.startTime }
    }

    enum class ScrollingMode {
        NONE,
        SYLLABLE,
        LRC
    }

    companion object {
        fun from(parsedInfo: LyricRepository.ParsedLyricsInfo?): ParsedLyricDisplayState {
            val timelineCapability = parsedInfo?.timelineCapability ?: LyricRepository.TimelineCapability.NONE
            return when {
                parsedInfo != null && parsedInfo.hasSyllable -> ParsedLyricDisplayState(
                    scrollingMode = ScrollingMode.SYLLABLE,
                    lines = parsedInfo.lines,
                    timelineCapability = timelineCapability
                )
                parsedInfo != null && parsedInfo.lines.isNotEmpty() -> ParsedLyricDisplayState(
                    scrollingMode = ScrollingMode.LRC,
                    lines = parsedInfo.lines,
                    timelineCapability = timelineCapability
                )
                else -> ParsedLyricDisplayState(timelineCapability = timelineCapability)
            }
        }
    }
}
