/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import androidx.compose.ui.util.lerp
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.overlay.display.TimingGapDisplayResolver
import com.example.islandlyrics.ui.overlay.model.SecondaryTextResolver
import kotlin.math.abs

internal data class LyricsPlaybackStart(
    val track: LyricRepository.MediaInfo,
    val lines: List<LyricLine>,
) {
    fun matches(candidate: LyricRepository.MediaInfo?): Boolean {
        if (candidate == null || candidate.packageName != track.packageName ||
            candidate.title != track.title || candidate.artist != track.artist
        ) return false
        if (candidate.mediaId.isNotBlank() && track.mediaId.isNotBlank()) {
            return candidate.mediaId == track.mediaId
        }
        return candidate.mediaUri.isBlank() || track.mediaUri.isBlank() || candidate.mediaUri == track.mediaUri
    }
}

internal fun LyricRepository.ParsedLyricsInfo?.playbackLines(): List<LyricLine> =
    if (this?.timelineCapability == LyricRepository.TimelineCapability.MULTI_LINE) {
        lines.filter { it.isTimedLyric() }.sortedBy { it.startTime }
    } else {
        emptyList()
    }

private fun LyricLine.isTimedLyric() = startTime >= 0 && !SecondaryTextResolver.isPlaceholder(text)

private const val PlaybackInterludeChargeMillis = 250L
private const val PlaybackInterludeExitMillis = 400L

internal data class PlaybackInterlude(val beforeLineIndex: Int, val startTime: Long, val endTime: Long) {
    val exitStartTime: Long get() = endTime - PlaybackInterludeExitMillis
    val chargeStartTime: Long get() = exitStartTime - PlaybackInterludeChargeMillis

    fun dotProgress(index: Int, position: Long): Float {
        val countdownDuration = (chargeStartTime - startTime).coerceAtLeast(1L)
        val progress = ((position - startTime).toFloat() / countdownDuration * 3f - index).coerceIn(0f, 1f)
        return progress * progress * (3f - 2f * progress)
    }

    fun chargeProgress(position: Long): Float =
        ((position - chargeStartTime).toFloat() / PlaybackInterludeChargeMillis).coerceIn(0f, 1f)

    fun exitProgress(position: Long): Float =
        ((position - exitStartTime).toFloat() / PlaybackInterludeExitMillis).coerceIn(0f, 1f)
}

internal fun List<LyricLine>.playbackInterludes(): Map<Int, PlaybackInterlude> {
    val first = firstOrNull() ?: return emptyMap()
    return buildMap {
        if (TimingGapDisplayResolver.supportsIntroCountdown(first.startTime)) {
            put(0, PlaybackInterlude(0, 0L, first.startTime))
        }
        var coveredUntil = first.startTime
        this@playbackInterludes.forEachIndexed { index, line ->
            if (TimingGapDisplayResolver.interludeStart(coveredUntil, line.startTime) != null) {
                put(index, PlaybackInterlude(index, coveredUntil, line.startTime))
            }
            coveredUntil = maxOf(coveredUntil, line.startTime, line.endTime)
        }
    }
}

internal fun Map<Int, PlaybackInterlude>.activeInterlude(currentLineIndex: Int, position: Long): PlaybackInterlude? =
    get(currentLineIndex + 1)?.takeIf { position >= it.startTime && position < it.endTime }

internal data class PlaybackLyricDepth(
    val mainBlur: Float,
    val secondaryBlur: Float,
    val mainAlpha: Float,
    val secondaryAlpha: Float,
)

internal const val PlaybackFocusedSecondaryAlpha = 0.68f
private val FocusedLyricDepth = PlaybackLyricDepth(0f, 0f, 1f, PlaybackFocusedSecondaryAlpha)

private val PlaybackLyricDepthStops = listOf(
    FocusedLyricDepth,
    PlaybackLyricDepth(1.2f, 0.8f, 0.50f, 0.28f),
    PlaybackLyricDepth(2.2f, 1.4f, 0.42f, 0.22f),
    PlaybackLyricDepth(3f, 1.8f, 0.35f, 0.18f),
)

private val LandscapeLyricDepthStops = listOf(
    FocusedLyricDepth,
    PlaybackLyricDepth(1.1f, 0.7f, 0.50f, 0.28f),
    PlaybackLyricDepth(1.9f, 1.2f, 0.42f, 0.22f),
    PlaybackLyricDepth(2.5f, 1.5f, 0.35f, 0.18f),
)

internal const val LandscapeLyricFocusFraction = 0.39f

internal fun landscapeLyricDistance(center: Float, viewportStart: Int, viewportEnd: Int): Float {
    val height = (viewportEnd - viewportStart).coerceAtLeast(1)
    val anchor = viewportStart + height * LandscapeLyricFocusFraction
    val offset = abs(center - anchor)
    // Scroll offsets are rounded to whole pixels and may leave a subpixel residual at rest.
    return if (offset <= 1f) 0f else offset / (height * 0.22f)
}

internal fun playbackLyricDepth(distance: Float, landscape: Boolean = false): PlaybackLyricDepth {
    val stops = if (landscape) LandscapeLyricDepthStops else PlaybackLyricDepthStops
    val step = distance.coerceIn(0f, stops.lastIndex.toFloat())
    val lower = step.toInt()
    val from = stops[lower]
    if (step == lower.toFloat()) return from

    val to = stops[lower + 1]
    val fraction = step - lower
    val progress = fraction * fraction * (3f - 2f * fraction)
    return PlaybackLyricDepth(
        mainBlur = lerp(from.mainBlur, to.mainBlur, progress),
        secondaryBlur = lerp(from.secondaryBlur, to.secondaryBlur, progress),
        mainAlpha = lerp(from.mainAlpha, to.mainAlpha, progress),
        secondaryAlpha = lerp(from.secondaryAlpha, to.secondaryAlpha, progress),
    )
}

internal fun List<LyricLine>.currentPlaybackLine(position: Long): Int {
    var start = 0
    var end = size
    while (start < end) {
        val middle = start + (end - start) / 2
        if (this[middle].startTime <= position) start = middle + 1 else end = middle
    }
    return start - 1
}
