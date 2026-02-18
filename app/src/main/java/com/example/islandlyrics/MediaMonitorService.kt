package com.example.islandlyrics

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log

class MediaMonitorService : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private var componentName: ComponentName? = null
    private var prefs: SharedPreferences? = null

    private val allowedPackages = HashSet<String>()
    
    // Explicitly use fully qualified names to avoid conflicts or ambiguity if imported
    private val activeControllers = java.util.ArrayList<MediaController>()
    // Fix: A Map to hold callbacks so we can unregister them later
    private val controllerCallbacks = java.util.HashMap<MediaController, MediaController.Callback>()
    
    // Deduplication: Track last metadata hash to avoid processing duplicates
    private var lastMetadataHash: Int = 0

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateControllers(controllers)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable {
        AppLogger.getInstance().log(TAG, "Stopping service after debounce.")
        LyricRepository.getInstance().updatePlaybackStatus(false) // Logic Fix: Move status update here
        val intent = Intent(this@MediaMonitorService, LyricService::class.java)
        intent.action = "ACTION_STOP"
        startService(intent)
    }
    
    // Health check mechanism
    private val healthCheckRunnable = object : Runnable {
        override fun run() {
            // Verify connection is still valid
            if (isConnected && mediaSessionManager != null && componentName != null) {
                try {
                    // Test access - this will throw if permission is revoked
                    mediaSessionManager?.getActiveSessions(componentName)
                    AppLogger.getInstance().log(TAG, "Health Check: OK")
                } catch (e: SecurityException) {
                    AppLogger.getInstance().log(TAG, "Health Check: FAILED - Permission lost")
                    isConnected = false
                    // Request rebind
                    requestRebind(this@MediaMonitorService)
                }
            }
            // Schedule next check - REFACTORED: Removed per user request to reduce overhead
            // handler.postDelayed(this, 30000)
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (PREF_PARSER_RULES == key) {
            loadWhitelist()
            recheckSessions()
        } else if ("service_enabled" == key) {
            recheckSessions()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        // Load initial whitelist
        loadWhitelist()
        // Register pref listener
        prefs?.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        AppLogger.getInstance().log(TAG, "onListenerConnected - Service binding initiated")
        
        // CRITICAL FIX: Ensure SharedPreferences is initialized
        // This may be called before onCreate() in some rebind scenarios
        if (prefs == null) {
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs?.registerOnSharedPreferenceChangeListener(prefListener)
            AppLogger.getInstance().log(TAG, "SharedPreferences initialized in onListenerConnected")
        }
        
        // CRITICAL FIX: Reload whitelist to ensure it's current
        // This prevents stale or empty whitelist after service restart
        loadWhitelist()
        
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        componentName = ComponentName(this, MediaMonitorService::class.java)

        mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)

        // Start health check monitoring
        handler.postDelayed(healthCheckRunnable, 30000)

        // CRITICAL FIX: Add delayed retry mechanism for getActiveSessions
        // Permissions may not be fully effective immediately after rebind
        handler.postDelayed({
            try {
                val controllers = mediaSessionManager?.getActiveSessions(componentName)
                AppLogger.getInstance().log(TAG, "Successfully retrieved ${controllers?.size ?: 0} active sessions")
                updateControllers(controllers)
            } catch (e: SecurityException) {
                AppLogger.getInstance().log(TAG, "Security Error on initial check: ${e.message}")
                // Retry once after 200ms in case permission is still being granted
                handler.postDelayed({
                    try {
                        val controllers = mediaSessionManager?.getActiveSessions(componentName)
                        AppLogger.getInstance().log(TAG, "Retry successful: ${controllers?.size ?: 0} sessions")
                        updateControllers(controllers)
                    } catch (e2: SecurityException) {
                        AppLogger.getInstance().log(TAG, "Retry failed: ${e2.message} - Permission may need manual grant")
                    }
                }, 200)
            }
        }, 100)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        AppLogger.getInstance().log(TAG, "onListenerDisconnected - Service unbound")
        
        // Stop health check
        handler.removeCallbacks(healthCheckRunnable)
        
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        
        // Clean up all callbacks
        synchronized(activeControllers) {
            controllerCallbacks.forEach { (controller, callback) ->
                controller.unregisterCallback(callback)
            }
            controllerCallbacks.clear()
            activeControllers.clear()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs?.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private fun recheckSessions() {
        if (mediaSessionManager != null && componentName != null) {
            try {
                updateControllers(mediaSessionManager?.getActiveSessions(componentName))
            } catch (e: SecurityException) {
                AppLogger.getInstance().log(TAG, "Error refreshing sessions: ${e.message}")
            }
        }
    }

    private fun loadWhitelist() {
        // Use ParserRuleHelper to get all enabled packages (replaces old WhitelistHelper)
        val set = ParserRuleHelper.getEnabledPackages(this)
        allowedPackages.clear()
        allowedPackages.addAll(set)
        AppLogger.getInstance().log(TAG, "Whitelist updated: ${allowedPackages.size} enabled apps: ${allowedPackages.joinToString()}")
    }

    private fun updateControllers(controllers: List<MediaController>?) {
        // Force reload whitelist to ensure we are always up to date
        loadWhitelist()

        // CHECK MASTER SWITCH
        val isServiceEnabled = prefs?.getBoolean("service_enabled", true) ?: true
        if (!isServiceEnabled) {
            AppLogger.getInstance().log(TAG, "Master Switch OFF. Ignoring updates.")
            synchronized(activeControllers) {
                controllerCallbacks.forEach { (controller, callback) ->
                    controller.unregisterCallback(callback)
                }
                controllerCallbacks.clear()
                activeControllers.clear()
            }
            LyricRepository.getInstance().updatePlaybackStatus(false)
            return
        }
        
        // Robust update: Wipe and Replace
        // This eliminates stale state issues at the cost of slight overhead
        synchronized(activeControllers) {
            // 1. Unregister ALL old
            controllerCallbacks.forEach { (controller, callback) ->
                try {
                    controller.unregisterCallback(callback)
                } catch (e: Exception) { /* Ignore */ }
            }
            controllerCallbacks.clear()
            activeControllers.clear()

            // 2. Register ALL new (if valid)
            if (controllers != null) {
                controllers.forEach { controller ->
                    try {
                        val callback = object : MediaController.Callback() {
                            private val debounceHandler = android.os.Handler(android.os.Looper.getMainLooper())
                            private val updateRunnable = Runnable {
                                updateMetadataIfPrimary(controller)
                            }

                            override fun onPlaybackStateChanged(state: PlaybackState?) {
                                checkServiceState() // Priority might have changed
                                updateMetadataIfPrimary(controller)
                            }

                            override fun onMetadataChanged(metadata: MediaMetadata?) {
                                debounceHandler.removeCallbacks(updateRunnable)
                                debounceHandler.postDelayed(updateRunnable, 100)
                            }
                            
                            override fun onSessionDestroyed() {
                                handler.post {
                                    recheckSessions() // Force full refresh
                                }
                            }
                        }
                        
                        controller.registerCallback(callback)
                        controllerCallbacks[controller] = callback
                        activeControllers.add(controller)
                        
                    } catch (e: Exception) {
                        AppLogger.getInstance().e(TAG, "Failed to hook controller: ${controller.packageName}")
                    }
                }
            }
        }

        // Initial check
        checkServiceState()

        // Force update from primary
        val primary = getPrimaryController()
        if (primary != null) {
            updateMetadataIfPrimary(primary)
        }
        
        // Report Diagnostics
        val diag = ServiceDiagnostics(
            isConnected = isConnected,
            totalControllers = activeControllers.size,
            whitelistedControllers = activeControllers.count { allowedPackages.contains(it.packageName) },
            primaryPackage = primary?.packageName ?: "None",
            whitelistSize = allowedPackages.size,
            lastUpdateParams = "Playing: ${primary?.playbackState?.state == PlaybackState.STATE_PLAYING}"
        )
        LyricRepository.getInstance().updateDiagnostics(diag)
    }

    private fun checkServiceState() {
        val primary = getPrimaryController()
        // STRICT: Only start service if primary is WHITELISTED
        val isWhitelisted = allowedPackages.contains(primary?.packageName)
        val isPlaying = isWhitelisted && primary?.playbackState?.state == PlaybackState.STATE_PLAYING

        if (isPlaying) {
            // Cancel any pending stop
            handler.removeCallbacks(stopRunnable)

            // Start/Update Service
            val intent = Intent(this, LyricService::class.java)
            startForegroundService(intent)

            // Sync State
            LyricRepository.getInstance().updatePlaybackStatus(true)

        } else {
            // Debounce Stop
            handler.removeCallbacks(stopRunnable)
            
            // Fix: Use user preference for delay
            // Default 500ms debounce for "Immediate" to handle track switches
            val dismissDelay = prefs?.getLong("notification_dismiss_delay", 0L) ?: 0L
            val finalDelay = if (dismissDelay == 0L) 500L else dismissDelay

            AppLogger.getInstance().log(TAG, "⏸️ Playback stopped/paused. Scheduling Stop in ${finalDelay}ms")
            handler.postDelayed(stopRunnable, finalDelay)
        }
    }

    private fun getPrimaryController(): MediaController? {
        synchronized(activeControllers) {
            // Priority 1: Whitelisted + Playing/Buffering
            // We want to show lyrics for these immediately
            val whitelistedPlaying = activeControllers.firstOrNull { 
                allowedPackages.contains(it.packageName) && 
                (it.playbackState?.state == PlaybackState.STATE_PLAYING || 
                 it.playbackState?.state == PlaybackState.STATE_BUFFERING)
            }
            if (whitelistedPlaying != null) {
                // AppLogger.getInstance().log("Select", "Selected Priority 1 (Whitelisted+Playing): ${whitelistedPlaying.packageName}")
                return whitelistedPlaying
            }

            // Priority 2: Whitelisted + Paused
            // Keep showing lyrics if user just paused music
            val whitelistedPaused = activeControllers.firstOrNull {
                 allowedPackages.contains(it.packageName)
            }
            if (whitelistedPaused != null) {
                // AppLogger.getInstance().log("Select", "Selected Priority 2 (Whitelisted+Paused): ${whitelistedPaused.packageName}")
                return whitelistedPaused
            }

            // Priority 3: Any Playing (Propagate for SUGGESTION only)
            // This is critical for the "Add Rule" feature to discover new apps
            val anyPlaying = activeControllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            }
            if (anyPlaying != null) {
                // AppLogger.getInstance().log("Select", "Selected Priority 3 (Any+Playing): ${anyPlaying.packageName}")
                return anyPlaying
            }

            // Priority 4: Oldest active (Fallback)
            return activeControllers.firstOrNull()
        }
    }

    private fun updateMetadataIfPrimary(controller: MediaController) {
        val metadata = controller.metadata ?: return
        val playbackState = controller.playbackState
        val pkg = controller.packageName
        
        val rawTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val rawArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        // 1. ALWAYS propogate via SUGGESTION channel (for ParserRuleScreen)
        // Logic Fix: Allow ANY playing app to update suggestion, even if not primary
        if (playbackState?.state == PlaybackState.STATE_PLAYING) {
            LyricRepository.getInstance().updateSuggestionMetadata(
                title = rawTitle ?: "Unknown",
                artist = rawArtist ?: "Unknown",
                packageName = pkg,
                duration = duration
            )
        }

        val primary = getPrimaryController() ?: return

        // Only process if this IS the primary controller
        if (controller.packageName != primary.packageName) return
        
        // 2. CHECK WHITELIST - Strict blocking for Main UI
        if (!allowedPackages.contains(pkg)) {
            AppLogger.getInstance().log("Meta", "⛔ Ignored non-whitelisted: $pkg (Sent to suggestion only)")
            return 
        }

        // --- VALID WHITELISTED PROCESSING BELOW ---

        val artBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) 
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val artHash = artBitmap?.hashCode() ?: 0
        
        val metadataHash = java.util.Objects.hash(rawTitle, rawArtist, pkg, duration, artHash)
        if (metadataHash == lastMetadataHash) {
            return
        }
        lastMetadataHash = metadataHash

        AppLogger.getInstance().log("Meta", "✅ Processing Whitelisted: $pkg - $rawTitle")

        var finalTitle = rawTitle
        var finalArtist = rawArtist
        var finalLyric: String? = null

        // Load parser rule for this package
        val rule = ParserRuleHelper.getRuleForPackage(this, pkg)


        // Apply parsing rules if enabled
        if (rule != null && rule.enabled) {
            // Case 1: Title contains "Artist - Title" (or similar)
            val titleParse = ParserRuleHelper.parseWithRule(rawTitle ?: "", rule)
            if (titleParse.third) {
                finalTitle = titleParse.first
                finalArtist = titleParse.second
                // finalLyric remains null
            } else {
                // Case 2: Artist contains "Artist - Title" AND Title contains Lyrics
                // This matches the user's reported issue: "歌名部分显示为歌词，歌手部分显示为歌名-歌手"
                val artistParse = ParserRuleHelper.parseWithRule(rawArtist ?: "", rule)
                if (artistParse.third) {
                    finalTitle = artistParse.first
                    finalArtist = artistParse.second
                    // If we successfully parsed the artist field, the "Title" field was likely lyrics
                    if (!rawTitle.isNullOrEmpty()) {
                        finalLyric = rawTitle
                        AppLogger.getInstance().log("Parser", "💡 Detected Lyrics in Title field! Swapped.")
                    }
                }
            }
        } 


        // Update PUBLIC Metadata (Main UI)
        LyricRepository.getInstance().updateMediaMetadata(finalTitle ?: "Unknown", finalArtist ?: "Unknown", pkg, duration)

        // Update Lyric if available
        if (finalLyric != null) {
            LyricRepository.getInstance().updateLyric(finalLyric, getAppName(pkg))
        }

        // Use already extracted artBitmap
        LyricRepository.getInstance().updateAlbumArt(artBitmap)
    }

    /**
     * Parse notification text using configurable separator and field order.
     * @param input Raw text like "Artist-Title" or "Title | Artist"
     * @param rule Parser rule with separator and field order
     * @return Triple(title, artist, isSuccess)
     */
    // Removed private parseWithRule, use ParserRuleHelper instead


    private fun getAppName(packageName: String?): String {
        if (packageName == null) return "Music"
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName // Fallback to package name if not found
        }
    }

    companion object {
        private const val TAG = "MediaMonitorService"
        private const val PREFS_NAME = "IslandLyricsPrefs"
        private const val PREF_PARSER_RULES = "parser_rules_json"
        
        var isConnected = false
        
        fun requestRebind(context: Context) {
            val componentName = ComponentName(context, MediaMonitorService::class.java)
            AppLogger.getInstance().d(TAG, "Requesting rebind for $componentName")
            try {
                NotificationListenerService.requestRebind(componentName)
            } catch (e: Exception) {
                AppLogger.getInstance().e(TAG, "Failed to request rebind: ${e.message}")
            }
        }

        fun forceRebind(context: Context) {
             val pm = context.packageManager
             val componentName = ComponentName(context, MediaMonitorService::class.java)
             AppLogger.getInstance().log(TAG, "☢️ Executing FORCE REBIND (Component Toggle) for $componentName")
             
             try {
                 // Disable
                 pm.setComponentEnabledSetting(
                     componentName,
                     PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                     PackageManager.DONT_KILL_APP
                 )
                 
                 // Enable
                 pm.setComponentEnabledSetting(
                     componentName,
                     PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                     PackageManager.DONT_KILL_APP
                 )
                 
                 AppLogger.getInstance().log(TAG, "☢️ Force Rebind Toggle Complete")
             } catch (e: Exception) {
                 AppLogger.getInstance().e(TAG, "Failed to force rebind: ${e.message}")
             }
        }
    }
}
