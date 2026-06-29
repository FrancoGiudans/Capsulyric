package com.example.islandlyrics.ui.overlay.display
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandTextLimitConfig
import com.example.islandlyrics.ui.overlay.config.LyricTextDisplayMode
import com.example.islandlyrics.ui.overlay.config.CapsuleRenderMode
import com.example.islandlyrics.ui.overlay.capsule.config.LiveUpdateTextLimitConfig
import com.example.islandlyrics.ui.overlay.capsule.LyricCapsuleHandler
import com.example.islandlyrics.ui.overlay.config.OverlayRenderDefaults
import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.model.LyricPresentation
import android.content.Context
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.core.logging.LogManager
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.LabFeatureManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Observer

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
    private val albumArtColorExtractor = AlbumArtColorExtractor(OverlayRenderDefaults.COLOR_PRIMARY)
    
    // Scroll state machine
    private enum class ScrollState {
        INITIAL_PAUSE, SCROLLING, FINAL_PAUSE, DONE
    }

    private var scrollState = ScrollState.INITIAL_PAUSE
    private var initialPauseStartTime: Long = 0
    
    // Config / Preferences
    private var displayConfig = OverlayDisplayConfig.from(AppPreferences.of(context))
    
    // Adaptive scroll metrics
    private var lastLyricChangeTime: Long = 0
    private var lastLyricLength: Int = 0
    private val lyricDurations = mutableListOf<Long>()
    private val maxHistory = 5
    private var adaptiveDelay: Long = OverlayRenderDefaults.SCROLL_STEP_DELAY
    private val isHeavySkin = RomUtils.isHeavySkin()
    private val baseMaxDisplayWeight = if (isHeavySkin) 16 else 10
    
    private val initialPauseDuration = 400L
    private val finalPauseDuration = 300L
    private val compensationThreshold = 20
    
    // Current parsed lyrics info
    private var parsedLyricState = ParsedLyricDisplayState()
    private var lastStableDisplayLyric = ""
    private var lastStableFullLyric = ""
    private var timingGapActive = false
    private var timingGapNextDelayMs = 0L

    // Observers
    private val parsedLyricsObserver = Observer<LyricRepository.ParsedLyricsInfo?> { parsedInfo ->
        parsedLyricState = ParsedLyricDisplayState.from(parsedInfo)

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
        albumArtColorExtractor.extract(bitmap) {
            LyricRepository.getInstance().liveAlbumArt.value?.hashCode()
        }
    }

    private val prefChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            AppPreferences.Keys.DISABLE_LYRIC_SCROLLING,
            LyricTextDisplayMode.PREF_KEY,
            CapsuleRenderMode.PREF_KEY,
            AppPreferences.Keys.SUPER_ISLAND_ENABLED_LEGACY,
            AppPreferences.Keys.SUPER_ISLAND_LYRIC_MODE,
            SuperIslandTextLimitConfig.KEY_RIGHT_CHARS,
            OverlayDisplayConfig.KEY_SUPER_ISLAND_RELAXED_TEXT_LIMITS,
            LabFeatureManager.KEY_LIVE_UPDATE_TEXT_LIMITS_ENABLED,
            LiveUpdateTextLimitConfig.KEY_CHARS -> {
                displayConfig = OverlayDisplayConfig.from(prefs)
                forceUpdate()
            }
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
        
        val prefs = AppPreferences.of(context)
        displayConfig = OverlayDisplayConfig.from(prefs)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        
        LyricRepository.getInstance().liveParsedLyrics.observeForever(parsedLyricsObserver)
        LyricRepository.getInstance().liveAlbumArt.observeForever(albumArtObserver)
        
        if (!pendingImmediateUpdate) {
            lastLyricChangeTime = 0
            lastLyricLength = 0
            lyricDurations.clear()
            adaptiveDelay = OverlayRenderDefaults.SCROLL_STEP_DELAY
            scrollState = ScrollState.INITIAL_PAUSE
            initialPauseStartTime = System.currentTimeMillis()
        }
        
        LogManager.getInstance().i(context, "LyricDisplayManager", 
            "Initialized. ROM: ${RomUtils.getRomType()}, HeavySkin: $isHeavySkin, MaxWeight: ${currentMaxDisplayWeight()}")
        
        pendingImmediateUpdate = false
        mainHandler.post(visualizerLoop)
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        
        AppPreferences.of(context).unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        
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
        val preferredPlainLyric = LyricTextDisplayMode.resolve(displayConfig.lyricTextDisplayMode, lyricInfo, currentLyric)
        val sourceApp = lyricInfo?.sourceApp ?: ""
        val currentPosition = progressInfo?.position ?: 0L
        val duration = progressInfo?.duration ?: metaInfo?.duration ?: 0L
        val isInstrumental = lyricInfo?.apiPath == LyricRepository.API_PATH_INSTRUMENTAL
        val effectiveParsedLyricState = if (isInstrumental) {
            ParsedLyricDisplayState()
        } else {
            parsedLyricState
        }
        
        var displayLyric = ""
        var fullLyricForDisplay = currentLyric
        var preferMetadataLayout = false
        var isTimingGapPlaceholder = false
        var isTimingGapAnimated = false
        var isStatic = false
        timingGapActive = false
        timingGapNextDelayMs = 0L
        val maxDisplayWeight = currentMaxDisplayWeight()
        
        if (isInstrumental) {
            displayLyric = ""
            fullLyricForDisplay = ""
            preferMetadataLayout = true
            scrollState = ScrollState.DONE
            isStatic = true
        } else if (displayConfig.disableScrolling) {
            val currentLine = if (effectiveParsedLyricState.lines != null) effectiveParsedLyricState.currentLine(currentPosition) else null
            if (currentLine != null) {
                val text = resolvePreferredLineText(currentLine.text, lyricInfo, currentLine)
                displayLyric = LyricTextWindowCalculator.extractByWeight(text, 0, maxDisplayWeight)
                fullLyricForDisplay = text
            } else {
                val gapDisplay = resolveTimingGapDisplay(effectiveParsedLyricState, currentPosition, isPlaying, maxDisplayWeight)
                if (gapDisplay != null) {
                    displayLyric = gapDisplay.displayLyric
                    fullLyricForDisplay = gapDisplay.fullLyric
                    preferMetadataLayout = gapDisplay.preferMetadataLayout
                    isTimingGapPlaceholder = gapDisplay.isTimingGapPlaceholder
                    isTimingGapAnimated = gapDisplay.isAnimated
                    timingGapActive = true
                    timingGapNextDelayMs = gapDisplay.nextDelayMs
                } else {
                    displayLyric = resolveNoCurrentLineDisplay(preferredPlainLyric, maxDisplayWeight)
                    fullLyricForDisplay = resolveNoCurrentLineFullLyric(preferredPlainLyric)
                }
            }
            scrollState = ScrollState.DONE
            isStatic = true
        } else if (effectiveParsedLyricState.useSyllableScrolling && effectiveParsedLyricState.lines != null) {
            val currentLine = effectiveParsedLyricState.currentLine(currentPosition)
            if (currentLine != null && !currentLine.syllables.isNullOrEmpty()) {
                val text = resolvePreferredLineText(currentLine.text, lyricInfo, currentLine)
                displayLyric = if (text == currentLine.text) {
                    val sungSyllables = currentLine.syllables.filter { it.startTime <= currentPosition }
                    LyricTextWindowCalculator.syllableWindow(
                        fullText = currentLine.text,
                        sungText = sungSyllables.joinToString("") { it.text },
                        maxDisplayWeight = maxDisplayWeight
                    )
                } else {
                    LyricTextWindowCalculator.extractByWeight(text, 0, maxDisplayWeight)
                }
                fullLyricForDisplay = text
                scrollState = ScrollState.SCROLLING
            } else if (currentLine != null) {
                val text = resolvePreferredLineText(currentLine.text, lyricInfo, currentLine)
                displayLyric = LyricTextWindowCalculator.extractByWeight(text, 0, maxDisplayWeight)
                fullLyricForDisplay = text
            } else {
                val gapDisplay = resolveTimingGapDisplay(effectiveParsedLyricState, currentPosition, isPlaying, maxDisplayWeight)
                if (gapDisplay != null) {
                    displayLyric = gapDisplay.displayLyric
                    fullLyricForDisplay = gapDisplay.fullLyric
                    preferMetadataLayout = gapDisplay.preferMetadataLayout
                    isTimingGapPlaceholder = gapDisplay.isTimingGapPlaceholder
                    isTimingGapAnimated = gapDisplay.isAnimated
                    timingGapActive = true
                    timingGapNextDelayMs = gapDisplay.nextDelayMs
                } else {
                    displayLyric = resolveNoCurrentLineDisplay(preferredPlainLyric, maxDisplayWeight)
                    fullLyricForDisplay = resolveNoCurrentLineFullLyric(preferredPlainLyric)
                }
            }
        } else if (effectiveParsedLyricState.useLrcScrolling && effectiveParsedLyricState.lines != null) {
            val foundLine = effectiveParsedLyricState.currentLine(currentPosition)
            if (foundLine != null) {
                val preferredLineText = resolvePreferredLineText(foundLine.text, lyricInfo, foundLine)
                fullLyricForDisplay = preferredLineText
                val lineDuration = foundLine.endTime - foundLine.startTime
                val lineProgress = if (lineDuration > 0) {
                    ((currentPosition - foundLine.startTime).toFloat() / lineDuration.toFloat()).coerceIn(0f, 1f)
                } else 0f
                val currentLineWeight = LyricTextWindowCalculator.weight(preferredLineText)
                
                if (currentLineWeight <= maxDisplayWeight) {
                    displayLyric = preferredLineText
                } else {
                    val currentProgressWeight = (lineProgress * currentLineWeight).toInt()
                    val scrollStartThreshold = LyricTextWindowCalculator.scrollStartThreshold(preferredLineText, maxDisplayWeight)
                    val targetWeightOffset = if (currentProgressWeight < scrollStartThreshold) 0 else currentProgressWeight - scrollStartThreshold
                    // Keep the scrolling window width aligned with the configured
                    // Super Island right-side limit instead of shrinking back to
                    // the old fixed 7-char tail.
                    val maxAllowedScroll = maxOf(0, currentLineWeight - maxDisplayWeight)
                    val finalOffset = minOf(targetWeightOffset, maxAllowedScroll)
                    displayLyric = LyricTextWindowCalculator.extractByWeight(preferredLineText, finalOffset, maxDisplayWeight)
                }
                scrollState = ScrollState.SCROLLING
            } else {
                val gapDisplay = resolveTimingGapDisplay(effectiveParsedLyricState, currentPosition, isPlaying, maxDisplayWeight)
                if (gapDisplay != null) {
                    displayLyric = gapDisplay.displayLyric
                    fullLyricForDisplay = gapDisplay.fullLyric
                    preferMetadataLayout = gapDisplay.preferMetadataLayout
                    isTimingGapPlaceholder = gapDisplay.isTimingGapPlaceholder
                    isTimingGapAnimated = gapDisplay.isAnimated
                    timingGapActive = true
                    timingGapNextDelayMs = gapDisplay.nextDelayMs
                } else {
                    displayLyric = resolveNoCurrentLineDisplay(preferredPlainLyric, maxDisplayWeight)
                    fullLyricForDisplay = resolveNoCurrentLineFullLyric(preferredPlainLyric)
                }
            }
        } else {
            val totalWeight = LyricTextWindowCalculator.weight(preferredPlainLyric)
            displayLyric = calculateAdaptiveScroll(preferredPlainLyric, totalWeight)
            fullLyricForDisplay = preferredPlainLyric
        }

        val shouldPersistStableDisplay = !timingGapActive && displayLyric.isNotBlank()
        if (shouldPersistStableDisplay) {
            lastStableDisplayLyric = displayLyric
            lastStableFullLyric = fullLyricForDisplay
        }
        
        val progressPercent = if (duration > 0) ((currentPosition.toFloat() / duration.toFloat()) * 100).toInt() else -1
        val lyricPresentation = buildLyricPresentation(
            lyricInfo = lyricInfo,
            position = currentPosition,
            gapDisplayText = displayLyric,
            useGapContext = timingGapActive,
            useGapIndicatorLine = timingGapActive && (isTimingGapPlaceholder || isTimingGapAnimated),
            parsedState = effectiveParsedLyricState
        )
        
        val state = UIState(
            isPlaying = isPlaying,
            title = metaInfo?.title ?: sourceApp,
            artist = metaInfo?.artist ?: "",
            displayLyric = displayLyric,
            fullLyric = fullLyricForDisplay,
            preferMetadataLayout = preferMetadataLayout,
            isTimingGapPlaceholder = isTimingGapPlaceholder,
            timelineCapability = effectiveParsedLyricState.timelineCapability,
            isStatic = isStatic,
            progressMax = 100,
            progressCurrent = progressPercent,
            albumColor = albumArtColorExtractor.currentColor,
            useSyllableScrolling = effectiveParsedLyricState.useSyllableScrolling,
            syllableLines = effectiveParsedLyricState.lines,
            currentLineIndex = lyricPresentation.currentLineIndex,
            lyricPresentation = lyricPresentation,
            mediaPackage = metaInfo?.packageName ?: "",
            albumArt = repo.liveAlbumArt.value
        )
        onStateUpdated?.invoke(state)
    }

    private fun buildLyricPresentation(
        lyricInfo: LyricRepository.LyricInfo?,
        position: Long,
        gapDisplayText: String,
        useGapContext: Boolean,
        useGapIndicatorLine: Boolean,
        parsedState: ParsedLyricDisplayState
    ): LyricPresentation {
        val currentIndex = parsedState.currentLineIndex(position)
        val parsedCurrentLine = parsedState.lines?.getOrNull(currentIndex)
        val isCompleteTimeline = parsedState.timelineCapability == LyricRepository.TimelineCapability.MULTI_LINE
        val beforeIndex = if (parsedCurrentLine == null && isCompleteTimeline) {
            parsedState.lineIndexBefore(position)
        } else {
            -1
        }
        val afterIndex = if (parsedCurrentLine == null && isCompleteTimeline) {
            parsedState.lineIndexAfter(position)
        } else {
            -1
        }
        val lines = parsedState.lines
        val canUseLiveLyricFallback =
            parsedState.timelineCapability != LyricRepository.TimelineCapability.MULTI_LINE
        val currentLine = parsedCurrentLine
            ?.toDisplayLine()
            ?: buildGapDisplayLine(gapDisplayText).takeIf { isCompleteTimeline && useGapIndicatorLine }
            ?: lines?.getOrNull(beforeIndex)?.toDisplayLine().takeIf { isCompleteTimeline && useGapContext }
            ?: lyricInfo?.takeIf { canUseLiveLyricFallback && it.lyric.isNotBlank() }?.let {
                LyricPresentation.DisplayLine(
                    text = it.lyric,
                    romanization = it.roma?.takeIf(String::isNotBlank),
                    translation = it.translation?.takeIf(String::isNotBlank)
                )
            }

        return LyricPresentation(
            currentLine = currentLine,
            previousLine = when {
                parsedCurrentLine != null -> parsedState.previousLine(currentIndex)?.toDisplayLine()
                currentLine != null && isCompleteTimeline && afterIndex < 0 -> lines?.getOrNull(beforeIndex - 1)?.toDisplayLine()
                isCompleteTimeline -> lines?.getOrNull(beforeIndex)?.toDisplayLine()
                else -> null
            },
            nextLine = when {
                parsedCurrentLine != null -> parsedState.nextLine(currentIndex)?.toDisplayLine()
                isCompleteTimeline -> lines?.getOrNull(afterIndex)?.toDisplayLine()
                else -> null
            },
            currentLineIndex = currentIndex,
            timelineCapability = parsedState.timelineCapability,
            wordProgress = buildWordProgress(parsedCurrentLine, position)
        )
    }

    private fun buildGapDisplayLine(text: String): LyricPresentation.DisplayLine {
        val normalizedText = text.takeUnless { it.isBlank() || it == "♪" } ?: "●●●"
        return LyricPresentation.DisplayLine(text = normalizedText)
    }

    private fun OnlineLyricFetcher.LyricLine.toDisplayLine(): LyricPresentation.DisplayLine {
        return LyricPresentation.DisplayLine(
            text = text,
            romanization = roma?.takeIf(String::isNotBlank),
            translation = translation?.takeIf(String::isNotBlank),
            startTime = startTime,
            endTime = endTime,
            syllables = syllables
        )
    }

    private fun buildWordProgress(
        line: OnlineLyricFetcher.LyricLine?,
        position: Long
    ): LyricPresentation.WordProgress? {
        val syllables = line?.syllables?.takeIf { it.isNotEmpty() } ?: return null
        val sungSyllables = syllables.filter { it.startTime <= position }
        val lineDuration = line.endTime - line.startTime
        val lineProgress = if (lineDuration > 0) {
            ((position - line.startTime).toFloat() / lineDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        return LyricPresentation.WordProgress(
            sungText = sungSyllables.joinToString("") { it.text },
            sungSyllableCount = sungSyllables.size,
            totalSyllableCount = syllables.size,
            lineProgress = lineProgress
        )
    }

    private fun computeNextFrameDelay(): Long {
        if (timingGapActive) {
            return timingGapNextDelayMs.coerceAtLeast(1L)
        }

        if (scrollState == ScrollState.DONE) {
            return 1000L
        }

        return when {
            parsedLyricState.useSyllableScrolling -> 120L
            parsedLyricState.useLrcScrolling -> 350L
            LyricRepository.getInstance().liveCurrentLine.value != null -> 350L
            else -> adaptiveDelay
        }
    }

    // ---------- Helper Methods ----------
    private fun resolvePreferredLineText(
        mainText: String,
        lyricInfo: LyricRepository.LyricInfo?,
        line: OnlineLyricFetcher.LyricLine? = null
    ): String {
        val preferred = when (displayConfig.lyricTextDisplayMode) {
            LyricTextDisplayMode.TRANSLATION -> line?.translation
            LyricTextDisplayMode.ROMANIZATION -> line?.roma
            else -> null
        }?.takeIf { it.isNotBlank() }
        if (preferred != null) return preferred

        return LyricTextDisplayMode.resolve(displayConfig.lyricTextDisplayMode, lyricInfo, mainText)
    }

    private fun currentMaxDisplayWeight(): Int {
        return displayConfig.maxDisplayWeight(baseMaxDisplayWeight)
    }

    private fun resolveTimingGapDisplay(
        state: ParsedLyricDisplayState,
        position: Long,
        isPlaying: Boolean,
        maxDisplayWeight: Int
    ): TimingGapDisplay? {
        return TimingGapDisplayResolver.resolve(
            lines = state.lines,
            position = position,
            isPlaying = isPlaying,
            timelineCapability = state.timelineCapability,
            lastStableDisplayLyric = lastStableDisplayLyric,
            lastStableFullLyric = lastStableFullLyric,
            maxDisplayWeight = maxDisplayWeight
        )
    }

    private fun resolveNoCurrentLineDisplay(currentLyric: String, maxDisplayWeight: Int): String {
        return TimingGapDisplayResolver.noCurrentLineDisplay(
            currentLyric = currentLyric,
            timelineCapability = parsedLyricState.timelineCapability,
            lastStableDisplayLyric = lastStableDisplayLyric,
            maxDisplayWeight = maxDisplayWeight
        )
    }

    private fun resolveNoCurrentLineFullLyric(currentLyric: String): String {
        return TimingGapDisplayResolver.noCurrentLineFullLyric(
            currentLyric = currentLyric,
            timelineCapability = parsedLyricState.timelineCapability,
            lastStableFullLyric = lastStableFullLyric
        )
    }

    private var scrollOffset = 0
    
    private fun calculateAdaptiveScroll(text: String, totalWeight: Int): String {
        val maxDisplayWeight = currentMaxDisplayWeight()
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
                return LyricTextWindowCalculator.extractByWeight(text, 0, maxDisplayWeight)
            }
            ScrollState.SCROLLING -> {
                val shiftWeight = LyricTextWindowCalculator.smartShiftWeight(text, scrollOffset)
                scrollOffset += shiftWeight
                
                val remainingWeight = totalWeight - scrollOffset
                if (remainingWeight <= compensationThreshold) {
                    scrollState = ScrollState.FINAL_PAUSE
                    initialPauseStartTime = now
                    scrollOffset = maxOf(0, totalWeight - maxDisplayWeight)
                }
                return LyricTextWindowCalculator.extractByWeight(text, scrollOffset, maxDisplayWeight)
            }
            ScrollState.FINAL_PAUSE -> {
                if (now - initialPauseStartTime > finalPauseDuration) {
                    scrollState = ScrollState.DONE
                }
                val snapOffset = maxOf(0, totalWeight - maxDisplayWeight)
                return LyricTextWindowCalculator.extractByWeight(text, snapOffset, maxDisplayWeight)
            }
            ScrollState.DONE -> {
                val snapOffset = maxOf(0, totalWeight - maxDisplayWeight)
                return LyricTextWindowCalculator.extractByWeight(text, snapOffset, maxDisplayWeight)
            }
        }
    }
    
    private fun calculateAdaptiveDelay(newLyric: String) {
        adaptiveDelay = LyricTextWindowCalculator.adaptiveDelay(
            text = newLyric,
            lyricDurations = lyricDurations,
            defaultDelayMs = OverlayRenderDefaults.SCROLL_STEP_DELAY
        )
    }
}
