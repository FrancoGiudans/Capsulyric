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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayList

class LyricService : Service() {

    private var lastUpdateTime = 0L
    private var simulationMode = "LEGACY"

    // To debounce updates
    private var lastLyric = ""

    private val lyricObserver = Observer<LyricRepository.LyricInfo?> { info ->
        if (info != null && info.lyric.isNotBlank()) {
            // Auto-start Capsule when lyrics are available
            if (capsuleHandler?.isRunning() != true) {
                capsuleHandler?.start()
                
                // START PROGRESS TRACKING
                isPlaying = true
                handler.post(updateTask)
            }
            // Always update notification with new lyric
            updateNotification(info.lyric, info.sourceApp, "")
        } else {
            // Stop Capsule when lyrics are unavailable
            if (capsuleHandler?.isRunning() == true) {
                capsuleHandler?.stop()
                
                // STOP PROGRESS TRACKING
                isPlaying = false
                handler.removeCallbacks(updateTask)
            }
        }
    }

    private val superLyricStub = object : ISuperLyric.Stub() {
        override fun onStop(data: SuperLyricData?) {
            Log.d(TAG, "onStop: ${data ?: "null"}")
            LyricRepository.getInstance().updatePlaybackStatus(false)
            // Stop Capsule when playback stops
            capsuleHandler?.stop()
            updateNotification("Playback Stopped", "Island Lyrics", "")
        }

        override fun onSuperLyric(data: SuperLyricData?) {
            if (data != null) {
                val lyric = data.lyric

                if (lyric != null) {
                    // Instrumental Filter
                    if (lyric.matches(".*(纯音乐|Instrumental|No lyrics|请欣赏|没有歌词).*".toRegex())) {
                        Log.d(TAG, "Instrumental detected: $lyric")
                        stopForeground(true)
                        return
                    }

                    if (lyric == lastLyric) {
                        return
                    }
                    lastLyric = lyric

                    val pkg = data.packageName
                    val appName = getAppName(pkg)

                    // Update Repository -> This triggers the Observer -> Notification
                    LyricRepository.getInstance().updateLyric(lyric, appName)
                }
            }
        }
    }

