package com.example.islandlyrics.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import com.example.islandlyrics.R
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.service.LyricService
import com.xzakota.hyper.notification.focus.FocusNotification
import kotlinx.coroutines.launch

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

    private var cachedNotification: Notification? = null
    private var cachedBuilder: Notification.Builder? = null
    
    // Preferences
    private var cachedClickStyle = "default"
    private var cachedSuperIslandTextColorEnabled = false
    private var cachedSuperIslandShareEnabled = true
    private var cachedSuperIslandShareFormat = "format_1"
    private var cachedProgressBarColorEnabled = false
    private var cachedActionStyle = "disabled"

    private var cachedContentIntent: PendingIntent? = null
    private var networkCutJob: kotlinx.coroutines.Job? = null

    private val prefs by lazy { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            "notification_click_style" -> {
                cachedClickStyle = p.getString(key, "default") ?: "default"
                cachedContentIntent = createContentIntent()
            }
            "super_island_text_color_enabled" -> cachedSuperIslandTextColorEnabled = p.getBoolean(key, false)
            "super_island_share_enabled" -> cachedSuperIslandShareEnabled = p.getBoolean(key, true)
            "super_island_share_format" -> cachedSuperIslandShareFormat = p.getString(key, "format_1") ?: "format_1"
            "progress_bar_color_enabled" -> cachedProgressBarColorEnabled = p.getBoolean(key, false)
            "notification_actions_style" -> cachedActionStyle = p.getString(key, "disabled") ?: "disabled"
        }
    }

    private fun loadPreferences() {
        cachedClickStyle = prefs.getString("notification_click_style", "default") ?: "default"
        cachedContentIntent = createContentIntent()
        cachedSuperIslandTextColorEnabled = prefs.getBoolean("super_island_text_color_enabled", false)
        cachedSuperIslandShareEnabled = prefs.getBoolean("super_island_share_enabled", true)
        cachedSuperIslandShareFormat = prefs.getString("super_island_share_format", "format_1") ?: "format_1"
        cachedProgressBarColorEnabled = prefs.getBoolean("progress_bar_color_enabled", false)
        cachedActionStyle = prefs.getString("notification_actions_style", "disabled") ?: "disabled"
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    // Change detection tracking
    private var lastSentDisplayLyric = ""
    private var lastSentProgressPercent = -1
    private var lastSentSubText = ""
    private var isFirstNotification = true
    
    private var lastAlbumArtHash = 0
    private var lastPicAppHash = 0
    private var lastPicActionsHash = 0
    
    private var cachedAvatarIcon: Icon? = null
    private var cachedIslandIcon: Icon? = null
    private var cachedIslandSmallIcon: Icon? = null
    private var cachedShareIcon: Icon? = null
    private var cachedAppIcon: Icon? = null
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
        cachedPlayPauseIcon = null
        cachedNextIcon = null

        cachedBuilder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            
        cachedNotification = cachedBuilder?.build()
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        manager?.cancel(NOTIFICATION_ID)
        cachedNotification = null
        cachedBuilder = null
        
        cachedAvatarIcon = null
        cachedIslandIcon = null
        cachedIslandSmallIcon = null
        cachedShareIcon = null
        cachedAppIcon = null
        cachedPlayPauseIcon = null
        cachedNextIcon = null
        
        // Ensure network is restored if we were blocking it
        networkCutJob?.cancel()
        if (prefs.getBoolean("block_xmsf_network", false)) {
            kotlinx.coroutines.GlobalScope.launch {
                com.example.islandlyrics.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, true)
            }
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_live_lyrics),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
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

        val actionsHash = if (cachedActionStyle == "media_controls") (if (isPlaying) 1 else 0) else -1
        if (actionsHash != lastPicActionsHash) {
            if (cachedActionStyle == "media_controls") {
                val playPauseResId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                val playPauseIconBitmap = renderButtonIcon(playPauseResId, 96, 0.6f, "#1A1A1A")
                val nextIconBitmap = renderButtonIcon(R.drawable.ic_skip_next, 96, 0.5f, "#333333")

                cachedPlayPauseIcon = Icon.createWithBitmap(playPauseIconBitmap)
                cachedNextIcon = Icon.createWithBitmap(nextIconBitmap)
            } else {
                cachedPlayPauseIcon = null
                cachedNextIcon = null
            }
            lastPicActionsHash = actionsHash
        }
    }

    fun render(state: UIState) {
        if (!isRunning) return
        val notification = cachedNotification ?: return

        val displayLyric = state.displayLyric
        val subText = if (state.artist.isNotBlank()) "${state.title} - ${state.artist}" else state.title
        val progressPercent = state.progressCurrent
        val albumColor = state.albumColor

        // Color changed?
        val colorChanged = albumColor != lastAppliedAlbumColor

        // Pre-JSON FAST PATH: skip expensive serialization when inputs haven't changed
        if (!isFirstNotification && !colorChanged &&
            displayLyric == lastSentDisplayLyric &&
            progressPercent == lastSentProgressPercent &&
            subText == lastSentSubText) {
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

        val extras = FocusNotification.buildV3 {
            business = "lyric_display"
            enableFloat = false
            updatable = true
            islandFirstFloat = false
            aodTitle = displayLyric.take(20).ifEmpty { "♪" }

            val avatarKey = cachedAvatarIcon?.let { createPicture("miui.focus.pic_avatar", it) }
            val appKey = cachedAppIcon?.let { createPicture("miui.focus.pic_app", it) }
            val islandKey = cachedIslandIcon?.let { createPicture("miui.focus.pic_island", it) }
            val islandSmallKey = cachedIslandSmallIcon?.let { createPicture("miui.land.pic_island", it) }
            val shareKey = cachedShareIcon?.let { createPicture("miui.focus.pic_share", it) }
            
            chatInfo {
                picProfile = avatarKey
                title = state.fullLyric.ifEmpty { state.title.ifEmpty { "♪" } }
                content = subText
                appIconPkg = packageName
                // picApp was removed or uses appIconPkg in V3 API
            }

            if (cachedActionStyle == "media_controls") {
                actions {
                    val playPauseUri = Intent("com.example.islandlyrics.ACTION_MEDIA_PLAY_PAUSE")
                        .setPackage(context.packageName)
                        .toUri(Intent.URI_INTENT_SCHEME)
                    val nextUri = Intent("com.example.islandlyrics.ACTION_MEDIA_NEXT")
                        .setPackage(context.packageName)
                        .toUri(Intent.URI_INTENT_SCHEME)

                    addActionInfo {
                        actionIcon = cachedPlayPauseIcon?.let { createPicture("miui.focus.pic_btn_play_pause", it) }
                        type = 0
                        actionIntentType = 2
                        this.actionIntent = playPauseUri
                    }
                    addActionInfo {
                        actionIcon = cachedNextIcon?.let { createPicture("miui.focus.pic_btn_next", it) }
                        type = 0
                        actionIntentType = 2
                        this.actionIntent = nextUri
                    }
                }
            } else {
                // Removed picInfo to switch from Template 7 to Template 21 (IM + Progress 2)
                
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
                            val titleWithArtist = if (state.artist.isNotBlank()) "${state.title} - ${state.artist}" else state.title
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
                            "format_2" -> "${state.fullLyric} -$shareArtist，$shareSong"
                            "format_3" -> "${state.fullLyric}\n$shareArtist，$shareSong"
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

        val newParams = extras.getString("miui.focus.param")

        // Secondary CHANGE DETECTION
        val oldParams = notification.extras.getString("miui.focus.param")
        if (oldParams == newParams && !colorChanged && !isFirstNotification) {
            return
        }

        // Apply new extras (clearing old explicitly replaced parts)
        notification.extras.putAll(extras)
        
        notification.color = if (cachedActionStyle == "media_controls") 0xFF757575.toInt() else albumColor
        lastAppliedAlbumColor = albumColor

        notification.extras.putString(Notification.EXTRA_TITLE, state.fullLyric.ifEmpty { "Capsulyric" })
        notification.extras.putString(Notification.EXTRA_TEXT, subText)
        notification.contentIntent = cachedContentIntent

        lastSentDisplayLyric = displayLyric
        lastSentProgressPercent = progressPercent
        lastSentSubText = subText

        notifyWithNetworkCut(notification, isFirstNotification)
        if (isFirstNotification) {
            isFirstNotification = false
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun notifyWithNetworkCut(notification: Notification, isFirst: Boolean) {
        val bypassWhitelist = prefs.getBoolean("block_xmsf_network", false)
        if (bypassWhitelist) {
            networkCutJob?.cancel()
            networkCutJob = kotlinx.coroutines.GlobalScope.launch {
                com.example.islandlyrics.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, false)
                kotlinx.coroutines.delay(50) // Wait brief moment for firewall rule to apply
                if (isFirst) {
                    service.startForeground(NOTIFICATION_ID, notification)
                } else {
                    manager?.notify(NOTIFICATION_ID, notification)
                }
                kotlinx.coroutines.delay(50) // Keep offline for a brief moment
                com.example.islandlyrics.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, true)
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

    companion object {
        private const val CHANNEL_ID = "lyric_capsule_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
