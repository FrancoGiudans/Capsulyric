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
    private var simulationMode = "MODERN"

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
            } else {
                // Capsule running: Force immediate update
                capsuleHandler?.updateLyricImmediate(info.lyric, info.sourceApp)
            }
        }
        // CRITICAL FIX: Removed else branch that called capsuleHandler?.stop()
        // Capsule now stays visible even if lyric becomes null/blank
        // Lifecycle is controlled by playback state, not lyric availability
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
                capsuleHandler?.updateLyricImmediate(currentLyric.lyric, currentLyric.sourceApp)
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
            Log.d(TAG, "API onStop: ${data?.packageName}")
            LyricRepository.getInstance().updatePlaybackStatus(false)
            
            // CRITICAL FIX: Don't stop capsule here - let playback state observer control lifecycle
            // capsuleHandler?.stop()  ← REMOVED
            // This prevents capsule from disappearing mid-playback when API sends stop events
        }

        override fun onSuperLyric(data: SuperLyricData?) {
            if (data != null) {
                val lyric = data.lyric

                // Instrumental Filter
                if (lyric.matches(".*(纯音乐|Instrumental|No lyrics|请欣赏|没有歌词).*".toRegex())) {
                    Log.d(TAG, "Instrumental detected: $lyric")
                    @Suppress("DEPRECATION")
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
        repo.isPlaying.observeForever(playbackObserver)  // ← NEW: Control capsule lifecycle

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
        SuperLyricTool.unregisterSuperLyric(this, superLyricStub)
        LyricRepository.getInstance().liveLyric.removeObserver(lyricObserver)
        broadcastStatus("🔴 Stopped")
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
        private const val NOTIFICATION_ID = 1001
    }
}
