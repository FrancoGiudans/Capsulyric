/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.islandlyrics.lyrics.source.OnlineLyricSource
import com.example.islandlyrics.lyrics.state.LyricRepository

/**
 * Debug-only receiver used to push fake playback metadata from adb and
 * exercise the real online lyric fetch path during emulator QA.
 */
class TestPlaybackInjector : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "received title=${intent.getStringExtra(EXTRA_TITLE)} artist=${intent.getStringExtra(EXTRA_ARTIST)}")
        try {
            val title = intent.getStringExtra(EXTRA_TITLE)?.trim().orEmpty()
            val artist = intent.getStringExtra(EXTRA_ARTIST)?.trim().orEmpty()
            val packageName = intent.getStringExtra(EXTRA_PACKAGE)?.trim().orEmpty()
            if (title.isBlank() || packageName.isBlank()) return

            LyricRepository.getInstance().updateMediaMetadata(
                title = title,
                artist = artist,
                packageName = packageName,
                duration = intent.getLongExtra(EXTRA_DURATION, DEFAULT_DURATION),
                rawTitle = title,
                rawArtist = artist,
                album = intent.getStringExtra(EXTRA_ALBUM).orEmpty()
            )
            OnlineLyricSource(context.applicationContext)
                .fetchFor(title, artist, packageName)
        } catch (e: Exception) {
            Log.e(TAG, "injection failed", e)
        }
    }

    companion object {
        private const val TAG = "TestPlaybackInjector"
        const val ACTION = "com.example.islandlyrics.debug.PUSH_PLAYBACK"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_ALBUM = "album"
        const val EXTRA_DURATION = "duration"
        private const val DEFAULT_DURATION = 240_000L
    }
}
