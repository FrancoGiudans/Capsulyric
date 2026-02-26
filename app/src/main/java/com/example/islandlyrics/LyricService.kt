package com.example.islandlyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Observer
import androidx.core.app.NotificationCompat
import com.hchen.superlyricapi.ISuperLyric
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.ArrayList

class LyricService : Service() {

    private var lastUpdateTime = 0L

    // To debounce updates
    private var lastLyric = ""
    
    // Online lyric fetcher
    private val onlineLyricFetcher = OnlineLyricFetcher()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var fetchJob: Job? = null

    private val lyricObserver = Observer<LyricRepository.LyricInfo?> { info ->
        if (info != null && info.lyric.isNotBlank()) {
            val lyric = info.lyric
            
             // Check for static metadata masquerading as lyrics
            val metadata = LyricRepository.getInstance().liveMetadata.value
            val title = metadata?.title ?: ""
            val artist = metadata?.artist ?: ""
            val pkg = metadata?.packageName

            // Strict check: Lyric equals title, artist, or "Title - Artist" combo
            val isStatic = (lyric.equals(title, ignoreCase = true) || 
                           lyric.equals(artist, ignoreCase = true) ||
                           lyric.equals("$title - $artist", ignoreCase = true) ||
                           lyric.equals("$artist - $title", ignoreCase = true))

            if (isStatic) {
                AppLogger.getInstance().log(TAG, "⚠️ Detected static metadata as lyric: '$lyric'")
                
                // If this happens, check if we should fetch online
                // Note: We use a fallback rule if none exists (handled in Helper or here)
                if (pkg != null) {
                    val rule = ParserRuleHelper.getRuleForPackage(this, pkg) 
                               ?: ParserRuleHelper.createDefaultRule(pkg) // Fallback if no rule exists

                    if (rule.useOnlineLyrics) {
                        AppLogger.getInstance().log(TAG, "Static metadata detected -> Triggering online fetch")
                        tryFetchOnlineLyrics()
                        return@Observer // Don't show static text if fetching online
                    } else {
                        AppLogger.getInstance().log(TAG, "Static metadata detected but online fetch disabled")
                    }
                }
            }

            updateActiveHandler()
            if (!isSuperIslandMode) {
                capsuleHandler?.updateLyricImmediate(info.lyric)
            } else {
                // ⚡ ZERO LATENCY: Force update island immediately on lyric change
                superIslandHandler?.forceUpdateNotification()
            }
        } else {
            // Stop both if lyric is empty
            if (superIslandHandler?.isRunning == true) superIslandHandler?.stop()
            if (capsuleHandler?.isRunning() == true) capsuleHandler?.stop()
        }
    }
    
    // Delayed Stop Logic
    private val delayedStopRunnable = Runnable {
        if (isSuperIslandMode) {
            if (superIslandHandler?.isRunning == true) {
                superIslandHandler?.stop()
                AppLogger.getInstance().log(TAG, "🛑 SuperIsland stopped: Playback stopped (Delayed)")
            }
        } else {
            if (capsuleHandler?.isRunning() == true) {
                capsuleHandler?.stop()
                AppLogger.getInstance().log(TAG, "🛑 Capsule stopped: Playback stopped (Delayed)")
            }
        }
    }

    private val playbackObserver = Observer<Boolean> { playing ->
        if (playing) {
            // Cancel any pending stop
            handler.removeCallbacks(delayedStopRunnable)

            // Resume/Start active handler if we have valid lyrics
            updateActiveHandler()
            // Start progress tracking (merged from second observer)
            startProgressUpdater()
        } else {
            // Stop progress tracking (merged from second observer)
            stopProgressUpdater()
            
            // Debounce: Cancel pending stop to restart timer if we get multiple 'false' events
            handler.removeCallbacks(delayedStopRunnable)

            // Get delay preference
            val prefs = getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
            val delay = prefs.getLong("notification_dismiss_delay", 0L)
            
            AppLogger.getInstance().log(TAG, "🛑 Playback stopped. Delay=$delay ms")

            if (delay > 0) {
                 AppLogger.getInstance().log(TAG, "⏳ Scheduling handler stop in ${delay}ms")
                 handler.postDelayed(delayedStopRunnable, delay)
            } else {
                 // Immediate stop
                 if (isSuperIslandMode) {
                     if (superIslandHandler?.isRunning == true) {
                         superIslandHandler?.stop()
                         AppLogger.getInstance().log(TAG, "🛑 SuperIsland stopped immediately (Delay=0)")
                     }
                 } else {
                     if (capsuleHandler?.isRunning() == true) {
                         capsuleHandler?.stop()
                         AppLogger.getInstance().log(TAG, "🛑 Capsule stopped immediately (Delay=0)")
                     }
                }
            }
        }
    }

