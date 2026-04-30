package com.example.islandlyrics.ui.common

import android.content.Context
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.data.lyric.OnlineLyricFetcher
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.core.logging.LogManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Observer
import androidx.palette.graphics.Palette

/**
 * LyricDisplayManager
 * Centralized processor for lyric scrolling, visual weight calculation, 
 * adaptive delay timings, and album art color extraction.
 * Emits UIState for renderers (LyricCapsuleHandler and SuperIslandHandler).
 */
class LyricDisplayManager(private val context: Context) {

    // Output callback
    var onStateUpdated: ((UIState) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var pendingImmediateUpdate = false
    private var immediateUpdateQueued = false
    
    // Core state
    private var currentAlbumColor = LyricCapsuleHandler.COLOR_PRIMARY
    private var lastAlbumArtHash = 0
    private var lastExtractedArtHash = 0
    
    // Scroll state machine
    private enum class ScrollState {
        INITIAL_PAUSE, SCROLLING, FINAL_PAUSE, DONE
    }

    private data class GapDisplay(
        val displayLyric: String,
        val fullLyric: String,
        val isAnimated: Boolean,
        val nextDelayMs: Long
    )
    private var scrollState = ScrollState.INITIAL_PAUSE
    private var initialPauseStartTime: Long = 0
    
    // Config / Preferences
    private var disableScrolling = false
    
    // Adaptive scroll metrics
    private var lastLyricChangeTime: Long = 0
    private var lastLyricLength: Int = 0
    private val lyricDurations = mutableListOf<Long>()
    private val maxHistory = 5
    private var adaptiveDelay: Long = LyricCapsuleHandler.SCROLL_STEP_DELAY
    private val minCharDuration = 50L
    private val minScrollDelay = 200L
    private val maxScrollDelay = 5000L
    
    private val isHeavySkin = RomUtils.isHeavySkin()
    private val maxDisplayWeight = if (isHeavySkin) 16 else 10
    
    private val initialPauseDuration = 400L
    private val finalPauseDuration = 300L
    private val baseFocusDelay = 500L
    private val staticTimeReserve = 1000L
    private val compensationThreshold = 20
    private val timingGapHoldThresholdMs = 2000L
    private val timingGapAnimationLeadMs = 800L
    private val timingGapDotFrameMs = 450L
    private val timingGapAnimationTotalMs = timingGapDotFrameMs * 3
    private val timingGapFullSequenceMs = timingGapAnimationLeadMs + timingGapAnimationTotalMs
    private val timingPlaceholder = "♪"
    
    // Current parsed lyrics info
    private var useSyllableScrolling = false
    private var useLrcScrolling = false
    private var currentParsedLines: List<OnlineLyricFetcher.LyricLine>? = null
    private var currentParsedLyricsApiPath: String? = null
    private var lastStableDisplayLyric = ""
    private var lastStableFullLyric = ""
    private var timingGapActive = false
    private var timingGapNextDelayMs = 0L

    // SuperLyric arrival-based timing: the pushed line IS the current line,
    // so we track when it arrived to drive syllable animation using relative word timestamps.
    private var superLyricArrivalTime = 0L
    
    // Observers
    private val parsedLyricsObserver = Observer<LyricRepository.ParsedLyricsInfo?> { parsedInfo ->
        currentParsedLyricsApiPath = parsedInfo?.apiPath
        if (parsedInfo != null && parsedInfo.hasSyllable) {
            useSyllableScrolling = true
            useLrcScrolling = false
            currentParsedLines = parsedInfo.lines
            // Record arrival time for SuperLyric so we can drive syllable
            // animation using elapsed time instead of absolute song position.
            if (parsedInfo.apiPath.equals("SuperLyric", ignoreCase = true)) {
                superLyricArrivalTime = android.os.SystemClock.elapsedRealtime()
            }
        } else if (parsedInfo != null && parsedInfo.lines.isNotEmpty()) {
            useSyllableScrolling = false
            useLrcScrolling = true
            currentParsedLines = parsedInfo.lines
        } else {
            useSyllableScrolling = false
            useLrcScrolling = false
            currentParsedLines = null
        }

        // Parsed lyrics can arrive in the middle of a slow idle frame window.
        // Refresh immediately so BEFORE_FIRST / long-gap placeholder states do
        // not miss their entry point and accidentally show the lyric first.
        if (isRunning) {
            forceUpdate()
        } else {
            pendingImmediateUpdate = true
        }
    }
    
    private val albumArtObserver = Observer<Bitmap?> { bitmap ->
        if (bitmap == null) {
            currentAlbumColor = LyricCapsuleHandler.COLOR_PRIMARY
            lastAlbumArtHash = 0
            lastExtractedArtHash = 0
            return@Observer
        }
        val artHash = bitmap.hashCode()
        if (artHash != lastExtractedArtHash) {
            val extractingFor = artHash
            lastAlbumArtHash = artHash
            Palette.from(bitmap).generate { palette ->
                val currentArtHash = LyricRepository.getInstance().liveAlbumArt.value?.hashCode()
                if (extractingFor != currentArtHash) return@generate
                
                if (palette != null) {
                    currentAlbumColor = palette.getVibrantColor(
                        palette.getMutedColor(
                            palette.getDominantColor(LyricCapsuleHandler.COLOR_PRIMARY)
                        )
                    )
                    lastExtractedArtHash = artHash
                }
            }
        }
    }

    private val prefChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "disable_lyric_scrolling") {
            disableScrolling = prefs.getBoolean(key, false)
        }
    }

    private val visualizerLoop = object : Runnable {
        override fun run() {
            if (!isRunning) return
            immediateUpdateQueued = false
            try {
                processTick()

                mainHandler.postDelayed(this, computeNextFrameDelay())
            } catch (t: Throwable) {
                Log.e("LyricDisplayManager", "Crash in display loop", t)
            }
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        
        val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        disableScrolling = prefs.getBoolean("disable_lyric_scrolling", false)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        
        LyricRepository.getInstance().liveParsedLyrics.observeForever(parsedLyricsObserver)
        LyricRepository.getInstance().liveAlbumArt.observeForever(albumArtObserver)
        
        if (!pendingImmediateUpdate) {
            lastLyricChangeTime = 0
            lastLyricLength = 0
            lyricDurations.clear()
            adaptiveDelay = LyricCapsuleHandler.SCROLL_STEP_DELAY
            scrollState = ScrollState.INITIAL_PAUSE
            initialPauseStartTime = System.currentTimeMillis()
        }
        
        LogManager.getInstance().i(context, "LyricDisplayManager", 
            "Initialized. ROM: ${RomUtils.getRomType()}, HeavySkin: $isHeavySkin, MaxWeight: $maxDisplayWeight")
        
        pendingImmediateUpdate = false
        mainHandler.post(visualizerLoop)
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        
        val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        
        LyricRepository.getInstance().liveParsedLyrics.removeObserver(parsedLyricsObserver)
        LyricRepository.getInstance().liveAlbumArt.removeObserver(albumArtObserver)
        
        mainHandler.removeCallbacks(visualizerLoop)
    }
    
    fun forceUpdate() {
        // Run tick immediately without waiting for timer
        if (isRunning) {
            if (immediateUpdateQueued) return
            immediateUpdateQueued = true
            mainHandler.removeCallbacks(visualizerLoop)
            mainHandler.post(visualizerLoop)
        } else {
            pendingImmediateUpdate = true
        }
    }

    fun notifyLyricChanged(newLyric: String) {
        val now = System.currentTimeMillis()
        val duration = now - lastLyricChangeTime

        // Guard: ignore if called within the same scheduler tick (< 20 ms).
        // This prevents double-processing from observer + forceUpdate() re-entrancy,
        // but does NOT filter slow SuperLyric pushes which previously got dropped by
        // the old avgCharDuration < 50ms guard.
        if (lastLyricChangeTime > 0L && duration < 20L) return

        if (duration > 30000L) {
            // Very long gap (e.g. first lyric after background pause) — reset history
            // but still drive the display immediately.
            lyricDurations.clear()
        } else if (lastLyricChangeTime > 0L) {
            lyricDurations.add(duration)
            if (lyricDurations.size > maxHistory) lyricDurations.removeAt(0)
        }

        lastLyricChangeTime = now
        lastLyricLength = newLyric.length

        calculateAdaptiveDelay(newLyric)
        scrollState = ScrollState.INITIAL_PAUSE
        initialPauseStartTime = now

        // Always kick the display loop so the notification fires immediately
        // instead of waiting for the next scheduled timer tick.
        if (isRunning) {
            forceUpdate()
        } else {
            pendingImmediateUpdate = true
        }
    }

    private fun processTick() {
        val repo = LyricRepository.getInstance()
        val lyricInfo = repo.liveLyric.value
        val progressInfo = repo.liveProgress.value
        val metaInfo = repo.liveMetadata.value
        
        val isPlaying = repo.isPlaying.value == true
        val currentLyric = lyricInfo?.lyric ?: ""
        val sourceApp = lyricInfo?.sourceApp ?: ""
        val currentPosition = progressInfo?.position ?: 0L
        val duration = progressInfo?.duration ?: metaInfo?.duration ?: 0L
        
        var displayLyric = ""
        var fullLyricForDisplay = currentLyric
        var isStatic = false
        timingGapActive = false
        timingGapNextDelayMs = 0L
        
        if (disableScrolling) {
            val currentLine = if (currentParsedLines != null) findCurrentLine(currentParsedLines!!, currentPosition) else null
            if (currentLine != null) {
                displayLyric = extractByWeight(currentLine.text, 0, maxDisplayWeight)
                fullLyricForDisplay = currentLine.text
            } else {
                val gapDisplay = resolveTimingGapDisplayIfSupported(currentParsedLines, currentPosition, isPlaying)
                if (gapDisplay != null) {
                    displayLyric = gapDisplay.displayLyric
                    fullLyricForDisplay = gapDisplay.fullLyric
                    timingGapActive = true
                    timingGapNextDelayMs = gapDisplay.nextDelayMs
                } else {
                    displayLyric = resolveNoCurrentLineDisplay(currentLyric)
                    fullLyricForDisplay = resolveNoCurrentLineFullLyric(currentLyric)
                }
            }
            scrollState = ScrollState.DONE
            isStatic = true
        } else if (useSyllableScrolling && currentParsedLines != null) {
            val isSuperLyric = currentParsedLyricsApiPath.equals("SuperLyric", ignoreCase = true)
            // SuperLyric: the pushed line IS the current line (no position matching needed).
            // Word timestamps are relative to line start; use elapsed time since push arrival.
            // Non-SuperLyric: use absolute song position to find the matching line.
            val currentLine = if (isSuperLyric) {
                currentParsedLines!!.firstOrNull()
            } else {
                findCurrentLine(currentParsedLines!!, currentPosition)
            }
            if (currentLine != null && !currentLine.syllables.isNullOrEmpty()) {
                val sungSyllables = currentLine.syllables.filter { it.startTime <= currentPosition }
                val unsungSyllables = currentLine.syllables.filter { it.startTime > currentPosition }
                displayLyric = calculateSyllableWindow(
                    fullText = currentLine.text,
                    sungText = sungSyllables.joinToString("") { it.text },
                    unsungText = unsungSyllables.joinToString("") { it.text }
                )
                fullLyricForDisplay = currentLine.text
                scrollState = ScrollState.SCROLLING
            } else if (currentLine != null) {
                displayLyric = extractByWeight(currentLine.text, 0, maxDisplayWeight)
                fullLyricForDisplay = currentLine.text
            } else {
                val gapDisplay = resolveTimingGapDisplayIfSupported(currentParsedLines, currentPosition, isPlaying)
                if (gapDisplay != null) {
                    displayLyric = gapDisplay.displayLyric
                    fullLyricForDisplay = gapDisplay.fullLyric
                    timingGapActive = true
                    timingGapNextDelayMs = gapDisplay.nextDelayMs
                } else {
                    displayLyric = resolveNoCurrentLineDisplay(currentLyric)
                    fullLyricForDisplay = resolveNoCurrentLineFullLyric(currentLyric)
                }
            }
        } else if (useLrcScrolling && currentParsedLines != null) {
            val foundLine = findCurrentLine(currentParsedLines!!, currentPosition)
            if (foundLine != null) {
                fullLyricForDisplay = foundLine.text
                val lineDuration = foundLine.endTime - foundLine.startTime
                val lineProgress = if (lineDuration > 0) {
                    ((currentPosition - foundLine.startTime).toFloat() / lineDuration.toFloat()).coerceIn(0f, 1f)
                } else 0f
                val currentLineWeight = calculateWeight(foundLine.text)
                
                if (currentLineWeight <= maxDisplayWeight) {
                    displayLyric = foundLine.text
                } else {
                    val currentProgressWeight = (lineProgress * currentLineWeight).toInt()
                    val scrollStartThreshold = if (isMostlyWestern(foundLine.text)) 4 else 8
                    val targetWeightOffset = if (currentProgressWeight < scrollStartThreshold) 0 else currentProgressWeight - scrollStartThreshold
                    val minVisibleWeight = 14
                    val maxAllowedScroll = maxOf(0, currentLineWeight - minVisibleWeight)
                    val finalOffset = minOf(targetWeightOffset, maxAllowedScroll)
                    displayLyric = extractByWeight(foundLine.text, finalOffset, maxDisplayWeight)
                }
                scrollState = ScrollState.SCROLLING
            } else {
                val gapDisplay = resolveTimingGapDisplayIfSupported(currentParsedLines, currentPosition, isPlaying)
                if (gapDisplay != null) {
                    displayLyric = gapDisplay.displayLyric
                    fullLyricForDisplay = gapDisplay.fullLyric
                    timingGapActive = true
                    timingGapNextDelayMs = gapDisplay.nextDelayMs
                } else {
                    displayLyric = resolveNoCurrentLineDisplay(currentLyric)
                    fullLyricForDisplay = resolveNoCurrentLineFullLyric(currentLyric)
                }
            }
        } else {
            val totalWeight = calculateWeight(currentLyric)
            displayLyric = calculateAdaptiveScroll(currentLyric, totalWeight)
        }

        val shouldPersistStableDisplay = !timingGapActive && displayLyric.isNotBlank()
        if (shouldPersistStableDisplay) {
            lastStableDisplayLyric = displayLyric
            lastStableFullLyric = fullLyricForDisplay
        }
        
        val progressPercent = if (duration > 0) ((currentPosition.toFloat() / duration.toFloat()) * 100).toInt() else -1
        
        val state = UIState(
            isPlaying = isPlaying,
            title = metaInfo?.title ?: sourceApp,
            artist = metaInfo?.artist ?: "",
            displayLyric = displayLyric,
            fullLyric = fullLyricForDisplay,
            isStatic = isStatic,
            progressMax = 100,
            progressCurrent = progressPercent,
            albumColor = currentAlbumColor,
            useSyllableScrolling = useSyllableScrolling,
            syllableLines = currentParsedLines,
            currentLineIndex = -1,
            mediaPackage = metaInfo?.packageName ?: "",
            albumArt = repo.liveAlbumArt.value
        )
        onStateUpdated?.invoke(state)
    }

    private fun computeNextFrameDelay(): Long {
        if (timingGapActive) {
            return timingGapNextDelayMs.coerceAtLeast(1L)
        }

        if (scrollState == ScrollState.DONE) {
            return 1000L
        }

        return when {
            useSyllableScrolling -> 120L
            useLrcScrolling -> 350L
            LyricRepository.getInstance().liveCurrentLine.value != null -> 350L
            else -> adaptiveDelay
        }
    }

    // ---------- Helper Methods ----------
    private fun findCurrentLine(lines: List<OnlineLyricFetcher.LyricLine>, position: Long): OnlineLyricFetcher.LyricLine? {
        for (line in lines) {
            if (position >= line.startTime && position < line.endTime) return line
        }
        return null
    }

    private fun charWeight(c: Char): Int {
        if (c.isWhitespace()) return 0
        return when (Character.UnicodeBlock.of(c)) {
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.HANGUL_SYLLABLES -> 2
            else -> 1
        }
    }
    
    private fun calculateWeight(text: String): Int = text.sumOf { charWeight(it) }
    
    private fun isMostlyWestern(text: String): Boolean {
        val nonWhitespaceChars = text.count { !it.isWhitespace() }
        if (nonWhitespaceChars == 0) return false
        return text.count { !it.isWhitespace() && charWeight(it) == 2 } <= nonWhitespaceChars / 2
    }

    private fun extractByWeight(text: String, startWeight: Int, maxWeight: Int): String {
        var currentWeight = 0
        var startIndex = 0
        var endIndex = 0
        for (i in text.indices) {
            if (currentWeight >= startWeight) {
                startIndex = i
                break
            }
            currentWeight += charWeight(text[i])
        }
        while (startIndex < text.length && text[startIndex].isWhitespace()) {
            startIndex++
        }
        currentWeight = 0
        for (i in startIndex until text.length) {
            currentWeight += charWeight(text[i])
            if (currentWeight > maxWeight) break
            endIndex = i + 1
        }
        if (endIndex <= startIndex) return ""
        return text.substring(startIndex, endIndex).trim()
    }
    
    private fun calculateSyllableWindow(fullText: String, sungText: String, unsungText: String): String {
        val fullWeight = calculateWeight(fullText)
        if (fullWeight <= maxDisplayWeight) {
            return fullText
        }

        val sungWeight = calculateWeight(sungText)
        val unsungWeight = calculateWeight(unsungText)
        val scrollStartThreshold = if (isMostlyWestern(fullText)) 4 else maxDisplayWeight / 2
        val minTailVisibleWeight = maxOf(maxDisplayWeight - 2, maxDisplayWeight / 2)
        val maxOffset = maxOf(0, fullWeight - minTailVisibleWeight)
        val targetOffset = maxOf(0, sungWeight - scrollStartThreshold)
        val finalOffset = minOf(targetOffset, maxOffset)
        val visibleWeight = if (unsungWeight <= minTailVisibleWeight || finalOffset >= maxOffset) {
            minTailVisibleWeight
        } else {
            maxDisplayWeight
        }
        return extractByWeight(fullText, finalOffset, visibleWeight)
    }

    private fun resolveTimingGapDisplayIfSupported(
        lines: List<OnlineLyricFetcher.LyricLine>?,
        position: Long,
        isPlaying: Boolean
    ): GapDisplay? {
        if (!shouldApplyTimingGapDisplay()) return null
        return resolveTimingGapDisplay(lines, position, isPlaying)
    }

    private fun shouldApplyTimingGapDisplay(): Boolean {
        return !currentParsedLyricsApiPath.equals("SuperLyric", ignoreCase = true)
    }

    private fun resolveNoCurrentLineDisplay(currentLyric: String): String {
        if (shouldHoldStableDisplayWithoutGapAnimation()) {
            return lastStableDisplayLyric.ifBlank { extractByWeight(currentLyric, 0, maxDisplayWeight) }
        }
        return extractByWeight(currentLyric, 0, maxDisplayWeight)
    }

    private fun resolveNoCurrentLineFullLyric(currentLyric: String): String {
        if (shouldHoldStableDisplayWithoutGapAnimation()) {
            return lastStableFullLyric.ifBlank { currentLyric }
        }
        return currentLyric
    }

    private fun shouldHoldStableDisplayWithoutGapAnimation(): Boolean {
        // SuperLyric: the pushed line is always used directly (skipping findCurrentLine),
        // so this fallback is only reached if currentParsedLines is empty (shouldn't happen).
        // Holding the stable display prevents flicker between line pushes.
        return currentParsedLyricsApiPath.equals("SuperLyric", ignoreCase = true)
    }

    private fun resolveTimingGapDisplay(
        lines: List<OnlineLyricFetcher.LyricLine>?,
        position: Long,
        isPlaying: Boolean
    ): GapDisplay? {
        if (!isPlaying || lines.isNullOrEmpty()) return null

        val firstLine = lines.first()
        if (position < firstLine.startTime) {
            val remainingUntilFirst = firstLine.startTime - position
            return if (firstLine.startTime >= timingGapFullSequenceMs &&
                remainingUntilFirst <= timingGapAnimationTotalMs) {
                buildCountdownGapDisplay(remainingUntilFirst)
            } else {
                GapDisplay(
                    displayLyric = timingPlaceholder,
                    fullLyric = timingPlaceholder,
                    isAnimated = false,
                    nextDelayMs = computeGapPlaceholderDelay(
                        remainingMs = remainingUntilFirst,
                        supportsCountdown = firstLine.startTime >= timingGapFullSequenceMs
                    )
                )
            }
        }

        val lastLine = lines.last()
        if (position >= lastLine.endTime) {
            return GapDisplay(timingPlaceholder, timingPlaceholder, false, 1000L)
        }

        for (index in 0 until lines.lastIndex) {
            val current = lines[index]
            val next = lines[index + 1]
            if (position < current.endTime || position >= next.startTime) continue

            val remainingUntilNext = next.startTime - position
            val gapDuration = next.startTime - current.endTime
            if (gapDuration < timingGapHoldThresholdMs) {
                return stableGapFallback(current, remainingUntilNext)
            }

            return if (gapDuration >= timingGapFullSequenceMs) {
                if (remainingUntilNext <= timingGapAnimationTotalMs) {
                    buildCountdownGapDisplay(remainingUntilNext)
                } else {
                    GapDisplay(
                        displayLyric = timingPlaceholder,
                        fullLyric = timingPlaceholder,
                        isAnimated = false,
                        nextDelayMs = computeGapPlaceholderDelay(
                            remainingMs = remainingUntilNext,
                            supportsCountdown = true
                        )
                    )
                }
            } else {
                GapDisplay(
                    displayLyric = timingPlaceholder,
                    fullLyric = timingPlaceholder,
                    isAnimated = false,
                    nextDelayMs = computeGapPlaceholderDelay(
                        remainingMs = remainingUntilNext,
                        supportsCountdown = false
                    )
                )
            }
        }

        return null
    }

    private fun stableGapFallback(
        previousLine: OnlineLyricFetcher.LyricLine,
        remainingUntilNext: Long
    ): GapDisplay {
        val display = lastStableDisplayLyric.ifBlank {
            extractByWeight(previousLine.text, 0, maxDisplayWeight)
        }
        val full = lastStableFullLyric.ifBlank { previousLine.text }
        return GapDisplay(display, full, false, remainingUntilNext.coerceAtLeast(1L))
    }

    private fun buildCountdownGapDisplay(remainingUntilNext: Long): GapDisplay {
        val indicator: String
        val nextDelayMs: Long

        when {
            remainingUntilNext > timingGapDotFrameMs * 2 -> {
                indicator = "●●●"
                nextDelayMs = remainingUntilNext - timingGapDotFrameMs * 2
            }
            remainingUntilNext > timingGapDotFrameMs -> {
                indicator = "●●"
                nextDelayMs = remainingUntilNext - timingGapDotFrameMs
            }
            else -> {
                indicator = "●"
                nextDelayMs = remainingUntilNext
            }
        }
        return GapDisplay(indicator, indicator, true, nextDelayMs.coerceAtLeast(1L))
    }

    private fun computeGapPlaceholderDelay(remainingMs: Long, supportsCountdown: Boolean): Long {
        if (remainingMs <= 0L) return 1L
        if (!supportsCountdown) return remainingMs.coerceAtLeast(1L)
        return when {
            remainingMs > timingGapFullSequenceMs ->
                (remainingMs - timingGapFullSequenceMs).coerceAtLeast(1L)
            remainingMs > timingGapAnimationTotalMs ->
                (remainingMs - timingGapAnimationTotalMs).coerceAtLeast(1L)
            else ->
                remainingMs.coerceAtLeast(1L)
        }
    }
    
    private var scrollOffset = 0
    
    private fun calculateAdaptiveScroll(text: String, totalWeight: Int): String {
        if (totalWeight <= maxDisplayWeight) {
            scrollState = ScrollState.DONE
            return text
        }

        val now = System.currentTimeMillis()
        when (scrollState) {
            ScrollState.INITIAL_PAUSE -> {
                if (now - initialPauseStartTime > initialPauseDuration) {
                    scrollState = ScrollState.SCROLLING
                    scrollOffset = 0
                }
                return extractByWeight(text, 0, maxDisplayWeight)
            }
            ScrollState.SCROLLING -> {
                val shiftWeight = calculateSmartShiftWeight(text, scrollOffset)
                scrollOffset += shiftWeight
                
                val remainingWeight = totalWeight - scrollOffset
                if (remainingWeight <= compensationThreshold) {
                    scrollState = ScrollState.FINAL_PAUSE
                    initialPauseStartTime = now
                    scrollOffset = maxOf(0, totalWeight - maxDisplayWeight)
                }
                return extractByWeight(text, scrollOffset, maxDisplayWeight)
            }
            ScrollState.FINAL_PAUSE -> {
                if (now - initialPauseStartTime > finalPauseDuration) {
                    scrollState = ScrollState.DONE
                }
                val snapOffset = maxOf(0, totalWeight - maxDisplayWeight)
                return extractByWeight(text, snapOffset, maxDisplayWeight)
            }
            ScrollState.DONE -> {
                val snapOffset = maxOf(0, totalWeight - maxDisplayWeight)
                return extractByWeight(text, snapOffset, maxDisplayWeight)
            }
        }
    }
    
    private fun calculateSmartShiftWeight(text: String, currentOffset: Int): Int {
        val segment = extractByWeight(text, currentOffset, 10)
        if (segment.isEmpty()) return 2
        
        val nonWhitespaceChars = segment.count { !it.isWhitespace() }
        val cjkCount = segment.count { !it.isWhitespace() && charWeight(it) == 2 }
        val isCJK = nonWhitespaceChars > 0 && cjkCount > nonWhitespaceChars / 2
        
        return if (isCJK) {
            // Smoother for Japanese - shift by 2 weights (1 ideograph)
            if (segment.isNotEmpty()) charWeight(segment[0]) else 2
        } else {
            2
        }
    }
    
    private fun calculateAdaptiveDelay(newLyric: String) {
        if (lyricDurations.isEmpty()) {
            adaptiveDelay = LyricCapsuleHandler.SCROLL_STEP_DELAY
            return
        }
        val avgDuration = lyricDurations.average().toLong()
        val avgLyricWeight = calculateWeight(newLyric)
        val currentStaticReserve = if (isMostlyWestern(newLyric)) 750L else staticTimeReserve
        
        if (avgLyricWeight == 0 || avgDuration < currentStaticReserve) {
            adaptiveDelay = LyricCapsuleHandler.SCROLL_STEP_DELAY
            return
        }
        
        val estimatedSteps = maxOf(1, (avgLyricWeight / 5))
        val availableTime = avgDuration - currentStaticReserve - (estimatedSteps * baseFocusDelay)
        val timePerUnit = if (availableTime > 0 && avgLyricWeight > 0) availableTime / avgLyricWeight else 100L
        
        val avgShiftWeight = 5
        val calculatedDelay = baseFocusDelay + (timePerUnit * avgShiftWeight)
        adaptiveDelay = calculatedDelay.coerceIn(minScrollDelay, maxScrollDelay)
    }
}
