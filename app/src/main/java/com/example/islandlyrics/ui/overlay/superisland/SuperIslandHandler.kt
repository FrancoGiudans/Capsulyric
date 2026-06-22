package com.example.islandlyrics.ui.overlay.superisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.runtime.service.LyricService
import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.superisland.cache.SuperIslandIconCache
import com.example.islandlyrics.ui.overlay.superisland.cache.SuperIslandProgressBitmapCache
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandColorSource
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandPreferencesCache
import com.example.islandlyrics.ui.overlay.superisland.intent.SuperIslandIntentFactory
import com.example.islandlyrics.ui.overlay.superisland.render.SuperIslandCustomFocusBuilder
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

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val intentFactory = SuperIslandIntentFactory(context)
    private val notificationBuilder = SuperIslandNotificationBuilder(context, CHANNEL_ID)

    private var cachedContentIntent: PendingIntent? = null
    private var cachedMiPlayIntent: PendingIntent? = null

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

        val displayLyric = state.displayLyric
        val subText = if (state.artist.isNotBlank()) "${state.title} - ${state.artist}" else state.title
        val progressPercent = state.progressCurrent
        val albumColor = state.albumColor
        val accentColor = SuperIslandColorSource.resolveColor(
            source = preferences.colorSource,
            albumColor = albumColor,
            customColor = preferences.customColor
        )

        val renderDecision = renderStateTracker.prepare(
            state = state,
            displayLyric = displayLyric,
            progressPercent = progressPercent,
            accentColor = accentColor
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
            actionStyle = preferences.actionStyle,
            mediaButtonLayout = preferences.mediaButtonLayout,
            notificationStyle = preferences.notificationStyle
        )

        val hexColor = String.format("#FF%06X", 0xFFFFFF and accentColor)
        val showHighlightColor = preferences.textColorEnabled 
        val ringColor = if (showHighlightColor) hexColor else "#757575"
        val progressBarColor = if (preferences.progressBarColorEnabled) hexColor else "#757575"
        val packageName = state.mediaPackage.ifEmpty { context.packageName }
        val titleWithArtist = if (state.artist.isNotBlank()) "${state.title} - ${state.artist}" else state.title

        val customExpandEnabled = preferences.notificationStyle == "advanced_beta" &&
            preferences.actionStyle == "media_controls"

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
            titleWithArtist = titleWithArtist
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
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSubText(if (state.mediaPackage.isNotBlank()) state.mediaPackage else null)
            .setColor(if (preferences.actionStyle == "media_controls") 0xFF757575.toInt() else accentColor)
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
    }

    private fun buildTrackKey(state: UIState): String {
        return listOf(state.mediaPackage, state.title, state.artist).joinToString("|")
    }

    companion object {
        private const val CHANNEL_ID = "lyric_capsule_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
