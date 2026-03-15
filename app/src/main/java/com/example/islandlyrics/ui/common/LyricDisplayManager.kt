package com.example.islandlyrics.ui.common

import android.content.Context
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.data.lyric.OnlineLyricFetcher
import com.example.islandlyrics.data.LyricRepository
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
    
    // Core state
    private var currentAlbumColor = LyricCapsuleHandler.COLOR_PRIMARY
    private var lastAlbumArtHash = 0
    private var lastExtractedArtHash = 0
    
    // Scroll state machine
    private enum class ScrollState {
        INITIAL_PAUSE, SCROLLING, FINAL_PAUSE, DONE
    }
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
    
    private val initialPauseDuration = 1000L
    private val finalPauseDuration = 500L
    private val baseFocusDelay = 500L
    private val staticTimeReserve = 1500L
    private val compensationThreshold = 20
    
    // Current parsed lyrics info
    private var useSyllableScrolling = false
    private var useLrcScrolling = false
    private var currentParsedLines: List<OnlineLyricFetcher.LyricLine>? = null
    
    // Observers
    private val parsedLyricsObserver = Observer<LyricRepository.ParsedLyricsInfo?> { parsedInfo ->
        if (parsedInfo != null && parsedInfo.hasSyllable) {
            useSyllableScrolling = true
            useLrcScrolling = false
            currentParsedLines = parsedInfo.lines
        } else if (parsedInfo != null && parsedInfo.lines.isNotEmpty()) {
            useSyllableScrolling = false
            useLrcScrolling = true
            currentParsedLines = parsedInfo.lines
        } else {
            useSyllableScrolling = false
            useLrcScrolling = false
            currentParsedLines = null
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
            try {
                processTick()
                
                if (scrollState == ScrollState.DONE) {
                    mainHandler.postDelayed(this, 1000)
                } else {
                    val currentLine = LyricRepository.getInstance().liveCurrentLine.value
                    if (currentLine != null) {
                        mainHandler.postDelayed(this, 200)
                    } else {
                        mainHandler.postDelayed(this, adaptiveDelay)
                    }
                }
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
        
        lastLyricChangeTime = 0
        lastLyricLength = 0
        lyricDurations.clear()
        adaptiveDelay = LyricCapsuleHandler.SCROLL_STEP_DELAY
        scrollState = ScrollState.INITIAL_PAUSE
        initialPauseStartTime = System.currentTimeMillis()
        
        LogManager.getInstance().i(context, "LyricDisplayManager", 
            "Initialized. ROM: ${RomUtils.getRomType()}, HeavySkin: $isHeavySkin, MaxWeight: $maxDisplayWeight")
        
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
            mainHandler.removeCallbacks(visualizerLoop)
            mainHandler.post(visualizerLoop)
        }
    }

    fun notifyLyricChanged(newLyric: String) {
        val now = System.currentTimeMillis()
        if (lastLyricChangeTime == 0L) {
            lastLyricChangeTime = now
            lastLyricLength = newLyric.length
            return
        }
        val duration = now - lastLyricChangeTime
        val avgCharDuration = if (lastLyricLength > 0) duration / lastLyricLength else 0
        
        if (avgCharDuration < minCharDuration) return
        if (duration > 30000) {
            lastLyricChangeTime = now
            lastLyricLength = newLyric.length
            return
        }
        
        lyricDurations.add(duration)
        if (lyricDurations.size > maxHistory) lyricDurations.removeAt(0)
        
        lastLyricChangeTime = now
        lastLyricLength = newLyric.length
        
        calculateAdaptiveDelay(newLyric)
        scrollState = ScrollState.INITIAL_PAUSE
        initialPauseStartTime = now
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
        val duration = metaInfo?.duration ?: 0L
        
        var displayLyric = ""
        var isStatic = false
        
        if (disableScrolling) {
            val currentLine = if (currentParsedLines != null) findCurrentLine(currentParsedLines!!, currentPosition) else null
            if (currentLine != null) {
                displayLyric = extractByWeight(currentLine.text, 0, maxDisplayWeight)
            } else {
                displayLyric = extractByWeight(currentLyric, 0, maxDisplayWeight)
            }
            scrollState = ScrollState.DONE
            isStatic = true
        } else if (useSyllableScrolling && currentParsedLines != null) {
            val currentLine = findCurrentLine(currentParsedLines!!, currentPosition)
            if (currentLine != null && !currentLine.syllables.isNullOrEmpty()) {
                val sungSyllables = currentLine.syllables.filter { it.startTime <= currentPosition }
                val unsungSyllables = currentLine.syllables.filter { it.startTime > currentPosition }
                displayLyric = calculateSyllableWindow(sungSyllables.joinToString("") { it.text }, unsungSyllables.joinToString("") { it.text })
                scrollState = ScrollState.SCROLLING
            } else if (currentLine != null) {
                displayLyric = extractByWeight(currentLine.text, 0, maxDisplayWeight)
            } else {
                displayLyric = " "
            }
        } else if (useLrcScrolling && currentParsedLines != null) {
            val foundLine = findCurrentLine(currentParsedLines!!, currentPosition)
            if (foundLine != null) {
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
                displayLyric = " "
            }
        } else {
            val totalWeight = calculateWeight(currentLyric)
            displayLyric = calculateAdaptiveScroll(currentLyric, totalWeight)
        }
        
        val progressPercent = if (duration > 0) ((currentPosition.toFloat() / duration.toFloat()) * 100).toInt() else -1
        
        val state = UIState(
            isPlaying = isPlaying,
            title = metaInfo?.title ?: sourceApp,
            artist = metaInfo?.artist ?: "",
            displayLyric = displayLyric,
            fullLyric = currentLyric,
            isStatic = isStatic,
            progressMax = 100,
            progressCurrent = progressPercent,
            albumColor = currentAlbumColor,
            useSyllableScrolling = useSyllableScrolling,
            syllableLines = currentParsedLines,
            currentLineIndex = -1,
            mediaPackage = metaInfo?.packageName ?: ""
        )
        onStateUpdated?.invoke(state)
    }

    // ---------- Helper Methods ----------
    private fun findCurrentLine(lines: List<OnlineLyricFetcher.LyricLine>, position: Long): OnlineLyricFetcher.LyricLine? {
        for (line in lines) {
            if (position >= line.startTime && position < line.endTime) return line
        }
        return null
    }

    private fun charWeight(c: Char): Int {
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
        if (text.isEmpty()) return false
        return text.count { charWeight(it) == 2 } <= text.length / 2
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
        currentWeight = 0
        for (i in startIndex until text.length) {
            currentWeight += charWeight(text[i])
            if (currentWeight > maxWeight) break
            endIndex = i + 1
        }
        if (endIndex <= startIndex) return ""
        return text.substring(startIndex, endIndex).trimEnd()
    }
    
    private fun calculateSyllableWindow(sungText: String, unsungText: String): String {
        val sungWeight = calculateWeight(sungText)
        val remainingCapacityWeight = maxOf(0, maxDisplayWeight - sungWeight)
        
        if (remainingCapacityWeight >= calculateWeight(unsungText)) {
            return "${sungText}${unsungText}"
        }
        
        val maxSafeSungWeight = maxDisplayWeight / 2
        
        if (sungWeight > maxSafeSungWeight) {
            val offsetWeight = sungWeight - maxSafeSungWeight
            val visibleSung = extractByWeight(sungText, offsetWeight, maxSafeSungWeight)
            val visibleUnsung = extractByWeight(unsungText, 0, maxDisplayWeight - calculateWeight(visibleSung))
            return "${visibleSung}${visibleUnsung}"
        } else {
            val visibleUnsung = extractByWeight(unsungText, 0, maxDisplayWeight - sungWeight)
            return "${sungText}${visibleUnsung}"
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
        if (segment.isEmpty()) return 4
        
        val cjkCount = segment.count { charWeight(it) == 2 }
        val isCJK = cjkCount > segment.length / 2
        
        return if (isCJK) {
            if (segment.length >= 2) charWeight(segment[0]) + charWeight(segment[1]) else 4
        } else {
            val spaceIndex = segment.indexOf(' ', 2)
            if (spaceIndex in 2..4) {
                calculateWeight(segment.take(spaceIndex + 1))
            } else if (segment.length >= 3) {
                calculateWeight(segment.take(3))
            } else 3
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
