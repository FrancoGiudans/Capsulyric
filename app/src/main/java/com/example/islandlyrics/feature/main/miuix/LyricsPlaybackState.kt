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

internal fun PlaybackState?.toLyricsClock(fallbackPosition: Long?): FloatingLyricsClock? {
    val playing = this?.state == PlaybackState.STATE_PLAYING
    val speed = this?.playbackSpeed?.takeIf { playing && it.isFinite() && it > 0f } ?: 0f
    if (this != null && position >= 0L && (!playing || lastPositionUpdateTime > 0L)) {
        return FloatingLyricsClock(position, lastPositionUpdateTime, speed)
    }
    return fallbackPosition?.let { FloatingLyricsClock(it, SystemClock.elapsedRealtime(), speed) }
}

internal data class PlaybackInterlude(val beforeLineIndex: Int, val startTime: Long, val endTime: Long)

internal fun List<LyricLine>.playbackInterludes(): Map<Int, PlaybackInterlude> {
    val first = firstOrNull() ?: return emptyMap()
    return buildMap {
        if (TimingGapDisplayResolver.supportsIntroCountdown(first.startTime)) {
            put(0, PlaybackInterlude(0, 0L, first.startTime))
        }
        var coveredUntil = first.startTime
        this@playbackInterludes.forEachIndexed { index, line ->
            TimingGapDisplayResolver.interludeStart(coveredUntil, line.startTime)?.let { start ->
                put(index, PlaybackInterlude(index, start, line.startTime))
            }
            coveredUntil = maxOf(coveredUntil, line.startTime, line.endTime)
        }
    }
}

internal fun Map<Int, PlaybackInterlude>.activeInterlude(currentLineIndex: Int, position: Long): PlaybackInterlude? =
    get(currentLineIndex + 1)?.takeIf { position >= it.startTime && position < it.endTime }

internal fun List<LyricLine>.currentPlaybackLine(position: Long): Int {
    var start = 0
    var end = size
    while (start < end) {
        val middle = start + (end - start) / 2
        if (this[middle].startTime <= position) start = middle + 1 else end = middle
    }
    return start - 1
}
