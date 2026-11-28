/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.islandlyrics.R
import com.example.islandlyrics.ui.miuix.preference.BlurOverlayDropdownPreference
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsTextWeight
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import kotlin.math.roundToInt

@Composable
internal fun LyricsPlaybackSettings(
    prefs: SharedPreferences,
    renderInRootScaffold: Boolean = true,
) {
    val settings = listOf(
        LyricsPlaybackPreference.AlbumProgress to R.string.settings_fullscreen_playback_album_progress,
        LyricsPlaybackPreference.LyricBlur to R.string.settings_fullscreen_playback_lyric_blur,
        LyricsPlaybackPreference.AlbumText to R.string.settings_fullscreen_playback_album_text,
        LyricsPlaybackPreference.KeepScreenOn to R.string.settings_fullscreen_playback_keep_screen_on,
        LyricsPlaybackPreference.HideSystemBars to R.string.settings_fullscreen_playback_hide_system_bars,
        LyricsPlaybackPreference.AutoHideControls to R.string.settings_fullscreen_playback_auto_hide_controls,
        LyricsPlaybackPreference.AutoFollow to R.string.settings_fullscreen_playback_auto_follow,
        LyricsPlaybackPreference.ShowWriters to R.string.settings_fullscreen_playback_show_writers,
        LyricsPlaybackPreference.DuetAlignment to R.string.settings_fullscreen_playback_duet_alignment,
    )
    settings.forEach { (preference, label) ->
        SwitchPreference(
            title = stringResource(label),
            summary = when (preference) {
                LyricsPlaybackPreference.AutoHideControls ->
                    stringResource(R.string.settings_fullscreen_playback_auto_hide_controls_summary)
                LyricsPlaybackPreference.AutoFollow ->
                    stringResource(R.string.settings_fullscreen_playback_auto_follow_summary)
                LyricsPlaybackPreference.ShowWriters ->
                    stringResource(R.string.settings_fullscreen_playback_show_writers_summary)
                LyricsPlaybackPreference.DuetAlignment ->
                    stringResource(R.string.settings_fullscreen_playback_duet_alignment_summary)
                else -> null
            },
            checked = preference.observe(prefs),
            onCheckedChange = { preference.set(prefs, it) },
        )
    }
    val typography = LyricsPlaybackTypography.observe(prefs)
    val sizeRange = LyricsPlaybackTypography.sizeRange
    SliderPreference(
        title = stringResource(R.string.settings_floating_text_size),
        value = typography.size.toFloat(),
        valueText = stringResource(R.string.floating_settings_text_size_value, typography.size),
        valueRange = sizeRange.first.toFloat()..sizeRange.last.toFloat(),
        steps = sizeRange.last - sizeRange.first - 1,
        showKeyPoints = false,
        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
        onValueChange = { LyricsPlaybackTypography.setSize(prefs, it.roundToInt()) },
    )
    BlurOverlayDropdownPreference(
        title = stringResource(R.string.settings_floating_text_weight),
        items = FloatingLyricsTextWeight.entries.map { stringResource(it.label) },
        selectedIndex = typography.weight.ordinal,
        onSelectedIndexChange = { index ->
            LyricsPlaybackTypography.setWeight(prefs, FloatingLyricsTextWeight.entries[index])
        },
        renderInRootScaffold = renderInRootScaffold,
    )
}
