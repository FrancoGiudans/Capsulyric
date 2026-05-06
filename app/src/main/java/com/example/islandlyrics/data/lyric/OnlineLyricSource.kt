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
                    if (cacheHit.result.missingRequestedSidecars(rule)) {
                        AppLogger.getInstance().i(
                            TAG,
                            "♻️ Online lyric cache missing requested sidecars [${cacheHit.result.api}] — refreshing"
                        )
                    } else {
                        AppLogger.getInstance().i(
                            TAG,
                            "♻️ Online lyric cache hit [${cacheHit.result.api}] custom=${cacheHit.hasCustomMatch}"
                        )
                        val lines = cacheHit.result.withSidecars(rule)
                        LyricRepository.getInstance().updateParsedLyrics(
                            lines = lines,
                            hasSyllable = cacheHit.result.hasSyllable,
                            sourceLabel = "${cacheHit.result.api} · Cache",
                            apiPath = "Online Cache"
                        )
                        return@launch
                    }
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
                    val lines = result.withSidecars(rule)
                    val cacheResult = result.copy(parsedLines = lines)
                    val currentMetadata = LyricRepository.getInstance().liveMetadata.value
                    if (currentMetadata?.packageName == packageName) {
                        withContext(Dispatchers.IO) {
                            cacheStore.saveLyricResult(
                                mediaInfo = currentMetadata,
                                queryTitle = queryTitle,
                                queryArtist = queryArtist,
                                result = cacheResult
                            )
                        }
                    }
                    AppLogger.getInstance().i(TAG,
                        "✅ Online fetch OK [${result.api}] syllable=${result.hasSyllable} fallback=${outcome.usedCleanTitleFallback}")
                    LyricRepository.getInstance()
                        .updateParsedLyrics(
                            lines = lines,
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

    private fun OnlineLyricFetcher.LyricResult.withSidecars(
        rule: com.example.islandlyrics.data.ParserRule
    ): List<OnlineLyricFetcher.LyricLine> {
        val lines = parsedLines.orEmpty()
        if (lines.isEmpty()) return emptyList()
        val sidecars = buildSidecars(rule)
        if (sidecars.translationByTime.isEmpty() && sidecars.romanByTime.isEmpty()) return lines

        return lines.map { line ->
            line.copy(
                translation = sidecars.translationByTime.closestText(line.startTime) ?: line.translation,
                roma = sidecars.romanByTime.closestText(line.startTime) ?: line.roma
            )
        }
    }

    private data class SidecarTimeline(
        val translationByTime: Map<Long, String>,
        val romanByTime: Map<Long, String>
    )

    private fun OnlineLyricFetcher.LyricResult.buildSidecars(
        rule: com.example.islandlyrics.data.ParserRule
    ): SidecarTimeline {
        val translationByTime = if (rule.receiveOnlineTranslation) {
            translationLyrics?.let { parseSidecarLrc(it) }.orEmpty()
        } else {
            emptyMap()
        }
        val romanByTime = if (rule.receiveOnlineRomanization) {
            romanLyrics?.let { parseSidecarLrc(it) }.orEmpty()
        } else {
            emptyMap()
        }
        return SidecarTimeline(translationByTime, romanByTime)
    }

    private fun Map<Long, String>.closestText(time: Long): String? {
        if (isEmpty()) return null
        this[time]?.let { return it.takeIf(String::isNotBlank) }
        return entries
            .minByOrNull { kotlin.math.abs(it.key - time) }
            ?.takeIf { kotlin.math.abs(it.key - time) <= SIDECAR_TIME_TOLERANCE_MS }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseSidecarLrc(content: String): Map<Long, String> {
        val lrcLines = parseLrcTimestampLines(content)
        if (lrcLines.isNotEmpty()) return lrcLines

        return parseQrcTimestampLines(content)
    }

    private fun parseLrcTimestampLines(content: String): Map<Long, String> {
        val timestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")
        return content.lineSequence()
            .mapNotNull { rawLine ->
                val matches = timestampRegex.findAll(rawLine).toList()
                if (matches.isEmpty()) return@mapNotNull null
                val text = rawLine.replace(timestampRegex, "").trim()
                if (text.isBlank()) return@mapNotNull null
                matches.map { match -> match.toMillis() to text }
            }
            .flatten()
            .toMap()
    }

    private fun parseQrcTimestampLines(content: String): Map<Long, String> {
        val lineHeaderRegex = Regex("""\[(\d+),(\d+)]""")
        val wordTokenRegex = Regex("""(?:<|\()\d+,\d+(?:,\d+)?(?:>|\))""")
        val headers = lineHeaderRegex.findAll(content).toList()
        if (headers.isEmpty()) return emptyMap()

        return headers.mapIndexedNotNull { index, match ->
            val startTime = match.groupValues[1].toLongOrNull() ?: return@mapIndexedNotNull null
            val segmentStart = match.range.last + 1
            val segmentEnd = headers.getOrNull(index + 1)?.range?.first ?: content.length
            if (segmentStart >= segmentEnd) return@mapIndexedNotNull null

            val text = content.substring(segmentStart, segmentEnd)
                .replace(wordTokenRegex, "")
                .replace(Regex("""\[[^\]]+]"""), "")
                .trim()
            if (text.isBlank()) null else startTime to text
        }.toMap()
    }

    private fun MatchResult.toMillis(): Long {
        val minutes = groupValues[1].toLongOrNull() ?: 0L
        val seconds = groupValues[2].toLongOrNull() ?: 0L
        val fraction = groupValues.getOrNull(3).orEmpty()
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.times(100L) ?: 0L
            2 -> fraction.toLongOrNull()?.times(10L) ?: 0L
            else -> fraction.take(3).toLongOrNull() ?: 0L
        }
        return minutes * 60_000L + seconds * 1000L + millis
    }

    private fun OnlineLyricFetcher.LyricResult.missingRequestedSidecars(
        rule: com.example.islandlyrics.data.ParserRule
    ): Boolean {
        val providerSupportsSidecars = provider in listOf(OnlineLyricProvider.QQMusic, OnlineLyricProvider.Netease)
        if (!providerSupportsSidecars) return false
        val missingTranslation = rule.receiveOnlineTranslation && translationLyrics.isNullOrBlank()
        val missingRomanization = rule.receiveOnlineRomanization && romanLyrics.isNullOrBlank()
        return missingTranslation || missingRomanization
    }

    companion object {
        private const val TAG = "OnlineLyricSource"
        private const val SIDECAR_TIME_TOLERANCE_MS = 1500L
    }
}
