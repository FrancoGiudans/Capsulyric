package com.example.islandlyrics.service

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.lyric.OnlineLyricFetcher
import kotlin.math.abs

class ProgressSyncController(
    private val handler: Handler,
    private val repo: LyricRepository,
    private val appNameProvider: (String) -> String,
    private val mediaSessionManagerProvider: () -> MediaSessionManager?,
    private val mediaComponentProvider: () -> ComponentName?
) {
    private data class ExternalProgressHint(
        val packageName: String,
        val position: Long,
        val duration: Long,
        val receivedAtMs: Long
    )

    private var isRunning = false
    private var currentPosition = 0L
    private var duration = 0L
    private var lastLineIndex = -1
    private var consecutiveResolveMisses = 0
    private var lastResolvedControllerKey: String? = null
    private var lastRecheckRequestAtMs = 0L
    private var externalProgressHint: ExternalProgressHint? = null

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

    fun destroy() {
        stop()
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
        repo.updateProgress(0L, duration)
    }

    fun syncExternalProgress(packageName: String, position: Long, duration: Long) {
        if (packageName.isBlank() || duration <= 0L) return

        externalProgressHint = ExternalProgressHint(
            packageName = packageName,
            position = position.coerceIn(0L, duration),
            duration = duration,
            receivedAtMs = SystemClock.elapsedRealtime()
        )

        if (isRunning) {
            handler.post {
                val metadata = repo.liveMetadata.value
                maybeApplyExternalProgress(metadata, shouldLog = false)
            }
        }
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

    private fun updateProgressFromController(shouldLog: Boolean = true) {
        if (shouldLog) {
            AppLogger.getInstance().log(TAG, "⚙️ updateProgressFromController() called")
        }

        try {
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
            } else {
                consecutiveResolveMisses++
                val usedExternalFallback = maybeApplyExternalProgress(currentMetadata, shouldLog)
                maybeTriggerSessionRecheck(currentMetadata?.packageName, shouldLog)
                if (shouldLog && !usedExternalFallback) {
                    AppLogger.getInstance().log(TAG, "⚠️ No matching active MediaController found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Progress Update Error: ${e.message}")
        }
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

    private fun maybeApplyExternalProgress(
        currentMetadata: LyricRepository.MediaInfo?,
        shouldLog: Boolean
    ): Boolean {
        val hint = externalProgressHint ?: return false
        val targetPackage = currentMetadata?.packageName ?: return false
        if (hint.packageName != targetPackage) return false

        val ageMs = SystemClock.elapsedRealtime() - hint.receivedAtMs
        if (ageMs > EXTERNAL_PROGRESS_TTL_MS) return false

        val expectedDuration = currentMetadata.duration.takeIf { it > 0L } ?: hint.duration
        if (currentMetadata.duration > 0L && hint.duration > 0L &&
            abs(currentMetadata.duration - hint.duration) > EXTERNAL_DURATION_TOLERANCE_MS
        ) {
            return false
        }

        currentPosition = hint.position.coerceIn(0L, expectedDuration)
        duration = expectedDuration
        repo.updateProgress(currentPosition, duration)

        if (shouldLog) {
            AppLogger.getInstance().log(
                TAG,
                "🩹 Applied external progress fallback [$targetPackage]: ${currentPosition}ms / ${duration}ms"
            )
        }
        return true
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
        private const val EXTERNAL_PROGRESS_TTL_MS = 3_000L
        private const val EXTERNAL_DURATION_TOLERANCE_MS = 5_000L
    }
}
