package com.example.islandlyrics.ui.overlay.superisland
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.platform.XmsfBypassMode
import com.example.islandlyrics.runtime.service.LyricService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class SuperIslandNotificationDispatcher(
    private val context: Context,
    private val service: LyricService,
    private val manager: NotificationManager?,
    private val scope: CoroutineScope
) {
    private var networkCutJob: Job? = null
    private var networkCutSeq = 0L
    private val networkCutMutex = Mutex()
    private var aggressiveNetworkCutActive = false
    private var aggressiveTrackKey: String? = null
    private var aggressiveCutGeneration = 0L

    fun resetState() {
        aggressiveNetworkCutActive = false
        aggressiveTrackKey = null
    }

    fun stop(mode: XmsfBypassMode) {
        networkCutJob?.cancel()
        aggressiveTrackKey = null
        aggressiveNetworkCutActive = false
        if (mode.isEnabled) {
            restoreXmsfNetworkingAsync()
        }
    }

    fun onModeChanged(mode: XmsfBypassMode) {
        if (mode != XmsfBypassMode.AGGRESSIVE && aggressiveNetworkCutActive) {
            restoreXmsfNetworkingAsync()
        }
    }

    fun prepareForRender(
        currentTrackKey: String,
        isPlaying: Boolean,
        mode: XmsfBypassMode
    ) {
        if (!aggressiveNetworkCutActive) return

        val songEnded = !isPlaying
        val songChanged = aggressiveTrackKey != null && aggressiveTrackKey != currentTrackKey
        if (songEnded || mode != XmsfBypassMode.AGGRESSIVE) {
            restoreXmsfNetworkingAsync(expectedGeneration = aggressiveCutGeneration)
        } else if (songChanged) {
            // Auto-advance can deliver the next track while playback remains active.
            // Keep the aggressive bypass window open instead of briefly restoring XMSF.
            aggressiveTrackKey = currentTrackKey
            aggressiveCutGeneration++
        }
    }

    fun notify(
        notification: Notification,
        isFirst: Boolean,
        firstReason: String,
        currentTrackKey: String,
        isPlaying: Boolean,
        mode: XmsfBypassMode,
        customDurationMs: Int
    ) {
        val canUsePrimedNotify = isFirst &&
            firstReason != "lyricLineChanged" &&
            service.isForegroundSlotPrimed() &&
            manager != null

        fun traceDispatch(path: String) {
            if (BuildConfig.DEBUG) {
                AppLogger.getInstance().log(
                    "SuperIsland",
                    "[NotifyTrace] dispatch mode=$mode path=$path isFirst=$isFirst firstReason=$firstReason primed=${service.isForegroundSlotPrimed()} playing=$isPlaying trackKey=$currentTrackKey"
                )
            }
        }

        when (mode) {
            XmsfBypassMode.AGGRESSIVE -> notifyAggressive(
                notification = notification,
                isFirst = isFirst,
                firstReason = firstReason,
                currentTrackKey = currentTrackKey,
                isPlaying = isPlaying,
                canUsePrimedNotify = canUsePrimedNotify,
                traceDispatch = ::traceDispatch
            )
            XmsfBypassMode.STANDARD,
            XmsfBypassMode.CUSTOM -> notifyWithTimedCut(
                notification = notification,
                isFirst = isFirst,
                firstReason = firstReason,
                cutDurationMs = if (mode == XmsfBypassMode.CUSTOM) {
                    customDurationMs.toLong()
                } else {
                    XmsfBypassMode.STANDARD_DURATION_MS.toLong()
                },
                traceDispatch = ::traceDispatch
            )
            XmsfBypassMode.DISABLED -> notifyDirect(
                notification = notification,
                isFirst = isFirst,
                firstReason = firstReason,
                canUsePrimedNotify = canUsePrimedNotify,
                traceDispatch = ::traceDispatch
            )
        }
    }

    private fun notifyAggressive(
        notification: Notification,
        isFirst: Boolean,
        firstReason: String,
        currentTrackKey: String,
        isPlaying: Boolean,
        canUsePrimedNotify: Boolean,
        traceDispatch: (String) -> Unit
    ) {
        networkCutJob?.cancel()
        if (!isPlaying) {
            restoreXmsfNetworkingAsync(expectedGeneration = aggressiveCutGeneration)
            if (canUsePrimedNotify) {
                traceDispatch("notify.aggressive.notPlaying.primed")
                manager?.notify(NOTIFICATION_ID, notification)
            } else if (isFirst) {
                traceDispatch("startForeground.aggressive.notPlaying")
                service.startForegroundTracked(
                    NOTIFICATION_ID,
                    notification,
                    reason = "superIsland.aggressive.notPlaying.$firstReason"
                )
            } else {
                traceDispatch("notify.aggressive.notPlaying")
                manager?.notify(NOTIFICATION_ID, notification)
            }
            return
        }

        val cutGeneration = ++aggressiveCutGeneration
        networkCutJob = scope.launch(Dispatchers.IO) {
            networkCutMutex.withLock {
                if (cutGeneration != aggressiveCutGeneration) {
                    return@withLock
                }
                if (!aggressiveNetworkCutActive) {
                    withContext(NonCancellable) {
                        com.example.islandlyrics.integration.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, false)
                    }
                    aggressiveNetworkCutActive = true
                }
                aggressiveTrackKey = currentTrackKey
                if (manager != null) {
                    traceDispatch("notify.aggressive.cut")
                    manager.notify(NOTIFICATION_ID, notification)
                } else if (isFirst) {
                    traceDispatch("startForeground.aggressive.cutFallback")
                    service.startForegroundTracked(
                        NOTIFICATION_ID,
                        notification,
                        reason = "superIsland.aggressive.cutFallback.$firstReason"
                    )
                }
            }
        }
    }

    private fun notifyWithTimedCut(
        notification: Notification,
        isFirst: Boolean,
        firstReason: String,
        cutDurationMs: Long,
        traceDispatch: (String) -> Unit
    ) {
        networkCutJob?.cancel()
        val seq = ++networkCutSeq
        networkCutJob = scope.launch(Dispatchers.IO) {
            networkCutMutex.withLock {
                // Avoid cancelling Shizuku bind on slower devices by running in NonCancellable.
                withContext(NonCancellable) {
                    com.example.islandlyrics.integration.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, false)
                }
                if (manager != null) {
                    traceDispatch("notify.standard.cut")
                    manager.notify(NOTIFICATION_ID, notification)
                } else if (isFirst) {
                    traceDispatch("startForeground.standard.cutFallback")
                    service.startForegroundTracked(
                        NOTIFICATION_ID,
                        notification,
                        reason = "superIsland.standard.cutFallback.$firstReason"
                    )
                }
                try {
                    kotlinx.coroutines.delay(cutDurationMs)
                } catch (_: CancellationException) {
                    // A newer job will handle restore.
                }
                if (seq == networkCutSeq) {
                    withContext(NonCancellable) {
                        com.example.islandlyrics.integration.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, true)
                    }
                }
            }
        }
    }

    private fun notifyDirect(
        notification: Notification,
        isFirst: Boolean,
        firstReason: String,
        canUsePrimedNotify: Boolean,
        traceDispatch: (String) -> Unit
    ) {
        if (canUsePrimedNotify) {
            traceDispatch("notify.disabled.primed")
            manager?.notify(NOTIFICATION_ID, notification)
        } else if (isFirst) {
            traceDispatch("startForeground.disabled")
            service.startForegroundTracked(
                NOTIFICATION_ID,
                notification,
                reason = "superIsland.disabled.$firstReason"
            )
        } else {
            traceDispatch("notify.disabled")
            manager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun restoreXmsfNetworkingAsync() {
        restoreXmsfNetworkingAsync(expectedGeneration = null)
    }

    private fun restoreXmsfNetworkingAsync(expectedGeneration: Long?) {
        scope.launch {
            withContext(Dispatchers.IO) {
                networkCutMutex.withLock {
                    if (expectedGeneration != null && expectedGeneration != aggressiveCutGeneration) {
                        return@withLock
                    }
                    aggressiveTrackKey = null
                    aggressiveNetworkCutActive = false
                    withContext(NonCancellable) {
                        com.example.islandlyrics.integration.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, true)
                    }
                }
            }
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}
