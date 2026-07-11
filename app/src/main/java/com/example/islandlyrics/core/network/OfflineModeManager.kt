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

package com.example.islandlyrics.core.network

import android.content.Context
import com.example.islandlyrics.core.settings.AppPreferences

object OfflineModeManager {
    const val KEY_FULLY_OFFLINE_MODE = AppPreferences.Keys.FULLY_OFFLINE_MODE

    fun isEnabled(context: Context): Boolean {
        return AppPreferences.isOfflineModeEnabled(AppPreferences.of(context.applicationContext))
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        AppPreferences.of(context.applicationContext)
            .edit()
            .putBoolean(KEY_FULLY_OFFLINE_MODE, enabled)
            .apply()
    }
}
