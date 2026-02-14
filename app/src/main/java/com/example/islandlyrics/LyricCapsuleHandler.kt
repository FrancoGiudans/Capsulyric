package com.example.islandlyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

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

    // Scrolling Mode Flags
    private var useSyllableScrolling = false
    private var useLrcScrolling = false
    private var currentParsedLines: List<OnlineLyricFetcher.LyricLine>? = null

    // Observer for parsed lyrics
    private val parsedLyricsObserver = androidx.lifecycle.Observer<LyricRepository.ParsedLyricsInfo?> { parsedInfo ->
        if (parsedInfo != null && parsedInfo.hasSyllable) {
            // Switch to syllable scrolling mode
            useSyllableScrolling = true
            useLrcScrolling = false
            currentParsedLines = parsedInfo.lines
            AppLogger.getInstance().log(TAG, "✨ Switched to SYLLABLE scrolling mode (${parsedInfo.lines.size} lines)")
        } else if (parsedInfo != null && parsedInfo.lines.isNotEmpty()) {
            // Switch to LRC time-based scrolling
            useSyllableScrolling = false
            useLrcScrolling = true
            currentParsedLines = parsedInfo.lines
            AppLogger.getInstance().log(TAG, "⏱️ Switched to LRC scrolling mode (${parsedInfo.lines.size} lines)")
        } else {
            // Fallback to visual weight scrolling
            useSyllableScrolling = false
            useLrcScrolling = false
            currentParsedLines = null
            AppLogger.getInstance().log(TAG, "📏 Switched to WEIGHT-BASED scrolling mode")
        }
    }

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
                    // Check if we are in TIMED mode (fast refresh needed)
                    val currentLine = LyricRepository.getInstance().liveCurrentLine.value
                    if (currentLine != null) {
                         // Timed Mode: Refresh at 200ms (reduced from 100ms to avoid island lag)
                         mainHandler.postDelayed(this, 200)
                    } else {
                         // Adaptive Mode: Use calculated delay
                         mainHandler.postDelayed(this, adaptiveDelay)
                    }
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
        
        // Register observer for parsed lyrics
        LyricRepository.getInstance().liveParsedLyrics.observeForever(parsedLyricsObserver)
        
        // Reset adaptive scroll history for new song
        lastLyricChangeTime = 0
        lastLyricLength = 0
        lyricDurations.clear()
        adaptiveDelay = SCROLL_STEP_DELAY
        
        // Reset scroll state machine
        scrollState = ScrollState.INITIAL_PAUSE
        initialPauseStartTime = System.currentTimeMillis() // Start pause immediately
        scrollOffset = 0
        lastLyricText = "" // Force reset in next updateNotification
        
        mainHandler.post(visualizerLoop)
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        
        // Unregister observer
        LyricRepository.getInstance().liveParsedLyrics.removeObserver(parsedLyricsObserver)
        
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
        lyricDurations.add(duration)
        if (lyricDurations.size > maxHistory) {
            lyricDurations.removeAt(0)
        }
        
        // Update state
        lastLyricChangeTime = now
        lastLyricLength = newLyric.length
        
        // Recalculate adaptive delay
        calculateAdaptiveDelay()
    }
    
    private fun calculateAdaptiveDelay() {
        if (lyricDurations.isEmpty()) {
            adaptiveDelay = SCROLL_STEP_DELAY
            return
        }
        val avgDuration = lyricDurations.average().toLong()
        
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
    
    // Extract substring by visual weight with SMART WORD TRUNCATION
    // - Respects word boundaries (backtracks to space if cut point is mid-word)
    // - Forces cut only if word is longer than maxWeight (unbreakable)
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
        
        // Find rough end position
        currentWeight = 0
        for (i in startIndex until text.length) {
            currentWeight += charWeight(text[i])
            if (currentWeight > maxWeight) {
                break
            }
            endIndex = i + 1
        }
        
        if (endIndex <= startIndex) return ""
        
        // SMART TRUNCATION: Avoid splitting words
        // If cut point is mid-word (alphanumeric chars on both sides), backtrack to space
        if (endIndex < text.length) {
            val charAtCut = text[endIndex]
            val charBeforeCut = text[endIndex - 1]
            
            // Only apply to Western characters (weight 1). CJK (weight 2) can be split anywhere.
            if (charWeight(charAtCut) == 1 && charWeight(charBeforeCut) == 1 &&
                Character.isLetterOrDigit(charAtCut) && Character.isLetterOrDigit(charBeforeCut)) {
                // We are inside a word. Try to backtrack to a space.
                var spaceIndex = -1
                for (i in endIndex - 1 downTo startIndex) {
                    if (Character.isWhitespace(text[i])) {
                        spaceIndex = i
                        break
                    }
                }
                
                // Only backtrack if we found a space and it's not too far back (e.g., keep at least 50% of window?)
                // Actually, for lyrics, readability is key. Backtrack to space if found.
                // Exception: If the word is HUGE (covers entire window), we must split it.
                if (spaceIndex != -1) {
                    endIndex = spaceIndex
                }
            }
        }
        
        return text.substring(startIndex, endIndex).trimEnd()
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

    // Icon State
    private var iconScrollOffset = 0
    private var lastIconBaseText = ""
    private var lastNotifiedIconText = ""
    
    // Helper Class for Icon State
    private data class IconFrame(val text: String, val fontSize: Float? = null)
    private var currentIconFrame = IconFrame("")

    private fun updateNotification() {
        // Throttling: 50ms limit (reduced from 200ms for faster response)
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 50) return 
        lastUpdateTime = now

        // Get current lyric and position
        val lyricInfo = LyricRepository.getInstance().liveLyric.value
        val currentLyric = lyricInfo?.lyric ?: ""
        val progressInfo = LyricRepository.getInstance().liveProgress.value
        val currentPosition = progressInfo?.position ?: 0L

        var displayLyric: String

        // MODE SELECTION: Syllable > LRC > Weight-Based Fallback
        if (useSyllableScrolling && currentParsedLines != null) {
            // --- SYLLABLE SCROLLING MODE ---
            // Use nearest-line lookup instead of strict range match to prevent capsule disappearance in gaps
            val lines = currentParsedLines!!
            var currentIndex = -1
            // Find the line whose range contains currentPosition (strict match first)
            for (i in lines.indices) {
                if (currentPosition >= lines[i].startTime && currentPosition < lines[i].endTime) {
                    currentIndex = i
                    break
                }
            }
            // Fallback REMOVED: In a gap, show SPACE (per user request V3.5 Revised)
            // if (currentIndex == -1) ...
            
            val currentLine = if (currentIndex != -1) lines[currentIndex] else null
            

            
            if (currentLine != null && !currentLine.syllables.isNullOrEmpty()) {
                // Find sung and unsung portions
                val sungSyllables = currentLine.syllables.filter { it.startTime <= currentPosition }
                val unsungSyllables = currentLine.syllables.filter { it.startTime > currentPosition }
                
                val sungText = sungSyllables.joinToString("") { it.text }
                val unsungText = unsungSyllables.joinToString("") { it.text }
                
                // BUFFER NEXT LINE REMOVED per user request (V3.3/V3.4)
                // We rely solely on calculateSyllableWindow's length constraint to keep 14 units visible.
                
                // Use refined syllable window calculation with deferred start
                displayLyric = calculateSyllableWindow(sungText, unsungText)
                scrollState = ScrollState.SCROLLING // Managed by timing
            } else if (currentLine != null) {
                // Line exists but no syllable data, show the line text with weight constraint
                displayLyric = extractByWeight(currentLine.text, 0, maxDisplayWeight)
            } else {
                // GAP detected: Show SPACE to keep notification alive but visually empty
                displayLyric = " " 
            }
            
        } else if (useLrcScrolling && currentParsedLines != null) {
            // --- LRC SCROLLING MODE ---
            // Use nearest-line lookup to prevent capsule disappearance in gaps
            val lines = currentParsedLines!!
            var foundIndex = -1
            
            // Strict range match first
            for (i in lines.indices) {
                if (currentPosition >= lines[i].startTime && currentPosition < lines[i].endTime) {
                    foundIndex = i
                    break
                }
            }
            
            // Fallback REMOVED: In a gap, show SPACE (per user request V3.5 Revised)
            // if (foundIndex == -1) ...
            
            if (foundIndex != -1) {
                val foundLine = lines[foundIndex]
                
                val lineDuration = foundLine.endTime - foundLine.startTime
                val lineProgress = if (lineDuration > 0) {
                    ((currentPosition - foundLine.startTime).toFloat() / lineDuration.toFloat()).coerceIn(0f, 1f)
                } else 0f
                
                // Calculate scroll offset based on time progress
                val currentLineWeight = calculateWeight(foundLine.text)
                
                if (currentLineWeight <= maxDisplayWeight) {
                    // Line fits completely, no scrolling needed
                    displayLyric = foundLine.text
                } else {
                    // Deferred-start LRC scrolling: don't scroll in the first 30% of the line
                    val deferredProgress = ((lineProgress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                    
                    val scrollableWeight = currentLineWeight - maxDisplayWeight
                    val targetWeightOffset = (deferredProgress * scrollableWeight).toInt()
                    
                    // LENGTH CONSTRAINT: Maintain minimum visual length (14 weight units)
                    // Even if line is finishing, keep 14 units visible until line switch
                    // Ensure that: currentLineWeight - finalOffset >= 14
                    val minVisibleWeight = 14
                    val maxAllowedScroll = maxOf(0, currentLineWeight - minVisibleWeight)
                    
                    val finalOffset = minOf(targetWeightOffset, maxAllowedScroll)
                    
                    displayLyric = extractByWeight(foundLine.text, finalOffset, maxDisplayWeight)
                }
                scrollState = ScrollState.SCROLLING // Managed by timing
            } else {
                 // GAP detected: Show SPACE to keep notification alive but visually empty
                 displayLyric = " "
            }
            
        } else {
            // --- WEIGHT-BASED ADAPTIVE SCROLLING (Fallback) ---
            val totalWeight = calculateWeight(currentLyric)
            displayLyric = calculateAdaptiveScroll(currentLyric, totalWeight)
        }
        
        // --- DYNAMIC ICON LOGIC START ---
        val metadata = LyricRepository.getInstance().liveMetadata.value
        val realTitle = metadata?.title ?: lyricInfo?.sourceApp ?: "Island Lyrics"
        val realArtist = metadata?.artist ?: ""
        
        val newIconFrame = calculateDynamicIconFrame(realTitle, realArtist)
        currentIconFrame = newIconFrame // Store for buildNotification
        // --- DYNAMIC ICON LOGIC END ---

        // Get progress
        val currentProgress = if (progressInfo != null && progressInfo.duration > 0) {
            ((progressInfo.position.toFloat() / progressInfo.duration.toFloat()) * 100).toInt()
        } else -1
        
        // SKIP UPDATE if ALL DISPLAYED content unchanged
        if (displayLyric == lastNotifiedLyric && 
            currentProgress == lastNotifiedProgress &&
            currentIconFrame.text == lastNotifiedIconText) {
            return
        }
        
        // Content changed, update notification
        lastNotifiedLyric = displayLyric
        lastNotifiedProgress = currentProgress
        lastNotifiedIconText = currentIconFrame.text

        try {
            val notification = buildNotification()
            service.startForeground(1001, notification)
            // service.inspectNotification(notification, manager!!)
        } catch (e: Exception) {
            LogManager.getInstance().e(context, TAG, "Update Failed: $e")
        }
    }
    
    private fun calculateDynamicIconFrame(title: String, artist: String): IconFrame {
        val titleWeight = calculateWeight(title)
        
        // Check Metadata Change to reset scroll
        val currentBase = "$title|$artist"
        if (currentBase != lastIconBaseText) {
            lastIconBaseText = currentBase
            iconScrollOffset = 0
        }

        // Rule 1: Adaptive Fit (7-8 CJK chars -> weight 14-16)
        // If Title is in this specific "slightly too long" range, forcing it is better than scrolling.
        // We use CJK char count approximation or weight. 
        // 7 CJK = 14 weight. 8 CJK = 16 weight. 
        // Normal max is 14. 
        if (titleWeight in 15..16) {
             // Force Title only, scale down font
             return IconFrame(title, fontSize = 26f) // Reduced to fit 7-8 chars comfortably
        }
        
        // Rule 2: Standard Logic (Fit or Scroll)
        val baseText = "$title - $artist"
        val maxIconWeight = 14
        
        if (calculateWeight(baseText) <= maxIconWeight) {
            // Fits perfectly but force reduced font size to match adaptive case
            return IconFrame(baseText, fontSize = 26f) 
        }
        
        // Rule 3: Circular Scrolling
        // "Title - Artist    Title - Artist"
        val spacer = "    " // 4 spaces = 4 weight
        val hubText = baseText + spacer + baseText
        val cycleWeight = calculateWeight(baseText + spacer)
        
        // Extract window
        val frameText = extractByWeight(hubText, iconScrollOffset, maxIconWeight)
        
        // Increment offset for next frame
        // Use a fixed shift for icon (simpler than smart shift) or reuse smart shift
        val shift = 2 // Move 2 weight units per tick (smoother for icon)
        iconScrollOffset = (iconScrollOffset + shift) % cycleWeight
        
        return IconFrame(frameText, fontSize = 26f)
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

        // Action Buttons (controlled by Debug Center style selector)
        val actionPrefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        val actionStyle = actionPrefs.getString("notification_actions_style", "disabled") ?: "disabled"
        
        when (actionStyle) {
            "media_controls" -> {
                // Style A: Pause + Next
                val pauseIntent = PendingIntent.getService(
                    context, 1,
                    Intent(context, LyricService::class.java).setAction("ACTION_MEDIA_PAUSE"),
                    PendingIntent.FLAG_IMMUTABLE
                )
                val nextIntent = PendingIntent.getService(
                    context, 2,
                    Intent(context, LyricService::class.java).setAction("ACTION_MEDIA_NEXT"),
                    PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, context.getString(R.string.action_pause), pauseIntent)
                builder.addAction(0, context.getString(R.string.action_next), nextIntent)
            }
            "miplay" -> {
                // Style B: Launch Xiaomi MiPlay
                val miplayIntent = Intent().apply {
                    component = android.content.ComponentName(
                        "miui.systemui.plugin",
                        "miui.systemui.miplay.MiPlayDetailActivity"
                    )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val miplayPendingIntent = PendingIntent.getActivity(
                    context, 3, miplayIntent, PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, context.getString(R.string.action_miplay), miplayPendingIntent)
            }
        }


        // 1. Get Live Lyrics from Repository
        val lyricInfo = LyricRepository.getInstance().liveLyric.value
        val currentLyric = lyricInfo?.lyric ?: "Waiting for lyrics..."
        val sourceApp = lyricInfo?.sourceApp ?: "Island Lyrics"
        
        // Use pre-calculated displayLyric from updateNotification()
        // In Syllable/LRC mode, keep SPACE " " (gap). Only fallback in legacy modes.
        val displayLyric = if (useSyllableScrolling || useLrcScrolling) {
             lastNotifiedLyric 
        } else {
             lastNotifiedLyric.ifEmpty { currentLyric }
        }
        
        val title = sourceApp  // Title = app name
        val shortText = displayLyric  // Short text = scrolling lyrics

        // Set text fields
        builder.setContentTitle(title)
        builder.setContentText(currentLyric)  // Full lyrics
        builder.setSubText(sourceApp)

        // 2. ProgressStyle - SIMPLIFIED MUSIC-ONLY APPROACH
        try {
            // Get real-time music progress
                val progressInfo = LyricRepository.getInstance().liveProgress.value
                
                if (progressInfo != null && progressInfo.duration > 0) {
                    val segment = NotificationCompat.ProgressStyle.Segment(100)
                    segment.setColor(COLOR_PRIMARY)
                    
                    val segments = ArrayList<NotificationCompat.ProgressStyle.Segment>()
                    segments.add(segment)
                    
                    val percentage = (progressInfo.position.toFloat() / progressInfo.duration.toFloat())
                    val progressValue = (percentage * 100).toInt().coerceIn(0, 100)
                    
                    val progressStyle = NotificationCompat.ProgressStyle()
                        .setProgressSegments(segments)
                        .setStyledByProgress(true)
                        .setProgress(progressValue)
                    
                    builder.setStyle(progressStyle)
                } else {
                    // Indeterminate
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

        return builder.build().apply {
            // HyperOS Dynamic Icon Logic
            val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
            val useDynamicIcon = prefs.getBoolean("dynamic_icon_enabled", false)
            val iconStyle = prefs.getString("dynamic_icon_style", "classic") ?: "classic"
            
            if (useDynamicIcon) {
                val bitmap = when (iconStyle) {
                    "advanced" -> {
                        // Advanced style: Use album art + dual-line layout
                        val metadata = LyricRepository.getInstance().liveMetadata.value
                        val realTitle = metadata?.title ?: ""
                        val realArtist = metadata?.artist ?: ""
                        val albumArt = LyricRepository.getInstance().liveAlbumArt.value
                        
                        // Cache key includes all relevant data
                        val cacheKey = "advanced|$realTitle|$realArtist|${albumArt?.hashCode()}"
                        if (cachedIconKey != cacheKey) {
                            val parsedTitle = TitleParser.parse(realTitle)
                            cachedIconBitmap = AdvancedIconRenderer.render(albumArt, parsedTitle, realArtist, context)
                            cachedIconKey = cacheKey
                        }
                        cachedIconBitmap
                    }
                    else -> {
                        // Classic style: Scrolling text
                        val iconText = currentIconFrame.text
                        val forceSize = currentIconFrame.fontSize
                        
                        // Cache check - Key includes fontSize to handle adaptive switches
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
    
    // Caching
    private var cachedIconKey = ""
    private var cachedIconBitmap: android.graphics.Bitmap? = null

    private fun textToBitmap(text: String, forceFontSize: Float? = null): android.graphics.Bitmap? {
        try {
            // Adaptive Font Size Algorithm
            // Base size: 40f. Minimum size: 20f.
            // Decay: 0.8f per character over 10 chars.
            val length = text.length
            
            val fontSize = forceFontSize ?: if (length <= 10) {
                40f
            } else {
                (40f - (length - 10) * 0.8f).coerceAtLeast(20f)
            }
            
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = fontSize
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.LEFT
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            
            val baseline = -paint.ascent() // ascent() is negative
            // Fix: Add more buffer for wide chars in tight crops
            val width = (paint.measureText(text) + 10).toInt() 
            val height = (baseline + paint.descent() + 5).toInt()
            
            // Safety check for empty or invalid dimensions
            if (width <= 0 || height <= 0) return null

            val image = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(image)
            // Draw with small left padding
            canvas.drawText(text, 5f, baseline, paint)
            return image
        } catch (e: Exception) {
            LogManager.getInstance().e(context, TAG, "Failed to generate text bitmap: $e")
            return null
        }
    }

    
    /**
     * Calculate display window for syllable-based scrolling.
     * Uses DEFERRED-START scrolling: no scroll until sung text reaches threshold,
     * then scrolls left keeping sung tail + unsung head within maxDisplayWeight.
     */
    private fun calculateSyllableWindow(sung: String, unsung: String): String {
        val sungWeight = calculateWeight(sung)
        val unsungWeight = calculateWeight(unsung)
        val totalWeight = sungWeight + unsungWeight
        
        // If total weight fits in display, show everything
        if (totalWeight <= maxDisplayWeight) {
            return sung + unsung
        }
        
        // DEFERRED-START SCROLLING:
        // Phase 1: sungWeight < scrollStartThreshold → show from line start, no scroll
        // Phase 2: sungWeight >= threshold → scroll left, keeping sung tail context
        val scrollStartThreshold = 8 // Start scrolling after ~4 CJK chars or ~8 Western chars
        
        val fullText = sung + unsung
        
        // Calculate target scroll amount (how much sung text EXCEEDS threshold)
        val targetScrollAmount = if (sungWeight < scrollStartThreshold) {
            0
        } else {
            sungWeight - scrollStartThreshold
        }
        
        // LENGTH CONSTRAINT: Maintain minimum visual length of current line (14 weight units)
        // Ensure that: totalWeight - scrollAmount >= 14
        // If totalWeight < 14, maxAllowedScroll will be 0 (no scrolling allowed)
        val minVisibleWeight = 14
        val maxAllowedScroll = maxOf(0, totalWeight - minVisibleWeight)
        
        // Final scroll amount is the smaller of target and max allowed
        // This ensures that as unsung text runs out, we stop scrolling to keep sung text visible
        val finalScrollAmount = minOf(targetScrollAmount, maxAllowedScroll)
        
        return extractByWeight(fullText, finalScrollAmount, maxDisplayWeight)
    }
    


    private fun calculateAdaptiveScroll(currentLyric: String, totalWeight: Int): String {
        // Reset scroll offset if lyric changed
        if (currentLyric != lastLyricText) {
            lastLyricText = currentLyric
            scrollOffset = 0
            scrollState = ScrollState.INITIAL_PAUSE
            initialPauseStartTime = System.currentTimeMillis()
        }
        
        val displayLyric: String
        
        // Short lyric: no scrolling needed
        if (totalWeight <= maxDisplayWeight) {
            displayLyric = currentLyric
            scrollState = ScrollState.DONE // Mark done immediately for short lyrics
        } else {
             // ... Existing State Machine Logic ...
            when (scrollState) {
                ScrollState.INITIAL_PAUSE -> {
                    val pauseElapsed = System.currentTimeMillis() - initialPauseStartTime
                    if (pauseElapsed >= initialPauseDuration) {
                        scrollState = ScrollState.SCROLLING
                    }
                    displayLyric = extractByWeight(currentLyric, 0, maxDisplayWeight)
                }
                
                ScrollState.SCROLLING -> {
                    val remainingWeight = totalWeight - scrollOffset
                    if (remainingWeight <= compensationThreshold) {
                        scrollState = ScrollState.FINAL_PAUSE
                        initialPauseStartTime = System.currentTimeMillis()
                        displayLyric = extractByWeight(currentLyric, scrollOffset, remainingWeight)
                    } else if (remainingWeight <= maxDisplayWeight) {
                        scrollState = ScrollState.FINAL_PAUSE
                        initialPauseStartTime = System.currentTimeMillis()
                        displayLyric = extractByWeight(currentLyric, scrollOffset, maxDisplayWeight)
                    } else {
                        displayLyric = extractByWeight(currentLyric, scrollOffset, maxDisplayWeight)
                        scrollOffset += calculateSmartShiftWeight(currentLyric, scrollOffset)
                    }
                }
                
                ScrollState.FINAL_PAUSE -> {
                    val remainingWeight = totalWeight - scrollOffset
                    displayLyric = extractByWeight(currentLyric, scrollOffset, maxOf(remainingWeight, maxDisplayWeight))
                    val pauseElapsed = System.currentTimeMillis() - initialPauseStartTime
                    if (pauseElapsed >= finalPauseDuration) {
                        scrollState = ScrollState.DONE
                    }
                }
                
                ScrollState.DONE -> {
                    val remainingWeight = totalWeight - scrollOffset
                    displayLyric = extractByWeight(currentLyric, scrollOffset, maxOf(remainingWeight, maxDisplayWeight))
                }
            }
        }
        return displayLyric
    }

    companion object {
        private const val TAG = "LyricCapsule"
        private const val CHANNEL_ID = "lyric_capsule_channel"
        private const val SCROLL_STEP_DELAY = 1800L  // Slower scroll: 1.8s per update (was 1s)

        // Colors for progress bar (Grayscale by default)
        private const val COLOR_PRIMARY = 0xFF757575.toInt()   // Grey 600
        private const val COLOR_TERTIARY = 0xFFBDBDBD.toInt() // Grey 400
    }
}
