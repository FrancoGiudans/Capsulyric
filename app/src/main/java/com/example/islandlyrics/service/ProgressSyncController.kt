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
    private val repo: LyricRepository,
    private val appNameProvider: (String) -> String,
    private val mediaSessionManagerProvider: () -> MediaSessionManager?,
    private val mediaComponentProvider: () -> ComponentName?
) {
    private var isRunning = false
    private var currentPosition = 0L
    private var duration = 0L
    private var lastLineIndex = -1
    private var consecutiveResolveMisses = 0
    private var lastResolvedControllerKey: String? = null
    private var lastRecheckRequestAtMs = 0L
    private var lastControllerPollAtMs = 0L
    private var lastPlaybackState: PlaybackState? = null
    private var lastProgressDispatchPosition = -1L
    private var lastProgressDispatchDuration = -1L

    // Dedicated background thread for progress polling to avoid main thread IPC blocking
    private val handlerThread = HandlerThread("ProgressSync").apply { start() }
    private val handler = Handler(handlerThread.looper)

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

            updateProgress(shouldLog)

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

    fun destroy() {
        stop()
        handlerThread.quitSafely()
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
        consecutiveResolveMisses = 0
        lastResolvedControllerKey = null
        lastPlaybackState = null
        lastControllerPollAtMs = 0L
        resetProgressDispatchWindow()
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

    private fun updateProgress(shouldLog: Boolean = true) {
        val now = SystemClock.elapsedRealtime()
        val state = lastPlaybackState
        val shouldPollController = state == null ||
                now - lastControllerPollAtMs >= CONTROLLER_POLL_INTERVAL_MS ||
                lastResolvedControllerKey == null

        if (shouldPollController) {
            updateProgressFromController(shouldLog)
        } else {
            updateProgressFromCachedState(state, now)
        }
    }

    private fun updateProgressFromController(shouldLog: Boolean = true) {
        if (shouldLog) {
            AppLogger.getInstance().log(TAG, "⚙️ updateProgressFromController() called")
        }

        try {
            lastControllerPollAtMs = SystemClock.elapsedRealtime()
            val mm = mediaSessionManagerProvider() ?: return
            val component = mediaComponentProvider() ?: return
            val controllers = mm.getActiveSessions(component)

            val currentMetadata = repo.liveMetadata.value
            val activeController = resolveActiveController(controllers, currentMetadata, shouldLog)

            if (activeController != null) {
                consecutiveResolveMisses = 0
                val state = activeController.playbackState
                val meta = activeController.metadata
                val durationLong = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                if (durationLong > 0) duration = durationLong

                if (state != null) {
                    lastPlaybackState = state
                    val currentPos = computeCurrentPosition(state, SystemClock.elapsedRealtime())

                    if (shouldLog) {
                        AppLogger.getInstance().log(
                            TAG,
                            "📍 Progress [${activeController.packageName}]: ${currentPos}ms / ${duration}ms"
                        )
                    }
                    dispatchProgressIfNeeded(currentPos, force = true)
                }
            } else {
                consecutiveResolveMisses++
                lastPlaybackState = null
                maybeTriggerSessionRecheck(currentMetadata?.packageName, shouldLog)
                if (shouldLog) {
                    AppLogger.getInstance().log(TAG, "⚠️ No matching active MediaController found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Progress Update Error: ${e.message}")
        }
    }

    private fun updateProgressFromCachedState(state: PlaybackState, now: Long) {
        val currentPos = computeCurrentPosition(state, now)
        dispatchProgressIfNeeded(currentPos, force = false)
    }

    private fun computeCurrentPosition(state: PlaybackState, now: Long): Long {
        var currentPos = state.position
        if (state.state == PlaybackState.STATE_PLAYING) {
            val timeDelta = now - state.lastPositionUpdateTime
            currentPos += (timeDelta * state.playbackSpeed).toLong()
        }

        if (duration > 0 && currentPos > duration) currentPos = duration
        if (currentPos < 0) currentPos = 0
        currentPosition = currentPos
        return currentPos
    }

    private fun dispatchProgressIfNeeded(position: Long, force: Boolean) {
        val durationChanged = duration != lastProgressDispatchDuration
        if (!force && !durationChanged && kotlin.math.abs(position - lastProgressDispatchPosition) < PROGRESS_DISPATCH_STEP_MS) {
            return
        }

        lastProgressDispatchPosition = position
        lastProgressDispatchDuration = duration
        repo.updateProgress(position, duration)
    }

    private fun resetProgressDispatchWindow() {
        lastProgressDispatchPosition = -1L
        lastProgressDispatchDuration = -1L
    }

    private fun resolveActiveController(
        controllers: List<MediaController>,
        currentMetadata: LyricRepository.MediaInfo?,
        shouldLog: Boolean
    ): MediaController? {
        val targetPackage = currentMetadata?.packageName
        val controller = MediaControllerSelection.selectProgressTarget(
            controllers = controllers,
            targetPackage = targetPackage,
            targetTitle = currentMetadata?.rawTitle ?: currentMetadata?.title,
            targetArtist = currentMetadata?.rawArtist ?: currentMetadata?.artist,
            targetDuration = currentMetadata?.duration ?: duration
        )

        if (controller == null) {
            if (!targetPackage.isNullOrBlank() && shouldLog) {
                AppLogger.getInstance().d(TAG, "⚠️ Target package '$targetPackage' has no active controller session")
            }
            lastResolvedControllerKey = null
            return null
        }

        val resolvedKey = buildControllerKey(controller)
        if (resolvedKey != lastResolvedControllerKey) {
            if (shouldLog) {
                AppLogger.getInstance().log(
                    TAG,
                    "🎯 Resolved controller -> ${controller.packageName} | ${controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"}"
                )
            }
            lastResolvedControllerKey = resolvedKey
        }

        return controller
    }

    private fun maybeTriggerSessionRecheck(targetPackage: String?, shouldLog: Boolean) {
        if (targetPackage.isNullOrBlank()) return
        if (consecutiveResolveMisses < RECHECK_THRESHOLD) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastRecheckRequestAtMs < RECHECK_COOLDOWN_MS) return

        lastRecheckRequestAtMs = now
        if (shouldLog) {
            AppLogger.getInstance().log(
                TAG,
                "🔁 Controller resolve missed $consecutiveResolveMisses times for $targetPackage, requesting session recheck"
            )
        }
        MediaMonitorService.triggerRecheck()
    }

    private fun buildControllerKey(controller: MediaController): String {
        val metadata = controller.metadata
        return buildString {
            append(controller.packageName)
            append('|')
            append(controller.playbackState?.state ?: -1)
            append('|')
            append(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "")
            append('|')
            append(metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "")
            append('|')
            append(metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
        }
    }

    private companion object {
        private const val TAG = "ProgressSyncController"
        private const val RECHECK_THRESHOLD = 5
        private const val RECHECK_COOLDOWN_MS = 1_500L
        private const val CONTROLLER_POLL_INTERVAL_MS = 1_000L
        private const val PROGRESS_DISPATCH_STEP_MS = 250L
    }
}
