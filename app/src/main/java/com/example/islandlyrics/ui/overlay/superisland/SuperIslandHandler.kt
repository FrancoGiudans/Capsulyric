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

package com.example.islandlyrics.ui.overlay.superisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.runtime.foreground.LockScreenNotificationPolicy
import com.example.islandlyrics.runtime.service.LyricService
import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.superisland.cache.SuperIslandIconCache
import com.example.islandlyrics.ui.overlay.superisland.cache.SuperIslandProgressBitmapCache
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandColorSource
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandPreferencesCache
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandSecondaryTextMode
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandTemplate2PicSource
import com.example.islandlyrics.ui.overlay.superisland.intent.SuperIslandIntentFactory
import com.example.islandlyrics.ui.overlay.superisland.render.SuperIslandCustomFocusBuilder
import com.example.islandlyrics.ui.overlay.superisland.render.SuperIslandDualLineTextResolver
import com.example.islandlyrics.ui.overlay.superisland.render.SuperIslandNotificationBuilder
import com.example.islandlyrics.ui.overlay.superisland.render.SuperIslandRemoteViewsFactory
import com.example.islandlyrics.ui.overlay.superisland.render.SuperIslandStandardFocusBuilder
import com.example.islandlyrics.ui.overlay.superisland.render.SuperIslandTextResolver
import com.example.islandlyrics.ui.overlay.superisland.state.SuperIslandRenderStateTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

/**
 * SuperIslandHandler
 * Pure DSL Renderer for Xiaomi Super Island notifications using FocusNotification API.
 */
