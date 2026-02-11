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
            // Schedule next check
            handler.postDelayed(this, 30000) // Every 30 seconds
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
            // 1. Identify valid new controllers (whitelisted)
            val newControllers = controllers.filter { allowedPackages.contains(it.packageName) }
            
            // 2. Find controllers to remove (present in old list but not in new list)
            // Note: MediaController equals() might not be reliable, checking by reference or token is better.
            // But usually session token is key. Here we iterate our map keys.
            val toRemove = ArrayList<MediaController>()
            controllerCallbacks.keys.forEach { existingController ->
                // Check if existingController is present in newControllers (by comparing session token or just object equality if valid)
                // MediaController.equals() checks internal mSessionBinder
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
                activeControllers.remove(controller) // Should use iterator if iterating, but we are iterating toRemove
            }
            
            // Refill activeControllers to match the new filtered list order (important for priority)
            activeControllers.clear()
            activeControllers.addAll(newControllers)

            // 4. Add new callbacks
            newControllers.forEach { controller ->
                // Check if we already have a callback for this controller (by token)
                val isRegistered = controllerCallbacks.keys.any { it.sessionToken == controller.sessionToken }
                
                if (!isRegistered) {
                    val callback = object : MediaController.Callback() {
                        // Debounce handler to prevent rapid/incomplete updates
                        private val debounceHandler = android.os.Handler(android.os.Looper.getMainLooper())
                        private val updateRunnable = Runnable {
                            // Double check state before updating
                            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                                updateMetadataIfPrimary(controller)
                            } else {
                                // If paused/stopped, update immediately to reflect state change
                                updateMetadataIfPrimary(controller)
                            }
                        }

                        override fun onPlaybackStateChanged(state: PlaybackState?) {
                            // CRITICAL FIX: Do NOT update repository here directly!
                            // That bypasses the debounce logic in checkServiceState.
                            // Just trigger the check.
                            
                            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
                            AppLogger.getInstance().log("Playback", "⏯️ State changed: ${if (isPlaying) "PLAYING" else "PAUSED/STOPPED"}")
                            
                            checkServiceState()
                        }

                        override fun onMetadataChanged(metadata: MediaMetadata?) {
                            // Add small delay (50ms) to allow notification to fully populate
                            // This helps with apps that update state before metadata (race condition)
                            debounceHandler.removeCallbacks(updateRunnable)
                            debounceHandler.postDelayed(updateRunnable, 50)
                        }
                        
                        override fun onSessionDestroyed() {
                            super.onSessionDestroyed()
                            // Self-cleanup if session dies
                            // We need to run this on the handler thread or safely
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
        val isPlaying = primary?.playbackState?.state == PlaybackState.STATE_PLAYING

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
            
            // LOGIC FIX: Status update moved to stopRunnable to respect debounce
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
            // Priority 2: First in list (or most recent?) - API list order usually implies recency
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

        // DEDUPLICATION: Include Duration and Bitmap to catch partial updates
        // Some apps send title first, then update duration or album art later.
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val artBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) 
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val artHash = artBitmap?.hashCode() ?: 0
        
        val metadataHash = java.util.Objects.hash(rawTitle, rawArtist, pkg, duration, artHash)
        if (metadataHash == lastMetadataHash) {
            AppLogger.getInstance().log("Meta-Debug", "⏭️ Skipping duplicate metadata for [$pkg]")
            return
        }
        lastMetadataHash = metadataHash

        AppLogger.getInstance().log("Meta-Debug", "📦 RAW Data from [$pkg]:")
        AppLogger.getInstance().log("Meta-Debug", "   ↳ Title : $rawTitle")
        AppLogger.getInstance().log("Meta-Debug", "   ↳ Artist: $rawArtist")

        var finalTitle = rawTitle
        var finalArtist = rawArtist
        var finalLyric: String? = null

        // Load parser rule for this package (configurable system)
        val rule = ParserRuleHelper.getRuleForPackage(this, pkg)

        if (rule != null && rule.usesCarProtocol && rawArtist != null) {
            // Car protocol mode: Parse artist field to extract title/artist, title field contains lyric
            AppLogger.getInstance().log("Parser", "⚙️ Applying car protocol rule: separator=[${rule.separatorPattern}], order=${rule.fieldOrder}")
            
            // Parse using configurable rule
            val (parsedTitle, parsedArtist) = parseWithRule(rawArtist, rule)
            
            if (parsedTitle.isNotEmpty()) {
                finalLyric = rawTitle  // Title field holds lyric
                finalTitle = parsedTitle
                finalArtist = parsedArtist
                AppLogger.getInstance().log("Parser", "✅ Parsed: Title=[$finalTitle], Artist=[$finalArtist]")
            }
        } else if (rule != null && !rule.usesCarProtocol) {
            // Non-car-protocol mode: Use raw metadata directly (for apps like Kugou, Apple Music)
            AppLogger.getInstance().log("Parser", "ℹ️ Non-car-protocol app, using raw metadata (no lyric extraction)")
            finalTitle = rawTitle
            finalArtist = rawArtist
            // finalLyric remains null (no lyric support)
        } else {
            // No rule found, use raw data
            AppLogger.getInstance().log("Parser", "⚠️ No parser rule found for [$pkg], using raw metadata")
        }

        AppLogger.getInstance().log("Repo", "✅ Posting: Title=[$finalTitle] Artist=[$finalArtist] Lyric=[${if (finalLyric != null) "YES" else "NO"}]")

        // Push to repository
        if (finalLyric != null) {
            LyricRepository.getInstance().updateLyric(finalLyric, getAppName(pkg))
        }

        // Use already extracted duration
        LyricRepository.getInstance().updateMediaMetadata(finalTitle ?: "Unknown", finalArtist ?: "Unknown", pkg, duration)

        // Use already extracted artBitmap
        LyricRepository.getInstance().updateAlbumArt(artBitmap)
    }

    /**
     * Parse notification text using configurable separator and field order.
     * @param input Raw text like "Artist-Title" or "Title | Artist"
     * @param rule Parser rule with separator and field order
     * @return Pair of (title, artist)
     */
    private fun parseWithRule(input: String, rule: ParserRule): Pair<String, String> {
        val separator = rule.separatorPattern
        val splitIndex = input.indexOf(separator)
        
        if (splitIndex == -1) {
            // Separator not found, return original with empty artist
            AppLogger.getInstance().log("Parser", "⚠️ Separator [$separator] not found in: $input")
            return Pair(input, "")
        }

        // Split the input
        val part1 = input.substring(0, splitIndex).trim()
        val part2 = input.substring(splitIndex + separator.length).trim()

        // Apply field order
        return when (rule.fieldOrder) {
            FieldOrder.ARTIST_TITLE -> Pair(part2, part1)  // part1=artist, part2=title
            FieldOrder.TITLE_ARTIST -> Pair(part1, part2)  // part1=title, part2=artist
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
            Log.d(TAG, "Requesting rebind for $componentName")
            try {
                NotificationListenerService.requestRebind(componentName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request rebind", e)
            }
        }
    }
}
