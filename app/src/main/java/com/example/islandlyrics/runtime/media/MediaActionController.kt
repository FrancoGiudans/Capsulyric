package com.example.islandlyrics.runtime.media

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.example.islandlyrics.core.logging.AppLogger

class MediaActionController(
    private val mediaSessionManagerProvider: () -> MediaSessionManager?,
    private val mediaComponentProvider: () -> ComponentName?,
    private val targetPackageProvider: () -> String?
) {
    fun pause() {
        perform(
            actionName = "Pause",
            targetLog = "⏸️ Media paused via notification action",
            missingLog = "⚠️ No playing controller found for pause",
            eligible = { it == PlaybackState.STATE_PLAYING },
            action = { it.transportControls.pause() }
        )
    }

    fun play() {
        perform(
            actionName = "Play",
            targetLog = "▶️ Media resumed via notification action",
            missingLog = "⚠️ No paused controller found for play",
            eligible = { it == PlaybackState.STATE_PAUSED },
            action = { it.transportControls.play() }
        )
    }

    fun next() {
        perform(
            actionName = "Next",
            targetLog = "⏭️ Skipped to next via notification action",
            missingLog = "⚠️ No active controller found for skip next",
            eligible = ::isPlayableOrPaused,
            action = { it.transportControls.skipToNext() }
        )
    }

    fun previous() {
        perform(
            actionName = "Previous",
            targetLog = "⏮️ Skipped to prev via notification action",
            missingLog = "⚠️ No active controller found for skip prev",
            eligible = ::isPlayableOrPaused,
            action = { it.transportControls.skipToPrevious() }
        )
    }

    private fun perform(
        actionName: String,
        targetLog: String,
        missingLog: String,
        eligible: (Int) -> Boolean,
        action: (MediaController) -> Unit
    ) {
        try {
            val manager = mediaSessionManagerProvider() ?: return
            val component = mediaComponentProvider() ?: return
            val controllers = manager.getActiveSessions(component)
            val targetPackage = targetPackageProvider()

            val targetController = targetPackage?.let { pkg ->
                controllers.firstOrNull { it.packageName == pkg && it.playbackState?.state?.let(eligible) == true }
            }
            if (targetController != null) {
                action(targetController)
                AppLogger.getInstance().log(TAG, "$targetLog (target=$targetPackage)")
                return
            }

            val fallbackController = controllers.firstOrNull { controller ->
                controller.playbackState?.state?.let(eligible) == true
            }
            if (fallbackController != null) {
                action(fallbackController)
                AppLogger.getInstance().log(TAG, targetLog)
                return
            }

            AppLogger.getInstance().log(TAG, missingLog)
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "$actionName action failed: ${e.message}")
        }
    }

    private fun isPlayableOrPaused(state: Int): Boolean {
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED
    }

    private companion object {
        private const val TAG = "MediaActionController"
    }
}