class SuperIslandHandler(
    private val context: Context,
    private val service: LyricService
) {

    private val manager: NotificationManager? = context.getSystemService(NotificationManager::class.java)

    var isRunning = false
        private set

    private var lastState: UIState? = null
    private var lastNotifiedVisibility = Notification.VISIBILITY_PUBLIC

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val intentFactory = SuperIslandIntentFactory(context)
    private val notificationBuilder = SuperIslandNotificationBuilder(context, CHANNEL_ID)

    private var cachedContentIntent: PendingIntent? = null
    private var cachedMiPlayIntent: PendingIntent? = null
    private var customTemplate2IconKey: String? = null
    private var customTemplate2Icon: Icon? = null

    private val preferences = SuperIslandPreferencesCache(
        context = context,
        onClickStyleChanged = {
            rebuildCachedIntents()
        },
        onXmsfModeChanged = { mode ->
            notificationDispatcher.onModeChanged(mode)
        }
    )

    private fun startPreferences() {
        preferences.start()
        rebuildCachedIntents()
    }

    private fun rebuildCachedIntents() {
        cachedContentIntent = intentFactory.createContentIntent(preferences.clickStyle)
        cachedMiPlayIntent = intentFactory.createMiPlayIntent()
    }

    private val renderStateTracker = SuperIslandRenderStateTracker()

    private val iconCache = SuperIslandIconCache(context)
    private val progressBitmapCache = SuperIslandProgressBitmapCache(context)
    private val remoteViewsFactory = SuperIslandRemoteViewsFactory(context, iconCache, progressBitmapCache)
    private val notificationDispatcher = SuperIslandNotificationDispatcher(context, service, manager, scope)
    private val standardFocusBuilder = SuperIslandStandardFocusBuilder(context, iconCache, preferences)
    private val customFocusBuilder = SuperIslandCustomFocusBuilder(iconCache, preferences, remoteViewsFactory)

    init {
        createChannel()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        startPreferences()

        renderStateTracker.resetForStart()

        iconCache.reset()
        progressBitmapCache.clear()
        notificationDispatcher.resetState()
    }

    fun refreshVisibility() {
        if (!isRunning) return
        val state = lastState ?: return
        render(state)
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        preferences.stop()
        manager?.cancel(NOTIFICATION_ID)
        renderStateTracker.clearFocusSignature()

        iconCache.reset()
        progressBitmapCache.clear()
        
        // Ensure network is restored if we were blocking it
        notificationDispatcher.stop(preferences.xmsfBypassMode)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_live_lyrics),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_live_lyrics_desc)
            setSound(null, null)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager?.createNotificationChannel(channel)
    }

    fun render(state: UIState) {
        if (!isRunning) return

        lastState = state
        val displayLyric = state.displayLyric
        val subText = if (state.artist.isNotBlank()) "${state.title} - ${state.artist}" else state.title
        val progressPercent = state.progressCurrent
        val albumColor = state.albumColor
        val notificationVisibility = LockScreenNotificationPolicy.visibility(context)
        val visibilityChanged = notificationVisibility != lastNotifiedVisibility
        // 显示模式的次要文本仅在模板2（标准样式 + 无按键 + 不显示进度条）时使用
        val secondaryText = if (effectiveActionStyle == ACTION_STYLE_TEMPLATE2) {
            SuperIslandDualLineTextResolver.resolveSecondary(
                state = state,
                modes = preferences.secondaryTextModes
            )
        } else {
            null
        }
        val accentColor = SuperIslandColorSource.resolveColor(
            source = preferences.colorSource,
            albumColor = albumColor,
            customColor = preferences.customColor
        )

        val dualLineText = if (preferences.notificationStyle == "advanced_lyrics_dual") {
            SuperIslandDualLineTextResolver.resolve(
                state,
                listOf(
                    SuperIslandSecondaryTextMode.from(preferences.dualLineMode)
                        ?: SuperIslandSecondaryTextMode.TRANSLATION
                )
            )
        } else {
            null
        }

        if (visibilityChanged) {
            renderStateTracker.forceNotify("lockScreenVisibility")
        }

        val renderDecision = renderStateTracker.prepare(
            state = state,
            displayLyric = displayLyric,
            progressPercent = progressPercent,
            accentColor = accentColor,
            colorMode = preferences.colorSource,
            extraContentSignature = dualLineText?.signature.orEmpty()
        ) ?: return

        val metadata = LyricRepository.getInstance().liveMetadata.value
        val albumArt = LyricRepository.getInstance().liveAlbumArt.value
        val currentTrackKey = buildTrackKey(state)

        notificationDispatcher.prepareForRender(
            currentTrackKey = currentTrackKey,
            isPlaying = state.isPlaying,
            mode = preferences.xmsfBypassMode
        )

        iconCache.update(
            metadata = metadata,
            albumArt = albumArt,
            isPlaying = state.isPlaying,
            actionStyle = effectiveActionStyle,
            mediaButtonLayout = preferences.mediaButtonLayout,
            notificationStyle = preferences.notificationStyle
        )

        val hexColor = String.format("#FF%06X", 0xFFFFFF and accentColor)
        val showHighlightColor = SuperIslandColorSource.isColorized(preferences.colorSource)
        val ringColor = if (showHighlightColor) hexColor else "#757575"
        val progressBarColor = if (showHighlightColor) hexColor else "#757575"
        val packageName = state.mediaPackage.ifEmpty { context.packageName }
        val titleWithArtist = if (state.artist.isNotBlank()) "${state.title} - ${state.artist}" else state.title
        val template2Icon = resolveTemplate2Icon()

        val customExpandEnabled = (preferences.notificationStyle == "advanced_beta" ||
            preferences.notificationStyle == "advanced_lyrics_dual") &&
            effectiveActionStyle == "media_controls"

        val standardExtras = standardFocusBuilder.build(
            state = state,
            displayLyric = displayLyric,
            subText = subText,
            progressPercent = progressPercent,
            hexColor = hexColor,
            showHighlightColor = showHighlightColor,
            ringColor = ringColor,
            progressBarColor = progressBarColor,
            packageName = packageName,
            titleWithArtist = titleWithArtist,
            effectiveActionStyle = effectiveActionStyle,
            template2Icon = template2Icon,
            secondaryText = secondaryText
        )

        val extras = if (customExpandEnabled) {
            customFocusBuilder.build(
                state = state,
                displayLyric = displayLyric,
                subText = subText,
                progressPercent = progressPercent,
                hexColor = hexColor,
                showHighlightColor = showHighlightColor,
            progressBarColor = progressBarColor,
            titleWithArtist = titleWithArtist,
            albumArt = albumArt,
            miPlayIntent = cachedMiPlayIntent,
            standardExtras = standardExtras
        )
        } else {
            standardExtras
        }

        val newParams = extras.getString("miui.focus.param")
        val newCustomParams = extras.getString("miui.focus.param.custom")

        val focusSignature = if (customExpandEnabled) newCustomParams.orEmpty() else newParams.orEmpty()
        if (renderStateTracker.isDuplicateFocusSignature(focusSignature, renderDecision.colorChanged)) {
            return
        }

        val notificationTitle = sequenceOf(displayLyric, state.fullLyric, state.title)
            .firstOrNull { !SuperIslandTextResolver.isPlaceholder(it) }
            ?: "Capsulyric"
        val notificationText = subText.ifEmpty { context.getString(R.string.channel_live_lyrics) }
        val notificationBuilder = notificationBuilder.createBase(cachedContentIntent)
            .setVisibility(notificationVisibility)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSubText(if (state.mediaPackage.isNotBlank()) state.mediaPackage else null)
            .setColor(if (effectiveActionStyle == "media_controls") 0xFF757575.toInt() else accentColor)
            .addExtras(extras)
        if (preferences.clickStyle == "open_playing_app") {
            notificationBuilder.setContentIntent(
                intentFactory.resolveContentIntent(
                    clickStyle = preferences.clickStyle,
                    mediaPackage = state.mediaPackage,
                    cachedContentIntent = cachedContentIntent
                )
            )
        }
        if (customExpandEnabled) {
            notificationBuilder.setStyle(Notification.DecoratedCustomViewStyle())
        }
        val notification = notificationBuilder.build()

        renderStateTracker.markRendered(
            state = state,
            displayLyric = displayLyric,
            progressPercent = progressPercent,
            subText = subText,
            accentColor = accentColor,
            colorMode = preferences.colorSource,
            extraContentSignature = dualLineText?.signature.orEmpty(),
            focusSignature = focusSignature
        )

        if (BuildConfig.DEBUG) {
            AppLogger.getInstance().log(
                "SuperIsland",
                "[NotifyTrace] send first=${renderStateTracker.isFirstNotification} reason=${renderStateTracker.firstNotificationReason} title=$notificationTitle focusEmpty=${focusSignature.isEmpty()} running=$isRunning isPlaying=${state.isPlaying} track=${state.title} - ${state.artist}"
            )
        }

        notificationDispatcher.notify(
            notification = notification,
            isFirst = renderStateTracker.isFirstNotification,
            firstReason = renderStateTracker.firstNotificationReason,
            currentTrackKey = currentTrackKey,
            isPlaying = state.isPlaying,
            mode = preferences.xmsfBypassMode,
            customDurationMs = preferences.xmsfCustomDurationMs
        )
        renderStateTracker.markFirstNotificationSent()
        lastNotifiedVisibility = notificationVisibility
    }

    private fun buildTrackKey(state: UIState): String {
        return listOf(state.mediaPackage, state.title, state.artist).joinToString("|")
    }

    /**
     * 新通知逻辑：播放按键布局 + 显示进度条 共同决定通知按键样式。
     * - 无按键 + 显示进度条：等价于旧「通知按键 = 关闭」（显示进度条，无媒体按钮）
     * - 标准样式 + 无按键 + 隐藏进度条：发送模板2通知（文本组件2 + 识别图形组件1）
     * - 有按键：显示媒体按钮
     */
    private val effectiveActionStyle: String
        get() = when {
            preferences.mediaButtonLayout == "no_button" &&
                !preferences.showProgressBar &&
                preferences.notificationStyle == LabFeatureManager.SUPER_ISLAND_STYLE_STANDARD ->
                ACTION_STYLE_TEMPLATE2
            preferences.mediaButtonLayout == "no_button" -> "disabled"
            else -> "media_controls"
        }

    /**
     * 模板2识别图形组件的图片来源：专辑图 / 正在播放App图标 / 应用图标 / 用户自定义图片。
     */
    private fun resolveTemplate2Icon(): Icon? = when (preferences.template2PicSource) {
        SuperIslandTemplate2PicSource.PLAYING_APP -> iconCache.appIcon
        SuperIslandTemplate2PicSource.APP_ICON ->
            Icon.createWithResource(context, R.mipmap.ic_launcher)
        SuperIslandTemplate2PicSource.CUSTOM -> resolveCustomTemplate2Icon()
        else -> iconCache.avatarIcon ?: iconCache.appIcon ?: Icon.createWithResource(context, R.mipmap.ic_launcher)
    }

    private fun resolveCustomTemplate2Icon(): Icon? {
        val uri = preferences.template2CustomPicUri
        if (uri.isNullOrBlank()) return null
        if (uri != customTemplate2IconKey) {
            customTemplate2IconKey = uri
            customTemplate2Icon = try {
                val bitmap = context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
                bitmap?.let { Icon.createWithBitmap(iconCache.scaleBitmap(it, 224)) }
            } catch (e: Exception) {
                null
            }
        }
        return customTemplate2Icon
    }

    companion object {
        private const val CHANNEL_ID = "lyric_capsule_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STYLE_TEMPLATE2 = "template2"
    }
}
