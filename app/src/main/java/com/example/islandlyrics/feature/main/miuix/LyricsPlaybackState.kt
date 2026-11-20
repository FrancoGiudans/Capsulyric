/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.media.session.PlaybackState
import android.os.SystemClock
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.overlay.display.TimingGapDisplayResolver
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsClock
import com.example.islandlyrics.ui.overlay.model.SecondaryTextResolver

internal fun LyricRepository.ParsedLyricsInfo?.hasCompleteLyrics(): Boolean =
    this?.timelineCapability == LyricRepository.TimelineCapability.MULTI_LINE &&
        lines.any { it.isTimedLyric() }

internal fun LyricRepository.ParsedLyricsInfo?.playbackLines(): List<LyricLine> =
    if (this?.timelineCapability == LyricRepository.TimelineCapability.MULTI_LINE) {
        lines.filter { it.isTimedLyric() }.sortedBy { it.startTime }
    } else {
        emptyList()
    }

private fun LyricLine.isTimedLyric() = startTime >= 0 && !SecondaryTextResolver.isPlaceholder(text)

internal fun PlaybackState?.toLyricsClock(fallbackPosition: Long?): FloatingLyricsClock? {
    val playing = this?.state == PlaybackState.STATE_PLAYING
    val speed = this?.playbackSpeed?.takeIf { playing && it.isFinite() && it > 0f } ?: 0f
    if (this != null && position >= 0L && (!playing || lastPositionUpdateTime > 0L)) {
        return FloatingLyricsClock(position, lastPositionUpdateTime, speed)
    }
    return fallbackPosition?.let { FloatingLyricsClock(it, SystemClock.elapsedRealtime(), speed) }
}

internal sealed interface PlaybackTimelineItem {
    val startTime: Long

    data class Verse(val line: LyricLine) : PlaybackTimelineItem {
        override val startTime: Long get() = line.startTime
    }

    data class Interlude(override val startTime: Long, val endTime: Long) : PlaybackTimelineItem
}

internal fun List<LyricLine>.withInterludes(): List<PlaybackTimelineItem> = buildList {
    val first = firstOrNull() ?: return@buildList
    if (TimingGapDisplayResolver.supportsIntroCountdown(first.startTime)) {
        add(PlaybackTimelineItem.Interlude(0L, first.startTime))
    }
    var coveredUntil = first.startTime
    for (line in this@withInterludes) {
        TimingGapDisplayResolver.interludeStart(coveredUntil, line.startTime)?.let { start ->
            add(PlaybackTimelineItem.Interlude(start, line.startTime))
        }
        add(PlaybackTimelineItem.Verse(line))
        coveredUntil = maxOf(coveredUntil, line.startTime, line.endTime)
    }
}

internal fun List<PlaybackTimelineItem>.currentPlaybackLine(position: Long): Int {
    var start = 0
    var end = size
    while (start < end) {
        val middle = start + (end - start) / 2
        if (this[middle].startTime <= position) start = middle + 1 else end = middle
    }
    return start - 1
}
