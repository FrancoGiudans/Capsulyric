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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayList

class LyricService : Service() {

    private var lastUpdateTime = 0L
    private var simulationMode = "MODERN"

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

            // Auto-start Capsule upon valid lyric (even if static, if we didn't return above)
            if (capsuleHandler?.isRunning() != true) {
                capsuleHandler?.start()
                
                // START PROGRESS TRACKING
                isPlaying = true
                handler.post(updateTask)
            } else {
                // Capsule running: Force immediate update
                capsuleHandler?.updateLyricImmediate(info.lyric)
            }
        }
    }
    
    // NEW: Playback state observer - controls capsule lifecycle
    private val playbackObserver = Observer<Boolean> { playing ->
        if (playing) {
            // Resume/Start capsule if we have valid lyrics
            val currentLyric = LyricRepository.getInstance().liveLyric.value
            if (currentLyric != null && currentLyric.lyric.isNotBlank() && capsuleHandler?.isRunning() != true) {
                capsuleHandler?.start()
                
                // START PROGRESS TRACKING
                isPlaying = true
                handler.post(updateTask)
                
                // Force immediate update with current lyric
                capsuleHandler?.updateLyricImmediate(currentLyric.lyric)
                AppLogger.getInstance().log(TAG, "▶️ Capsule started: Playback resumed")
            }
        } else {
            // Stop capsule ONLY when playback actually stops
            if (capsuleHandler?.isRunning() == true) {
                capsuleHandler?.stop()
                
                // STOP PROGRESS TRACKING
                isPlaying = false
                handler.removeCallbacks(updateTask)
                
                AppLogger.getInstance().log(TAG, "🛑 Capsule stopped: Playback stopped")
            }
        }
    }

    private val superLyricStub = object : ISuperLyric.Stub() {
        override fun onStop(data: SuperLyricData?) {
            AppLogger.getInstance().d(TAG, "API onStop: ${data?.packageName}")
            LyricRepository.getInstance().updatePlaybackStatus(false)
            
            // CRITICAL FIX: Don't stop capsule here - let playback state observer control lifecycle
            // capsuleHandler?.stop()  ← REMOVED
            // This prevents capsule from disappearing mid-playback when API sends stop events
        }

        override fun onSuperLyric(data: SuperLyricData?) {
            if (data != null) {
                val pkg = data.packageName
                val lyric = data.lyric
                
                // Check per-app settings (with Default Fallback)
                val rule = ParserRuleHelper.getRuleForPackage(this@LyricService, pkg) 
                           ?: ParserRuleHelper.createDefaultRule(pkg)

                if (!rule.useSuperLyricApi) {
                    AppLogger.getInstance().log(TAG, "[$pkg] SuperLyric API disabled for this app")
                    // Don't fetch here - metadata observer already handles it for non-CarProtocol apps
                    return
                }

                // Instrumental Filter
                if (lyric.matches(".*(纯音乐|Instrumental|No lyrics|请欣赏|没有歌词).*".toRegex())) {
                    AppLogger.getInstance().d(TAG, "Instrumental detected: $lyric")
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                    return
                }

                if (lyric == lastLyric) {
                    return
                }
                lastLyric = lyric

                val appName = getAppName(pkg)

                // Update Repository -> This triggers the Observer -> Notification
                LyricRepository.getInstance().updateLyric(lyric, appName)
                
                // If online lyrics enabled for this app AND SuperLyric doesn't provide syllable info
                if (rule.useOnlineLyrics && !lyric.contains("<")) {
                    AppLogger.getInstance().log(TAG, "[$pkg] SuperLyric无逐字信息，尝试在线获取")
                    tryFetchOnlineLyrics()
                }
            }
        }
    }

    // Progress Logic
    // Progress Logic
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
                val parsedInfo = LyricRepository.getInstance().liveParsedLyrics.value
                if (parsedInfo != null && parsedInfo.lines.isNotEmpty()) {
                     updateCurrentLyricLine(parsedInfo.lines)
                }
                
                // Balanced frequency: 250ms (reduced from 100ms to avoid island lag with visualizerLoop)
                handler.postDelayed(this, 250)
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

    // --- Mock Implementation ---
    private var isSimulating = false
    private var playbackStartTime = 0L
    private val mockLyrics = ArrayList<String>()

    // Rotation Logic State
    private var lastLyricSwitchTime = 0L
    private var currentMockLineIndex = 0

    private var currentChannelId = CHANNEL_ID

    private var hasOpenedSettings = false
    // Toggle for Chip Keep-Alive Hack
    private var invisibleToggle = false
    private var capsuleHandler: LyricCapsuleHandler? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        capsuleHandler = LyricCapsuleHandler(this, this)
        
        // Always register SuperLyric for detection (check per-app settings in callback)
        SuperLyricTool.registerSuperLyric(this, superLyricStub)
        AppLogger.getInstance().log(TAG, "SuperLyric API: Registered for detection")

        // Observe Repository
        val repo = LyricRepository.getInstance()
        repo.liveLyric.observeForever(lyricObserver)
        repo.isPlaying.observeForever(playbackObserver)  // ← NEW: Control capsule lifecycle

        repo.isPlaying.observeForever { isPlaying ->
            if (java.lang.Boolean.TRUE == isPlaying) {
                // Remove the check here, let startProgressUpdater handle it
                startProgressUpdater()
            } else {
                stopProgressUpdater()
            }
        }

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
        // [Fix Task 1] Immediate Foreground Promotion
        createNotificationChannel()
        
        // CRITICAL FIX: Prioritize the Rich Capsule Notification if it's already active
        if (capsuleHandler != null && capsuleHandler!!.isRunning()) {
            // Ask the handler to repost its rich notification
            // This satisfies startForeground requirements without downgrading the UI
            capsuleHandler!!.forceUpdateNotification()
        } else {
            // Fallback: Use basic notification logic (improved to show real lyrics if available)
            val currentInfo = LyricRepository.getInstance().liveLyric.value
            val title = currentInfo?.sourceApp ?: "Island Lyrics"
            val text = currentInfo?.lyric ?: "Initializing..."
            startForeground(NOTIFICATION_ID, buildNotification(text, title, ""))
        }

        val action = intent?.action ?: "null"
        AppLogger.getInstance().log(TAG, "Received Action: $action")

        if (intent != null && "ACTION_STOP" == intent.action) {
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        } else if (intent != null && "ACTION_MEDIA_PAUSE" == intent.action) {
            handleMediaPause()
            return START_STICKY
        } else if (intent != null && "ACTION_MEDIA_NEXT" == intent.action) {
            handleMediaNext()
            return START_STICKY
        } else if (intent != null && "com.example.islandlyrics.ACTION_SIMULATE" == intent.action) {
            // Read Mode: "MODERN" (Default)
            val mode = intent.getStringExtra("SIMULATION_MODE")
            simulationMode = mode ?: "MODERN"
            LogManager.getInstance().d(this, TAG, "Starting Simulation Mode: $simulationMode")
            broadcastStatus("🔵 Running (Modern)")
            
            if ("MODERN" == simulationMode) {
                 // huntForPromotedApi() // Not implemented in Java, skipped for now or inline?
            }
            startSimulation()
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
            SuperLyricTool.unregisterSuperLyric(this, superLyricStub)
        }
        
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
                // Standard Channel
                val serviceChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Live Updates (High Priority)",
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

        // Use ProgressStyle via Reflection
        try {
            // 1. Create ProgressStyle
            val styleClass = Class.forName("android.app.Notification\$ProgressStyle")
            val style = styleClass.getConstructor().newInstance()

            // 2. Set "StyledByProgress" (Critical for appearance)
            styleClass.getMethod("setStyledByProgress", Boolean::class.javaPrimitiveType).invoke(style, true)

            // 3. Create a dummy Segment (Required by internal logic)
            val segmentClass = Class.forName("android.app.Notification\$ProgressStyle\$Segment")
            val segment = segmentClass.getConstructor(Int::class.javaPrimitiveType).newInstance(1)

            // 4. Set Segments List
            val segments = ArrayList<Any>()
            segments.add(segment)
            styleClass.getMethod("setProgressSegments", List::class.java).invoke(style, segments)

            // 5. Apply Style to Builder
            builder.style = style as Notification.Style
            LogManager.getInstance().d(this, "Capsulyric", "✅ ProgressStyle applied")
        } catch (e: Exception) {
            LogManager.getInstance().e(this, "Capsulyric", "❌ ProgressStyle failed: $e")
        }

        // Unified Attributes (Chip, Promotion)
        // [Task 3] Keep-Alive Hack & Real Lyrics
        // 1. Get current lyric (Fallback to Capsule name)
        val rawText = if (!text.isEmpty()) text else "Capsulyric"

        // 2. Toggle Invisible Char to force System UI update (Reset 6s timer)
        invisibleToggle = !invisibleToggle
        val chipText = rawText + if (invisibleToggle) "\u200B" else ""

        applyLiveAttributes(builder, chipText) // Tries Builder methods

        val notification = builder.build()

        // Task 2: The "Desperate" Fallback
        applyPromotedFlagFallback(notification)

        return notification
    }



    private fun applyLiveAttributes(builder: Notification.Builder, text: String) {

        // 1. Set Status Chip Text
        try {
            // PROBE A: Try String.class (Known working on QPR2 Emulator)
            val method = Notification.Builder::class.java.getMethod("setShortCriticalText", String::class.java)
            method.invoke(builder, text)
            LogManager.getInstance().d(this, "Capsulyric", "✅ Chip set via String signature: $text")
        } catch (e1: Exception) {
            try {
                // PROBE B: Try CharSequence.class (Standard API definition fallback)
                val method = Notification.Builder::class.java.getMethod("setShortCriticalText", CharSequence::class.java)
                method.invoke(builder, text as CharSequence)
                LogManager.getInstance().d(this, "Capsulyric", "✅ Chip set via CharSequence signature")
            } catch (e2: Exception) {
                LogManager.getInstance().e(this, "Capsulyric", "❌ Chip Reflection Failed. API changed? ${e1.javaClass.simpleName}")
            }
        }

        // 2. Set Promoted Ongoing (Crucial for Chip visibility)
        try {
            // EXACT SIGNATURE: setRequestPromotedOngoing(boolean)
            val method = Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
            method.invoke(builder, true)
            LogManager.getInstance().d(this, "Capsulyric", "✅ Promoted Flag Set")
        } catch (e: Exception) {
            LogManager.getInstance().e(this, "Capsulyric", "❌ Promoted Flag Failed: $e")
        }
    }

    private fun applyPromotedFlagFallback(notification: Notification) {
         try {
             val f = Notification::class.java.getField("FLAG_PROMOTED_ONGOING")
             val value = f.getInt(null)
             notification.flags = notification.flags or value
         } catch (e: Exception) {
             // Fallback failed
         }
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
        var hasChar = false
        try {
            val m = notification.javaClass.getMethod("hasPromotableCharacteristics")
            hasChar = m.invoke(notification) as Boolean
            intent.putExtra("hasPromotable", hasChar)
        } catch (e: Exception) {}

        // 3. Ongoing Flag (API 36)
        var isPromoted = false
        try {
            val f = Notification::class.java.getField("FLAG_PROMOTED_ONGOING")
            val flagVal = f.getInt(null)
            isPromoted = (notification.flags and flagVal) != 0
            intent.putExtra("isPromoted", isPromoted)
        } catch (e: Exception) {
            // Fallback/Log
        }

        sendBroadcast(intent)

        // --- Detailed Logging (DIAG_UPDATE) ---
        // 4. System Permission
        var canPost = false
        try {
            val m = nm.javaClass.getMethod("canPostPromotedNotifications")
            canPost = m.invoke(nm) as Boolean
        } catch (e: Exception) {}

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

    private fun startSimulation() {
        isSimulating = true
        currentPosition = 0
        duration = 252000 // 4:12
        playbackStartTime = android.os.SystemClock.elapsedRealtime()
        
        // Reset Rotation State
        lastLyricSwitchTime = 0
        currentMockLineIndex = 0

        loadMockLyrics()

        // Force Channel Reset for Simulation
        val nm = getSystemService(NotificationManager::class.java)
        if (nm != null) {
             val ch = nm.getNotificationChannel(currentChannelId)
             if (ch != null && ch.importance < NotificationManager.IMPORTANCE_HIGH) {
                 nm.deleteNotificationChannel(currentChannelId)
                 currentChannelId = "lyric_sim_" + System.currentTimeMillis()
                 createNotificationChannel()
             }
        }

        // [Fix Task 3] Update IMMEDIATELY with Real Data
        updateProgressFromController()
        startProgressUpdater()
    }

    private fun loadMockLyrics() {
        mockLyrics.clear()
        try {
            val reader = BufferedReader(InputStreamReader(assets.open("mock_lyrics.txt")))
            var line: String? 
            while (reader.readLine().also { line = it } != null) {
                val l = line!!
                if (l.trim().isNotEmpty()) {
                    var text = l
                    if (l.startsWith("[") && l.contains("]")) {
                        text = l.substring(l.indexOf("]") + 1).trim()
                    }
                    if (text.isNotEmpty()) {
                        mockLyrics.add(text)
                    }
                }
            }
            reader.close()
            Log.d(TAG, "Loaded ${mockLyrics.size} lines for simulation.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load mock lyrics: ${e.message}")
        }

        if (mockLyrics.isEmpty()) {
            mockLyrics.add("Simulating Live Updates...")
            mockLyrics.add("Line 1: 1500ms Rotation")
            mockLyrics.add("Line 2: Checking Float State")
            mockLyrics.add("Line 3: Still Active")
            mockLyrics.add("Waiting for Trigger...")
        }
    }

    private fun updateProgressFromController(shouldLog: Boolean = true) {
        if (shouldLog) {
            AppLogger.getInstance().log(TAG, "⚙️ updateProgressFromController() called")
        }
        
        if (isSimulating) {
            val now = android.os.SystemClock.elapsedRealtime()
            currentPosition = now - playbackStartTime

            if (currentPosition > duration) {
                currentPosition = duration
                isSimulating = false
                updateNotification("Simulation Ended", "Island Lyrics", "")
                return
            }

            // Rotation Logic (Every 1500ms)
            if (now - lastLyricSwitchTime > 1500) {
                lastLyricSwitchTime = now
                currentMockLineIndex++
                if (currentMockLineIndex >= mockLyrics.size) {
                    currentMockLineIndex = 0 // Loop
                }

                val currentLine = mockLyrics[currentMockLineIndex]
                val title = "一吻天荒"
                updateNotification(currentLine, title, "Mock Live Test")
            } else {
                 val currentLine = mockLyrics[currentMockLineIndex]
                 val title = "一吻天荒"
                 updateNotification(currentLine, title, "Mock Live Test")
            }
            return
        }

        try {
            val mm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = android.content.ComponentName(this, MediaMonitorService::class.java)
            val controllers = mm.getActiveSessions(component)
            
            var activeController: android.media.session.MediaController? = null
            for (c in controllers) {
                if (c.playbackState != null && c.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    activeController = c
                    break
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
                     
                     var currentPos = lastPosition + ((android.os.SystemClock.elapsedRealtime() - lastUpdateTimeVal) * speed).toLong()

                     if (duration > 0 && currentPos > duration) currentPos = duration
                     if (currentPos < 0) currentPos = 0
                     
                     currentPosition = currentPos

                     // Update Repository with progress
                     if (shouldLog) {
                        AppLogger.getInstance().log(TAG, "📍 Progress: ${currentPos}ms / ${duration}ms")
                     }
                     LyricRepository.getInstance().updateProgress(currentPos, duration)
                     
                     // Album art now handled by MediaMonitorService on metadata change
                     // to decouple it from playback/lyric state loops.

                     val info = LyricRepository.getInstance().liveLyric.value
                     val lyric = info?.lyric ?: lastLyric
                     val pkg = info?.sourceApp ?: "Island Lyrics"
                     
                     updateNotification(lyric, pkg, "")
                }
            } else {
                // No active MediaController found
                if (shouldLog) {
                    AppLogger.getInstance().log(TAG, "⚠️ No active MediaController found")
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

    companion object {
        private const val TAG = "LyricService"
        private const val CHANNEL_ID = "capsulyric_live_high_v1"
        private const val NOTIFICATION_ID = 1001
    }
}
