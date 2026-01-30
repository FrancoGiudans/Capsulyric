package com.example.islandlyrics

import androidx.lifecycle.MutableLiveData

/**
 * Singleton repository to hold lyric state.
 */
class LyricRepository private constructor() {

    val isPlaying = MutableLiveData(false)
    val currentLyric = MutableLiveData("Thinking...")
    val sourceAppName = MutableLiveData("No Source")
    val songTitle = MutableLiveData("Unknown Title")
    val artistName = MutableLiveData("Unknown Artist")

    // New Atomic Metadata Container
    data class MediaInfo(
        val title: String,
        val artist: String,
        val packageName: String,
        val duration: Long
    )

    // New Atomic Lyric Container
    data class LyricInfo(
        val lyric: String,
        val sourceApp: String
    )

    // Playback Progress Container
    data class PlaybackProgress(
        val position: Long,  // in milliseconds
        val duration: Long   // in milliseconds
    )

    val liveMetadata = MutableLiveData<MediaInfo?>()
    val liveLyric = MutableLiveData<LyricInfo?>()
    val liveProgress = MutableLiveData<PlaybackProgress?>()

    // Update methods
    fun updatePlaybackStatus(playing: Boolean) {
        if (isPlaying.value != playing) {
            isPlaying.postValue(playing)
        }
    }

    fun updateLyric(lyric: String?, app: String?) {
        if (lyric != null) currentLyric.postValue(lyric)
        if (app != null) sourceAppName.postValue(app)

        // Atomic Update
        if (lyric != null && app != null) {
            liveLyric.postValue(LyricInfo(lyric, app))
        }

        // This method NO LONGER updates title/artist to prevent overwriting metadata from MediaMonitor

        // Implicitly playing if we get data
        updatePlaybackStatus(true)
    }

    fun updateMediaMetadata(title: String, artist: String, packageName: String, duration: Long) {
        AppLogger.getInstance().log("Repo", "Posting metadata for: $packageName")
        // Atomic update
        liveMetadata.postValue(MediaInfo(title, artist, packageName, duration))
    }

    fun updateProgress(position: Long, duration: Long) {
        liveProgress.postValue(PlaybackProgress(position, duration))
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
