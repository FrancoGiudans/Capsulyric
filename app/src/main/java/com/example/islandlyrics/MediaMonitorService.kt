package com.example.islandlyrics

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
        AppLogger.getInstance().log(TAG, "Whitelist updated: ${allowedPackages.size} enabled apps.")
    }

    private fun updateControllers(controllers: List<MediaController>?) {
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

        if (controllers == null) return

        synchronized(activeControllers) {
            // 1. Identify valid new controllers (Track ALL for suggestion feature)
            val newControllers = controllers // No filter here!
            
            // 2. Find controllers to remove (present in old list but not in new list)
            val toRemove = ArrayList<MediaController>()
            controllerCallbacks.keys.forEach { existingController ->
                val stillExists = newControllers.any { it.sessionToken == existingController.sessionToken }
                if (!stillExists) {
                    toRemove.add(existingController)
                }
            }

            // 3. Remove old callbacks
            toRemove.forEach { controller ->
                val cb = controllerCallbacks.remove(controller)
                if (cb != null) {
                    controller.unregisterCallback(cb)
                }
                activeControllers.remove(controller)
            }
            
            // Refill activeControllers
            activeControllers.clear()
            activeControllers.addAll(newControllers)

            // 4. Add new callbacks
            newControllers.forEach { controller ->
                val isRegistered = controllerCallbacks.keys.any { it.sessionToken == controller.sessionToken }
                
                if (!isRegistered) {
                    val callback = object : MediaController.Callback() {
                        private val debounceHandler = android.os.Handler(android.os.Looper.getMainLooper())
                        private val updateRunnable = Runnable {
                            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                                updateMetadataIfPrimary(controller)
                            } else {
                                updateMetadataIfPrimary(controller)
                            }
                        }

                        override fun onPlaybackStateChanged(state: PlaybackState?) {
                            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
                            AppLogger.getInstance().log("Playback", "⏯️ State changed: ${if (isPlaying) "PLAYING" else "PAUSED/STOPPED"}")
                            checkServiceState()
                        }

                        override fun onMetadataChanged(metadata: MediaMetadata?) {
                            debounceHandler.removeCallbacks(updateRunnable)
                            debounceHandler.postDelayed(updateRunnable, 50)
                        }
                        
                        override fun onSessionDestroyed() {
                            super.onSessionDestroyed()
                            handler.post {
                                controllerCallbacks.remove(controller)
                                activeControllers.remove(controller)
                                checkServiceState()
                            }
                        }
                    }
                    
                    controller.registerCallback(callback)
                    controllerCallbacks[controller] = callback
                }
            }
        }

        // Initial check
        checkServiceState()

        // Force update metadata from the CURRENT primary
        val primary = getPrimaryController()
        if (primary != null) {
            updateMetadataIfPrimary(primary)
        }
    }

    private fun checkServiceState() {
        val primary = getPrimaryController()
        // FIX: Ensure apps must be explicitly enabled to start the service
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
            handler.postDelayed(stopRunnable, 500)
        }
    }

    private fun getPrimaryController(): MediaController? {
        synchronized(activeControllers) {
            // Priority 1: Playing
            for (c in activeControllers) {
                val state = c.playbackState
                if (state != null && state.state == PlaybackState.STATE_PLAYING) {
                    return c
                }
            }
            // Priority 2: First in list
            if (activeControllers.isNotEmpty()) {
                return activeControllers[0]
            }
            return null // None active
        }
    }

    private fun updateMetadataIfPrimary(controller: MediaController) {
        val primary = getPrimaryController() ?: return

        // Only update if this controller IS the primary one
        if (controller.packageName == primary.packageName) {
            val metadata = controller.metadata
            if (metadata != null) {
                extractMetadata(metadata, controller.packageName)
            }
        }
    }

    private fun extractMetadata(metadata: MediaMetadata, pkg: String) {
        val rawTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val rawArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val artBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) 
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val artHash = artBitmap?.hashCode() ?: 0
        
        // Always update metadata for the "Suggest Current App" UI
        LyricRepository.getInstance().updateMediaMetadata(rawTitle ?: "Unknown", rawArtist ?: "Unknown", pkg, duration)
        
        // CHECK WHITELIST - Block logic for unknown apps
        if (!allowedPackages.contains(pkg)) {
            AppLogger.getInstance().log("Meta", "ℹ️ Package $pkg not whitelisted. Metadata updated for UI only.")
            return
        }

        // Logic continues only for whitelisted apps...
        val metadataHash = java.util.Objects.hash(rawTitle, rawArtist, pkg, duration, artHash)
        if (metadataHash == lastMetadataHash) {
            AppLogger.getInstance().log("Meta-Debug", "⏭️ Skipping duplicate metadata for [$pkg]")
            return
        }
        lastMetadataHash = metadataHash

        AppLogger.getInstance().log("Meta-Debug", "📦 RAW Data from [$pkg]: Title=$rawTitle, Artist=$rawArtist")

        var finalTitle = rawTitle
        var finalArtist = rawArtist
        var finalLyric: String? = null

        // Load parser rule for this package
        val rule = ParserRuleHelper.getRuleForPackage(this, pkg)

        if (rule != null && rule.usesCarProtocol && rawArtist != null) {
            val (parsedTitle, parsedArtist, isSuccess) = parseWithRule(rawArtist, rule)
            
            if (isSuccess && parsedTitle.isNotEmpty()) {
                finalLyric = rawTitle 
                finalTitle = parsedTitle
                finalArtist = parsedArtist
                AppLogger.getInstance().log("Parser", "✅ Smart Match: Treated as Lyric.")
            } else {
                finalTitle = rawTitle
                finalArtist = rawArtist
            }
        } else {
             // Non-protocol or no rule
             finalTitle = rawTitle
             finalArtist = rawArtist
        }

        AppLogger.getInstance().log("Repo", "✅ Posting: Lyric=[${if (finalLyric != null) "YES" else "NO"}]")

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
    private fun parseWithRule(input: String, rule: ParserRule): Triple<String, String, Boolean> {
        val separator = rule.separatorPattern
        val splitIndex = input.indexOf(separator)
        
        if (splitIndex == -1) {
            // Separator not found, return original with empty artist, and FALSE for success
            // AppLogger.getInstance().log("Parser", "⚠️ Separator [$separator] not found in: $input")
            return Triple(input, "", false)
        }

        // Split the input
        val part1 = input.substring(0, splitIndex).trim()
        val part2 = input.substring(splitIndex + separator.length).trim()

        // Apply field order
        return when (rule.fieldOrder) {
            FieldOrder.ARTIST_TITLE -> Triple(part2, part1, true)  // part1=artist, part2=title
            FieldOrder.TITLE_ARTIST -> Triple(part1, part2, true)  // part1=title, part2=artist
        }
    }


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
    }
}
