package com.example.islandlyrics.data.lyric

import android.content.Context
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.service.LyricService
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.LyricRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OnlineLyricSource
 *
 * Encapsulates the online-lyric-fetch path that was previously inlined in
 * LyricService.  Responsibilities:
 *  - Receive a (title, artist, packageName) trigger
 *  - Guard against fetches disabled by per-app rule
 *  - Cancel in-flight requests when the song changes
 *  - Write results to [LyricRepository] (parsedLyrics + startProgressUpdater signal)
 *
 * Thread-safety: [fetchFor] and [cancel] are safe to call from any thread.
 */
class OnlineLyricSource(private val context: Context) {

    private val fetcher    = OnlineLyricFetcher()
    private val cacheStore = OnlineLyricCacheStore(context)
    private val scope      = CoroutineScope(Dispatchers.Main + Job())
    private var fetchJob: Job? = null

    // Track the last request so we can discard stale results
    private var pendingTitle  = ""
    private var pendingArtist = ""
    private var pendingPackageName = ""

    private fun resolveQuery(
        packageName: String,
        fallbackTitle: String,
        fallbackArtist: String
    ): Pair<String, String> {
        val rule = ParserRuleHelper.getRuleForPackage(context, packageName)
            ?: ParserRuleHelper.createDefaultRule(packageName)
        val liveMetadata = LyricRepository.getInstance().liveMetadata.value
        val metadata = if (liveMetadata?.packageName == packageName) liveMetadata else null
        return if (metadata != null) {
            cacheStore.resolveQuery(
                mediaInfo = metadata,
                fallbackTitle = fallbackTitle,
                fallbackArtist = fallbackArtist,
                useRawMetadata = rule.useRawMetadataForOnlineMatching
            )
        } else if (rule.useRawMetadataForOnlineMatching) {
            fallbackTitle to fallbackArtist
        } else {
            fallbackTitle to fallbackArtist
        }
    }

    /**
     * Triggers an online lyric fetch for the given track.
     * A concurrent fetch for a previous track is cancelled automatically.
     */
    fun fetchFor(title: String, artist: String, packageName: String) {
        if (OfflineModeManager.isEnabled(context)) {
            AppLogger.getInstance().i(TAG, "[$packageName] Offline mode enabled — skipped")
            cancel()
            return
        }

        val rule = ParserRuleHelper.getRuleForPackage(context, packageName)
                   ?: ParserRuleHelper.createDefaultRule(packageName)

        if (!rule.useOnlineLyrics) {
            AppLogger.getInstance().d(TAG, "[$packageName] Online lyrics disabled by rule — skipped")
            return
        }

        val (queryTitle, queryArtist) = resolveQuery(packageName, title, artist)

        if (queryTitle.isBlank() || queryArtist.isBlank()) {
            AppLogger.getInstance().log(TAG, "Missing title/artist — cannot fetch")
            return
        }

        // Cancel any in-flight request (e.g. rapid song skips)
        fetchJob?.cancel()
        pendingTitle  = queryTitle
        pendingArtist = queryArtist
        pendingPackageName = packageName

        AppLogger.getInstance().i(TAG, "🚀 Online fetch: $queryTitle - $queryArtist [$packageName]")

        fetchJob = scope.launch {
            try {
                val metadata = LyricRepository.getInstance().liveMetadata.value
                val cacheHit = if (metadata?.packageName == packageName) {
                    withContext(Dispatchers.IO) {
                        cacheStore.getCachedLyric(metadata, queryTitle, queryArtist)
                    }
                } else {
                    null
                }

                if (cacheHit != null) {
                    AppLogger.getInstance().i(
                        TAG,
                        "♻️ Online lyric cache hit [${cacheHit.result.api}] custom=${cacheHit.hasCustomMatch}"
                    )
                    LyricRepository.getInstance().updateParsedLyrics(
                        lines = cacheHit.result.parsedLines.orEmpty(),
                        hasSyllable = cacheHit.result.hasSyllable,
                        sourceLabel = "${cacheHit.result.api} · Cache",
                        apiPath = "Online Cache"
                    )
                    return@launch
                }

                val outcome = fetcher.fetchLyrics(
                    title = queryTitle,
                    artist = queryArtist,
                    providerOrderIds = if (rule.useSmartOnlineLyricSelection) {
                        OnlineLyricProvider.defaultIds()
                    } else {
                        rule.onlineLyricProviderOrder
                    },
                    useSmartSelection = rule.useSmartOnlineLyricSelection
                )
                val result = outcome.bestResult

                // Staleness check: ensure the song hasn't changed while we were fetching
                val current = LyricRepository.getInstance().liveMetadata.value
                val currentQuery = if (current?.packageName == pendingPackageName) {
                    resolveQuery(current.packageName, current.title, current.artist)
                } else {
                    null
                }
                if (currentQuery?.first != pendingTitle || currentQuery.second != pendingArtist) {
                    AppLogger.getInstance().i(TAG,
                        "⚠️ Stale fetch discarded (song changed). Expected: $pendingTitle")
                    return@launch
                }

                if (result != null &&
                    result.parsedLines != null &&
                    result.parsedLines.isNotEmpty())
                {
                    val currentMetadata = LyricRepository.getInstance().liveMetadata.value
                    if (currentMetadata?.packageName == packageName) {
                        withContext(Dispatchers.IO) {
                            cacheStore.saveLyricResult(
                                mediaInfo = currentMetadata,
                                queryTitle = queryTitle,
                                queryArtist = queryArtist,
                                result = result
                            )
                        }
                    }
                    AppLogger.getInstance().i(TAG,
                        "✅ Online fetch OK [${result.api}] syllable=${result.hasSyllable} fallback=${outcome.usedCleanTitleFallback}")
                    LyricRepository.getInstance()
                        .updateParsedLyrics(
                            lines = result.parsedLines,
                            hasSyllable = result.hasSyllable,
                            sourceLabel = result.api,
                            apiPath = "Online API"
                        )
                } else {
                    AppLogger.getInstance().i(TAG, "Online fetch: no usable result")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                AppLogger.getInstance().d(TAG, "Online fetch cancelled (song changed)")
            } catch (e: Exception) {
                AppLogger.getInstance().e(TAG, "Online fetch error: ${e.message}")
            }
        }
    }

    /** Cancel any in-flight fetch immediately (call on song change or service stop). */
    fun cancel() {
        fetchJob?.cancel()
        fetchJob = null
    }

    companion object {
        private const val TAG = "OnlineLyricSource"
    }
}
