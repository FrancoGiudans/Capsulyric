package com.example.islandlyrics.service

import android.content.SharedPreferences
import android.os.Handler
import com.example.islandlyrics.core.logging.AppLogger

class DelayedStopController(
    private val handler: Handler,
    private val prefsProvider: () -> SharedPreferences,
    private val onStop: () -> Unit
) {
    private val delayedStopRunnable = Runnable {
        onStop()
    }

    fun onPlaybackChanged(isPlaying: Boolean) {
        if (isPlaying) {
            cancel()
            return
        }

        cancel()
        val prefs = prefsProvider()
        val userDelay = prefs.getLong("notification_dismiss_delay", 0L)
        val delay = if (userDelay < 250L) 250L else userDelay

        AppLogger.getInstance().log(TAG, "🛑 Playback stopped. Scheduling delayed stop in ${delay}ms")
        handler.postDelayed(delayedStopRunnable, delay)
    }

    fun cancel() {
        handler.removeCallbacks(delayedStopRunnable)
    }

    companion object {
        private const val TAG = "DelayedStopController"
    }
}
