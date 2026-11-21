package com.example.islandlyrics.ui.overlay.floating

import android.media.session.PlaybackState
import android.os.SystemClock
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.overlay.display.toDisplayLine
import com.example.islandlyrics.ui.overlay.model.LyricPresentation
import com.example.islandlyrics.ui.overlay.model.SecondaryTextResolver
import com.example.islandlyrics.ui.overlay.model.WordProgressCalculator
import kotlin.math.ceil

internal data class FloatingLyricsClock(val positionMs: Long, val updatedAtMs: Long, val speed: Float) {
    private fun position(now: Long): Double =
        positionMs + if (updatedAtMs > 0) (now - updatedAtMs).coerceAtLeast(0L) * speed.toDouble() else 0.0

    fun positionAt(now: Long): Long = position(now).toLong()

    fun delayUntil(positionMs: Long, now: Long): Long? =
        if (updatedAtMs > 0L && speed > 0f) {
            ceil((positionMs - position(now)) / speed).toLong().coerceAtLeast(1L)
        } else {
            null
        }
}

internal fun PlaybackState?.toLyricsClock(fallbackPosition: Long?): FloatingLyricsClock? {
    val playing = this?.state == PlaybackState.STATE_PLAYING
    val speed = this?.playbackSpeed?.takeIf { playing && it.isFinite() && it > 0f } ?: 0f
    if (this != null && position >= 0L && (!playing || lastPositionUpdateTime > 0L)) {
        return FloatingLyricsClock(position, lastPositionUpdateTime, speed)
    }
    return fallbackPosition?.let { FloatingLyricsClock(it, SystemClock.elapsedRealtime(), speed) }
}

internal class FloatingLyricsTimeline private constructor(private val lines: List<LyricPresentation.DisplayLine>) {
    fun presentationAt(position: Long?): LyricPresentation {
        val index = if (position == null) -1 else lastStartedLine(position)
        return LyricPresentation(
            currentLine = lines.getOrNull(index),
            previousLine = lines.getOrNull(index - 1),
            nextLine = lines.getOrNull(index + 1),
            currentLineIndex = index,
            timelineCapability = LyricRepository.TimelineCapability.MULTI_LINE
        )
    }

    // Keep each line in its slot until the next line starts, including instrumental gaps.
    private fun lastStartedLine(position: Long): Int {
        var first = 0
        var end = lines.size
        while (first < end) {
            val middle = first + (end - first) / 2
            if (lines[middle].startTime <= position) first = middle + 1 else end = middle
        }
        return first - 1
    }

    companion object {
        fun from(parsed: LyricRepository.ParsedLyricsInfo?): FloatingLyricsTimeline? {
            val lines = parsed?.takeIf { it.timelineCapability == LyricRepository.TimelineCapability.MULTI_LINE }
                ?.lines ?: return null
            val displayLines = lines.asSequence()
                .filterNot { SecondaryTextResolver.isPlaceholder(it.text) }
                .sortedBy { it.startTime }
                .distinctBy { it.startTime }
                .map { it.toDisplayLine() }
                .toList()
            return FloatingLyricsTimeline(displayLines)
        }
    }
}

internal fun floatingLiveLine(
    parsed: LyricRepository.ParsedLyricsInfo?,
    lyric: LyricRepository.LyricInfo?
): LyricPresentation {
    val current = parsed?.takeIf { it.timelineCapability == LyricRepository.TimelineCapability.ACTIVE_LINE_ONLY }
        ?.lines?.firstOrNull()?.toDisplayLine()
        ?: lyric?.takeIf { it.lyric.isNotBlank() }?.let {
            LyricPresentation.DisplayLine(it.lyric, romanization = it.roma, translation = it.translation)
        }
    return LyricPresentation(currentLine = current)
}

internal fun LyricPresentation.DisplayLine.wordProgressAt(position: Long): LyricPresentation.WordProgress? =
    WordProgressCalculator.compute(text, startTime, endTime, syllables, position)
