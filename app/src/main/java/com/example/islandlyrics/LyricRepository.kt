package com.example.islandlyrics

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData

/**
 * Singleton repository to hold lyric state.
 */
class LyricRepository private constructor() {

    // Playback state
    val isPlaying = MutableLiveData(false)

    // Atomic Metadata Container
    data class MediaInfo(
        val title: String,
        val artist: String,
        val packageName: String,
        val duration: Long
    )

    // Atomic Lyric Container
    data class LyricInfo(
        val lyric: String,
        val sourceApp: String
    )

    // Playback Progress Container
    data class PlaybackProgress(
        val position: Long,  // in milliseconds
        val duration: Long   // in milliseconds
    )

    // Modern atomic LiveData (single source of truth)
    val liveMetadata = MutableLiveData<MediaInfo?>()
    val liveLyric = MutableLiveData<LyricInfo?>()
    val liveProgress = MutableLiveData<PlaybackProgress?>()
    val liveAlbumArt = MutableLiveData<Bitmap?>()
    
    // Parsed lyrics from online sources (for syllable-based scrolling)
    data class ParsedLyricsInfo(
        val lines: List<OnlineLyricFetcher.LyricLine>,
        val hasSyllable: Boolean
    )
    val liveParsedLyrics = MutableLiveData<ParsedLyricsInfo?>()
    
    // Valid timing-rich lyric line for the current position
    val liveCurrentLine = MutableLiveData<OnlineLyricFetcher.LyricLine?>()

    // Track previous song to detect changes
    private var lastTrackId: String? = null

    // Update methods
    fun updatePlaybackStatus(playing: Boolean) {
        if (isPlaying.value != playing) {
            isPlaying.postValue(playing)
        }
    }

    fun updateLyric(lyric: String?, app: String?) {
        // Atomic Update: Only post if both lyric and app are non-null
        if (lyric != null && app != null) {
            liveLyric.postValue(LyricInfo(lyric, app))
        }

        // Implicitly playing if we get lyric data
        updatePlaybackStatus(true)
    }

    fun updateMediaMetadata(title: String, artist: String, packageName: String, duration: Long) {
        // Detect song change to clear old lyrics
        val currentTrackId = "$title-$artist-$packageName"
        if (lastTrackId != currentTrackId) {
            lastTrackId = currentTrackId
            AppLogger.getInstance().log("Repo", "📝 Song changed, clearing old lyric")
            liveLyric.postValue(LyricInfo("", packageName))
        }

        AppLogger.getInstance().log("Repo", "📝 Metadata: $title - $artist [$packageName]")
        liveMetadata.postValue(MediaInfo(title, artist, packageName, duration))
    }

    // Raw metadata for "Add Rule" suggestion (bypasses whitelist check in UI)
    val liveSuggestionMetadata = MutableLiveData<MediaInfo?>()

    fun updateSuggestionMetadata(title: String, artist: String, packageName: String, duration: Long) {
        liveSuggestionMetadata.postValue(MediaInfo(title, artist, packageName, duration))
    }

    fun updateProgress(position: Long, duration: Long) {
        liveProgress.postValue(PlaybackProgress(position, duration))
    }

    fun updateAlbumArt(bitmap: Bitmap?) {
        liveAlbumArt.postValue(bitmap)
    }
    
    fun updateParsedLyrics(lines: List<OnlineLyricFetcher.LyricLine>, hasSyllable: Boolean) {
        liveParsedLyrics.postValue(ParsedLyricsInfo(lines, hasSyllable))
        AppLogger.getInstance().log("Repo", "📝 Parsed lyrics updated: ${lines.size} lines, syllable: $hasSyllable")
    }
    
    fun updateCurrentLine(line: OnlineLyricFetcher.LyricLine?) {
        // Atomic update for timing-critical components
        liveCurrentLine.postValue(line)
    }

    // Diagnostics
    val liveDiagnostics = MutableLiveData<ServiceDiagnostics>()

    fun updateDiagnostics(diag: ServiceDiagnostics) {
        liveDiagnostics.postValue(diag)
    }

    companion object {
        private var instance: LyricRepository? = null

        @Synchronized
        fun getInstance(): LyricRepository {
            if (instance == null) {
                instance = LyricRepository()
            }
            return instance!!
        }
    }
}

data class ServiceDiagnostics(
    val isConnected: Boolean = false,
    val totalControllers: Int = 0,
    val whitelistedControllers: Int = 0,
    val primaryPackage: String = "None",
    val whitelistSize: Int = 0,
    val lastUpdateParams: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
