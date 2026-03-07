package com.example.islandlyrics.data.lyric

import android.content.Context
import com.example.islandlyrics.utils.AppLogger
import com.example.islandlyrics.service.LyricService
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.LyricRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    private val scope      = CoroutineScope(Dispatchers.Main + Job())
    private var fetchJob: Job? = null

    // Track the last request so we can discard stale results
    private var pendingTitle  = ""
    private var pendingArtist = ""

    /**
     * Triggers an online lyric fetch for the given track.
     * A concurrent fetch for a previous track is cancelled automatically.
     */
    fun fetchFor(title: String, artist: String, packageName: String) {
        val rule = ParserRuleHelper.getRuleForPackage(context, packageName)
                   ?: ParserRuleHelper.createDefaultRule(packageName)

        if (!rule.useOnlineLyrics) {
            AppLogger.getInstance().d(TAG, "[$packageName] Online lyrics disabled by rule — skipped")
            return
        }

        if (title.isBlank() || artist.isBlank()) {
            AppLogger.getInstance().log(TAG, "Missing title/artist — cannot fetch")
            return
        }

        // Cancel any in-flight request (e.g. rapid song skips)
        fetchJob?.cancel()
        pendingTitle  = title
        pendingArtist = artist

        AppLogger.getInstance().i(TAG, "🚀 Online fetch: $title - $artist [$packageName]")

        fetchJob = scope.launch {
            try {
                val result = fetcher.fetchBestLyrics(title, artist)

                // Staleness check: ensure the song hasn't changed while we were fetching
                val current = LyricRepository.getInstance().liveMetadata.value
                if (current?.title != pendingTitle || current.artist != pendingArtist) {
                    AppLogger.getInstance().i(TAG,
                        "⚠️ Stale fetch discarded (song changed). Expected: $pendingTitle")
                    return@launch
                }

                if (result != null &&
                    result.parsedLines != null &&
                    result.parsedLines.isNotEmpty())
                {
                    AppLogger.getInstance().i(TAG,
                        "✅ Online fetch OK [${result.api}] syllable=${result.hasSyllable}")
                    LyricRepository.getInstance()
                        .updateParsedLyrics(result.parsedLines, result.hasSyllable)
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