    // Progress Logic
    private var currentPosition = 0L
    private var duration = 0L
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private val updateTask = object : Runnable {
        override fun run() {
            if (isPlaying) {
                updateProgressFromController()
                handler.postDelayed(this, 1000)
            }
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
        SuperLyricTool.registerSuperLyric(this, superLyricStub)

        // Observe Repository
        val repo = LyricRepository.getInstance()
        repo.liveLyric.observeForever(lyricObserver)

        repo.isPlaying.observeForever { isPlaying ->
            if (java.lang.Boolean.TRUE == isPlaying) {
                if (!this.isPlaying) {
                    this.isPlaying = true
                    startProgressUpdater()
                }
            } else {
                stopProgressUpdater()
            }
        }

        repo.liveMetadata.observeForever { info ->
            if (info != null) {
                if (info.duration > 0) {
                    duration = info.duration
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // [Fix Task 1] Immediate Foreground Promotion
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initializing...", "Island Lyrics", ""))

        val action = intent?.action ?: "null"
        AppLogger.getInstance().log(TAG, "Received Action: $action")

        if (intent != null && "ACTION_STOP" == intent.action) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        } else if (intent != null && "com.example.islandlyrics.ACTION_SIMULATE" == intent.action) {
            // Read Mode: "LEGACY" (Default) or "MODERN"
            val mode = intent.getStringExtra("SIMULATION_MODE")
            simulationMode = mode ?: "LEGACY"
            LogManager.getInstance().d(this, TAG, "Starting Simulation Mode: $simulationMode")
            broadcastStatus(if (mode != null && mode == "MODERN") "🔵 Running (Modern)" else "🟢 Running (Legacy)")
            
            if ("MODERN" == simulationMode) {
                 // huntForPromotedApi() // Not implemented in Java, skipped for now or inline?
            }
            startSimulation()
            return START_STICKY
        } else if (intent != null && "com.example.islandlyrics.ACTION_SIMULATE_CLONE" == intent.action) {
            simulationMode = "CLONE"
            LogManager.getInstance().d(this, TAG, "Starting Clone Mode Simulation")
            broadcastStatus("🧬 Running (Clone)")

            // Start the handler
            capsuleHandler?.start()
            return START_STICKY
        } else if (intent != null && "FORCE_UPDATE_UI" == intent.action) {
            if (BuildConfig.DEBUG) {
                val title = intent.getStringExtra("title")
                val artist = intent.getStringExtra("artist")
                val lyric = intent.getStringExtra("lyric")
                updateNotification(
                    lyric ?: "Debug Lyric",
                    title ?: "Debug Source",
                    artist ?: ""
                )
            }
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
        SuperLyricTool.unregisterSuperLyric(this, superLyricStub)
        LyricRepository.getInstance().liveLyric.removeObserver(lyricObserver)
        broadcastStatus("🔴 Stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

                // Clone Channel (InstallerX)
                val cloneChannel = NotificationChannel(
                    CHANNEL_ID_CLONE,
                    "Installer Live",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(cloneChannel)
            }
        }
    }

    private fun buildNotification(title: String, text: String, subText: String): Notification {
        return if ("MODERN" == simulationMode && Build.VERSION.SDK_INT >= 36) {
            buildModernNotification(title, text, subText)
        } else {
            buildLegacyNotification(title, text, subText)
        }
    }

    private fun buildModernNotification(title: String, text: String, subText: String): Notification {
        LogManager.getInstance().d(this, TAG, "Building Modern Notification (ProgressStyle)")
        if (Build.VERSION.SDK_INT < 36) return buildLegacyNotification(title, text, subText)

        val builder = Notification.Builder(this, currentChannelId)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_HIGH)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )

        // Use ProgressStyle via Reflection (InstallerX Method)
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                // 1. Create ProgressStyle
                val styleClass = Class.forName("android.app.Notification\$ProgressStyle")
                val style = styleClass.getConstructor().newInstance()

                // 2. Set "StyledByProgress" (Critical for appearance)
                styleClass.getMethod("setStyledByProgress", Boolean::class.javaPrimitiveType).invoke(style, true)

                // 3. Create a dummy Segment (Required by InstallerX logic)
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

    private fun buildLegacyNotification(title: String, text: String, subText: String): Notification {
        if (Build.VERSION.SDK_INT >= 35) { // Baklava / Android 15+
            val builder = Notification.Builder(this, currentChannelId)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(subText)
                .setStyle(Notification.BigTextStyle().bigText(title))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 0,
                        Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )

            // Unified Attributes
            applyLiveAttributes(builder, text)

            return builder.build()
        }

        // Fallback for older Android
        return NotificationCompat.Builder(this, currentChannelId)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun applyLiveAttributes(builder: Notification.Builder, text: String) {
        if (Build.VERSION.SDK_INT < 36) return

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
            // EXACT SIGNATURE from InstallerX: setRequestPromotedOngoing(boolean)
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

    private fun updateNotification(title: String, text: String, subText: String) {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 500) return // Strict 500ms cap like InstallerX
        lastUpdateTime = now

        // DISABLED: InstallerCloneHandler now handles all Capsule notifications
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
        LogManager.getInstance().d(this, TAG, "LyricService updateNotification called (skipped - using InstallerCloneHandler)")
    }

    internal fun inspectNotification(notification: Notification, nm: NotificationManager) {
        // Prepare Status Broadcast
        val intent = Intent("com.example.islandlyrics.STATUS_UPDATE")
        intent.setPackage(packageName)

        // 1. General Status
        val modeStatus = if ("MODERN" == simulationMode && Build.VERSION.SDK_INT >= 36) "🔵 Modern (API 36)" else "🟢 Legacy"
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        if (!isPlaying) {
            isPlaying = true
            handler.post(updateTask)
        }
    }

    private fun stopProgressUpdater() {
        isPlaying = false
        handler.removeCallbacks(updateTask)
    }

    private fun startSimulation() {
        if ("CLONE" == simulationMode) {
            return // Handled by InstallerCloneHandler
        }
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
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    private fun updateProgressFromController() {
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
                     Log.d(TAG, "Updating progress: pos=$currentPos, dur=$duration")
                     LyricRepository.getInstance().updateProgress(currentPos, duration)
                     Log.d(TAG, "Progress updated in repository")

                     val info = LyricRepository.getInstance().liveLyric.value
                     val lyric = info?.lyric ?: lastLyric
                     val pkg = info?.sourceApp ?: "Island Lyrics"
                     
                     updateNotification(lyric, pkg, "")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Progress Update Error: ${e.message}")
        }
    }

    private fun getAppName(pkg: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    companion object {
        private const val TAG = "LyricService"
        private const val CHANNEL_ID = "capsulyric_live_high_v1"
        private const val CHANNEL_ID_CLONE = "installer_live_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
