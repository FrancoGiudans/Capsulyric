package com.example.islandlyrics.ui.overlay.floating

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit
import androidx.annotation.StringRes
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.LabFeatureManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class FloatingLyricsTrack(
    val player: String,
    val mediaId: String,
    val mediaUri: String,
    val title: String,
    val artist: String
) {
    val isIdentifiable: Boolean
        get() = mediaId.isNotBlank() || mediaUri.isNotBlank() || title.isNotBlank()

    fun differsFrom(other: FloatingLyricsTrack): Boolean {
        if (player != other.player) return true
        if (mediaId.isNotBlank() && other.mediaId.isNotBlank()) return mediaId != other.mediaId
        if (mediaUri.isNotBlank() && other.mediaUri.isNotBlank()) return mediaUri != other.mediaUri
        if (title.isBlank() || other.title.isBlank()) return false
        return !title.equals(other.title, ignoreCase = true) ||
            (artist.isNotBlank() && other.artist.isNotBlank() && !artist.equals(other.artist, ignoreCase = true))
    }
}

internal enum class FloatingLyricsPlayback { INACTIVE, PLAYING, PAUSED }
internal enum class FloatingLyricsDismissal { VISIBLE, UNTIL_RESUME, UNTIL_NEXT_TRACK, UNTIL_MANUAL }

internal enum class FloatingLyricsRecovery(val value: String, @param:StringRes val label: Int) {
    ON_RESUME("on_resume", R.string.settings_floating_recover_on_resume),
    NEXT_TRACK("next_track", R.string.settings_floating_recover_next_track),
    OFF("off", R.string.settings_floating_recover_off)
}

internal data class FloatingLyricsSession(
    val playback: FloatingLyricsPlayback = FloatingLyricsPlayback.INACTIVE,
    val track: FloatingLyricsTrack? = null,
    val dismissal: FloatingLyricsDismissal = FloatingLyricsDismissal.VISIBLE,
    val mediaSession: Any? = null,
    val hiddenMediaSession: Any? = null,
    val waitingForResume: Boolean = false,
    val retainOnPause: Boolean = false,
    val clock: FloatingLyricsClock? = null
) {
    val isHidden: Boolean get() = dismissal != FloatingLyricsDismissal.VISIBLE

    fun withPlayback(
        playback: FloatingLyricsPlayback,
        candidate: FloatingLyricsTrack?,
        mediaSession: Any? = null,
        isActuallyPlaying: Boolean = playback == FloatingLyricsPlayback.PLAYING,
        wasVisible: Boolean = true
    ): FloatingLyricsSession {
        val nextTrack = candidate?.takeIf { it.isIdentifiable }
        val changedTrack = nextTrack != null && track != null && nextTrack.differsFrom(track)
        val confirmedTrack = when {
            changedTrack || track == null -> nextTrack
            nextTrack != null && track != null -> track.copy(
                mediaId = nextTrack.mediaId.ifBlank { track.mediaId },
                mediaUri = nextTrack.mediaUri.ifBlank { track.mediaUri },
                title = nextTrack.title.ifBlank { track.title },
                artist = nextTrack.artist.ifBlank { track.artist }
            )
            else -> track
        }
        val sameHiddenSession = mediaSession != null && mediaSession == hiddenMediaSession
        val nextHiddenSession = hiddenMediaSession.takeIf { mediaSession == null || sameHiddenSession }
        val resumed = sameHiddenSession && waitingForResume && isActuallyPlaying
        val nextDismissal = when {
            resumed && dismissal == FloatingLyricsDismissal.UNTIL_RESUME -> FloatingLyricsDismissal.VISIBLE
            changedTrack && dismissal == FloatingLyricsDismissal.UNTIL_NEXT_TRACK -> FloatingLyricsDismissal.VISIBLE
            else -> dismissal
        }
        val sameMediaSession = mediaSession == this.mediaSession
        val retain = playback == FloatingLyricsPlayback.PAUSED && sameMediaSession && when (this.playback) {
            FloatingLyricsPlayback.PLAYING -> wasVisible && !isHidden
            FloatingLyricsPlayback.PAUSED -> retainOnPause
            FloatingLyricsPlayback.INACTIVE -> false
        }
        return copy(
            playback = playback, track = confirmedTrack, dismissal = nextDismissal,
            mediaSession = mediaSession ?: this.mediaSession,
            hiddenMediaSession = nextHiddenSession,
            waitingForResume = nextHiddenSession != null && (waitingForResume ||
                (sameHiddenSession && playback == FloatingLyricsPlayback.PAUSED)),
            retainOnPause = retain
        )
    }

    fun hide(recovery: FloatingLyricsRecovery): FloatingLyricsSession = copy(
        dismissal = when (recovery) {
            FloatingLyricsRecovery.ON_RESUME -> FloatingLyricsDismissal.UNTIL_RESUME
            FloatingLyricsRecovery.NEXT_TRACK -> FloatingLyricsDismissal.UNTIL_NEXT_TRACK
            FloatingLyricsRecovery.OFF -> FloatingLyricsDismissal.UNTIL_MANUAL
        },
        hiddenMediaSession = mediaSession.takeIf { recovery == FloatingLyricsRecovery.ON_RESUME },
        waitingForResume = recovery == FloatingLyricsRecovery.ON_RESUME && playback == FloatingLyricsPlayback.PAUSED
    )

    fun endMediaSession(token: Any): FloatingLyricsSession = copy(
        mediaSession = mediaSession.takeUnless { it == token },
        hiddenMediaSession = hiddenMediaSession.takeUnless { it == token },
        waitingForResume = waitingForResume && hiddenMediaSession != token,
        retainOnPause = retainOnPause && mediaSession != token,
        clock = clock.takeUnless { mediaSession == token }
    )

    fun shouldShow(enabled: Boolean, keepOnPause: Boolean, hasPermission: Boolean, calibrating: Boolean): Boolean =
        enabled && hasPermission && !calibrating && !isHidden &&
            (playback == FloatingLyricsPlayback.PLAYING ||
                (keepOnPause && playback == FloatingLyricsPlayback.PAUSED && retainOnPause))
}

