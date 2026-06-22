package com.example.islandlyrics.runtime.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.lifecycle.Observer
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.logging.LogManager
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.lyrics.source.LyricGetterSource
import com.example.islandlyrics.lyrics.source.LocalLyricSource
import com.example.islandlyrics.lyrics.source.LyriconSource
import com.example.islandlyrics.lyrics.source.OnlineLyricSource
import com.example.islandlyrics.lyrics.source.SuperLyricSource
import com.example.islandlyrics.runtime.foreground.DelayedStopController
import com.example.islandlyrics.runtime.foreground.LyricForegroundController
import com.example.islandlyrics.runtime.media.LyricMediaCommandRouter
import com.example.islandlyrics.runtime.media.MediaActionController
import com.example.islandlyrics.runtime.media.ProgressSyncController
import com.example.islandlyrics.runtime.metadata.AppNameResolver
import com.example.islandlyrics.runtime.metadata.MetadataLyricFetchCoordinator
import com.example.islandlyrics.runtime.metadata.StaticLyricDetector
import com.example.islandlyrics.runtime.render.RenderModeCoordinator
import com.example.islandlyrics.ui.overlay.capsule.LyricCapsuleHandler
import com.example.islandlyrics.ui.overlay.display.LyricDisplayManager
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsRenderer
import com.example.islandlyrics.ui.overlay.superisland.SuperIslandHandler
import com.example.islandlyrics.ui.overlay.config.CapsuleRenderMode

class LyricService : Service() {

    private var lastUpdateTime = 0L
    private var lastObservedTrackKey: String? = null

    // ── Lyric sources (each handles one acquisition path) ────────────────────
    private lateinit var localLyricSource: LocalLyricSource
    private lateinit var onlineLyricSource: OnlineLyricSource
    private lateinit var superLyricSource: SuperLyricSource
    private lateinit var lyricGetterSource: LyricGetterSource
    private lateinit var lyriconSource: LyriconSource

    private lateinit var progressSyncController: ProgressSyncController
    private lateinit var mediaActionController: MediaActionController
    private lateinit var mediaCommandRouter: LyricMediaCommandRouter
    private lateinit var appNameResolver: AppNameResolver
    private lateinit var metadataLyricFetchCoordinator: MetadataLyricFetchCoordinator
    private lateinit var foregroundController: LyricForegroundController
    private var renderModeCoordinator: RenderModeCoordinator? = null
    private var delayedStopController: DelayedStopController? = null

