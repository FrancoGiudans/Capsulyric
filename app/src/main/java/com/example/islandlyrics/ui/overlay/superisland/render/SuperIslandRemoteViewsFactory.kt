package com.example.islandlyrics.ui.overlay.superisland.render

import com.example.islandlyrics.ui.overlay.model.UIState
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.example.islandlyrics.R
import com.example.islandlyrics.ui.overlay.superisland.cache.SuperIslandIconCache
import com.example.islandlyrics.ui.overlay.superisland.cache.SuperIslandProgressBitmapCache

internal class SuperIslandRemoteViewsFactory(
    private val context: Context,
    private val iconCache: SuperIslandIconCache,
    private val progressBitmapCache: SuperIslandProgressBitmapCache
) {
    fun createExpand(
        state: UIState,
        subText: String,
        progressPercent: Int,
        progressBarColor: String,
        darkMode: Boolean,
        albumArt: Bitmap?,
        miPlayIntent: PendingIntent?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.super_island_custom_expand)
        val primaryTextColor = android.graphics.Color.parseColor(if (darkMode) "#FFFFFFFF" else "#FF111111")
        val secondaryTextColor = android.graphics.Color.parseColor(if (darkMode) "#B3FFFFFF" else "#99000000")
        val iconTintColor = primaryTextColor
        views.setTextViewText(R.id.custom_expand_title, SuperIslandTextResolver.primaryText(state))
        views.setTextViewText(R.id.custom_expand_subtitle, subText.ifEmpty { context.getString(R.string.channel_live_lyrics) })
        views.setTextColor(R.id.custom_expand_title, primaryTextColor)
        views.setTextColor(R.id.custom_expand_subtitle, secondaryTextColor)
        if (albumArt != null) {
            views.setImageViewBitmap(R.id.custom_expand_cover, iconCache.scaleBitmap(albumArt, 116))
        } else {
            views.setImageViewResource(R.id.custom_expand_cover, R.drawable.ic_music_note)
        }
        views.setImageViewBitmap(
            R.id.custom_expand_progress,
            progressBitmapCache.createWide(progressPercent, progressBarColor, darkMode)
        )
        views.setImageViewResource(
            R.id.custom_expand_play_pause,
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )
        views.setInt(R.id.custom_expand_prev, "setColorFilter", iconTintColor)
        views.setInt(R.id.custom_expand_play_pause, "setColorFilter", iconTintColor)
        views.setInt(R.id.custom_expand_next, "setColorFilter", iconTintColor)
        views.setInt(R.id.custom_expand_miplay, "setColorFilter", iconTintColor)
        views.setOnClickPendingIntent(R.id.custom_expand_prev, createMediaActionIntent(2100, "com.example.islandlyrics.ACTION_MEDIA_PREV"))
        views.setOnClickPendingIntent(R.id.custom_expand_play_pause, createMediaActionIntent(2101, "com.example.islandlyrics.ACTION_MEDIA_PLAY_PAUSE"))
        views.setOnClickPendingIntent(R.id.custom_expand_next, createMediaActionIntent(2102, "com.example.islandlyrics.ACTION_MEDIA_NEXT"))
        miPlayIntent?.let { views.setOnClickPendingIntent(R.id.custom_expand_miplay, it) }
        return views
    }

    fun createTiny(
        state: UIState,
        subText: String,
        progressPercent: Int,
        progressBarColor: String,
        darkMode: Boolean,
        albumArt: Bitmap?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.super_island_custom_tiny)
        val primaryTextColor = android.graphics.Color.parseColor(if (darkMode) "#FFFFFFFF" else "#FF111111")
        val secondaryTextColor = android.graphics.Color.parseColor(if (darkMode) "#B3FFFFFF" else "#99000000")
        views.setTextViewText(R.id.custom_tiny_title, SuperIslandTextResolver.compactText(state))
        views.setTextViewText(R.id.custom_tiny_subtitle, subText.ifEmpty { context.getString(R.string.channel_live_lyrics) })
        views.setTextColor(R.id.custom_tiny_title, primaryTextColor)
        views.setTextColor(R.id.custom_tiny_subtitle, secondaryTextColor)
        views.setImageViewBitmap(
            R.id.custom_tiny_progress,
            progressBitmapCache.createTiny(progressPercent, progressBarColor, darkMode)
        )

        if (albumArt != null) {
            views.setImageViewBitmap(R.id.custom_tiny_cover, iconCache.scaleBitmap(albumArt, 64))
        } else {
            views.setImageViewResource(R.id.custom_tiny_cover, R.drawable.ic_music_note)
        }
        return views
    }

    private fun createMediaActionIntent(requestCode: Int, action: String): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(action)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
