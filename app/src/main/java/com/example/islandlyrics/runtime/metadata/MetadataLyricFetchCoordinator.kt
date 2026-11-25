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
import com.example.islandlyrics.lyrics.online.selection.CandidateMatcher
import com.example.islandlyrics.lyrics.source.LocalLyricSource
import com.example.islandlyrics.lyrics.source.OnlineLyricSource
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.rules.ParserRuleHelper

internal fun hasCurrentSuperLyricTimeline(
    expected: LyricRepository.MediaInfo,
    current: LyricRepository.MediaInfo?,
    parsed: LyricRepository.ParsedLyricsInfo?
): Boolean = current != null && expected.packageName == current.packageName &&
    expected.title == current.title && expected.artist == current.artist && expected.mediaId == current.mediaId &&
    parsed?.apiPath == "SuperLyric" && parsed.timelineCapability == LyricRepository.TimelineCapability.MULTI_LINE &&
    parsed.lines.any { it.startTime > 0 || it.endTime > it.startTime }

internal fun needsMetadataStabilizationDelay(info: LyricRepository.MediaInfo): Boolean =
    info.duration <= 0L && info.album.isBlank()

/**
 * Checks whether [current] is still the same song as [expected] when launching
 * online fetch after async local/imported checks.
 *
 * Players frequently emit `mediaId = ""` on the first frame and populate the
 * real `mediaId` (e.g. `"900463142"`) a few milliseconds later for the SAME
 * track; treating `"" != "900463142"` as a track change silently drops the
 * entire online lyric request!
 */
internal fun isSameTrackForOnlineFetch(
    expected: LyricRepository.MediaInfo,
    current: LyricRepository.MediaInfo?
): Boolean {
    if (current == null) return false
    if (expected.packageName != current.packageName ||
        expected.title != current.title ||
        expected.artist != current.artist
    ) {
        return false
    }
    if (expected.mediaId.isNotBlank() &&
        current.mediaId.isNotBlank() &&
        expected.mediaId != current.mediaId
    ) {
        return false
    }
    return true
}

internal fun isMetadataEnrichedForSameTrack(
    previous: LyricRepository.MediaInfo?,
    updated: LyricRepository.MediaInfo
): Boolean {
    if (previous == null) return false
    if (previous.packageName != updated.packageName ||
        previous.title != updated.title ||
        previous.artist != updated.artist
    ) {
        return false
    }
    val gainedDuration = previous.duration <= 0L && updated.duration > 0L
    val gainedAlbum = previous.album.isBlank() && updated.album.isNotBlank()
    return gainedDuration || gainedAlbum
}

internal fun shouldRefetchOnMetadataEnriched(
    previous: LyricRepository.MediaInfo?,
    updated: LyricRepository.MediaInfo,
    isOnlineFetching: Boolean,
    resolveState: LyricRepository.LyricResolveState?,
    currentParsed: LyricRepository.ParsedLyricsInfo?,
    lastMatchedDurationMs: Long?
): Boolean {
    if (!isMetadataEnrichedForSameTrack(previous, updated)) return false
    // Never interrupt an active online fetch mid-flight; let it complete first
    // (OnlineLyricSource already waits briefly for initial duration inside its coroutine).
    if (isOnlineFetching) return false
    if (resolveState == LyricRepository.LyricResolveState.NOT_FOUND) return true
    val isOnlineResult = currentParsed?.apiPath == "Online API" || currentParsed?.apiPath == "Online Cache"
    if (!isOnlineResult) return false
    if (updated.duration > 0L && lastMatchedDurationMs != null && lastMatchedDurationMs > 0L &&
        !CandidateMatcher.isDurationCompatible(updated.duration, lastMatchedDurationMs)
    ) {
        return true
    }
    return false
}