    private val lyricObserver = Observer<LyricRepository.LyricInfo?> { info ->
        if (info != null && info.lyric.isNotBlank()) {
            val lyric = info.lyric
            val metadata = LyricRepository.getInstance().liveMetadata.value
            val title = metadata?.title ?: ""
            val artist = metadata?.artist ?: ""
            val pkg = metadata?.packageName

            if (StaticLyricDetector.isStaticMetadataLyric(lyric, metadata)) {
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
        if (BuildConfig.DEBUG) {
            val currentLyric = LyricRepository.getInstance().liveLyric.value
            val hasLyric = currentLyric?.lyric?.isNotBlank() == true
            val hasMetadata = LyricRepository.getInstance().liveMetadata.value != null
            AppLogger.getInstance().log(
                TAG,
                "[NotifyTrace] playbackObserver playing=$playing isAlive=$isAlive superRunning=${superIslandHandler.isRunning} capsuleRunning=${capsuleHandler?.isRunning()} hasMetadata=$hasMetadata hasLyric=$hasLyric"
            )
        }
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
            val previousTrackKey = lastObservedTrackKey
            val trackChanged = trackKey != lastObservedTrackKey
            lastObservedTrackKey = trackKey
            if (BuildConfig.DEBUG) {
                AppLogger.getInstance().log(
                    TAG,
                    "[NotifyTrace] metadataObserver prevTrackKey=$previousTrackKey newTrackKey=$trackKey trackChanged=$trackChanged duration=${info.duration} rawTitle=${info.rawTitle} rawArtist=${info.rawArtist}"
                )
            }

            if (trackChanged) {
                resetForTrackChange(info)
            }

            metadataLyricFetchCoordinator.onMetadataChanged(info, trackChanged)
            
            // CRITICAL: Force immediate UI update to propagate new track metadata to SuperIsland
            displayManager.forceUpdate()
        }
    }

    private fun resetForTrackChange(info: LyricRepository.MediaInfo) {
        val repo = LyricRepository.getInstance()
        repo.updateLyric("", info.packageName, "System")
        repo.updateParsedLyrics(
            lines = emptyList(),
            hasSyllable = false,
            timelineCapability = LyricRepository.TimelineCapability.NONE
        )
        repo.updateCurrentLine(null)
        progressSyncController.resetLineIndex()
        progressSyncController.resetProgressForTrackChange(info.duration)

        if (repo.isPlaying.value == true) {
            progressSyncController.start()
            updateActiveHandler()
        }
    }

    // ── SuperLyric API stub is now in SuperLyricSource ────────────────────────
    // (Removed: superLyricStub inline definition)

    private val handler = Handler(Looper.getMainLooper())

    // Cached system services — initialized once in onCreate() to avoid repeated IPC
    private var cachedMediaSessionManager: android.media.session.MediaSessionManager? = null
    private var cachedMediaComponent: android.content.ComponentName? = null

    private var capsuleHandler: LyricCapsuleHandler? = null
    private lateinit var superIslandHandler: SuperIslandHandler
    private lateinit var displayManager: LyricDisplayManager
    private var floatingLyricsRenderer: FloatingLyricsRenderer? = null
    private var capsuleRenderMode = CapsuleRenderMode.XIAOMI_SUPER_ISLAND

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (LyricRepository.ACTION_REFRESH_DIAGNOSTICS == intent?.action) {
                AppLogger.getInstance().log(TAG, "Manual diagnostic refresh requested.")
                updateAdvancedDiagnostics()
            }
        }
    }

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == CapsuleRenderMode.PREF_KEY ||
            (key == AppPreferences.Keys.SUPER_ISLAND_ENABLED_LEGACY && !p.contains(CapsuleRenderMode.PREF_KEY))
        ) {
            capsuleRenderMode = CapsuleRenderMode.effective(p)
            AppLogger.getInstance().log(TAG, "Mode Switched via Settings -> capsuleRenderMode: $capsuleRenderMode")
            renderModeCoordinator?.setMode(capsuleRenderMode)
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
        } else if (key == "parser_rules_json") {
            syncPushLyricSources()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isAlive = true
        
        val prefs = AppPreferences.of(this)
        capsuleRenderMode = CapsuleRenderMode.effective(prefs)

        foregroundController = LyricForegroundController(this)
        foregroundController.createNotificationChannel()
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
        renderModeCoordinator = RenderModeCoordinator(
            displayManager,
            capsuleHandler,
            superIslandHandler
        )
        renderModeCoordinator!!.setMode(capsuleRenderMode)
        delayedStopController = DelayedStopController(
            handler = handler,
            prefsProvider = { AppPreferences.of(this@LyricService) },
            onStop = {
                progressSyncController.stop()
                renderModeCoordinator!!.stopForCurrentMode()
                AppLogger.getInstance().log(TAG, "🛑 Renderer stopped: Playback stopped (Delayed)")
            }
        )

        // Cache system services once here rather than in every IPC call
        cachedMediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
        cachedMediaComponent = android.content.ComponentName(this, MediaMonitorService::class.java)
        appNameResolver = AppNameResolver(this)
        progressSyncController = ProgressSyncController(
            repo = LyricRepository.getInstance(),
            appNameProvider = { pkg -> getAppName(pkg) },
            mediaSessionManagerProvider = { cachedMediaSessionManager },
            mediaComponentProvider = { cachedMediaComponent }
        )
        mediaActionController = MediaActionController(
            mediaSessionManagerProvider = { cachedMediaSessionManager },
            mediaComponentProvider = { cachedMediaComponent },
            targetPackageProvider = { LyricRepository.getInstance().liveMetadata.value?.packageName }
        )
        mediaCommandRouter = LyricMediaCommandRouter(this, mediaActionController)
        
        // Initialise lyric sources
        localLyricSource = LocalLyricSource(this)
        onlineLyricSource = OnlineLyricSource(this)
        superLyricSource  = SuperLyricSource(
            context = this,
            onlineLyricSource = onlineLyricSource
        )
        lyricGetterSource = LyricGetterSource(this, onlineLyricSource)
        lyriconSource = LyriconSource(this)
        metadataLyricFetchCoordinator = MetadataLyricFetchCoordinator(
            context = this,
            localLyricSource = localLyricSource,
            onlineLyricSource = onlineLyricSource
        )
        syncPushLyricSources()

        mediaCommandRouter.register()

        // Register refresh receiver
        val refreshFilter = IntentFilter(LyricRepository.ACTION_REFRESH_DIAGNOSTICS)
        registerReceiver(refreshReceiver, refreshFilter, Context.RECEIVER_NOT_EXPORTED)

        AppPreferences.of(this).registerOnSharedPreferenceChangeListener(prefsListener)

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

    private fun syncPushLyricSources() {
        if (!::superLyricSource.isInitialized || !::lyricGetterSource.isInitialized || !::lyriconSource.isInitialized) return
        superLyricSource.stop()
        lyricGetterSource.stop()
        lyriconSource.stop()
        superLyricSource.start()
        lyricGetterSource.start()
        lyriconSource.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "null"
        AppLogger.getInstance().log(TAG, "onStartCommand Received Action: $action")
        if (BuildConfig.DEBUG) {
            AppLogger.getInstance().log(
                TAG,
                "[NotifyTrace] onStartCommand action=$action startId=$startId isAlive=$isAlive mode=$capsuleRenderMode superRunning=${superIslandHandler.isRunning} capsuleRunning=${capsuleHandler?.isRunning()}"
            )
        }

        // 1. Proactively sync the render mode preference
        val prefs = AppPreferences.of(this)
        capsuleRenderMode = CapsuleRenderMode.effective(prefs)
        renderModeCoordinator?.setMode(capsuleRenderMode)
        syncFloatingLyricsState(prefs)

        // [Fix Task 1] Immediate Foreground Promotion
        foregroundController.createNotificationChannel()

        foregroundController.maybeWarmForegroundSlot(action)

        // 2. Ensure only the correct handler is running
        updateActiveHandler()

        // CRITICAL FIX: Prioritize the Rich Capsule Notification if it's already active
        // 3. Promote to foreground (avoid redundant channel creation)
        if (superIslandHandler.isRunning == true ||
            capsuleHandler?.isRunning() == true
        ) {
            displayManager.forceUpdate()
        }

        if ("ACTION_STOP" == action) {
            @Suppress("DEPRECATION")
            stopForeground(true)
            foregroundController.clearForegroundSlotPrimed("actionStop")
            stopSelf()
            return START_NOT_STICKY
        } else if (mediaCommandRouter.handleServiceAction(action)) {
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
        foregroundController.clearForegroundSlotPrimed("onDestroy")
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
        lyriconSource.stop()
        localLyricSource.cancel()
        onlineLyricSource.cancel()
        
        AppPreferences.of(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        val repo = LyricRepository.getInstance()
        repo.liveLyric.removeObserver(lyricObserver)
        repo.isPlaying.removeObserver(playbackObserver)
        repo.liveMetadata.removeObserver(metadataObserver)
        
        try {
            mediaCommandRouter.unregister()
            unregisterReceiver(refreshReceiver)
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Failed to unregister receivers: ${e.message}")
        }
        
        broadcastStatus("🔴 Stopped")
    }
    
    // ── Online lyric fetching is now in OnlineLyricSource ────────────────────
    // (Removed: tryFetchOnlineLyrics inline implementation)

    fun isForegroundSlotPrimed(): Boolean = foregroundController.isForegroundSlotPrimed()

    fun startForegroundTracked(notificationId: Int, notification: Notification, reason: String) {
        foregroundController.startForegroundTracked(notificationId, notification, reason)
    }

    private fun broadcastStatus(status: String) {
        val intent = Intent("com.example.islandlyrics.STATUS_UPDATE")
        intent.putExtra("status", status)
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
        foregroundController.inspectNotification(notification, nm)
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

    private fun getAppName(pkg: String): String {
        return appNameResolver.resolve(pkg)
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
        @Volatile var isAlive: Boolean = false
    }
}