    private val superLyricStub = object : ISuperLyric.Stub() {
        override fun onStop(data: SuperLyricData?) {
            if (BuildConfig.DEBUG) {
                AppLogger.getInstance().d(TAG, "API onStop: ${data?.packageName}")
            }
            LyricRepository.getInstance().updatePlaybackStatus(false)
        }

        override fun onSuperLyric(data: SuperLyricData?) {
            if (BuildConfig.DEBUG) {
                AppLogger.getInstance().d(TAG, "API onSuperLyric received: ${data?.lyric?.take(50)}")
            }

            if (data != null) {
                val pkg = data.packageName
                val lyric = data.lyric
                
                // Check per-app settings (with Default Fallback)
                val rule = ParserRuleHelper.getRuleForPackage(this@LyricService, pkg) 
                           ?: ParserRuleHelper.createDefaultRule(pkg)

                if (!rule.useSuperLyricApi) {
                    if (BuildConfig.DEBUG) {
                        AppLogger.getInstance().d(TAG, "[$pkg] SuperLyric API disabled for this app")
                    }
                    // Don't fetch here - metadata observer already handles it for non-CarProtocol apps
                    return
                }

                // Instrumental Filter
                if (lyric.matches(".*(纯音乐|Instrumental|No lyrics|请欣赏|没有歌词).*".toRegex())) {
                    if (BuildConfig.DEBUG) {
                        AppLogger.getInstance().d(TAG, "Instrumental detected: $lyric")
                    }
                    val appName = getAppName(pkg)
                    LyricRepository.getInstance().updateLyric("", appName)
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                    return
                }

                if (lyric == lastLyric) {
                    if (BuildConfig.DEBUG) {
                        AppLogger.getInstance().d(TAG, "Duplicate lyric ignored")
                    }
                    return
                }
                lastLyric = lyric

                val appName = getAppName(pkg)

                // Update Repository -> This triggers the Observer -> Notification
                LyricRepository.getInstance().updateLyric(lyric, appName)
                
                // If online lyrics enabled for this app AND SuperLyric doesn't provide syllable info
                if (rule.useOnlineLyrics && !lyric.contains("<")) {
                    if (BuildConfig.DEBUG) {
                        AppLogger.getInstance().d(TAG, "[$pkg] SuperLyric无逐字信息，尝试在线获取")
                    }
                    tryFetchOnlineLyrics()
                }
            } else {
                if (BuildConfig.DEBUG) {
                    AppLogger.getInstance().d(TAG, "API onSuperLyric received NULL data")
                }
            }
        }
    }

    private var currentPosition = 0L
    private var duration = 0L
    private var isPlaying = false
    private var lastLineIndex = -1
    private val handler = Handler(Looper.getMainLooper())
    private val updateTask = object : Runnable {
        private var logCounter = 0
        
        override fun run() {
            if (isPlaying) {
                logCounter++
                val shouldLog = (logCounter % 10 == 0)
                
                if (shouldLog) {
                    AppLogger.getInstance().log(TAG, "🔄 updateTask running...")
                }
                
                updateProgressFromController(shouldLog)
                
                // Drive Lyric Updates if we have parsed lyrics
                val repo = LyricRepository.getInstance()
                val parsedLines = repo.liveParsedLyrics.value?.lines
                if (!parsedLines.isNullOrEmpty()) {
                     updateCurrentLyricLine(parsedLines)
                }
                
                // ⚡ Optimized frequency: 120ms for high responsiveness
                handler.postDelayed(this, 120)
            } else {
                AppLogger.getInstance().log(TAG, "⏸️ updateTask stopped (isPlaying=false)")
            }
        }
    }
    
