package com.example.islandlyrics.ui.overlay.display
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.state.LyricRepository

internal data class TimingGapDisplay(
    val displayLyric: String,
    val fullLyric: String,
    val preferMetadataLayout: Boolean,
    val isTimingGapPlaceholder: Boolean,
    val isAnimated: Boolean,
    val nextDelayMs: Long
)

internal object TimingGapDisplayResolver {
    private const val HOLD_THRESHOLD_MS = 2_000L
    private const val TRANSITION_HOLD_MS = 1_200L
    private const val METADATA_THRESHOLD_MS = 4_500L
    private const val ANIMATION_LEAD_MS = 800L
    private const val DOT_FRAME_MS = 450L
    private const val ANIMATION_TOTAL_MS = DOT_FRAME_MS * 3
    private const val FULL_SEQUENCE_MS = ANIMATION_LEAD_MS + ANIMATION_TOTAL_MS
    private const val PLACEHOLDER = "♪"

    fun resolve(
        lines: List<OnlineLyricFetcher.LyricLine>?,
        position: Long,
        isPlaying: Boolean,
        timelineCapability: LyricRepository.TimelineCapability,
        lastStableDisplayLyric: String,
        lastStableFullLyric: String,
        maxDisplayWeight: Int
    ): TimingGapDisplay? {
        if (timelineCapability != LyricRepository.TimelineCapability.MULTI_LINE) return null
        if (!isPlaying || lines.isNullOrEmpty()) return null

        val firstLine = lines.first()
        if (position < firstLine.startTime) {
            val remainingUntilFirst = firstLine.startTime - position
            val shouldPreferMetadataLayout = remainingUntilFirst > FULL_SEQUENCE_MS
            return if (firstLine.startTime >= FULL_SEQUENCE_MS &&
                remainingUntilFirst <= ANIMATION_TOTAL_MS) {
                buildCountdownGapDisplay(remainingUntilFirst)
            } else {
                TimingGapDisplay(
                    displayLyric = PLACEHOLDER,
                    fullLyric = PLACEHOLDER,
                    preferMetadataLayout = shouldPreferMetadataLayout,
                    isTimingGapPlaceholder = true,
                    isAnimated = false,
                    nextDelayMs = computeGapPlaceholderDelay(
                        remainingMs = remainingUntilFirst,
                        supportsCountdown = firstLine.startTime >= FULL_SEQUENCE_MS
                    )
                )
            }
        }

        val lastLine = lines.last()
        if (position >= lastLine.endTime) {
            val display = lastStableDisplayLyric.ifBlank {
                LyricTextWindowCalculator.extractByWeight(lastLine.text, 0, maxDisplayWeight)
            }
            val full = lastStableFullLyric.ifBlank { lastLine.text }
            return TimingGapDisplay(display, full, false, false, false, 1000L)
        }

        for (index in 0 until lines.lastIndex) {
            val current = lines[index]
            val next = lines[index + 1]
            if (position < current.endTime || position >= next.startTime) continue

            val remainingUntilNext = next.startTime - position
            val gapDuration = next.startTime - current.endTime
            if (gapDuration < HOLD_THRESHOLD_MS) {
                return stableGapFallback(current, remainingUntilNext, lastStableDisplayLyric, lastStableFullLyric, maxDisplayWeight)
            }

            if (gapDuration < METADATA_THRESHOLD_MS) {
                return stableGapFallback(current, remainingUntilNext, lastStableDisplayLyric, lastStableFullLyric, maxDisplayWeight)
            }

            val elapsedInGap = (gapDuration - remainingUntilNext).coerceAtLeast(0L)
            if (elapsedInGap < TRANSITION_HOLD_MS) {
                return stableGapFallback(
                    previousLine = current,
                    remainingUntilNext = minOf(
                        remainingUntilNext,
                        (TRANSITION_HOLD_MS - elapsedInGap).coerceAtLeast(1L)
                    ),
                    lastStableDisplayLyric = lastStableDisplayLyric,
                    lastStableFullLyric = lastStableFullLyric,
                    maxDisplayWeight = maxDisplayWeight
                )
            }

            return if (remainingUntilNext <= ANIMATION_TOTAL_MS) {
                buildCountdownGapDisplay(remainingUntilNext)
            } else {
                TimingGapDisplay(
                    displayLyric = PLACEHOLDER,
                    fullLyric = PLACEHOLDER,
                    preferMetadataLayout = true,
                    isTimingGapPlaceholder = true,
                    isAnimated = false,
                    nextDelayMs = (remainingUntilNext - ANIMATION_TOTAL_MS).coerceAtLeast(1L)
                )
            }
        }

        return null
    }

