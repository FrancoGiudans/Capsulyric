package com.example.islandlyrics.service

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
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

            updateProgressFromController(shouldLog)

            val parsedLines = repo.liveParsedLyrics.value?.lines
            if (!parsedLines.isNullOrEmpty()) {
                updateCurrentLyricLine(parsedLines)
            }

            handler.postDelayed(this, 200)
        }
    }

    fun start() {
        AppLogger.getInstance().log(TAG, "▶️ startProgressUpdater() called, isRunning=$isRunning")
        if (!isRunning) {
            isRunning = true
            handler.post(updateTask)
            AppLogger.getInstance().log(TAG, "✅ Posted updateTask to handler")
        } else {
            AppLogger.getInstance().log(TAG, "⚠️ Already running, skipping post")
        }
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(updateTask)
    }

    fun setDurationIfValid(value: Long) {
        if (value > 0) duration = value
    }

    fun resetLineIndex() {
        lastLineIndex = -1
    }

    private fun updateCurrentLyricLine(lines: List<OnlineLyricFetcher.LyricLine>) {
        val position = currentPosition
        var index = -1

        for (i in lines.indices) {
            val line = lines[i]
            if (position >= line.startTime) {
                index = i
            } else {
                break
            }
        }

        if (index != -1 && index != lastLineIndex) {
            lastLineIndex = index
            val line = lines[index]

            val metadata = repo.liveMetadata.value
            val source = metadata?.packageName?.let { appNameProvider(it) } ?: "Online Lyrics"

            repo.updateLyric(line.text, source, "Online API")
            repo.updateCurrentLine(line)
        }
    }

    private fun updateProgressFromController(shouldLog: Boolean = true) {
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
                    val lastPosition = state.position
                    val lastUpdateTimeVal = state.lastPositionUpdateTime
                    val speed = state.playbackSpeed

                    var currentPos = lastPosition
                    if (state.state == PlaybackState.STATE_PLAYING) {
                        val timeDelta = android.os.SystemClock.elapsedRealtime() - lastUpdateTimeVal
                        currentPos += (timeDelta * speed).toLong()
                    }

                    if (duration > 0 && currentPos > duration) currentPos = duration
                    if (currentPos < 0) currentPos = 0

                    currentPosition = currentPos

                    if (shouldLog) {
                        AppLogger.getInstance().log(
                            TAG,
                            "📍 Progress [${activeController.packageName}]: ${currentPos}ms / ${duration}ms"
                        )
                    }
                    repo.updateProgress(currentPos, duration)
                }
            } else if (shouldLog) {
                AppLogger.getInstance().log(TAG, "⚠️ No matching active MediaController found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Progress Update Error: ${e.message}")
        }
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
    }
}
