package com.example.islandlyrics.ui.overlay.capsule.intent

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.feature.mediacontrol.MediaControlActivity
import com.example.islandlyrics.runtime.service.LyricService

internal data class LyricCapsuleIntents(
    val contentIntent: PendingIntent?,
    val pauseIntent: PendingIntent?,
    val nextIntent: PendingIntent?,
    val miplayIntent: PendingIntent?
)

internal class LyricCapsuleIntentFactory(
    private val context: Context
) {
    fun create(clickStyle: String): LyricCapsuleIntents {
        return LyricCapsuleIntents(
            contentIntent = createContentIntent(clickStyle),
            pauseIntent = PendingIntent.getService(
                context,
                1,
                Intent(context, LyricService::class.java).setAction("ACTION_MEDIA_PAUSE"),
                PendingIntent.FLAG_IMMUTABLE
            ),
            nextIntent = PendingIntent.getService(
                context,
                2,
                Intent(context, LyricService::class.java).setAction("ACTION_MEDIA_NEXT"),
                PendingIntent.FLAG_IMMUTABLE
            ),
            miplayIntent = PendingIntent.getActivity(
                context,
                3,
                Intent().apply {
                    component = android.content.ComponentName(
                        "miui.systemui.plugin",
                        "miui.systemui.miplay.MiPlayDetailActivity"
                    )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    fun resolveContentIntent(sourceApp: String): PendingIntent {
        return if (sourceApp.isNotBlank()) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(sourceApp)
            if (launchIntent != null) {
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
        } else {
            createFallbackIntent()
        }
    }

    private fun createContentIntent(clickStyle: String): PendingIntent {
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
