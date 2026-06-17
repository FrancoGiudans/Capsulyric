package com.example.islandlyrics.ui.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.islandlyrics.R
import com.example.islandlyrics.core.logging.LogManager
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.service.LyricService

class ColorOsFluidCloudHandler(
    private val context: Context,
    private val service: LyricService
) {
    private val manager: NotificationManager? = context.getSystemService(NotificationManager::class.java)
    private val bridge = ColorOsFluidCloudBridge(context)
    private val prefs by lazy { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            "super_island_lyric_mode",
            "super_island_full_lyric_show_left_cover",
            "super_island_text_color_enabled",
            "progress_bar_color_enabled",
            SuperIslandColorSource.PREF_KEY,
            SuperIslandColorSource.CUSTOM_COLOR_PREF_KEY -> loadRenderConfig(p)
        }
    }

    var isRunning = false
        private set

    private var renderConfig = ColorOsFluidCloudBridge.RenderConfig()
    private var lastSignature = ""
    private var lastNotifyTime = 0L
    private var isFirstNotification = true
    private var loggedMissingSdk = false
    private var loggedUnsupported = false

    private val contentIntent: PendingIntent by lazy {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    init {
        createChannel()
        loadRenderConfig(prefs)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    fun start() {
        if (isRunning) return
        loadRenderConfig(prefs)
        isRunning = true
        lastSignature = ""
        lastNotifyTime = 0L
        isFirstNotification = true
        loggedMissingSdk = false
        loggedUnsupported = false
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        manager?.cancel(NOTIFICATION_ID)
    }

    fun render(state: UIState) {
        if (!isRunning) return

        val signature = listOf(
            state.displayLyric,
            state.fullLyric,
            state.title,
            state.artist,
            state.isPlaying,
            state.progressCurrent,
            state.albumArt?.hashCode() ?: 0,
            renderConfig
        ).joinToString("|")
        val now = android.os.SystemClock.elapsedRealtime()
        if (signature == lastSignature && now - lastNotifyTime < THROTTLE_MS) return

        lastSignature = signature
        lastNotifyTime = now

        if (!bridge.isSdkAvailable()) {
            if (!loggedMissingSdk) {
                LogManager.getInstance().w(
                    context,
                    TAG,
                    "SeedlingSupportSDK is not available; using standard notification fallback."
                )
                loggedMissingSdk = true
            }
        } else if (!bridge.isFluidCloudSupported()) {
            if (!loggedUnsupported) {
                LogManager.getInstance().w(
                    context,
                    TAG,
                    "This ColorOS build does not report Fluid Cloud support; using fallback."
                )
                loggedUnsupported = true
            }
        } else {
            bridge.updateExistingCards(state, renderConfig)
        }

        postFallbackNotification(state)
    }

    private fun postFallbackNotification(state: UIState) {
        val accentColor = SuperIslandColorSource.resolveColor(
            source = renderConfig.colorSource,
            albumColor = state.albumColor,
            customColor = renderConfig.customColor
        )
        val primaryText = resolvePrimaryLyricText(state)
        val compactText = resolveCompactLyricText(state)
        val displayText = if (renderConfig.lyricMode == "full") primaryText else compactText
        val title = state.title.ifBlank { context.getString(R.string.channel_live_lyrics) }
        val text = displayText.ifBlank {
            state.artist.ifBlank { context.getString(R.string.channel_live_lyrics) }
        }
        val shortText = trimCapsuleText(text)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(context.getString(R.string.capsule_mode_fluid_cloud))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(accentColor)
            .setColorized(renderConfig.textColorEnabled)

        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(shortText)
        }

        state.albumArt?.takeUnless { it.isRecycled }?.let { albumArt ->
            builder.setLargeIcon(scaleBitmap(albumArt, 256))
        }

        if (state.progressCurrent >= 0) {
            builder.setProgress(100, state.progressCurrent.coerceIn(0, 100), false)
        }

        val notification = builder.build()
        if (isFirstNotification && !service.isForegroundSlotPrimed()) {
            service.startForegroundTracked(NOTIFICATION_ID, notification, "fluidCloud.first")
        } else {
            manager?.notify(NOTIFICATION_ID, notification)
        }
        isFirstNotification = false
    }

    private fun loadRenderConfig(prefs: SharedPreferences) {
        renderConfig = ColorOsFluidCloudBridge.RenderConfig(
            lyricMode = sanitizeLyricMode(prefs.getString("super_island_lyric_mode", "standard")),
            showLeftCoverInFullLyric = prefs.getBoolean("super_island_full_lyric_show_left_cover", true),
            textColorEnabled = prefs.getBoolean("super_island_text_color_enabled", false),
            progressColorEnabled = prefs.getBoolean("progress_bar_color_enabled", false),
            colorSource = SuperIslandColorSource.read(prefs),
            customColor = SuperIslandColorSource.readCustomColor(prefs)
        )
    }

    private fun sanitizeLyricMode(mode: String?): String = if (mode == "full") "full" else "standard"

    private fun resolvePrimaryLyricText(state: UIState): String {
        val candidates = if (state.isTimingGapPlaceholder && !state.preferMetadataLayout) {
            sequenceOf(state.fullLyric, state.displayLyric)
        } else {
            sequenceOf(state.fullLyric, state.displayLyric, state.title)
        }
        return candidates.firstOrNull { !isLyricPlaceholder(it) } ?: "♪"
    }

    private fun resolveCompactLyricText(state: UIState): String {
        val candidates = if (state.isTimingGapPlaceholder && !state.preferMetadataLayout) {
            sequenceOf(state.displayLyric)
        } else {
            sequenceOf(state.displayLyric, state.title)
        }
        return candidates.firstOrNull { !isLyricPlaceholder(it) } ?: "♪"
    }

    private fun isLyricPlaceholder(text: String): Boolean {
        return text.isBlank() || text.trim() == "♪"
    }

    private fun trimCapsuleText(text: String): String {
        val clean = text.trim()
        if (SuperIslandLyricLayout.calculateWeight(clean) <= ColorOsFluidCloudBridge.CAPSULE_TEXT_WEIGHT_LIMIT) {
            return clean
        }
        return SuperIslandLyricLayout.takeByWeight(clean, ColorOsFluidCloudBridge.CAPSULE_TEXT_WEIGHT_LIMIT)
            .ifBlank { clean.take(ColorOsFluidCloudBridge.CAPSULE_TEXT_LIMIT) }
    }

    private fun scaleBitmap(src: Bitmap, targetSize: Int): Bitmap {
        if (src.isRecycled) return Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val size = minOf(src.width, src.height)
        val x = ((src.width - size) / 2).coerceAtLeast(0)
        val y = ((src.height - size) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(src, x, y, size, size)
        val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        if (cropped !== src && cropped !== scaled) cropped.recycle()
        return scaled
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

    companion object {
        private const val TAG = "ColorOsFluidCloudHandler"
        private const val CHANNEL_ID = "coloros_fluid_cloud_channel"
        private const val NOTIFICATION_ID = 1003
        private const val THROTTLE_MS = 1000L
    }
}
