/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.islandlyrics.core.settings.AppPreferences

internal enum class LyricsPlaybackPreference(private val key: String, private val default: Boolean = false) {
    AlbumProgress(AppPreferences.Keys.FULLSCREEN_PLAYBACK_ALBUM_PROGRESS),
    LyricBlur(AppPreferences.Keys.FULLSCREEN_PLAYBACK_LYRIC_BLUR, true),
    AlbumText(AppPreferences.Keys.FULLSCREEN_PLAYBACK_ALBUM_TEXT),
    KeepScreenOn(AppPreferences.Keys.FULLSCREEN_PLAYBACK_KEEP_SCREEN_ON),
    AutoHideControls(AppPreferences.Keys.FULLSCREEN_PLAYBACK_AUTO_HIDE_CONTROLS),
    Translation(AppPreferences.Keys.FULLSCREEN_PLAYBACK_TRANSLATION),
    Romanization(AppPreferences.Keys.FULLSCREEN_PLAYBACK_ROMANIZATION);

    @Composable
    fun observe(prefs: SharedPreferences): Boolean {
        var enabled by remember(prefs, this) { mutableStateOf(prefs.getBoolean(key, default)) }
        DisposableEffect(prefs, this) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { source, changedKey ->
                if (changedKey == key || changedKey == null) enabled = source.getBoolean(key, default)
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return enabled
    }

    fun set(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(key, enabled).apply()
    }
}
