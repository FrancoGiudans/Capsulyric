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

        AppLogger.getInstance().log(
            TAG,
            "Metadata Change: ${info.title} (${info.packageName}) | Rule: Active | Online: ${rule.useOnlineLyrics} | Car: ${rule.usesCarProtocol}"
        )

        if (!trackChanged) return

        if (rule.useOnlineLyrics) {
            if (rule.useLocalLyrics) {
                localLyricSource.fetchFor(info.title, info.artist, info.packageName) { found ->
                    if (!found && !rule.usesCarProtocol) {
                        AppLogger.getInstance().log(TAG, "[${info.packageName}] Local miss, triggering online fetch...")
                        onlineLyricSource.fetchFor(info.title, info.artist, info.packageName)
                    }
                }
            } else if (!rule.usesCarProtocol) {
                AppLogger.getInstance().log(TAG, "[${info.packageName}] Non-CarProtocol app, triggering fetch...")
                onlineLyricSource.fetchFor(info.title, info.artist, info.packageName)
            } else {
                AppLogger.getInstance().log(TAG, "[${info.packageName}] CarProtocol app, waiting for lyric observer trigger...")
            }
        } else if (rule.useLocalLyrics) {
            localLyricSource.fetchFor(info.title, info.artist, info.packageName) { _ -> }
        }
    }

    private companion object {
        private const val TAG = "MetadataLyricFetch"
    }
}
