package com.example.islandlyrics.ui.overlay.superisland.intent

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.feature.mediacontrol.MediaControlActivity

internal class SuperIslandIntentFactory(
    private val context: Context
) {
    fun createContentIntent(clickStyle: String): PendingIntent {
        return when (clickStyle) {
            "media_controls" -> {
                val intent = Intent(context, MediaControlActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            "open_playing_app" -> createFallbackIntent()
            else -> createFallbackIntent()
        }
    }

    fun resolveContentIntent(
        clickStyle: String,
        mediaPackage: String,
        cachedContentIntent: PendingIntent?
    ): PendingIntent {
        return when (clickStyle) {
            "open_playing_app" -> {
                if (mediaPackage.isNotBlank()) {
                    createOpenAppIntent(mediaPackage)
                } else {
                    createFallbackIntent()
                }
            }
            else -> cachedContentIntent ?: createFallbackIntent()
        }
    }

    fun createMiPlayIntent(): PendingIntent? {
        return runCatching {
            PendingIntent.getActivity(
                context,
                4,
                Intent().apply {
                    component = android.content.ComponentName(
                        "miui.systemui.plugin",
                        "miui.systemui.miplay.MiPlayDetailActivity"
                    )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }.getOrNull()
    }

    private fun createOpenAppIntent(packageName: String): PendingIntent {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            createFallbackIntent()
        }
    }

    private fun createFallbackIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}
