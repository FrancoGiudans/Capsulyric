package com.example.islandlyrics.service

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.lyric.OnlineLyricFetcher

class ProgressSyncController(
    private val handler: Handler,
    private val repo: LyricRepository,
    private val appNameProvider: (String) -> String,
    private val mediaSessionManagerProvider: () -> MediaSessionManager?,
    private val mediaComponentProvider: () -> ComponentName?
) {
    private var isRunning = false
    private var currentPosition = 0L
    private var duration = 0L
    private var lastLineIndex = -1
    private var lastControllerSyncAt = 0L
    private var lastPlaybackState = PlaybackState.STATE_NONE
    private var lastPlaybackSpeed = 1f
    private var lastPlaybackPosition = 0L
    private var lastPlaybackPositionAt = 0L

    private val workerThread = HandlerThread("ProgressSyncController").apply { start() }
    private val workerHandler = Handler(workerThread.looper)

    private val updateTask = object : Runnable {
        private var logCounter = 0

        override fun run() {
            if (!isRunning) {
                AppLogger.getInstance().log(TAG, "⏸️ updateTask stopped (isRunning=false)")
                return
            }

            logCounter++
            val shouldLog = (logCounter % 10 == 0)
            if (shouldLog) {
                AppLogger.getInstance().log(TAG, "🔄 updateTask running...")
            }

            val now = SystemClock.elapsedRealtime()
            if (shouldSyncController(now)) {
                syncProgressFromController(shouldLog, now)
            } else {
                updateEstimatedProgress(now)
            }

            val parsedLines = repo.liveParsedLyrics.value?.lines
            if (!parsedLines.isNullOrEmpty()) {
                updateCurrentLyricLine(parsedLines)
            }

            workerHandler.postDelayed(this, computeNextDelay(parsedLines))
        }
    }

    fun start() {
        AppLogger.getInstance().log(TAG, "▶️ startProgressUpdater() called, isRunning=$isRunning")
        if (!isRunning) {
            isRunning = true
            workerHandler.post(updateTask)
            AppLogger.getInstance().log(TAG, "✅ Posted updateTask to handler")
        } else {
            AppLogger.getInstance().log(TAG, "⚠️ Already running, skipping post")
        }
    }

    fun stop() {
        isRunning = false
        workerHandler.removeCallbacks(updateTask)
    }

    fun destroy() {
        stop()
        workerThread.quitSafely()
    }

    fun setDurationIfValid(value: Long) {
        if (value > 0) duration = value
    }

    fun resetLineIndex() {
        lastLineIndex = -1
    }

    fun resetProgressForTrackChange(newDuration: Long) {
        duration = newDuration.coerceAtLeast(0L)
        currentPosition = 0L
        lastPlaybackPosition = 0L
        lastPlaybackPositionAt = 0L
        lastPlaybackSpeed = 1f
        lastPlaybackState = PlaybackState.STATE_NONE
        lastControllerSyncAt = 0L
        repo.updateProgress(0L, duration)
    }

    private fun updateCurrentLyricLine(lines: List<OnlineLyricFetcher.LyricLine>) {
        val position = currentPosition
        var activeIndex = -1

        for (i in lines.indices) {
            val line = lines[i]
            if (position >= line.startTime && position < line.endTime) {
                activeIndex = i
                break
            }
            if (position < line.startTime) {
                break
            }
        }

        if (activeIndex == -1) {
            if (lastLineIndex != -1 || repo.liveCurrentLine.value != null) {
                lastLineIndex = -1
                repo.updateCurrentLine(null)
            }
            return
        }

        if (activeIndex != lastLineIndex) {
            lastLineIndex = activeIndex
            val line = lines[activeIndex]

            val metadata = repo.liveMetadata.value
            val source = metadata?.packageName?.let { appNameProvider(it) } ?: "Online Lyrics"
            val parsedInfo = repo.liveParsedLyrics.value
            val apiPath = parsedInfo?.apiPath ?: "Online API"
            repo.updateLyric(line.text, source, apiPath)
            repo.updateCurrentLine(line)
        }
    }

    private fun shouldSyncController(now: Long): Boolean {
        if (lastControllerSyncAt == 0L) return true
        val interval = when (lastPlaybackState) {
            PlaybackState.STATE_PLAYING -> CONTROLLER_SYNC_INTERVAL_PLAYING_MS
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING -> CONTROLLER_SYNC_INTERVAL_ACTIVE_MS
            else -> CONTROLLER_SYNC_INTERVAL_IDLE_MS
        }
        return now - lastControllerSyncAt >= interval
    }

    private fun computeNextDelay(parsedLines: List<OnlineLyricFetcher.LyricLine>?): Long {
        return when {
            !parsedLines.isNullOrEmpty() -> FAST_LOOP_INTERVAL_MS
            lastPlaybackState == PlaybackState.STATE_PLAYING -> NORMAL_LOOP_INTERVAL_MS
            else -> IDLE_LOOP_INTERVAL_MS
        }
    }

    private fun syncProgressFromController(shouldLog: Boolean = true, now: Long = SystemClock.elapsedRealtime()) {
        if (shouldLog) {
            AppLogger.getInstance().log(TAG, "⚙️ updateProgressFromController() called")
        }

        try {
            val mm = mediaSessionManagerProvider() ?: return
            val component = mediaComponentProvider() ?: return
            val controllers = mm.getActiveSessions(component)

            val currentMetadata = repo.liveMetadata.value
            val targetPackage = currentMetadata?.packageName

            val activeController = resolveActiveController(controllers, targetPackage, shouldLog)

            if (activeController != null) {
                val state = activeController.playbackState
                val meta = activeController.metadata
                val durationLong = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                if (durationLong > 0) duration = durationLong

                if (state != null) {
                    lastPlaybackState = state.state
                    lastPlaybackPosition = state.position
                    lastPlaybackPositionAt = state.lastPositionUpdateTime
                    lastPlaybackSpeed = state.playbackSpeed.takeUnless { it == 0f } ?: 1f
                    lastControllerSyncAt = now

                    updateEstimatedProgress(now)

                    if (shouldLog) {
                        AppLogger.getInstance().log(
                            TAG,
                            "📍 Progress [${activeController.packageName}]: ${currentPosition}ms / ${duration}ms"
                        )
                    }
                }
            } else {
                lastControllerSyncAt = now
                if (shouldLog) {
                    AppLogger.getInstance().log(TAG, "⚠️ No matching active MediaController found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Progress Update Error: ${e.message}")
        }
    }

    private fun updateEstimatedProgress(now: Long) {
        var estimated = lastPlaybackPosition
        if (lastPlaybackState == PlaybackState.STATE_PLAYING) {
            val positionBase = if (lastPlaybackPositionAt > 0L) lastPlaybackPositionAt else now
            val timeDelta = now - positionBase
            estimated += (timeDelta * lastPlaybackSpeed).toLong()
        }

        if (duration > 0 && estimated > duration) estimated = duration
        if (estimated < 0) estimated = 0

        if (estimated == currentPosition && repo.liveProgress.value?.duration == duration) {
            return
        }

        currentPosition = estimated
        repo.updateProgress(estimated, duration)
    }

    private fun resolveActiveController(
        controllers: List<MediaController>,
        targetPackage: String?,
        shouldLog: Boolean
    ): MediaController? {
        if (targetPackage != null) {
            val controller = controllers.firstOrNull { it.packageName == targetPackage }
            if (controller == null && shouldLog) {
                AppLogger.getInstance().d(TAG, "⚠️ Target package '$targetPackage' has no active controller session")
            }
            return controller
        }

        return controllers.firstOrNull {
            it.playbackState != null && it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
    }

    private companion object {
        private const val TAG = "ProgressSyncController"
        private const val FAST_LOOP_INTERVAL_MS = 250L
        private const val NORMAL_LOOP_INTERVAL_MS = 500L
        private const val IDLE_LOOP_INTERVAL_MS = 1000L
        private const val CONTROLLER_SYNC_INTERVAL_PLAYING_MS = 1000L
        private const val CONTROLLER_SYNC_INTERVAL_ACTIVE_MS = 750L
        private const val CONTROLLER_SYNC_INTERVAL_IDLE_MS = 1500L
    }
}
