package com.example.islandlyrics.lyrics.state

import android.graphics.Bitmap
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.integration.shizuku.ShizukuUserServiceRecycler
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
        val duration: Long,
        val rawTitle: String = title,
        val rawArtist: String = artist
    )

    // Atomic Lyric Container
    data class LyricInfo(
        val lyric: String,
        val sourceApp: String,     // E.g. "QQ音乐" or package name
        val apiPath: String = "Unknown", // E.g. "SuperLyric", "Lyric Getter", "Online", "Notification"
        val translation: String? = null,
        val roma: String? = null
    )

    // Playback Progress Container
    data class PlaybackProgress(
        val position: Long,  // in milliseconds
        val duration: Long   // in milliseconds
    )

    data class SuperLyricDebugInfo(
        val publisher: String?,
        val packageName: String,
        val lyric: String,
        val translation: String?,
        val roma: String?,
        val hasLyric: Boolean,
        val hasTranslation: Boolean,
        val hasSecondary: Boolean,
        val lyricLineRaw: String?,
        val translationLineRaw: String?,
        val secondaryLineRaw: String?,
        val lyricWordsPreview: String,
        val translationWordsPreview: String,
        val secondaryWordsPreview: String,
        val extraKeys: List<String>,
        val skipReason: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Modern atomic LiveData (single source of truth)
    val liveMetadata = MutableLiveData<MediaInfo?>()
    val liveLyric = MutableLiveData<LyricInfo?>()
    val liveProgress = MutableLiveData<PlaybackProgress?>()
    val liveAlbumArt = MutableLiveData<Bitmap?>()
    val liveSuperLyricDebug = MutableLiveData<SuperLyricDebugInfo?>()
    
    // Parsed lyrics from online sources (for syllable-based scrolling)
    data class ParsedLyricsInfo(
        val lines: List<OnlineLyricFetcher.LyricLine>,
        val hasSyllable: Boolean,
        val sourceLabel: String? = null,
        val apiPath: String? = null,
        val timelineCapability: TimelineCapability = TimelineCapability.NONE
    )
    val liveParsedLyrics = MutableLiveData<ParsedLyricsInfo?>()

    enum class TimelineCapability {
        NONE,
        ACTIVE_LINE_ONLY,
        MULTI_LINE
    }
    
    // Valid timing-rich lyric line for the current position
    val liveCurrentLine = MutableLiveData<OnlineLyricFetcher.LyricLine?>()

    private val sidecarStore = LyricSidecarStore()
    private val lyricSourceArbiter = LyricSourceArbiter()
    private val trackChangeDetector = TrackChangeDetector()

    // Update methods
    fun updatePlaybackStatus(playing: Boolean) {
        if (isPlaying.value != playing) {
            postOrSet(isPlaying, playing)
        }
    }

    fun updateLyric(
        lyric: String?,
        app: String?,
        apiPath: String = "Unknown",
        translation: String? = null,
        roma: String? = null
    ) {
        if (lyric == null || app == null) return

        val normalizedTranslation = translation?.takeIf { it.isNotBlank() }
        val normalizedRoma = roma?.takeIf { it.isNotBlank() }
        if (lyric.isNotBlank() && (normalizedTranslation != null || normalizedRoma != null)) {
            sidecarStore.put(apiPath, lyric, normalizedTranslation, normalizedRoma)
        }
        val sidecar = sidecarStore.find(apiPath, lyric)

        val newInfo = LyricInfo(
            lyric = lyric,
            sourceApp = app,
            apiPath = apiPath,
            translation = normalizedTranslation ?: sidecar?.translation,
            roma = normalizedRoma ?: sidecar?.roma
        )
        if (liveLyric.value == newInfo) return

        val acceptedInfo = lyricSourceArbiter.chooseLyric(
            current = liveLyric.value,
            candidate = newInfo
        ) ?: return

        postOrSet(liveLyric, acceptedInfo)
    }

    fun updateLyricSidecars(
        apiPath: String,
        entries: Map<String, Pair<String?, String?>>
    ) {
        sidecarStore.putAll(apiPath, entries)
    }

    fun updateSuperLyricDebug(info: SuperLyricDebugInfo) {
        postOrSet(liveSuperLyricDebug, info)
    }

    fun updateMediaMetadata(
        title: String,
        artist: String,
        packageName: String,
        duration: Long,
        rawTitle: String = title,
        rawArtist: String = artist
    ) {
        val newInfo = MediaInfo(title, artist, packageName, duration, rawTitle, rawArtist)
        if (liveMetadata.value == newInfo) return

        // Detect song change to clear old lyrics
        // FIX: Only clear if Title/Artist/Pkg actually changed significantly (not just tiny metadata ping)
        if (trackChangeDetector.didTrackChange(title, artist, packageName)) {
            lyricSourceArbiter.resetForTrack()
            val trackId = trackChangeDetector.describe(title, artist, packageName)
            AppLogger.getInstance().log("Repo", "📝 Song changed ($trackId), clearing old lyric")
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
        liveSuggestionMetadata.postValue(MediaInfo(title, artist, packageName, duration, title, artist))
    }

    fun updateProgress(position: Long, duration: Long) {
        val newProgress = PlaybackProgress(position, duration)
        if (liveProgress.value == newProgress) return
        postOrSet(liveProgress, newProgress)
    }

    fun updateAlbumArt(bitmap: Bitmap?) {
        val old = liveAlbumArt.value
        // Skip if same reference (e.g. duplicate metadata callback)
        if (old === bitmap) return
        liveAlbumArt.postValue(bitmap)
    }
    
    fun updateParsedLyrics(
        lines: List<OnlineLyricFetcher.LyricLine>,
        hasSyllable: Boolean,
        sourceLabel: String? = null,
        apiPath: String? = null,
        timelineCapability: TimelineCapability = TimelineCapability.NONE
    ) {
        val info = ParsedLyricsInfo(lines, hasSyllable, sourceLabel, apiPath, timelineCapability)
        val acceptedInfo = lyricSourceArbiter.chooseParsedLyrics(
            current = liveParsedLyrics.value,
            candidate = info
        ) ?: return
        liveParsedLyrics.postValue(acceptedInfo)
        AppLogger.getInstance().log(
            "Repo",
            "📝 Parsed lyrics updated: ${lines.size} lines, syllable: $hasSyllable, capability=$timelineCapability"
        )
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
        val prefs = AppPreferences.of(context)
        if (enabled) {
            prefs.edit().putBoolean(AppPreferences.Keys.DEV_MODE_ENABLED, true).apply()
        } else {
            prefs.edit().remove(AppPreferences.Keys.DEV_MODE_ENABLED).apply()
        }
        AppLogger.getInstance().init(context)
        ShizukuUserServiceRecycler.setLogCallbackEnabled(enabled)
        postOrSet(liveDevMode, enabled)
    }

    fun init(context: android.content.Context) {
        val enabled = AppPreferences.isDevModeEnabled(AppPreferences.of(context))
        postOrSet(liveDevMode, enabled)
        ShizukuUserServiceRecycler.setLogCallbackEnabled(enabled)
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
        const val API_PATH_INSTRUMENTAL = "Instrumental"
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
