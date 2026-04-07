package com.example.islandlyrics.data.lyric

import android.content.Context
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.service.MediaMonitorService
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.LyricRepository
import android.media.MediaMetadata
import android.os.Handler
import android.os.Looper
import com.hchen.superlyricapi.ISuperLyric
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricTool

/**
 * SuperLyricSource
 *
 * Handles the SuperLyric Xposed-module lyric push path.
 * Responsibilities:
 *  - Register / unregister the ISuperLyric AIDL stub
 *  - Detect song changes from incoming SuperLyricData and immediately update
 *    metadata + album art (instead of waiting for a MediaSession callback)
 *  - Convert EnhancedLRCData (word-level) into LyricLine and push to the
 *    repository, so the online-lyric fetch can be skipped when data is already
 *    available
 *  - Delegate online-lyric fetching to [OnlineLyricSource] when needed
 *
 * This class is intentionally free of notification or UI concerns.
 */
class SuperLyricSource(
    private val context: Context,
    private val onlineLyricSource: OnlineLyricSource,
    private val onProgressHint: (packageName: String, position: Long, duration: Long) -> Unit = { _, _, _ -> }
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    // Track the last song identity to detect genuine song changes
    private var lastPackageName = ""
    private var lastTitle = ""
    private var lastArtist = ""
    // Deduplicate consecutive identical lyric lines
    private var lastLyric = ""

    // Cache app-display-names to avoid repeated PackageManager IPC
    private val appNameCache = HashMap<String, String>()

    // ── ISuperLyric AIDL stub ─────────────────────────────────────────────────

    private val stub = object : ISuperLyric.Stub() {

        override fun onSuperLyric(data: SuperLyricData?) {
            if (data == null) {
                AppLogger.getInstance().d(TAG, "onSuperLyric: null data — ignored")
                return
            }

            val pkg  = data.packageName
            val rule = ParserRuleHelper.getRuleForPackage(context, pkg)
                       ?: ParserRuleHelper.createDefaultRule(pkg)

            if (!rule.useSuperLyricApi) {
                AppLogger.getInstance().d(TAG, "[$pkg] SuperLyric disabled by rule — skipped")
                return
            }

            // ── Phase 1: Verify song identity (rely entirely on MediaMonitorService for actual metadata/album art) ──
            val liveMeta = LyricRepository.getInstance().liveMetadata.value
            val liveTitle = liveMeta?.title ?: ""
            val liveArtist = liveMeta?.artist ?: ""
            val livePkg = liveMeta?.packageName ?: ""

            // What did SuperLyric give us?
            val meta = data.mediaMetadata
            val providedTitle = if (data.isExistMediaMetadata) meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "" else ""
            val providedArtist = if (data.isExistMediaMetadata) meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "" else ""
            
            // If SuperLyric provided metadata, we strictly verify it against what the system MediaSession says is playing.
            // If it doesn't match, this is a stale or cross-app lyric packet, ignore it.
            // If SuperLyric didn't provide metadata (e.g., just sending lyrics for the current song), we allow it
            // as long as the packageName matches our currently active session.
            val isMatch = if (data.isExistMediaMetadata) {
                (providedTitle.equals(liveTitle, ignoreCase = true) || liveTitle.isEmpty()) && pkg == livePkg // Allow slight artist mismatch if title & pkg match, but strict title is usually best
            } else {
                pkg == livePkg
            }

            if (!isMatch) {
                AppLogger.getInstance().d(TAG, "[$pkg] Lyric ignored. Mismatch w/ current session (${liveMeta?.title} - ${liveMeta?.artist}) vs ($providedTitle - $providedArtist)")
                return
            }

            // We are confident this lyric belongs to the currently playing song handled by MediaSession

            // Sync progress if SuperLyricData carries explicit playback state.
            // NOTE: We intentionally do NOT write isPlaying here.
            // MediaMonitorService (via MediaSession) is the sole authority for playback state.
            // Writing isPlaying from SuperLyric would bypass whitelist checks and
            // re-couple the lyric source back into the playback state machine.
            val playbackState = data.playbackState
            if (playbackState != null) {
                // Pass progress hints into the dedicated progress synchronizer so the
                // repository has a single authority for progress writes.
                val liveDuration = LyricRepository.getInstance().liveMetadata.value?.duration ?: 0L
                if (liveDuration > 0 && playbackState.position > 0) {
                    onProgressHint(pkg, playbackState.position, liveDuration)
                }
            }

            // ── Phase 2: handle lyric text ────────────────────────────────────
            val lyric = data.lyric

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

            // Do NOT call updatePlaybackStatus(true) here.
            // Lyric arrival does not imply "is playing" — that judgment belongs to
            // MediaMonitorService which checks the actual MediaSession state and whitelist.
            LyricRepository.getInstance().updateLyric(lyric, getAppName(pkg), "SuperLyric")

            // ── Phase 3: handle EnhancedLRCData (word-level syllable data) ───
            val enhancedData = data.enhancedLRCData
            if (!enhancedData.isNullOrEmpty()) {
                // Convert API EnhancedLRCData into internal LyricLine list.
                // Each word becomes a single-word "line"; aggregate by lyric
                // boundaries if delay info is available.
                val lines = convertEnhancedToLines(lyric, enhancedData)
                LyricRepository.getInstance().updateParsedLyrics(
                    lines = lines,
                    hasSyllable = true,
                    sourceLabel = getAppName(pkg),
                    apiPath = "SuperLyric"
                )
                AppLogger.getInstance().d(TAG, "EnhancedLRC: ${lines.size} lines — online fetch skipped")
                return   // No need to fetch online
            }

            // ── Phase 4: decide whether to trigger online fetch ───────────────
            if (rule.useOnlineLyrics) {
                // Only fetch if EnhancedLRC was not provided
                onlineLyricSource.fetchFor(liveTitle, liveArtist, pkg)
            }
        }

        override fun onStop(data: SuperLyricData?) {
            AppLogger.getInstance().d(TAG, "onStop: ${data?.packageName}")
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
        SuperLyricTool.registerSuperLyric(context, stub)
        AppLogger.getInstance().log(TAG, "SuperLyricSource started — AIDL stub registered")
    }

    fun stop() {
        SuperLyricTool.unregisterSuperLyric(context, stub)
        reset()
        AppLogger.getInstance().log(TAG, "SuperLyricSource stopped — AIDL stub unregistered")
    }

    private fun reset() {
        lastPackageName = ""
        lastTitle  = ""
        lastArtist = ""
        lastLyric  = ""
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Converts an [Array] of [SuperLyricData.EnhancedLRCData] syllable entries
     * into a list of [OnlineLyricFetcher.LyricLine] objects.
     *
     * Each word/syllable becomes a [OnlineLyricFetcher.SyllableInfo] under a single
     * parent line that spans the full lyric.
     */
    private fun convertEnhancedToLines(
        fullLyric: String,
        words: Array<SuperLyricData.EnhancedLRCData>
    ): List<OnlineLyricFetcher.LyricLine> {
        if (words.isEmpty()) return emptyList()

        val startMs = words.first().startTime.toLong()
        val endMs   = words.last().endTime.toLong()

        val syllables = words.map { w ->
            OnlineLyricFetcher.SyllableInfo(
                startTime = w.startTime.toLong(),
                endTime   = w.endTime.toLong(),
                text      = w.word
            )
        }

        val line = OnlineLyricFetcher.LyricLine(
            startTime  = startMs,
            endTime    = endMs,
            text       = fullLyric.ifBlank { words.joinToString("") { it.word } },
            syllables  = syllables
        )
        return listOf(line)
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
    }
}
