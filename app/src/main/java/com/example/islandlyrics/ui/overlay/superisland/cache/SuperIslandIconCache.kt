package com.example.islandlyrics.ui.overlay.superisland.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import com.example.islandlyrics.lyrics.state.LyricRepository

internal class SuperIslandIconCache(
    private val context: Context
) {
    var avatarIcon: Icon? = null
        private set
    var islandIcon: Icon? = null
        private set
    var islandSmallIcon: Icon? = null
        private set
    var shareIcon: Icon? = null
        private set
    var appIcon: Icon? = null
        private set
    var prevIcon: Icon? = null
        private set
    var prevIconDark: Icon? = null
        private set
    var playPauseIcon: Icon? = null
        private set
    var playPauseIconDark: Icon? = null
        private set
    var nextIcon: Icon? = null
        private set
    var nextIconDark: Icon? = null
        private set

    private var lastAlbumArtHash = 0
    private var lastAppHash = 0
    private var lastActionsHash = 0
    private val bitmapScaler = SuperIslandBitmapScaler()
    private val actionIconRenderer = SuperIslandActionIconRenderer(context)
    private val appIconLoader = SuperIslandAppIconLoader(context)

    fun reset() {
        lastAlbumArtHash = 0
        lastAppHash = 0
        lastActionsHash = 0
        avatarIcon = null
        islandIcon = null
        islandSmallIcon = null
        shareIcon = null
        appIcon = null
        prevIcon = null
        prevIconDark = null
        playPauseIcon = null
        playPauseIconDark = null
        nextIcon = null
        nextIconDark = null
        bitmapScaler.clear()
    }

    fun update(
        metadata: LyricRepository.MediaInfo?,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        actionStyle: String,
        mediaButtonLayout: String,
        notificationStyle: String
    ) {
        updateAlbumArtIcons(metadata, albumArt)
        updateActionIcons(isPlaying, actionStyle, mediaButtonLayout, notificationStyle)
    }

    fun scaleBitmap(src: Bitmap, targetSize: Int): Bitmap {
        return bitmapScaler.scaleRound(src, targetSize)
    }

    private fun updateAlbumArtIcons(
        metadata: LyricRepository.MediaInfo?,
        albumArt: Bitmap?
    ) {
        val albumArtHash = albumArt?.hashCode() ?: 0
        if (albumArtHash != lastAlbumArtHash) {
            if (albumArt != null) {
                avatarIcon = Icon.createWithBitmap(scaleBitmap(albumArt, 480))
                islandIcon = Icon.createWithBitmap(scaleBitmap(albumArt, 120))
                islandSmallIcon = Icon.createWithBitmap(scaleBitmap(albumArt, 88))
                shareIcon = Icon.createWithBitmap(scaleBitmap(albumArt, 224))
            } else {
                avatarIcon = null
                islandIcon = null
                islandSmallIcon = null
                shareIcon = null
            }
            lastAlbumArtHash = albumArtHash
            updateAppIconIfNeeded(metadata, forceIfMissing = true)
        } else {
            updateAppIconIfNeeded(metadata, forceIfMissing = false)
        }
    }

    private fun updateAppIconIfNeeded(
        metadata: LyricRepository.MediaInfo?,
        forceIfMissing: Boolean
    ) {
        val appIconHash = metadata?.packageName?.hashCode() ?: 0
        if (appIconHash == lastAppHash && (!forceIfMissing || appIcon != null)) return

        val appIconBitmap = appIconLoader.load(metadata?.packageName)
        appIcon = appIconBitmap?.let { Icon.createWithBitmap(scaleBitmap(it, 96)) }
        lastAppHash = appIconHash
    }

    private fun updateActionIcons(
        isPlaying: Boolean,
        actionStyle: String,
        mediaButtonLayout: String,
        notificationStyle: String
    ) {
        val effectiveButtonLayout = if (notificationStyle == "advanced_beta") "three_button" else mediaButtonLayout
        val actionsHash = if (actionStyle == "media_controls") {
            "$isPlaying|$effectiveButtonLayout".hashCode()
        } else {
            -1
        }
        if (actionsHash == lastActionsHash) return

        if (actionStyle == "media_controls") {
            val showPrevButton = effectiveButtonLayout == "three_button"
            val actionIcons = actionIconRenderer.render(isPlaying, showPrevButton)

            prevIcon = actionIcons.prev?.let { Icon.createWithBitmap(it) }
            prevIconDark = actionIcons.prevDark?.let { Icon.createWithBitmap(it) }
            playPauseIcon = Icon.createWithBitmap(actionIcons.playPause)
            playPauseIconDark = Icon.createWithBitmap(actionIcons.playPauseDark)
            nextIcon = Icon.createWithBitmap(actionIcons.next)
            nextIconDark = Icon.createWithBitmap(actionIcons.nextDark)
        } else {
            prevIcon = null
            prevIconDark = null
            playPauseIcon = null
            playPauseIconDark = null
            nextIcon = null
            nextIconDark = null
        }
        lastActionsHash = actionsHash
    }

}
