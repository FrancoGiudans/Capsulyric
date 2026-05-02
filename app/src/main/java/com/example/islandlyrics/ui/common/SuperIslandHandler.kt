package com.example.islandlyrics.ui.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.widget.RemoteViews
import com.example.islandlyrics.R
import com.example.islandlyrics.core.platform.XmsfBypassMode
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.service.LyricService
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.feature.mediacontrol.MediaControlActivity
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.focus.template.CustomFocusTemplate
import com.xzakota.hyper.notification.focus.template.CustomFocusTemplateV3
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    
    // Preferences
    private var cachedClickStyle = "default"
    private var cachedSuperIslandTextColorEnabled = false
    private var cachedSuperIslandShareEnabled = true
    private var cachedSuperIslandShareFormat = "format_1"
    private var cachedProgressBarColorEnabled = false
    private var cachedActionStyle = "disabled"
    private var cachedMediaButtonLayout = "two_button"
    private var cachedSuperIslandNotificationStyle = "standard"
    private var cachedSuperIslandLyricMode = "standard"
    private var cachedSuperIslandFullLyricShowLeftCover = true
    private var cachedXmsfBypassMode = XmsfBypassMode.DISABLED

    private var cachedContentIntent: PendingIntent? = null
    private var cachedMiPlayIntent: PendingIntent? = null
    private var networkCutJob: kotlinx.coroutines.Job? = null
    // Keep the blind window aligned with the working InstallerX implementation.
    // 50ms is too tight on HyperOS 3.0 / Android 16 and often expires before
    // SystemUI finishes scanning the posted notification.
    private val networkCutDurationMs = 100L
    private var networkCutSeq = 0L
    private val networkCutMutex = Mutex()
    private var aggressiveNetworkCutActive = false
    private var aggressiveTrackKey: String? = null

    private val prefs by lazy { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            "notification_click_style" -> {
                cachedClickStyle = p.getString(key, "default") ?: "default"
                cachedContentIntent = createContentIntent()
                cachedMiPlayIntent = createMiPlayIntent()
            }
            "super_island_text_color_enabled" -> cachedSuperIslandTextColorEnabled = p.getBoolean(key, false)
            "super_island_share_enabled" -> cachedSuperIslandShareEnabled = p.getBoolean(key, true)
            "super_island_share_format" -> cachedSuperIslandShareFormat = p.getString(key, "format_1") ?: "format_1"
            "progress_bar_color_enabled" -> cachedProgressBarColorEnabled = p.getBoolean(key, false)
            "notification_actions_style" -> cachedActionStyle = p.getString(key, "disabled") ?: "disabled"
            "super_island_media_button_layout" -> cachedMediaButtonLayout = p.getString(key, "two_button") ?: "two_button"
            "super_island_notification_style" -> cachedSuperIslandNotificationStyle = p.getString(key, "standard") ?: "standard"
            "super_island_lyric_mode" -> cachedSuperIslandLyricMode = p.getString(key, "standard") ?: "standard"
            "super_island_full_lyric_show_left_cover" -> cachedSuperIslandFullLyricShowLeftCover = p.getBoolean(key, true)
            "block_xmsf_network_mode", "block_xmsf_network" -> {
                cachedXmsfBypassMode = XmsfBypassMode.read(p)
                if (cachedXmsfBypassMode != XmsfBypassMode.AGGRESSIVE && aggressiveNetworkCutActive) {
                    restoreXmsfNetworkingAsync()
                }
            }
        }
    }

    private fun loadPreferences() {
        cachedClickStyle = prefs.getString("notification_click_style", "default") ?: "default"
        cachedContentIntent = createContentIntent()
        cachedMiPlayIntent = createMiPlayIntent()
        cachedSuperIslandTextColorEnabled = prefs.getBoolean("super_island_text_color_enabled", false)
        cachedSuperIslandShareEnabled = prefs.getBoolean("super_island_share_enabled", true)
        cachedSuperIslandShareFormat = prefs.getString("super_island_share_format", "format_1") ?: "format_1"
        cachedProgressBarColorEnabled = prefs.getBoolean("progress_bar_color_enabled", false)
        cachedActionStyle = prefs.getString("notification_actions_style", "disabled") ?: "disabled"
        cachedMediaButtonLayout = prefs.getString("super_island_media_button_layout", "two_button") ?: "two_button"
        cachedSuperIslandNotificationStyle = prefs.getString("super_island_notification_style", "standard") ?: "standard"
        cachedSuperIslandLyricMode = prefs.getString("super_island_lyric_mode", "standard") ?: "standard"
        cachedSuperIslandFullLyricShowLeftCover = prefs.getBoolean("super_island_full_lyric_show_left_cover", true)
        cachedXmsfBypassMode = XmsfBypassMode.read(prefs)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    // Change detection tracking
    private var lastSentDisplayLyric = ""
    private var lastSentFullLyric = ""
    private var lastSentProgressPercent = -1
    private var lastSentSubText = ""
    private var lastSentIsPlaying = false
    private var lastSentTitle = ""
    private var lastSentArtist = ""
    private var isFirstNotification = true
    private var lastFocusParam = ""
    
    private var lastNotifyTime = 0L
    private val throttleIntervalMs = 1000L
    
    private var lastAlbumArtHash = 0
    private var lastPicAppHash = 0
    private var lastPicActionsHash = 0
    
    private var cachedAvatarIcon: Icon? = null
    private var cachedIslandIcon: Icon? = null
    private var cachedIslandSmallIcon: Icon? = null
    private var cachedShareIcon: Icon? = null
    private var cachedAppIcon: Icon? = null
    private var cachedPrevIcon: Icon? = null
    private var cachedPrevIconDark: Icon? = null
    private var cachedPlayPauseIcon: Icon? = null
    private var cachedPlayPauseIconDark: Icon? = null
    private var cachedNextIcon: Icon? = null
    private var cachedNextIconDark: Icon? = null
    private val scaledBitmapCache = LinkedHashMap<String, Bitmap>(8, 0.75f, true)
    private val progressBitmapCache = LinkedHashMap<String, Bitmap>(12, 0.75f, true)

    private var lastAppliedAlbumColor = 0

    init {
        createChannel()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        loadPreferences()

        lastSentDisplayLyric = ""
        lastSentFullLyric = ""
        lastSentProgressPercent = -1
        lastSentSubText = ""
        lastSentIsPlaying = false
        isFirstNotification = true
        
        lastAlbumArtHash = 0
        lastPicAppHash = 0
        lastPicActionsHash = 0
        lastAppliedAlbumColor = 0

        cachedAvatarIcon = null
        cachedIslandIcon = null
        cachedIslandSmallIcon = null
        cachedShareIcon = null
        cachedAppIcon = null
        cachedPrevIcon = null
        cachedPrevIconDark = null
        cachedPlayPauseIcon = null
        cachedPlayPauseIconDark = null
        cachedNextIcon = null
        cachedNextIconDark = null
        clearBitmapCaches()
        aggressiveNetworkCutActive = false
        aggressiveTrackKey = null

        lastFocusParam = ""
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        manager?.cancel(NOTIFICATION_ID)
        lastFocusParam = ""
        
        // Cancel all pending scope jobs
        // But wait, if we cancel the whole scope we might not be able to restart it?
        // Actually, for a service-bound handler, we should probably use a job that we cancel.
        
        cachedAvatarIcon = null
        cachedIslandIcon = null
        cachedIslandSmallIcon = null
        cachedShareIcon = null
        cachedAppIcon = null
        cachedPrevIcon = null
        cachedPrevIconDark = null
        cachedPlayPauseIcon = null
        cachedPlayPauseIconDark = null
        cachedNextIcon = null
        cachedNextIconDark = null
        clearBitmapCaches()
        aggressiveTrackKey = null
        aggressiveNetworkCutActive = false
        
        // Ensure network is restored if we were blocking it
        networkCutJob?.cancel()
        if (cachedXmsfBypassMode.isEnabled) {
            restoreXmsfNetworkingAsync()
        }
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

    private fun updateIcons(metadata: LyricRepository.MediaInfo?, albumArt: Bitmap?, isPlaying: Boolean) {
        val albumArtHash = albumArt?.hashCode() ?: 0
        if (albumArtHash != lastAlbumArtHash) {
            if (albumArt != null) {
                cachedAvatarIcon = Icon.createWithBitmap(scaleBitmap(albumArt, 480))
                cachedIslandIcon = Icon.createWithBitmap(scaleBitmap(albumArt, 120))
                cachedIslandSmallIcon = Icon.createWithBitmap(scaleBitmap(albumArt, 88))
                cachedShareIcon = Icon.createWithBitmap(scaleBitmap(albumArt, 224))
            } else {
                cachedAvatarIcon = null
                cachedIslandIcon = null
                cachedIslandSmallIcon = null
                cachedShareIcon = null
            }
            lastAlbumArtHash = albumArtHash
            
            // Also update app icon if metadata changed concurrently
            val appIconHash = metadata?.packageName?.hashCode() ?: 0
            if (appIconHash != lastPicAppHash || cachedAppIcon == null) {
                val appIconBmp = getAppIcon(metadata?.packageName)
                cachedAppIcon = if (appIconBmp != null) Icon.createWithBitmap(scaleBitmap(appIconBmp, 96)) else null
                lastPicAppHash = appIconHash
            }
        } else if (metadata?.packageName?.hashCode() != lastPicAppHash) {
            val appIconBmp = getAppIcon(metadata?.packageName)
            cachedAppIcon = if (appIconBmp != null) Icon.createWithBitmap(scaleBitmap(appIconBmp, 96)) else null
            lastPicAppHash = metadata?.packageName?.hashCode() ?: 0
        }

        val effectiveButtonLayout = if (cachedSuperIslandNotificationStyle == "advanced_beta") "three_button" else cachedMediaButtonLayout
        val actionsHash = if (cachedActionStyle == "media_controls") {
            "$isPlaying|$effectiveButtonLayout".hashCode()
        } else {
            -1
        }
        if (actionsHash != lastPicActionsHash) {
            if (cachedActionStyle == "media_controls") {
                val showPrevButton = effectiveButtonLayout == "three_button"
                val prevIconBitmap = if (showPrevButton) {
                    renderButtonIcon(R.drawable.ic_skip_previous, 96, 0.5f, null, android.graphics.Color.parseColor("#FF111111"))
                } else {
                    null
                }
                val prevIconBitmapDark = if (showPrevButton) {
                    renderButtonIcon(R.drawable.ic_skip_previous, 96, 0.5f, null, android.graphics.Color.WHITE)
                } else {
                    null
                }
                val playPauseResId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                val playPauseIconBitmap = renderButtonIcon(playPauseResId, 96, 0.42f, null, android.graphics.Color.parseColor("#FF111111"))
                val playPauseIconBitmapDark = renderButtonIcon(playPauseResId, 96, 0.42f, null, android.graphics.Color.WHITE)
                val nextIconBitmap = renderButtonIcon(R.drawable.ic_skip_next, 96, 0.5f, null, android.graphics.Color.parseColor("#FF111111"))
                val nextIconBitmapDark = renderButtonIcon(R.drawable.ic_skip_next, 96, 0.5f, null, android.graphics.Color.WHITE)

                cachedPrevIcon = prevIconBitmap?.let { Icon.createWithBitmap(it) }
                cachedPrevIconDark = prevIconBitmapDark?.let { Icon.createWithBitmap(it) }
                cachedPlayPauseIcon = Icon.createWithBitmap(playPauseIconBitmap)
                cachedPlayPauseIconDark = Icon.createWithBitmap(playPauseIconBitmapDark)
                cachedNextIcon = Icon.createWithBitmap(nextIconBitmap)
                cachedNextIconDark = Icon.createWithBitmap(nextIconBitmapDark)
            } else {
                cachedPrevIcon = null
                cachedPrevIconDark = null
                cachedPlayPauseIcon = null
                cachedPlayPauseIconDark = null
                cachedNextIcon = null
                cachedNextIconDark = null
            }
            lastPicActionsHash = actionsHash
        }
    }

    private fun createBaseBuilder(): Notification.Builder {
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(cachedContentIntent)
    }

    fun render(state: UIState) {
        if (!isRunning) return

        val displayLyric = state.displayLyric
        val subText = if (state.artist.isNotBlank()) "${state.title} - ${state.artist}" else state.title
        val progressPercent = state.progressCurrent
        val albumColor = state.albumColor

        // 1. TRACK SWITCH DETECTION: Clear builder cache to prevent "stuck" metadata on Android 15
        val trackChanged = state.title != lastSentTitle || state.artist != lastSentArtist
        if (trackChanged && !isFirstNotification) {
            com.example.islandlyrics.core.logging.AppLogger.getInstance().d("SuperIsland", "Track changed: ${state.title}. Resetting builder.")
            isFirstNotification = true
            lastFocusParam = ""
        }

        // Color changed?
        val colorChanged = albumColor != lastAppliedAlbumColor

        // 2. LYRIC LINE CHANGE: Force startForeground to clear MIUI rendering frame cache (Issue #22)
        // Compare fullLyric (not displayLyric) to detect actual line changes.
        // displayLyric changes on every syllable scroll tick within the same line,
        // which would cause excessive startForeground calls and system-level delays.
        val lyricLineChanged = !isFirstNotification && !trackChanged
                && state.fullLyric.isNotEmpty() && state.fullLyric != lastSentFullLyric
        if (lyricLineChanged) {
            isFirstNotification = true
        }

        // 3. CONTENT-AWARE THROTTLING
        val contentChanged = trackChanged || lyricLineChanged || displayLyric != lastSentDisplayLyric || state.isPlaying != lastSentIsPlaying
        val now = System.currentTimeMillis()
        
        if (!isFirstNotification && !colorChanged && !contentChanged) {
            // Only progress changed. Apply 1000ms throttle for Android 15 stability.
            val progressChangedEnough = kotlin.math.abs(progressPercent - lastSentProgressPercent) >= PROGRESS_NOTIFY_STEP_PERCENT
            if (!progressChangedEnough || now - lastNotifyTime < throttleIntervalMs) {
                return
            }
        }

        // Pre-JSON FAST PATH: skip expensive serialization if literally nothing changed (including exact progress)
        if (!isFirstNotification && !colorChanged && !contentChanged && progressPercent == lastSentProgressPercent) {
            return
        }

        val metadata = LyricRepository.getInstance().liveMetadata.value
        val albumArt = LyricRepository.getInstance().liveAlbumArt.value
        val currentTrackKey = buildTrackKey(state)

        if (aggressiveNetworkCutActive) {
            val songEnded = !state.isPlaying
            val songChanged = aggressiveTrackKey != null && aggressiveTrackKey != currentTrackKey
            if (songEnded || songChanged || cachedXmsfBypassMode != XmsfBypassMode.AGGRESSIVE) {
                restoreXmsfNetworkingAsync()
            }
        }

        updateIcons(metadata, albumArt, state.isPlaying)

        val hexColor = String.format("#FF%06X", 0xFFFFFF and albumColor)
        val showHighlightColor = cachedSuperIslandTextColorEnabled 
        val ringColor = if (showHighlightColor) hexColor else "#757575"
        val progressBarColor = if (cachedProgressBarColorEnabled) hexColor else "#757575"
        val packageName = state.mediaPackage.ifEmpty { context.packageName }
        val titleWithArtist = if (state.artist.isNotBlank()) "${state.title} - ${state.artist}" else state.title

        val customExpandEnabled = cachedSuperIslandNotificationStyle == "advanced_beta" &&
            cachedActionStyle == "media_controls"

        val standardExtras = buildStandardFocusBundle(
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
            val customExtras = FocusNotification.buildCustomV3 {
                business = "lyric_display"
                isShowNotification = true
                enableFloat = false
                updatable = true
                islandFirstFloat = false
                hideDeco = true
                aodTitle = displayLyric.take(20).ifEmpty { "♪" }
                val avatarKey = cachedAvatarIcon?.let { createPicture("miui.focus.pic_avatar", it) }
                val appKey = cachedAppIcon?.let { createPicture("miui.focus.pic_app", it) }
                val islandKey = cachedIslandIcon?.let { createPicture("miui.focus.pic_island", it) }
                val islandSmallKey = cachedIslandSmallIcon?.let { createPicture("miui.land.pic_island", it) }
                val shareKey = cachedShareIcon?.let { createPicture("miui.focus.pic_share", it) }

                ticker = displayLyric.ifEmpty { subText.ifEmpty { state.title.ifEmpty { "♪" } } }
                tickerPic = appKey ?: islandSmallKey ?: avatarKey

                val customLightViews = createCustomExpandRemoteViews(
                    state = state,
                    subText = subText,
                    progressPercent = progressPercent,
                    progressBarColor = progressBarColor,
                    darkMode = false
                )
                val customDarkViews = createCustomExpandRemoteViews(
                    state = state,
                    subText = subText,
                    progressPercent = progressPercent,
                    progressBarColor = progressBarColor,
                    darkMode = true
                )
                val tinyViews = createCustomTinyRemoteViews(
                    state = state,
                    subText = subText,
                    progressPercent = progressPercent,
                    progressBarColor = progressBarColor,
                    darkMode = true
                )
                createRemoteViews(CustomFocusTemplate.LAYOUT, customLightViews)
                createRemoteViews(CustomFocusTemplate.LAYOUT_NIGHT, customDarkViews)
                createRemoteViews(CustomFocusTemplate.LAYOUT_FLIP_TINY, tinyViews)
                createRemoteViews(CustomFocusTemplate.LAYOUT_FLIP_TINY_NIGHT, tinyViews)
                createRemoteViews(CustomFocusTemplateV3.LAYOUT_ISLAND_EXPAND, customDarkViews)

                island {
                    islandProperty = 1
                    if (showHighlightColor) {
                        highlightColor = hexColor
                    }

            bigIslandArea {
                applyBigIslandLyrics(
                    fullLyric = state.fullLyric,
                    displayLyric = displayLyric,
                    titleWithArtist = titleWithArtist,
                    islandKey = islandKey,
                    showHighlightColor = showHighlightColor
                )
            }

                    if (cachedSuperIslandShareEnabled) {
                        shareData {
                            pic = shareKey
                            title = state.title.ifEmpty { "♪" }
                            content = state.fullLyric.ifEmpty { "♪" }
                            val shareArtist = if (state.artist.isNotBlank()) state.artist else "未知歌手"
                            val shareSong = state.title.ifEmpty { "未知歌曲" }
                            this.shareContent = when (cachedSuperIslandShareFormat) {
                                "format_2" -> "${state.fullLyric} -$shareArtist\uff0c$shareSong"
                                "format_3" -> "${state.fullLyric}\n$shareArtist\uff0c$shareSong"
                                else -> "${state.fullLyric}\n$shareSong by $shareArtist"
                            }
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
                        }
                    }
                }
            }
            mergeCustomFocusWithStandardIsland(customExtras, standardExtras)
        } else {
            standardExtras
        }

        val newParams = extras.getString("miui.focus.param")
        val newCustomParams = extras.getString("miui.focus.param.custom")

        val focusSignature = if (customExpandEnabled) newCustomParams.orEmpty() else newParams.orEmpty()
        if (focusSignature == lastFocusParam && !colorChanged && !isFirstNotification) {
            return
        }

        val notificationTitle = displayLyric.ifEmpty { state.fullLyric.ifEmpty { state.title.ifEmpty { "Capsulyric" } } }
        val notificationText = subText.ifEmpty { context.getString(R.string.channel_live_lyrics) }
        val notificationBuilder = createBaseBuilder()
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSubText(if (state.mediaPackage.isNotBlank()) state.mediaPackage else null)
            .setColor(if (cachedActionStyle == "media_controls") 0xFF757575.toInt() else albumColor)
            .addExtras(extras)
        if (customExpandEnabled) {
            notificationBuilder.setStyle(Notification.DecoratedCustomViewStyle())
        }
        val notification = notificationBuilder.build()

        lastAppliedAlbumColor = albumColor
        lastFocusParam = focusSignature

        lastSentDisplayLyric = displayLyric
        lastSentFullLyric = state.fullLyric
        lastSentProgressPercent = progressPercent
        lastSentSubText = subText
        lastSentIsPlaying = state.isPlaying
        lastSentTitle = state.title
        lastSentArtist = state.artist
        lastNotifyTime = System.currentTimeMillis()

        notifyWithNetworkCut(notification, isFirstNotification, currentTrackKey)
        if (isFirstNotification) {
            isFirstNotification = false
        }
    }

    private fun notifyWithNetworkCut(notification: Notification, isFirst: Boolean, currentTrackKey: String) {
        when (cachedXmsfBypassMode) {
            XmsfBypassMode.AGGRESSIVE -> {
                networkCutJob?.cancel()
                scope.launch(Dispatchers.IO) {
                    networkCutMutex.withLock {
                        if (!aggressiveNetworkCutActive) {
                            withContext(NonCancellable) {
                                com.example.islandlyrics.integration.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, false)
                            }
                            aggressiveNetworkCutActive = true
                        }
                        aggressiveTrackKey = currentTrackKey
                        if (manager != null) {
                            manager.notify(NOTIFICATION_ID, notification)
                        } else if (isFirst) {
                            service.startForeground(NOTIFICATION_ID, notification)
                        }
                    }
                }
            }
            XmsfBypassMode.STANDARD -> {
            networkCutJob?.cancel()
            val seq = ++networkCutSeq
            networkCutJob = scope.launch(Dispatchers.IO) {
                networkCutMutex.withLock {
                    // Avoid cancelling Shizuku bind on slower devices (e.g., MTK) by running in NonCancellable.
                    withContext(NonCancellable) {
                        com.example.islandlyrics.integration.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, false)
                    }
                    // During the blind window we must go through NotificationManager directly.
                    // startForeground() adds extra service/AMS hops, which can let HyperOS finish
                    // the whitelist scan after the network has already been restored.
                    if (manager != null) {
                        manager.notify(NOTIFICATION_ID, notification)
                    } else if (isFirst) {
                        service.startForeground(NOTIFICATION_ID, notification)
                    }
                    // Keep offline for a brief moment; if a new send happens within this window,
                    // the previous restore will be cancelled.
                    try {
                        kotlinx.coroutines.delay(networkCutDurationMs)
                    } catch (_: CancellationException) {
                        // A newer job will handle restore.
                    }
                    if (seq == networkCutSeq) {
                        withContext(NonCancellable) {
                            com.example.islandlyrics.integration.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, true)
                        }
                    }
                }
            }
            }
            XmsfBypassMode.DISABLED -> {
                if (isFirst) {
                    service.startForeground(NOTIFICATION_ID, notification)
                } else {
                    manager?.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun restoreXmsfNetworkingAsync() {
        aggressiveTrackKey = null
        aggressiveNetworkCutActive = false
        scope.launch {
            withContext(Dispatchers.IO) {
                withContext(NonCancellable) {
                    com.example.islandlyrics.integration.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, true)
                }
            }
        }
    }

    private fun buildTrackKey(state: UIState): String {
        return listOf(state.mediaPackage, state.title, state.artist).joinToString("|")
    }

    private fun renderButtonIcon(
        resourceId: Int,
        size: Int,
        iconScale: Float,
        bgColorHex: String? = null,
        iconTint: Int = android.graphics.Color.WHITE
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        if (bgColorHex != null) {
            paint.color = android.graphics.Color.parseColor(bgColorHex)
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        }

        val drawable = context.getDrawable(resourceId) ?: return bitmap
        drawable.mutate().setTint(iconTint)
        val iconSize = (size * iconScale).toInt()
        val margin = (size - iconSize) / 2
        drawable.setBounds(margin, margin, margin + iconSize, margin + iconSize)
        drawable.draw(canvas)

        return bitmap
    }

    private fun scaleBitmap(src: Bitmap, targetSize: Int): Bitmap {
        if (src.isRecycled) {
            return Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        }
        val cacheKey = "${System.identityHashCode(src)}:${src.width}x${src.height}:$targetSize"
        scaledBitmapCache[cacheKey]?.let { return it }

        val needsScale = src.width != targetSize || src.height != targetSize
        val scaled = if (!needsScale) src 
                     else Bitmap.createScaledBitmap(src, targetSize, targetSize, true)
                     
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())
        
        val cornerRadius = targetSize * 0.2f
        
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        
        // Recycle the intermediate scaled bitmap to prevent Native Heap fragmentation
        if (needsScale) {
            scaled.recycle()
        }
        
        putBitmapCacheEntry(scaledBitmapCache, cacheKey, output, maxEntries = 8)
        return output
    }

    private fun getAppIcon(packageName: String?): Bitmap? {
        if (packageName == null) return null
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun createContentIntent(): PendingIntent {
        return if (cachedClickStyle == "media_controls") {
            val intent = Intent(context, MediaControlActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }
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
        return createAction(key, notificationAction)
    }

    private fun createMiPlayIntent(): PendingIntent? {
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

    private fun createCustomExpandRemoteViews(
        state: UIState,
        subText: String,
        progressPercent: Int,
        progressBarColor: String,
        darkMode: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.super_island_custom_expand)
        val primaryTextColor = android.graphics.Color.parseColor(if (darkMode) "#FFFFFFFF" else "#FF111111")
        val secondaryTextColor = android.graphics.Color.parseColor(if (darkMode) "#B3FFFFFF" else "#99000000")
        val iconTintColor = primaryTextColor
        views.setTextViewText(
            R.id.custom_expand_title,
            state.fullLyric.ifEmpty { state.displayLyric.ifEmpty { "♪" } }
        )
        views.setTextViewText(R.id.custom_expand_subtitle, subText.ifEmpty { context.getString(R.string.channel_live_lyrics) })
        views.setTextColor(R.id.custom_expand_title, primaryTextColor)
        views.setTextColor(R.id.custom_expand_subtitle, secondaryTextColor)
        val albumArt = LyricRepository.getInstance().liveAlbumArt.value
        if (albumArt != null) {
            views.setImageViewBitmap(R.id.custom_expand_cover, scaleBitmap(albumArt, 116))
        } else {
            views.setImageViewResource(R.id.custom_expand_cover, R.drawable.ic_music_note)
        }
        views.setImageViewBitmap(
            R.id.custom_expand_progress,
            createProgressBarBitmap(progressPercent, progressBarColor, darkMode)
        )
        views.setImageViewResource(
            R.id.custom_expand_play_pause,
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )
        views.setInt(R.id.custom_expand_prev, "setColorFilter", iconTintColor)
        views.setInt(R.id.custom_expand_play_pause, "setColorFilter", iconTintColor)
        views.setInt(R.id.custom_expand_next, "setColorFilter", iconTintColor)
        views.setInt(R.id.custom_expand_miplay, "setColorFilter", iconTintColor)
        views.setOnClickPendingIntent(
            R.id.custom_expand_prev,
            PendingIntent.getBroadcast(
                context,
                2100,
                Intent("com.example.islandlyrics.ACTION_MEDIA_PREV")
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        views.setOnClickPendingIntent(
            R.id.custom_expand_play_pause,
            PendingIntent.getBroadcast(
                context,
                2101,
                Intent("com.example.islandlyrics.ACTION_MEDIA_PLAY_PAUSE")
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        views.setOnClickPendingIntent(
            R.id.custom_expand_next,
            PendingIntent.getBroadcast(
                context,
                2102,
                Intent("com.example.islandlyrics.ACTION_MEDIA_NEXT")
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        cachedMiPlayIntent?.let { views.setOnClickPendingIntent(R.id.custom_expand_miplay, it) }
        return views
    }

    private fun createProgressBarBitmap(
        progressPercent: Int,
        progressColor: String,
        darkMode: Boolean
    ): Bitmap {
        val cacheKey = "wide:$progressPercent:$progressColor:$darkMode"
        progressBitmapCache[cacheKey]?.let { return it }

        val width = dpToPx(240)
        val height = dpToPx(6)
        val radius = height / 2f
        val progress = progressPercent.coerceIn(0, 100)
        val progressWidth = if (progress <= 0) 0f else (width * (progress / 100f)).coerceAtLeast(radius * 2)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())

        paint.color = android.graphics.Color.parseColor(if (darkMode) "#33FFFFFF" else "#26000000")
        canvas.drawRoundRect(rect, radius, radius, paint)

        if (progressWidth > 0f) {
            paint.color = android.graphics.Color.parseColor(progressColor)
            canvas.drawRoundRect(
                android.graphics.RectF(0f, 0f, progressWidth, height.toFloat()),
                radius,
                radius,
                paint
            )
        }

        putBitmapCacheEntry(progressBitmapCache, cacheKey, bitmap, maxEntries = 12)
        return bitmap
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private fun createCustomTinyRemoteViews(
        state: UIState,
        subText: String,
        progressPercent: Int,
        progressBarColor: String,
        darkMode: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.super_island_custom_tiny)
        val primaryTextColor = android.graphics.Color.parseColor(if (darkMode) "#FFFFFFFF" else "#FF111111")
        val secondaryTextColor = android.graphics.Color.parseColor(if (darkMode) "#B3FFFFFF" else "#99000000")
        views.setTextViewText(
            R.id.custom_tiny_title,
            state.displayLyric.ifEmpty { state.title.ifEmpty { "♪" } }
        )
        views.setTextViewText(
            R.id.custom_tiny_subtitle,
            subText.ifEmpty { context.getString(R.string.channel_live_lyrics) }
        )
        views.setTextColor(R.id.custom_tiny_title, primaryTextColor)
        views.setTextColor(R.id.custom_tiny_subtitle, secondaryTextColor)
        views.setImageViewBitmap(
            R.id.custom_tiny_progress,
            createTinyProgressBarBitmap(progressPercent, progressBarColor, darkMode)
        )

        val albumArt = LyricRepository.getInstance().liveAlbumArt.value
        if (albumArt != null) {
            views.setImageViewBitmap(R.id.custom_tiny_cover, scaleBitmap(albumArt, 64))
        } else {
            views.setImageViewResource(R.id.custom_tiny_cover, R.drawable.ic_music_note)
        }
        return views
    }

    private fun createTinyProgressBarBitmap(
        progressPercent: Int,
        progressColor: String,
        darkMode: Boolean
    ): Bitmap {
        val cacheKey = "tiny:$progressPercent:$progressColor:$darkMode"
        progressBitmapCache[cacheKey]?.let { return it }

        val width = dpToPx(44)
        val height = dpToPx(4)
        val radius = height / 2f
        val progress = progressPercent.coerceIn(0, 100)
        val progressWidth = if (progress <= 0) 0f else (width * (progress / 100f)).coerceAtLeast(radius * 2)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())

        paint.color = android.graphics.Color.parseColor(if (darkMode) "#33FFFFFF" else "#26000000")
        canvas.drawRoundRect(rect, radius, radius, paint)

        if (progressWidth > 0f) {
            paint.color = android.graphics.Color.parseColor(progressColor)
            canvas.drawRoundRect(
                android.graphics.RectF(0f, 0f, progressWidth, height.toFloat()),
                radius,
                radius,
                paint
            )
        }

        putBitmapCacheEntry(progressBitmapCache, cacheKey, bitmap, maxEntries = 12)
        return bitmap
    }

    private fun putBitmapCacheEntry(
        cache: LinkedHashMap<String, Bitmap>,
        key: String,
        bitmap: Bitmap,
        maxEntries: Int
    ) {
        cache.put(key, bitmap)?.let { old ->
            if (old !== bitmap) recycleBitmap(old)
        }
        while (cache.size > maxEntries) {
            cache.remove(cache.entries.first().key)?.let { recycleBitmap(it) }
        }
    }

    private fun clearBitmapCaches() {
        scaledBitmapCache.values.forEach { recycleBitmap(it) }
        progressBitmapCache.values.forEach { recycleBitmap(it) }
        scaledBitmapCache.clear()
        progressBitmapCache.clear()
    }

    private fun recycleBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun createAction(key: String, value: Notification.Action): String {
        return key.also {
            standardActionBundle.putParcelable(it, value)
        }
    }

    private val standardActionBundle = android.os.Bundle()

    private fun com.xzakota.hyper.notification.island.model.BigIslandArea.applyBigIslandLyrics(
        fullLyric: String,
        displayLyric: String,
        titleWithArtist: String,
        islandKey: String?,
        showHighlightColor: Boolean
    ) {
        if (cachedSuperIslandLyricMode == "full") {
            val lyric = fullLyric.ifBlank { displayLyric }.ifBlank { "♪" }
            val showLeftCover = cachedSuperIslandFullLyricShowLeftCover && islandKey != null
            val split = splitBalancedByWeight(
                text = lyric,
                leftMaxWeight = if (showLeftCover) FULL_LYRIC_LEFT_WITH_COVER_WEIGHT else FULL_LYRIC_LEFT_NO_COVER_WEIGHT,
                rightMaxWeight = FULL_LYRIC_RIGHT_WEIGHT,
                leftVisualNumerator = FULL_LYRIC_LEFT_VISUAL_NUMERATOR,
                leftVisualDenominator = FULL_LYRIC_LEFT_VISUAL_DENOMINATOR
            )

            imageTextInfoLeft {
                type = 1
                if (showLeftCover) {
                    picInfo {
                        type = 1
                        pic = islandKey
                    }
                }
                textInfo {
                    title = split.left.ifEmpty { "♪" }
                    this.showHighlightColor = showHighlightColor
                    narrowFont = false
                }
            }
            this.textInfo = com.xzakota.hyper.notification.island.model.TextInfo().apply {
                title = split.right.ifEmpty { "♪" }
                this.showHighlightColor = showHighlightColor
                narrowFont = false
            }
            return
        }

        imageTextInfoLeft {
            type = 1
            if (islandKey != null) {
                picInfo {
                    type = 1
                    pic = islandKey
                }
            }
            textInfo {
                title = titleWithArtist.ifEmpty { "♪" }
                this.showHighlightColor = showHighlightColor
            }
        }
        this.textInfo = com.xzakota.hyper.notification.island.model.TextInfo().apply {
            title = displayLyric.ifEmpty { "♪" }
            this.showHighlightColor = showHighlightColor
            narrowFont = false
        }
    }

    private data class IslandLyricSplit(val left: String, val right: String)

    private fun splitBalancedByWeight(
        text: String,
        leftMaxWeight: Int,
        rightMaxWeight: Int,
        leftVisualNumerator: Int,
        leftVisualDenominator: Int
    ): IslandLyricSplit {
        val normalized = text.trim()
        if (normalized.isEmpty()) return IslandLyricSplit("", "")

        val chars = normalized.toList()
        var bestSplit = 1
        var bestScore = Int.MAX_VALUE

        for (splitIndex in 1..chars.size) {
            val left = chars.subList(0, splitIndex).joinToString("").trim()
            val right = chars.subList(splitIndex, chars.size).joinToString("").trim()
            val leftWeight = calculateTextWeight(left)
            val rightWeight = calculateTextWeight(right)

            if (leftWeight > leftMaxWeight || rightWeight > rightMaxWeight) continue

            val leftVisualWeight = scaleLeftVisualWeight(
                leftWeight = leftWeight,
                numerator = leftVisualNumerator,
                denominator = leftVisualDenominator
            )
            if (leftVisualWeight < rightWeight) continue

            val usedWeight = leftWeight + rightWeight
            val balancePenalty = kotlin.math.abs(leftVisualWeight - rightWeight) * 4
            val unusedPenalty = (leftMaxWeight + rightMaxWeight - usedWeight) * 2
            val edgePenalty = if (left.isEmpty() || right.isEmpty()) 20 else 0
            val score = balancePenalty + unusedPenalty + edgePenalty

            if (score < bestScore) {
                bestScore = score
                bestSplit = splitIndex
            }
        }

        if (bestScore == Int.MAX_VALUE) {
            val totalWeight = calculateTextWeight(normalized)
            val leftTarget = minOf(
                leftMaxWeight,
                maxOf(
                    1,
                    (totalWeight * leftVisualDenominator + leftVisualNumerator + leftVisualDenominator - 1) /
                        (leftVisualNumerator + leftVisualDenominator)
                )
            )
            val left = extractTextByWeight(normalized, 0, leftTarget)
            val rightStart = left.length
            val right = extractTextByWeight(normalized.drop(rightStart).trimStart(), 0, rightMaxWeight)
            return IslandLyricSplit(left, right)
        }

        return IslandLyricSplit(
            left = chars.subList(0, bestSplit).joinToString("").trim(),
            right = chars.subList(bestSplit, chars.size).joinToString("").trim()
        )
    }

    private fun scaleLeftVisualWeight(
        leftWeight: Int,
        numerator: Int,
        denominator: Int
    ): Int {
        return (leftWeight * numerator) / denominator
    }

    private fun calculateTextWeight(text: String): Int = text.sumOf { charWeight(it) }

    private fun charWeight(c: Char): Int {
        if (c.isWhitespace()) return 0
        return when (Character.UnicodeBlock.of(c)) {
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.HANGUL_SYLLABLES -> 2
            else -> 1
        }
    }

    private fun extractTextByWeight(text: String, startWeight: Int, maxWeight: Int): String {
        var currentWeight = 0
        var startIndex = 0
        var endIndex = 0
        for (i in text.indices) {
            if (currentWeight >= startWeight) {
                startIndex = i
                break
            }
            currentWeight += charWeight(text[i])
        }
        while (startIndex < text.length && text[startIndex].isWhitespace()) {
            startIndex++
        }
        currentWeight = 0
        for (i in startIndex until text.length) {
            currentWeight += charWeight(text[i])
            if (currentWeight > maxWeight) break
            endIndex = i + 1
        }
        if (endIndex <= startIndex) return ""
        return text.substring(startIndex, endIndex).trim()
    }

    private fun buildStandardFocusBundle(
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
        standardActionBundle.clear()
        val bundle = FocusNotification.buildV3 {
        business = "lyric_display"
        isShowNotification = true
        enableFloat = false
        updatable = true
        islandFirstFloat = false
        aodTitle = displayLyric.take(20).ifEmpty { "♪" }
        val avatarKey = cachedAvatarIcon?.let { createPicture("miui.focus.pic_avatar", it) }
        val appKey = cachedAppIcon?.let { createPicture("miui.focus.pic_app", it) }
        val islandKey = cachedIslandIcon?.let { createPicture("miui.focus.pic_island", it) }
        val islandSmallKey = cachedIslandSmallIcon?.let { createPicture("miui.land.pic_island", it) }
        val shareKey = cachedShareIcon?.let { createPicture("miui.focus.pic_share", it) }

        ticker = displayLyric.ifEmpty { subText.ifEmpty { state.title.ifEmpty { "♪" } } }
        tickerPic = appKey ?: islandSmallKey ?: avatarKey

        chatInfo {
            picProfile = avatarKey
            title = state.fullLyric.ifEmpty { state.title.ifEmpty { "♪" } }
            content = subText
            appIconPkg = packageName
        }

        if (cachedActionStyle == "media_controls") {
            actions {
                val effectiveButtonLayout = if (cachedSuperIslandNotificationStyle == "advanced_beta") "three_button" else cachedMediaButtonLayout
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
                        actionIcon = cachedPrevIcon?.let { createPicture("miui.focus.pic_btn_prev", it) }
                        actionIconDark = cachedPrevIconDark?.let { createPicture("miui.focus.pic_btn_prev_dark", it) }
                        clickWithCollapse = false
                    }
                }
                addActionInfo {
                    type = 0
                    action = playPauseActionKey
                    actionIcon = cachedPlayPauseIcon?.let { createPicture("miui.focus.pic_btn_play_pause", it) }
                    actionIconDark = cachedPlayPauseIconDark?.let { createPicture("miui.focus.pic_btn_play_pause_dark", it) }
                    clickWithCollapse = false
                }
                addActionInfo {
                    type = 0
                    action = nextActionKey
                    actionIcon = cachedNextIcon?.let { createPicture("miui.focus.pic_btn_next", it) }
                    actionIconDark = cachedNextIconDark?.let { createPicture("miui.focus.pic_btn_next_dark", it) }
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
                applyBigIslandLyrics(
                    fullLyric = state.fullLyric,
                    displayLyric = displayLyric,
                    titleWithArtist = titleWithArtist,
                    islandKey = islandKey,
                    showHighlightColor = showHighlightColor
                )
            }

            if (cachedSuperIslandShareEnabled) {
                shareData {
                    pic = shareKey
                    title = state.title.ifEmpty { "♪" }
                    content = state.fullLyric.ifEmpty { "♪" }
                    val shareArtist = if (state.artist.isNotBlank()) state.artist else "未知歌手"
                    val shareSong = state.title.ifEmpty { "未知歌曲" }
                    this.shareContent = when (cachedSuperIslandShareFormat) {
                        "format_2" -> "${state.fullLyric} -$shareArtist\uff0c$shareSong"
                        "format_3" -> "${state.fullLyric}\n$shareArtist\uff0c$shareSong"
                        else -> "${state.fullLyric}\n$shareSong by $shareArtist"
                    }
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
        if (standardActionBundle.keySet().isNotEmpty()) {
            bundle.putBundle("miui.focus.actions", android.os.Bundle(standardActionBundle))
        }
        return bundle
    }

    private fun mergeCustomFocusWithStandardIsland(customExtras: android.os.Bundle, standardExtras: android.os.Bundle): android.os.Bundle {
        val merged = android.os.Bundle(customExtras)
        val customJson = customExtras.getString("miui.focus.param.custom") ?: return merged
        val standardJson = standardExtras.getString("miui.focus.param") ?: return merged

        runCatching {
            val customRoot = JSONObject(customJson)
            val standardRoot = JSONObject(standardJson)
            val island = standardRoot.optJSONObject("param_v2")?.optJSONObject("param_island")
            if (island != null) {
                customRoot.put("param_island", island)
                merged.putString("miui.focus.param.custom", customRoot.toString())
            }
        }

        return merged
    }

    companion object {
        private const val CHANNEL_ID = "lyric_capsule_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PROGRESS_NOTIFY_STEP_PERCENT = 2
        private const val FULL_LYRIC_LEFT_WITH_COVER_WEIGHT = 13
        private const val FULL_LYRIC_LEFT_NO_COVER_WEIGHT = 16
        private const val FULL_LYRIC_RIGHT_WEIGHT = 14
        private const val FULL_LYRIC_LEFT_VISUAL_NUMERATOR = 5
        private const val FULL_LYRIC_LEFT_VISUAL_DENOMINATOR = 6
    }
}
