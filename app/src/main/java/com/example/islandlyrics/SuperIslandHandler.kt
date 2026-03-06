package com.example.islandlyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import org.json.JSONObject
import android.content.SharedPreferences

/**
 * SuperIslandHandler
 * Pure JSON Renderer for Xiaomi Super Island notifications using Template 7/12.
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
    private var cachedPicsBundle: Bundle? = null
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
        cachedPicsBundle = null
        lastAppliedAlbumColor = 0

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
        cachedPicsBundle = null
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

    fun render(state: UIState) {
        if (!isRunning) return
        val notification = cachedNotification ?: return

        val displayLyric = state.displayLyric
        val subText = if (state.artist.isNotBlank()) "${state.title} - ${state.artist}" else state.title
        val progressPercent = state.progressCurrent
        val albumColor = state.albumColor

        // Color changed?
        val colorChanged = albumColor != lastAppliedAlbumColor

        // Pre-JSON FAST PATH: skip expensive JSON serialization when inputs haven't changed
        if (!isFirstNotification && !colorChanged &&
            displayLyric == lastSentDisplayLyric &&
            progressPercent == lastSentProgressPercent &&
            subText == lastSentSubText) {
            return
        }

        val islandParams = buildIslandParamsJson(
            displayLyric = displayLyric,
            fullLyric = state.fullLyric,
            subText = subText,
            progressPercent = progressPercent,
            title = state.title,
            artist = state.artist,
            albumColor = albumColor,
            isPlaying = state.isPlaying,
            packageName = state.mediaPackage
        )

        // Secondary CHANGE DETECTION
        val oldParams = notification.extras.getString("miui.focus.param")
        if (oldParams == islandParams && !colorChanged && !isFirstNotification) {
            return
        }

        val metadata = LyricRepository.getInstance().liveMetadata.value
        val albumArt = LyricRepository.getInstance().liveAlbumArt.value

        applyPicsAndActions(metadata, albumArt, notification, state.isPlaying)
        
        notification.extras.putString("miui.focus.param", islandParams)
        
        notification.color = if (cachedActionStyle == "media_controls") 0xFF757575.toInt() else albumColor
        lastAppliedAlbumColor = albumColor

        notification.extras.putString(Notification.EXTRA_TITLE, state.fullLyric.ifEmpty { "Capsulyric" })
        notification.extras.putString(Notification.EXTRA_TEXT, subText)
        notification.contentIntent = cachedContentIntent

        lastSentDisplayLyric = displayLyric
        lastSentProgressPercent = progressPercent
        lastSentSubText = subText

        if (isFirstNotification) {
            service.startForeground(NOTIFICATION_ID, notification)
            isFirstNotification = false
        } else {
            manager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun applyPicsAndActions(
        metadata: LyricRepository.MediaInfo?, 
        albumArt: Bitmap?, 
        notification: Notification,
        isPlaying: Boolean
    ) {
        val targetExtras = notification.extras

        val albumArtHash = albumArt?.hashCode() ?: 0
        if (albumArtHash != lastAlbumArtHash || cachedPicsBundle == null) {
            val pics = Bundle()
            if (albumArt != null) {
                pics.putParcelable("miui.focus.pic_avatar", Icon.createWithBitmap(scaleBitmap(albumArt, 480)))
                pics.putParcelable("miui.focus.pic_island", Icon.createWithBitmap(scaleBitmap(albumArt, 120)))
                pics.putParcelable("miui.land.pic_island", Icon.createWithBitmap(scaleBitmap(albumArt, 88)))
                pics.putParcelable("miui.focus.pic_share", Icon.createWithBitmap(scaleBitmap(albumArt, 224)))
            }
            
            val appIcon = getAppIcon(metadata?.packageName)
            if (appIcon != null) {
                pics.putParcelable("miui.focus.pic_app", Icon.createWithBitmap(scaleBitmap(appIcon, 96)))
            }
            cachedPicsBundle = pics
            lastAlbumArtHash = albumArtHash
            lastPicAppHash = metadata?.packageName?.hashCode() ?: 0
        } else if (metadata?.packageName?.hashCode() != lastPicAppHash) {
            val pics = Bundle(cachedPicsBundle)
            val appIcon = getAppIcon(metadata?.packageName)
            if (appIcon != null) {
                pics.putParcelable("miui.focus.pic_app", Icon.createWithBitmap(scaleBitmap(appIcon, 96)))
            }
            cachedPicsBundle = pics
            lastPicAppHash = metadata?.packageName?.hashCode() ?: 0
        }

        val actionsHash = if (cachedActionStyle == "media_controls") (if (isPlaying) 1 else 0) else -1
        if (actionsHash != lastPicActionsHash || cachedPicsBundle?.containsKey("miui.focus.pic_btn_play_pause") == false) {
            if (cachedActionStyle == "media_controls") {
                val playPauseResId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                val playPauseIconBitmap = renderButtonIcon(playPauseResId, 96, 0.6f, "#1A1A1A")
                val nextIconBitmap = renderButtonIcon(R.drawable.ic_skip_next, 96, 0.5f, "#333333")

                cachedPicsBundle?.let {
                    it.putParcelable("miui.focus.pic_btn_play_pause", Icon.createWithBitmap(playPauseIconBitmap))
                    it.putParcelable("miui.focus.pic_btn_next", Icon.createWithBitmap(nextIconBitmap))
                }
            } else {
                cachedPicsBundle?.remove("miui.focus.pic_btn_play_pause")
                cachedPicsBundle?.remove("miui.focus.pic_btn_next")
            }
            lastPicActionsHash = actionsHash
        }

        cachedPicsBundle?.let { targetExtras.putBundle("miui.focus.pics", it) }
        targetExtras.remove("miui.focus.actions")
    }

    private fun buildIslandParamsJson(
        displayLyric: String,
        fullLyric: String,
        subText: String,
        progressPercent: Int,
        title: String,
        artist: String,
        albumColor: Int,
        isPlaying: Boolean,
        packageName: String
    ): String {
        val root = JSONObject()
        val paramV2 = JSONObject()

        val hexColor = String.format("#FF%06X", 0xFFFFFF and albumColor)
        val showHighlightColor = cachedSuperIslandTextColorEnabled 
        val ringColor = if (showHighlightColor) hexColor else "#757575"

        paramV2.put("protocol", 1)
        paramV2.put("business", "lyric_display")
        paramV2.put("enableFloat", false)
        paramV2.put("updatable", true)
        paramV2.put("islandFirstFloat", false)
        paramV2.put("aodTitle", displayLyric.take(20).ifEmpty { "♪" })

        val progressBarColor = if (cachedProgressBarColorEnabled) hexColor else "#757575"

        if (cachedActionStyle == "media_controls") {
            paramV2.put("template", 12)
            
            val chatInfo = JSONObject()
            chatInfo.put("picProfile", "miui.focus.pic_avatar")
            chatInfo.put("title", fullLyric.ifEmpty { title.ifEmpty { "♪" } }) 
            chatInfo.put("content", subText)
            val playbackPkg = packageName.ifEmpty { context.packageName }
            chatInfo.put("appIconPkg", playbackPkg)
            paramV2.put("chatInfo", chatInfo)

            val actionsArray = org.json.JSONArray()
            val playPauseUri = Intent("com.example.islandlyrics.ACTION_MEDIA_PLAY_PAUSE")
                .setPackage(context.packageName)
                .toUri(Intent.URI_INTENT_SCHEME)
            val nextUri = Intent("com.example.islandlyrics.ACTION_MEDIA_NEXT")
                .setPackage(context.packageName)
                .toUri(Intent.URI_INTENT_SCHEME)

            val btnPlay = JSONObject()
            btnPlay.put("actionIcon", "miui.focus.pic_btn_play_pause")
            btnPlay.put("type", 0)  
            btnPlay.put("actionIntentType", 2)
            btnPlay.put("actionIntent", playPauseUri)
            actionsArray.put(btnPlay)

            val btnNext = JSONObject()
            btnNext.put("actionIcon", "miui.focus.pic_btn_next")
            btnNext.put("type", 0)
            btnNext.put("actionIntentType", 2)
            btnNext.put("actionIntent", nextUri)
            actionsArray.put(btnNext)

            paramV2.put("actions", actionsArray)

        } else {
            paramV2.put("template", 7)

            val chatInfo = JSONObject()
            chatInfo.put("picProfile", "miui.focus.pic_avatar")
            chatInfo.put("title", fullLyric.ifEmpty { title.ifEmpty { "♪" } })
            chatInfo.put("content", subText)
            val playbackPkg = packageName.ifEmpty { context.packageName }
            chatInfo.put("appIconPkg", playbackPkg)
            chatInfo.put("picApp", "miui.focus.pic_app")
            paramV2.put("chatInfo", chatInfo)
            
            val picInfo = JSONObject()
            picInfo.put("type", 1)
            picInfo.put("pic", "miui.focus.pic_app")
            paramV2.put("picInfo", picInfo)

            val progressInfo = JSONObject()
            progressInfo.put("progress", progressPercent)
            progressInfo.put("colorProgress", progressBarColor)
            progressInfo.put("colorProgressEnd", progressBarColor)
            progressInfo.put("colorBackground", "#ffffffff")
            paramV2.put("progressInfo", progressInfo)
        }

        val paramIsland = JSONObject()
        paramIsland.put("islandProperty", 1)
        if (showHighlightColor) {
            paramIsland.put("highlightColor", hexColor)
        }

        val bigIslandArea = JSONObject()

        val imageTextInfoLeft = JSONObject()
        imageTextInfoLeft.put("type", 1)
        
        val picInfoA = JSONObject()
        picInfoA.put("type", 1)
        picInfoA.put("pic", "miui.focus.pic_island")
        imageTextInfoLeft.put("picInfo", picInfoA)
        
        val textInfoA = JSONObject()
        val titleWithArtist = if (artist.isNotBlank()) "$title - $artist" else title
        textInfoA.put("title", titleWithArtist.ifEmpty { "♪" })
        textInfoA.put("showHighlightColor", showHighlightColor)
        imageTextInfoLeft.put("textInfo", textInfoA)
        bigIslandArea.put("imageTextInfoLeft", imageTextInfoLeft)

        val textInfo = JSONObject()
        textInfo.put("title", displayLyric.ifEmpty { "♪" })
        textInfo.put("showHighlightColor", showHighlightColor)
        textInfo.put("narrowFont", false)
        bigIslandArea.put("textInfo", textInfo)

        paramIsland.put("bigIslandArea", bigIslandArea)

        if (cachedSuperIslandShareEnabled) {
            val shareData = JSONObject()
            shareData.put("pic", "miui.focus.pic_share")
            shareData.put("title", title.ifEmpty { "♪" })
            shareData.put("content", fullLyric.ifEmpty { "♪" })
            val shareArtist = if (artist.isNotBlank()) artist else "未知歌手"
            val shareSong = title.ifEmpty { "未知歌曲" }
            
            val shareContent = when (cachedSuperIslandShareFormat) {
                "format_2" -> "$fullLyric -$shareArtist，$shareSong"
                "format_3" -> "$fullLyric\n$shareArtist，$shareSong"
                else -> "$fullLyric\n$shareSong by $shareArtist"
            }
            shareData.put("shareContent", shareContent)
            paramIsland.put("shareData", shareData)
        }

        val smallIslandArea = JSONObject()
        val combinePicInfoSmall = JSONObject()
        val picInfoSmall = JSONObject()
        picInfoSmall.put("type", 1)
        picInfoSmall.put("pic", "miui.land.pic_island")
        combinePicInfoSmall.put("picInfo", picInfoSmall)
        
        val ringInfoSmall = JSONObject()
        ringInfoSmall.put("progress", progressPercent)
        ringInfoSmall.put("colorReach", ringColor)
        ringInfoSmall.put("colorProgress", ringColor)
        ringInfoSmall.put("colorUnReach", "#333333")
        combinePicInfoSmall.put("progressInfo", ringInfoSmall)
        
        smallIslandArea.put("combinePicInfo", combinePicInfoSmall)
        paramIsland.put("smallIslandArea", smallIslandArea)

        paramV2.put("param_island", paramIsland)

        root.put("param_v2", paramV2)
        return root.toString()
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
