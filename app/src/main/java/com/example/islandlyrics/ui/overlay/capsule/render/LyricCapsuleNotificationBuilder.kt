package com.example.islandlyrics.ui.overlay.capsule.render

import android.app.Notification
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.islandlyrics.R
import com.example.islandlyrics.core.logging.LogManager
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.ui.overlay.capsule.config.LiveUpdateTextLimitConfig
import com.example.islandlyrics.ui.overlay.capsule.config.LyricCapsulePreferencesCache
import com.example.islandlyrics.ui.overlay.capsule.intent.LyricCapsuleIntentFactory
import com.example.islandlyrics.ui.overlay.capsule.intent.LyricCapsuleIntents
import com.example.islandlyrics.ui.overlay.config.OneUiCapsuleColorMode
import com.example.islandlyrics.ui.overlay.config.OverlayRenderDefaults
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandLyricLayout

internal class LyricCapsuleNotificationBuilder(
    private val context: Context,
    private val preferences: LyricCapsulePreferencesCache,
    private val intentFactory: LyricCapsuleIntentFactory,
    private val dynamicIconCache: LyricCapsuleDynamicIconCache
) {
    fun build(
        displayLyric: String,
        fullLyric: String,
        title: String,
        sourceApp: String,
        progressPercent: Int,
        iconFrame: LyricCapsuleIconFrame,
        albumColor: Int,
        intents: LyricCapsuleIntents
    ): Notification {
        val builder = applyImmediateForegroundBehavior(
            NotificationCompat.Builder(context, CHANNEL_ID)
        )
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        builder.setContentIntent(intents.contentIntent)
        if (preferences.clickStyle == "open_playing_app") {
            builder.setContentIntent(intentFactory.resolveContentIntent(sourceApp))
        }
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
        }

        when (preferences.actionStyle) {
            "media_controls" -> {
                builder.addAction(0, context.getString(R.string.action_pause), intents.pauseIntent)
                builder.addAction(0, context.getString(R.string.action_next), intents.nextIntent)
            }
            "miplay" -> {
                builder.addAction(0, context.getString(R.string.action_miplay), intents.miplayIntent)
            }
        }

        val currentLyric = fullLyric.ifEmpty { "Waiting for lyrics..." }
        val shortText = displayLyric.ifEmpty { currentLyric }

        builder.setContentTitle(title)
        builder.setContentText(currentLyric)
        builder.setSubText(sourceApp)

        applyProgressStyle(
            builder = builder,
            shortText = shortText,
            progressPercent = progressPercent,
            albumColor = albumColor
        )

        return builder.build().apply {
            if (preferences.useDynamicIcon) {
                dynamicIconCache.inject(this, preferences.iconStyle, iconFrame)
            }
        }
    }

    private fun applyProgressStyle(
        builder: NotificationCompat.Builder,
        shortText: String,
        progressPercent: Int,
        albumColor: Int
    ) {
        try {
            val barColor = if (preferences.useAlbumColor) albumColor else OverlayRenderDefaults.COLOR_PRIMARY
            val barColorIndeterminate = if (preferences.useAlbumColor) albumColor else COLOR_TERTIARY

            if (RomUtils.getRomType() == "OneUI") {
                builder.setColor(
                    OneUiCapsuleColorMode.resolveColor(
                        mode = preferences.oneUiCapsuleColorMode,
                        albumColor = albumColor
                    )
                )
            } else {
                builder.setColor(barColor)
            }

            if (Build.VERSION.SDK_INT >= 36) {
                if (progressPercent >= 0) {
                    val segment = NotificationCompat.ProgressStyle.Segment(100)
                    segment.setColor(barColor)

                    val progressStyle = NotificationCompat.ProgressStyle()
                        .setProgressSegments(arrayListOf(segment))
                        .setStyledByProgress(true)
                        .setProgress(progressPercent.coerceIn(0, 100))

                    builder.setStyle(progressStyle)
                } else {
                    val segment = NotificationCompat.ProgressStyle.Segment(100)
                    segment.setColor(barColorIndeterminate)

                    val progressStyle = NotificationCompat.ProgressStyle()
                        .setProgressSegments(arrayListOf(segment))
                        .setProgressIndeterminate(true)

                    builder.setStyle(progressStyle)
                }

                val shortCriticalText = SuperIslandLyricLayout.takeByWeight(
                    text = shortText,
                    maxWeight = if (preferences.liveUpdateTextLimitsEnabled) {
                        preferences.liveUpdateTextWeight
                    } else {
                        LiveUpdateTextLimitConfig.defaultWeight()
                    }
                ).ifEmpty { shortText }
                builder.setShortCriticalText(shortCriticalText)
            }
        } catch (e: Exception) {
            LogManager.getInstance().e(context, TAG, "ProgressStyle failed: $e")
        }
    }

    private fun applyImmediateForegroundBehavior(
        builder: NotificationCompat.Builder
    ): NotificationCompat.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder
    }

    private companion object {
        const val TAG = "LyricCapsule"
        const val CHANNEL_ID = "lyric_capsule_channel"
        const val COLOR_TERTIARY = 0xFFBDBDBD.toInt()
    }
}
