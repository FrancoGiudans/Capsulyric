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
            currentLines.firstOrNull { line ->
                position >= line.startTime && position < line.endTime
            }
        }
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
