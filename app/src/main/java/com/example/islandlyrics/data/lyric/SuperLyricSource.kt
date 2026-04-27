package com.example.islandlyrics.data.lyric

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.ParserRuleHelper
import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import com.hchen.superlyricapi.SuperLyricLine

/**
 * SuperLyricSource
 *
 * Handles the SuperLyric Xposed-module lyric push path on the 3.x API.
 * Responsibilities:
 *  - Register / unregister the ISuperLyricReceiver AIDL stub
 *  - Detect song changes from incoming SuperLyricData and immediately update
 *    metadata + album art (instead of waiting for a MediaSession callback)
 *  - Convert SuperLyricLine / SuperLyricWord (word-level) into LyricLine and push to the
 *    repository, so the online-lyric fetch can be skipped when data is already
 *    available
 *  - Delegate online-lyric fetching to [OnlineLyricSource] when needed
 *
 * This class is intentionally free of notification or UI concerns.
 */
class SuperLyricSource(
    private val context: Context,
    private val onlineLyricSource: OnlineLyricSource
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var started = false
    private var receiverRegistered = false

    // Deduplicate consecutive identical lyric lines
    private var lastLyric = ""

    // Cache app-display-names to avoid repeated PackageManager IPC
    private val appNameCache = HashMap<String, String>()

    private val registerReceiverRunnable = object : Runnable {
        override fun run() {
            if (!started || receiverRegistered) return

            try {
                SuperLyricHelper.registerReceiver(stub)
                receiverRegistered = true
                AppLogger.getInstance().log(TAG, "SuperLyricSource started — receiver registered")
            } catch (t: IllegalStateException) {
                AppLogger.getInstance().w(
                    TAG,
                    "SuperLyric service unavailable, retrying in ${REGISTER_RETRY_DELAY_MS}ms: ${t.message}"
                )
                mainHandler.postDelayed(this, REGISTER_RETRY_DELAY_MS)
            } catch (t: Throwable) {
                AppLogger.getInstance().e(TAG, "Failed to register SuperLyric receiver", t)
            }
        }
    }

    // ── ISuperLyricReceiver AIDL stub ─────────────────────────────────────────

    private val stub = object : ISuperLyricReceiver.Stub() {

        override fun onLyric(publisher: String?, data: SuperLyricData?) {
            if (data == null) {
                AppLogger.getInstance().d(TAG, "onLyric: null data — ignored")
                return
            }

            val liveMeta = LyricRepository.getInstance().liveMetadata.value
            val pkg = publisher?.takeIf { it.isNotBlank() } ?: liveMeta?.packageName.orEmpty()
            if (pkg.isBlank()) {
                AppLogger.getInstance().d(TAG, "onLyric: publisher missing and no active session — ignored")
                return
            }

            val rule = ParserRuleHelper.getRuleForPackage(context, pkg)
                       ?: ParserRuleHelper.createDefaultRule(pkg)

            if (!rule.useSuperLyricApi) {
                AppLogger.getInstance().d(TAG, "[$pkg] SuperLyric disabled by rule — skipped")
                return
            }

            // ── Phase 1: Verify song identity (rely entirely on MediaMonitorService for actual metadata/album art) ──
            val liveTitle = liveMeta?.title ?: ""
            val liveArtist = liveMeta?.artist ?: ""
            val livePkg = liveMeta?.packageName ?: ""

            val providedTitle = data.title.orEmpty()
            val providedArtist = data.artist.orEmpty()

            // 3.x API exposes lightweight title/artist fields only.
            // Playback state and progress remain fully owned by MediaSession.
            val isMatch = if (providedTitle.isNotBlank() || providedArtist.isNotBlank()) {
                val titleMatches = providedTitle.isBlank() || liveTitle.isBlank() || providedTitle.equals(liveTitle, ignoreCase = true)
                val artistMatches = providedArtist.isBlank() || liveArtist.isBlank() || providedArtist.equals(liveArtist, ignoreCase = true)
                pkg == livePkg && titleMatches && artistMatches
            } else {
                pkg == livePkg
            }

            if (!isMatch) {
                AppLogger.getInstance().d(TAG, "[$pkg] Lyric ignored. Mismatch w/ current session (${liveMeta?.title} - ${liveMeta?.artist}) vs ($providedTitle - $providedArtist)")
                return
            }

            // ── Phase 2: handle lyric text ────────────────────────────────────
            val lyricLine = data.lyric
            val lyric = lyricLine?.text.orEmpty()
            if (lyric.isBlank()) {
                AppLogger.getInstance().d(TAG, "[$pkg] Empty lyric line — ignored")
                return
            }

            // Instrumental / no-lyrics marker
            if (lyric.matches(".*(纯音乐|Instrumental|No lyrics|请欣赏|没有歌词).*".toRegex())) {
                AppLogger.getInstance().d(TAG, "Instrumental marker detected")
                // Do NOT clear it to "", otherwise the UI shows "Waiting for lyrics..." indefinitely.
                // Just pass it through so the user sees "纯音乐".
                LyricRepository.getInstance().updateLyric(lyric, getAppName(pkg), "SuperLyric")
                return
            }

            if (lyric == lastLyric) {
                AppLogger.getInstance().d(TAG, "Duplicate lyric — skipped")
                return
            }
            lastLyric = lyric

            // ── Phase 3: prepare parsed lyrics if word-level data is available ──
            val words = lyricLine?.words
            val parsedLines = if (!words.isNullOrEmpty()) {
                convertLineToParsedLyrics(lyric = lyricLine)
            } else null

            val appName = getAppName(pkg)
            val shouldFetchOnline = parsedLines == null && rule.useOnlineLyrics

            // Dispatch repository writes to main thread so they go through
            // LiveData.setValue() (synchronous) instead of postValue() (async).
            // postValue() silently merges consecutive calls, which drops lyrics
            // when SuperLyric pushes arrive in rapid succession on the Binder pool.
            mainHandler.post {
                LyricRepository.getInstance().updateLyric(lyric, appName, "SuperLyric")

                if (parsedLines != null) {
                    LyricRepository.getInstance().updateParsedLyrics(
                        lines = parsedLines,
                        hasSyllable = true,
                        sourceLabel = appName,
                        apiPath = "SuperLyric"
                    )
                    AppLogger.getInstance().d(
                        TAG,
                        "SuperLyric 3.x line converted: ${parsedLines.size} line(s), words=${words?.size}, translation=${data.translation != null}, secondary=${data.secondary != null}"
                    )
                }

                // ── Phase 4: decide whether to trigger online fetch ───────────
                if (shouldFetchOnline) {
                    onlineLyricSource.fetchFor(liveTitle, liveArtist, pkg)
                }
            }
        }

        override fun onStop(publisher: String?, data: SuperLyricData?) {
            AppLogger.getInstance().d(TAG, "onStop: ${publisher ?: data?.title ?: "unknown"}")
            // Only propagate the stop signal when MediaMonitorService agrees that nothing is
            // playing. If MediaSession still reports STATE_PLAYING (e.g. the module fired
            // prematurely), we skip writing to avoid overriding the authoritative state.
            val mediaSessionSaysPlaying = LyricRepository.getInstance().isPlaying.value ?: false
            if (!mediaSessionSaysPlaying) {
                // Already stopped from MediaMonitorService side — this is redundant but harmless.
                AppLogger.getInstance().d(TAG, "onStop: MediaSession already stopped, no-op")
            }
            // Regardless, reset the dedup cache so the next lyric push is never suppressed.
            lastLyric = ""
        }
    }

    // ── Public lifecycle ──────────────────────────────────────────────────────

    fun start() {
        started = true
        mainHandler.removeCallbacks(registerReceiverRunnable)
        mainHandler.post {
            registerReceiverRunnable.run()
        }
    }

    fun stop() {
        started = false
        mainHandler.removeCallbacks(registerReceiverRunnable)
        mainHandler.post {
            if (receiverRegistered) {
                try {
                    SuperLyricHelper.unregisterReceiver(stub)
                } catch (t: IllegalStateException) {
                    AppLogger.getInstance().w(TAG, "SuperLyric service unavailable during unregister: ${t.message}")
                } catch (t: Throwable) {
                    AppLogger.getInstance().e(TAG, "Failed to unregister SuperLyric receiver", t)
                }
            }
            receiverRegistered = false
            reset()
            AppLogger.getInstance().log(TAG, "SuperLyricSource stopped")
        }
    }

    private fun reset() {
        lastLyric  = ""
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Converts a 3.x [SuperLyricLine] and its optional companion lines into the
     * repository format used by the existing progress/highlighting pipeline.
     */
    private fun convertLineToParsedLyrics(
        lyric: SuperLyricLine
    ): List<OnlineLyricFetcher.LyricLine> {
        val words = lyric.words
        val baseStart = when {
            lyric.startTime > 0L -> lyric.startTime
            !words.isNullOrEmpty() -> words.first().startTime
            else -> 0L
        }
        val baseEnd = when {
            lyric.endTime > 0L -> lyric.endTime
            !words.isNullOrEmpty() -> words.last().endTime
            else -> baseStart
        }

        val syllables = buildList {
            words.orEmpty().forEach { word ->
                add(
                    OnlineLyricFetcher.SyllableInfo(
                        startTime = word.startTime,
                        endTime = word.endTime,
                        text = word.word
                    )
                )
            }
        }.takeIf { it.isNotEmpty() }

        return listOf(
            OnlineLyricFetcher.LyricLine(
                startTime = baseStart,
                endTime = maxOf(baseEnd, baseStart),
                text = lyric.text.ifBlank { words.orEmpty().joinToString("") { it.word } },
                syllables = syllables
            )
        )
    }

    private fun getAppName(pkg: String): String {
        appNameCache[pkg]?.let { return it }
        return try {
            val pm   = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            val name = pm.getApplicationLabel(info).toString()
            if (name.isNotEmpty()) appNameCache[pkg] = name
            name
        } catch (_: Exception) { pkg }
    }

    companion object {
        private const val TAG = "SuperLyricSource"
        private const val REGISTER_RETRY_DELAY_MS = 5_000L
    }
}
