package com.example.islandlyrics.data

import android.graphics.Bitmap
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.data.lyric.OnlineLyricFetcher
import androidx.lifecycle.LiveData
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
        val sourceApp: String,     // E.g. "QQ音乐" or package name
        val apiPath: String = "Unknown" // E.g. "SuperLyric", "Lyric Getter", "Online", "Notification"
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

    fun updateLyric(lyric: String?, app: String?, apiPath: String = "Unknown") {
        if (lyric == null || app == null) return
        
        val newInfo = LyricInfo(lyric, app, apiPath)
        if (liveLyric.value == newInfo) return
        
        postOrSet(liveLyric, newInfo)

        // Implicitly playing if we get lyric data
        updatePlaybackStatus(true)
    }

    fun updateMediaMetadata(title: String, artist: String, packageName: String, duration: Long) {
        val newInfo = MediaInfo(title, artist, packageName, duration)
        if (liveMetadata.value == newInfo) return

        // Detect song change to clear old lyrics
        // FIX: Only clear if Title/Artist/Pkg actually changed significantly (not just tiny metadata ping)
        val currentTrackId = "$title-$artist-$packageName"
        if (lastTrackId != currentTrackId) {
            lastTrackId = currentTrackId
            AppLogger.getInstance().log("Repo", "📝 Song changed ($currentTrackId), clearing old lyric")
            postOrSet(liveLyric, LyricInfo("", packageName, "System"))
            postOrSet(liveParsedLyrics, null)
            postOrSet(liveCurrentLine, null)
        }

        AppLogger.getInstance().log("Repo", "📝 Metadata Update: $title - $artist [$packageName]")
        postOrSet(liveMetadata, newInfo)
    }

    // Raw metadata for "Add Rule" suggestion (bypasses whitelist check in UI)
    val liveSuggestionMetadata = MutableLiveData<MediaInfo?>()

    fun updateSuggestionMetadata(title: String, artist: String, packageName: String, duration: Long) {
        liveSuggestionMetadata.postValue(MediaInfo(title, artist, packageName, duration))
    }

    fun updateProgress(position: Long, duration: Long) {
        val newProgress = PlaybackProgress(position, duration)
        if (liveProgress.value == newProgress) return
        postOrSet(liveProgress, newProgress)
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
    val diagnostics: LiveData<ServiceDiagnostics> = liveDiagnostics

    private val liveDevMode = MutableLiveData<Boolean>()
    val devModeEnabled: LiveData<Boolean> = liveDevMode

    fun setDevMode(context: android.content.Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("IslandLyricsPrefs", android.content.Context.MODE_PRIVATE)
        if (enabled) {
            prefs.edit().putBoolean("dev_mode_enabled", true).apply()
        } else {
            prefs.edit().remove("dev_mode_enabled").apply()
        }
        com.example.islandlyrics.core.logging.AppLogger.getInstance().enableLogging(enabled)
        com.example.islandlyrics.integration.shizuku.ShizukuUserServiceRecycler.setLogCallbackEnabled(enabled)
        postOrSet(liveDevMode, enabled)
    }

    fun init(context: android.content.Context) {
        val prefs = context.getSharedPreferences("IslandLyricsPrefs", android.content.Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("dev_mode_enabled", false)
        postOrSet(liveDevMode, enabled)
        com.example.islandlyrics.integration.shizuku.ShizukuUserServiceRecycler.setLogCallbackEnabled(enabled)
    }

    fun refreshAdvancedDiagnostics(context: android.content.Context) {
        val intent = android.content.Intent(ACTION_REFRESH_DIAGNOSTICS).apply {
            `package` = context.packageName
        }
        context.sendBroadcast(intent)
    }

    fun updateDiagnostics(diag: ServiceDiagnostics) {
        postOrSet(liveDiagnostics, diag)
    }

    fun mergeDiagnostics(update: (ServiceDiagnostics) -> ServiceDiagnostics) {
        val current = liveDiagnostics.value ?: ServiceDiagnostics()
        postOrSet(liveDiagnostics, update(current))
    }

    private fun <T> postOrSet(liveData: MutableLiveData<T>, value: T) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            liveData.value = value
        } else {
            liveData.postValue(value)
        }
    }

    companion object {
        const val ACTION_REFRESH_DIAGNOSTICS = "com.example.islandlyrics.ACTION_REFRESH_DIAGNOSTICS"
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
    val timestamp: Long = System.currentTimeMillis(),
    // Android 16+ Promoted Notifications
    val canPostPromoted: Boolean = false,
    val hasPromotableChar: Boolean = false,
    val isCurrentlyPromoted: Boolean = false,
    // Xiaomi Super Island
    val isIslandSupported: Boolean = false,
    val islandVersion: Int = 0,
    val hasFocusPermission: Boolean = false
)
