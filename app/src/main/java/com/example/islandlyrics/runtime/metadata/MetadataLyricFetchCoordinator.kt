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

        if (rule.useOnlineLyrics) {
            if (rule.useLocalLyrics) {
                localLyricSource.fetchFor(info.title, info.artist, info.packageName) { found ->
                    if (found) {
                        repo.updateLyricResolveState(LyricRepository.LyricResolveState.FOUND)
                    } else if (!rule.usesCarProtocol) {
                        AppLogger.getInstance().log(TAG, "[${info.packageName}] Local miss, triggering online fetch...")
                        onlineLyricSource.fetchFor(info.title, info.artist, info.packageName, onResolve = markResolveFinished)
                    } else {
                        repo.reportLyricResolveFailed()
                    }
                }
            } else if (!rule.usesCarProtocol) {
                AppLogger.getInstance().log(TAG, "[${info.packageName}] Non-CarProtocol app, triggering fetch...")
                onlineLyricSource.fetchFor(info.title, info.artist, info.packageName, onResolve = markResolveFinished)
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

    private companion object {
        private const val TAG = "MetadataLyricFetch"
    }
}
