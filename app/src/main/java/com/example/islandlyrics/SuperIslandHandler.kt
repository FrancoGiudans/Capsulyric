package com.example.islandlyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.palette.graphics.Palette
import org.json.JSONObject

import android.content.SharedPreferences
import com.example.islandlyrics.OnlineLyricFetcher

/**
 * SuperIslandHandler
 * Sends Xiaomi Super Island notifications using Template 7:
 *   展开态: IM图文组件 + 识别图形组件1 + 进度组件2
 *   摘要态: 图文组件1 (A区, album art) + 文本 (B区, scrolling lyrics)
 *   小岛:   album art thumbnail
 *
 * Content mapping:
 *   头像图   → album art (LyricRepository.liveAlbumArt)
 *   主要文本 → current lyric line
 *   次要文本 → "songTitle - artist"
 *   进度组件 → playback progress
 *   摘要态B区 → scrolling lyric text (maxDisplayWeight=8)
 */
class SuperIslandHandler(
    private val context: Context,
    private val service: LyricService
) {

    private val manager: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    var isRunning = false
        private set

    // Cached notification — built once, extras updated in-place
    private var cachedNotification: Notification? = null
    private var cachedBuilder: Notification.Builder? = null
    private var cachedClickStyle = "default"

    // New preferences
    private var cachedSuperIslandTextColorEnabled = false
    private var cachedSuperIslandShareEnabled = true
    private var cachedSuperIslandShareFormat = "format_1"
    private var cachedProgressBarColorEnabled = false
    private var cachedActionStyle = "disabled"
    private var cachedAlbumColor = 0
    private var lastAlbumArtPaletteHash = 0
    private var lastMetadata: LyricRepository.MediaInfo? = null

    private val prefs by lazy { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            "notification_click_style" -> {
                cachedClickStyle = p.getString(key, "default") ?: "default"
                forceUpdateNotification()
            }
            "super_island_text_color_enabled" -> {
                cachedSuperIslandTextColorEnabled = p.getBoolean(key, false)
                forceUpdateNotification()
            }
            "super_island_share_enabled" -> {
                cachedSuperIslandShareEnabled = p.getBoolean(key, true)
                forceUpdateNotification()
            }
            "super_island_share_format" -> {
                cachedSuperIslandShareFormat = p.getString(key, "format_1") ?: "format_1"
                forceUpdateNotification()
            }
            "disable_lyric_scrolling" -> {
                cachedDisableScrolling = p.getBoolean(key, false)
                forceUpdateNotification()
            }
            "progress_bar_color_enabled" -> {
                cachedProgressBarColorEnabled = p.getBoolean(key, false)
                forceUpdateNotification()
            }
            "notification_actions_style" -> {
                cachedActionStyle = p.getString(key, "disabled") ?: "disabled"
                forceUpdateNotification()
            }
        }
    }

    // Shared scrolling preference
    private var cachedDisableScrolling = false

    // Change detection
    private var lastLyric = ""
    private var lastProgress = -1
    private var lastAlbumArtHash = 0
    private var lastSubText = ""
    private var lastPackageName = ""
    private var lastAppliedAlbumColor = 0
    private var lastPicAppHash = 0
    private var lastPicActionsHash = 0
    private var cachedPicsBundle: Bundle? = null
    private var cachedActionsBundle: Bundle? = null

    // Scroll state machine
    private enum class ScrollState {
        INITIAL_PAUSE, SCROLLING, FINAL_PAUSE, DONE
    }
    
    private var scrollState = ScrollState.INITIAL_PAUSE
    private var initialPauseStartTime: Long = 0
    private var scrollOffset = 0
    private var lastLyricText = ""

    // Content tracking to prevent flicker
    private var lastNotifiedLyric = ""
    private var lastNotifiedProgress = -1
    private var isFirstNotification = true
    private var lastUpdateTime = 0L

    // Scroll Configuration
    private val heavySkinRoms = setOf("HyperOS", "ColorOS", "OriginOS/FuntouchOS", "Flyme", "OneUI", "MagicOS", "RealmeUI")
    private val isHeavySkin = RomUtils.getRomType() in heavySkinRoms
    private val maxDisplayWeight = if (isHeavySkin) 18 else 10
    private val compensationThreshold = 8
    
    private val initialPauseDuration = 1000L
    private val finalPauseDuration = 500L
    private val baseFocusDelay = 500L
    private val staticTimeReserve = 1500L

    // Adaptive scroll metrics
    private var lastLyricChangeTime: Long = 0
    private var lastLyricLength: Int = 0
    private val lyricDurations = mutableListOf<Long>()
    private val maxHistory = 5
    private var adaptiveDelay: Long = 1800L
    private val minCharDuration = 50L
    private val minScrollDelay = 200L
    private val maxScrollDelay = 5000L

    // Scrolling Mode Flags
    private var useSyllableScrolling = false
    private var useLrcScrolling = false
    private var currentParsedLines: List<OnlineLyricFetcher.LyricLine>? = null

    // Observers — debounce to coalesce rapid-fire updates
    private val lyricObserver = Observer<LyricRepository.LyricInfo?> { scheduleUpdate() }
    private val progressObserver = Observer<LyricRepository.PlaybackProgress?> { scheduleUpdate() }
    private val metadataObserver = Observer<LyricRepository.MediaInfo?> { scheduleUpdate() }

    private val parsedLyricsObserver = Observer<LyricRepository.ParsedLyricsInfo?> { parsedInfo ->
        if (parsedInfo != null && parsedInfo.hasSyllable) {
            useSyllableScrolling = true
            useLrcScrolling = false
            currentParsedLines = parsedInfo.lines
            AppLogger.getInstance().log(TAG, "🏝️ SuperIsland: Switched to SYLLABLE scrolling")
        } else if (parsedInfo != null && parsedInfo.lines.isNotEmpty()) {
            useSyllableScrolling = false
            useLrcScrolling = true
            currentParsedLines = parsedInfo.lines
            AppLogger.getInstance().log(TAG, "🏝️ SuperIsland: Switched to LRC scrolling")
        } else {
            useSyllableScrolling = false
            useLrcScrolling = false
            currentParsedLines = null
            AppLogger.getInstance().log(TAG, "🏝️ SuperIsland: Switched to WEIGHT scrolling")
        }
    }

    private val albumArtObserver = Observer<Bitmap?> { bitmap -> 
        if (bitmap == null) {
            cachedAlbumColor = 0xFF757575.toInt()
            lastAlbumArtPaletteHash = 0
            scheduleUpdate()
            return@Observer
        }
        val artHash = bitmap.hashCode()
        if (artHash != lastAlbumArtPaletteHash) {
            Palette.from(bitmap).generate { palette ->
                if (palette != null) {
                    cachedAlbumColor = palette.getVibrantColor(
                        palette.getMutedColor(
                            palette.getDominantColor(0xFF757575.toInt())
                        )
                    )
                    lastAlbumArtPaletteHash = artHash
                }
                scheduleUpdate()
            }
        } else {
            scheduleUpdate()
        }
    }

    private var pendingUpdate = false

    private val visualizerLoop = object : Runnable {
        override fun run() {
            if (!isRunning) return
            try {
                updateNotification()
                
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
                LogManager.getInstance().e(context, TAG, "CRASH in visualizer loop: $t")
                stop()
            }
        }
    }

    private val debouncedUpdate = Runnable {
        pendingUpdate = false
        // Update notification synchronously to reflect repository changes immediately
        updateNotification()
        
        // Also inform adaptive scrolling
        val currentLyric = LyricRepository.getInstance().liveLyric.value?.lyric ?: ""
        recordLyricChange(currentLyric)
        
        if (isRunning) {
            mainHandler.removeCallbacks(visualizerLoop)
            mainHandler.post(visualizerLoop)
        }
    }

    private fun scheduleUpdate() {
        if (!isRunning) return
        if (!pendingUpdate) {
            pendingUpdate = true
            // ⚡ Snappy debounce: 80ms to coalesce rapid meta/progress pings without feeling laggy
            mainHandler.postDelayed(debouncedUpdate, 80)
        }
    }

    init {
        createChannel()
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        val repo = LyricRepository.getInstance()
        repo.liveLyric.observeForever(lyricObserver)
        repo.liveProgress.observeForever(progressObserver)
        repo.liveAlbumArt.observeForever(albumArtObserver)
        repo.liveMetadata.observeForever(metadataObserver)
        repo.liveParsedLyrics.observeForever(parsedLyricsObserver)

        cachedClickStyle = prefs.getString("notification_click_style", "default") ?: "default"
        cachedSuperIslandTextColorEnabled = prefs.getBoolean("super_island_text_color_enabled", false)
        cachedSuperIslandShareEnabled = prefs.getBoolean("super_island_share_enabled", true)
        cachedSuperIslandShareFormat = prefs.getString("super_island_share_format", "format_1") ?: "format_1"
        cachedProgressBarColorEnabled = prefs.getBoolean("progress_bar_color_enabled", false)
        cachedActionStyle = prefs.getString("notification_actions_style", "disabled") ?: "disabled"
        cachedDisableScrolling = prefs.getBoolean("disable_lyric_scrolling", false)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        lastLyric = ""
        lastProgress = -1
        lastAlbumArtHash = 0
        lastPicAppHash = 0
        lastPicActionsHash = 0
        cachedPicsBundle = null
        cachedActionsBundle = null
        lastSubText = ""
        cachedAlbumColor = 0xFF757575.toInt()
        lastAppliedAlbumColor = cachedAlbumColor

        // Build the notification ONCE and send as foreground
        buildInitialNotification()
        
        mainHandler.post(visualizerLoop)
        AppLogger.getInstance().log(TAG, "🏝️ SuperIslandHandler started")
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false

        val repo = LyricRepository.getInstance()
        repo.liveLyric.removeObserver(lyricObserver)
        repo.liveProgress.removeObserver(progressObserver)
        repo.liveAlbumArt.removeObserver(albumArtObserver)
        repo.liveMetadata.removeObserver(metadataObserver)
        repo.liveParsedLyrics.removeObserver(parsedLyricsObserver)

        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)

        mainHandler.removeCallbacks(debouncedUpdate)
        mainHandler.removeCallbacks(visualizerLoop)
        manager?.cancel(NOTIFICATION_ID)
        cachedNotification = null
        cachedBuilder = null
        AppLogger.getInstance().log(TAG, "🏝️ SuperIslandHandler stopped")
    }

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

    // ── Build notification ONCE, then reuse ──

    fun forceUpdateNotification() {
        if (!isRunning) return
        doUpdate(force = true)
    }

    private fun buildInitialNotification() {
        val repo = LyricRepository.getInstance()
        val lyric = repo.liveLyric.value?.lyric ?: ""
        val metadata = repo.liveMetadata.value
        val title = metadata?.title ?: ""
        val artist = metadata?.artist ?: ""
        val subText = if (artist.isNotBlank()) "$title - $artist" else title
        val albumArt = repo.liveAlbumArt.value
        val progressInfo = repo.liveProgress.value
        val progressPercent = if (progressInfo != null && progressInfo.duration > 0) {
            ((progressInfo.position.toFloat() / progressInfo.duration.toFloat()) * 100).toInt()
                .coerceIn(0, 100)
        } else 0

        lastLyric = lyric
        lastProgress = progressPercent
        lastAlbumArtHash = albumArt?.hashCode() ?: 0
        lastSubText = subText
        lastAppliedAlbumColor = cachedAlbumColor
        lastMetadata = metadata

        isFirstNotification = true
        scrollState = ScrollState.INITIAL_PAUSE
        initialPauseStartTime = System.currentTimeMillis()
        scrollOffset = 0
        lastLyricText = ""
        
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(lyric.ifEmpty { "Capsulyric" })
            .setContentText(subText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(if (cachedActionStyle == "media_controls") 0xFF757575.toInt() else cachedAlbumColor)  // Neutral gray for T12 ring; album color for T7 progress bar

        // ⚡ OPTIMIZATION: Populate island params immediately so the system UI 
        // recognizes this as an island notification from the first frame.
        val displayLyric = if (lyric.isNotBlank()) {
             extractByWeight(lyric, 0, maxDisplayWeight)
        } else "♪"
        
        val islandParams = buildIslandParamsJson(
            displayLyric = displayLyric,
            fullLyric = lyric,
            subText = subText,
            progressPercent = progressPercent,
            title = title,
            artist = artist
        )
        builder.extras.putString("miui.focus.param", islandParams)

        cachedBuilder = builder
        applyPicsAndActions(metadata, albumArt, null)

        // Set the appropriate ContentIntent based on preference
        builder.setContentIntent(createContentIntent())
        val notification = builder.build()
        cachedNotification = notification

        service.startForeground(NOTIFICATION_ID, notification)
    }

    private fun applyPicsAndActions(metadata: LyricRepository.MediaInfo?, albumArt: Bitmap?, notification: Notification?) {
        val targetExtras = notification?.extras ?: cachedBuilder?.extras ?: return

        // ── Detect Album Art Changes ──
        val albumArtHash = albumArt?.hashCode() ?: 0
        if (albumArtHash != lastAlbumArtHash || cachedPicsBundle == null) {
            val pics = Bundle()
            if (albumArt != null) {
                // Scaling is expensive - do it once per change
                pics.putParcelable("miui.focus.pic_avatar", Icon.createWithBitmap(scaleBitmap(albumArt, 480)))
                pics.putParcelable("miui.focus.pic_island", Icon.createWithBitmap(scaleBitmap(albumArt, 120)))
                pics.putParcelable("miui.land.pic_island", Icon.createWithBitmap(scaleBitmap(albumArt, 88)))
                pics.putParcelable("miui.focus.pic_share", Icon.createWithBitmap(scaleBitmap(albumArt, 224)))
            }
            
            val appIcon = getAppIcon(metadata?.packageName)
            if (appIcon != null) {
                pics.putParcelable("miui.focus.pic_app", Icon.createWithBitmap(scaleBitmap(appIcon, 96)))
            }
            cachedPicsBundle = pics
            lastAlbumArtHash = albumArtHash
            lastPicAppHash = metadata?.packageName?.hashCode() ?: 0
        } else if (metadata?.packageName?.hashCode() != lastPicAppHash) {
            // App icon changed but album art didn't
            val pics = Bundle(cachedPicsBundle)
            val appIcon = getAppIcon(metadata?.packageName)
            if (appIcon != null) {
                pics.putParcelable("miui.focus.pic_app", Icon.createWithBitmap(scaleBitmap(appIcon, 96)))
            }
            cachedPicsBundle = pics
            lastPicAppHash = metadata?.packageName?.hashCode() ?: 0
        }

        // ── Detect Media Control Icon Changes ──
        // Note: Template 12's type=1 (progress ring) button colors are system-hardcoded for
        // third-party apps — neither JSON color fields nor notification.color can override them.
        // We use type=0 (simple circle) instead, drawing the background color into the icon
        // bitmap itself, which is the only reliable way to control button appearance.
        val isPlaying = LyricRepository.getInstance().isPlaying.value ?: false
        val actionsHash = if (cachedActionStyle == "media_controls") (if (isPlaying) 1 else 0) else -1

        if (actionsHash != lastPicActionsHash || cachedPicsBundle?.containsKey("miui.focus.pic_btn_play_pause") == false) {
            if (cachedActionStyle == "media_controls") {
                // Draw both icon + dark background into the bitmap — the only reliable path
                // for consistent button colors on HyperOS
                val playPauseResId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                val playPauseIconBitmap = renderButtonIcon(playPauseResId, 96, 0.6f, "#1A1A1A")
                val nextIconBitmap = renderButtonIcon(R.drawable.ic_skip_next, 96, 0.5f, "#333333")

                cachedPicsBundle?.let {
                    it.putParcelable("miui.focus.pic_btn_play_pause", Icon.createWithBitmap(playPauseIconBitmap))
                    it.putParcelable("miui.focus.pic_btn_next", Icon.createWithBitmap(nextIconBitmap))
                }
            } else {
                cachedPicsBundle?.remove("miui.focus.pic_btn_play_pause")
                cachedPicsBundle?.remove("miui.focus.pic_btn_next")
            }
            cachedActionsBundle = null
            lastPicActionsHash = actionsHash
        }

        // ── Apply to Extras ──
        cachedPicsBundle?.let { targetExtras.putBundle("miui.focus.pics", it) }
        // Explicitly remove any stale actions Bundle to prevent Method 1 from interfering
        targetExtras.remove("miui.focus.actions")
    }

    // ── Update: modify cached notification extras in-place ──

    private fun doUpdate(force: Boolean = false) {
        if (!isRunning) return
        if (force) {
            isFirstNotification = true
        }
        updateNotification()
    }

    private fun updateNotification() {
        if (!isRunning) return
        val notification = cachedNotification ?: return

        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 50) return
        lastUpdateTime = now

        val repo = LyricRepository.getInstance()
        val lyricInfo = repo.liveLyric.value
        val currentLyric = lyricInfo?.lyric ?: ""
        val progressInfo = repo.liveProgress.value
        val currentPosition = progressInfo?.position ?: 0L
        val metadata = repo.liveMetadata.value
        val albumArt = repo.liveAlbumArt.value

        val title = metadata?.title ?: ""
        val artist = metadata?.artist ?: ""
        val subText = if (artist.isNotBlank()) "$title - $artist" else title

        val progressPercent = if (progressInfo != null && progressInfo.duration > 0) {
            ((progressInfo.position.toFloat() / progressInfo.duration.toFloat()) * 100).toInt()
                .coerceIn(0, 100)
        } else 0

        var displayLyric: String
        
        if (cachedDisableScrolling) {
            val currentLine = if (currentParsedLines != null) findCurrentLine(currentParsedLines!!, currentPosition) else null
            if (currentLine != null) {
                displayLyric = extractByWeight(currentLine.text, 0, maxDisplayWeight)
            } else {
                displayLyric = extractByWeight(currentLyric, 0, maxDisplayWeight)
            }
            scrollState = ScrollState.DONE
            
        } else if (useSyllableScrolling && currentParsedLines != null) {
            val currentLine = findCurrentLine(currentParsedLines!!, currentPosition)
            if (currentLine != null && !currentLine.syllables.isNullOrEmpty()) {
                val sungSyllables = currentLine.syllables.filter { it.startTime <= currentPosition }
                val unsungSyllables = currentLine.syllables.filter { it.startTime > currentPosition }
                val sungText = sungSyllables.joinToString("") { it.text }
                val unsungText = unsungSyllables.joinToString("") { it.text }
                displayLyric = calculateSyllableWindow(sungText, unsungText)
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
                    val minVisibleWeight = 16
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

        val islandParams = buildIslandParamsJson(displayLyric, currentLyric, subText, progressPercent, title, artist)
        
        // ⚡ CHANGE DETECTION: Skip notify if nothing visible changed
        val oldParams = notification.extras.getString("miui.focus.param")
        if (oldParams == islandParams && !isFirstNotification) {
            return
        }

        applyPicsAndActions(metadata, albumArt, notification)
        notification.extras.putString("miui.focus.param", islandParams)

        // Restore album color for all templates — previously attempted to branch on Template 12
        // to fix ring color, but notification.color has no effect on Template 12 ring either.
        notification.color = cachedAlbumColor

        // Update base notification text fields
        notification.extras.putString(Notification.EXTRA_TITLE, currentLyric.ifEmpty { "Capsulyric" })
        notification.extras.putString(Notification.EXTRA_TEXT, subText)

        // Update ContentIntent if it changed (or just refresh it)
        notification.contentIntent = createContentIntent()

        manager?.notify(NOTIFICATION_ID, notification)
        isFirstNotification = false
    }

    // ── Scrolling Helper Math ──
    private fun recordLyricChange(newLyric: String) {
        val now = System.currentTimeMillis()
        if (lastLyricChangeTime == 0L) {
            lastLyricChangeTime = now
            lastLyricLength = newLyric.length
            return
        }
        val duration = now - lastLyricChangeTime
        val avgCharDuration = if (lastLyricLength > 0) duration / lastLyricLength else 0
        if (avgCharDuration < minCharDuration || duration > 30000) {
            lastLyricChangeTime = now
            lastLyricLength = newLyric.length
            return
        }
        lyricDurations.add(duration)
        if (lyricDurations.size > maxHistory) {
            lyricDurations.removeAt(0)
        }
        lastLyricChangeTime = now
        lastLyricLength = newLyric.length
        calculateAdaptiveDelay()
    }

    private fun calculateAdaptiveDelay() {
        if (lyricDurations.isEmpty()) { adaptiveDelay = 1800L; return }
        val avgDuration = lyricDurations.average().toLong()
        val avgLyricWeight = calculateWeight(lastLyricText)
        val currentStaticReserve = if (isMostlyWestern(lastLyricText)) 750L else staticTimeReserve
        if (avgLyricWeight == 0 || avgDuration < currentStaticReserve) { adaptiveDelay = 1800L; return }
        val estimatedSteps = maxOf(1, (avgLyricWeight / 5))
        val availableTime = avgDuration - currentStaticReserve - (estimatedSteps * baseFocusDelay)
        val timePerUnit = if (availableTime > 0 && avgLyricWeight > 0) availableTime / avgLyricWeight else 100L
        val calculatedDelay = baseFocusDelay + (timePerUnit * 5)
        adaptiveDelay = calculatedDelay.coerceIn(minScrollDelay, maxScrollDelay)
    }

    private fun charWeight(c: Char): Int = when (Character.UnicodeBlock.of(c)) {
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
        Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
        Character.UnicodeBlock.HIRAGANA,
        Character.UnicodeBlock.KATAKANA,
        Character.UnicodeBlock.HANGUL_SYLLABLES -> 2
        else -> 1
    }

    private fun calculateWeight(text: String): Int = text.sumOf { charWeight(it) }

    private fun isMostlyWestern(text: String): Boolean {
        if (text.isEmpty()) return false
        val cjkCount = text.count { charWeight(it) == 2 }
        return cjkCount <= text.length / 2
    }

    private fun extractByWeight(text: String, startWeight: Int, maxWeight: Int): String {
        var currentWeight = 0
        var startIndex = 0
        var endIndex = 0
        for (i in text.indices) {
            if (currentWeight >= startWeight) { startIndex = i; break }
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

    private fun calculateSmartShiftWeight(text: String, currentOffset: Int): Int {
        val segment = extractByWeight(text, currentOffset, 10)
        if (segment.isEmpty()) return 4
        val isCJK = segment.count { charWeight(it) == 2 } > segment.length / 2
        return if (isCJK) {
            if (segment.length >= 2) charWeight(segment[0]) + charWeight(segment[1]) else 4
        } else {
            val spaceIndex = segment.indexOf(' ', 2)
            if (spaceIndex in 2..4) calculateWeight(segment.take(spaceIndex + 1))
            else if (segment.length >= 3) calculateWeight(segment.take(3)) else 3
        }
    }

    private fun findSnapOffset(text: String, targetWeight: Int): Int {
        var currentWeight = 0
        var lastSnapPoint = 0
        for (i in text.indices) {
            val c = text[i]
            val w = charWeight(c)
            if (currentWeight + w > targetWeight) return lastSnapPoint
            currentWeight += w
            if (Character.isWhitespace(c)) lastSnapPoint = currentWeight
        }
        return lastSnapPoint
    }

    private fun findCurrentLine(lines: List<OnlineLyricFetcher.LyricLine>, position: Long): OnlineLyricFetcher.LyricLine? {
        for (line in lines) {
            if (position >= line.startTime && position < line.endTime) return line
        }
        return null
    }

    private fun calculateSnappedScroll(text: String, targetScroll: Int, maxScroll: Int): Int {
        val snapScroll = findSnapOffset(text, targetScroll)
        val effectiveScroll = if (targetScroll - snapScroll > 12) targetScroll else snapScroll
        return minOf(effectiveScroll, maxScroll)
    }

    private fun calculateSyllableWindow(sung: String, unsung: String): String {
        val totalWeight = calculateWeight(sung) + calculateWeight(unsung)
        if (totalWeight <= maxDisplayWeight) return sung + unsung
        val sungWeight = calculateWeight(sung)
        val scrollStartThreshold = 8
        val fullText = sung + unsung
        val targetScrollAmount = if (sungWeight < scrollStartThreshold) 0 else sungWeight - scrollStartThreshold
        val minVisibleWeight = 14
        val maxAllowedScroll = maxOf(0, totalWeight - minVisibleWeight)
        val finalScrollAmount = calculateSnappedScroll(fullText, targetScrollAmount, maxAllowedScroll)
        return extractByWeight(fullText, finalScrollAmount, maxDisplayWeight)
    }

    private fun calculateAdaptiveScroll(currentLyric: String, totalWeight: Int): String {
        if (currentLyric != lastLyricText) {
            lastLyricText = currentLyric
            scrollOffset = 0
            scrollState = ScrollState.INITIAL_PAUSE
            initialPauseStartTime = System.currentTimeMillis()
        }
        if (totalWeight <= maxDisplayWeight) {
            scrollState = ScrollState.DONE
            return currentLyric
        }
        return when (scrollState) {
            ScrollState.INITIAL_PAUSE -> {
                val requiredPause = if (isMostlyWestern(currentLyric)) 500L else initialPauseDuration
                if (System.currentTimeMillis() - initialPauseStartTime >= requiredPause) {
                    scrollState = ScrollState.SCROLLING
                }
                extractByWeight(currentLyric, 0, maxDisplayWeight)
            }
            ScrollState.SCROLLING -> {
                val remainingWeight = totalWeight - scrollOffset
                if (remainingWeight <= compensationThreshold || remainingWeight <= maxDisplayWeight) {
                    scrollState = ScrollState.FINAL_PAUSE
                    initialPauseStartTime = System.currentTimeMillis()
                    extractByWeight(currentLyric, scrollOffset, if (remainingWeight <= compensationThreshold) remainingWeight else maxDisplayWeight)
                } else {
                    val ret = extractByWeight(currentLyric, scrollOffset, maxDisplayWeight)
                    scrollOffset += calculateSmartShiftWeight(currentLyric, scrollOffset)
                    ret
                }
            }
            ScrollState.FINAL_PAUSE -> {
                val requiredPause = if (isMostlyWestern(currentLyric)) 250L else finalPauseDuration
                if (System.currentTimeMillis() - initialPauseStartTime >= requiredPause) {
                    scrollState = ScrollState.DONE
                }
                extractByWeight(currentLyric, scrollOffset, maxOf(totalWeight - scrollOffset, maxDisplayWeight))
            }
            ScrollState.DONE -> {
                extractByWeight(currentLyric, scrollOffset, maxOf(totalWeight - scrollOffset, maxDisplayWeight))
            }
        }
    }

    /**
     * Build the miui.focus.param JSON string for Template 7.
     */
    private fun buildIslandParamsJson(
        displayLyric: String,
        fullLyric: String,
        subText: String,
        progressPercent: Int,
        title: String,
        artist: String
    ): String {
        val root = JSONObject()
        val paramV2 = JSONObject()

        val hexColor = String.format("#FF%06X", 0xFFFFFF and cachedAlbumColor)
        val showHighlightColor = cachedSuperIslandTextColorEnabled 
        val ringColor = if (showHighlightColor) hexColor else "#757575"

        // ── Notification attributes ──
        paramV2.put("protocol", 1)
        paramV2.put("business", "lyric_display")
        paramV2.put("enableFloat", false)
        paramV2.put("updatable", true)
        paramV2.put("islandFirstFloat", false)

        // ── 息屏 AOD ──
        paramV2.put("aodTitle", displayLyric.take(20).ifEmpty { "♪" })

        // 展开态模版配置
        val progressBarColor = if (cachedProgressBarColorEnabled) hexColor else "#757575"

        if (cachedActionStyle == "media_controls") {
            // 模版12: IM图文组件 + 按钮组件1
            paramV2.put("template", 12)
            
            val chatInfo = JSONObject()
            chatInfo.put("picProfile", "miui.focus.pic_avatar")
            // Optimize text: put artist info as a hint if title is empty
            chatInfo.put("title", fullLyric.ifEmpty { title.ifEmpty { "♪" } }) 
            chatInfo.put("content", subText)
            val playbackPkg = lastMetadata?.packageName ?: context.packageName
            chatInfo.put("appIconPkg", playbackPkg)  // Standard field per chatInfo spec
            paramV2.put("chatInfo", chatInfo)

            val actionsArray = org.json.JSONArray()
            
            // Buttons — type=0 (simple circle) only: ring type=1 colors are system-hardcoded
            // for third-party apps and cannot be overridden. Background is baked into the bitmap.
            val playPauseUri = Intent("com.example.islandlyrics.ACTION_MEDIA_PLAY_PAUSE")
                .setPackage(context.packageName)
                .toUri(Intent.URI_INTENT_SCHEME)
            val nextUri = Intent("com.example.islandlyrics.ACTION_MEDIA_NEXT")
                .setPackage(context.packageName)
                .toUri(Intent.URI_INTENT_SCHEME)

            val btnPlay = JSONObject()
            btnPlay.put("actionIcon", "miui.focus.pic_btn_play_pause")
            btnPlay.put("type", 0)  // Simple circle — background color baked into bitmap
            btnPlay.put("actionIntentType", 2)
            btnPlay.put("actionIntent", playPauseUri)
            actionsArray.put(btnPlay)

            val btnNext = JSONObject()
            btnNext.put("actionIcon", "miui.focus.pic_btn_next")
            btnNext.put("type", 0)
            btnNext.put("actionIntentType", 2)
            btnNext.put("actionIntent", nextUri)
            actionsArray.put(btnNext)

            paramV2.put("actions", actionsArray)

        } else {
            // 展开态模版7: chatInfo (IM图文) + picInfo (识别图形) + progressInfo (进度)
            paramV2.put("template", 7)

            val chatInfo = JSONObject()
            chatInfo.put("picProfile", "miui.focus.pic_avatar")
            chatInfo.put("title", fullLyric.ifEmpty { title.ifEmpty { "♪" } })
            chatInfo.put("content", subText)
            val playbackPkg = lastMetadata?.packageName ?: context.packageName
            chatInfo.put("appIconPkg", playbackPkg)
            chatInfo.put("picApp", "miui.focus.pic_app")
            paramV2.put("chatInfo", chatInfo)
            
            val picInfo = JSONObject()
            picInfo.put("type", 1)
            picInfo.put("pic", "miui.focus.pic_app")
            paramV2.put("picInfo", picInfo)

            val progressInfo = JSONObject()
            progressInfo.put("progress", progressPercent)
            progressInfo.put("colorProgress", progressBarColor)
            progressInfo.put("colorProgressEnd", progressBarColor)
            progressInfo.put("colorBackground", "#ffffffff")
            paramV2.put("progressInfo", progressInfo)
        }

        // ── 岛数据 (param_island) ──
        // 摘要态模版2: 图文组件1 (A区) + 文本组件 (B区)
        val paramIsland = JSONObject()
        paramIsland.put("islandProperty", 1)
        if (showHighlightColor) {
            paramIsland.put("highlightColor", hexColor)
        }

        val bigIslandArea = JSONObject()

        // A区: 图文组件1 — album art icon + song title
        // NOTE: Standard Template 1 only supports picInfo, so we use it for maximum visibility
        val imageTextInfoLeft = JSONObject()
        imageTextInfoLeft.put("type", 1)
        
        val picInfoA = JSONObject()
        picInfoA.put("type", 1)
        picInfoA.put("pic", "miui.focus.pic_island")
        imageTextInfoLeft.put("picInfo", picInfoA)
        
        // A区 text: song title
        val textInfoA = JSONObject()
        textInfoA.put("title", title.ifEmpty { "♪" })
        textInfoA.put("showHighlightColor", showHighlightColor)
        imageTextInfoLeft.put("textInfo", textInfoA)
        bigIslandArea.put("imageTextInfoLeft", imageTextInfoLeft)

        // B区: 文本组件 — 歌词作为正文大字
        val textInfo = JSONObject()
        textInfo.put("title", displayLyric.ifEmpty { "♪" })  // 正文大字: 滚动歌词片段
        textInfo.put("showHighlightColor", showHighlightColor)
        textInfo.put("narrowFont", false)
        bigIslandArea.put("textInfo", textInfo)

        paramIsland.put("bigIslandArea", bigIslandArea)

        // 分享数据 (shareData)
        if (cachedSuperIslandShareEnabled) {
            val shareData = JSONObject()
            shareData.put("pic", "miui.focus.pic_share")
            shareData.put("title", title.ifEmpty { "♪" })
            shareData.put("content", fullLyric.ifEmpty { "♪" })
            val shareArtist = if (artist.isNotBlank()) artist else "未知歌手"
            val shareSong = title.ifEmpty { "未知歌曲" }
            
            val shareContent = when (cachedSuperIslandShareFormat) {
                "format_2" -> "$fullLyric -$shareArtist，$shareSong"
                "format_3" -> "$fullLyric\n$shareArtist，$shareSong"
                else -> "$fullLyric\n$shareSong by $shareArtist"
            }
            shareData.put("shareContent", shareContent)
            paramIsland.put("shareData", shareData)
        }

        // 小岛: album art thumbnail with progress ring (using miui.land key)
        val smallIslandArea = JSONObject()
        val combinePicInfoSmall = JSONObject()
        val picInfoSmall = JSONObject()
        picInfoSmall.put("type", 1)
        picInfoSmall.put("pic", "miui.land.pic_island")
        combinePicInfoSmall.put("picInfo", picInfoSmall)
        
        val ringInfoSmall = JSONObject()
        ringInfoSmall.put("progress", progressPercent)
        ringInfoSmall.put("colorReach", ringColor)
        ringInfoSmall.put("colorProgress", ringColor)
        ringInfoSmall.put("colorUnReach", "#333333")
        combinePicInfoSmall.put("progressInfo", ringInfoSmall)
        
        smallIslandArea.put("combinePicInfo", combinePicInfoSmall)
        paramIsland.put("smallIslandArea", smallIslandArea)

        paramV2.put("param_island", paramIsland)

        root.put("param_v2", paramV2)
        return root.toString()
    }

    private fun renderButtonIcon(resourceId: Int, size: Int, iconScale: Float, bgColorHex: String? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        if (bgColorHex != null) {
            paint.color = android.graphics.Color.parseColor(bgColorHex)
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        }

        val drawable = context.getDrawable(resourceId) ?: return bitmap
        // Force white tint directly on the mutated drawable so it's applied when drawn.
        // (DrawableCompat.wrap returns a new wrapper — setting tint there then drawing the
        // original has no effect. mutate() + setTint() works in-place.)
        drawable.mutate().setTint(android.graphics.Color.WHITE)
        val iconSize = (size * iconScale).toInt()
        val margin = (size - iconSize) / 2
        drawable.setBounds(margin, margin, margin + iconSize, margin + iconSize)
        drawable.draw(canvas)

        return bitmap
    }

    private fun scaleBitmap(src: Bitmap, targetSize: Int): Bitmap {
        val scaled = if (src.width == targetSize && src.height == targetSize) src 
                     else Bitmap.createScaledBitmap(src, targetSize, targetSize, true)
                     
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())
        
        // 20% corner radius
        val cornerRadius = targetSize * 0.2f
        
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        
        return output
    }

    private fun getAppIcon(packageName: String?): Bitmap? {
        if (packageName == null) return null
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun createContentIntent(): PendingIntent {
        return if (cachedClickStyle == "media_controls") {
            // Option B: Open Media Controls (Transparent Activity)
            val intent = Intent(context, MediaControlActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            // Option A: Default (Open App)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    companion object {
        private const val TAG = "SuperIsland"
        private const val CHANNEL_ID = "lyric_capsule_channel"  // Shared with LyricCapsuleHandler
        private const val NOTIFICATION_ID = 1001  // Shared with LyricCapsuleHandler
    }
}
