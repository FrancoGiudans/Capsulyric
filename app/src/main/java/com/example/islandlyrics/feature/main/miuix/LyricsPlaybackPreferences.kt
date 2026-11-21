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
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsTextWeight

internal enum class LyricsPlaybackPreference(private val key: String, private val default: Boolean = false) {
    AlbumProgress(AppPreferences.Keys.FULLSCREEN_PLAYBACK_ALBUM_PROGRESS),
    LyricBlur(AppPreferences.Keys.FULLSCREEN_PLAYBACK_LYRIC_BLUR, true),
    AlbumText(AppPreferences.Keys.FULLSCREEN_PLAYBACK_ALBUM_TEXT),
    KeepScreenOn(AppPreferences.Keys.FULLSCREEN_PLAYBACK_KEEP_SCREEN_ON),
    HideSystemBars(AppPreferences.Keys.FULLSCREEN_PLAYBACK_HIDE_SYSTEM_BARS),
    AutoHideControls(AppPreferences.Keys.FULLSCREEN_PLAYBACK_AUTO_HIDE_CONTROLS),
    AutoFollow(AppPreferences.Keys.FULLSCREEN_PLAYBACK_AUTO_FOLLOW, true),
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

internal data class LyricsPlaybackTypography(val size: Int, val weight: FloatingLyricsTextWeight) {
    companion object {
        val sizeRange = 16..36
        private const val DefaultSize = 24
        private val DefaultWeight = FloatingLyricsTextWeight.SEMIBOLD
        private const val SizeKey = AppPreferences.Keys.FULLSCREEN_PLAYBACK_TEXT_SIZE
        private const val WeightKey = AppPreferences.Keys.FULLSCREEN_PLAYBACK_TEXT_WEIGHT

        @Composable
        fun observe(prefs: SharedPreferences): LyricsPlaybackTypography {
            var typography by remember(prefs) { mutableStateOf(read(prefs)) }
            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { source, key ->
                    if (key == SizeKey || key == WeightKey || key == null) typography = read(source)
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            return typography
        }

        fun setSize(prefs: SharedPreferences, size: Int) {
            prefs.edit().putInt(SizeKey, size.coerceIn(sizeRange)).apply()
        }

        fun setWeight(prefs: SharedPreferences, weight: FloatingLyricsTextWeight) {
            prefs.edit().putInt(WeightKey, weight.weight).apply()
        }

        private fun read(prefs: SharedPreferences): LyricsPlaybackTypography {
            val size = prefs.getInt(SizeKey, DefaultSize).coerceIn(sizeRange)
            val weight = prefs.getInt(WeightKey, DefaultWeight.weight)
            return LyricsPlaybackTypography(
                size,
                FloatingLyricsTextWeight.entries.firstOrNull { it.weight == weight } ?: DefaultWeight,
            )
        }
    }
}
