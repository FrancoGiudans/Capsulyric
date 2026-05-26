package com.example.islandlyrics.data.lyric

import android.content.Context
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.ParserRuleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalLyricSource(private val context: Context) {

    private val directoryManager = LocalLyricDirectoryManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var searchJob: Job? = null

    fun fetchFor(
        title: String,
        artist: String,
        packageName: String,
        onResult: (found: Boolean) -> Unit
    ) {
        val rule = ParserRuleHelper.getRuleForPackage(context, packageName)
            ?: ParserRuleHelper.createDefaultRule(packageName)

        if (!rule.useLocalLyrics) {
            onResult(false)
            return
        }

        if (!directoryManager.hasDirectories()) {
            AppLogger.getInstance().d(TAG, "No local lyric directories configured")
            onResult(false)
            return
        }

        searchJob?.cancel()
        searchJob = scope.launch {
            try {
                val match = withContext(Dispatchers.IO) {
                    directoryManager.findMatch(title, artist)
                }

                if (match == null) {
                    AppLogger.getInstance().d(TAG, "No local match for: $title - $artist")
                    onResult(false)
                    return@launch
                }

                AppLogger.getInstance().i(TAG, "Local match found: ${match.originalName}")

                val parseResult = withContext(Dispatchers.IO) {
                    LocalLrcParser.parse(context, match.uri)
                }

                if (parseResult == null || parseResult.lines.isEmpty()) {
                    AppLogger.getInstance().e(TAG, "Failed to parse local file: ${match.originalName}")
                    onResult(false)
                    return@launch
                }

                val linesWithSidecars = if (parseResult.translationLines.isNotEmpty() || parseResult.romanLines.isNotEmpty()) {
                    parseResult.lines.map { line ->
                        line.copy(
                            translation = parseResult.translationLines[line.startTime],
                            roma = parseResult.romanLines[line.startTime]
                        )
                    }
                } else {
                    parseResult.lines
                }

                LyricRepository.getInstance().updateParsedLyrics(
                    lines = linesWithSidecars,
                    hasSyllable = parseResult.hasSyllable,
                    sourceLabel = "Local · ${match.originalName}",
                    apiPath = "Local LRC",
                    timelineCapability = LyricRepository.TimelineCapability.MULTI_LINE
                )

                AppLogger.getInstance().i(TAG, "Local lyrics loaded: ${linesWithSidecars.size} lines, syllable=${parseResult.hasSyllable}")
                onResult(true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                AppLogger.getInstance().d(TAG, "Local search cancelled")
            } catch (e: Exception) {
                AppLogger.getInstance().e(TAG, "Local search error: ${e.message}")
                onResult(false)
            }
        }
    }

    fun cancel() {
        searchJob?.cancel()
        searchJob = null
    }

    fun rebuildIndex() {
        scope.launch {
            directoryManager.rebuildIndex()
        }
    }

    companion object {
        private const val TAG = "LocalLyricSource"
    }
}