    private fun updateCurrentLyricLine(lines: List<OnlineLyricFetcher.LyricLine>) {
        // Find current line based on position + offset (e.g. 500ms lookahead?)
        // Standard: Find last line where startTime <= position
        val position = currentPosition
        var index = -1
        
        for (i in lines.indices) {
            val line = lines[i]
            if (position >= line.startTime) {
                index = i
            } else {
                break
            }
        }
        
        if (index != -1 && index != lastLineIndex) {
            lastLineIndex = index
            val line = lines[index]
            
            // Update Repository
            val metadata = LyricRepository.getInstance().liveMetadata.value
            // Use actual app name (safe cached call)
            val source = metadata?.packageName?.let { getAppName(it) } ?: "Online Lyrics"
            
            LyricRepository.getInstance().updateLyric(line.text, source)
            LyricRepository.getInstance().updateCurrentLine(line)
        }
    }

    private var currentChannelId = CHANNEL_ID

    private var hasOpenedSettings = false
    // Toggle for Chip Keep-Alive Hack
    private var invisibleToggle = false
    private var capsuleHandler: LyricCapsuleHandler? = null
    private var superIslandHandler: SuperIslandHandler? = null
    private var isSuperIslandMode = false

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == "super_island_enabled") {
            isSuperIslandMode = p.getBoolean(key, false)
            AppLogger.getInstance().log(TAG, "Mode Switched via Settings -> isSuperIslandMode: $isSuperIslandMode")
            updateActiveHandler()
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Load mode from preferences
        isSuperIslandMode = getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
            .getBoolean("debug_super_island_enabled", false)

        createNotificationChannel()
        capsuleHandler = LyricCapsuleHandler(this, this)
        superIslandHandler = SuperIslandHandler(this, this)
        
        // Always register SuperLyric for detection (check per-app settings in callback)
        SuperLyricTool.registerSuperLyric(this, superLyricStub)
        if (BuildConfig.DEBUG) {
            AppLogger.getInstance().d(TAG, "SuperLyric API: Registered for detection")
        }

        getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)

        // Observe Repository
        val repo = LyricRepository.getInstance()
        repo.liveLyric.observeForever(lyricObserver)
        repo.isPlaying.observeForever(playbackObserver)  // Controls capsule lifecycle + progress tracking

        repo.liveMetadata.observeForever { info ->
            if (info != null) {
                if (info.duration > 0) {
                    duration = info.duration
                }
                
                // Reset parsed lyrics on song change
                LyricRepository.getInstance().updateParsedLyrics(emptyList(), false)
                LyricRepository.getInstance().updateCurrentLine(null)
                lastLineIndex = -1

                // Proactive online lyric fetching logic
                val rule = ParserRuleHelper.getRuleForPackage(this, info.packageName)
                           ?: ParserRuleHelper.createDefaultRule(info.packageName)
                
                AppLogger.getInstance().log(TAG, "Metadata Change: ${info.title} (${info.packageName}) | Rule: Active | Online: ${rule.useOnlineLyrics} | Car: ${rule.usesCarProtocol}")
                
                if (rule.useOnlineLyrics) {
                    // Strategy:
                    // 1. If app does NOT use Car Protocol (e.g. Kugou), it won't send lyrics via notification. Fetch immediately.
                    // 2. If app USES Car Protocol, it might send lyrics. Wait and check content.
                    
                    if (!rule.usesCarProtocol) {
                        AppLogger.getInstance().log(TAG, "[${info.packageName}] Non-CarProtocol app, triggering fetch...")
                        tryFetchOnlineLyrics()
                    } else {
                        AppLogger.getInstance().log(TAG, "[${info.packageName}] CarProtocol app, waiting for lyric observer trigger...")
                    }
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "null"
        AppLogger.getInstance().log(TAG, "onStartCommand Received Action: $action")

        // 1. Proactively sync the Super Island Mode preference
        val prefs = getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        isSuperIslandMode = prefs.getBoolean("super_island_enabled", false)

        // [Fix Task 1] Immediate Foreground Promotion
        createNotificationChannel()

        // 2. Ensure only the correct handler is running
        updateActiveHandler()

        // CRITICAL FIX: Prioritize the Rich Capsule Notification if it's already active
        // 3. Promote to foreground (avoid redundant channel creation)
        if (isSuperIslandMode && superIslandHandler?.isRunning == true) {
            superIslandHandler?.forceUpdateNotification()
        } else if (!isSuperIslandMode && capsuleHandler?.isRunning() == true) {
            // Ask the handler to repost its rich notification
            // This satisfies startForeground requirements without downgrading the UI
            capsuleHandler?.forceUpdateNotification()
        } else {
            // Only use fallback if service is starting up and no handler is ready yet
            if (action == "null" || action == "ACTION_START") {
                val currentInfo = LyricRepository.getInstance().liveLyric.value
                val title = currentInfo?.sourceApp ?: "Island Lyrics"
                val text = currentInfo?.lyric ?: "Initializing..."
                try {
                    startForeground(NOTIFICATION_ID, buildNotification(text, title, ""))
                } catch (e: Exception) {
                    AppLogger.getInstance().e(TAG, "Failed startForeground in fallback: ${e.message}")
                }
            }
        }

        if ("ACTION_STOP" == action) {
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        } else if ("ACTION_MEDIA_PAUSE" == action) {
            handleMediaPause()
            return START_STICKY
        } else if ("ACTION_MEDIA_PLAY" == action) {
            handleMediaPlay()
            return START_STICKY
        } else if ("ACTION_MEDIA_NEXT" == action) {
            handleMediaNext()
            return START_STICKY
        } else if ("ACTION_MEDIA_PREV" == action) {
            handleMediaPrev()
            return START_STICKY
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null // Not a bound service
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.getInstance().log(TAG, "Service Destroyed")
        capsuleHandler?.stop()
        
        // Only unregister if it was registered
        val prefs = getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        val useSuperLyricApi = prefs.getBoolean("use_superlyric_api", true)
        if (useSuperLyricApi) {
            // Unregistering manually is known to break future callbacks until app reboot
            // SuperLyric API handles automatic unregistration when binder dies
            if (BuildConfig.DEBUG) {
                AppLogger.getInstance().d(TAG, "SuperLyric API: Left to auto-unregister by module")
            }
        }
        
        getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener)
        LyricRepository.getInstance().liveLyric.removeObserver(lyricObserver)
        broadcastStatus("🔴 Stopped")
    }
    
    private fun tryFetchOnlineLyrics() {
        // Get current track metadata
        val metadata = LyricRepository.getInstance().liveMetadata.value
        val title = metadata?.title
        val artist = metadata?.artist
        val pkg = metadata?.packageName
        
        if (title.isNullOrBlank() || artist.isNullOrBlank() || pkg.isNullOrBlank()) {
            AppLogger.getInstance().log(TAG, "无法获取在线歌词：缺少元数据 (Title=$title, Artist=$artist, Pkg=$pkg)")
            return
        }
        
        // Check per-app online lyrics setting (with Default Fallback)
        val rule = ParserRuleHelper.getRuleForPackage(this, pkg)
                   ?: ParserRuleHelper.createDefaultRule(pkg)

        if (!rule.useOnlineLyrics) {
            AppLogger.getInstance().d(TAG, "Skip Fetch: Online lyrics disabled for $pkg")
            return
        }
        
        // This log is outside coroutine, keeping it or moving logic?
        // Let's keep it here as INFO so we know we decided to fetch.
        // The one inside coroutine (added in previous step) might be redundant but okay.
        AppLogger.getInstance().i(TAG, "🚀 PREPARING FETCH: $title - $artist [$pkg]")
        
        // Cancel any pending fetch to avoid race conditions (e.g. rapid song skipping)
        fetchJob?.cancel()

        // Launch coroutine to fetch lyrics
        fetchJob = serviceScope.launch {
            try {
                // Info: Start of fetch is important
                AppLogger.getInstance().i(TAG, "🚀 STARTING ONLINE FETCH: $title - $artist [$pkg]")
                
                val result = onlineLyricFetcher.fetchBestLyrics(title, artist)

                // CONSISTENCY CHECK: Ensure we are still playing the same song
                val currentMetadata = LyricRepository.getInstance().liveMetadata.value
                val currentTitle = currentMetadata?.title
                val currentArtist = currentMetadata?.artist
                
                if (currentTitle != title || currentArtist != artist) {
                    AppLogger.getInstance().i(TAG, "⚠️ Discarding stale lyric result (Song changed during fetch). Expected: $title, Found: $currentTitle")
                    return@launch
                }
                
                if (result != null && result.parsedLines != null && result.parsedLines.isNotEmpty()) {
                    AppLogger.getInstance().i(TAG, "在线歌词获取成功 [${result.api}] 逐字:${result.hasSyllable}")
                    
                    // Store parsed lyrics in repository
                    LyricRepository.getInstance().updateParsedLyrics(result.parsedLines, result.hasSyllable)
                    
                    // Reset line tracker
                    lastLineIndex = -1
                    AppLogger.getInstance().d(TAG, "在线歌词就绪，检查播放状态")
                    
                    // CRITICAL FIX: If music is already playing, start the progress updater
                    val repoPlaying = LyricRepository.getInstance().isPlaying.value
                    if (repoPlaying == true && !isPlaying) {
                        AppLogger.getInstance().d(TAG, "⚡ 音乐正在播放，启动进度追踪器")
                        startProgressUpdater()
                    } else if (repoPlaying == true && isPlaying) {
                        AppLogger.getInstance().d(TAG, "✅ 进度追踪器已运行")
                    } else {
                        AppLogger.getInstance().d(TAG, "⏸️ 音乐未播放，等待播放状态变化")
                    }
                } else {
                    AppLogger.getInstance().i(TAG, "在线歌词获取失败：无有效结果")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                     AppLogger.getInstance().d(TAG, "Online fetch cancelled (Song changed)")
                } else {
                     AppLogger.getInstance().e(TAG, "在线歌词获取异常: ${e.message}")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (true) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager != null) {
                // Unified Channel for all types (Capsule, Super Island, Fallback)
                val serviceChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Lyrics (Capsule / Super Island)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(serviceChannel)
            }
        }
    }

    private fun buildNotification(title: String, text: String, subText: String): Notification {
        return buildModernNotification(title, text, subText)
    }

    private fun buildModernNotification(title: String, text: String, subText: String): Notification {
        LogManager.getInstance().d(this, TAG, "Building Modern Notification (ProgressStyle)")

        val builder = Notification.Builder(this, currentChannelId)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )

        // Use ProgressStyle via Public API 36
        // 1. Create ProgressStyle
        val style = Notification.ProgressStyle()

        // 2. Set "StyledByProgress" (Critical for appearance)
        style.setStyledByProgress(true)

        // 3. Create a Segment (Required by internal logic)
        val segment = Notification.ProgressStyle.Segment(1)

        // 4. Set Segments List
        style.setProgressSegments(listOf(segment))

        // 5. Apply Style to Builder
        builder.style = style


        // Unified Attributes (Chip, Promotion)
        // [Task 3] Keep-Alive Hack & Real Lyrics
        // 1. Get current lyric (Fallback to Capsule name)
        val rawText = if (!text.isEmpty()) text else "Capsulyric"

        // 2. Toggle Invisible Char to force System UI update (Reset 6s timer)
        invisibleToggle = !invisibleToggle
        val chipText = rawText + if (invisibleToggle) "\u200B" else ""

        applyLiveAttributes(builder, chipText) // Uses Native Build methods

        val notification = builder.build()

        // Task 2: The "Desperate" Fallback
        applyPromotedFlagFallback(notification)

        return notification
    }



    private fun applyLiveAttributes(builder: Notification.Builder, text: String) {

        // 1. Set Status Chip Text
        builder.setShortCriticalText(text)

        // 2. Set Promoted Ongoing (Crucial for Chip visibility)
        // The method setRequestPromotedOngoing isn't exposed in standard Notification.Builder stubs,
        // so we set the exact extra manually.
        builder.extras.putBoolean("android.app.extra.REQUEST_PROMOTED_ONGOING", true)
    }

    private fun applyPromotedFlagFallback(notification: Notification) {
        notification.flags = notification.flags or Notification.FLAG_PROMOTED_ONGOING
    }

    private fun broadcastStatus(status: String) {
        val intent = Intent("com.example.islandlyrics.STATUS_UPDATE")
        intent.putExtra("status", status)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun broadcastLog(msg: String) {
        val intent = Intent("com.example.islandlyrics.DIAG_UPDATE")
        intent.putExtra("log_msg", msg)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun updateNotification(_title: String, _text: String, _subText: String) {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 500) return // Strict 500ms cap
        lastUpdateTime = now

        // DISABLED: LyricCapsuleHandler now handles all Capsule notifications
        // val notification = buildNotification(title, text, subText)
        // val nm = getSystemService(NotificationManager::class.java)
        // if (nm != null) {
        //     nm.notify(NOTIFICATION_ID, notification)
        //
        //     // Diagnostics & Channel Health
        //     checkAndHealChannel(nm)
        //
        //     // Inspect & Broadcast (API 36 Dashboard)
        //     inspectNotification(notification, nm)
        // }
        
        // Log for debugging but don't send notification
        LogManager.getInstance().d(this, TAG, "LyricService updateNotification called (skipped - using CapsuleHandler)")
    }

    internal fun inspectNotification(notification: Notification, nm: NotificationManager) {
        // Prepare Status Broadcast
        val intent = Intent("com.example.islandlyrics.STATUS_UPDATE")
        intent.setPackage(packageName)

        // 1. General Status
        val modeStatus = "🔵 Modern (API 36)"
        intent.putExtra("status", modeStatus)

        // 2. Promotable Characteristics (API 36)
        val hasChar = notification.hasPromotableCharacteristics()
        intent.putExtra("hasPromotable", hasChar)

        // 3. Ongoing Flag (API 36)
        val flagVal = Notification.FLAG_PROMOTED_ONGOING
        val isPromoted = (notification.flags and flagVal) != 0
        intent.putExtra("isPromoted", isPromoted)

        sendBroadcast(intent)

        // --- Detailed Logging (DIAG_UPDATE) ---
        // 4. System Permission
        val canPost = nm.canPostPromotedNotifications()

        // 5. Channel Status
        var channelStatus = "Unknown"
        if (true) {
            val channel = nm.getNotificationChannel(currentChannelId)
            if (channel != null) {
                channelStatus = "Imp: ${channel.importance}"
            }
        }

        val diagMsg = String.format(
            "[API] Perm: %b | Promotable: %b | Flag: %b\n[Channel] %s\n[Text] %s",
            canPost, hasChar, isPromoted, channelStatus, getSmartSnippet(notification.extras.getString(Notification.EXTRA_TITLE) ?: "")
        )
        broadcastLog(diagMsg)
    }
    
    // Helper to shorten text
    private fun getSmartSnippet(text: String): String {
        return if (text.length > 20) text.substring(0, 20) + "..." else text
    }

    private fun checkAndHealChannel(nm: NotificationManager) {
        if (true) {
            val channel = nm.getNotificationChannel(currentChannelId)
            if (channel != null) {
                // Rule: Must be IMPORTANCE_HIGH (4).
                // If DEFAULT(3) or Lower, it's demoted.
                if (channel.importance < NotificationManager.IMPORTANCE_HIGH) {
                    broadcastLog("⚠️ Channel Demoted (Imp=${channel.importance}). Resurrecting...")

                    // Resurrection Strategy: Delete & Recreate with new ID
                    // 1. Delete Old
                    nm.deleteNotificationChannel(currentChannelId)

                    // 2. Generate New ID
                    currentChannelId = CHANNEL_ID + "_" + System.currentTimeMillis()

                    // 3. Create New
                    createNotificationChannel()
                }
            }
        }
    }

    private fun startProgressUpdater() {
        AppLogger.getInstance().log(TAG, "▶️ startProgressUpdater() called, isPlaying=$isPlaying")
        if (!isPlaying) {
            isPlaying = true
            handler.post(updateTask)
            AppLogger.getInstance().log(TAG, "✅ Posted updateTask to handler")
        } else {
            AppLogger.getInstance().log(TAG, "⚠️ Already playing, skipping post")
        }
    }

    private fun stopProgressUpdater() {
        isPlaying = false
        handler.removeCallbacks(updateTask)
    }



    private fun updateActiveHandler() {
        val currentLyric = LyricRepository.getInstance().liveLyric.value
        val hasLyric = currentLyric != null && currentLyric.lyric.isNotBlank()
        val playing = LyricRepository.getInstance().isPlaying.value ?: false

        if (isSuperIslandMode) {
            // Ensure Capsule is STOPPED
            if (capsuleHandler?.isRunning() == true) {
                capsuleHandler?.stop()
            }
            // Start Island if playing and has lyric
            if (playing && hasLyric && superIslandHandler?.isRunning != true) {
                superIslandHandler?.start()
            }
        } else {
            // Ensure Island is STOPPED
            if (superIslandHandler?.isRunning == true) {
                superIslandHandler?.stop()
            }
            // Start Capsule if playing and has lyric
            if (playing && hasLyric && capsuleHandler?.isRunning() != true) {
                capsuleHandler?.start()
            }
        }
    }

    private fun updateProgressFromController(shouldLog: Boolean = true) {
        if (shouldLog) {
            AppLogger.getInstance().log(TAG, "⚙️ updateProgressFromController() called")
        }
        
        try {
            val mm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = android.content.ComponentName(this, MediaMonitorService::class.java)
            val controllers = mm.getActiveSessions(component)
            
            // [Fix Task 2] Strict Controller Matching
            // Prioritize the controller that matches the currently displayed metadata
            val currentMetadata = LyricRepository.getInstance().liveMetadata.value
            val targetPackage = currentMetadata?.packageName

            var activeController: android.media.session.MediaController? = null

            if (targetPackage != null) {
                // strict match
                activeController = controllers.firstOrNull { it.packageName == targetPackage }
                
                if (activeController == null && shouldLog) {
                    AppLogger.getInstance().d(TAG, "⚠️ Target package '$targetPackage' has no active controller session")
                }
            } else {
                // Fallback: No metadata? Use first playing (Legacy)
                 activeController = controllers.firstOrNull {
                    it.playbackState != null && it.playbackState?.state == PlaybackState.STATE_PLAYING
                }
            }

            if (activeController != null) {
                val state = activeController.playbackState
                val meta = activeController.metadata
                val durationLong = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                
                if (durationLong > 0) duration = durationLong

                if (state != null) {
                     val lastPosition = state.position
                     val lastUpdateTimeVal = state.lastPositionUpdateTime
                     val speed = state.playbackSpeed
                     
                     // Verify state freshness - if last update was too long ago (> 1 sec) and we are playing, 
                     // we might extrapolate only if state is genuinely playing.
                     
                     var currentPos = lastPosition
                     if (state.state == PlaybackState.STATE_PLAYING) {
                        val timeDelta = android.os.SystemClock.elapsedRealtime() - lastUpdateTimeVal
                        currentPos += (timeDelta * speed).toLong()
                     }

                     if (duration > 0 && currentPos > duration) currentPos = duration
                     if (currentPos < 0) currentPos = 0
                     
                     currentPosition = currentPos

                     // Update Repository with progress
                     if (shouldLog) {
                        AppLogger.getInstance().log(TAG, "📍 Progress [${activeController.packageName}]: ${currentPos}ms / ${duration}ms")
                     }
                     LyricRepository.getInstance().updateProgress(currentPos, duration)
                     
                     // Album art now handled by MediaMonitorService on metadata change
                     // Notification updates handled by LyricCapsuleHandler.visualizerLoop
                }
            } else {
                // No active MediaController found
                if (shouldLog) {
                    AppLogger.getInstance().log(TAG, "⚠️ No matching active MediaController found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Progress Update Error: ${e.message}")
        }
    }

    // Cache for App Names to avoid IPC overhead
    private val appNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun getAppName(pkg: String): String {
        // Return cached name if available
        appNameCache[pkg]?.let { return it }
        
        // 1. Check for Custom Name rule first
        val rule = ParserRuleHelper.getRuleForPackage(this, pkg)
        if (rule?.customName != null && rule.customName.isNotBlank()) {
            val customName = rule.customName
            appNameCache[pkg] = customName
            return customName
        }

        // 2. Fallback to System Label
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            val name = pm.getApplicationLabel(info).toString()
            // Cache the result
            if (name.isNotEmpty()) {
                appNameCache[pkg] = name
            }
            name
        } catch (e: Exception) {
            pkg
        }
    }

    private fun handleMediaPause() {
        try {
            val mm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = android.content.ComponentName(this, MediaMonitorService::class.java)
            val controllers = mm.getActiveSessions(component)
            
            for (c in controllers) {
                val state = c.playbackState
                if (state != null && state.state == PlaybackState.STATE_PLAYING) {
                    c.transportControls.pause()
                    AppLogger.getInstance().log(TAG, "⏸️ Media paused via notification action")
                    return
                }
            }
            AppLogger.getInstance().log(TAG, "⚠️ No playing controller found for pause")
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Pause action failed: ${e.message}")
        }
    }

    private fun handleMediaPlay() {
        try {
            val mm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = android.content.ComponentName(this, MediaMonitorService::class.java)
            val controllers = mm.getActiveSessions(component)
            
            for (c in controllers) {
                val state = c.playbackState
                if (state != null && state.state == PlaybackState.STATE_PAUSED) {
                    c.transportControls.play()
                    AppLogger.getInstance().log(TAG, "▶️ Media resumed via notification action")
                    return
                }
            }
            AppLogger.getInstance().log(TAG, "⚠️ No paused controller found for play")
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Play action failed: ${e.message}")
        }
    }

    private fun handleMediaNext() {
        try {
            val mm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = android.content.ComponentName(this, MediaMonitorService::class.java)
            val controllers = mm.getActiveSessions(component)
            
            // Skip to next on the first active controller (playing or paused)
            for (c in controllers) {
                val state = c.playbackState
                if (state != null && (state.state == PlaybackState.STATE_PLAYING || state.state == PlaybackState.STATE_PAUSED)) {
                    c.transportControls.skipToNext()
                    AppLogger.getInstance().log(TAG, "⏭️ Skipped to next via notification action")
                    return
                }
            }
            AppLogger.getInstance().log(TAG, "⚠️ No active controller found for skip next")
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Next action failed: ${e.message}")
        }
    }

    private fun handleMediaPrev() {
        try {
            val mm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = android.content.ComponentName(this, MediaMonitorService::class.java)
            val controllers = mm.getActiveSessions(component)
            
            // Skip to previous on the first active controller (playing or paused)
            for (c in controllers) {
                val state = c.playbackState
                if (state != null && (state.state == PlaybackState.STATE_PLAYING || state.state == PlaybackState.STATE_PAUSED)) {
                    c.transportControls.skipToPrevious()
                    AppLogger.getInstance().log(TAG, "⏮️ Skipped to prev via notification action")
                    return
                }
            }
            AppLogger.getInstance().log(TAG, "⚠️ No active controller found for skip prev")
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Prev action failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LyricService"
        private const val CHANNEL_ID = "lyric_capsule_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
