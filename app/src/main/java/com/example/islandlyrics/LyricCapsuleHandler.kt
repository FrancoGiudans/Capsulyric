package com.example.islandlyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * LyricCapsuleHandler
 * Manages live lyric notifications displayed in the Dynamic Island (Promoted Ongoing notifications)
 * on Android 16+ using androidx.core NotificationCompat with ProgressStyle.
 */
class LyricCapsuleHandler(
    private val context: Context,
    private val service: LyricService
) {

    private val manager: NotificationManager? = context.getSystemService(NotificationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var isRunning = false
    private var lastUpdateTime = 0L
    
    // Simulation State
    private var currentState = 0 // 0=Resolving, 1=Preparing, 2=Analysing, 3=Installing, 4=Success
    private var installProgress = 0f // 0.0 to 1.0 for Installing phase

    // State tracking fields
    private var lastState = -1
    private var lastNotifiedState = -1
    private var installStartTime = 0L
    
    // Content tracking to prevent flicker
    private var lastNotifiedLyric = ""
    private var lastNotifiedProgress = -1

    // Scrolling marquee state for long lyrics
    private var scrollOffset = 0
    private var lastLyricText = ""
    private val SCROLL_STEP = 3  // Jump 3 chars per step (User req: replace ~4 chars, keep rest)
    private val MAX_DISPLAY_LENGTH = 12  // Increased for English lyrics (was 7)

    private val simulationRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            try {
                updateSimulationState()
                updateNotification()

                // Loop for installation progress
                if (currentState == 3 && installProgress < 1.0f) {
                    mainHandler.postDelayed(this, 200)
                } else if (currentState < 4) {
                    mainHandler.postDelayed(this, SIMULATION_STEP_DELAY)
                } else {
                    mainHandler.postDelayed({ stop() }, 5000)
                }
            } catch (t: Throwable) {
                LogManager.getInstance().e(context, TAG, "CRASH in update loop: $t")
                stop()
            }
        }
    }

    init {
        createChannel()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        currentState = 3
        installProgress = 0f
        installStartTime = System.currentTimeMillis()
        mainHandler.post(simulationRunnable)
    }

    fun stop() {
        isRunning = false
        mainHandler.removeCallbacks(simulationRunnable)
        manager?.cancel(1001)
    }

    fun isRunning() = isRunning

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.installer_live_channel_name),
                NotificationManager.IMPORTANCE_HIGH // Critical: IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateSimulationState() {
        // NO STATE MACHINE - currentState permanently at 3
        // Notification updates happen in simulationRunnable line 50
    }

    fun updateLyricImmediate(lyric: String, app: String) {
        // Force immediate update and restart scroll loop
        lastUpdateTime = 0
        updateNotification()
        
        // Restart runnable loop to ensure continuous scrolling
        if (isRunning) {
            mainHandler.removeCallbacks(simulationRunnable)
            mainHandler.postDelayed(simulationRunnable, SIMULATION_STEP_DELAY)
        }
    }

    private fun updateNotification() {
        // Throttling: 50ms limit (reduced from 200ms for faster response)
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 50) return 
        lastUpdateTime = now

        // Calculate display lyric FIRST (with scroll)
        val lyricInfo = LyricRepository.getInstance().liveLyric.value
        val currentLyric = lyricInfo?.lyric ?: ""
        
        // Reset scroll offset if lyric changed
        if (currentLyric != lastLyricText) {
            scrollOffset = 0
            lastLyricText = currentLyric
        }
        
        // Calculate scrolled display text
        val displayLyric = if (currentLyric.length > MAX_DISPLAY_LENGTH) {
            val paddedLyric = currentLyric + "   " + currentLyric
            val startIdx = scrollOffset % currentLyric.length
            val endIdx = (startIdx + MAX_DISPLAY_LENGTH).coerceAtMost(paddedLyric.length)
            val window = paddedLyric.substring(startIdx, endIdx)
            scrollOffset += SCROLL_STEP
            window
        } else {
            currentLyric
        }
        
        // Get progress
        val progressInfo = LyricRepository.getInstance().liveProgress.value
        val currentProgress = if (progressInfo != null && progressInfo.duration > 0) {
            ((progressInfo.position.toFloat() / progressInfo.duration.toFloat()) * 100).toInt()
        } else -1
        
        // SKIP UPDATE if DISPLAYED content unchanged
        if (displayLyric == lastNotifiedLyric && currentProgress == lastNotifiedProgress) {
            return
        }
        
        // Content changed, update notification
        lastNotifiedLyric = displayLyric
        lastNotifiedProgress = currentProgress

        // Update whenever state changes OR when in lyrics mode (state 3)
        if (currentState != lastNotifiedState || currentState == 3) {
            lastNotifiedState = currentState

            try {
                val notification = buildNotification()
                service.startForeground(1001, notification)
                service.inspectNotification(notification, manager!!)
            } catch (e: Exception) {
                LogManager.getInstance().e(context, TAG, "Update Failed: $e")
            }
        }
    }

    private fun buildNotification(): Notification {
        // CRITICAL: Use NotificationCompat.Builder from androidx.core 1.17.0+
        // This provides native .setRequestPromotedOngoing() without reflection!
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setRequestPromotedOngoing(true)  // Native AndroidX method - no reflection needed!

        // 1. Get Live Lyrics from Repository
        val lyricInfo = LyricRepository.getInstance().liveLyric.value
        val currentLyric = lyricInfo?.lyric ?: "Waiting for lyrics..."
        val sourceApp = lyricInfo?.sourceApp ?: "Island Lyrics"
        
        // Scrolling Marquee Logic for long lyrics
        var displayLyric = currentLyric
        
        // Reset scroll offset if lyric changed
        if (currentLyric != lastLyricText) {
            lastLyricText = currentLyric
            scrollOffset = 0
        }
        
        // Apply scrolling for long lyrics (state 3 only)
        if (currentState == 3 && currentLyric.length > MAX_DISPLAY_LENGTH) {
            // Linear scrolling (no loop)
            val totalLength = currentLyric.length
            
            // Calculate window
            var start = scrollOffset
            var end = start + MAX_DISPLAY_LENGTH
            
            // Check boundaries
            if (end >= totalLength) {
                // Reached end - lock to last 7 chars
                end = totalLength
                start = (totalLength - MAX_DISPLAY_LENGTH).coerceAtLeast(0)
                // Do not increment scrollOffset anymore
            } else {
                // Continue scrolling
                scrollOffset += SCROLL_STEP
            }
            
            displayLyric = currentLyric.substring(start, end)
        } else {
            displayLyric = currentLyric.take(MAX_DISPLAY_LENGTH)
        }
        
        // CRITICAL: Island displays setShortCriticalText(), not title!
        // So map: shortText = lyrics (scrolling), title = app name
        var title = sourceApp  // Title = app name (for notification drawer)
        var shortText = displayLyric  // Short text = scrolling lyrics for island

        // Override with installation states only for initial setup phases
        when (currentState) {
            0 -> {
                title = context.getString(R.string.installer_resolving)
                shortText = context.getString(R.string.installer_live_channel_short_text_resolving)
            }
            1 -> {
                title = context.getString(R.string.installer_preparing)
                shortText = context.getString(R.string.installer_live_channel_short_text_preparing)
                builder.addAction(0, context.getString(R.string.cancel), null)
            }
            2 -> {
                title = context.getString(R.string.installer_analysing)
                shortText = context.getString(R.string.installer_live_channel_short_text_analysing)
            }
            3 -> {
                // State 3 = Show Live Lyrics (use lyricInfo from above)
                // title and shortText already set from lyricInfo
            }
            4 -> {
                title = context.getString(R.string.installer_install_success)
                shortText = context.getString(R.string.installer_live_channel_short_text_success)
                builder.setOngoing(false).setOnlyAlertOnce(false)
            }
        }

        // Set text fields
        builder.setContentTitle(title)
        builder.setContentText(currentLyric)  // Full lyrics in notification body
        builder.setSubText(sourceApp)  // App name as subtext
        LogManager.getInstance().d(context, TAG, "State: 3 (lyrics), Title: $title, Short: $shortText")

        // 2. ProgressStyle - SIMPLIFIED MUSIC-ONLY APPROACH
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                // Get real-time music progress
                val progressInfo = LyricRepository.getInstance().liveProgress.value
                
                if (progressInfo != null && progressInfo.duration > 0) {
                    // REAL MUSIC PROGRESS: Use single 100-unit segment
                    LogManager.getInstance().d(context, TAG, "Building progress: pos=${progressInfo.position}ms, dur=${progressInfo.duration}ms")
                    
                    // Create single segment with length = 100
                    val segment = NotificationCompat.ProgressStyle.Segment(100)
                    segment.setColor(COLOR_PRIMARY)
                    
                    val segments = ArrayList<NotificationCompat.ProgressStyle.Segment>()
                    segments.add(segment)
                    
                    // Calculate progress: (position / duration) * 100
                    val percentage = (progressInfo.position.toFloat() / progressInfo.duration.toFloat())
                    val progressValue = (percentage * 100).toInt().coerceIn(0, 100)
                    
                    val progressStyle = NotificationCompat.ProgressStyle()
                        .setProgressSegments(segments)
                        .setStyledByProgress(true)
                        .setProgress(progressValue)
                    
                    LogManager.getInstance().d(context, TAG, "✓ Real music progress: ${progressValue}/100 (${(percentage * 100).toInt()}%)")
                    builder.setStyle(progressStyle)
                } else {
                    // NO DATA: Use indeterminate progress
                    LogManager.getInstance().d(context, TAG, "⚠ No progress data available (pos=${progressInfo?.position}, dur=${progressInfo?.duration})")
                    
                    // Single gray segment with indeterminate state
                    val segment = NotificationCompat.ProgressStyle.Segment(100)
                    segment.setColor(COLOR_TERTIARY)
                    val segments = ArrayList<NotificationCompat.ProgressStyle.Segment>()
                    segments.add(segment)
                    
                    val progressStyle = NotificationCompat.ProgressStyle()
                        .setProgressSegments(segments)
                        .setProgressIndeterminate(true)
                    
                    builder.setStyle(progressStyle)
                }

                // Set ShortCriticalText (AndroidX native method)
                builder.setShortCriticalText(shortText)
                LogManager.getInstance().d(context, TAG, "Set ShortCriticalText: $shortText")

            } catch (e: Exception) {
                LogManager.getInstance().e(context, TAG, "ProgressStyle failed: $e")
            }
        }

        return builder.build()
    }

    companion object {
        private const val TAG = "LyricCapsule"
        private const val CHANNEL_ID = "lyric_capsule_channel"
        private const val SIMULATION_STEP_DELAY = 1800L  // Slower scroll: 1.8s per update (was 1s)

        // Colors for progress bar
        private const val COLOR_PRIMARY = 0xFF6750A4.toInt()   // Material Purple
        private const val COLOR_TERTIARY = 0xFF7D5260.toInt() // Material Tertiary
    }
}
