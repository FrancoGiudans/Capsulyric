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
        // Simple atomic update - no song change detection, no lyric clearing
        // Capsule lifecycle is now controlled by playback state, not metadata changes
        AppLogger.getInstance().log("Repo", "📝 Metadata: $title - $artist [$packageName]")
        liveMetadata.postValue(MediaInfo(title, artist, packageName, duration))
    }

    fun updateProgress(position: Long, duration: Long) {
        liveProgress.postValue(PlaybackProgress(position, duration))
    }

    fun updateAlbumArt(bitmap: Bitmap?) {
        liveAlbumArt.postValue(bitmap)
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
