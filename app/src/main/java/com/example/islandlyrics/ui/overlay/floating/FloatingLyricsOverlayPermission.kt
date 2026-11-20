package com.example.islandlyrics.ui.overlay.floating

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.LabFeatureManager

@Composable
internal fun rememberFloatingLyricsPermissionAction(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) onGranted()
    }
    return {
        if (Settings.canDrawOverlays(context)) {
            onGranted()
        } else {
            launcher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
        }
    }
}

@Composable
internal fun rememberFloatingLyricsRestoreAction(onRestored: () -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val prefs = remember(context) { AppPreferences.of(context) }
    val session by FloatingLyricsVisibility.session.collectAsState()
    val enabled = LabFeatureManager.isFloatingLyricsEnabled(prefs) && prefs.getBoolean("service_enabled", true)
    val restore = rememberFloatingLyricsPermissionAction {
        if (LabFeatureManager.isFloatingLyricsEnabled(prefs) && prefs.getBoolean("service_enabled", true)) {
            FloatingLyricsVisibility.showNow(context)
            onRestored()
        }
    }
    return restore.takeIf { enabled && session.isHidden }
}
