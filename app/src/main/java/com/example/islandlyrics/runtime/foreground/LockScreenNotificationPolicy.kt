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

package com.example.islandlyrics.runtime.foreground

import android.app.KeyguardManager
import android.app.Notification
import android.content.Context
import android.os.PowerManager
import com.example.islandlyrics.core.settings.AppPreferences

/**
 * Lock-screen visibility policy for lyric notifications.
 *
 * The foreground notification itself cannot disappear from the lock screen,
 * so this mirrors the Live Clock behavior: hide the content with
 * VISIBILITY_SECRET while the screen is off or the keyguard is locked.
 */
object LockScreenNotificationPolicy {

    fun isHidden(context: Context): Boolean {
        val prefs = AppPreferences.of(context)
        if (!prefs.getBoolean(AppPreferences.Keys.NOTIFICATION_LOCK_SCREEN_HIDDEN, false)) {
            return false
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isInteractive == false) {
            return true
        }
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isKeyguardLocked == true
    }

    fun visibility(context: Context): Int {
        return if (isHidden(context)) {
            Notification.VISIBILITY_SECRET
        } else {
            Notification.VISIBILITY_PUBLIC
        }
    }
}
