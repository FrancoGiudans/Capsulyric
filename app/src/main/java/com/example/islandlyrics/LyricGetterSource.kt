package com.example.islandlyrics

import android.content.Context
import cn.lyric.getter.api.API
import cn.lyric.getter.api.data.LyricData
import cn.lyric.getter.api.listener.LyricListener
import cn.lyric.getter.api.listener.LyricReceiver
import cn.lyric.getter.api.tools.Tools

/**
 * LyricGetterSource
 *
 * Handles the Lyric Getter API lyric push path.
 * Responsibilities:
 *  - Register / unregister the BroadcastReceiver using Tools.registerLyricListener
 *  - Verify the source package from ExtraData.packageName matches the current session
 *  - Deduplicate consecutive identical lyric lines
 *  - Delegate to [LyricRepository] to push lyric updates
 *  - Optionally trigger [OnlineLyricSource] if enabled by per-app rule
 *
 * This class is intentionally free of notification or UI concerns.
 */
class LyricGetterSource(
    private val context: Context,
    private val onlineLyricSource: OnlineLyricSource
) {
    // Deduplicate consecutive identical lyric lines
    private var lastLyric = ""

    // Cache app-display-names to avoid repeated PackageManager IPC
    private val appNameCache = HashMap<String, String>()

    // ── LyricListener implementation ──────────────────────────────────────────

    private val listener = object : LyricListener() {

        override fun onUpdate(lyricData: LyricData) {
            val pkg = lyricData.extraData.packageName

            // Guard: check per-app rule
            val rule = if (pkg.isNotEmpty()) {
                ParserRuleHelper.getRuleForPackage(context, pkg)
                    ?: ParserRuleHelper.createDefaultRule(pkg)
            } else {
                // No package info — use the current session's rule
                val livePkg = LyricRepository.getInstance().liveMetadata.value?.packageName ?: ""
                if (livePkg.isEmpty()) {
                    AppLogger.getInstance().d(TAG, "onUpdate: no package info — skipped")
                    return
                }
                ParserRuleHelper.getRuleForPackage(context, livePkg)
                    ?: ParserRuleHelper.createDefaultRule(livePkg)
            }

            if (!rule.useLyricGetterApi) {
                AppLogger.getInstance().d(TAG, "[$pkg] Lyric Getter disabled by rule — skipped")
                return
            }

            // Verify the source package matches the currently active session
            val liveMeta = LyricRepository.getInstance().liveMetadata.value
            val livePkg  = liveMeta?.packageName ?: ""

            val effectivePkg = if (pkg.isNotEmpty()) pkg else livePkg

            if (effectivePkg.isNotEmpty() && livePkg.isNotEmpty() && effectivePkg != livePkg) {
                AppLogger.getInstance().d(
                    TAG,
                    "[$effectivePkg] Lyric ignored — mismatch with current session [$livePkg]"
                )
                return
            }

            val lyric = lyricData.lyric

            // Deduplicate
            if (lyric == lastLyric) {
                AppLogger.getInstance().d(TAG, "Duplicate lyric — skipped")
                return
            }
            lastLyric = lyric

            val appName = getAppName(effectivePkg.ifEmpty { livePkg })

            AppLogger.getInstance().i(TAG, "📥 Lyric received from [$effectivePkg]: $lyric")
            LyricRepository.getInstance().updatePlaybackStatus(true)
            LyricRepository.getInstance().updateLyric(lyric, appName, "Lyric Getter")

            // Optionally trigger online lyric fetch
            val liveTitle  = liveMeta?.title  ?: ""
            val liveArtist = liveMeta?.artist ?: ""
            if (rule.useOnlineLyrics && liveTitle.isNotBlank() && liveArtist.isNotBlank()) {
                onlineLyricSource.fetchFor(liveTitle, liveArtist, effectivePkg.ifEmpty { livePkg })
            }
        }

        override fun onStop(lyricData: LyricData) {
            AppLogger.getInstance().d(TAG, "onStop received")
            LyricRepository.getInstance().updatePlaybackStatus(false)
            lastLyric = "" // Reset deduplication state on stop
        }
    }

    private val receiver = LyricReceiver(listener)

    // ── Public lifecycle ──────────────────────────────────────────────────────

    fun start() {
        Tools.registerLyricListener(context, API.API_VERSION, receiver)
        AppLogger.getInstance().log(TAG, "LyricGetterSource started — receiver registered")
    }

    fun stop() {
        Tools.unregisterLyricListener(context, receiver)
        lastLyric = ""
        AppLogger.getInstance().log(TAG, "LyricGetterSource stopped — receiver unregistered")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getAppName(pkg: String): String {
        if (pkg.isEmpty()) return "Lyric Getter"
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
        private const val TAG = "LyricGetterSource"
    }
}
