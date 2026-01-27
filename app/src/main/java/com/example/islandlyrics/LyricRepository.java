package com.example.islandlyrics;

import androidx.lifecycle.MutableLiveData;

/**
 * Singleton repository to hold lyric state.
 */
public class LyricRepository {
    private static LyricRepository sInstance;

    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<String> currentLyric = new MutableLiveData<>("Thinking...");
    private final MutableLiveData<String> sourceAppName = new MutableLiveData<>("No Source");
    private final MutableLiveData<String> songTitle = new MutableLiveData<>("Unknown Title");
    private final MutableLiveData<String> artistName = new MutableLiveData<>("Unknown Artist");
    
    // New Atomic Metadata Container
    public static class MediaInfo {
        public final String title;
        public final String artist;
        public final String packageName;
        
        public MediaInfo(String title, String artist, String packageName) {
            this.title = title;
            this.artist = artist;
            this.packageName = packageName;
        }
    }
    
    // New Atomic Lyric Container
    public static class LyricInfo {
        public final String lyric;
        public final String sourceApp;
        
        public LyricInfo(String lyric, String sourceApp) {
            this.lyric = lyric;
            this.sourceApp = sourceApp;
        }
    }
    
    private final MutableLiveData<MediaInfo> liveMetadata = new MutableLiveData<>();
    private final MutableLiveData<LyricInfo> liveLyric = new MutableLiveData<>();

    private LyricRepository() {}

    public static synchronized LyricRepository getInstance() {
        if (sInstance == null) {
            sInstance = new LyricRepository();
        }
        return sInstance;
    }

    // Getters for LiveData
    public MutableLiveData<Boolean> getIsPlaying() { return isPlaying; }
    public MutableLiveData<String> getCurrentLyric() { return currentLyric; }
    public MutableLiveData<String> getSourceAppName() { return sourceAppName; }
    public MutableLiveData<String> getSongTitle() { return songTitle; }
    public MutableLiveData<String> getArtistName() { return artistName; }
    public MutableLiveData<MediaInfo> getLiveMetadata() { return liveMetadata; }
    public MutableLiveData<LyricInfo> getLiveLyric() { return liveLyric; }

    // Update methods
    public void updatePlaybackStatus(boolean playing) {
        if (isPlaying.getValue() != playing) {
            isPlaying.postValue(playing);
        }
    }

    public void updateLyric(String lyric, String app) {
        if (lyric != null) currentLyric.postValue(lyric);
        if (app != null) sourceAppName.postValue(app);
        
        // Atomic Update
        if (lyric != null && app != null) {
            liveLyric.postValue(new LyricInfo(lyric, app));
        }
        
        // This method NO LONGER updates title/artist to prevent overwriting metadata from MediaMonitor
        
        // Implicitly playing if we get data
        updatePlaybackStatus(true);
    }

    public void updateMediaMetadata(String title, String artist, String packageName) {
        AppLogger.getInstance().log("Repo", "Posting metadata for: " + packageName);
        // Atomic update
        liveMetadata.postValue(new MediaInfo(title, artist, packageName));
    }
}
