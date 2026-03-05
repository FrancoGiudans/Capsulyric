package com.example.islandlyrics

import android.content.Context
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
    private val onlineLyricSource: OnlineLyricSource
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

            // ── Phase 1: detect song change and refresh metadata / album art ──
            val meta   = data.mediaMetadata
            val title  = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)  ?: lastTitle
            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: lastArtist
            val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            val songChanged = pkg != lastPackageName || title != lastTitle || artist != lastArtist

            if (songChanged) {
                AppLogger.getInstance().log(TAG, "Song change detected: [$pkg] $title - $artist")

                lastPackageName = pkg
                lastTitle  = title
                lastArtist = artist
                lastLyric  = ""   // Reset dedup

                // Update metadata immediately — this triggers Palette extraction
                // in SuperIslandHandler / LyricCapsuleHandler before the first
                // lyric line even arrives.
                LyricRepository.getInstance().updateMediaMetadata(title, artist, pkg, duration)

                // Request a fresh album art scan by asking MediaMonitorService
                // to re-read the current MediaSession for this package.
                // This eliminates the delay where colour stays grey until the
                // next lyric line arrives.
                mainHandler.post {
                    MediaMonitorService.refreshAlbumArtForPackage(context, pkg)
                }

                // Cancel any in-flight online lyric fetch from the previous song
                onlineLyricSource.cancel()

                // Reset parsed-lyrics state in the repository
                LyricRepository.getInstance().updateParsedLyrics(emptyList(), false)
                LyricRepository.getInstance().updateCurrentLine(null)
            }

            // ── Phase 2: handle lyric text ────────────────────────────────────
            val lyric = data.lyric

            // Instrumental / no-lyrics marker
            if (lyric.matches(".*(纯音乐|Instrumental|No lyrics|请欣赏|没有歌词).*".toRegex())) {
                AppLogger.getInstance().d(TAG, "Instrumental marker detected — clearing lyric")
                LyricRepository.getInstance().updateLyric("", getAppName(pkg))
                return
            }

            if (lyric == lastLyric && !songChanged) {
                AppLogger.getInstance().d(TAG, "Duplicate lyric — skipped")
                return
            }
            lastLyric = lyric

            LyricRepository.getInstance().updatePlaybackStatus(true)
            LyricRepository.getInstance().updateLyric(lyric, getAppName(pkg))

            // ── Phase 3: handle EnhancedLRCData (word-level syllable data) ───
            val enhancedData = data.enhancedLRCData
            if (!enhancedData.isNullOrEmpty()) {
                // Convert API EnhancedLRCData into internal LyricLine list.
                // Each word becomes a single-word "line"; aggregate by lyric
                // boundaries if delay info is available.
                val lines = convertEnhancedToLines(lyric, enhancedData)
                LyricRepository.getInstance().updateParsedLyrics(lines, hasSyllable = true)
                AppLogger.getInstance().d(TAG, "EnhancedLRC: ${lines.size} lines — online fetch skipped")
                return   // No need to fetch online
            }

            // ── Phase 4: decide whether to trigger online fetch ───────────────
            if (rule.useOnlineLyrics) {
                // Only fetch if EnhancedLRC was not provided
                onlineLyricSource.fetchFor(title, artist, pkg)
            }
        }

        override fun onStop(data: SuperLyricData?) {
            AppLogger.getInstance().d(TAG, "onStop: ${data?.packageName}")
            LyricRepository.getInstance().updatePlaybackStatus(false)
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
