package com.example.islandlyrics.ui.common

import android.app.Notification
import com.example.islandlyrics.R
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.core.logging.LogManager
import com.example.islandlyrics.service.LyricService
import com.example.islandlyrics.data.mediadata.TitleParser
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.feature.mediacontrol.MediaControlActivity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * LyricCapsuleHandler
 * Pure renderer for live lyric notifications displayed in the Dynamic Island
 * on Android 16+ using androidx.core NotificationCompat with ProgressStyle.
 */
class LyricCapsuleHandler(
    private val context: Context,
    private val service: LyricService
) {

    private val manager: NotificationManager? = context.getSystemService(NotificationManager::class.java)
    
    private var isRunning = false
    
    // Content tracking to prevent flicker
    private var lastNotifiedLyric = ""
    private var lastNotifiedProgress = -1
    private var isFirstNotification = true
    
    // Cached preferences
    private var cachedActionStyle = "disabled"
    private var cachedUseAlbumColor = false
    private var cachedUseDynamicIcon = false
    private var cachedIconStyle = "classic"
    private var cachedClickStyle = "default"
    private var cachedOneuiCapsuleColorEnabled = false
    
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "notification_actions_style" -> {
                cachedActionStyle = prefs.getString(key, "disabled") ?: "disabled"
                rebuildCachedIntents()
            }
            "progress_bar_color_enabled" -> cachedUseAlbumColor = prefs.getBoolean(key, false)
            "dynamic_icon_enabled" -> cachedUseDynamicIcon = prefs.getBoolean(key, false)
            "dynamic_icon_style" -> cachedIconStyle = prefs.getString(key, "classic") ?: "classic"
            "notification_click_style" -> {
                cachedClickStyle = prefs.getString(key, "default") ?: "default"
                rebuildCachedIntents()
            }
            "oneui_capsule_color_enabled" -> cachedOneuiCapsuleColorEnabled = prefs.getBoolean(key, false)
        }
    }
    
    private fun loadPreferences() {
        val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        cachedActionStyle = prefs.getString("notification_actions_style", "disabled") ?: "disabled"
        cachedUseAlbumColor = prefs.getBoolean("progress_bar_color_enabled", false)
        cachedUseDynamicIcon = prefs.getBoolean("dynamic_icon_enabled", false)
        cachedIconStyle = prefs.getString("dynamic_icon_style", "classic") ?: "classic"
        cachedClickStyle = prefs.getString("notification_click_style", "default") ?: "default"
        cachedOneuiCapsuleColorEnabled = prefs.getBoolean("oneui_capsule_color_enabled", false)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    private var cachedContentIntent: PendingIntent? = null
    private var cachedPauseIntent: PendingIntent? = null
    private var cachedNextIntent: PendingIntent? = null
    private var cachedMiplayIntent: PendingIntent? = null

    private fun rebuildCachedIntents() {
        cachedContentIntent = if (cachedClickStyle == "media_controls") {
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

        cachedPauseIntent = PendingIntent.getService(
            context, 1,
            Intent(context, LyricService::class.java).setAction("ACTION_MEDIA_PAUSE"),
            PendingIntent.FLAG_IMMUTABLE
        )
        cachedNextIntent = PendingIntent.getService(
            context, 2,
            Intent(context, LyricService::class.java).setAction("ACTION_MEDIA_NEXT"),
            PendingIntent.FLAG_IMMUTABLE
        )
        cachedMiplayIntent = PendingIntent.getActivity(
            context, 3,
            Intent().apply {
                component = android.content.ComponentName(
                    "miui.systemui.plugin",
                    "miui.systemui.miplay.MiPlayDetailActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun unloadPreferences() {
        context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefChangeListener)
    }

    init {
        createChannel()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        loadPreferences()
        rebuildCachedIntents()
        lastNotifiedLyric = ""
        isFirstNotification = true
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        unloadPreferences()
        manager?.cancel(1001)
    }

    fun isRunning() = isRunning

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

    // Helper Class for Icon State
    private data class IconFrame(val text: String, val fontSize: Float? = null)
    private var lastNotifiedIconText = ""
    private var cachedIconKey = ""
    private var cachedIconBitmap: Bitmap? = null

    private fun textToBitmap(text: String, forceFontSize: Float? = null): Bitmap? {
        try {
            val density = context.resources.displayMetrics.density
            val fontSize = (forceFontSize ?: 20f) * density
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.textSize = fontSize
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.LEFT
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                isSubpixelText = true
                isLinearText = true
            }
            val baseline = -paint.ascent()
            val width = (paint.measureText(text) + 10 * density).toInt() 
            val height = (baseline + paint.descent() + 5 * density).toInt()
            
            if (width <= 0 || height <= 0) return null

            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(image)
            canvas.drawText(text, 5 * density, baseline, paint)
            return image
        } catch (e: Exception) {
            LogManager.getInstance().e(context, TAG, "Failed to generate text bitmap: $e")
            return null
        }
    }

    private fun calculateDynamicIconFrame(title: String, artist: String): IconFrame {
        val baseText = "$title - $artist"
        val maxIconWeight = 17
        
        var currentWeight = 0
        var endIndex = 0
        for (i in baseText.indices) {
            val c = baseText[i]
            val w = if (c.code > 0x2e00) 2 else 1 // Simplified weight check for icon
            if (currentWeight + w > maxIconWeight) {
                break
            }
            currentWeight += w
            endIndex = i + 1
        }
        
        val displayText = baseText.substring(0, endIndex).trimEnd()
        val cleanText = displayText.removeSuffix(" -").removeSuffix(" - ")
        return IconFrame(cleanText)
    }

    fun render(state: UIState) {
        if (!isRunning) return

        val displayLyric = state.displayLyric
        val currentProgress = state.progressCurrent
        val iconFrame = calculateDynamicIconFrame(state.title, state.artist)

        if (displayLyric == lastNotifiedLyric && 
            currentProgress == lastNotifiedProgress &&
            iconFrame.text == lastNotifiedIconText) {
            return
        }
        
        lastNotifiedLyric = displayLyric
        lastNotifiedProgress = currentProgress
        lastNotifiedIconText = iconFrame.text

        try {
            val displayTitle = if (state.artist.isNotBlank()) "${state.title} - ${state.artist}" else state.title

            val notification = buildNotification(
                displayLyric, 
                state.fullLyric, 
                displayTitle,
                state.mediaPackage, 
                currentProgress, 
                iconFrame,
                state.albumColor
            )
            
            if (isFirstNotification) {
                service.startForeground(1001, notification)
                isFirstNotification = false
            } else {
                manager?.notify(1001, notification)
            }
        } catch (e: Exception) {
            LogManager.getInstance().e(context, TAG, "Update Failed: $e")
        }
    }

    private fun buildNotification(
        displayLyric: String,
        fullLyric: String,
        title: String,
        sourceApp: String,
        progressPercent: Int,
        iconFrame: IconFrame,
        albumColor: Int
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            
        builder.setContentIntent(cachedContentIntent)
        builder.setRequestPromotedOngoing(true)

        when (cachedActionStyle) {
            "media_controls" -> {
                builder.addAction(0, context.getString(R.string.action_pause), cachedPauseIntent)
                builder.addAction(0, context.getString(R.string.action_next), cachedNextIntent)
            }
            "miplay" -> {
                builder.addAction(0, context.getString(R.string.action_miplay), cachedMiplayIntent)
            }
        }

        val currentLyric = fullLyric.ifEmpty { "Waiting for lyrics..." }
        val shortText = displayLyric.ifEmpty { currentLyric }

        builder.setContentTitle(title)
        builder.setContentText(currentLyric)
        builder.setSubText(sourceApp)

        try {
            val barColor = if (cachedUseAlbumColor) albumColor else COLOR_PRIMARY
            val barColorIndeterminate = if (cachedUseAlbumColor) albumColor else COLOR_TERTIARY

            if (RomUtils.getRomType() == "OneUI") {
                builder.setColor(if (cachedOneuiCapsuleColorEnabled) albumColor else android.graphics.Color.BLACK)
            } else {
                builder.setColor(barColor)
            }

            if (progressPercent >= 0) {
                val segment = NotificationCompat.ProgressStyle.Segment(100)
                segment.setColor(barColor)
                
                val segments = ArrayList<NotificationCompat.ProgressStyle.Segment>()
                segments.add(segment)
                
                val progressValue = progressPercent.coerceIn(0, 100)
                
                val progressStyle = NotificationCompat.ProgressStyle()
                    .setProgressSegments(segments)
                    .setStyledByProgress(true)
                    .setProgress(progressValue)
                
                builder.setStyle(progressStyle)
            } else {
                val segment = NotificationCompat.ProgressStyle.Segment(100)
                segment.setColor(barColorIndeterminate)
                val segments = ArrayList<NotificationCompat.ProgressStyle.Segment>()
                segments.add(segment)
                
                val progressStyle = NotificationCompat.ProgressStyle()
                    .setProgressSegments(segments)
                    .setProgressIndeterminate(true)
                
                builder.setStyle(progressStyle)
            }

            builder.setShortCriticalText(shortText)

        } catch (e: Exception) {
            LogManager.getInstance().e(context, TAG, "ProgressStyle failed: $e")
        }

        return builder.build().apply {
            if (cachedUseDynamicIcon) {
                val bitmap = when (cachedIconStyle) {
                    "advanced" -> {
                        val metadata = LyricRepository.getInstance().liveMetadata.value
                        val albumArt = LyricRepository.getInstance().liveAlbumArt.value
                        
                        val realTitle = metadata?.title ?: ""
                        val realArtist = metadata?.artist ?: ""
                        
                        val cacheKey = "advanced|$realTitle|$realArtist|${albumArt?.hashCode()}"
                        if (cachedIconKey != cacheKey) {
                            val parsedTitle = TitleParser.parse(realTitle)
                            cachedIconBitmap = AdvancedIconRenderer.render(albumArt, parsedTitle, realArtist, context)
                            cachedIconKey = cacheKey
                        }
                        cachedIconBitmap
                    }
                    else -> {
                        val iconText = iconFrame.text 
                        val forceSize = iconFrame.fontSize
                        
                        val cacheKey = "classic|$iconText|$forceSize"
                        if (cachedIconKey != cacheKey) {
                            cachedIconBitmap = textToBitmap(iconText, forceSize)
                            cachedIconKey = cacheKey
                        }
                        cachedIconBitmap
                    }
                }
                
                bitmap?.let { bmp ->
                    try {
                        val icon = android.graphics.drawable.Icon.createWithBitmap(bmp)
                        val field = android.app.Notification::class.java.getDeclaredField("mSmallIcon")
                        field.isAccessible = true
                        field.set(this, icon)
                    } catch (e: Exception) {
                        LogManager.getInstance().e(context, TAG, "Failed to inject Dynamic Icon: $e")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "LyricCapsule"
        private const val CHANNEL_ID = "lyric_capsule_channel"
        const val SCROLL_STEP_DELAY = 1800L
        const val COLOR_PRIMARY = 0xFF757575.toInt()
        private const val COLOR_TERTIARY = 0xFFBDBDBD.toInt()
    }
}
