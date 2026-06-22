package com.example.islandlyrics.ui.overlay.floating
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.feature.mediacontrol.MediaControlActivity
import com.example.islandlyrics.runtime.service.LyricService

internal class FloatingLyricsActionController(
    private val context: Context,
    private val onAfterAction: () -> Unit,
    private val onDisabled: () -> Unit
) {
    fun sendMediaAction(action: String) {
        try {
            context.startService(Intent(context, LyricService::class.java).apply { this.action = action })
        } catch (e: Exception) {
            Log.w(TAG, "sendMediaAction: ${e.message}")
        }
        onAfterAction()
    }

    fun openMediaControl() {
        try {
            context.startActivity(
                Intent(context, MediaControlActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "openMediaControl: ${e.message}")
        }
        onAfterAction()
    }

    fun disableFloatingLyrics() {
        AppPreferences.of(context).edit {
            putBoolean(AppPreferences.Keys.FLOATING_LYRICS_ENABLED, false)
        }
        onDisabled()
    }

    private companion object {
        const val TAG = "FloatingLyricsAction"
    }
}
