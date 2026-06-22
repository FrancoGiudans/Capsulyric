package com.example.islandlyrics.ui.overlay.capsule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.logging.LogManager
import com.example.islandlyrics.runtime.service.LyricService
import com.example.islandlyrics.ui.overlay.capsule.config.LyricCapsulePreferencesCache
import com.example.islandlyrics.ui.overlay.capsule.intent.LyricCapsuleIntentFactory
import com.example.islandlyrics.ui.overlay.capsule.intent.LyricCapsuleIntents
import com.example.islandlyrics.ui.overlay.capsule.render.LyricCapsuleDynamicIconCache
import com.example.islandlyrics.ui.overlay.capsule.render.LyricCapsuleIconFrame
import com.example.islandlyrics.ui.overlay.capsule.render.LyricCapsuleNotificationBuilder
import com.example.islandlyrics.ui.overlay.model.UIState

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
    private var lastNotifiedFullLyric = ""
    private var lastNotifiedTitle = ""
    private var lastNotifiedProgress = -1
    private var lastNotifyTime = 0L
    private var isFirstNotification = true
    
    private val preferences = LyricCapsulePreferencesCache(
        context = context,
        onActionsChanged = { rebuildCachedIntents() },
        onDynamicIconStyleChanged = { dynamicIconCache.release() }
    )
    private val intentFactory = LyricCapsuleIntentFactory(context)
    private val dynamicIconCache = LyricCapsuleDynamicIconCache(context)
    private val notificationBuilder = LyricCapsuleNotificationBuilder(
        context = context,
        preferences = preferences,
        intentFactory = intentFactory,
        dynamicIconCache = dynamicIconCache
    )

    private var cachedIntents = LyricCapsuleIntents(
        contentIntent = null,
        pauseIntent = null,
        nextIntent = null,
        miplayIntent = null
    )

    private fun rebuildCachedIntents() {
        cachedIntents = intentFactory.create(preferences.clickStyle)
    }
    
    private fun unloadPreferences() {
        preferences.stop()
    }

    init {
        createChannel()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        preferences.start()
        rebuildCachedIntents()
        lastNotifiedLyric = ""
        lastNotifiedFullLyric = ""
        lastNotifiedTitle = ""
        lastNotifiedProgress = -1
        lastNotifyTime = 0L
        isFirstNotification = true
        dynamicIconCache.release()
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        unloadPreferences()
        manager?.cancel(1001)
        dynamicIconCache.release()
    }

    fun isRunning() = isRunning

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

    private var lastNotifiedIconText = ""

    private fun calculateDynamicIconFrame(title: String, artist: String): LyricCapsuleIconFrame {
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
        return LyricCapsuleIconFrame(cleanText)
    }

    fun render(state: UIState) {
        if (!isRunning) return

        val displayLyric = state.displayLyric
        val currentProgress = state.progressCurrent
        val iconFrame = calculateDynamicIconFrame(state.title, state.artist)
        val displayTitle = if (state.artist.isNotBlank()) "${state.title} - ${state.artist}" else state.title

        val lyricChanged = displayLyric != lastNotifiedLyric
        val fullLyricChanged = state.fullLyric != lastNotifiedFullLyric
        val titleChanged = displayTitle != lastNotifiedTitle
        val iconChanged = iconFrame.text != lastNotifiedIconText
        val progressChangedEnough = kotlin.math.abs(currentProgress - lastNotifiedProgress) >= PROGRESS_NOTIFY_STEP_PERCENT
        val now = android.os.SystemClock.elapsedRealtime()
        if (!isFirstNotification && !lyricChanged && !fullLyricChanged && !titleChanged && !iconChanged &&
            (!progressChangedEnough || now - lastNotifyTime < PROGRESS_NOTIFY_INTERVAL_MS)) {
            return
        }
        if (!isFirstNotification && (lyricChanged || fullLyricChanged || titleChanged || iconChanged) &&
            now - lastNotifyTime < CONTENT_NOTIFY_DEBOUNCE_MS) {
            return
        }
        
        lastNotifiedLyric = displayLyric
        lastNotifiedFullLyric = state.fullLyric
        lastNotifiedTitle = displayTitle
        lastNotifiedProgress = currentProgress
        lastNotifiedIconText = iconFrame.text
        lastNotifyTime = now

        try {
            val notification = notificationBuilder.build(
                displayLyric = displayLyric,
                fullLyric = state.fullLyric,
                title = displayTitle,
                sourceApp = state.mediaPackage,
                progressPercent = currentProgress,
                iconFrame = iconFrame,
                albumColor = state.albumColor,
                intents = cachedIntents
            )

            if (BuildConfig.DEBUG) {
                LogManager.getInstance().d(
                    context,
                    TAG,
                    "[NotifyTrace] send first=$isFirstNotification primed=${service.isForegroundSlotPrimed()} running=$isRunning title=$displayTitle lyric=${displayLyric.ifBlank { state.fullLyric.ifBlank { "<blank>" } }}"
                )
            }
            
            if (isFirstNotification && service.isForegroundSlotPrimed() && manager != null) {
                manager.notify(1001, notification)
                isFirstNotification = false
            } else if (isFirstNotification) {
                service.startForegroundTracked(1001, notification, "capsule.first")
                isFirstNotification = false
            } else {
                manager?.notify(1001, notification)
            }
        } catch (e: Exception) {
            LogManager.getInstance().e(context, TAG, "Update Failed: $e")
        }
    }

    companion object {
        private const val TAG = "LyricCapsule"
        private const val CHANNEL_ID = "lyric_capsule_channel"
        private const val PROGRESS_NOTIFY_STEP_PERCENT = 2
        private const val PROGRESS_NOTIFY_INTERVAL_MS = 2_000L
        private const val CONTENT_NOTIFY_DEBOUNCE_MS = 120L
    }
}
