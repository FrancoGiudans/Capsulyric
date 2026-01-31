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

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateControllers(controllers)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable {
        AppLogger.getInstance().log(TAG, "Stopping service after debounce.")
        val intent = Intent(this@MediaMonitorService, LyricService::class.java)
        intent.action = "ACTION_STOP"
        startService(intent)
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (PREF_WHITELIST == key) {
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
        Log.d(TAG, "onListenerConnected")
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        componentName = ComponentName(this, MediaMonitorService::class.java)

        mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)

        // Initial check
        try {
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            updateControllers(controllers)
        } catch (e: SecurityException) {
             AppLogger.getInstance().log(TAG, "Security Error on Connect: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
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
        // Use Helper to get only Enabled packages
        val set = WhitelistHelper.getEnabledPackages(this)
        allowedPackages.clear()
        allowedPackages.addAll(set)
        AppLogger.getInstance().log(TAG, "Whitelist updated: ${allowedPackages.size} enabled apps.")
    }

    private fun updateControllers(controllers: List<MediaController>?) {
        // CHECK MASTER SWITCH
        val isServiceEnabled = prefs?.getBoolean("service_enabled", true) ?: true
        if (!isServiceEnabled) {
            AppLogger.getInstance().log(TAG, "Master Switch OFF. Ignoring updates.")
            activeControllers.clear()
            LyricRepository.getInstance().updatePlaybackStatus(false)
            return
        }

        activeControllers.clear()
        controllers?.forEach { controller ->
            if (allowedPackages.contains(controller.packageName)) {
                activeControllers.add(controller)
                // Re-register callbacks to ensure we catch state changes
                controller.registerCallback(object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        checkServiceState()
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        updateMetadataIfPrimary(controller)
                    }
                })
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            // Sync State
            LyricRepository.getInstance().updatePlaybackStatus(true)

        } else {
            // Debounce Stop
            handler.removeCallbacks(stopRunnable)
            handler.postDelayed(stopRunnable, 500)

            LyricRepository.getInstance().updatePlaybackStatus(false)
        }
    }

    private fun getPrimaryController(): MediaController? {
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

        AppLogger.getInstance().log("Meta-Debug", "📦 RAW Data from [$pkg]:")
        AppLogger.getInstance().log("Meta-Debug", "   ↳ Title : $rawTitle")
        AppLogger.getInstance().log("Meta-Debug", "   ↳ Artist: $rawArtist")

        var finalTitle = rawTitle
        var finalArtist = rawArtist
        var finalLyric: String? = null

        // Load parser rule for this package (configurable system)
        val rule = ParserRuleHelper.getRuleForPackage(this, pkg)

        if (rule != null && rule.usesCarProtocol && rawArtist != null) {
            AppLogger.getInstance().log("Parser", "⚙️ Applying rule: separator=[${rule.separatorPattern}], order=${rule.fieldOrder}")
            
            // Parse using configurable rule
            val (parsedTitle, parsedArtist) = parseWithRule(rawArtist, rule)
            
            if (parsedTitle.isNotEmpty()) {
                // Car protocol mode: Title field holds lyric
                finalLyric = rawTitle
                finalTitle = parsedTitle
                finalArtist = parsedArtist
                AppLogger.getInstance().log("Parser", "✅ Parsed: Title=[$finalTitle], Artist=[$finalArtist]")
            }
        }

        AppLogger.getInstance().log("Repo", "✅ Posting: Title=[$finalTitle] Artist=[$finalArtist] Lyric=[${if (finalLyric != null) "YES" else "NO"}]")

        // Push to repository
        if (finalLyric != null) {
            LyricRepository.getInstance().updateLyric(finalLyric, getAppName(pkg))
        }

        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        LyricRepository.getInstance().updateMediaMetadata(finalTitle ?: "Unknown", finalArtist ?: "Unknown", pkg, duration)
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
        if (packageName.contains("qqmusic")) return "QQ Music"
        if (packageName.contains("netease")) return "NetEase"
        if (packageName.contains("miui")) return "Mi Music"
        return packageName
    }

    companion object {
        private const val TAG = "MediaMonitorService"
        private const val PREFS_NAME = "IslandLyricsPrefs"
        private const val PREF_WHITELIST = "whitelist_json"
    }
}
