package com.example.islandlyrics.ui.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.islandlyrics.R
import com.example.islandlyrics.core.logging.LogManager
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.service.LyricService

class ColorOsFluidCloudHandler(
    private val context: Context,
    private val service: LyricService
) {
    private val manager: NotificationManager? = context.getSystemService(NotificationManager::class.java)
    private val bridge = ColorOsFluidCloudBridge(context)

    var isRunning = false
        private set

    private var lastSignature = ""
    private var lastNotifyTime = 0L
    private var isFirstNotification = true
    private var loggedMissingSdk = false
    private var loggedUnsupported = false

    private val contentIntent: PendingIntent by lazy {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    init {
        createChannel()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        lastSignature = ""
        lastNotifyTime = 0L
        isFirstNotification = true
        loggedMissingSdk = false
        loggedUnsupported = false
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        manager?.cancel(NOTIFICATION_ID)
    }

    fun render(state: UIState) {
        if (!isRunning) return

        val signature = listOf(
            state.displayLyric,
            state.fullLyric,
            state.title,
            state.artist,
            state.isPlaying,
            state.progressCurrent
        ).joinToString("|")
        val now = android.os.SystemClock.elapsedRealtime()
        if (signature == lastSignature && now - lastNotifyTime < THROTTLE_MS) return

        lastSignature = signature
        lastNotifyTime = now

        if (!bridge.isSdkAvailable()) {
            if (!loggedMissingSdk) {
                LogManager.getInstance().w(
                    context,
                    TAG,
                    "SeedlingSupportSDK is not available; using standard notification fallback."
                )
                loggedMissingSdk = true
            }
        } else if (!bridge.isFluidCloudSupported()) {
            if (!loggedUnsupported) {
                LogManager.getInstance().w(
                    context,
                    TAG,
                    "This ColorOS build does not report Fluid Cloud support; using fallback."
                )
                loggedUnsupported = true
            }
        } else {
            bridge.updateExistingCards(state)
        }

        postFallbackNotification(state)
    }

    private fun postFallbackNotification(state: UIState) {
        val title = state.title.ifBlank { context.getString(R.string.channel_live_lyrics) }
        val text = state.fullLyric.ifBlank {
            state.displayLyric.ifBlank { state.artist.ifBlank { context.getString(R.string.channel_live_lyrics) } }
        }
        val shortText = state.displayLyric
            .ifBlank { text }
            .take(ColorOsFluidCloudBridge.CAPSULE_TEXT_LIMIT)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(context.getString(R.string.capsule_mode_fluid_cloud))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(state.albumColor)

        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(shortText)
        }

        if (state.progressCurrent >= 0) {
            builder.setProgress(100, state.progressCurrent.coerceIn(0, 100), false)
        }

        val notification = builder.build()
        if (isFirstNotification && !service.isForegroundSlotPrimed()) {
            service.startForegroundTracked(NOTIFICATION_ID, notification, "fluidCloud.first")
        } else {
            manager?.notify(NOTIFICATION_ID, notification)
        }
        isFirstNotification = false
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_live_lyrics),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_live_lyrics_desc)
            setSound(null, null)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager?.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ColorOsFluidCloudHandler"
        private const val CHANNEL_ID = "coloros_fluid_cloud_channel"
        private const val NOTIFICATION_ID = 1003
        private const val THROTTLE_MS = 1000L
    }
}