class MetadataLyricFetchCoordinator(
    private val context: Context,
    private val localLyricSource: LocalLyricSource,
    private val onlineLyricSource: OnlineLyricSource
) {
    private var lastFetchedMetadata: LyricRepository.MediaInfo? = null
    private var enrichedRefetchTrackKey: String? = null

    fun onMetadataChanged(
        info: LyricRepository.MediaInfo,
        trackChanged: Boolean
    ) {
        val rule = ParserRuleHelper.getRuleForPackage(context, info.packageName)
            ?: ParserRuleHelper.createDefaultRule(info.packageName)
        val repo = LyricRepository.getInstance()
        val trackKey = trackKeyOf(info)

        AppLogger.getInstance().log(
            TAG,
            "Metadata Change: ${info.title} (${info.packageName}) | Rule: Active | Online: ${rule.useOnlineLyrics} | Car: ${rule.usesCarProtocol} | dur=${info.duration} | album=${info.album}"
        )

        if (!trackChanged) {
            if (rule.useOnlineLyrics &&
                !rule.usesCarProtocol &&
                enrichedRefetchTrackKey != trackKey &&
                shouldRefetchOnMetadataEnriched(
                    previous = lastFetchedMetadata,
                    updated = info,
                    isOnlineFetching = onlineLyricSource.isFetching(),
                    resolveState = repo.liveLyricResolveState.value,
                    currentParsed = repo.liveParsedLyrics.value,
                    lastMatchedDurationMs = onlineLyricSource.lastMatchedDurationMs()
                )
            ) {
                enrichedRefetchTrackKey = trackKey
                val hasDurationConflict = info.duration > 0L &&
                    onlineLyricSource.lastMatchedDurationMs()?.let { matchedDur ->
                        matchedDur > 0L && !CandidateMatcher.isDurationCompatible(info.duration, matchedDur)
                    } == true
                AppLogger.getInstance().log(
                    TAG,
                    "Metadata enriched after initial fetch (dur=${info.duration}, album=${info.album}, conflict=$hasDurationConflict), upgrading online fetch"
                )
                lastFetchedMetadata = info
                fetchOnlineUnlessSuperLyricResolved(info, forceRefresh = hasDurationConflict) { found ->
                    if (found) {
                        repo.updateLyricResolveState(LyricRepository.LyricResolveState.FOUND)
                    } else {
                        repo.reportLyricResolveFailed()
                    }
                }
            }
            return
        }

        enrichedRefetchTrackKey = null
        lastFetchedMetadata = info

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
                            fetchOnlineUnlessSuperLyricResolved(info, forceRefresh = false, markResolveFinished)
                        } else {
                            repo.reportLyricResolveFailed()
                        }
                    }
                } else if (!rule.usesCarProtocol) {
                    AppLogger.getInstance().log(TAG, "[${info.packageName}] Non-CarProtocol app, triggering fetch...")
                    fetchOnlineUnlessSuperLyricResolved(info, forceRefresh = false, markResolveFinished)
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

    private fun fetchOnlineUnlessSuperLyricResolved(
        info: LyricRepository.MediaInfo,
        forceRefresh: Boolean = false,
        onResolve: (Boolean) -> Unit
    ) {
        val repo = LyricRepository.getInstance()
        val current = repo.liveMetadata.value ?: return
        if (!isSameTrackForOnlineFetch(info, current)) return
        val activeRule = ParserRuleHelper.getRuleForPackage(context, current.packageName)
            ?: ParserRuleHelper.createDefaultRule(current.packageName)
        if (activeRule.enabled && activeRule.useSuperLyricApi && hasCurrentSuperLyricTimeline(current, current, repo.liveParsedLyrics.value)) {
            onResolve(true)
            return
        }
        lastFetchedMetadata = current
        onlineLyricSource.fetchFor(
            title = current.title,
            artist = current.artist,
            packageName = current.packageName,
            forceRefresh = forceRefresh,
            onResolve = onResolve
        )
    }

    private fun trackKeyOf(info: LyricRepository.MediaInfo): String =
        "${info.packageName}|${info.title}|${info.artist}"

    private companion object {
        private const val TAG = "MetadataLyricFetch"
    }
}
