package com.example.islandlyrics.runtime.foreground

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.logging.LogManager
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.runtime.service.LyricService

internal class LyricForegroundController(
    private val service: LyricService
) {
    @Volatile
    private var foregroundSlotPrimed = false
    private var lastWarmAttemptAtMs = 0L
    private var lastWarmTrackKey: String? = null
    private var currentChannelId = CHANNEL_ID
    private var invisibleToggle = false

    fun createNotificationChannel() {
        val manager = service.getSystemService(NotificationManager::class.java) ?: return
        val serviceChannel = NotificationChannel(
            currentChannelId,
            "Lyrics (Capsule / Super Island)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = service.getString(R.string.channel_live_lyrics_desc)
            setSound(null, null)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(serviceChannel)
    }

    fun isForegroundSlotPrimed(): Boolean = foregroundSlotPrimed

    fun startForegroundTracked(notificationId: Int, notification: Notification, reason: String) {
        if (BuildConfig.DEBUG) {
            AppLogger.getInstance().log(
                TAG,
                "[NotifyTrace] startForegroundTracked reason=$reason primedBefore=$foregroundSlotPrimed notificationId=$notificationId"
            )
        }
        service.startForeground(notificationId, notification)
        foregroundSlotPrimed = true
    }

    fun clearForegroundSlotPrimed(reason: String) {
        if (BuildConfig.DEBUG && foregroundSlotPrimed) {
            AppLogger.getInstance().log(
                TAG,
                "[NotifyTrace] clearForegroundSlot reason=$reason"
            )
        }
        foregroundSlotPrimed = false
    }

    fun maybeWarmForegroundSlot(action: String) {
        val repo = LyricRepository.getInstance()
        val metadata = repo.liveMetadata.value
        val isPlaying = repo.isPlaying.value == true
        val hasMetadata = metadata?.title?.isNotBlank() == true || metadata?.artist?.isNotBlank() == true
        val trackKey = metadata?.let { "${it.packageName}|${it.title}|${it.artist}" }
        val activeNotificationPresent = hasActivePrimaryNotification()
        val now = SystemClock.elapsedRealtime()
        val warmThrottled = lastWarmTrackKey == trackKey && now - lastWarmAttemptAtMs < WARM_DEBOUNCE_MS
        val shouldWarm = action == "null" &&
            isPlaying &&
            hasMetadata &&
            !foregroundSlotPrimed &&
            !activeNotificationPresent &&
            !warmThrottled

        if (BuildConfig.DEBUG) {
            AppLogger.getInstance().log(
                TAG,
                "[NotifyTrace] maybeWarmForegroundSlot action=$action shouldWarm=$shouldWarm playing=$isPlaying hasMetadata=$hasMetadata primed=$foregroundSlotPrimed active=$activeNotificationPresent throttled=$warmThrottled trackKey=${trackKey ?: "<none>"}"
            )
        }

        if (!shouldWarm) {
            return
        }

        lastWarmAttemptAtMs = now
        lastWarmTrackKey = trackKey
        warmForegroundSlot()
    }

    fun buildNotification(title: String, text: String, subText: String): Notification {
        return buildModernNotification(title, text, subText)
    }

    fun inspectNotification(notification: Notification, nm: NotificationManager) {
        val intent = Intent("com.example.islandlyrics.STATUS_UPDATE")
        intent.setPackage(service.packageName)

        val modeStatus = "🔵 Modern (API 36)"
        intent.putExtra("status", modeStatus)

        var hasChar = false
        var isPromoted = false
        if (Build.VERSION.SDK_INT >= 36) {
            hasChar = notification.hasPromotableCharacteristics()
            intent.putExtra("hasPromotable", hasChar)

            val flagVal = Notification.FLAG_PROMOTED_ONGOING
            isPromoted = (notification.flags and flagVal) != 0
            intent.putExtra("isPromoted", isPromoted)
        }

        service.sendBroadcast(intent)

        LyricRepository.getInstance().mergeDiagnostics { old ->
            old.copy(
                hasPromotableChar = hasChar,
                isCurrentlyPromoted = isPromoted
            )
        }

        val canPost = if (Build.VERSION.SDK_INT >= 36) {
            nm.canPostPromotedNotifications()
        } else {
            false
        }

        var channelStatus = "Unknown"
        val channel = nm.getNotificationChannel(currentChannelId)
        if (channel != null) {
            channelStatus = "Imp: ${channel.importance}"
        }

        val diagMsg = String.format(
            "[API] Perm: %b | Promotable: %b | Flag: %b\n[Channel] %s\n[Text] %s",
            canPost,
            hasChar,
            isPromoted,
            channelStatus,
            getSmartSnippet(notification.extras.getString(Notification.EXTRA_TITLE) ?: "")
        )
        broadcastLog(diagMsg)
    }

    fun checkAndHealChannel(nm: NotificationManager) {
        val channel = nm.getNotificationChannel(currentChannelId)
        if (channel != null && channel.importance < NotificationManager.IMPORTANCE_HIGH) {
            broadcastLog("⚠️ Channel Demoted (Imp=${channel.importance}). Resurrecting...")
            nm.deleteNotificationChannel(currentChannelId)
            currentChannelId = CHANNEL_ID + "_" + System.currentTimeMillis()
            createNotificationChannel()
        }
    }

    private fun warmForegroundSlot() {
        try {
            if (BuildConfig.DEBUG) {
                AppLogger.getInstance().log(
                    TAG,
                    "[NotifyTrace] warmForegroundSlot channel=$currentChannelId notificationId=$NOTIFICATION_ID"
                )
            }
            startForegroundTracked(
                NOTIFICATION_ID,
                buildPlainWarmNotification(),
                reason = "warmForegroundSlot"
            )
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Failed to warm foreground slot: ${e.message}")
        }
    }

    private fun hasActivePrimaryNotification(): Boolean {
        return try {
            val manager = service.getSystemService(NotificationManager::class.java) ?: return false
            manager.activeNotifications.any { record ->
                record.id == NOTIFICATION_ID && record.packageName == service.packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun buildPlainWarmNotification(): Notification {
        return applyImmediateForegroundBehavior(
            Notification.Builder(service, currentChannelId)
        )
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle("")
            .setContentText("")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(createMainActivityIntent())
            .build()
    }

    private fun buildModernNotification(title: String, text: String, subText: String): Notification {
        LogManager.getInstance().d(service, TAG, "Building Modern Notification (ProgressStyle)")

        val builder = applyImmediateForegroundBehavior(
            Notification.Builder(service, currentChannelId)
        )
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(createMainActivityIntent())

        if (Build.VERSION.SDK_INT >= 36) {
            val style = Notification.ProgressStyle()
            style.setStyledByProgress(true)
            val segment = Notification.ProgressStyle.Segment(1)
            style.setProgressSegments(listOf(segment))
            builder.style = style
        }

        val rawText = if (text.isNotEmpty()) text else "Capsulyric"
        invisibleToggle = !invisibleToggle
        val chipText = rawText + if (invisibleToggle) "\u200B" else ""

        applyLiveAttributes(builder, chipText)

        val notification = builder.build()
        applyPromotedFlagFallback(notification)
        return notification
    }

    private fun createMainActivityIntent(): PendingIntent {
        return PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun applyImmediateForegroundBehavior(builder: Notification.Builder): Notification.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder
    }

    private fun applyLiveAttributes(builder: Notification.Builder, text: String) {
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText(text)
        }
        builder.extras.putBoolean("android.app.extra.REQUEST_PROMOTED_ONGOING", true)
    }

    private fun applyPromotedFlagFallback(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 36) {
            notification.flags = notification.flags or Notification.FLAG_PROMOTED_ONGOING
        }
    }

    private fun broadcastLog(msg: String) {
        val intent = Intent("com.example.islandlyrics.DIAG_UPDATE")
        intent.putExtra("log_msg", msg)
        intent.setPackage(service.packageName)
        service.sendBroadcast(intent)
    }

    private fun getSmartSnippet(text: String): String {
        return if (text.length > 20) text.substring(0, 20) + "..." else text
    }

    private companion object {
        private const val TAG = "LyricForegroundController"
        private const val CHANNEL_ID = "lyric_capsule_channel"
        private const val NOTIFICATION_ID = 1001
        private const val WARM_DEBOUNCE_MS = 1500L
    }
}
