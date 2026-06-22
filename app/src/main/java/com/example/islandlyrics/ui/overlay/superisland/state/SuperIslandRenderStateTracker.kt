package com.example.islandlyrics.ui.overlay.superisland.state

import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.ui.overlay.model.UIState

internal data class SuperIslandRenderDecision(
    val colorChanged: Boolean
)

internal class SuperIslandRenderStateTracker {
    var isFirstNotification = true
        private set
    var firstNotificationReason = "initial"
        private set

    private var lastSentDisplayLyric = ""
    private var lastSentFullLyric = ""
    private var lastSentProgressPercent = -1
    private var lastSentSubText = ""
    private var lastSentIsPlaying = false
    private var lastSentTitle = ""
    private var lastSentArtist = ""
    private var lastFocusParam = ""
    private var lastNotifyTime = 0L
    private var lastAppliedAlbumColor = 0

    fun resetForStart() {
        lastSentDisplayLyric = ""
        lastSentFullLyric = ""
        lastSentProgressPercent = -1
        lastSentSubText = ""
        lastSentIsPlaying = false
        isFirstNotification = true
        firstNotificationReason = "initial"
        lastAppliedAlbumColor = 0
        lastFocusParam = ""
    }

    fun clearFocusSignature() {
        lastFocusParam = ""
    }

    fun prepare(
        state: UIState,
        displayLyric: String,
        progressPercent: Int,
        accentColor: Int
    ): SuperIslandRenderDecision? {
        val trackChanged = state.title != lastSentTitle || state.artist != lastSentArtist
        if (trackChanged && !isFirstNotification) {
            AppLogger.getInstance().d("SuperIsland", "Track changed: ${state.title}. Resetting builder.")
            if (BuildConfig.DEBUG) {
                AppLogger.getInstance().log(
                    "SuperIsland",
                    "[NotifyTrace] markFirst reason=trackChanged track=${state.title} - ${state.artist}"
                )
            }
            isFirstNotification = true
            firstNotificationReason = "trackChanged"
            lastFocusParam = ""
        }

        val colorChanged = accentColor != lastAppliedAlbumColor

        val lyricLineChanged = !isFirstNotification && !trackChanged
                && state.fullLyric.isNotEmpty() && state.fullLyric != lastSentFullLyric
        if (lyricLineChanged) {
            if (BuildConfig.DEBUG) {
                AppLogger.getInstance().log(
                    "SuperIsland",
                    "[NotifyTrace] markFirst reason=lyricLineChanged fullLyric=${state.fullLyric}"
                )
            }
            isFirstNotification = true
            firstNotificationReason = "lyricLineChanged"
        }

        val contentChanged = trackChanged ||
                lyricLineChanged ||
                displayLyric != lastSentDisplayLyric ||
                state.isPlaying != lastSentIsPlaying
        val now = System.currentTimeMillis()

        if (!isFirstNotification && !colorChanged && !contentChanged) {
            val progressChangedEnough =
                kotlin.math.abs(progressPercent - lastSentProgressPercent) >= PROGRESS_NOTIFY_STEP_PERCENT
            if (!progressChangedEnough || now - lastNotifyTime < PROGRESS_THROTTLE_INTERVAL_MS) {
                return null
            }
        }

        if (!isFirstNotification && !colorChanged && !contentChanged && progressPercent == lastSentProgressPercent) {
            return null
        }

        return SuperIslandRenderDecision(colorChanged)
    }

    fun isDuplicateFocusSignature(focusSignature: String, colorChanged: Boolean): Boolean {
        return focusSignature == lastFocusParam && !colorChanged && !isFirstNotification
    }

    fun markRendered(
        state: UIState,
        displayLyric: String,
        progressPercent: Int,
        subText: String,
        accentColor: Int,
        focusSignature: String
    ) {
        lastAppliedAlbumColor = accentColor
        lastFocusParam = focusSignature
        lastSentDisplayLyric = displayLyric
        lastSentFullLyric = state.fullLyric
        lastSentProgressPercent = progressPercent
        lastSentSubText = subText
        lastSentIsPlaying = state.isPlaying
        lastSentTitle = state.title
        lastSentArtist = state.artist
        lastNotifyTime = System.currentTimeMillis()
    }

    fun markFirstNotificationSent() {
        if (isFirstNotification) {
            isFirstNotification = false
            firstNotificationReason = "steady"
        }
    }

    private companion object {
        const val PROGRESS_NOTIFY_STEP_PERCENT = 2
        const val PROGRESS_THROTTLE_INTERVAL_MS = 1_000L
    }
}
