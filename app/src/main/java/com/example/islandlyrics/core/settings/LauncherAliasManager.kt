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

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.Button
import android.widget.Toast
import androidx.core.content.edit
import com.example.islandlyrics.R

/**
 * Manages the launcher alias visibility.
 * When hidden, the app icon is removed from the home screen launcher.
 * Users can still access the app via Quick Settings tile, URL scheme, or Manage Space.
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
        val componentName = ComponentName(context, ".LauncherAlias")
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
     * Show a warning dialog before hiding the launcher icon.
     * Explains the three alternative entry points.
     */
    fun showHiddenWarningDialog(
        context: Context,
        onConfirmed: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_hide_launcher_warning)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnAddTile = dialog.findViewById<Button>(R.id.btn_add_tile_and_hide)
        val btnHideNow = dialog.findViewById<Button>(R.id.btn_hide_now)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)

        btnAddTile.setOnClickListener {
            requestAddTile(context)
            onConfirmed()
            dialog.dismiss()
        }

        btnHideNow.setOnClickListener {
            onConfirmed()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Request the system to show the "Add Tile" dialog for the Quick Settings tile.
     * Available on Android 13+ (API 33+). Since minSdk=35, this is always available.
     */
    private fun requestAddTile(context: Context) {
        // requestAddTileService requires system-level permission on some devices;
        // fall back to showing a toast that guides the user to add the tile manually.
        Toast.makeText(
            context,
            context.getString(R.string.toast_add_tile_manually),
            Toast.LENGTH_LONG
        ).show()
    }
}
