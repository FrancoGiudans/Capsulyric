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
    private static final String PREF_WHITELIST = "whitelist_packages";
    
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
                // Retrigger check
                if (mMediaSessionManager != null) {
                    try {
                        updateControllers(mMediaSessionManager.getActiveSessions(mComponentName));
                    } catch (SecurityException e) {
                        AppLogger.getInstance().log(TAG, "Error refreshing sessions: " + e.getMessage());
                    }
                }
            }
        };

    private void loadWhitelist() {
        Set<String> set = mPrefs.getStringSet(PREF_WHITELIST, null);
        mAllowedPackages.clear();
        if (set != null) {
            mAllowedPackages.addAll(set);
        } else {
             // Fallback default
             mAllowedPackages.add("com.netease.cloudmusic");
             mAllowedPackages.add("com.tencent.qqmusic");
             mAllowedPackages.add("com.spotify.music");
        }
        AppLogger.getInstance().log(TAG, "Whitelist updated: " + mAllowedPackages.size() + " apps.");
    }

    private final List<MediaController> mActiveControllers = new java.util.ArrayList<>();

    private final MediaSessionManager.OnActiveSessionsChangedListener mSessionsChangedListener = 
            this::updateControllers;

    private void updateControllers(List<MediaController> controllers) {
        AppLogger.getInstance().log(TAG, "Sessions changed. New Count: " + (controllers != null ? controllers.size() : "null"));
        
        // Clear references
        mActiveControllers.clear();

        if (controllers != null && !controllers.isEmpty()) {
            for (MediaController controller : controllers) {
                String pkg = controller.getPackageName();
                
                if (!mAllowedPackages.contains(pkg)) {
                    // AppLogger.getInstance().log(TAG, "Ignoring: " + pkg + " (Not Whitelisted)");
                    continue; 
                }
                
                PlaybackState currentState = controller.getPlaybackState();
                int stateInt = currentState != null ? currentState.getState() : -1;
               // AppLogger.getInstance().log(TAG, "Session: " + pkg + " | State: " + stateInt);
                
                MediaController.Callback callback = new MediaController.Callback() {
                    @Override
                    public void onMetadataChanged(MediaMetadata metadata) {
                        if (metadata != null) {
                            String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                            // boolean hasArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null;
                            AppLogger.getInstance().log(TAG, "[" + pkg + "] Meta | Title: " + title);
                            extractMetadata(metadata, pkg);
                        }
                    }

                    @Override
                    public void onPlaybackStateChanged(PlaybackState state) {
                        int rawState = state != null ? state.getState() : -1;
                        AppLogger.getInstance().log(TAG, "[" + pkg + "] State | Raw: " + rawState);
                        
                        boolean isPlaying = rawState == PlaybackState.STATE_PLAYING;
                        LyricRepository.getInstance().updatePlaybackStatus(isPlaying);
                        
                        // Global Check triggered by callback
                        checkServiceState();
                    }
                };
                
                controller.registerCallback(callback);
                mActiveControllers.add(controller);
                
                // Trigger immediate update
                MediaMetadata metadata = controller.getMetadata();
                if (metadata != null) {
                    extractMetadata(metadata, pkg);
                }
            }
        }
        
        // Also check state immediately when controllers change
        checkServiceState();
    }
    
    private void checkServiceState() {
        boolean shouldRun = shouldServiceBeRunning();
        // AppLogger.getInstance().log(TAG, "Decision: Should Service Run? " + shouldRun);
        
        if (shouldRun) {
            android.content.Intent intent = new android.content.Intent(MediaMonitorService.this, LyricService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } else {
            // NOTE: We only stop if we are SURE nothing is playing. 
            // In a real multi-app scenario, this logic needs to be robust. 
            // For now, if no whitelisted app is playing, we stop.
            android.content.Intent intent = new android.content.Intent(MediaMonitorService.this, LyricService.class);
            intent.setAction("ACTION_STOP");
            startService(intent);
        }
    }

    private boolean shouldServiceBeRunning() {
        if (mMediaSessionManager == null) return false;
        
        List<MediaController> controllers = mMediaSessionManager.getActiveSessions(mComponentName);
        if (controllers == null) return false;

        // AppLogger.getInstance().log(TAG, "--- Checking Global State ---");
        for (MediaController controller : controllers) {
            String pkg = controller.getPackageName();
            if (!mAllowedPackages.contains(pkg)) continue;
            
            PlaybackState state = controller.getPlaybackState();
            int stateInt = state != null ? state.getState() : -1;
            boolean isPlaying = stateInt == PlaybackState.STATE_PLAYING;
            
            if (isPlaying) {
                // AppLogger.getInstance().log(TAG, "  > Found active session: " + pkg);
                return true; // Found at least one playing session
            }
        }
        return false;
    }
    
    private void extractMetadata(MediaMetadata metadata, String pkg) {
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        
        LyricRepository.getInstance().updateMediaMetadata(title, artist, pkg);
    }
}
