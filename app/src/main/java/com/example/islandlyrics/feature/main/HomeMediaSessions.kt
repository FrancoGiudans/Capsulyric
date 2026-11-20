/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main

import android.content.ComponentName
import android.content.SharedPreferences
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.runtime.service.MediaMonitorService

@Composable
internal fun rememberWhitelistedMediaSessions(): List<MediaController> {
    val context = LocalContext.current
    var activeControllers by remember(context) { mutableStateOf<List<MediaController>>(emptyList()) }
    var enabledPackages by remember(context) { mutableStateOf<Set<String>>(emptySet()) }

    DisposableEffect(context) {
        val prefs = AppPreferences.of(context)
        val ruleListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppPreferences.Keys.PARSER_RULES_JSON || key == null) {
                enabledPackages = ParserRuleHelper.getEnabledPackages(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(ruleListener)
        enabledPackages = ParserRuleHelper.getEnabledPackages(context)

        val manager = context.getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(context, MediaMonitorService::class.java)
        val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            activeControllers = controllers.orEmpty()
        }
        try {
            manager?.addOnActiveSessionsChangedListener(sessionListener, component)
            activeControllers = manager?.getActiveSessions(component).orEmpty()
        } catch (_: SecurityException) {
            // Session access requires notification listener permission.
        }

        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(ruleListener)
            manager?.removeOnActiveSessionsChangedListener(sessionListener)
        }
    }

    return remember(activeControllers, enabledPackages) {
        activeControllers
            .filter { it.packageName in enabledPackages }
            .sortedByDescending {
                val state = it.playbackState?.state
                state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
            }
    }
}
