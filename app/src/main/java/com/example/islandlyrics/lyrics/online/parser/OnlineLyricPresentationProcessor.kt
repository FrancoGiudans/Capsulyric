/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.lyrics.online.parser

import android.content.Context
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.rules.ParserRule

internal data class ProcessedOnlineLyrics(
    val lines: List<OnlineLyricFetcher.LyricLine>,
    val writers: List<String>,
    val removedInfoLineCount: Int
)

/**
 * Single presentation-layer pipeline applied to online lyric results right before
 * publishing to [LyricRepository]. Never mutates the cached raw [OnlineLyricFetcher.LyricResult].
 *
 * Order:
 * 1. Merge translation/romanization sidecars (dropping sidecar noise when filtering is enabled).
 * 2. Filter head/tail credit lines and copyright notices, while extracting songwriter credits.
 * 3. Detect duet vocalists (TTML `ttm:agent` or text prefixes), assign left/right alignment,
 *    and strip speaker prefixes along with matching syllable offsets.
 */
internal object OnlineLyricPresentationProcessor {

    fun prepare(
        context: Context,
        result: OnlineLyricFetcher.LyricResult,
        rule: ParserRule,
        mediaInfo: LyricRepository.MediaInfo?
    ): ProcessedOnlineLyrics {
        val prefs = AppPreferences.of(context)
        val filterInfoLines = AppPreferences.isOnlineLyricInfoFilterEnabled(prefs)
        val duetAlignmentEnabled = AppPreferences.isFullscreenPlaybackDuetAlignmentEnabled(prefs)
        val trackContext = TrackFilterContext(
            title = mediaInfo?.title.orEmpty(),
            artist = mediaInfo?.artist.orEmpty(),
            rawTitle = mediaInfo?.rawTitle.orEmpty(),
            rawArtist = mediaInfo?.rawArtist.orEmpty(),
            matchedTitle = result.matchedTitle,
            matchedArtist = result.matchedArtist
        )
        return prepare(
            result = result,
            rule = rule,
            trackContext = trackContext,
            filterInfoLines = filterInfoLines,
            duetAlignmentEnabled = duetAlignmentEnabled
        )
    }

    fun prepare(
        result: OnlineLyricFetcher.LyricResult,
        rule: ParserRule,
        trackContext: TrackFilterContext = TrackFilterContext(
            matchedTitle = result.matchedTitle,
            matchedArtist = result.matchedArtist
        ),
        filterInfoLines: Boolean = true,
        duetAlignmentEnabled: Boolean = true
    ): ProcessedOnlineLyrics {
        val merged = OnlineLyricSidecarMerger.withSidecars(
            result = result,
            rule = rule,
            filterInfoLines = filterInfoLines
        )
        val filtered = OnlineLyricInfoFilter.process(
            lines = merged,
            rawLyrics = result.lyrics,
            context = trackContext,
            filterInfoLines = filterInfoLines
        )
        val aligned = OnlineLyricDuetAligner.process(
            lines = filtered.lines,
            rawLyrics = result.lyrics,
            context = trackContext,
            enabled = duetAlignmentEnabled,
            stripPrefixes = true
        )
        return ProcessedOnlineLyrics(
            lines = aligned,
            writers = filtered.writers,
            removedInfoLineCount = filtered.removedCount
        )
    }
}
