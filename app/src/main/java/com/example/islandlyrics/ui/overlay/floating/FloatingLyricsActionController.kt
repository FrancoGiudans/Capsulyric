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

package com.example.islandlyrics.ui.overlay.floating
import android.app.ActivityOptions
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.runtime.service.MediaMonitorService

internal class FloatingLyricsActionController(
    private val context: Context,
    private val onClosed: () -> Unit,
    private val onMediaAction: (String) -> Unit
) {
    fun sendMediaAction(action: String) {
        try {
            onMediaAction(action)
        } catch (e: Exception) {
            Log.w(TAG, "sendMediaAction: ${e.message}")
        }
    }

    fun openPlayingApp(): Boolean {
        val controller = MediaMonitorService.currentLyricsPlayer()
        val player = controller?.packageName ?: FloatingLyricsVisibility.session.value.track?.player
        if (player.isNullOrBlank()) return showPlayerUnavailable()

        @Suppress("DEPRECATION")
        val launchMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        val options = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(launchMode)
            .toBundle()
        val sessionActivity = controller?.sessionActivity?.takeIf { it.creatorPackage == player }
        if (sessionActivity != null &&
            runCatching { sessionActivity.send(context, 0, null, null, null, null, options) }.isSuccess
        ) {
            return true
        }
        return runCatching {
            val launcher = context.packageManager.getLaunchIntentSenderForPackage(player)
            context.startIntentSender(launcher, null, 0, 0, 0, options)
        }.fold(
            onSuccess = { true },
            onFailure = {
                Log.w(TAG, "Unable to open playback app $player", it)
                showPlayerUnavailable()
            }
        )
    }

    private fun showPlayerUnavailable(): Boolean {
        Toast.makeText(context, R.string.floating_window_player_unavailable, Toast.LENGTH_SHORT).show()
        return false
    }

    fun closeFloatingLyrics() {
        FloatingLyricsVisibility.hide(AppPreferences.of(context))
        onClosed()
    }

    private companion object {
        const val TAG = "FloatingLyricsAction"
    }
}