    fun noCurrentLineDisplay(
        currentLyric: String,
        timelineCapability: LyricRepository.TimelineCapability,
        lastStableDisplayLyric: String,
        maxDisplayWeight: Int
    ): String {
        if (shouldHoldStableDisplayWithoutGapAnimation(timelineCapability)) {
            return lastStableDisplayLyric.ifBlank {
                LyricTextWindowCalculator.extractByWeight(currentLyric, 0, maxDisplayWeight)
            }
        }
        return LyricTextWindowCalculator.extractByWeight(currentLyric, 0, maxDisplayWeight)
    }

    fun noCurrentLineFullLyric(
        currentLyric: String,
        timelineCapability: LyricRepository.TimelineCapability,
        lastStableFullLyric: String
    ): String {
        if (shouldHoldStableDisplayWithoutGapAnimation(timelineCapability)) {
            return lastStableFullLyric.ifBlank { currentLyric }
        }
        return currentLyric
    }

    private fun shouldHoldStableDisplayWithoutGapAnimation(
        timelineCapability: LyricRepository.TimelineCapability
    ): Boolean {
        return timelineCapability == LyricRepository.TimelineCapability.ACTIVE_LINE_ONLY
    }

    private fun stableGapFallback(
        previousLine: OnlineLyricFetcher.LyricLine,
        remainingUntilNext: Long,
        lastStableDisplayLyric: String,
        lastStableFullLyric: String,
        maxDisplayWeight: Int
    ): TimingGapDisplay {
        val display = lastStableDisplayLyric.ifBlank {
            LyricTextWindowCalculator.extractByWeight(previousLine.text, 0, maxDisplayWeight)
        }
        val full = lastStableFullLyric.ifBlank { previousLine.text }
        return TimingGapDisplay(display, full, false, false, false, remainingUntilNext.coerceAtLeast(1L))
    }

    private fun buildCountdownGapDisplay(remainingUntilNext: Long): TimingGapDisplay {
        val indicator: String
        val nextDelayMs: Long

        when {
            remainingUntilNext > DOT_FRAME_MS * 2 -> {
                indicator = "●●●"
                nextDelayMs = remainingUntilNext - DOT_FRAME_MS * 2
            }
            remainingUntilNext > DOT_FRAME_MS -> {
                indicator = "●●"
                nextDelayMs = remainingUntilNext - DOT_FRAME_MS
            }
            else -> {
                indicator = "●"
                nextDelayMs = remainingUntilNext
            }
        }
        return TimingGapDisplay(indicator, indicator, false, false, true, nextDelayMs.coerceAtLeast(1L))
    }

    private fun computeGapPlaceholderDelay(remainingMs: Long, supportsCountdown: Boolean): Long {
        if (remainingMs <= 0L) return 1L
        if (!supportsCountdown) return remainingMs.coerceAtLeast(1L)
        return when {
            remainingMs > FULL_SEQUENCE_MS ->
                (remainingMs - FULL_SEQUENCE_MS).coerceAtLeast(1L)
            remainingMs > ANIMATION_TOTAL_MS ->
                (remainingMs - ANIMATION_TOTAL_MS).coerceAtLeast(1L)
            else ->
                remainingMs.coerceAtLeast(1L)
        }
    }
}
