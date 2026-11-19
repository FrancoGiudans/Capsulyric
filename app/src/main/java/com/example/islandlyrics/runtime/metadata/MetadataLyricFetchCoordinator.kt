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

package com.example.islandlyrics.runtime.metadata

import android.content.Context
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.lyrics.source.LocalLyricSource
import com.example.islandlyrics.lyrics.source.OnlineLyricSource

internal fun hasCurrentSuperLyricTimeline(
    expected: LyricRepository.MediaInfo,
    current: LyricRepository.MediaInfo?,
    parsed: LyricRepository.ParsedLyricsInfo?
): Boolean = current != null && expected.packageName == current.packageName &&
    expected.title == current.title && expected.artist == current.artist && expected.mediaId == current.mediaId &&
    parsed?.apiPath == "SuperLyric" && parsed.timelineCapability == LyricRepository.TimelineCapability.MULTI_LINE &&
    parsed.lines.any { it.startTime > 0 || it.endTime > it.startTime }

class MetadataLyricFetchCoordinator(
    private val context: Context,
    private val localLyricSource: LocalLyricSource,
    private val onlineLyricSource: OnlineLyricSource
) {
    fun onMetadataChanged(
        info: LyricRepository.MediaInfo,
        trackChanged: Boolean
    ) {
        val rule = ParserRuleHelper.getRuleForPackage(context, info.packageName)
            ?: ParserRuleHelper.createDefaultRule(info.packageName)
        val repo = LyricRepository.getInstance()

        AppLogger.getInstance().log(
            TAG,
            "Metadata Change: ${info.title} (${info.packageName}) | Rule: Active | Online: ${rule.useOnlineLyrics} | Car: ${rule.usesCarProtocol}"
        )

        if (!trackChanged) return

        val markResolveFinished = { found: Boolean ->
            if (found) {
                repo.updateLyricResolveState(LyricRepository.LyricResolveState.FOUND)
            } else {
                repo.reportLyricResolveFailed()
            }
        }

        onlineLyricSource.fetchImportedFor(info) { imported ->
            if (imported) {
                markResolveFinished(true)
            } else if (rule.useOnlineLyrics) {
                if (rule.useLocalLyrics) {
                    localLyricSource.fetchFor(info.title, info.artist, info.packageName) { found ->
                        if (found) {
                            repo.updateLyricResolveState(LyricRepository.LyricResolveState.FOUND)
                        } else if (!rule.usesCarProtocol) {
                            AppLogger.getInstance().log(TAG, "[${info.packageName}] Local miss, triggering online fetch...")
                            fetchOnlineUnlessSuperLyricResolved(info, markResolveFinished)
                        } else {
                            repo.reportLyricResolveFailed()
                        }
                    }
                } else if (!rule.usesCarProtocol) {
                    AppLogger.getInstance().log(TAG, "[${info.packageName}] Non-CarProtocol app, triggering fetch...")
                    fetchOnlineUnlessSuperLyricResolved(info, markResolveFinished)
                } else {
                    AppLogger.getInstance().log(TAG, "[${info.packageName}] CarProtocol app, waiting for lyric observer trigger...")
                }
            } else if (rule.useLocalLyrics) {
                localLyricSource.fetchFor(info.title, info.artist, info.packageName) { found ->
                    if (found) {
                        repo.updateLyricResolveState(LyricRepository.LyricResolveState.FOUND)
                    } else {
                        repo.reportLyricResolveFailed()
                    }
                }
            }
        }
    }

    private fun fetchOnlineUnlessSuperLyricResolved(info: LyricRepository.MediaInfo, onResolve: (Boolean) -> Unit) {
        val repo = LyricRepository.getInstance()
        val current = repo.liveMetadata.value ?: return
        if (current.packageName != info.packageName || current.title != info.title ||
            current.artist != info.artist || current.mediaId != info.mediaId) return
        val activeRule = ParserRuleHelper.getRuleForPackage(context, info.packageName)
            ?: ParserRuleHelper.createDefaultRule(info.packageName)
        if (activeRule.enabled && activeRule.useSuperLyricApi && hasCurrentSuperLyricTimeline(info, current, repo.liveParsedLyrics.value)) {
            onResolve(true)
            return
        }
        onlineLyricSource.fetchFor(info.title, info.artist, info.packageName, onResolve = onResolve)
    }

    private companion object {
        private const val TAG = "MetadataLyricFetch"
    }
}
