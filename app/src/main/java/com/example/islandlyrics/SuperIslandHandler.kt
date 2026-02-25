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
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import org.json.JSONObject

/**
 * SuperIslandHandler
 * Sends Xiaomi Super Island notifications using Template 7:
 *   展开态: IM图文组件 + 识别图形组件1 + 进度组件2
 *   摘要态: 图文组件1 (A区, album art) + 文本 (B区, scrolling lyrics)
 *   小岛:   album art thumbnail
 *
 * Content mapping:
 *   头像图   → album art (LyricRepository.liveAlbumArt)
 *   主要文本 → current lyric line
 *   次要文本 → "songTitle - artist"
 *   进度组件 → playback progress
 *   摘要态B区 → scrolling lyric text (maxDisplayWeight=8)
 */
class SuperIslandHandler(
    private val context: Context,
    private val service: LyricService
) {

    private val manager: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    var isRunning = false
        private set

    // Cached notification — built once, extras updated in-place
    private var cachedNotification: Notification? = null
    private var cachedBuilder: Notification.Builder? = null
    private var cachedClickStyle = "default"

    private val prefs by lazy { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == "notification_click_style") {
            cachedClickStyle = p.getString(key, "default") ?: "default"
            // We need to rebuild/update the intent on the next notification ping
            forceUpdateNotification()
        }
    }

    // Change detection
    private var lastLyric = ""
    private var lastProgress = -1
    private var lastAlbumArtHash = 0
    private var lastSubText = ""
    private var lastPackageName = ""

    // Observers — debounce to coalesce rapid-fire updates
    private val lyricObserver = Observer<LyricRepository.LyricInfo?> { scheduleUpdate() }
    private val progressObserver = Observer<LyricRepository.PlaybackProgress?> { scheduleUpdate() }
    private val albumArtObserver = Observer<Bitmap?> { scheduleUpdate() }
    private val metadataObserver = Observer<LyricRepository.MediaInfo?> { scheduleUpdate() }

    private var pendingUpdate = false

    private val debouncedUpdate = Runnable {
        pendingUpdate = false
        doUpdate()
    }

    private fun scheduleUpdate() {
        if (!isRunning) return
        if (!pendingUpdate) {
            pendingUpdate = true
            // ⚡ Snappy debounce: 80ms to coalesce rapid meta/progress pings without feeling laggy
            mainHandler.postDelayed(debouncedUpdate, 80)
        }
    }

    init {
        createChannel()
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        val repo = LyricRepository.getInstance()
        repo.liveLyric.observeForever(lyricObserver)
        repo.liveProgress.observeForever(progressObserver)
        repo.liveAlbumArt.observeForever(albumArtObserver)
        repo.liveMetadata.observeForever(metadataObserver)

        cachedClickStyle = prefs.getString("notification_click_style", "default") ?: "default"
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        lastLyric = ""
        lastProgress = -1
        lastAlbumArtHash = 0
        lastSubText = ""

        // Build the notification ONCE and send as foreground
        buildInitialNotification()
        AppLogger.getInstance().log(TAG, "🏝️ SuperIslandHandler started")
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false

        val repo = LyricRepository.getInstance()
        repo.liveLyric.removeObserver(lyricObserver)
        repo.liveProgress.removeObserver(progressObserver)
        repo.liveAlbumArt.removeObserver(albumArtObserver)
        repo.liveMetadata.removeObserver(metadataObserver)

        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)

        mainHandler.removeCallbacks(debouncedUpdate)
        manager?.cancel(NOTIFICATION_ID)
        cachedNotification = null
        cachedBuilder = null
        AppLogger.getInstance().log(TAG, "🏝️ SuperIslandHandler stopped")
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

    // ── Build notification ONCE, then reuse ──

    fun forceUpdateNotification() {
        if (!isRunning) return
        doUpdate(force = true)
    }

    private fun buildInitialNotification() {
        val repo = LyricRepository.getInstance()
        val lyric = repo.liveLyric.value?.lyric ?: ""
        val metadata = repo.liveMetadata.value
        val title = metadata?.title ?: ""
        val artist = metadata?.artist ?: ""
        val subText = if (artist.isNotBlank()) "$title - $artist" else title
        val albumArt = repo.liveAlbumArt.value
        val progressInfo = repo.liveProgress.value
        val progressPercent = if (progressInfo != null && progressInfo.duration > 0) {
            ((progressInfo.position.toFloat() / progressInfo.duration.toFloat()) * 100).toInt()
                .coerceIn(0, 100)
        } else 0

        lastLyric = lyric
        lastProgress = progressPercent
        lastAlbumArtHash = albumArt?.hashCode() ?: 0
        lastSubText = subText

        val islandParams = buildIslandParamsJson(lyric, subText, progressPercent, title)

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(lyric.ifEmpty { "Capsulyric" })
            .setContentText(subText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        // Set the appropriate ContentIntent based on preference
        builder.setContentIntent(createContentIntent())

        // Add pics + island params to builder extras BEFORE build
        val bundle = Bundle()
        val pics = Bundle()
        if (albumArt != null) {
            pics.putParcelable("miui.focus.pic_avatar", Icon.createWithBitmap(scaleBitmap(albumArt, 224)))
            pics.putParcelable("miui.focus.pic_island", Icon.createWithBitmap(scaleBitmap(albumArt, 88)))
            pics.putParcelable("miui.land.pic_island", Icon.createWithBitmap(scaleBitmap(albumArt, 88)))
        }
        val appIcon = getAppIcon(metadata?.packageName)
        if (appIcon != null) {
            pics.putParcelable("miui.focus.pic_app", Icon.createWithBitmap(scaleBitmap(appIcon, 96)))
        }
        bundle.putBundle("miui.focus.pics", pics)
        bundle.putString("miui.focus.param", islandParams)
        builder.addExtras(bundle)

        cachedBuilder = builder
        val notification = builder.build()
        cachedNotification = notification

        try {
            service.startForeground(NOTIFICATION_ID, notification)
            AppLogger.getInstance().log(TAG, "✅ Island initial notification pushed.")
        } catch (e: Exception) {
            LogManager.getInstance().e(context, TAG, "Island initial notification failed: $e")
        }
    }

    // ── Update: modify cached notification extras in-place ──

    private fun doUpdate(force: Boolean = false) {
        if (!isRunning) return
        val notification = cachedNotification ?: return

        val repo = LyricRepository.getInstance()
        val lyricInfo = repo.liveLyric.value
        val currentLyric = lyricInfo?.lyric ?: ""
        val progressInfo = repo.liveProgress.value
        val metadata = repo.liveMetadata.value
        val albumArt = repo.liveAlbumArt.value

        val title = metadata?.title ?: ""
        val artist = metadata?.artist ?: ""
        val subText = if (artist.isNotBlank()) "$title - $artist" else title

        val progressPercent = if (progressInfo != null && progressInfo.duration > 0) {
            ((progressInfo.position.toFloat() / progressInfo.duration.toFloat()) * 100).toInt()
                .coerceIn(0, 100)
        } else 0

        // Skip if nothing changed (unless forced)
        val artHash = albumArt?.hashCode() ?: 0
        if (!force && currentLyric == lastLyric && progressPercent == lastProgress
            && artHash == lastAlbumArtHash && subText == lastSubText
            && metadata?.packageName == lastPackageName) {
            return
        }

        val artChanged = artHash != lastAlbumArtHash

        lastLyric = currentLyric
        lastProgress = progressPercent
        lastAlbumArtHash = artHash
        lastSubText = subText
        lastPackageName = metadata?.packageName ?: ""

        try {
            // Update the island params JSON in-place on the SAME notification object
            val islandParams = buildIslandParamsJson(currentLyric, subText, progressPercent, title)
            notification.extras.putString("miui.focus.param", islandParams)

            // Update base notification text fields
            notification.extras.putString(Notification.EXTRA_TITLE, currentLyric.ifEmpty { "Capsulyric" })
            notification.extras.putString(Notification.EXTRA_TEXT, subText)

            // Update ContentIntent if it changed (or just refresh it)
            notification.contentIntent = createContentIntent()

            // Only update pics if album art or package actually changed
            if ((artChanged || metadata?.packageName != lastPackageName) && (albumArt != null)) {
                val pics = Bundle()
                pics.putParcelable("miui.focus.pic_avatar", Icon.createWithBitmap(scaleBitmap(albumArt, 224)))
                pics.putParcelable("miui.focus.pic_island", Icon.createWithBitmap(scaleBitmap(albumArt, 88)))
                pics.putParcelable("miui.land.pic_island", Icon.createWithBitmap(scaleBitmap(albumArt, 88)))
                
                val appIcon = getAppIcon(metadata?.packageName)
                if (appIcon != null) {
                    pics.putParcelable("miui.focus.pic_app", Icon.createWithBitmap(scaleBitmap(appIcon, 96)))
                }
                notification.extras.putBundle("miui.focus.pics", pics)
            }

            manager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            LogManager.getInstance().e(context, TAG, "Island build failed: $e")
        }
    }

    /**
     * Build the miui.focus.param JSON string for Template 7.
     */
    private fun buildIslandParamsJson(
        lyric: String,
        subText: String,
        progressPercent: Int,
        title: String
    ): String {
        val root = JSONObject()
        val paramV2 = JSONObject()

        // ── Notification attributes ──
        paramV2.put("protocol", 1)
        paramV2.put("business", "lyric_display")
        paramV2.put("enableFloat", false)
        paramV2.put("updatable", true)
        paramV2.put("islandFirstFloat", false)

        // ── 息屏 AOD ──
        paramV2.put("aodTitle", lyric.take(20).ifEmpty { "♪" })

        // ── 展开态: chatInfo (IM图文组件) ──
        val chatInfo = JSONObject()
        chatInfo.put("picProfile", "miui.focus.pic_avatar")  // 头像图: 使用专辑图
        chatInfo.put("title", lyric.ifEmpty { "♪" })         // 主要文本: 歌词
        chatInfo.put("content", subText)                      // 次要文本: 歌名 - 歌手
        paramV2.put("chatInfo", chatInfo)

        // ── 展开态: picInfo (识别图形组件1) ──
        val picInfo = JSONObject()
        picInfo.put("type", 1)  // 1: Static image
        picInfo.put("pic", "miui.focus.pic_app") // Source app icon
        paramV2.put("picInfo", picInfo)
        // ── 进度组件2 (Expanded State) ──
        val progressInfo = JSONObject()
        progressInfo.put("progress", progressPercent)
        progressInfo.put("colorProgress", "#757575")
        progressInfo.put("colorProgressEnd", "#757575")
        paramV2.put("progressInfo", progressInfo)

        // ── 岛数据 (param_island) ──
        // 摘要态模版2: 图文组件1 (A区) + 文本组件 (B区)
        val paramIsland = JSONObject()
        paramIsland.put("islandProperty", 1)

        val bigIslandArea = JSONObject()

        // A区: 图文组件1 — album art icon + song title
        // NOTE: Standard Template 1 only supports picInfo, so we use it for maximum visibility
        val imageTextInfoLeft = JSONObject()
        imageTextInfoLeft.put("type", 1)
        
        val picInfoA = JSONObject()
        picInfoA.put("type", 1)
        picInfoA.put("pic", "miui.focus.pic_island")
        imageTextInfoLeft.put("picInfo", picInfoA)
        
        // A区 text: song title
        val textInfoA = JSONObject()
        textInfoA.put("title", title.ifEmpty { "♪" })
        textInfoA.put("showHighlightColor", false)
        imageTextInfoLeft.put("textInfo", textInfoA)
        bigIslandArea.put("imageTextInfoLeft", imageTextInfoLeft)

        // B区: 文本组件 — 歌词作为正文大字
        val textInfo = JSONObject()
        textInfo.put("title", lyric.ifEmpty { "♪" })  // 正文大字: 歌词
        textInfo.put("showHighlightColor", false)
        textInfo.put("narrowFont", false)
        bigIslandArea.put("textInfo", textInfo)

        paramIsland.put("bigIslandArea", bigIslandArea)

        // 小岛: album art thumbnail with progress ring (using miui.land key)
        val smallIslandArea = JSONObject()
        val combinePicInfoSmall = JSONObject()
        val picInfoSmall = JSONObject()
        picInfoSmall.put("type", 1)
        picInfoSmall.put("pic", "miui.land.pic_island")
        combinePicInfoSmall.put("picInfo", picInfoSmall)
        
        val ringInfoSmall = JSONObject()
        ringInfoSmall.put("progress", progressPercent)
        ringInfoSmall.put("colorReach", "#757575")
        ringInfoSmall.put("colorUnReach", "#333333")
        combinePicInfoSmall.put("progressInfo", ringInfoSmall)
        
        smallIslandArea.put("combinePicInfo", combinePicInfoSmall)
        paramIsland.put("smallIslandArea", smallIslandArea)

        paramV2.put("param_island", paramIsland)

        root.put("param_v2", paramV2)
        return root.toString()
    }

    private fun scaleBitmap(src: Bitmap, targetSize: Int): Bitmap {
        if (src.width == targetSize && src.height == targetSize) return src
        return Bitmap.createScaledBitmap(src, targetSize, targetSize, true)
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
            // Option B: Open Media Controls (Transparent Activity)
            val intent = Intent(context, MediaControlActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            // Option A: Default (Open App)
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
        private const val TAG = "SuperIsland"
        private const val CHANNEL_ID = "lyric_capsule_channel"  // Shared with LyricCapsuleHandler
        private const val NOTIFICATION_ID = 1001  // Shared with LyricCapsuleHandler
    }
}
