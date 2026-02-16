package com.example.islandlyrics

import android.app.Notification
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
import androidx.lifecycle.Observer
import androidx.palette.graphics.Palette


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
    
    // Cached preferences (Fix 3: avoid repeated SharedPreferences reads in hot loop)
    private var cachedActionStyle = "disabled"
    private var cachedUseAlbumColor = false
    private var cachedUseDynamicIcon = false
    private var cachedIconStyle = "classic"
    
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "notification_actions_style" -> cachedActionStyle = prefs.getString(key, "disabled") ?: "disabled"
            "progress_bar_color_enabled" -> cachedUseAlbumColor = prefs.getBoolean(key, false)
            "dynamic_icon_enabled" -> cachedUseDynamicIcon = prefs.getBoolean(key, false)
            "dynamic_icon_style" -> cachedIconStyle = prefs.getString(key, "classic") ?: "classic"
        }
    }
    
    private fun loadPreferences() {
        val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        cachedActionStyle = prefs.getString("notification_actions_style", "disabled") ?: "disabled"
        cachedUseAlbumColor = prefs.getBoolean("progress_bar_color_enabled", false)
        cachedUseDynamicIcon = prefs.getBoolean("dynamic_icon_enabled", false)
        cachedIconStyle = prefs.getString("dynamic_icon_style", "classic") ?: "classic"
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }
    
    private fun unloadPreferences() {
        context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefChangeListener)
    }

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
    
    // Determine max display weight based on ROM type
    // Heavy skins (HyperOS, OneUI, etc.) support wider display (~18 weight)
    // AOSP/Stock-like ROMs restricted to ~10 weight
    private val heavySkinRoms = setOf("HyperOS", "ColorOS", "OriginOS/FuntouchOS", "Flyme", "OneUI", "MagicOS", "RealmeUI")
    private val isHeavySkin = RomUtils.getRomType() in heavySkinRoms
    private val maxDisplayWeight = if (isHeavySkin) 18 else 10
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
    private val parsedLyricsObserver = Observer<LyricRepository.ParsedLyricsInfo?> { parsedInfo ->
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

    // Observer for album art changes - async color extraction (Fix 1: avoid blocking main thread)
    private val albumArtObserver = Observer<Bitmap?> { bitmap ->
        if (bitmap == null) {
            cachedAlbumColor = COLOR_PRIMARY
            cachedAlbumArtHash = 0
            return@Observer
        }
        val artHash = bitmap.hashCode()
        if (artHash == cachedAlbumArtHash) return@Observer
        // Async extraction - callback runs on main thread but extraction is off-main
        Palette.from(bitmap).generate { palette ->
            if (palette != null) {
                cachedAlbumColor = palette.getVibrantColor(
                    palette.getMutedColor(
                        palette.getDominantColor(COLOR_PRIMARY)
                    )
                )
                cachedAlbumArtHash = artHash
            }
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
        
        // Register observers
        LyricRepository.getInstance().liveParsedLyrics.observeForever(parsedLyricsObserver)
        LyricRepository.getInstance().liveAlbumArt.observeForever(albumArtObserver)
        
        // Load and listen for preference changes (Fix 3)
        loadPreferences()
        
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
        
        // Unregister observers
        LyricRepository.getInstance().liveParsedLyrics.removeObserver(parsedLyricsObserver)
        LyricRepository.getInstance().liveAlbumArt.removeObserver(albumArtObserver)
        
        // Unregister preference listener (Fix 3)
        unloadPreferences()
        
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

    fun updateLyricImmediate(lyric: String) {
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
                
                // Only backtrack if we found a space and it's not too far back.
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

    // Helper to find the nearest word boundary (space) before the target weight
    // Returns the weight position AFTER the space (start of next word)
    private fun findSnapOffset(text: String, targetWeight: Int): Int {
        var currentWeight = 0
        var lastSnapPoint = 0
        
        for (i in text.indices) {
            val c = text[i]
            val w = charWeight(c)
            
            // If including this char exceeds target, fallback to last snap point
            if (currentWeight + w > targetWeight) return lastSnapPoint
            
            currentWeight += w
            
            if (Character.isWhitespace(c)) {
                lastSnapPoint = currentWeight // Snap to character AFTER space
            }
        }
        return lastSnapPoint
    }

    // Icon State
    private var iconScrollOffset = 0
    private var lastIconBaseText = ""
    private var lastNotifiedIconText = ""
    
    // Helper Class for Icon State
    private data class IconFrame(val text: String, val fontSize: Float? = null)
    private var currentIconFrame = IconFrame("")

    // Helper to find current line in strict time range
    private fun findCurrentLine(lines: List<OnlineLyricFetcher.LyricLine>, position: Long): OnlineLyricFetcher.LyricLine? {
        for (line in lines) {
            if (position >= line.startTime && position < line.endTime) {
                return line
            }
        }
        return null
    }

    private fun updateNotification() {
        // Throttling: 50ms limit (reduced from 200ms for faster response)
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 50) return 
        lastUpdateTime = now

        // Get current lyric and position
        val lyricInfo = LyricRepository.getInstance().liveLyric.value
        val currentLyric = lyricInfo?.lyric ?: ""
        val sourceApp = lyricInfo?.sourceApp ?: "Island Lyrics" // Extract sourceApp here
        val progressInfo = LyricRepository.getInstance().liveProgress.value
        val currentPosition = progressInfo?.position ?: 0L

        var displayLyric: String

        // MODE SELECTION: Syllable > LRC > Weight-Based Fallback
        if (useSyllableScrolling && currentParsedLines != null) {
            // --- SYLLABLE SCROLLING MODE ---
            val currentLine = findCurrentLine(currentParsedLines!!, currentPosition)
            
            if (currentLine != null && !currentLine.syllables.isNullOrEmpty()) {
                // Find sung and unsung portions
                val sungSyllables = currentLine.syllables.filter { it.startTime <= currentPosition }
                val unsungSyllables = currentLine.syllables.filter { it.startTime > currentPosition }
                
                val sungText = sungSyllables.joinToString("") { it.text }
                val unsungText = unsungSyllables.joinToString("") { it.text }
                
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
            val foundLine = findCurrentLine(currentParsedLines!!, currentPosition)
            
            if (foundLine != null) {
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
                    // FIX: Use Play Head Tracking logic (same as Syllable mode) instead of Relative Scaling
                    // This ensures the scroll target moves 1:1 with the reading position, preventing lag.
                    val currentProgressWeight = (lineProgress * currentLineWeight).toInt()
                    
                    val scrollStartThreshold = 8
                    val targetWeightOffset = if (currentProgressWeight < scrollStartThreshold) {
                        0
                    } else {
                        currentProgressWeight - scrollStartThreshold
                    }
                    
                    // LENGTH CONSTRAINT: Maintain minimum visual length (14 weight units)
                    val minVisibleWeight = 14
                    val maxAllowedScroll = maxOf(0, currentLineWeight - minVisibleWeight)
                    
                    // Apply Snapping + Fallback (consistent with Syllable mode)
                    val finalOffset = calculateSnappedScroll(foundLine.text, targetWeightOffset, maxAllowedScroll)
                    
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
        val realTitle = metadata?.title ?: sourceApp
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
            // Pass all calculated values to avoid re-calculation
            val notification = buildNotification(
                displayLyric, 
                currentLyric, 
                sourceApp, 
                currentProgress, 
                currentIconFrame
            )
            service.startForeground(1001, notification)
        } catch (e: Exception) {
            LogManager.getInstance().e(context, TAG, "Update Failed: $e")
        }
    }
    
    private fun calculateDynamicIconFrame(title: String, artist: String): IconFrame {
        val titleWeight = calculateWeight(title)
        
        // Check Metadata Change to reset scroll (logic kept for safety, though scrolling is removed)
        val currentBase = "$title|$artist"
        if (currentBase != lastIconBaseText) {
            lastIconBaseText = currentBase
            iconScrollOffset = 0
        }

        // FIX: Strict Truncation Logic
        // Max weight 17 (approx 17 Western or 8 CJK chars)
        // No scrolling, no font shrinking.
        val baseText = "$title - $artist"
        val maxIconWeight = 17
        
        // Truncate directly locally
        val displayText = extractByWeight(baseText, 0, maxIconWeight)
        
        // Remove " - " dangling at end if artist was cut
        val cleanText = displayText.removeSuffix(" -").removeSuffix(" - ")
        
        // Check if we need ellipsis? User said "Truncate directly". 
        // usually icons don't have ellipsis space.
        
        // Use default font size (40f) by not passing argument
        return IconFrame(cleanText)
    }

    private fun buildNotification(
        displayLyric: String,
        fullLyric: String,
        sourceApp: String,
        progressPercent: Int,
        iconFrame: IconFrame
    ): Notification {
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

        // Action Buttons (using cached preferences - Fix 3)
        when (cachedActionStyle) {
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

        // Use passed values
        val currentLyric = fullLyric.ifEmpty { "Waiting for lyrics..." }
        val title = sourceApp  // Title = app name
        
        // Ensure displayLyric is valid
        val shortText = displayLyric.ifEmpty { currentLyric }

        // Set text fields
        builder.setContentTitle(title)
        builder.setContentText(currentLyric)  // Full lyrics
        builder.setSubText(sourceApp)

        // 2. ProgressStyle - SIMPLIFIED MUSIC-ONLY APPROACH
        try {
            // Determine progress bar color (using cached preference - Fix 3)
                val barColor = if (cachedUseAlbumColor) extractAlbumColor() else COLOR_PRIMARY
                val barColorIndeterminate = if (cachedUseAlbumColor) extractAlbumColor() else COLOR_TERTIARY

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
                    // Indeterminate
                    val segment = NotificationCompat.ProgressStyle.Segment(100)
                    segment.setColor(barColorIndeterminate)
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
            // HyperOS Dynamic Icon Logic (using cached preferences - Fix 3)
            if (cachedUseDynamicIcon) {
                val bitmap = when (cachedIconStyle) {
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
                        // Classic style: Text only
                        val iconText = iconFrame.text 
                        val forceSize = iconFrame.fontSize
                        
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

    // Album art color extraction cache
    private var cachedAlbumArtHash: Int = 0
    private var cachedAlbumColor: Int = COLOR_PRIMARY

    /**
     * Extract dominant color from album art using Palette API (Monet-style).
     * Color is pre-extracted asynchronously by albumArtObserver when album art changes.
     * This method is now a pure cache read - no blocking.
     */
    private fun extractAlbumColor(): Int {
        return cachedAlbumColor
    }

    private fun textToBitmap(text: String, forceFontSize: Float? = null): android.graphics.Bitmap? {
        try {
            // Adaptive Font Size Algorithm REMOVED
            // User requested fixed size, no shrinking.
            val fontSize = forceFontSize ?: 40f
            
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

    
    // Helper to calculate final scroll amount with Snapping + Fallback
    private fun calculateSnappedScroll(text: String, targetScroll: Int, maxScroll: Int): Int {
        // FIX: Snap to word boundaries
        val snapScroll = findSnapOffset(text, targetScroll)
        
        // Safety Fallback for giant words
        val effectiveScroll = if (targetScroll - snapScroll > 12) {
             targetScroll
        } else {
             snapScroll
        }
        
        return minOf(effectiveScroll, maxScroll)
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
        val minVisibleWeight = 14
        val maxAllowedScroll = maxOf(0, totalWeight - minVisibleWeight)
        
        // Apply Snapping + Fallback
        val finalScrollAmount = calculateSnappedScroll(fullText, targetScrollAmount, maxAllowedScroll)
        
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
