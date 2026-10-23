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
            .setVisibility(LockScreenNotificationPolicy.visibility(service))
            .setContentIntent(createMainActivityIntent())
            .build()
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

    private companion object {
        private const val TAG = "LyricForegroundController"
        private const val CHANNEL_ID = "lyric_capsule_channel"
        private const val NOTIFICATION_ID = 1001
        private const val WARM_DEBOUNCE_MS = 1500L
    }
}
