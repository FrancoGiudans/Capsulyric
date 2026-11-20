/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main

import android.content.ComponentName
import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.runtime.media.MediaControllerSelection
import com.example.islandlyrics.runtime.service.MediaMonitorService
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsVisibility
import kotlin.math.abs

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

internal data class HomeMediaCards(val controllers: List<MediaController>, val primaryToken: MediaSession.Token?)

@Composable
internal fun rememberHomeMediaCards(track: LyricRepository.MediaInfo?): HomeMediaCards {
    val controllers = rememberWhitelistedMediaSessions()
    val session by FloatingLyricsVisibility.session.collectAsState()
    val primaryToken = session.mediaSession
    val duplicateCandidates = remember(controllers) {
        controllers.groupBy { it.packageName }.values.filter { it.size > 1 }.flatten()
    }
    var metadataRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(duplicateCandidates) {
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) { metadataRevision++ }
        }
        duplicateCandidates.forEach { it.registerCallback(callback) }
        onDispose { duplicateCandidates.forEach { it.unregisterCallback(callback) } }
    }
    return remember(controllers, track, primaryToken, metadataRevision) {
        val primary = selectHomePlaybackController(controllers, track, primaryToken)
        val ordered = if (primary == null) controllers else listOf(primary) + controllers.filter {
            it.sessionToken != primary.sessionToken
        }
        val visible = mutableListOf<MediaController>()
        val metadata = mutableMapOf<MediaSession.Token, MediaMetadata?>()
        fun metadataFor(controller: MediaController): MediaMetadata? =
            metadata.getOrPut(controller.sessionToken) { controller.metadata }
        for (controller in ordered) {
            val duplicate = visible.any { earlier ->
                earlier.sessionToken == controller.sessionToken ||
                    (earlier.packageName == controller.packageName &&
                        sameHomeTrack(metadataFor(earlier), metadataFor(controller)))
            }
            if (!duplicate) visible.add(controller)
        }
        HomeMediaCards(visible, primary?.sessionToken)
    }
}

private fun sameHomeTrack(first: MediaMetadata?, second: MediaMetadata?): Boolean {
    fun matchesId(key: String): Boolean {
        val id = first?.getString(key)?.takeIf { it.isNotBlank() } ?: return false
        return id == second?.getString(key)
    }
    if (matchesId(MediaMetadata.METADATA_KEY_MEDIA_ID) || matchesId(MediaMetadata.METADATA_KEY_MEDIA_URI)) return true
    val firstTitle = first?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
    val secondTitle = second?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
    if (firstTitle.isNullOrBlank() || secondTitle.isNullOrBlank()) {
        val unknown = if (firstTitle.isNullOrBlank()) first else second
        return unknown?.getString(MediaMetadata.METADATA_KEY_ARTIST).isNullOrBlank()
    }
    if (!secondTitle.equals(firstTitle, ignoreCase = true)) return false
    val firstArtist = first.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
    val secondArtist = second.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
    if (!firstArtist.isNullOrBlank() && !secondArtist.isNullOrBlank() &&
        !firstArtist.equals(secondArtist, ignoreCase = true)
    ) return false
    val firstDuration = first.getLong(MediaMetadata.METADATA_KEY_DURATION)
    val secondDuration = second.getLong(MediaMetadata.METADATA_KEY_DURATION)
    return firstDuration <= 0L || secondDuration <= 0L || abs(firstDuration - secondDuration) <= 1_500L
}

internal fun selectHomePlaybackController(
    controllers: List<MediaController>,
    track: LyricRepository.MediaInfo?,
    primaryToken: Any?,
): MediaController? {
    if (track == null) return null
    val candidates = controllers.filter { it.packageName == track.packageName }
    if (primaryToken != null) return candidates.firstOrNull { it.sessionToken == primaryToken }
    if (candidates.size == 1) return candidates.single()
    return MediaControllerSelection.selectProgressTarget(
        controllers = candidates.filter { it.metadata.belongsTo(track) },
        targetPackage = track.packageName,
        targetTitle = track.rawTitle,
        targetArtist = track.rawArtist,
        targetDuration = track.duration,
    )
}

internal fun MediaMetadata?.belongsTo(track: LyricRepository.MediaInfo?): Boolean {
    if (this == null || track == null) return false
    val id = getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
    if (!id.isNullOrBlank() && track.mediaId.isNotBlank()) return id == track.mediaId
    val uri = getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
    if (!uri.isNullOrBlank() && track.mediaUri.isNotBlank()) return uri == track.mediaUri
    val title = getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
    val artist = getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
    return (title.equals(track.rawTitle.trim(), ignoreCase = true) || title.equals(track.title.trim(), ignoreCase = true)) &&
        (artist.isNullOrBlank() || track.rawArtist.isBlank() ||
            artist.equals(track.rawArtist.trim(), ignoreCase = true) || artist.equals(track.artist.trim(), ignoreCase = true))
}
