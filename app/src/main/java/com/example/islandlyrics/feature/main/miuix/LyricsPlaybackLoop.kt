/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@ConsistentCopyVisibility
internal data class LyricLoopRange private constructor(
    val start: Long,
    val end: Long,
    private val endsWithTrack: Boolean,
) {
    private val preroll get() = minOf(100L, (end - start) / 4)
    private val seekTolerance get() = minOf(500L, (end - start) / 2)

    fun contains(position: Long): Boolean = position >= start - preroll && position < end

    fun reachedStart(position: Long): Boolean =
        contains(position) && position <= start + seekTolerance

    fun needsRewind(position: Long): Boolean {
        // At the end of the track, leave time for the external player to receive the seek.
        val rewindAt = if (endsWithTrack) end - minOf(48L, (end - start) / 8) else end
        return position >= rewindAt || position < start - preroll
    }

    companion object {
        fun from(line: LyricLine, duration: Long): LyricLoopRange? {
            val end = minOf(line.endTime, duration)
            return if (line.startTime >= 0 && end > line.startTime) {
                LyricLoopRange(line.startTime, end, endsWithTrack = end == duration)
            } else {
                null
            }
        }
    }
}

private const val LoopSeekTimeoutMillis = 3_000L

@Composable
internal fun RunLyricsPlaybackLoop(
    range: LyricLoopRange?,
    position: State<Long?>,
    playing: Boolean,
    onSeek: (start: Long, initial: Boolean) -> Boolean,
    onSeekFailed: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isPlaying by rememberUpdatedState(playing)
    val seek by rememberUpdatedState(onSeek)
    val seekFailed by rememberUpdatedState(onSeekFailed)
    LaunchedEffect(range, position, lifecycle) {
        if (range == null) return@LaunchedEffect
        var initial = true
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (!initial) {
                    snapshotFlow {
                        isPlaying && position.value?.let(range::needsRewind) == true
                    }.first { it }
                }
                if (!seek(range.start, initial)) {
                    seekFailed()
                    return@repeatOnLifecycle
                }
                initial = false
                val confirmed = withTimeoutOrNull(LoopSeekTimeoutMillis) {
                    snapshotFlow { position.value?.let(range::reachedStart) == true }.first { it }
                }
                if (confirmed == null) {
                    seekFailed()
                    return@repeatOnLifecycle
                }
            }
        }
    }
}
