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
    private var currentRetryDelayMs = REGISTER_RETRY_INITIAL_DELAY_MS
    private var unavailableWarningLogged = false
    private var registerAttemptCount = 0

    // Deduplicate consecutive identical lyric lines
    private var lastLyricKey = ""

    // Cache app-display-names to avoid repeated PackageManager IPC
    private val appNameCache = HashMap<String, String>()

    private val registerReceiverRunnable = object : Runnable {
        override fun run() {
            if (!started || receiverRegistered) return

            try {
                registerAttemptCount += 1
                SuperLyricHelper.registerReceiver(stub)
                receiverRegistered = true
                currentRetryDelayMs = REGISTER_RETRY_INITIAL_DELAY_MS
                unavailableWarningLogged = false
                registerAttemptCount = 0
                AppLogger.getInstance().log(TAG, "SuperLyricSource started — receiver registered")
            } catch (t: IllegalStateException) {
                if (!ParserRuleHelper.hasEnabledSuperLyricRule(context)) {
                    AppLogger.getInstance().log(TAG, "SuperLyricSource stopped retrying — no enabled parser rule uses SuperLyric")
                    started = false
                    return
                }

                if (registerAttemptCount >= REGISTER_RETRY_MAX_ATTEMPTS) {
                    AppLogger.getInstance().w(
                        TAG,
                        "SuperLyric service unavailable; stopping retries until rules/service restart: ${t.message}"
                    )
                    started = false
                    currentRetryDelayMs = REGISTER_RETRY_INITIAL_DELAY_MS
                    registerAttemptCount = 0
                    return
                }

                val message = "SuperLyric service unavailable, retrying once in ${currentRetryDelayMs}ms: ${t.message}"
                if (!unavailableWarningLogged) {
                    AppLogger.getInstance().w(TAG, message)
                    unavailableWarningLogged = true
                } else {
                    AppLogger.getInstance().d(TAG, message)
                }
                mainHandler.postDelayed(this, currentRetryDelayMs)
                currentRetryDelayMs = (currentRetryDelayMs * 2).coerceAtMost(REGISTER_RETRY_MAX_DELAY_MS)
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
                updateDebugSnapshot(publisher, "", data, null, null, null, "publisher missing and no active session")
                AppLogger.getInstance().d(TAG, "onLyric: publisher missing and no active session — ignored")
                return
            }

            val rule = ParserRuleHelper.getRuleForPackage(context, pkg)
                       ?: ParserRuleHelper.createDefaultRule(pkg)

            if (!rule.useSuperLyricApi) {
                updateDebugSnapshot(publisher, pkg, data, null, null, null, "disabled by rule")
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
                updateDebugSnapshot(publisher, pkg, data, null, null, null, "mismatch with current session")
                AppLogger.getInstance().d(TAG, "[$pkg] Lyric ignored. Mismatch w/ current session (${liveMeta?.title} - ${liveMeta?.artist}) vs ($providedTitle - $providedArtist)")
                return
            }

            val lyricLine = data.lyric
            val lyric = lyricLine?.asText().orEmpty()
            val translationText = data.translation?.asText()
            val romaText = data.secondary?.asText()
            updateDebugSnapshot(publisher, pkg, data, lyric, translationText, romaText, null)

            // Some publishers send secondary/translation as a companion update without repeating
            // the primary lyric line. Keep it attached to the current SuperLyric line instead of
            // dropping the update as an empty lyric.
            if (lyric.isBlank()) {
                val currentLyric = LyricRepository.getInstance().liveLyric.value
                    ?.takeIf { it.apiPath == "SuperLyric" && it.lyric.isNotBlank() }
                    ?.lyric
                if (currentLyric != null && (translationText != null || romaText != null)) {
                    LyricRepository.getInstance().updateLyric(
                        lyric = currentLyric,
                        app = getAppName(pkg),
                        apiPath = "SuperLyric",
                        translation = translationText,
                        roma = romaText
                    )
                    return
                }

                updateDebugSnapshot(publisher, pkg, data, lyric, translationText, romaText, "empty lyric line")
                AppLogger.getInstance().d(TAG, "[$pkg] Empty lyric line — ignored")
                return
            }

            // Instrumental / no-lyrics marker
            if (lyric.matches(".*(纯音乐|Instrumental|No lyrics|请欣赏|没有歌词).*".toRegex())) {
                AppLogger.getInstance().d(TAG, "Instrumental marker detected")
                // Do NOT clear it to "", otherwise the UI shows "Waiting for lyrics..." indefinitely.
                // Just pass it through so the user sees "纯音乐".
                LyricRepository.getInstance().updateLyric(
                    lyric = lyric,
                    app = getAppName(pkg),
                    apiPath = "SuperLyric",
                    translation = data.translation?.asText(),
                    roma = data.secondary?.asText()
                )
                return
            }

            val lyricKey = buildLyricKey(lyric, translationText, romaText)
            if (lyricKey == lastLyricKey) {
                updateDebugSnapshot(publisher, pkg, data, lyric, translationText, romaText, "duplicate lyric")
                AppLogger.getInstance().d(TAG, "Duplicate lyric — skipped")
                return
            }
            lastLyricKey = lyricKey

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
                LyricRepository.getInstance().updateLyric(
                    lyric = lyric,
                    app = appName,
                    apiPath = "SuperLyric",
                    translation = translationText,
                    roma = romaText
                )

                if (parsedLines != null) {
                    LyricRepository.getInstance().updateParsedLyrics(
                        lines = parsedLines,
                        hasSyllable = true,
                        sourceLabel = appName,
                        apiPath = "SuperLyric"
                    )
                    AppLogger.getInstance().d(
                        TAG,
                        "SuperLyric 3.x line converted: ${parsedLines.size} line(s), words=${words?.size}, translation=${data.translation != null}, roma=${data.secondary != null}"
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
            lastLyricKey = ""
        }
    }

    // ── Public lifecycle ──────────────────────────────────────────────────────

    fun start() {
        if (started || receiverRegistered) return
        if (!ParserRuleHelper.hasEnabledSuperLyricRule(context)) {
            AppLogger.getInstance().log(TAG, "SuperLyricSource skipped — no enabled parser rule uses SuperLyric")
            return
        }
        started = true
        currentRetryDelayMs = REGISTER_RETRY_INITIAL_DELAY_MS
        unavailableWarningLogged = false
        registerAttemptCount = 0
        mainHandler.removeCallbacks(registerReceiverRunnable)
        mainHandler.post {
            registerReceiverRunnable.run()
        }
    }

    fun stop() {
        if (!started && !receiverRegistered) return
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
            currentRetryDelayMs = REGISTER_RETRY_INITIAL_DELAY_MS
            unavailableWarningLogged = false
            registerAttemptCount = 0
            reset()
            AppLogger.getInstance().log(TAG, "SuperLyricSource stopped")
        }
    }

    private fun reset() {
        lastLyricKey = ""
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

    private fun SuperLyricLine.asText(): String? {
        return text.ifBlank {
            words.orEmpty().joinToString("") { it.word }
        }.takeIf { it.isNotBlank() }
    }

    private fun updateDebugSnapshot(
        publisher: String?,
        pkg: String,
        data: SuperLyricData,
        lyric: String?,
        translation: String?,
        roma: String?,
        skipReason: String?
    ) {
        LyricRepository.getInstance().updateSuperLyricDebug(
            LyricRepository.SuperLyricDebugInfo(
                publisher = publisher,
                packageName = pkg,
                lyric = lyric ?: data.lyric?.asText().orEmpty(),
                translation = translation ?: data.translation?.asText(),
                roma = roma ?: data.secondary?.asText(),
                hasLyric = data.hasLyric(),
                hasTranslation = data.hasTranslation(),
                hasSecondary = data.hasSecondary(),
                lyricLineRaw = data.lyric?.toString(),
                translationLineRaw = data.translation?.toString(),
                secondaryLineRaw = data.secondary?.toString(),
                lyricWordsPreview = data.lyric.wordsPreview(),
                translationWordsPreview = data.translation.wordsPreview(),
                secondaryWordsPreview = data.secondary.wordsPreview(),
                extraKeys = data.extra?.keySet()?.toList().orEmpty(),
                skipReason = skipReason
            )
        )
    }

    private fun SuperLyricLine?.wordsPreview(): String {
        val words = this?.words
        if (words.isNullOrEmpty()) return "(空)"
        return words.take(8).joinToString(separator = " | ") { word ->
            "${word.word}@${word.startTime}-${word.endTime}"
        }
    }

    private fun buildLyricKey(
        lyric: String,
        translation: String?,
        roma: String?
    ): String = buildString {
        append(lyric)
        append('\u0000')
        append(translation.orEmpty())
        append('\u0000')
        append(roma.orEmpty())
    }

    companion object {
        private const val TAG = "SuperLyricSource"
        private const val REGISTER_RETRY_INITIAL_DELAY_MS = 5_000L
        private const val REGISTER_RETRY_MAX_DELAY_MS = 5 * 60_000L
        private const val REGISTER_RETRY_MAX_ATTEMPTS = 2
    }
}
