/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *
 *  * This file is part of Capsulyric.
 *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.core.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.edit
import com.example.islandlyrics.R

/**
 * Manages the launcher alias visibility.
 * When hidden, the app icon is removed from the home screen launcher.
 * Users can still access the app via Quick Settings tile, URL scheme, or Manage Space,
 * which opens the Cache Management page.
 */
object LauncherAliasManager {

    private const val PREF_KEY = AppPreferences.Keys.LAUNCHER_ALIAS_HIDDEN

    /**
     * Returns true if the launcher icon is currently hidden.
     */
    fun isHidden(context: Context): Boolean {
        return AppPreferences.of(context).getBoolean(PREF_KEY, false)
    }

    /**
     * Enable or disable the LauncherAlias component.
     * When disabled, the app icon disappears from the launcher.
     */
    fun setAliasEnabled(context: Context, enabled: Boolean) {
        val componentName = ComponentName(context, "com.example.islandlyrics.LauncherAlias")
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            componentName,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        AppPreferences.of(context).edit { putBoolean(PREF_KEY, !enabled) }
    }

    /**
     * Show a toast guiding the user to manually add the Quick Settings tile.
     */
    fun showAddTileToast(context: Context) {
        Toast.makeText(
            context,
            context.getString(R.string.toast_add_tile_manually),
            Toast.LENGTH_LONG
        ).show()
    }
}
