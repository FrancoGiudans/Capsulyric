/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.content.SharedPreferences
import android.media.AudioManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.islandlyrics.R
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import kotlin.math.roundToInt

@Composable
internal fun LyricsPlaybackSettingsSheet(
    show: Boolean,
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    onOpenOnlineLyricRematch: (() -> Unit)?,
) {
    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.settings_fullscreen_playback),
        onDismissRequest = onDismiss,
        renderInRootScaffold = false,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onOpenOnlineLyricRematch != null) {
                Card(Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.online_lyric_rematch_entry),
                        onClick = onOpenOnlineLyricRematch,
                    )
                }
            }
            Card(Modifier.fillMaxWidth()) {
                LyricsPlaybackSettings(prefs, renderInRootScaffold = false)
            }
        }
    }
}

@Composable
internal fun LyricsPlaybackVolumeSheet(show: Boolean, onDismiss: () -> Unit) {
    val activity = LocalActivity.current
    val stream = AudioManager.STREAM_MUSIC
    if (show && activity != null) {
        DisposableEffect(activity) {
            val previousStream = activity.volumeControlStream
            activity.volumeControlStream = stream
            onDispose { activity.volumeControlStream = previousStream }
        }
    }
    val title = stringResource(R.string.lyrics_playback_volume)
    OverlayBottomSheet(
        show = show,
        title = title,
        insideMargin = DpSize(BottomSheetDefaults.insideMargin.width, 24.dp),
        onDismissRequest = onDismiss,
        renderInRootScaffold = false,
    ) {
        val context = LocalContext.current
        val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
        if (audioManager != null) {
            val minVolume = remember(audioManager) { audioManager.getStreamMinVolume(stream) }
            val maxVolume = remember(audioManager) { audioManager.getStreamMaxVolume(stream) }
            var canAdjust by remember(audioManager) { mutableStateOf(!audioManager.isVolumeFixed && maxVolume > minVolume) }
            var volume by remember(audioManager) { mutableIntStateOf(audioManager.getStreamVolume(stream)) }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(show, audioManager, lifecycle) {
                if (show) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            volume = audioManager.getStreamVolume(stream)
                            delay(250)
                        }
                    }
                }
            }
            Slider(
                value = volume.toFloat(),
                onValueChange = { value ->
                    val level = value.roundToInt()
                    if (level != volume) {
                        try {
                            audioManager.setStreamVolume(stream, level, 0)
                            volume = audioManager.getStreamVolume(stream)
                        } catch (_: SecurityException) {
                            canAdjust = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    .semantics { contentDescription = title },
                enabled = canAdjust,
                valueRange = minVolume.toFloat()..maxOf(maxVolume, minVolume + 1).toFloat(),
                steps = (maxVolume - minVolume - 1).coerceAtLeast(0),
            )
        }
    }
}
