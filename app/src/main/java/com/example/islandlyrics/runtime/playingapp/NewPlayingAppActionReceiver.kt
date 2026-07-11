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

package com.example.islandlyrics.runtime.playingapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NewPlayingAppActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(NewPlayingAppNotifier.EXTRA_PACKAGE_NAME) ?: return
        val appName = intent.getStringExtra(NewPlayingAppNotifier.EXTRA_APP_NAME)

        when (intent.action) {
            NewPlayingAppNotifier.ACTION_ADD_APP -> {
                NewPlayingAppNotifier.addApp(context, packageName, appName)
            }
            NewPlayingAppNotifier.ACTION_IGNORE_APP -> {
                NewPlayingAppNotifier.ignoreApp(context, packageName)
            }
        }
    }
}
