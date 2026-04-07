package com.example.islandlyrics.service

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import kotlin.math.abs

object MediaControllerSelection {

    fun isActivePlaybackState(state: Int?): Boolean {
        return state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_BUFFERING ||
            state == PlaybackState.STATE_CONNECTING ||
            state == PlaybackState.STATE_SKIPPING_TO_NEXT ||
            state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
            state == PlaybackState.STATE_FAST_FORWARDING ||
            state == PlaybackState.STATE_REWINDING
    }

    fun selectPrimary(
        controllers: List<MediaController>,
        allowedPackages: Set<String>
    ): MediaController? {
        return controllers.maxByOrNull { controller ->
            val state = controller.playbackState?.state
            val whitelisted = allowedPackages.contains(controller.packageName)
            when {
                whitelisted && isActivePlaybackState(state) -> 3_000 + playbackPriority(state)
                whitelisted -> 2_000 + playbackPriority(state)
                else -> 1_000 + playbackPriority(state)
            }
        }
    }

    fun selectSuggestion(
        controllers: List<MediaController>,
        allowedPackages: Set<String>
    ): MediaController? {
        return controllers.maxByOrNull { controller ->
            val state = controller.playbackState?.state
            val whitelisted = allowedPackages.contains(controller.packageName)
            when {
                !whitelisted && state == PlaybackState.STATE_PLAYING -> 4_000
                state == PlaybackState.STATE_PLAYING -> 3_000
                whitelisted -> 2_000
                else -> 1_000
            } + playbackPriority(state)
        }
    }

    fun selectProgressTarget(
        controllers: List<MediaController>,
        targetPackage: String?,
        targetTitle: String?,
        targetArtist: String?,
        targetDuration: Long
    ): MediaController? {
        if (controllers.isEmpty()) return null

        val packageMatches = targetPackage?.takeIf { it.isNotBlank() }?.let { pkg ->
            controllers.filter { it.packageName == pkg }
        }.orEmpty()
        val candidates = if (packageMatches.isNotEmpty()) packageMatches else controllers

        return candidates.maxByOrNull { controller ->
            var score = playbackPriority(controller.playbackState?.state) * 10

            if (!targetPackage.isNullOrBlank() && controller.packageName == targetPackage) {
                score += 1_000
            }

            val metadata = controller.metadata
            val controllerTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val controllerArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val controllerDuration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            if (!targetTitle.isNullOrBlank() && controllerTitle.equals(targetTitle, ignoreCase = true)) {
                score += 240
            }
            if (!targetArtist.isNullOrBlank() && controllerArtist.equals(targetArtist, ignoreCase = true)) {
                score += 120
            }
            if (targetDuration > 0L && controllerDuration > 0L) {
                val delta = abs(controllerDuration - targetDuration)
                score += when {
                    delta <= 1_500L -> 120
                    delta <= 5_000L -> 40
                    else -> 0
                }
            }
            if ((controller.playbackState?.position ?: 0L) > 0L) {
                score += 5
            }

            score
        }
    }

    private fun playbackPriority(state: Int?): Int {
        return when (state) {
            PlaybackState.STATE_PLAYING -> 100
            PlaybackState.STATE_BUFFERING -> 90
            PlaybackState.STATE_CONNECTING -> 80
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> 70
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING -> 60
            PlaybackState.STATE_PAUSED -> 40
            else -> 0
        }
    }
}
