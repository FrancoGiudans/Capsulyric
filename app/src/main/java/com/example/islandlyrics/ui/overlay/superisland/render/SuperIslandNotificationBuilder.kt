package com.example.islandlyrics.ui.overlay.superisland.render

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import com.example.islandlyrics.R

internal class SuperIslandNotificationBuilder(
    private val context: Context,
    private val channelId: String
) {
    fun createBase(contentIntent: PendingIntent?): Notification.Builder {
        return applyImmediateForegroundBehavior(
            Notification.Builder(context, channelId)
        )
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
    }

    private fun applyImmediateForegroundBehavior(builder: Notification.Builder): Notification.Builder {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder
    }
}
