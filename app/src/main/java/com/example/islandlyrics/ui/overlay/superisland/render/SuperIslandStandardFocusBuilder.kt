package com.example.islandlyrics.ui.overlay.superisland.render

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.example.islandlyrics.R
import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.superisland.cache.SuperIslandIconCache
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandPreferencesCache
import com.example.islandlyrics.ui.overlay.superisland.config.toLyricRenderConfig
import com.example.islandlyrics.ui.overlay.superisland.render.SuperIslandBigAreaRenderer.applyLyrics
import com.xzakota.hyper.notification.focus.FocusNotification

internal class SuperIslandStandardFocusBuilder(
    private val context: Context,
    private val iconCache: SuperIslandIconCache,
    private val preferences: SuperIslandPreferencesCache
) {
    private val actionBundle = android.os.Bundle()

    fun build(
        state: UIState,
        displayLyric: String,
        subText: String,
        progressPercent: Int,
        hexColor: String,
        showHighlightColor: Boolean,
        ringColor: String,
        progressBarColor: String,
        packageName: String,
        titleWithArtist: String
    ): android.os.Bundle {
        actionBundle.clear()
        val bundle = FocusNotification.buildV3 {
            business = "lyric_display"
            isShowNotification = true
            enableFloat = false
            updatable = true
            islandFirstFloat = false
            aodTitle = displayLyric.take(20).ifEmpty { "♪" }
            val avatarKey = iconCache.avatarIcon?.let { createPicture("miui.focus.pic_avatar", it) }
            val appKey = iconCache.appIcon?.let { createPicture("miui.focus.pic_app", it) }
            val islandKey = iconCache.islandIcon?.let { createPicture("miui.focus.pic_island", it) }
            val islandSmallKey = iconCache.islandSmallIcon?.let { createPicture("miui.land.pic_island", it) }
            val shareKey = iconCache.shareIcon?.let { createPicture("miui.focus.pic_share", it) }

            ticker = displayLyric.ifEmpty { subText.ifEmpty { state.title.ifEmpty { "♪" } } }
            tickerPic = appKey ?: islandSmallKey ?: avatarKey

            chatInfo {
                picProfile = avatarKey
                title = SuperIslandTextResolver.primaryText(state)
                content = subText
                appIconPkg = packageName
            }

            if (preferences.actionStyle == "media_controls") {
                actions {
                    val effectiveButtonLayout = if (preferences.notificationStyle == "advanced_beta") "three_button" else preferences.mediaButtonLayout
                    val showPrevButton = effectiveButtonLayout == "three_button"
                    val prevActionKey = if (showPrevButton) {
                        createFocusBroadcastAction(
                            key = "miui.focus.action_prev",
                            requestCode = 3100,
                            action = "com.example.islandlyrics.ACTION_MEDIA_PREV",
                            iconResId = R.drawable.ic_skip_previous,
                            title = "Previous"
                        )
                    } else {
                        null
                    }
                    val playPauseActionKey = createFocusBroadcastAction(
                        key = "miui.focus.action_play_pause",
                        requestCode = 3101,
                        action = "com.example.islandlyrics.ACTION_MEDIA_PLAY_PAUSE",
                        iconResId = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                        title = if (state.isPlaying) "Pause" else "Play"
                    )
                    val nextActionKey = createFocusBroadcastAction(
                        key = "miui.focus.action_next",
                        requestCode = 3102,
                        action = "com.example.islandlyrics.ACTION_MEDIA_NEXT",
                        iconResId = R.drawable.ic_skip_next,
                        title = "Next"
                    )

                    if (showPrevButton) {
                        addActionInfo {
                            type = 0
                            action = prevActionKey
                            actionIcon = iconCache.prevIcon?.let { createPicture("miui.focus.pic_btn_prev", it) }
                            actionIconDark = iconCache.prevIconDark?.let { createPicture("miui.focus.pic_btn_prev_dark", it) }
                            clickWithCollapse = false
                        }
                    }
                    addActionInfo {
                        type = 0
                        action = playPauseActionKey
                        actionIcon = iconCache.playPauseIcon?.let { createPicture("miui.focus.pic_btn_play_pause", it) }
                        actionIconDark = iconCache.playPauseIconDark?.let { createPicture("miui.focus.pic_btn_play_pause_dark", it) }
                        clickWithCollapse = false
                    }
                    addActionInfo {
                        type = 0
                        action = nextActionKey
                        actionIcon = iconCache.nextIcon?.let { createPicture("miui.focus.pic_btn_next", it) }
                        actionIconDark = iconCache.nextIconDark?.let { createPicture("miui.focus.pic_btn_next_dark", it) }
                        clickWithCollapse = false
                    }
                }
            } else {
                progressInfo {
                    progress = progressPercent
                    colorProgress = progressBarColor
                    colorProgressEnd = progressBarColor
                }
            }

            island {
                islandProperty = 1
                if (showHighlightColor) {
                    this.highlightColor = hexColor
                }

                bigIslandArea {
                    applyLyrics(
                        config = preferences.toLyricRenderConfig(),
                        preferMetadataLayout = state.preferMetadataLayout,
                        isTimingGapPlaceholder = state.isTimingGapPlaceholder,
                        fullLyric = state.fullLyric,
                        displayLyric = displayLyric,
                        titleWithArtist = titleWithArtist,
                        islandKey = islandKey,
                        showHighlightColor = showHighlightColor,
                        title = state.title,
                        artist = state.artist
                    )
                }

                if (preferences.shareEnabled) {
                    shareData {
                        pic = shareKey
                        title = state.title.ifEmpty { "♪" }
                        content = SuperIslandTextResolver.primaryText(state)
                        this.shareContent = SuperIslandTextResolver.shareContent(state, preferences.shareFormat)
                    }
                }

                smallIslandArea {
                    combinePicInfo {
                        if (islandSmallKey != null) {
                            picInfo {
                                type = 1
                                pic = islandSmallKey
                            }
                        }
                        progressInfo {
                            progress = progressPercent
                            colorReach = ringColor
                            colorUnReach = "#333333"
                        }
                    }
                }
            }
        }
        if (actionBundle.keySet().isNotEmpty()) {
            bundle.putBundle("miui.focus.actions", android.os.Bundle(actionBundle))
        }
        return bundle
    }

    private fun createFocusBroadcastAction(
        key: String,
        requestCode: Int,
        action: String,
        iconResId: Int,
        title: String
    ): String {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(action)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notificationAction = Notification.Action.Builder(
            Icon.createWithResource(context, iconResId),
            title,
            pendingIntent
        ).build()
        return key.also {
            actionBundle.putParcelable(it, notificationAction)
        }
    }
}
