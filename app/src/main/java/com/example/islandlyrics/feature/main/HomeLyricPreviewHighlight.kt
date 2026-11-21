/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main

import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.ui.overlay.floating.isFullySung
import com.example.islandlyrics.ui.overlay.floating.toLyricsClock
import com.example.islandlyrics.ui.overlay.model.LyricPresentation
import com.example.islandlyrics.ui.overlay.model.SecondaryTextMode
import com.example.islandlyrics.ui.overlay.model.WordProgressCalculator
import kotlinx.coroutines.delay

private const val LyricHighlightIntervalMs = 33L

@Composable
internal fun rememberHighlightedLyricPreview(
    preview: HomeLyricPreviewDisplay.Preview,
    wordLine: LyricLine?,
    color: Color,
    playbackState: PlaybackState?,
    fallbackPositionMs: Long?,
): AnnotatedString {
    val line = wordLine?.takeIf {
        !it.syllables.isNullOrEmpty() && preview.primary == it.text
    }
    val clock = if (line != null) {
        remember(playbackState, fallbackPositionMs) {
            playbackState.toLyricsClock(fallbackPositionMs)
        }
    } else {
        null
    }
    val progress = if (line != null && clock != null) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val endTime = remember(line) { line.syllables.orEmpty().maxOf { it.endTime } }
        var position by remember(line, clock) {
            mutableLongStateOf(clock.positionAt(SystemClock.elapsedRealtime()))
        }
        LaunchedEffect(line, clock, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                do {
                    position = clock.positionAt(SystemClock.elapsedRealtime())
                    if (clock.speed <= 0f || position >= endTime) break
                    delay(LyricHighlightIntervalMs)
                } while (true)
            }
        }
        remember(line, position) {
            WordProgressCalculator.compute(line.text, line.startTime, line.endTime, line.syllables, position)
        }
    } else {
        null
    }

    return remember(preview, progress, color) { highlightLyricPreview(preview, progress, color) }
}

internal fun highlightLyricPreview(
    preview: HomeLyricPreviewDisplay.Preview,
    progress: LyricPresentation.WordProgress?,
    color: Color,
): AnnotatedString = buildAnnotatedString {
    appendHighlightedLyric(preview.primary, progress, color)
    val unsungColor = color.copy(alpha = color.alpha * WordProgressCalculator.UNSUNG_ALPHA)
    preview.secondary?.let {
        append('\n')
        val start = length
        append(it.text)
        if (it.mode == SecondaryTextMode.NEXT_LYRIC) {
            addStyle(SpanStyle(color = unsungColor), start, length)
        }
    }
}

private fun AnnotatedString.Builder.appendHighlightedLyric(
    text: String,
    progress: LyricPresentation.WordProgress?,
    color: Color,
) {
    val offset = length
    append(text)
    if (progress != null && !progress.isFullySung()) {
        var lastEnd = 0
        for (syllable in progress.syllables) {
            val start = syllable.charStart.coerceIn(0, text.length)
            val end = syllable.charEnd.coerceIn(start, text.length)
            if (start == end) continue
            val alpha = WordProgressCalculator.highlightAlpha(syllable.progress)
            addStyle(SpanStyle(color = color.copy(alpha = color.alpha * alpha)), offset + start, offset + end)
            lastEnd = end
        }
        if (lastEnd < text.length) {
            val unsungColor = color.copy(alpha = color.alpha * WordProgressCalculator.UNSUNG_ALPHA)
            addStyle(SpanStyle(color = unsungColor), offset + lastEnd, length)
        }
    }
}
