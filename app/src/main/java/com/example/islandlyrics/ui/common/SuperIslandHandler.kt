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

    private var cachedContentIntent: PendingIntent? = null
    private var cachedMiPlayIntent: PendingIntent? = null
    private var networkCutJob: kotlinx.coroutines.Job? = null
    // Keep the blind window aligned with the working InstallerX implementation.
    // 50ms is too tight on HyperOS 3.0 / Android 16 and often expires before
    // SystemUI finishes scanning the posted notification.
    private val networkCutDurationMs = 100L
    private var networkCutSeq = 0L
    private val networkCutMutex = Mutex()

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
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    // Change detection tracking
    private var lastSentDisplayLyric = ""
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
    private var cachedPlayPauseIcon: Icon? = null
    private var cachedNextIcon: Icon? = null

    private var lastAppliedAlbumColor = 0

    init {
        createChannel()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        loadPreferences()

        lastSentDisplayLyric = ""
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
        cachedPlayPauseIcon = null
        cachedNextIcon = null

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
        cachedPlayPauseIcon = null
        cachedNextIcon = null
        
        // Ensure network is restored if we were blocking it
        networkCutJob?.cancel()
        if (prefs.getBoolean("block_xmsf_network", false)) {
            scope.launch {
                com.example.islandlyrics.integration.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, true)
            }
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
                    renderButtonIcon(R.drawable.ic_skip_previous, 96, 0.5f, "#333333")
                } else {
                    null
                }
                val playPauseResId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                val playPauseIconBitmap = renderButtonIcon(playPauseResId, 96, 0.42f, null)
                val nextIconBitmap = renderButtonIcon(R.drawable.ic_skip_next, 96, 0.5f, "#333333")

                cachedPrevIcon = prevIconBitmap?.let { Icon.createWithBitmap(it) }
                cachedPlayPauseIcon = Icon.createWithBitmap(playPauseIconBitmap)
                cachedNextIcon = Icon.createWithBitmap(nextIconBitmap)
            } else {
                cachedPrevIcon = null
                cachedPlayPauseIcon = null
                cachedNextIcon = null
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
        // When the previous lyric fills the display area (≥8 CJK), the system caches the right-aligned
        // render state and reuses it for the next lyric, causing it to appear truncated from non-zero offset.
        // Re-using the startForeground path (same as track switching) forces a full re-layout.
        val lyricLineChanged = !isFirstNotification && !trackChanged
                && displayLyric.isNotEmpty() && displayLyric != lastSentDisplayLyric
        if (lyricLineChanged) {
            isFirstNotification = true
        }

        // 3. CONTENT-AWARE THROTTLING
        val contentChanged = trackChanged || lyricLineChanged || displayLyric != lastSentDisplayLyric || state.isPlaying != lastSentIsPlaying
        val now = System.currentTimeMillis()
        
        if (!isFirstNotification && !colorChanged && !contentChanged) {
            // Only progress changed. Apply 1000ms throttle for Android 15 stability.
            if (now - lastNotifyTime < throttleIntervalMs) {
                return
            }
        }

        // Pre-JSON FAST PATH: skip expensive serialization if literally nothing changed (including exact progress)
        if (!isFirstNotification && !colorChanged && !contentChanged && progressPercent == lastSentProgressPercent) {
            return
        }

        val metadata = LyricRepository.getInstance().liveMetadata.value
        val albumArt = LyricRepository.getInstance().liveAlbumArt.value

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
        lastSentProgressPercent = progressPercent
        lastSentSubText = subText
        lastSentIsPlaying = state.isPlaying
        lastSentTitle = state.title
        lastSentArtist = state.artist
        lastNotifyTime = System.currentTimeMillis()

        notifyWithNetworkCut(notification, isFirstNotification)
        if (isFirstNotification) {
            isFirstNotification = false
        }
    }

    private fun notifyWithNetworkCut(notification: Notification, isFirst: Boolean) {
        val bypassWhitelist = prefs.getBoolean("block_xmsf_network", false)
        if (bypassWhitelist) {
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
        } else {
            if (isFirst) {
                service.startForeground(NOTIFICATION_ID, notification)
            } else {
                manager?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun renderButtonIcon(resourceId: Int, size: Int, iconScale: Float, bgColorHex: String? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        if (bgColorHex != null) {
            paint.color = android.graphics.Color.parseColor(bgColorHex)
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        }

        val drawable = context.getDrawable(resourceId) ?: return bitmap
        drawable.mutate().setTint(android.graphics.Color.WHITE)
        val iconSize = (size * iconScale).toInt()
        val margin = (size - iconSize) / 2
        drawable.setBounds(margin, margin, margin + iconSize, margin + iconSize)
        drawable.draw(canvas)

        return bitmap
    }

    private fun scaleBitmap(src: Bitmap, targetSize: Int): Bitmap {
        val scaled = if (src.width == targetSize && src.height == targetSize) src 
                     else Bitmap.createScaledBitmap(src, targetSize, targetSize, true)
                     
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())
        
        val cornerRadius = targetSize * 0.2f
        
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        
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
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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

        return bitmap
    }

    private fun buildStandardFocusBundle(
        state: UIState,
        displayLyric: String,
        subText: String,
        progressPercent: Int,
        hexColor: String,
        showHighlightColor: Boolean,
        progressBarColor: String,
        packageName: String,
        titleWithArtist: String
    ) = FocusNotification.buildV3 {
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
                val playPauseServiceIntent = Intent(context, LyricService::class.java)
                    .setAction("ACTION_MEDIA_PLAY_PAUSE")
                val playPauseServiceUri = playPauseServiceIntent.toUri(Intent.URI_INTENT_SCHEME)
                val prevIntent = Intent("com.example.islandlyrics.ACTION_MEDIA_PREV")
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                val nextIntent = Intent("com.example.islandlyrics.ACTION_MEDIA_NEXT")
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)

                val prevPending = PendingIntent.getBroadcast(
                    context,
                    2000,
                    prevIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val nextPending = PendingIntent.getBroadcast(
                    context,
                    2002,
                    nextIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val prevAction = Notification.Action.Builder(
                    cachedPrevIcon ?: Icon.createWithResource(context, R.drawable.ic_skip_previous),
                    "",
                    prevPending
                ).build()
                val nextAction = Notification.Action.Builder(
                    cachedNextIcon ?: Icon.createWithResource(context, R.drawable.ic_skip_next),
                    "",
                    nextPending
                ).build()

                val prevActionKey = createAction("miui.focus.action_key_prev", prevAction)
                val nextActionKey = createAction("miui.focus.action_key_next", nextAction)

                if (showPrevButton) {
                    addActionInfo {
                        actionIcon = cachedPrevIcon?.let { createPicture("miui.focus.pic_btn_prev", it) }
                        type = 0
                        action = prevActionKey
                    }
                }
                addActionInfo {
                    actionIcon = cachedPlayPauseIcon?.let { createPicture("miui.focus.pic_btn_play_pause", it) }
                    actionTitle = if (state.isPlaying) "Pause" else "Play"
                    type = 1
                    actionIntentType = 3
                    actionIntent = playPauseServiceUri
                    clickWithCollapse = false
                    actionBgColor = "#1A1A1A"
                    actionBgColorDark = "#1A1A1A"
                    progressInfo {
                        progress = progressPercent
                        colorProgress = "#FFFFFF"
                    }
                }
                addActionInfo {
                    actionIcon = cachedNextIcon?.let { createPicture("miui.focus.pic_btn_next", it) }
                    type = 0
                    action = nextActionKey
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
    }
}
