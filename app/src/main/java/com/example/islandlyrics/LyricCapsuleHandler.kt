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
    
    // Content tracking to prevent flicker
    private var lastNotifiedLyric = ""
    private var lastNotifiedProgress = -1

    // Scroll state machine
    private enum class ScrollState {
        INITIAL_PAUSE,  // Show beginning, let user read
        SCROLLING,      // Active scrolling
        FINAL_PAUSE,    // Show ending before next lyric
        DONE
    }
    
    private var scrollState = ScrollState.INITIAL_PAUSE
    private var initialPauseStartTime: Long = 0
    
    // Visual weight-based scrolling (CJK=2, Western=1)
    private var scrollOffset = 0  // Current scroll position in visual weight units
    private var lastLyricText = ""
    private val maxDisplayWeight = 18  // Visual capacity: ~11 CJK or ~22 Western chars
    private val compensationThreshold = 8  // Stop scrolling if remainder < this (keeps capsule stable)
    
    // Timing constants
    private val initialPauseDuration = 1000L  // 1s to read beginning
    private val finalPauseDuration = 500L     // 0.5s before next lyric
    private val baseFocusDelay = 500L         // Per-shift eye refocus time
    private val staticTimeReserve = 1500L     // Initial + Final pause reserve

    // Adaptive scroll speed tracking
    private var lastLyricChangeTime: Long = 0
    private var lastLyricLength: Int = 0
    private val lyricDurations = mutableListOf<Long>()
    private val maxHistory = 5
    private var adaptiveDelay: Long = SCROLL_STEP_DELAY
    private val minCharDuration = 50L
    private val minScrollDelay = 500L
    private val maxScrollDelay = 5000L

    private val visualizerLoop = object : Runnable {
        override fun run() {
            if (!isRunning) return

            try {
                // Update the visual state
                updateNotification()

                // Optimize: If scrolling is DONE, wait longer or pause until next lyric update triggers it
                if (scrollState == ScrollState.DONE) {
                    // Slow down loop significantly if nothing to animate, just checking for progress updates
                    mainHandler.postDelayed(this, 1000) 
                } else {
                    // Active scrolling - use adaptive delay
                    mainHandler.postDelayed(this, adaptiveDelay)
                }
            } catch (t: Throwable) {
                LogManager.getInstance().e(context, TAG, "CRASH in visualizer loop: $t")
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
        
        // Reset adaptive scroll history for new song
        lastLyricChangeTime = 0
        lastLyricLength = 0
        synchronized(lyricDurations) {
            lyricDurations.clear()
        }
        adaptiveDelay = SCROLL_STEP_DELAY
        
        // Reset scroll state machine
        scrollState = ScrollState.INITIAL_PAUSE
        initialPauseStartTime = System.currentTimeMillis() // Start pause immediately
        scrollOffset = 0
        lastLyricText = "" // Force reset in next updateNotification
        
        mainHandler.post(visualizerLoop)
    }

    fun stop() {
        isRunning = false
        mainHandler.removeCallbacks(visualizerLoop)
        manager?.cancel(1001)
    }

    fun isRunning() = isRunning

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_live_lyrics),
            NotificationManager.IMPORTANCE_HIGH // Critical: IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager?.createNotificationChannel(channel)
    }

    fun updateLyricImmediate(lyric: String, @Suppress("UNUSED_PARAMETER") app: String) {
        // Record timing for adaptive scroll
        recordLyricChange(lyric)
        
        // Force immediate update and restart scroll loop
        lastUpdateTime = 0
        // Important: Update checks happen inside updateNotification
        
        // Restart runnable loop with adaptive delay
        if (isRunning) {
            mainHandler.removeCallbacks(visualizerLoop)
            mainHandler.post(visualizerLoop)
        }
    }

    // EXPOSED METHOD for LyricService to request a refresh/repost
    fun forceUpdateNotification() {
        // Reset last notified state to force a repost
        lastNotifiedLyric = "" 
        lastNotifiedProgress = -1
        // FIX: Reset throttler to ensure immediate update for service start
        lastUpdateTime = 0 
        updateNotification()
    }
    
    private fun recordLyricChange(newLyric: String) {
        val now = System.currentTimeMillis()
        
        // Skip first lyric (no previous timing)
        if (lastLyricChangeTime == 0L) {
            lastLyricChangeTime = now
            lastLyricLength = newLyric.length
            return
        }
        
        val duration = now - lastLyricChangeTime
        val avgCharDuration = if (lastLyricLength > 0) duration / lastLyricLength else 0
        
        // Filter noise: ignore if too fast (< 50ms per char)
        if (avgCharDuration < minCharDuration) {
            LogManager.getInstance().d(context, TAG, "Ignoring fast update: ${avgCharDuration}ms/char")
            return
        }
        
        // Filter pauses: ignore if too slow (> 30s total)
        if (duration > 30000) {
            LogManager.getInstance().d(context, TAG, "Ignoring long pause: ${duration}ms")
            lastLyricChangeTime = now
            lastLyricLength = newLyric.length
            return
        }
        
        // Add to history (sliding window)
        synchronized(lyricDurations) {
            lyricDurations.add(duration)
            if (lyricDurations.size > maxHistory) {
                lyricDurations.removeAt(0)
            }
        }
        
        // Update state
        lastLyricChangeTime = now
        lastLyricLength = newLyric.length
        
        // Recalculate adaptive delay
        calculateAdaptiveDelay()
    }
    
    private fun calculateAdaptiveDelay() {
        val avgDuration = synchronized(lyricDurations) {
            if (lyricDurations.isEmpty()) {
                adaptiveDelay = SCROLL_STEP_DELAY
                return
            }
            lyricDurations.average().toLong()
        }
        
        // Calculate total visual weight of recent lyric
        val avgLyricWeight = calculateWeight(lastLyricText)
        
        if (avgLyricWeight == 0 || avgDuration < staticTimeReserve) {
            adaptiveDelay = SCROLL_STEP_DELAY
            return
        }
        
        // Estimate number of scroll steps needed
        val estimatedSteps = maxOf(1, (avgLyricWeight / 5))  // ~5 weight per shift
        
        // T_per_unit = (T_total - T_static - N*T_base) / L_total
        val availableTime = avgDuration - staticTimeReserve - (estimatedSteps * baseFocusDelay)
        val timePerUnit = if (availableTime > 0 && avgLyricWeight > 0) {
            availableTime / avgLyricWeight
        } else {
            100L  // Fallback
        }
        
        // T_wait = T_base + (T_per_unit × W_shift)
        // Assume average shift weight ~5 (2-3 CJK or 3-4 Western)
        val avgShiftWeight = 5
        val calculatedDelay = baseFocusDelay + (timePerUnit * avgShiftWeight)
        
        // Clamp to reasonable range
        adaptiveDelay = calculatedDelay.coerceIn(minScrollDelay, maxScrollDelay)
        
        LogManager.getInstance().d(context, TAG, "Adaptive scroll: ${adaptiveDelay}ms (avgWeight: $avgLyricWeight, timePerUnit: ${timePerUnit}ms)")
    }
    
    // Calculate visual weight of a character (CJK=2, Western=1)
    private fun charWeight(c: Char): Int {
        return when (Character.UnicodeBlock.of(c)) {
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.HANGUL_SYLLABLES -> 2  // CJK characters
            else -> 1  // Western, digits, symbols
        }
    }
    
    // Calculate total visual weight of a string
    private fun calculateWeight(text: String): Int {
        return text.sumOf { charWeight(it) }
    }
    
    // Extract substring by visual weight (not character count)
    private fun extractByWeight(text: String, startWeight: Int, maxWeight: Int): String {
        var currentWeight = 0
        var startIndex = 0
        var endIndex = 0
        
        // Find start position
        for (i in text.indices) {
            if (currentWeight >= startWeight) {
                startIndex = i
                break
            }
            currentWeight += charWeight(text[i])
        }
        
        // Find end position
        currentWeight = 0
        for (i in startIndex until text.length) {
            currentWeight += charWeight(text[i])
            if (currentWeight > maxWeight) {
                break
            }
            endIndex = i + 1
        }
        
        // Fix: Ensure we don't return partial string if extracted length is 0 but start index is valid
        // Actually the logic seems fine, if start reached but maxWeight is 0, it returns empty.
        
        return if (endIndex > startIndex) text.substring(startIndex, endIndex) else ""
    }
    
    // Calculate smart shift weight (CJK: 2-3 chars=4-6 weight, Western: 3-4 chars=3-4 weight)
    private fun calculateSmartShiftWeight(text: String, currentOffset: Int): Int {
        // Extract segment starting from currentOffset
        val segment = extractByWeight(text, currentOffset, 10)  // Look ahead 10 weight
        if (segment.isEmpty()) return 4  // Default
        
        // Detect if primarily CJK or Western
        val cjkCount = segment.count { charWeight(it) == 2 }
        val totalChars = segment.length
        
        val isCJK = cjkCount > totalChars / 2
        
        return if (isCJK) {
            // CJK: shift 2-3 chars (4-6 weight), prefer 2 chars
            if (segment.length >= 2) {
                charWeight(segment[0]) + charWeight(segment[1])  // 2 chars
            } else {
                4  // Fallback
            }
        } else {
            // Western: shift 3-4 chars (3-4 weight)
            // Try to find word boundary (space)
            val spaceIndex = segment.indexOf(' ', 2)  // Look for space after 2 chars
            if (spaceIndex in 2..4) {
                // Shift to space
                calculateWeight(segment.take(spaceIndex + 1))
            } else if (segment.length >= 3) {
                // Shift 3 chars
                calculateWeight(segment.take(3))
            } else {
                3  // Fallback
            }
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
        
        // Scrolling Marquee Logic with Visual Weight & Compensation Algorithm
        var displayLyric: String
        val totalWeight = calculateWeight(currentLyric)
        
        // Reset scroll offset if lyric changed
        if (currentLyric != lastLyricText) {
            lastLyricText = currentLyric
            scrollOffset = 0
            scrollState = ScrollState.INITIAL_PAUSE
            initialPauseStartTime = System.currentTimeMillis()
        }
        
        // Short lyric: no scrolling needed
        if (totalWeight <= maxDisplayWeight) {
            displayLyric = currentLyric
            scrollState = ScrollState.DONE // Mark done immediately for short lyrics
        } else {
            // State machine for scroll timing
            when (scrollState) {
                ScrollState.INITIAL_PAUSE -> {
                    val pauseElapsed = System.currentTimeMillis() - initialPauseStartTime
                    if (pauseElapsed >= initialPauseDuration) {
                        scrollState = ScrollState.SCROLLING
                    }
                    // Show beginning of lyric
                    displayLyric = extractByWeight(currentLyric, 0, maxDisplayWeight)
                }
                
                ScrollState.SCROLLING -> {
                    // Calculate remaining content
                    val remainingWeight = totalWeight - scrollOffset
                    
                    // COMPENSATION ALGORITHM: Stop scrolling if remainder is small to keep capsule stable
                    if (remainingWeight <= compensationThreshold) {
                        // Show all remaining content (even if > maxDisplayWeight)
                        scrollState = ScrollState.FINAL_PAUSE
                        initialPauseStartTime = System.currentTimeMillis()
                        displayLyric = extractByWeight(currentLyric, scrollOffset, remainingWeight)
                    } else if (remainingWeight <= maxDisplayWeight) {
                        // Last full segment: switch to FINAL_PAUSE
                        scrollState = ScrollState.FINAL_PAUSE
                        initialPauseStartTime = System.currentTimeMillis()
                        displayLyric = extractByWeight(currentLyric, scrollOffset, maxDisplayWeight)
                    } else {
                        // Active scrolling
                        displayLyric = extractByWeight(currentLyric, scrollOffset, maxDisplayWeight)
                        
                        // Increment scroll offset by smart step (2-3 CJK or 3-4 Western)
                        scrollOffset += calculateSmartShiftWeight(currentLyric, scrollOffset)
                    }
                }
                
                ScrollState.FINAL_PAUSE -> {
                    // Show final segment (may be > maxDisplayWeight due to compensation)
                    val remainingWeight = totalWeight - scrollOffset
                    displayLyric = extractByWeight(currentLyric, scrollOffset, maxOf(remainingWeight, maxDisplayWeight))
                    
                    val pauseElapsed = System.currentTimeMillis() - initialPauseStartTime
                    if (pauseElapsed >= finalPauseDuration) {
                        scrollState = ScrollState.DONE
                    }
                }
                
                ScrollState.DONE -> {
                    // Keep showing final segment
                    val remainingWeight = totalWeight - scrollOffset
                    displayLyric = extractByWeight(currentLyric, scrollOffset, maxOf(remainingWeight, maxDisplayWeight))
                }
            }
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

        try {
            val notification = buildNotification()
            service.startForeground(1001, notification)
            service.inspectNotification(notification, manager!!)
        } catch (e: Exception) {
            LogManager.getInstance().e(context, TAG, "Update Failed: $e")
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
        
        // Use pre-calculated displayLyric from updateNotification() (weight-based scrolling)
        // This ensures consistent behavior with the advanced scrolling logic
        val displayLyric = lastNotifiedLyric.ifEmpty { 
            // Fallback: if no calculated lyric yet, use visual weight extraction
            if (calculateWeight(currentLyric) <= maxDisplayWeight) {
                currentLyric
            } else {
                extractByWeight(currentLyric, 0, maxDisplayWeight)
            }
        }
        
        // CRITICAL: Island displays setShortCriticalText(), not title!
        // So map: shortText = lyrics (scrolling), title = app name
        val title = sourceApp  // Title = app name (for notification drawer)
        val shortText = displayLyric  // Short text = scrolling lyrics for island

        // Set text fields
        builder.setContentTitle(title)
        builder.setContentText(currentLyric)  // Full lyrics in notification body
        builder.setSubText(sourceApp)  // App name as subtext
        // LogManager.getInstance().d(context, TAG, "Title: $title, Short: $shortText")

        // 2. ProgressStyle - SIMPLIFIED MUSIC-ONLY APPROACH
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                // Get real-time music progress
                val progressInfo = LyricRepository.getInstance().liveProgress.value
                
                if (progressInfo != null && progressInfo.duration > 0) {
                    // REAL MUSIC PROGRESS: Use single 100-unit segment
                    // LogManager.getInstance().d(context, TAG, "Building progress: pos=${progressInfo.position}ms, dur=${progressInfo.duration}ms")
                    
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
                    
                    builder.setStyle(progressStyle)
                } else {
                    // NO DATA: Use indeterminate progress
                    
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

            } catch (e: Exception) {
                LogManager.getInstance().e(context, TAG, "ProgressStyle failed: $e")
            }
        }

        return builder.build()
    }

    companion object {
        private const val TAG = "LyricCapsule"
        private const val CHANNEL_ID = "lyric_capsule_channel"
        private const val SCROLL_STEP_DELAY = 1800L  // Slower scroll: 1.8s per update (was 1s)

        // Colors for progress bar
        private const val COLOR_PRIMARY = 0xFF6750A4.toInt()   // Material Purple
        private const val COLOR_TERTIARY = 0xFF7D5260.toInt() // Material Tertiary
    }
}
