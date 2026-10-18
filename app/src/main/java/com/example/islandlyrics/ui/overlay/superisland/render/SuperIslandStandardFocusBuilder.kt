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

package com.example.islandlyrics.ui.overlay.superisland.render

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import com.example.islandlyrics.feature.mediacontrol.MediaControlActivity
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.superisland.cache.SuperIslandIconCache
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandTemplate18ButtonAction
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandPreferencesCache
import com.example.islandlyrics.ui.overlay.superisland.config.toLyricRenderConfig
import com.example.islandlyrics.ui.overlay.superisland.render.SuperIslandBigAreaRenderer.applyLyrics
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.focus.model.ActionInfo
import com.xzakota.hyper.notification.focus.model.CoverInfo
import com.xzakota.hyper.notification.focus.model.HighlightInfoV3

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
        titleWithArtist: String,
        effectiveActionStyle: String,
        darkMode: Boolean,
        template2Icon: Icon? = null,
        secondaryText: String? = null,
        template18Content: SuperIslandTemplate18Content? = null
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

            if (effectiveActionStyle != ACTION_STYLE_TEMPLATE2 && effectiveActionStyle != ACTION_STYLE_TEMPLATE18) {
                chatInfo {
                    picProfile = avatarKey
                    title = SuperIslandTextResolver.primaryText(state)
                    content = secondaryText ?: subText
                    appIconPkg = packageName
                }
            }

            if (effectiveActionStyle == ACTION_STYLE_TEMPLATE18 && template18Content != null) {
                val coverKey = (iconCache.coverInfoIcon ?: iconCache.avatarIcon)
                    ?.let { createPicture("miui.focus.pic_template18_cover", it) }
                val appKey = iconCache.appIcon?.let { createPicture("miui.focus.pic_template18_app", it) }
                val cover = coverKey ?: appKey ?: islandSmallKey ?: avatarKey
                val selectedAction = preferences.template18ButtonAction
                val actionTitle = context.getString(selectedAction.titleRes)
                val actionKey = registerTemplate18Action(selectedAction, state, actionTitle, darkMode)
                val actionIconKey = createPicture(
                    "miui.focus.pic_template18_action",
                    resolveTemplate18ActionIcon(selectedAction, state, dark = false)
                )
                val actionIconDarkKey = createPicture(
                    "miui.focus.pic_template18_action_dark",
                    resolveTemplate18ActionIcon(selectedAction, state, dark = true)
                )

                coverInfo = CoverInfo().apply {
                    picCover = cover
                    this.title = template18Content.currentLyric
                    this.content = template18Content.secondaryTexts[0]
                    subContent = template18Content.secondaryTexts[1]
                    // Leave default text colors to the native template, as with chatInfo/baseInfo.
                    if (showHighlightColor) {
                        colorTitle = hexColor
                        colorTitleDark = hexColor
                    }
                }
                picInfo {
                    type = 1
                    pic = appKey ?: islandSmallKey ?: avatarKey
                }
                highlightInfoV3 = HighlightInfoV3().apply {
                    primaryText = SuperIslandTemplate18PlaybackInfo.elapsed(state.playbackPositionMs, state.playbackDurationMs)
                    this.secondaryText = SuperIslandTemplate18PlaybackInfo.total(state.playbackDurationMs)
                    if (showHighlightColor) {
                        primaryColor = hexColor
                        primaryColorDark = hexColor
                    }
                    actionInfo = ActionInfo().apply {
                        type = 0
                        action = actionKey
                        // Button component 5 reads these JSON fields even when an Action is supplied.
                        this.actionTitle = actionTitle
                        actionIcon = actionIconKey
                        actionIconDark = actionIconDarkKey
                        actionTitleColor = "#FF111111"
                        actionTitleColorDark = "#FFFFFFFF"
                        actionBgColor = "#14000000"
                        actionBgColorDark = "#26FFFFFF"
                        clickWithCollapse = false
                    }
                }
            } else if (effectiveActionStyle == "media_controls") {
                actions {
                    val effectiveButtonLayout = if (preferences.notificationStyle == "advanced_beta" || preferences.notificationStyle == "advanced_lyrics_dual") "three_button" else preferences.mediaButtonLayout
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
            } else if (effectiveActionStyle == ACTION_STYLE_TEMPLATE2) {
                // 模板2：文本组件2 + 识别图形组件1（无进度条、无按钮）
                baseInfo {
                    type = 2
                    title = SuperIslandTextResolver.primaryText(state)
                    content = secondaryText ?: subText
                }
                if (template2Icon != null) {
                    val template2PicKey = createPicture("miui.focus.pic_template2", template2Icon)
                    picInfo {
                        type = 1
                        pic = template2PicKey
                    }
                } else {
                    picInfo {
                        type = 1
                        pic = appKey ?: islandSmallKey ?: avatarKey
                    }
                }
            } else if (preferences.showProgressBar) {
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
                        artist = state.artist,
                        fullLyricUsesDedicatedRightColumn =
                            preferences.notificationStyle == LabFeatureManager.SUPER_ISLAND_STYLE_STANDARD,
                        rightProgressRingEnabled = preferences.rightIslandProgressActive,
                        progressPercent = progressPercent,
                        progressReachColor = ringColor
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
                        if (preferences.smallIslandShowProgressBar) {
                            progressInfo {
                                progress = progressPercent
                                colorReach = ringColor
                                colorUnReach = "#333333"
                            }
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

    companion object {
        private const val ACTION_STYLE_TEMPLATE2 = "template2"
        private const val ACTION_STYLE_TEMPLATE18 = "template18"
    }

    private fun resolveTemplate18ActionIcon(
        action: SuperIslandTemplate18ButtonAction,
        state: UIState,
        dark: Boolean = false
    ): Icon {
        val cachedIcon = when (action) {
            SuperIslandTemplate18ButtonAction.PLAY_PAUSE -> if (dark) iconCache.playPauseIconDark else iconCache.playPauseIcon
            SuperIslandTemplate18ButtonAction.PREVIOUS -> if (dark) iconCache.prevIconDark else iconCache.prevIcon
            SuperIslandTemplate18ButtonAction.NEXT -> if (dark) iconCache.nextIconDark else iconCache.nextIcon
            SuperIslandTemplate18ButtonAction.CONTROLS -> null
        }
        if (cachedIcon != null) return cachedIcon
        val resourceId = when (action) {
            SuperIslandTemplate18ButtonAction.PLAY_PAUSE -> if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
            SuperIslandTemplate18ButtonAction.PREVIOUS -> R.drawable.ic_skip_previous
            SuperIslandTemplate18ButtonAction.NEXT -> R.drawable.ic_skip_next
            SuperIslandTemplate18ButtonAction.CONTROLS -> R.drawable.ic_miplay
        }
        return Icon.createWithResource(context, resourceId)
            .setTint(if (dark) Color.WHITE else 0xFF111111.toInt())
    }

    private fun registerTemplate18Action(
        action: SuperIslandTemplate18ButtonAction,
        state: UIState,
        title: String,
        darkMode: Boolean
    ): String {
        val key = "miui.focus.action_template18_${action.preferenceValue}"
        val requestCode = 3110 + action.ordinal
        val pendingIntent = if (action == SuperIslandTemplate18ButtonAction.CONTROLS) {
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, MediaControlActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            val broadcastAction = when (action) {
                SuperIslandTemplate18ButtonAction.PLAY_PAUSE -> "com.example.islandlyrics.ACTION_MEDIA_PLAY_PAUSE"
                SuperIslandTemplate18ButtonAction.PREVIOUS -> "com.example.islandlyrics.ACTION_MEDIA_PREV"
                SuperIslandTemplate18ButtonAction.NEXT -> "com.example.islandlyrics.ACTION_MEDIA_NEXT"
                SuperIslandTemplate18ButtonAction.CONTROLS -> error("Handled above")
            }
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(broadcastAction)
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val notificationAction = Notification.Action.Builder(
            resolveTemplate18ActionIcon(action, state, dark = darkMode),
            title,
            pendingIntent
        ).build()
        actionBundle.putParcelable(key, notificationAction)
        return key
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
