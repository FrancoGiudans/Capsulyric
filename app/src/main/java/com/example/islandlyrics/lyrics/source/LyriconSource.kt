/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.lyrics.source

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.rules.ParserRuleHelper
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ConnectionListener
import io.github.proify.lyricon.subscriber.LyriconFactory
import io.github.proify.lyricon.subscriber.LyriconSubscriber
import io.github.proify.lyricon.subscriber.ProviderInfo

/**
 * Receives lyric data from Lyricon's active-player subscriber bridge.
 *
 * This source imports plain text, timed lines, word timing, translation, and
 * romanization from Lyricon's active player feed.
 */
class LyriconSource(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var subscriber: LyriconSubscriber? = null
    private var started = false
    private var subscribed = false
    private var sessionGeneration = 0L
    private var retryDelayMs = INITIAL_RETRY_MS
    private var activeProviderInfo: ProviderInfo? = null
    private var latestSong: Song? = null
    private var isPlainTextMode = false
    private var lastPublishedTextKey: Pair<String, String?>? = null

    @Volatile
    private var lastPositionMs = -1L

    private val appNameCache = HashMap<String, String>()

    private val reconnectRunnable = Runnable {
        if (!started) return@Runnable
        if (!ParserRuleHelper.hasEnabledLyriconRule(context)) {
            stop()
            return@Runnable
        }
        AppLogger.getInstance().d(TAG, "Reconnecting Lyricon subscriber after timeout/disconnect")
        reconnectSubscriber()
    }

    private fun createConnectionListener(generation: Long) = object : ConnectionListener {
        override fun onConnected(subscriber: LyriconSubscriber) {
            onMain {
                if (!started || generation != sessionGeneration) return@onMain
                retryDelayMs = INITIAL_RETRY_MS
                mainHandler.removeCallbacks(reconnectRunnable)
                AppLogger.getInstance().i(TAG, "Lyricon connected")
            }
        }

        override fun onReconnected(subscriber: LyriconSubscriber) {
            onMain {
                if (!started || generation != sessionGeneration) return@onMain
                retryDelayMs = INITIAL_RETRY_MS
                mainHandler.removeCallbacks(reconnectRunnable)
                AppLogger.getInstance().i(TAG, "Lyricon reconnected")
            }
        }

        override fun onDisconnected(subscriber: LyriconSubscriber) {
            onMain {
                if (generation != sessionGeneration) return@onMain
                resetActiveFeedState()
                AppLogger.getInstance().d(TAG, "Lyricon disconnected")
                if (started) {
                    scheduleReconnect()
                }
            }
        }

        override fun onConnectTimeout(subscriber: LyriconSubscriber) {
            onMain {
                if (!started || generation != sessionGeneration) return@onMain
                AppLogger.getInstance().w(TAG, "Lyricon connection timed out — scheduling retry in ${retryDelayMs}ms")
                scheduleReconnect()
            }
        }
    }

    private fun createActivePlayerListener(generation: Long) = object : ActivePlayerListener {
        override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
            onMain {
                if (!started || generation != sessionGeneration) return@onMain
                val changed = providerInfo == null || providerInfo != activeProviderInfo
                activeProviderInfo = providerInfo
                if (changed) {
                    latestSong = null
                    isPlainTextMode = false
                    lastPublishedTextKey = null
                    lastPositionMs = -1L
                }
                if (providerInfo == null) {
                    AppLogger.getInstance().d(TAG, "No active Lyricon provider")
                } else {
                    AppLogger.getInstance().d(
                        TAG,
                        "Active Lyricon provider: provider=${providerInfo.providerPackageName}, player=${providerInfo.playerPackageName}"
                    )
                }
            }
        }

        override fun onSongChanged(song: Song?) {
            onMain {
                if (!started || generation != sessionGeneration) return@onMain
                latestSong = song
                isPlainTextMode = false
                lastPublishedTextKey = null
                if (song == null) return@onMain
                applySongIfEligible(song)
            }
        }

        override fun onReceiveText(text: String?) {
            onMain {
                if (!started || generation != sessionGeneration) return@onMain
                latestSong = null
                isPlainTextMode = true
                if (text.isNullOrBlank()) {
                    lastPublishedTextKey = null
                    return@onMain
                }
                applyPlainTextIfEligible(text)
            }
        }

        override fun onPlaybackStateChanged(isPlaying: Boolean) = Unit

        override fun onPositionChanged(position: Long) {
            lastPositionMs = position.coerceAtLeast(0L)
        }

        override fun onSeekTo(position: Long) {
            val pos = position.coerceAtLeast(0L)
            lastPositionMs = pos
            onMain {
                if (!started || generation != sessionGeneration || isPlainTextMode) return@onMain
                val repo = LyricRepository.getInstance()
                val parsed = repo.liveParsedLyrics.value
                if (parsed?.apiPath != API_PATH || parsed.lines.isEmpty()) return@onMain

                val activeLine = LyriconMapper.findActiveLine(parsed.lines, pos)
                repo.updateCurrentLine(activeLine)
                if (activeLine != null) {
                    val pkg = repo.liveMetadata.value?.packageName
                        ?: resolvePackageName()
                        ?: resolveFallbackPackageName()
                    val appName = pkg?.let(::getAppName) ?: parsed.sourceLabel ?: API_PATH
                    repo.updateLyric(
                        lyric = activeLine.text,
                        app = appName,
                        apiPath = API_PATH,
                        translation = activeLine.translation,
                        roma = activeLine.roma,
                        receiveTranslation = parsed.receiveTranslation,
                        receiveRomanization = parsed.receiveRomanization
                    )
                }
            }
        }

        override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) = Unit

        override fun onDisplayRomaChanged(isDisplayRoma: Boolean) = Unit
    }

    private var activeConnectionListener: ConnectionListener? = null
    private var activePlayerListener: ActivePlayerListener? = null

    fun start(): Unit = onMain {
        if (started) return@onMain
        if (!ParserRuleHelper.hasEnabledLyriconRule(context)) {
            AppLogger.getInstance().log(TAG, "LyriconSource skipped — no enabled parser rule uses Lyricon")
            return@onMain
        }
        started = true
        retryDelayMs = INITIAL_RETRY_MS
        openSubscriber()
    }

    fun stop(): Unit = onMain {
        mainHandler.removeCallbacks(reconnectRunnable)
        started = false
        sessionGeneration++
        closeSubscriber()
        resetActiveFeedState()
        AppLogger.getInstance().log(TAG, "LyriconSource stopped")
    }

    fun onTrackChanged(): Unit = onMain {
        lastPublishedTextKey = null
        lastPositionMs = -1L
        if (!started) return@onMain
        val cachedSong = latestSong ?: return@onMain
        if (!isPlainTextMode) {
            applySongIfEligible(cachedSong)
        }
    }

    private fun openSubscriber() {
        closeSubscriber()
        val generation = ++sessionGeneration
        val created = try {
            LyriconFactory.createSubscriber(context.applicationContext)
        } catch (t: Throwable) {
            AppLogger.getInstance().e(TAG, "Failed to create Lyricon subscriber", t)
            scheduleReconnect()
            return
        }

        val connListener = createConnectionListener(generation)
        val playerListener = createActivePlayerListener(generation)
        subscriber = created
        activeConnectionListener = connListener
        activePlayerListener = playerListener

        created.addConnectionListener(connListener)
        subscribed = created.subscribeActivePlayer(playerListener)
        if (!subscribed) {
            AppLogger.getInstance().w(TAG, "Lyricon active-player subscription was not accepted")
        }

        try {
            created.register()
            AppLogger.getInstance().log(TAG, "LyriconSource started — subscriber registered")
        } catch (t: Throwable) {
            AppLogger.getInstance().e(TAG, "Failed to register Lyricon subscriber", t)
            scheduleReconnect()
        }
    }

    private fun closeSubscriber() {
        val current = subscriber
        val connListener = activeConnectionListener
        val playerListener = activePlayerListener
        subscriber = null
        activeConnectionListener = null
        activePlayerListener = null
        val wasSubscribed = subscribed
        subscribed = false

        if (current == null) return
        try {
            if (wasSubscribed && playerListener != null) {
                current.unsubscribeActivePlayer(playerListener)
            }
            current.unregister()
            if (connListener != null) {
                current.removeConnectionListener(connListener)
            }
            current.destroy()
        } catch (t: Throwable) {
            AppLogger.getInstance().e(TAG, "Failed to stop Lyricon subscriber", t)
        }
    }

    private fun reconnectSubscriber() {
        if (!started) return
        openSubscriber()
    }

    private fun scheduleReconnect() {
        if (!started) return
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, retryDelayMs)
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
    }

    private fun resetActiveFeedState() {
        activeProviderInfo = null
        latestSong = null
        isPlainTextMode = false
        lastPublishedTextKey = null
        lastPositionMs = -1L
    }

    private fun applySongIfEligible(song: Song) {
        val pkg = resolvePackageName() ?: resolveFallbackPackageName() ?: run {
            AppLogger.getInstance().d(TAG, "onSongChanged: no active player package — skipped")
            return
        }
        val rule = ParserRuleHelper.getRuleForPackage(context, pkg)
            ?: ParserRuleHelper.createDefaultRule(pkg)
        if (!rule.enabled || !rule.useLyriconApi) {
            AppLogger.getInstance().d(TAG, "[$pkg] Lyricon disabled by rule — skipped")
            return
        }

        val repo = LyricRepository.getInstance()
        val liveMeta = repo.liveMetadata.value
        if (liveMeta != null) {
            val track = LyriconTrack(
                packageName = liveMeta.packageName,
                title = liveMeta.title,
                artist = liveMeta.artist,
                rawTitle = liveMeta.rawTitle,
                rawArtist = liveMeta.rawArtist,
                mediaId = liveMeta.mediaId
            )
            val playerPkg = activeProviderInfo?.playerPackageName?.takeIf { it.isNotBlank() } ?: pkg
            if (!LyriconMapper.matchesSession(playerPkg, song, track)) {
                AppLogger.getInstance().d(
                    TAG,
                    "[$pkg] Lyricon song ignored — mismatch with current session (${liveMeta.title} - ${liveMeta.artist}) vs (${song.name} - ${song.artist})"
                )
                return
            }
        }

        val parsedLines = LyriconMapper.convertSong(
            song = song,
            receiveTranslation = rule.receiveLyriconTranslation,
            receiveRomanization = rule.receiveLyriconRomanization
        )
        if (parsedLines.isEmpty()) {
            AppLogger.getInstance().d(TAG, "[$pkg] Lyricon song has no structured lyric lines")
            return
        }

        val appName = getAppName(pkg)
        val hasSyllable = parsedLines.any { !it.syllables.isNullOrEmpty() }

        repo.updateParsedLyrics(
            lines = parsedLines,
            hasSyllable = hasSyllable,
            sourceLabel = appName,
            apiPath = API_PATH,
            timelineCapability = LyricRepository.TimelineCapability.MULTI_LINE,
            receiveTranslation = rule.receiveLyriconTranslation,
            receiveRomanization = rule.receiveLyriconRomanization
        )

        val effectivePositionMs = lastPositionMs.takeIf { it >= 0L }
            ?: repo.liveProgress.value?.position
            ?: 0L
        val activeLine = LyriconMapper.findActiveLine(parsedLines, effectivePositionMs)
        repo.updateCurrentLine(activeLine)
        if (activeLine != null) {
            repo.updateLyric(
                lyric = activeLine.text,
                app = appName,
                apiPath = API_PATH,
                translation = activeLine.translation,
                roma = activeLine.roma,
                receiveTranslation = rule.receiveLyriconTranslation,
                receiveRomanization = rule.receiveLyriconRomanization
            )
        }
        AppLogger.getInstance().d(
            TAG,
            "Lyricon structured lyrics imported: ${parsedLines.size} lines, syllable=$hasSyllable, pos=${effectivePositionMs}ms"
        )
    }

    private fun applyPlainTextIfEligible(rawText: String) {
        val repo = LyricRepository.getInstance()
        val pkg = resolvePackageName()
            ?: resolveFallbackPackageName()
            ?: repo.liveMetadata.value?.packageName
            ?: run {
                AppLogger.getInstance().d(TAG, "onReceiveText: no package — skipped")
                return
            }
        val rule = ParserRuleHelper.getRuleForPackage(context, pkg)
            ?: ParserRuleHelper.createDefaultRule(pkg)
        if (!rule.enabled || !rule.useLyriconApi) {
            AppLogger.getInstance().d(TAG, "[$pkg] Lyricon disabled by rule — skipped")
            return
        }
        val livePkg = repo.liveMetadata.value?.packageName
        if (!livePkg.isNullOrBlank() && pkg != livePkg) {
            AppLogger.getInstance().d(TAG, "[$pkg] Lyricon ignored — mismatch with current session [$livePkg]")
            return
        }

        val parsedText = LyriconMapper.parseReceiveText(
            rawText = rawText,
            receiveTranslation = rule.receiveLyriconTranslation
        ) ?: run {
            lastPublishedTextKey = null
            return
        }

        val dedupKey = parsedText.mainLyric to parsedText.translation
        if (dedupKey == lastPublishedTextKey) return
        lastPublishedTextKey = dedupKey

        if (repo.liveParsedLyrics.value?.apiPath == API_PATH) {
            repo.updateParsedLyrics(
                lines = emptyList(),
                hasSyllable = false,
                timelineCapability = LyricRepository.TimelineCapability.NONE
            )
            repo.updateCurrentLine(null)
        }

        repo.updateLyric(
            lyric = parsedText.mainLyric,
            app = getAppName(pkg),
            apiPath = API_PATH,
            translation = parsedText.translation,
            roma = null,
            receiveTranslation = rule.receiveLyriconTranslation,
            receiveRomanization = rule.receiveLyriconRomanization
        )
    }

    private fun resolvePackageName(): String? {
        val livePkg = LyricRepository.getInstance().liveMetadata.value?.packageName
        val candidates = listOf(
            activeProviderInfo?.playerPackageName,
            activeProviderInfo?.providerPackageName,
            livePkg
        ).filterNotNull().filter { it.isNotBlank() }.distinct()

        return candidates.firstOrNull { pkg ->
            ParserRuleHelper.getRuleForPackage(context, pkg)
                ?.let { it.enabled && it.useLyriconApi } == true
        } ?: candidates.firstOrNull()
    }

    private fun resolveFallbackPackageName(): String? {
        val livePkg = LyricRepository.getInstance().liveMetadata.value?.packageName
        if (!livePkg.isNullOrBlank()) return livePkg

        return ParserRuleHelper.loadRules(context)
            .firstOrNull { it.enabled && it.useLyriconApi }
            ?.packageName
    }

    private fun getAppName(pkg: String): String {
        appNameCache[pkg]?.let { return it }
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            val name = pm.getApplicationLabel(info).toString()
            if (name.isNotEmpty()) appNameCache[pkg] = name
            name
        } catch (_: Exception) {
            pkg
        }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    companion object {
        private const val TAG = "LyriconSource"
        private const val API_PATH = "Lyricon"
        private const val INITIAL_RETRY_MS = 5_000L
        private const val MAX_RETRY_MS = 60_000L
    }
}
