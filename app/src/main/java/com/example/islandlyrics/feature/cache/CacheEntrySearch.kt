/*
 *
 *  * Copyright (c) 2026 Franco Giudance
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

package com.example.islandlyrics.feature.cache

import com.example.islandlyrics.lyrics.cache.OnlineLyricCacheStore

internal fun List<OnlineLyricCacheStore.LyricCacheEntrySummary>.filterByCacheQuery(
    query: String
): List<OnlineLyricCacheStore.LyricCacheEntrySummary> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return this
    return filter { entry ->
        listOf(
            entry.id,
            entry.packageName,
            entry.title,
            entry.artist,
            entry.queryTitle,
            entry.queryArtist,
            entry.providerLabel
        ).any { it.contains(normalizedQuery, ignoreCase = true) }
    }
}
