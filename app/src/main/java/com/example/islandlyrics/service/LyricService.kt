package com.example.islandlyrics.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.lifecycle.Observer
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.platform.XmsfBypassMode
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.logging.LogManager
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.lyric.LyricGetterSource
import com.example.islandlyrics.data.lyric.OnlineLyricSource
import com.example.islandlyrics.data.lyric.SuperLyricSource
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.ui.common.LyricCapsuleHandler
import com.example.islandlyrics.ui.common.LyricDisplayManager
import com.example.islandlyrics.ui.common.FloatingLyricsRenderer
import com.example.islandlyrics.ui.common.SuperIslandHandler

class LyricService : Service() {

    private var lastUpdateTime = 0L
    private var lastObservedTrackKey: String? = null

    // ── Lyric sources (each handles one acquisition path) ────────────────────
    private lateinit var onlineLyricSource: OnlineLyricSource
    private lateinit var superLyricSource: SuperLyricSource
    private lateinit var lyricGetterSource: LyricGetterSource

    private lateinit var progressSyncController: ProgressSyncController
    private var renderModeCoordinator: RenderModeCoordinator? = null
    private var delayedStopController: DelayedStopController? = null

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
                
                if (pkg != null) {
                    val rule = ParserRuleHelper.getRuleForPackage(this, pkg) 
                               ?: ParserRuleHelper.createDefaultRule(pkg)

                    if (rule.useOnlineLyrics) {
                        AppLogger.getInstance().log(TAG, "Static metadata detected -> Triggering online fetch")
                        onlineLyricSource.fetchFor(title, artist, pkg)
                        return@Observer
                    } else {
                        AppLogger.getInstance().log(TAG, "Static metadata detected but online fetch disabled")
                    }
                }
            }

            updateActiveHandler()
            displayManager.notifyLyricChanged(lyric)
        } else {
            // In the gap of track switching, lyrics might temporarily clear.
            // As long as we have valid metadata, update UI but DO NOT stop the handler.
            if (LyricRepository.getInstance().liveMetadata.value != null) {
                updateActiveHandler()
                displayManager.forceUpdate()
            }
        }
    }

    private val playbackObserver = Observer<Boolean> { playing ->
        if (playing) {
            updateActiveHandler()
            progressSyncController.start()
            // After the renderer is started (or confirmed running), immediately push
            // a display update so the notification appears without waiting for the
            // next visualizerLoop tick. This is especially important when resuming
            // after a background pause where the loop was idle.
            displayManager.forceUpdate()
        }
        delayedStopController?.onPlaybackChanged(playing)
    }

    private val metadataObserver = Observer<LyricRepository.MediaInfo?> { info ->
        if (info != null) {
            progressSyncController.setDurationIfValid(info.duration)
            val trackKey = "${info.packageName}|${info.title}|${info.artist}"
            val trackChanged = trackKey != lastObservedTrackKey
            lastObservedTrackKey = trackKey

            if (trackChanged) {
                // Only clear lyric-related state when the actual track identity changes.
                // Metadata can refresh multiple times per song, and wiping here would
                // replace an already received lyric with the placeholder notification.
                LyricRepository.getInstance().updateLyric("", info.packageName, "System")
                LyricRepository.getInstance().updateParsedLyrics(emptyList(), false)
                LyricRepository.getInstance().updateCurrentLine(null)
                progressSyncController.resetLineIndex()
                progressSyncController.resetProgressForTrackChange(info.duration)

                if (LyricRepository.getInstance().isPlaying.value == true) {
                    progressSyncController.start()
                    updateActiveHandler()
                }
            }

            // Proactive online lyric fetching logic
            val rule = ParserRuleHelper.getRuleForPackage(this, info.packageName)
                       ?: ParserRuleHelper.createDefaultRule(info.packageName)

            AppLogger.getInstance().log(
                TAG,
                "Metadata Change: ${info.title} (${info.packageName}) | Rule: Active | Online: ${rule.useOnlineLyrics} | Car: ${rule.usesCarProtocol}"
            )

            if (trackChanged && rule.useOnlineLyrics) {
                if (!rule.usesCarProtocol) {
                    AppLogger.getInstance().log(TAG, "[${info.packageName}] Non-CarProtocol app, triggering fetch...")
                    onlineLyricSource.fetchFor(info.title, info.artist, info.packageName)
                } else {
                    AppLogger.getInstance().log(TAG, "[${info.packageName}] CarProtocol app, waiting for lyric observer trigger...")
                }
            }
            
            // CRITICAL: Force immediate UI update to propagate new track metadata to SuperIsland
            displayManager.forceUpdate()
        }
    }

    // ── SuperLyric API stub is now in SuperLyricSource ────────────────────────
    // (Removed: superLyricStub inline definition)

    private val handler = Handler(Looper.getMainLooper())

    // Cached system services — initialized once in onCreate() to avoid repeated IPC
    private var cachedMediaSessionManager: android.media.session.MediaSessionManager? = null
    private var cachedMediaComponent: android.content.ComponentName? = null

    private var currentChannelId = CHANNEL_ID

    // Toggle for Chip Keep-Alive Hack
    private var invisibleToggle = false
    private var capsuleHandler: LyricCapsuleHandler? = null
    private lateinit var superIslandHandler: SuperIslandHandler
    private lateinit var displayManager: LyricDisplayManager
    private var floatingLyricsRenderer: FloatingLyricsRenderer? = null
    // Live Update builds read mode from prefs; otherwise default to SuperIsland
    private var isSuperIslandMode = !RomUtils.isLiveUpdateSupported()

    private val mediaActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                "com.example.islandlyrics.ACTION_MEDIA_PLAY_PAUSE", "miui.focus.action_play_pause" -> {
                    val playing = LyricRepository.getInstance().isPlaying.value ?: false
                    if (playing) handleMediaPause() else handleMediaPlay()
                }
                "com.example.islandlyrics.ACTION_MEDIA_NEXT", "miui.focus.action_next" -> handleMediaNext()
                "com.example.islandlyrics.ACTION_MEDIA_PREV", "miui.focus.action_prev" -> handleMediaPrev()
            }
        }
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (LyricRepository.ACTION_REFRESH_DIAGNOSTICS == intent?.action) {
                AppLogger.getInstance().log(TAG, "Manual diagnostic refresh requested.")
                updateAdvancedDiagnostics()
            }
        }
    }

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == "super_island_enabled" && RomUtils.isLiveUpdateSupported()) {
            isSuperIslandMode = p.getBoolean(key, false)
            AppLogger.getInstance().log(TAG, "Mode Switched via Settings -> isSuperIslandMode: $isSuperIslandMode")
            renderModeCoordinator?.setMode(isSuperIslandMode)
            updateActiveHandler()
        } else if (key == FloatingLyricsRenderer.PREF_KEY) {
            val enabled = p.getBoolean(FloatingLyricsRenderer.PREF_KEY, false)
            AppLogger.getInstance().log(TAG, "Floating lyrics toggled -> enabled: $enabled")
            if (enabled) {
                floatingLyricsRenderer?.start()
            } else {
                floatingLyricsRenderer?.stop()
            }
        } else if (key == OfflineModeManager.KEY_FULLY_OFFLINE_MODE && p.getBoolean(key, false)) {
            AppLogger.getInstance().log(TAG, "Offline mode enabled -> cancelling online lyric requests")
            onlineLyricSource.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isAlive = true
        
        // Load mode from preferences when Live Update is enabled
        if (RomUtils.isLiveUpdateSupported()) {
            isSuperIslandMode = getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                .getBoolean("super_island_enabled", false)
        } else {
            isSuperIslandMode = true
        }

        createNotificationChannel()
        if (RomUtils.isLiveUpdateSupported()) {
            capsuleHandler = LyricCapsuleHandler(this, this)
        }
        superIslandHandler = SuperIslandHandler(this, this)
        floatingLyricsRenderer = FloatingLyricsRenderer(this)
        displayManager = LyricDisplayManager(this).apply {
            onStateUpdated = { state ->
                renderModeCoordinator?.render(state)
                floatingLyricsRenderer?.render(state)
            }
        }
        renderModeCoordinator = RenderModeCoordinator(displayManager, capsuleHandler, superIslandHandler)
        renderModeCoordinator!!.setMode(isSuperIslandMode)
        delayedStopController = DelayedStopController(
            handler = handler,
            prefsProvider = { getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) },
            onStop = {
                progressSyncController.stop()
                renderModeCoordinator!!.stopForCurrentMode()
                AppLogger.getInstance().log(TAG, "🛑 Renderer stopped: Playback stopped (Delayed)")
            }
        )

        // Cache system services once here rather than in every IPC call
        cachedMediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
        cachedMediaComponent = android.content.ComponentName(this, MediaMonitorService::class.java)
        progressSyncController = ProgressSyncController(
            handler = handler,
            repo = LyricRepository.getInstance(),
            appNameProvider = { pkg -> getAppName(pkg) },
            mediaSessionManagerProvider = { cachedMediaSessionManager },
            mediaComponentProvider = { cachedMediaComponent }
        )
        
        // Initialise lyric sources
        onlineLyricSource = OnlineLyricSource(this)
        superLyricSource  = SuperLyricSource(
            context = this,
            onlineLyricSource = onlineLyricSource
        )
        superLyricSource.start()
        AppLogger.getInstance().d(TAG, "SuperLyricSource started")
        lyricGetterSource = LyricGetterSource(this, onlineLyricSource)
        lyricGetterSource.start()
        AppLogger.getInstance().d(TAG, "LyricGetterSource started")

        val filter = IntentFilter().apply {
            addAction("com.example.islandlyrics.ACTION_MEDIA_PLAY_PAUSE")
            addAction("com.example.islandlyrics.ACTION_MEDIA_NEXT")
            addAction("com.example.islandlyrics.ACTION_MEDIA_PREV")
            addAction("miui.focus.action_play_pause")
            addAction("miui.focus.action_next")
            addAction("miui.focus.action_prev")
        }
        registerReceiver(mediaActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // Register refresh receiver
        val refreshFilter = IntentFilter(LyricRepository.ACTION_REFRESH_DIAGNOSTICS)
        registerReceiver(refreshReceiver, refreshFilter, Context.RECEIVER_NOT_EXPORTED)

        getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)

        // Observe Repository
        val repo = LyricRepository.getInstance()
        repo.liveLyric.observeForever(lyricObserver)
        repo.isPlaying.observeForever(playbackObserver)  // Controls capsule lifecycle + progress tracking
        repo.liveMetadata.observeForever(metadataObserver)
    }

    private fun syncFloatingLyricsState(prefs: android.content.SharedPreferences) {
        val enabled = prefs.getBoolean(FloatingLyricsRenderer.PREF_KEY, false)
        if (enabled && !floatingLyricsRenderer!!.isRunning) {
            floatingLyricsRenderer?.start()
        } else if (!enabled && floatingLyricsRenderer!!.isRunning) {
            floatingLyricsRenderer?.stop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "null"
        AppLogger.getInstance().log(TAG, "onStartCommand Received Action: $action")

        // 1. Proactively sync the Super Island Mode preference
        val prefs = getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        if (RomUtils.isLiveUpdateSupported()) {
            isSuperIslandMode = prefs.getBoolean("super_island_enabled", false)
            renderModeCoordinator?.setMode(isSuperIslandMode)
        } else {
            isSuperIslandMode = true
            renderModeCoordinator?.setMode(true)
        }
        syncFloatingLyricsState(prefs)

        // [Fix Task 1] Immediate Foreground Promotion
        createNotificationChannel()

        val shouldWarmForegroundForXmsfBypass =
            isSuperIslandMode && XmsfBypassMode.read(prefs).isEnabled
        if (shouldWarmForegroundForXmsfBypass) {
            val currentInfo = LyricRepository.getInstance().liveLyric.value
            val title = currentInfo?.sourceApp ?: "Island Lyrics"
            val text = currentInfo?.lyric ?: "Initializing..."
            try {
                startForeground(NOTIFICATION_ID, buildNotification(text, title, ""))
            } catch (e: Exception) {
                AppLogger.getInstance().e(TAG, "Failed to warm foreground for XMSF bypass: ${e.message}")
            }
        }

        // 2. Ensure only the correct handler is running
        updateActiveHandler()

        // CRITICAL FIX: Prioritize the Rich Capsule Notification if it's already active
        // 3. Promote to foreground (avoid redundant channel creation)
        if (isSuperIslandMode && superIslandHandler.isRunning == true) {
            displayManager.forceUpdate()
        } else if (RomUtils.isLiveUpdateSupported() && !isSuperIslandMode && capsuleHandler?.isRunning() == true) {
            displayManager.forceUpdate()
        } else {
            // Always preheat the foreground slot with a plain notification before
            // the richer renderer takes over. This matches the observed behavior
            // on MIUI where a missed warm-up causes the first lyric update to lag.
            val currentInfo = LyricRepository.getInstance().liveLyric.value
            val title = currentInfo?.sourceApp ?: "Island Lyrics"
            val text = currentInfo?.lyric ?: "Initializing..."
            try {
                startForeground(NOTIFICATION_ID, buildNotification(text, title, ""))
            } catch (e: Exception) {
                AppLogger.getInstance().e(TAG, "Failed startForeground in fallback: ${e.message}")
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
        isAlive = false
        AppLogger.getInstance().log(TAG, "Service Destroyed")
        progressSyncController.stop()
        progressSyncController.destroy()
        delayedStopController?.cancel()
        floatingLyricsRenderer?.stop()
        renderModeCoordinator?.stopAll() ?: run {
            // Fallback: manually stop SuperIsland when coordinator isn't initialized
            superIslandHandler.stop()
            displayManager.stop()
        }
        
        superLyricSource.stop()
        lyricGetterSource.stop()
        onlineLyricSource.cancel()
        
        getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener)
        val repo = LyricRepository.getInstance()
        repo.liveLyric.removeObserver(lyricObserver)
        repo.isPlaying.removeObserver(playbackObserver)
        repo.liveMetadata.removeObserver(metadataObserver)
        
        try {
            unregisterReceiver(mediaActionReceiver)
            unregisterReceiver(refreshReceiver)
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Failed to unregister receivers: ${e.message}")
        }
        
        broadcastStatus("🔴 Stopped")
    }
    
    // ── Online lyric fetching is now in OnlineLyricSource ────────────────────
    // (Removed: tryFetchOnlineLyrics inline implementation)

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
                    description = getString(R.string.channel_live_lyrics_desc)
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
        if (Build.VERSION.SDK_INT >= 36) {
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
        }


        // Unified Attributes (Chip, Promotion)
        // [Task 3] Keep-Alive Hack & Real Lyrics
        // 1. Get current lyric (Fallback to Capsule name)
        val rawText = if (text.isNotEmpty()) text else "Capsulyric"

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
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText(text)
        }

        // 2. Set Promoted Ongoing (Crucial for Chip visibility)
        // The method setRequestPromotedOngoing isn't exposed in standard Notification.Builder stubs,
        // so we set the exact extra manually.
        builder.extras.putBoolean("android.app.extra.REQUEST_PROMOTED_ONGOING", true)
    }

    private fun applyPromotedFlagFallback(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 36) {
            notification.flags = notification.flags or Notification.FLAG_PROMOTED_ONGOING
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
        var isPromoted = false
        if (Build.VERSION.SDK_INT >= 36) {
            hasChar = notification.hasPromotableCharacteristics()
            intent.putExtra("hasPromotable", hasChar)

            // 3. Ongoing Flag (API 36)
            val flagVal = Notification.FLAG_PROMOTED_ONGOING
            isPromoted = (notification.flags and flagVal) != 0
            intent.putExtra("isPromoted", isPromoted)
        }

        sendBroadcast(intent)

        // Update Repo Diagnostics
        LyricRepository.getInstance().mergeDiagnostics { old ->
            old.copy(
                hasPromotableChar = hasChar,
                isCurrentlyPromoted = isPromoted
            )
        }

        // --- Detailed Logging (DIAG_UPDATE) ---
        // 4. System Permission
        val canPost = if (Build.VERSION.SDK_INT >= 36) {
            nm.canPostPromotedNotifications()
        } else {
            false
        }

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

    private fun updateActiveHandler() {
        val currentLyric = LyricRepository.getInstance().liveLyric.value
        val hasLyric = currentLyric != null && currentLyric.lyric.isNotBlank()
        val hasMetadata = LyricRepository.getInstance().liveMetadata.value != null
        val playing = LyricRepository.getInstance().isPlaying.value ?: false
        val shouldRender = playing && (hasMetadata || hasLyric)

        val coordinator = renderModeCoordinator
        if (coordinator != null) {
            // Render state should follow MediaSession playback + session content,
            // not whether a lyric source happened to push a line.
            coordinator.updateActiveHandler(shouldRender)
        } else {
            // Live Update disabled: SuperIsland is the only renderer
            if (shouldRender && !superIslandHandler.isRunning) {
                superIslandHandler.start()
            } else if (!shouldRender) {
                superIslandHandler.stop()
            }
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
            val mm = cachedMediaSessionManager ?: return
            val component = cachedMediaComponent ?: return
            val controllers = mm.getActiveSessions(component)
            val targetPackage = LyricRepository.getInstance().liveMetadata.value?.packageName
            val targetController = targetPackage?.let { pkg ->
                controllers.firstOrNull { it.packageName == pkg }
            }
            
            if (targetController != null) {
                val state = targetController.playbackState
                if (state != null && state.state == PlaybackState.STATE_PLAYING) {
                    targetController.transportControls.pause()
                    AppLogger.getInstance().log(TAG, "⏸️ Media paused via notification action (target=$targetPackage)")
                    return
                }
            }

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
            val mm = cachedMediaSessionManager ?: return
            val component = cachedMediaComponent ?: return
            val controllers = mm.getActiveSessions(component)
            val targetPackage = LyricRepository.getInstance().liveMetadata.value?.packageName
            val targetController = targetPackage?.let { pkg ->
                controllers.firstOrNull { it.packageName == pkg }
            }
            
            if (targetController != null) {
                val state = targetController.playbackState
                if (state != null && state.state == PlaybackState.STATE_PAUSED) {
                    targetController.transportControls.play()
                    AppLogger.getInstance().log(TAG, "▶️ Media resumed via notification action (target=$targetPackage)")
                    return
                }
            }

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
            val mm = cachedMediaSessionManager ?: return
            val component = cachedMediaComponent ?: return
            val controllers = mm.getActiveSessions(component)
            val targetPackage = LyricRepository.getInstance().liveMetadata.value?.packageName
            val targetController = targetPackage?.let { pkg ->
                controllers.firstOrNull { it.packageName == pkg }
            }
            
            if (targetController != null) {
                val state = targetController.playbackState
                if (state != null && (state.state == PlaybackState.STATE_PLAYING || state.state == PlaybackState.STATE_PAUSED)) {
                    targetController.transportControls.skipToNext()
                    AppLogger.getInstance().log(TAG, "⏭️ Skipped to next via notification action (target=$targetPackage)")
                    return
                }
            }

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
            val mm = cachedMediaSessionManager ?: return
            val component = cachedMediaComponent ?: return
            val controllers = mm.getActiveSessions(component)
            val targetPackage = LyricRepository.getInstance().liveMetadata.value?.packageName
            val targetController = targetPackage?.let { pkg ->
                controllers.firstOrNull { it.packageName == pkg }
            }
            
            if (targetController != null) {
                val state = targetController.playbackState
                if (state != null && (state.state == PlaybackState.STATE_PLAYING || state.state == PlaybackState.STATE_PAUSED)) {
                    targetController.transportControls.skipToPrevious()
                    AppLogger.getInstance().log(TAG, "⏮️ Skipped to prev via notification action (target=$targetPackage)")
                    return
                }
            }

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

    private fun updateAdvancedDiagnostics() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val canPost = if (Build.VERSION.SDK_INT >= 36) {
            nm.canPostPromotedNotifications()
        } else {
            false
        }
        
        LyricRepository.getInstance().mergeDiagnostics { old ->
            old.copy(
                canPostPromoted = canPost,
                isIslandSupported = com.example.islandlyrics.core.platform.RomUtils.isIslandSupported(),
                islandVersion = com.example.islandlyrics.core.platform.RomUtils.getFocusProtocolVersion(this),
                hasFocusPermission = com.example.islandlyrics.core.platform.RomUtils.hasFocusPermission(this)
            )
        }
    }

    companion object {
        private const val TAG = "LyricService"
        private const val CHANNEL_ID = "lyric_capsule_channel"
        private const val NOTIFICATION_ID = 1001
        @Volatile var isAlive: Boolean = false
    }
}
