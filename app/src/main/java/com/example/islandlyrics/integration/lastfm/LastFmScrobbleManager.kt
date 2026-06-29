package com.example.islandlyrics.integration.lastfm

import android.content.Context
import androidx.core.content.edit
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.rules.ParserRuleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LastFmScrobbleManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = AppPreferences.of(appContext)
    private val store = LastFmSecureStore(appContext)
    private val api = LastFmApiClient(networkAllowed = { !OfflineModeManager.isEnabled(appContext) })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentInstance: PlayInstance? = null
    private var lastPositionMs = 0L
    private var wasPlaying = false

    fun onMetadataChanged(info: LyricRepository.MediaInfo?) {
        if (info == null) {
            currentInstance = null
            return
        }
        if (ParserRuleHelper.getRuleForPackage(appContext, info.packageName) == null) {
            currentInstance = null
            return
        }

        val track = LastFmTrack(
            title = info.title,
            artist = info.artist,
            durationSeconds = info.duration.takeIf { it > 0 }?.div(1000L) ?: 0L
        )
        val key = "${info.packageName}|${track.title}|${track.artist}|${info.duration}"
        if (currentInstance?.key == key) return

        currentInstance = PlayInstance(
            key = key,
            track = track,
            startedAtSeconds = System.currentTimeMillis() / 1000L
        )
        lastPositionMs = 0L

        if (wasPlaying) {
            sendNowPlayingIfReady()
        }
    }

    fun onPlaybackChanged(playing: Boolean) {
        wasPlaying = playing
        if (playing) {
            sendNowPlayingIfReady()
        }
    }

    fun onProgressChanged(progress: LyricRepository.PlaybackProgress?) {
        val instance = currentInstance ?: return
        if (!wasPlaying || progress == null) return

        val position = progress.position
        val duration = if (progress.duration > 0) progress.duration else instance.track.durationSeconds * 1000L

        if (lastPositionMs > 0 && position < LOOP_RESET_POSITION_MS && lastPositionMs > duration - LOOP_RESET_WINDOW_MS) {
            currentInstance = instance.copy(
                id = instance.id + 1,
                startedAtSeconds = System.currentTimeMillis() / 1000L,
                nowPlayingSent = false,
                scrobbled = false
            )
            lastPositionMs = position
            sendNowPlayingIfReady()
            return
        }

        lastPositionMs = position
        val active = currentInstance ?: return
        if (active.scrobbled || !active.track.isValid()) return
        if (duration < MIN_TRACK_DURATION_MS) return
        if (position < scrobbleThresholdMs(duration)) return

        currentInstance = active.copy(scrobbled = true)
        scope.launch {
            val credentials = store.getCredentials()
            if (!isReady(credentials)) return@launch
            val result = api.scrobble(credentials, active.track, active.startedAtSeconds)
            result.onFailure {
                AppLogger.getInstance().e(TAG, "Last.fm scrobble failed: ${it.message}")
            }
        }
    }

    fun disconnect() {
        prefs.edit { putBoolean(AppPreferences.Keys.LASTFM_ENABLED, false) }
        store.clearSession()
        currentInstance = null
    }

    private fun sendNowPlayingIfReady() {
        val instance = currentInstance ?: return
        if (instance.nowPlayingSent || !instance.track.isValid()) return
        currentInstance = instance.copy(nowPlayingSent = true)
        scope.launch {
            val credentials = store.getCredentials()
            if (!isReady(credentials)) return@launch
            api.updateNowPlaying(credentials, instance.track).onFailure {
                AppLogger.getInstance().e(TAG, "Last.fm now playing failed: ${it.message}")
            }
        }
    }

    private fun isReady(credentials: LastFmCredentials): Boolean {
        return AppPreferences.isLastFmEnabled(prefs) &&
            !OfflineModeManager.isEnabled(appContext) &&
            credentials.isConnected
    }

    private fun scrobbleThresholdMs(durationMs: Long): Long {
        return minOf(durationMs / 2L, FOUR_MINUTES_MS)
    }

    private data class PlayInstance(
        val key: String,
        val track: LastFmTrack,
        val startedAtSeconds: Long,
        val id: Int = 0,
        val nowPlayingSent: Boolean = false,
        val scrobbled: Boolean = false
    )

    private companion object {
        private const val TAG = "LastFm"
        private const val MIN_TRACK_DURATION_MS = 30_000L
        private const val FOUR_MINUTES_MS = 240_000L
        private const val LOOP_RESET_POSITION_MS = 5_000L
        private const val LOOP_RESET_WINDOW_MS = 5_000L
    }
}
