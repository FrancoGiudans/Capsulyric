package com.example.islandlyrics.service

import android.content.ComponentName
import com.example.islandlyrics.data.ServiceDiagnostics
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.data.lyric.SuperLyricSource
import com.example.islandlyrics.data.WhitelistHelper
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.feature.parserrule.material.ParserRuleScreen
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
    private var lastComputedIsPlaying: Boolean? = null
    
    // Debounce Token
    private val updateToken = Any()
    
    // Deduplication: Track last controller signatures
    private var lastControllerSignatures: String = ""

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        // Debounce updates (200ms)
        handler.removeCallbacksAndMessages(updateToken)
        val r = Runnable { updateControllers(controllers) }
        handler.postAtTime(r, updateToken, android.os.SystemClock.uptimeMillis() + 200)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable {
        AppLogger.getInstance().log(TAG, "Stopping service after debounce.")
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
        instance = this
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
        if (instance === this) instance = null
        prefs?.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    /**
     * Re-reads the MediaSession for [packageName] and updates album art in [LyricRepository].
     */
    private fun doRefreshAlbumArtForPackage(packageName: String) {
        synchronized(activeControllers) {
            val controller = activeControllers.firstOrNull { it.packageName == packageName } ?: return
            val artBitmap = controller.metadata
                ?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: controller.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            LyricRepository.getInstance().updateAlbumArt(artBitmap)
            AppLogger.getInstance().d(TAG, "Album art refreshed for $packageName (active session)")
        }
    }

    fun recheckSessions() {
        if (mediaSessionManager != null && componentName != null) {
            try {
                // FORCE UPDATE: Reset metadata hash so next update propagates immediately
                lastMetadataHash = 0
                lastComputedIsPlaying = null
                lastControllerSignatures = "" // Reset signature deduplication
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
        
        // DEDUPLICATION CHECK
        val currentSignatures = controllers?.joinToString("|") { "${it.packageName}@${it.hashCode()}" } ?: "null"
        if (currentSignatures == lastControllerSignatures) {
             AppLogger.getInstance().d(TAG, "Duplicate session update ignored.")
             return
        }
        lastControllerSignatures = currentSignatures
        AppLogger.getInstance().d(TAG, "Processing new session update: $currentSignatures")
        
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
                            override fun onPlaybackStateChanged(state: PlaybackState?) {
                                checkServiceState() // Priority might have changed
                                
                                val primary = getPrimaryController()
                                if (primary != null && primary.packageName == controller.packageName) {
                                    // Buggy apps don't always fire onMetadataChanged (e.g., Xiaomi Music with SuperLyric module).
                                    // We manually compute the un-parsed hash here. 
                                    // If it differs from lastMetadataHash, they covertly changed the song!
                                    val meta = primary.metadata
                                    if (meta != null) {
                                        val artHash = (meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART))?.hashCode() ?: 0
                                        val currentHash = java.util.Objects.hash(
                                            meta.getString(MediaMetadata.METADATA_KEY_TITLE),
                                            meta.getString(MediaMetadata.METADATA_KEY_ARTIST),
                                            primary.packageName,
                                            meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
                                            artHash
                                        )
                                        if (currentHash != lastMetadataHash) {
                                            AppLogger.getInstance().d(TAG, "Caught unannounced metadata change via playback state!")
                                            updateMetadataIfPrimary(primary)
                                        }
                                    }
                                }
                                
                                // Suggestion update: find the best candidate for recommendation
                                // This ensures recommendations refresh when any app starts playing
                                val suggestion = getSuggestionController()
                                if (suggestion != null) {
                                    updateMetadataForSuggestion(suggestion)
                                }
                            }

                            override fun onMetadataChanged(metadata: MediaMetadata?) {
                                updateMetadataIfPrimary(controller)
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

        // Suggestion update: find the best candidate for recommendation
        val suggestionCandidate = getSuggestionController()
        if (suggestionCandidate != null) {
            updateMetadataForSuggestion(suggestionCandidate)
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
        val state = primary?.playbackState?.state
        val isPlaying = isWhitelisted && (
            state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_BUFFERING ||
            state == PlaybackState.STATE_CONNECTING ||
            state == PlaybackState.STATE_SKIPPING_TO_NEXT ||
            state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
            state == PlaybackState.STATE_FAST_FORWARDING ||
            state == PlaybackState.STATE_REWINDING
        )

        // Only act if the state has genuinely changed to avoid log spam and infinite stop latency
        if (lastComputedIsPlaying == isPlaying) {
            return
        }
        lastComputedIsPlaying = isPlaying

        if (isPlaying) {
            // Cancel any pending stop
            handler.removeCallbacks(stopRunnable)

            // Start/Update Service
            val intent = Intent(this, LyricService::class.java)
            try {
                startForegroundService(intent)
            } catch (e: Exception) {
                AppLogger.getInstance().e(TAG, "Exception starting foreground service: ${e.message}")
                try {
                    startService(intent) // Fallback for strict OS limits
                } catch (e2: Exception) {
                    AppLogger.getInstance().e(TAG, "Fallback startService also failed: ${e2.message}")
                }
            }

            // Sync State
            LyricRepository.getInstance().updatePlaybackStatus(true)

        } else {
            // Debounce Stop
            handler.removeCallbacks(stopRunnable)
            
            // IMMEDIATELY sync the paused state so LyricService's progress updater stops running
            // (LyricService has its own UI debounce logic, so the UI won't flicker)
            LyricRepository.getInstance().updatePlaybackStatus(false)
            
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
            // Priority 1: Whitelisted + Playing/Buffering/Skipping
            // We want to show lyrics for these immediately
            val whitelistedPlaying = activeControllers.firstOrNull { 
                val st = it.playbackState?.state
                allowedPackages.contains(it.packageName) && 
                (st == PlaybackState.STATE_PLAYING || 
                 st == PlaybackState.STATE_BUFFERING ||
                 st == PlaybackState.STATE_CONNECTING ||
                 st == PlaybackState.STATE_SKIPPING_TO_NEXT ||
                 st == PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
                 st == PlaybackState.STATE_FAST_FORWARDING ||
                 st == PlaybackState.STATE_REWINDING)
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

            // Priority 3: Oldest active (Fallback)
            return activeControllers.firstOrNull()
        }
    }

    private fun getSuggestionController(): MediaController? {
        synchronized(activeControllers) {
            // Priority 1: Playing + NOT whitelisted (The most likely one to add)
            val playingNotWhitelisted = activeControllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING && !allowedPackages.contains(it.packageName)
            }
            if (playingNotWhitelisted != null) return playingNotWhitelisted

            // Priority 2: Any Playing
            val anyPlaying = activeControllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            }
            if (anyPlaying != null) return anyPlaying

            // Priority 3: Any whitelisted but NOT playing (Still useful as backup)
            val whitelisted = activeControllers.firstOrNull {
                allowedPackages.contains(it.packageName)
            }
            if (whitelisted != null) return whitelisted

            return activeControllers.firstOrNull()
        }
    }

    private fun updateMetadataForSuggestion(controller: MediaController) {
        val metadata = controller.metadata ?: return
        val pkg = controller.packageName
        
        val rawTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val rawArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        LyricRepository.getInstance().updateSuggestionMetadata(
            title = rawTitle ?: "Unknown",
            artist = rawArtist ?: "Unknown",
            packageName = pkg,
            duration = duration
        )
    }

    private fun updateMetadataIfPrimary(controller: MediaController) {
        val metadata = controller.metadata ?: return
        val playbackState = controller.playbackState
        val pkg = controller.packageName
        
        val rawTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val rawArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        // (Suggestion logic moved to updateMetadataForSuggestion)

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
        
        // SKIP DUPLICATE CHECK if this is a forced update (from whitelist change)
        // We detect this via a transient flag or simply by checking if the LAST hash was from a DIFFERENT package?
        // Simpler: We just rely on the fact that if we switched primary, the hash is likely different.
        // BUT, if we re-enabled the SAME app, the hash might be identical to what we had before we disabled it?
        // Actually, `activeControllers` mechanism in `updateControllers` handles the "switch", 
        // but `updateMetadataIfPrimary` is called explicitly. 
        // To be safe, we can relax this check OR ensure `lastMetadataHash` is reset in `recheckSessions`.
        // For now, let's keep the check but ensure `recheckSessions` clears the last hash.
        
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

        // Try restoring state if this is the first update for this package after a service restart
        tryRestoreParserState(pkg, rawArtist, duration)

        // Apply parsing rules if enabled
        if (rule != null && rule.enabled) {

            val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
            val displayTitle = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)

            // --- Anti-False-Positive Heuristics ---
            val isInstrumental = rawTitle?.contains("Instrument", ignoreCase = true) == true ||
                                 rawTitle?.contains("纯音乐") == true ||
                                 rawTitle?.contains("伴奏") == true
            // NOTE: We only compare against album, NOT displayTitle.
            // On many devices (Xiaomi etc.), DISPLAY_TITLE == TITLE, making the check useless.
            val isLikelyRealTitle = !album.isNullOrEmpty() && rawTitle == album
            val isFalsePositiveLyric = isInstrumental || isLikelyRealTitle

            // --- Read Previous State ---
            val prevTitle  = packageLastTitle[pkg]
            val prevArtist = packageLastArtist[pkg]
            val prevDuration = packageLastDuration[pkg] ?: 0L
            var isDynamicLyricMode = packageLyricMode[pkg] ?: false

            // New-song detection: duration change of >1000ms is a reliable indicator
            val durationDiffers = duration > 0 && prevDuration > 0 &&
                                  Math.abs(duration - prevDuration) > 1000L
            if (durationDiffers) {
                // Reset all cached state for this package
                isDynamicLyricMode = false
                packageRealTitle.remove(pkg)
                packageRealArtist.remove(pkg)
                AppLogger.getInstance().log("Parser", "🔄 New song detected for $pkg, resetting lyric state.")
            }
            
            // --- Core Dynamic Mode Detection ---
            // If title changes while artist stays the same → it's a live lyric feed!
            // This is the PRIMARY detection mechanism for most apps.
            if (!durationDiffers && rawTitle != prevTitle && 
                !rawTitle.isNullOrEmpty() && !prevTitle.isNullOrEmpty() &&
                rawArtist == prevArtist) {
                isDynamicLyricMode = true
                AppLogger.getInstance().log("Parser", "🔀 Title changed mid-song! Dynamic lyric mode ON. '$prevTitle' → '$rawTitle'")
            }

            // --- STRATEGY 1: Transition Detection (highest confidence) ---
            // Some apps enter lyric mode by changing BOTH Title and Artist simultaneously.
            // The new Artist field begins with the old Title: "RealTitle-Artist - Album".
            val isLyricTransition = !prevTitle.isNullOrEmpty() &&
                                    !prevArtist.isNullOrEmpty() &&
                                    rawTitle != prevTitle &&
                                    rawArtist != prevArtist &&
                                    !isFalsePositiveLyric &&
                                    (rawArtist?.startsWith(prevTitle) == true ||
                                     rawArtist?.startsWith(prevTitle + "-") == true ||
                                     rawArtist?.contains("$prevTitle-") == true ||
                                     rawArtist?.contains("$prevTitle ") == true)

            if (isLyricTransition) {
                packageRealTitle[pkg]  = prevTitle
                packageRealArtist[pkg] = prevArtist
                isDynamicLyricMode = true
                AppLogger.getInstance().log("Parser", "🎯 Lyric transition detected for $pkg! RealTitle='$prevTitle'")
            }

            // --- STRATEGY 2: Sustained Dynamic Mode ---
            // If we have established dynamic mode before (but no transition this round),
            // use the cached real identity.
            if (isDynamicLyricMode && !isLyricTransition) {
                val cachedTitle  = packageRealTitle[pkg]
                val cachedArtist = packageRealArtist[pkg]
                if (!cachedTitle.isNullOrEmpty() && !isFalsePositiveLyric) {
                    // We have a cached identity from a previous transition or parse
                    finalTitle  = cachedTitle
                    finalArtist = cachedArtist ?: rawArtist
                    finalLyric  = rawTitle
                    AppLogger.getInstance().log("Parser", "🎵 Dynamic lyric (cached identity): '$rawTitle'")
                } else if (!isFalsePositiveLyric) {
                    // Dynamic mode confirmed (title keeps changing) but no cached identity yet.
                    // This happens with apps like 小米音乐 where rawArtist is always "songTitle-artist"
                    // from the start (no transition moment). Since we've PROVEN it's lyrics mode
                    // (title changed while artist stayed the same), parsing the artist is now SAFE.
                    val artistParse = ParserRuleHelper.parseWithRule(rawArtist ?: "", rule)
                    if (artistParse.third) {
                        val parsedTitle  = artistParse.first
                        val parsedArtist = artistParse.second
                        // Cache so we don't need to re-parse every line
                        packageRealTitle[pkg]  = parsedTitle
                        packageRealArtist[pkg] = parsedArtist
                        finalTitle  = parsedTitle
                        finalArtist = parsedArtist
                        finalLyric  = rawTitle
                        AppLogger.getInstance().log("Parser", "🎯 Dynamic mode: parsed & cached identity from artist field. Title='$parsedTitle' Lyric='$rawTitle'")
                    } else {
                        // Artist doesn't parse but title keeps changing → use rawTitle as lyric, keep rawArtist
                        finalLyric = rawTitle
                        AppLogger.getInstance().log("Parser", "🎵 Dynamic lyric (no parse, raw artist): '$rawTitle'")
                    }
                }
            } else if (isLyricTransition) {
                // Apply first lyric from this transition
                finalTitle  = prevTitle
                finalArtist = prevArtist
                finalLyric  = rawTitle
            } else if (!isDynamicLyricMode) {
                // --- STRATEGY 3: Parse-based detection (fallback for other formats) ---
                // Case A: Title contains "Artist - Title" (e.g. bluetooth media)
                val titleParse = ParserRuleHelper.parseWithRule(rawTitle ?: "", rule)
                if (titleParse.third) {
                    finalTitle  = titleParse.first
                    finalArtist = titleParse.second
                } else {
                    // Case B: Artist contains "Artist - Title" AND Title is lyrics
                    val artistParse = ParserRuleHelper.parseWithRule(rawArtist ?: "", rule)
                    if (artistParse.third) {
                        val suspectedTitle  = artistParse.first
                        val suspectedArtist = artistParse.second

                        val isRiskySeparator = rule.separatorPattern == "-" && !(rawArtist?.contains(" - ") == true)
                        val isAnomalousParse = isRiskySeparator &&
                                               suspectedTitle.contains("/") &&
                                               !suspectedArtist.contains("/")
                        val signalAlbumMatch   = !album.isNullOrEmpty() && suspectedTitle == album
                        val signalSafeSeparator = !isRiskySeparator
                        val isConfirmedLyric   = signalSafeSeparator || signalAlbumMatch

                        if (!isFalsePositiveLyric && !isAnomalousParse && isConfirmedLyric) {
                            finalTitle  = suspectedTitle
                            finalArtist = suspectedArtist
                            if (!rawTitle.isNullOrEmpty()) {
                                finalLyric = rawTitle
                                AppLogger.getInstance().log("Parser", "💡 Parse-based lyric: safe=$signalSafeSeparator album=$signalAlbumMatch")
                            }
                        } else if (isRiskySeparator) {
                            AppLogger.getInstance().log("Parser", "🚫 Risky separator, no signals. Title='$rawTitle'")
                        }
                    }
                }
            }

            // --- Save State ---
            packageLastTitle[pkg]    = rawTitle
            packageLastArtist[pkg]   = rawArtist
            packageLastDuration[pkg] = duration
            packageLyricMode[pkg]    = isDynamicLyricMode
        }


        // Update PUBLIC Metadata (Main UI)
        LyricRepository.getInstance().updateMediaMetadata(finalTitle ?: "Unknown", finalArtist ?: "Unknown", pkg, duration)

        // Update Lyric if available
        if (finalLyric != null) {
            LyricRepository.getInstance().updateLyric(finalLyric, getAppName(pkg), "Notification")
        }

        // --- Save state for persistence ---
        saveParserState(pkg, finalTitle, finalArtist, isDynamicLyricMode, duration)

        // Use already extracted artBitmap
        LyricRepository.getInstance().updateAlbumArt(artBitmap)
    }

    private fun saveParserState(pkg: String, title: String?, artist: String?, lyricMode: Boolean, duration: Long) {
        val statePrefs = getSharedPreferences(PREFS_PARSER_STATE, MODE_PRIVATE)
        val historyJson = statePrefs.getString(PREF_STATE_HISTORY, "[]") ?: "[]"
        val history = JSONArray(historyJson)
        
        // Build new entry
        val entry = JSONObject().apply {
            put("pkg", pkg)
            put("title", title)
            put("artist", artist)
            put("mode", lyricMode)
            put("duration", duration)
            put("ts", System.currentTimeMillis())
        }

        // Update history (remove existing for same pkg)
        val newHistory = JSONArray()
        newHistory.put(entry)
        for (i in 0 until history.length()) {
            val old = history.getJSONObject(i)
            if (old.getString("pkg") != pkg) {
                newHistory.put(old)
            }
        }

        // Prune to last 5
        val finalHistory = JSONArray()
        for (i in 0 until minOf(newHistory.length(), 5)) {
            finalHistory.put(newHistory.get(i))
        }

        statePrefs.edit().putString(PREF_STATE_HISTORY, finalHistory.toString()).apply()
    }

    private fun tryRestoreParserState(pkg: String, currentArtist: String?, currentDuration: Long): Boolean {
        if (packageLastTitle.containsKey(pkg)) return false // Already have in-memory state

        val statePrefs = getSharedPreferences(PREFS_PARSER_STATE, MODE_PRIVATE)
        val historyJson = statePrefs.getString(PREF_STATE_HISTORY, "[]") ?: "[]"
        val history = JSONArray(historyJson)

        for (i in 0 until history.length()) {
            val entry = history.getJSONObject(i)
            if (entry.getString("pkg") == pkg) {
                val savedDuration = entry.getLong("duration")
                val savedArtist = entry.optString("artist", null)
                
                // Validate: Duration must match (±1s) and artist must match
                if (Math.abs(savedDuration - currentDuration) < 1000L && savedArtist == currentArtist) {
                    packageRealTitle[pkg] = entry.optString("title", null)
                    packageRealArtist[pkg] = savedArtist
                    packageLyricMode[pkg] = entry.getBoolean("mode")
                    packageLastDuration[pkg] = savedDuration
                    packageLastArtist[pkg] = savedArtist
                    AppLogger.getInstance().log(TAG, "♻️ Restored parser state for $pkg: ${entry.optString("title")}")
                    return true
                }
                break
            }
        }
        return false
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
        
        private const val PREFS_PARSER_STATE = "MediaParserState"
        private const val PREF_STATE_HISTORY = "state_history"
        
        // --- Dynamic Lyric State Tracking Maps ---
        private val packageLyricMode    = HashMap<String, Boolean>()
        private val packageLastArtist   = HashMap<String, String?>()
        private val packageLastDuration = HashMap<String, Long>()
        private val packageLastTitle    = HashMap<String, String?>()
        // Cached confirmed real song identity (set at lyric-mode transition)
        private val packageRealTitle    = HashMap<String, String?>()
        private val packageRealArtist   = HashMap<String, String?>()

        // Singleton instance — set in onCreate, cleared in onDestroy
        @Volatile private var instance: MediaMonitorService? = null

        var isConnected = false

        /**
         * Asks the running [MediaMonitorService] instance to immediately re-read the
         * MediaSession album art for [packageName] and push it to [LyricRepository].
         *
         * Called by [SuperLyricSource] whenever a song change is detected via the
         * SuperLyric API, so that album art (and Palette colour) is updated
         * before the first lyric line arrives — eliminating the grey-capsule window.
         */
        fun refreshAlbumArtForPackage(@Suppress("UNUSED_PARAMETER") context: Context, packageName: String) {
            instance?.doRefreshAlbumArtForPackage(packageName)
                ?: AppLogger.getInstance().d(TAG, "refreshAlbumArt: no running instance")
        }

        /**
         * Triggers an immediate re-scan of active media sessions.
         * Used by UI to ensure recommendations are current.
         */
        fun triggerRecheck() {
            instance?.recheckSessions() ?: AppLogger.getInstance().d(TAG, "triggerRecheck: no running instance")
        }
        
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
