/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.lyrics.state

import android.graphics.Bitmap
import android.media.session.MediaSession
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricParser
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
        val rawArtist: String = artist,
        val album: String = "",
        val albumArtist: String = "",
        val mediaId: String = "",
        val mediaUri: String = "",
        val trackNumber: Int = 0,
        internal val mediaSessionToken: MediaSession.Token? = null,
    ) {
        internal fun withoutRawMetadata(): MediaInfo =
            if (rawTitle == title && rawArtist == artist) this else copy(rawTitle = title, rawArtist = artist)

        internal val albumArtKey: AlbumArtKey
            get() = AlbumArtKey(packageName, title, artist, duration, mediaId, mediaUri)
    }

    internal data class AlbumArtKey(
        val packageName: String,
        val title: String,
        val artist: String,
        val duration: Long,
        val mediaId: String,
        val mediaUri: String,
    )

    internal data class TrackAlbumArt(val trackKey: AlbumArtKey, val bitmap: Bitmap?)

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
        val mode: String,
        val registrationResult: String,
        val serverApiVersion: Int?,
        val documentId: String?,
        val lineCount: Int,
        val currentIndex: Int,
        val wordsPreview: String,
        val position: Long?,
        val speed: Float?,
        val playbackState: String?,
        val stopReason: String? = null,
        val skipReason: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Modern atomic LiveData (single source of truth)
    val liveMetadata = MutableLiveData<MediaInfo?>()
    val liveLyric = MutableLiveData<LyricInfo?>()
    val liveProgress = MutableLiveData<PlaybackProgress?>()
    val liveAlbumArt = MutableLiveData<Bitmap?>()
    internal val liveTrackAlbumArt = MutableLiveData<TrackAlbumArt>()
    val liveSuperLyricDebug = MutableLiveData<SuperLyricDebugInfo?>()

    // Resolution state: distinguishes "still resolving" from "definitely no lyrics"
    enum class LyricResolveState { PENDING, FOUND, NOT_FOUND }
    val liveLyricResolveState = MutableLiveData(LyricResolveState.PENDING)
    
    // Parsed lyrics from online sources (for syllable-based scrolling)
    data class ParsedLyricsInfo(
        val lines: List<OnlineLyricFetcher.LyricLine>,
        val hasSyllable: Boolean,
        val sourceLabel: String? = null,
        val apiPath: String? = null,
        val timelineCapability: TimelineCapability = TimelineCapability.NONE,
        val receiveTranslation: Boolean = true,
        val receiveRomanization: Boolean = true,
        val writers: List<String> = emptyList()
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
        roma: String? = null,
        receiveTranslation: Boolean = true,
        receiveRomanization: Boolean = true
    ) {
        if (lyric == null || app == null) return

        val normalizedTranslation = translation?.takeIf { receiveTranslation && it.isNotBlank() }
        val normalizedRoma = roma?.takeIf { receiveRomanization && it.isNotBlank() }
        if (lyric.isNotBlank() && (normalizedTranslation != null || normalizedRoma != null)) {
            sidecarStore.put(apiPath, lyric, normalizedTranslation, normalizedRoma)
        }
        val sidecar = sidecarStore.find(apiPath, lyric, receiveTranslation, receiveRomanization)

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
        if (acceptedInfo.lyric.isNotBlank()) {
            updateLyricResolveState(LyricResolveState.FOUND)
        }

        // QQ 逐行/逐字原文（[ti:...]/[kana:...]/[startMs,durMs]text）若直接从 hook/通知等
        // 路径进入且尚未有解析行，则按时间轴解析，避免把整段原文原样展示为“当前歌词”。
        if (acceptedInfo.lyric.isNotBlank() &&
            liveParsedLyrics.value?.lines?.isNotEmpty() != true &&
            OnlineLyricParser.hasQqLineSegments(acceptedInfo.lyric)
        ) {
            val qqLines = OnlineLyricParser.parseLrcLyrics(acceptedInfo.lyric)
            if (qqLines.isNotEmpty()) {
                updateParsedLyrics(
                    lines = qqLines,
                    hasSyllable = false,
                    sourceLabel = app,
                    apiPath = apiPath,
                    timelineCapability = TimelineCapability.MULTI_LINE
                )
            }
        }
    }

    internal fun clearNotificationLyrics(packageName: String) {
        if (liveMetadata.value?.packageName != packageName) return

        val parsed = liveParsedLyrics.value
        val hasNotificationTimeline = parsed?.apiPath == API_PATH_NOTIFICATION
        if (hasNotificationTimeline) {
            updateParsedLyrics(emptyList(), hasSyllable = false)
            postOrSet(liveCurrentLine, null)
        }
        if (liveLyric.value?.apiPath != API_PATH_NOTIFICATION) return

        updateLyric("", packageName, "System")
        if (parsed != null && !hasNotificationTimeline) {
            liveCurrentLine.value?.takeIf { it in parsed.lines }?.let { line ->
                updateLyric(
                    lyric = line.text,
                    app = parsed.sourceLabel ?: packageName,
                    apiPath = parsed.apiPath ?: "Online API",
                    translation = line.translation,
                    roma = line.roma,
                    receiveTranslation = parsed.receiveTranslation,
                    receiveRomanization = parsed.receiveRomanization,
                )
            }
        }
    }

    fun updateLyricResolveState(state: LyricResolveState) {
        if (liveLyricResolveState.value != state) {
            postOrSet(liveLyricResolveState, state)
        }
    }

    /**
     * Marks a lyric-resolution attempt as finished without a result.
     * If content arrived from another source meanwhile, keeps FOUND.
     */
    fun reportLyricResolveFailed() {
        val hasContent = liveLyric.value?.lyric?.isNotBlank() == true ||
            liveParsedLyrics.value?.lines?.isNotEmpty() == true
        updateLyricResolveState(if (hasContent) LyricResolveState.FOUND else LyricResolveState.NOT_FOUND)
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
        rawArtist: String = artist,
        album: String = "",
        albumArtist: String = "",
        mediaId: String = "",
        mediaUri: String = "",
        trackNumber: Int = 0,
        mediaSessionToken: MediaSession.Token? = null,
    ): MediaInfo {
        val newInfo = MediaInfo(
            title = title,
            artist = artist,
            packageName = packageName,
            duration = duration,
            rawTitle = rawTitle,
            rawArtist = rawArtist,
            album = album,
            albumArtist = albumArtist,
            mediaId = mediaId,
            mediaUri = mediaUri,
            trackNumber = trackNumber,
            mediaSessionToken = mediaSessionToken,
        )
        if (liveMetadata.value == newInfo) return newInfo

        // Detect song change to clear old lyrics
        // FIX: Only clear if Title/Artist/Pkg actually changed significantly (not just tiny metadata ping)
        if (trackChangeDetector.didTrackChange(title, artist, packageName)) {
            lyricSourceArbiter.resetForTrack()
            val trackId = trackChangeDetector.describe(title, artist, packageName)
            AppLogger.getInstance().log("Repo", "📝 Song changed ($trackId), clearing old lyric")
            postOrSet(liveLyric, LyricInfo("", packageName, "System"))
            postOrSet(liveParsedLyrics, null)
            postOrSet(liveCurrentLine, null)
            updateLyricResolveState(LyricResolveState.PENDING)
        }

        AppLogger.getInstance().log("Repo", "📝 Metadata Update: $title - $artist [$packageName]")
        postOrSet(liveMetadata, newInfo)
        return newInfo
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

    internal fun updateAlbumArt(track: MediaInfo, bitmap: Bitmap?) {
        postOrSet(liveTrackAlbumArt, TrackAlbumArt(track.albumArtKey, bitmap))
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
        timelineCapability: TimelineCapability = TimelineCapability.NONE,
        receiveTranslation: Boolean = true,
        receiveRomanization: Boolean = true,
        writers: List<String> = emptyList()
    ): Boolean {
        val info = ParsedLyricsInfo(
            lines = lines,
            hasSyllable = hasSyllable,
            sourceLabel = sourceLabel,
            apiPath = apiPath,
            timelineCapability = timelineCapability,
            receiveTranslation = receiveTranslation,
            receiveRomanization = receiveRomanization,
            writers = writers
        )
        val acceptedInfo = lyricSourceArbiter.chooseParsedLyrics(
            current = liveParsedLyrics.value,
            candidate = info
        ) ?: return false
        postOrSet(liveParsedLyrics, acceptedInfo)
        if (acceptedInfo.lines.isNotEmpty()) {
            updateLyricResolveState(LyricResolveState.FOUND)
        }
        AppLogger.getInstance().log(
            "Repo",
            "📝 Parsed lyrics updated: ${lines.size} lines, syllable: $hasSyllable, capability=$timelineCapability"
        )
        return true
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
        internal const val API_PATH_NOTIFICATION = "Notification"
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
