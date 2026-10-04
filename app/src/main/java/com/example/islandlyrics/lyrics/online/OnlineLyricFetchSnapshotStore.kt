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

package com.example.islandlyrics.lyrics.online

/**
 * Keeps the most recent online lyric fetch outcome in memory so the
 * re-match screen can show every provider result without re-fetching.
 */
object OnlineLyricFetchSnapshotStore {

    data class Snapshot(
        val packageName: String,
        val queryTitle: String,
        val queryArtist: String,
        val fetchedAt: Long,
        val bestResult: OnlineLyricFetcher.LyricResult?,
        val attempts: List<OnlineLyricFetcher.ProviderAttempt>,
        val usedCleanTitleFallback: Boolean,
        val fromCache: Boolean = false,
        val songKey: String? = null
    )

    private var snapshot: Snapshot? = null
    private var trackIdSnapshot: Snapshot? = null

    fun save(snapshot: Snapshot) {
        synchronized(this) {
            this.snapshot = snapshot
            val idAttempts = snapshot.attempts.filter { it.queryVariant == "track_id" }
            if (snapshot.songKey != null && idAttempts.isNotEmpty()) {
                trackIdSnapshot = snapshot.copy(attempts = idAttempts)
            }
        }
    }

    fun get(
        packageName: String,
        queryTitle: String,
        queryArtist: String,
        songKey: String? = null
    ): Snapshot? = synchronized(this) {
        val current = snapshot?.takeIf {
            it.matches(packageName, queryTitle, queryArtist) &&
                (songKey == null || it.songKey == null || it.songKey == songKey)
        }
        val retained = trackIdSnapshot?.takeIf { songKey != null && it.songKey == songKey }
        // ID selections belong to the song, even when notification lyrics change the query.
        retained?.copy(
            attempts = current?.attempts.orEmpty().filterNot { it.queryVariant == "track_id" } + retained.attempts
        ) ?: current
    }

    fun getTrackId(songKey: String?): Snapshot? = synchronized(this) {
        trackIdSnapshot?.takeIf { songKey != null && it.songKey == songKey }
    }

    fun clear() {
        synchronized(this) {
            snapshot = null
            trackIdSnapshot = null
        }
    }

    fun buildKey(
        packageName: String,
        queryTitle: String,
        queryArtist: String
    ): String = "$packageName\u0000${queryTitle.trim().lowercase()}\u0000${queryArtist.trim().lowercase()}"

    private fun Snapshot.matches(
        packageName: String,
        queryTitle: String,
        queryArtist: String
    ): Boolean = buildKey(packageName, queryTitle, queryArtist) ==
        buildKey(this.packageName, this.queryTitle, this.queryArtist)
}
