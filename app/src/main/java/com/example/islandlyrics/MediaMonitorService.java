package com.example.islandlyrics;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MediaMonitorService extends NotificationListenerService {
    private static final String TAG = "MediaMonitorService";
    private static final String PREFS_NAME = "IslandLyricsPrefs";
    private static final String PREF_WHITELIST = "whitelist_json";
    
    private MediaSessionManager mMediaSessionManager;
    private ComponentName mComponentName;
    private SharedPreferences mPrefs;
    
    private final Set<String> mAllowedPackages = new HashSet<>();

    @Override
    public void onCreate() {
        super.onCreate();
        mPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        // Load initial whitelist
        loadWhitelist();
        // Register pref listener
        mPrefs.registerOnSharedPreferenceChangeListener(mPrefListener);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "onListenerConnected");
        mMediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mComponentName = new ComponentName(this, MediaMonitorService.class);

        mMediaSessionManager.addOnActiveSessionsChangedListener(mSessionsChangedListener, mComponentName);
        
        // Initial check
        List<MediaController> controllers = mMediaSessionManager.getActiveSessions(mComponentName);
        updateControllers(controllers);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (mMediaSessionManager != null) {
            mMediaSessionManager.removeOnActiveSessionsChangedListener(mSessionsChangedListener);
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPrefs != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(mPrefListener);
        }
    }
    
    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener = 
        (sharedPreferences, key) -> {
            if (PREF_WHITELIST.equals(key)) {
                loadWhitelist();
                recheckSessions();
            } else if ("service_enabled".equals(key)) {
                recheckSessions();
            }
        };

    private void recheckSessions() {
        if (mMediaSessionManager != null) {
            try {
                updateControllers(mMediaSessionManager.getActiveSessions(mComponentName));
            } catch (SecurityException e) {
                AppLogger.getInstance().log(TAG, "Error refreshing sessions: " + e.getMessage());
            }
        }
    }

    private void loadWhitelist() {
        // Use Helper to get only Enabled packages
        Set<String> set = WhitelistHelper.getEnabledPackages(this);
        mAllowedPackages.clear();
        mAllowedPackages.addAll(set);
        AppLogger.getInstance().log(TAG, "Whitelist updated: " + mAllowedPackages.size() + " enabled apps.");
    }

    private final java.util.List<android.media.session.MediaController> mActiveControllers = new java.util.ArrayList<>();

    private final android.media.session.MediaSessionManager.OnActiveSessionsChangedListener mSessionsChangedListener = 
            this::updateControllers;

    private final android.os.Handler mHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mStopRunnable = () -> {
        AppLogger.getInstance().log(TAG, "Stopping service after debounce.");
        android.content.Intent intent = new android.content.Intent(MediaMonitorService.this, LyricService.class);
        intent.setAction("ACTION_STOP");
        startService(intent);
    };

    private void updateControllers(List<MediaController> controllers) {
        // AppLogger.getInstance().log(TAG, "Segments changed.");
        
        // CHECK MASTER SWITCH
        boolean isServiceEnabled = mPrefs.getBoolean("service_enabled", true);
        if (!isServiceEnabled) {
             AppLogger.getInstance().log(TAG, "Master Switch OFF. Ignoring updates.");
             mActiveControllers.clear();
             LyricRepository.getInstance().updatePlaybackStatus(false);
             return;
        }
        
        mActiveControllers.clear();
        if (controllers != null) {
            for (MediaController controller : controllers) {
                if (mAllowedPackages.contains(controller.getPackageName())) {
                    mActiveControllers.add(controller);
                    // Re-register callbacks to ensure we catch state changes
                    // Note: In production, careful not to double-register. 
                    // MediaSessionManager usually returns new objects.
                    controller.registerCallback(new MediaController.Callback() {
                        @Override
                        public void onPlaybackStateChanged(PlaybackState state) {
                            checkServiceState();
                        }
                        @Override
                        public void onMetadataChanged(MediaMetadata metadata) {
                            updateMetadataIfPrimary(controller);
                        }
                    });
                }
            }
        }
        
        // Initial check
        checkServiceState();
        
        // Force update metadata from the CURRENT primary
        MediaController primary = getPrimaryController();
        if (primary != null) {
            updateMetadataIfPrimary(primary);
        }
    }
    
    private void checkServiceState() {
        MediaController primary = getPrimaryController();
        boolean isPlaying = primary != null && 
                            primary.getPlaybackState() != null &&
                            primary.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;

        if (isPlaying) {
            // Cancel any pending stop
            mHandler.removeCallbacks(mStopRunnable);
            
            // Start/Update Service
            // AppLogger.getInstance().log(TAG, "Starting/Keeping service for: " + primary.getPackageName());
            android.content.Intent intent = new android.content.Intent(MediaMonitorService.this, LyricService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            
            // Sync State
            LyricRepository.getInstance().updatePlaybackStatus(true);
            
        } else {
            // Debounce Stop
            // Only post if not already posting? 
            // Better: remove previous and post new to reset timer? 
            // No, just ensure we don't have duplicates.
            // But if we are already stopping, just let it be.
            // If we transitioned Play -> Pause, we trigger this.
            
            // If already posted, do nothing? Or defer?
            // Simple approach: Remove and Post (Reset timer)
            mHandler.removeCallbacks(mStopRunnable);
            mHandler.postDelayed(mStopRunnable, 500);
            
            LyricRepository.getInstance().updatePlaybackStatus(false);
        }
    }

    private MediaController getPrimaryController() {
        // Priority 1: Playing
        for (MediaController c : mActiveControllers) {
            PlaybackState state = c.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                return c;
            }
        }
        // Priority 2: First in list (or most recent?) - API list order usually implies recency
        if (!mActiveControllers.isEmpty()) {
            return mActiveControllers.get(0);
        }
        return null; // None active
    }

    private void updateMetadataIfPrimary(MediaController controller) {
        MediaController primary = getPrimaryController();
        if (primary == null) return;
        
        // Only update if this controller IS the primary one
        if (controller.getPackageName().equals(primary.getPackageName())) {
             MediaMetadata metadata = controller.getMetadata();
             if (metadata != null) {
                 extractMetadata(metadata, controller.getPackageName());
             }
        }
    }

    private void extractMetadata(MediaMetadata metadata, String pkg) {
        if (metadata == null) return;
        
        String rawTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String rawArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        
        AppLogger.getInstance().log("Meta-Debug", "📦 RAW Data from [" + pkg + "]:");
        AppLogger.getInstance().log("Meta-Debug", "   ↳ Title : " + (rawTitle == null ? "null" : rawTitle));
        AppLogger.getInstance().log("Meta-Debug", "   ↳ Artist: " + (rawArtist == null ? "null" : rawArtist));

        String finalTitle = rawTitle;
        String finalArtist = rawArtist;
        String finalLyric = null; // Default to null

        // Target specific apps known to use the "Car Bluetooth" hack
        // NetEase also uses a similar Title=Lyric scheme in Bluetooth broadcast
        boolean isTencentBase = pkg.contains("tencent") || pkg.contains("miui.player") || pkg.contains("netease");

        if (isTencentBase) {
            // Check if it looks like the Car Protocol (Artist field acts as container)
            // Check if it looks like the Car Protocol (Artist field acts as container)
            // or if we just want to try parsing it.
            if (rawArtist != null) {
                String separator = null;
                int splitIndex = -1;
                int offset = 0;

                // STRATEGY 1: Look for Standard " - " (Space Hyphen Space)
                // Use Last Index here (Safe for "Anti-Hero - Taylor Swift")
                if (rawArtist.contains(" - ")) {
                    separator = " - ";
                    splitIndex = rawArtist.lastIndexOf(separator);
                    offset = separator.length();
                } 
                // STRATEGY 2: Fallback to Tight "-" (Hyphen)
                // Use FIRST Index here to protect Artist names like "HOYO-MiX"
                // Assumption: "Song-HOYO-MiX" -> Split at 1st hyphen -> Title="Song", Artist="HOYO-MiX"
                else if (rawArtist.contains("-")) {
                    separator = "-";
                    splitIndex = rawArtist.indexOf(separator);
                    offset = separator.length();
                }

                if (splitIndex != -1) {
                     AppLogger.getInstance().log("Parser", "⚠ Car Protocol Detected via separator [" + separator + "]");
                     
                     // 1. In this mode, the Title field actually holds the Lyric
                     finalLyric = rawTitle;

                     // 2. The Artist field holds "Song - Artist"
                     finalTitle = rawArtist.substring(0, splitIndex).trim();
                     finalArtist = rawArtist.substring(splitIndex + offset).trim();
                }
            }
        }
        
        AppLogger.getInstance().log("Repo", "✅ Posting: Title=[" + finalTitle + "] Artist=[" + finalArtist + "] Lyric=[" + (finalLyric != null ? "YES" : "NO") + "]");

        // Push Decision
        if (finalLyric != null) {
            LyricRepository.getInstance().updateLyric(finalLyric, getAppName(pkg));
        }
        LyricRepository.getInstance().updateMediaMetadata(finalTitle, finalArtist, pkg);
    }
    
    private String getAppName(String packageName) {
        if (packageName == null) return "Music";
        if (packageName.contains("qqmusic")) return "QQ Music";
        if (packageName.contains("netease")) return "NetEase";
        if (packageName.contains("miui")) return "Mi Music";
        return packageName;
    }
}
