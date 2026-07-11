/*
 *
 *  * Copyright (c) 2026 Franco Giudance
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

package com.example.islandlyrics.runtime.foreground

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