/** Process state is shared by the media listener, lyric service and settings. */
internal object FloatingLyricsVisibility {
    internal const val RESUME_NEXT_TRACK = "floating_resume_next_track"
    const val RECOVERY = "floating_hidden_recovery"
    const val KEEP_ON_PAUSE = "floating_keep_on_pause"
    internal const val MANUALLY_HIDDEN = "desktop_lyrics_manually_hidden"
    private const val VISIBILITY_MIGRATED = "desktop_lyrics_visibility_migrated"

    private val currentSession = MutableStateFlow(FloatingLyricsSession())
    val session = currentSession.asStateFlow()

    fun updatePlayback(
        playback: FloatingLyricsPlayback,
        track: FloatingLyricsTrack? = null,
        mediaSession: Any? = null,
        isActuallyPlaying: Boolean = playback == FloatingLyricsPlayback.PLAYING,
        wasVisible: Boolean = true,
        clock: FloatingLyricsClock? = null
    ) {
        currentSession.value = currentSession.value
            .withPlayback(playback, track, mediaSession, isActuallyPlaying, wasVisible)
            .copy(clock = clock)
    }

    fun endMediaSession(token: Any) {
        currentSession.value = currentSession.value.endMediaSession(token)
    }

    fun restoreManualDismissal(prefs: SharedPreferences) {
        if (!prefs.getBoolean(VISIBILITY_MIGRATED, false)) {
            val wasHidden = LabFeatureManager.isFloatingLyricsEnabled(prefs) &&
                !prefs.getBoolean(AppPreferences.Keys.FLOATING_LYRICS_ENABLED, false)
            prefs.edit {
                if (wasHidden) putBoolean(MANUALLY_HIDDEN, true)
                putBoolean(VISIBILITY_MIGRATED, true)
            }
        }
        if (prefs.getBoolean(MANUALLY_HIDDEN, false)) {
            currentSession.value = currentSession.value.hide(FloatingLyricsRecovery.OFF)
        }
    }

    fun readRecovery(prefs: SharedPreferences): FloatingLyricsRecovery {
        val saved = prefs.getString(RECOVERY, null)
        return FloatingLyricsRecovery.entries.firstOrNull { it.value == saved }
            ?: if (prefs.getBoolean(RESUME_NEXT_TRACK, false)) FloatingLyricsRecovery.NEXT_TRACK else FloatingLyricsRecovery.OFF
    }

    fun hide(prefs: SharedPreferences) {
        val hidden = currentSession.value.hide(readRecovery(prefs))
        prefs.edit {
            if (hidden.dismissal == FloatingLyricsDismissal.UNTIL_MANUAL) putBoolean(MANUALLY_HIDDEN, true)
            else remove(MANUALLY_HIDDEN)
        }
        currentSession.value = hidden
    }

    fun showNow(prefs: SharedPreferences) {
        prefs.edit { remove(MANUALLY_HIDDEN) }
        currentSession.value = currentSession.value.copy(
            dismissal = FloatingLyricsDismissal.VISIBLE, hiddenMediaSession = null, waitingForResume = false
        )
    }

    fun showNow(context: Context) {
        showNow(AppPreferences.of(context))
    }

    fun shouldShow(context: Context, prefs: SharedPreferences, calibrating: Boolean = false): Boolean =
        currentSession.value.shouldShow(
            enabled = prefs.getBoolean("service_enabled", true) &&
                LabFeatureManager.isFloatingLyricsEnabled(prefs),
            keepOnPause = prefs.getBoolean(KEEP_ON_PAUSE, false),
            hasPermission = Settings.canDrawOverlays(context),
            calibrating = calibrating
        )
}
