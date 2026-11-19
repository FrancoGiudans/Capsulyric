package com.example.islandlyrics.ui.overlay.superisland.calibration

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandLyricLayout

/** Owns only the calibrated Super Island window; other renderers keep their existing metrics. */
internal class CalibratedLyricWindowResolver {
    private var adaptiveKey: Any? = null
    private var adaptiveStart = 0
    private var adaptiveShiftAt = 0L
    private var adaptiveTailAt = 0L
    private var windowKey: Any? = null
    private var cachedWindow: CalibratedTextWindow? = null

    data class Result(val text: String, val done: Boolean)

    fun reset() { adaptiveKey = null; windowKey = null; cachedWindow = null }

    fun resolve(
        text: String,
        policy: RightTextWidthPolicy,
        leftLimit: Int?,
        disabled: Boolean,
        line: OnlineLyricFetcher.LyricLine?,
        syllablesEnabled: Boolean,
        centered: Boolean,
        position: Long,
        now: Long,
        shiftDelay: Long,
        initialPause: Long,
        finalPause: Long
    ): Result {
        val key = Triple(text, policy.calibration, leftLimit)
        if (windowKey != key) {
            cachedWindow = CalibratedTextWindow(text, policy, leftLimit, SuperIslandLyricLayout::calculateWeight)
            windowKey = key
        }
        val window = checkNotNull(cachedWindow)
        if (disabled || window.fits(text)) return Result(window.prefix(), true)
        if (line != null) {
            val syllables = line.syllables
            if (syllablesEnabled && text == line.text && !syllables.isNullOrEmpty()) {
                val focus = focusedIndex(text, syllables, position)
                if (focus != null) {
                    val last = syllables.lastOrNull { it.text.isNotBlank() }
                    return Result(if (last != null && position >= last.startTime) window.suffix()
                        else window.atFocus(focus, centered), false)
                }
            }
            val duration = line.endTime - line.startTime
            // Reach the final suffix before the next line replaces this one.
            val startHold = minOf(initialPause, duration.coerceAtLeast(0L) / 4)
            val endHold = minOf(finalPause, duration.coerceAtLeast(0L) / 4)
            val scrollDuration = duration - startHold - endHold
            val progress = if (scrollDuration > 0) {
                ((position - line.startTime - startHold).toDouble() / scrollDuration).toFloat()
            } else 0f
            return Result(window.atProgress(progress), false)
        }
        if (key != adaptiveKey) {
            adaptiveKey = key
            adaptiveStart = 0
            adaptiveShiftAt = now + initialPause
            adaptiveTailAt = 0L
        }
        val tail = window.tailStart()
        if (adaptiveStart < tail && now >= adaptiveShiftAt) {
            adaptiveStart = window.nextIndex(adaptiveStart).coerceAtMost(tail)
            adaptiveShiftAt = now + shiftDelay.coerceAtLeast(100L)
        }
        if (adaptiveStart == tail && adaptiveTailAt == 0L) adaptiveTailAt = now
        return Result(window.fromIndex(adaptiveStart), adaptiveTailAt > 0 && now - adaptiveTailAt >= finalPause)
    }

    private fun focusedIndex(
        text: String, syllables: List<OnlineLyricFetcher.SyllableInfo>, position: Long
    ): Int? {
        val offsets = text.indices.filterNot { text[it].isWhitespace() || Character.isSpaceChar(text[it]) }
        val normalized = offsets.map { text[it] }.joinToString("")
        var offset = 0
        var focus = 0
        for (syllable in syllables) {
            val part = syllable.text.filterNot { it.isWhitespace() || Character.isSpaceChar(it) }
            if (part.isEmpty()) continue
            if (!normalized.regionMatches(offset, part, 0, part.length)) return null
            if (position >= syllable.startTime) focus = offsets.getOrNull(offset) ?: return null
            offset += part.length
        }
        return focus.takeIf { offset == normalized.length }
    }
}
