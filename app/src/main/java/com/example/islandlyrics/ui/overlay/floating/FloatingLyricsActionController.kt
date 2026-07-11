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
